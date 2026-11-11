import {
  extractShader, extractCommon, parseParams, parsePresets, parseColorSlots, parseBindings, agslToGlsl, lintAgsl,
} from './kotlin-parse.js';

// Query: w=<wallpaper id>  preset=<preset id>  s=<css scale of 621x1380, default 0.5>
//        res=<internal pixel width, default 1080>  t=<seconds, default 18>  anim=1  ui=1
//        hour=<0..24>  scroll=<0..1>  p.<param>=<v>  c.<slot>=<rrggbb>  file=<Name.kt>
const KT_DIR = '../../feature/wallpaper/src/main/java/com/dotos/feature/wallpaper/wallpapers/';
const FILES = {
  atmosphere: 'Atmosphere.kt', fluted_bloom: 'FlutedBloom.kt', xray_leaves: 'XrayLeaves.kt', grain_totem: 'GrainTotem.kt',
};
const REF_W = 621, REF_H = 1380;

const q = new URLSearchParams(location.search);
const num = (k, d) => (q.has(k) ? parseFloat(q.get(k)) : d);
const statusEl = document.getElementById('status');
const log = (m, bad) => { statusEl.textContent += m + '\n'; if (bad) statusEl.className = 'bad'; };

function hex(h) {
  const v = parseInt(h, 16);
  return { r: ((v >> 16) & 255) / 255, g: ((v >> 8) & 255) / 255, b: (v & 255) / 255, a: 1 };
}

async function main() {
  const wid = q.get('w') || 'grain_totem';
  const file = q.get('file') || FILES[wid];
  const src = await (await fetch(KT_DIR + file + '?' + Date.now())).text();
  const noise = await (await fetch(KT_DIR + 'Noise.kt?' + Date.now())).text();
  const agsl = extractShader(src, extractCommon(noise));
  const params = parseParams(src);
  const presets = parsePresets(src);
  const slots = parseColorSlots(src);
  const bindings = parseBindings(src);
  const glsl = agslToGlsl(agsl);

  const setNames = [];
  for (const b of bindings) {
    if (b.kind === 'standard') setNames.push('iResolution', 'iTime'); else setNames.push(b.name);
  }
  for (const w of lintAgsl(agsl, setNames)) log('LINT: ' + w, true);

  const preset = presets.find((p) => p.id === q.get('preset')) || presets[0];
  const P = {}; for (const p of params) P[p.id] = preset.params[p.id] ?? p.default;
  const C = { ...preset.colors };
  for (const [k, v] of q) {
    if (k.startsWith('p.')) P[k.slice(2)] = parseFloat(v);
    if (k.startsWith('c.')) C[k.slice(2)] = hex(v);
  }

  const resW = num('res', 1080), resH = Math.round(resW * REF_H / REF_W);
  const s = num('s', 0.5);
  const canvas = document.getElementById('c');
  // The WebGL canvas is rendered at device resolution (default 1080 wide, like a phone) and shown
  // through a high-quality downscale into a 2D canvas, so grain looks like it does on a real screen.
  const glCanvas = document.createElement('canvas');
  glCanvas.width = resW; glCanvas.height = resH;
  const shownW = Math.round(REF_W * s), shownH = Math.round(REF_H * s);
  canvas.width = shownW; canvas.height = shownH;
  canvas.style.width = shownW + 'px'; canvas.style.height = shownH + 'px';
  const ctx2d = canvas.getContext('2d');
  ctx2d.imageSmoothingEnabled = true; ctx2d.imageSmoothingQuality = 'high';
  const gl = glCanvas.getContext('webgl2', { antialias: false, preserveDrawingBuffer: true });
  if (!gl) { log('WebGL2 unavailable', true); return; }

  const mk = (type, code) => {
    const sh = gl.createShader(type); gl.shaderSource(sh, code); gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
      const lines = code.split('\n');
      const err = gl.getShaderInfoLog(sh);
      log('GLSL compile error:\n' + err, true);
      const m = err.match(/ERROR: \d+:(\d+)/); if (m) log('line ' + m[1] + ': ' + lines[+m[1] - 1], true);
      throw new Error('compile');
    }
    return sh;
  };
  const prog = gl.createProgram();
  gl.attachShader(prog, mk(gl.VERTEX_SHADER,
    '#version 300 es\nvoid main(){ vec2 p = vec2((gl_VertexID<<1)&2, gl_VertexID&2); gl_Position = vec4(p*2.0-1.0,0.0,1.0); }'));
  gl.attachShader(prog, mk(gl.FRAGMENT_SHADER, glsl));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) { log(gl.getProgramInfoLog(prog), true); return; }
  gl.useProgram(prog);
  gl.viewport(0, 0, resW, resH);
  const loc = (n) => {
    const l = gl.getUniformLocation(prog, n);
    if (l === null) log('uniform not active (unused or undeclared): ' + n, false);
    return l;
  };
  const locs = new Map();
  for (const n of new Set(setNames)) locs.set(n, loc(n));

  const state = { time: num('t', 18), scroll: num('scroll', 0), hour: num('hour', 18) };

  function draw() {
    const F = {
      scroll: state.scroll, hour: state.hour, time: state.time, width: resW, height: resH,
      tiltX: 0, tiltY: 0, touchX: -1, touchY: -1, touchAge: 1000, battery: 1, density: 1,
    };
    for (const b of bindings) {
      if (b.kind === 'standard') {
        gl.uniform2f(locs.get('iResolution'), resW, resH);
        gl.uniform1f(locs.get('iTime'), state.time);
      } else if (b.kind === 'color') {
        const c = C[b.slot]; if (!c) throw new Error('missing colour slot ' + b.slot);
        const l = locs.get(b.name); if (l) gl.uniform4f(l, c.r, c.g, c.b, c.a);
      } else {
        const l = locs.get(b.name); if (!l) continue;
        const v = b.exprs.map((f) => f(P, F));
        if (v.length === 1) gl.uniform1f(l, v[0]); else if (v.length === 2) gl.uniform2f(l, ...v);
        else if (v.length === 3) gl.uniform3f(l, ...v); else gl.uniform4f(l, ...v);
      }
    }
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    if (shownW === resW) ctx2d.drawImage(glCanvas, 0, 0);
    else ctx2d.drawImage(glCanvas, 0, 0, resW, resH, 0, 0, shownW, shownH);
  }

  if (q.get('anim') === '1') {
    let last = performance.now();
    const loop = (now) => { state.time += (now - last) / 1000; last = now; draw(); requestAnimationFrame(loop); };
    requestAnimationFrame(loop);
  } else draw();

  if (q.get('ui') === '1') buildUi({ params, presets, slots, P, C, state, draw, wid });
  document.title = 'ready ' + wid + ' ' + preset.id;
  window.__ready = true;
  log(`${wid} / ${preset.id}  ${resW}x${resH}  glsl ${glsl.length}b`);
}

function buildUi({ params, presets, slots, P, C, state, draw, wid }) {
  const ui = document.getElementById('ui'); ui.style.display = 'block';
  const row = (label, el) => { const d = document.createElement('div'); d.append(label + ' ', el); ui.append(d); return d; };
  const sel = document.createElement('select');
  for (const p of presets) sel.append(new Option(p.name, p.id));
  sel.value = q.get('preset') || presets[0].id;
  sel.onchange = () => { const u = new URL(location.href); u.searchParams.set('preset', sel.value); location.href = u; };
  row('preset', sel);
  for (const p of params) {
    const r = document.createElement('input'); r.type = 'range'; r.min = p.min; r.max = p.max; r.step = p.step || (p.max - p.min) / 200; r.value = P[p.id];
    r.oninput = () => { P[p.id] = parseFloat(r.value); draw(); };
    row(p.label, r);
  }
  for (const sl of slots) {
    const c = C[sl.id]; if (!c) continue;
    const i = document.createElement('input'); i.type = 'color';
    i.value = '#' + [c.r, c.g, c.b].map((x) => Math.round(x * 255).toString(16).padStart(2, '0')).join('');
    i.oninput = () => { C[sl.id] = hex(i.value.slice(1)); draw(); };
    row(sl.label, i);
  }
  const t = document.createElement('input'); t.type = 'range'; t.min = 0; t.max = 600; t.step = 0.1; t.value = state.time;
  t.oninput = () => { state.time = parseFloat(t.value); draw(); };
  row('time', t);
}

main().catch((e) => { log(String(e), true); console.error(e); });
