// Minimal Kotlin-source scraper + AGSL -> GLSL ES 3.0 translator for the wallpaper preview.
// The harness reads the real DotWallpaper .kt files, so the shader that is previewed here is the
// exact text that ships in the APK (only mechanically translated), and presets / params are the
// real ones. Supported subset: see the DotWallpaper files in feature/wallpaper (shaderSource raw
// string, params/presets listOf(...) with positional args, bindUniforms with simple statements).

/** Index of the ')' / '}' matching the opener at src[open], skipping strings and // comments. */
export function findBalanced(src, open) {
  const openCh = src[open];
  const closeCh = openCh === '(' ? ')' : openCh === '{' ? '}' : ']';
  let depth = 0;
  for (let i = open; i < src.length; i++) {
    const ch = src[i];
    if (ch === '"') {
      if (src.startsWith('"""', i)) {
        i = src.indexOf('"""', i + 3) + 2;
      } else {
        i++;
        while (i < src.length && src[i] !== '"') { if (src[i] === '\\') i++; i++; }
      }
    } else if (ch === '/' && src[i + 1] === '/') {
      while (i < src.length && src[i] !== '\n') i++;
    } else if (ch === openCh) depth++;
    else if (ch === closeCh) { depth--; if (depth === 0) return i; }
  }
  throw new Error('unbalanced ' + openCh + ' at ' + open);
}

/** Splits on top-level commas. Drops empty trailing items (Kotlin trailing commas). */
export function splitTop(s) {
  const out = []; let depth = 0, cur = '';
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (ch === '"') {
      let j = i + 1; while (j < s.length && s[j] !== '"') { if (s[j] === '\\') j++; j++; }
      cur += s.slice(i, j + 1); i = j; continue;
    }
    if (ch === '(' || ch === '[' || ch === '{') depth++;
    if (ch === ')' || ch === ']' || ch === '}') depth--;
    if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; } else cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

const stripComments = (s) => s.replace(/(^|[^:"'])\/\/.*$/gm, '$1');

export function extractShader(src, common = '') {
  const m = src.match(/override val shaderSource[^=]*=\s*(Agsl\.COMMON\s*\+\s*)?"""([\s\S]*?)"""/);
  if (!m) throw new Error('shaderSource raw string not found');
  return (m[1] ? common + String.fromCharCode(10) : '') + m[2];
}

/** Extracts `const val COMMON = """..."""` (shared AGSL helpers) from Noise.kt. */
export function extractCommon(src) {
  const m = src.match(/const val COMMON\s*=\s*"""([\s\S]*?)"""/);
  return m ? m[1] : '';
}

function parseValue(tok) {
  tok = tok.trim();
  if (tok[0] === '"') return JSON.parse(tok);
  let m = tok.match(/^(?:c|Color)\(\s*0x([0-9A-Fa-f]{8})\s*\)$/);
  if (m) {
    const v = parseInt(m[1], 16);
    return { a: ((v >>> 24) & 255) / 255, r: ((v >>> 16) & 255) / 255, g: ((v >>> 8) & 255) / 255, b: (v & 255) / 255 };
  }
  m = tok.match(/^mapOf\(([\s\S]*)\)$/);
  if (m) {
    const o = {};
    for (const e of splitTop(m[1])) {
      const mm = e.match(/^("[^"]*")\s+to\s+([\s\S]+)$/);
      if (!mm) throw new Error('bad map entry: ' + e);
      o[JSON.parse(mm[1])] = parseValue(mm[2]);
    }
    return o;
  }
  if (tok === 'emptyMap()') return {};
  m = tok.match(/^(-?[\d.]+)f?$/);
  if (m) return parseFloat(m[1]);
  throw new Error('cannot parse Kotlin value: ' + tok);
}

function parseCalls(src, listMarker, ctor) {
  const at = src.search(listMarker);
  if (at < 0) return [];
  const open = src.indexOf('(', src.indexOf('listOf', at));
  const body = stripComments(src.slice(open + 1, findBalanced(src, open)));
  return splitTop(body).map((item) => {
    const mm = item.match(new RegExp('^' + ctor + '\\(([\\s\\S]*)\\)$'));
    if (!mm) throw new Error('expected ' + ctor + '(...) got: ' + item.slice(0, 60));
    return splitTop(mm[1]).map(parseValue);
  });
}

export function parseParams(src) {
  return parseCalls(src, /override val params\s*=/, 'WallpaperParam').map((a) => ({
    id: a[0], label: a[1], min: a[2], max: a[3], default: a[4], step: a[5] ?? 0,
  }));
}

export function parseColorSlots(src) {
  return parseCalls(src, /override val colorSlots\s*=/, 'ColorSlot').map((a) => ({ id: a[0], label: a[1] }));
}

export function parsePresets(src) {
  return parseCalls(src, /override val presets\s*=/, 'WallpaperPreset').map((a) => ({
    id: a[0], name: a[1], colors: a[2], params: a[3] ?? {},
  }));
}

/** Returns [{kind:'float', name, exprs:[js-fn...]} | {kind:'color', name, slot} | {kind:'standard'}]. */
export function parseBindings(src) {
  const at = src.search(/override fun bindUniforms\s*\(/);
  if (at < 0) return [];
  const open = src.indexOf('{', src.indexOf(')', at));
  const body = stripComments(src.slice(open + 1, findBalanced(src, open)));
  const out = [];
  const re = /shader\.(setFloatUniform|setColor|setStandard)\s*\(/g;
  let m;
  while ((m = re.exec(body))) {
    const o = body.indexOf('(', m.index + 'shader.'.length);
    const args = splitTop(body.slice(o + 1, findBalanced(body, o)));
    if (m[1] === 'setStandard') out.push({ kind: 'standard' });
    else if (m[1] === 'setColor') {
      const cm = args[1].match(/frame\.color\("(\w+)"\)/);
      if (!cm) throw new Error('setColor needs frame.color("slot"): ' + args[1]);
      out.push({ kind: 'color', name: JSON.parse(args[0]), slot: cm[1] });
    } else {
      out.push({ kind: 'float', name: JSON.parse(args[0]), exprs: args.slice(1).map(ktExpr) });
    }
  }
  return out;
}

function ktExpr(e) {
  const js = e
    .replace(/frame\.param\("(\w+)"\)/g, 'P["$1"]')
    .replace(/frame\.(scroll|hour|time|width|height|tiltX|tiltY|touchX|touchY|touchAge|battery|density)\b/g, 'F.$1')
    .replace(/\b(\d+(?:\.\d+)?)f\b/g, '$1')
    .replace(/\.toFloat\(\)/g, '')
    .replace(/(^|[^.\w])(sin|cos|sqrt|abs|min|max|floor)\(/g, '$1Math.$2(');
  return new Function('P', 'F', 'return (' + js + ');');
}

/** Static checks for AGSL restrictions that desktop GLSL would silently accept. */
export function lintAgsl(src, setNames) {
  const warn = [];
  const code = src.replace(/\/\/.*$/gm, '');
  if (/\btexture\s*\(/.test(code)) warn.push('texture() is not available in AGSL');
  if (/\$/.test(code)) warn.push('"$" inside a Kotlin raw string is a template');
  if (/%/.test(code)) warn.push('% operator is not allowed in SkSL ES2');
  if (/\bsaturate\b|\bint[234]\b|\buint\b/.test(code)) warn.push('avoid saturate/int vectors/uint');
  if (/(float|half|int)\s+\w+\s*\[/.test(code)) warn.push('arrays: keep to constant-index only (avoid)');
  const forRe = /for\s*\(([^)]*)\)/g; let m;
  while ((m = forRe.exec(code))) {
    const mm = m[1].match(/^\s*(int|float)\s+(\w+)\s*=\s*(-?[\d.]+)\s*;\s*\2\s*<=?\s*([\d.]+)\s*;\s*(?:\2\+\+|\+\+\2|\2\s*\+=\s*[\d.]+)\s*$/);
    if (!mm) warn.push('loop not in constant-bound form: for(' + m[1] + ')');
    else if (parseFloat(mm[4]) - parseFloat(mm[3]) > 24) warn.push('loop longer than 24 iterations: for(' + m[1] + ')');
  }
  const declared = [...code.matchAll(/uniform\s+(?:layout\(color\)\s+)?\w+\s+(\w+)\s*;/g)].map((x) => x[1]);
  for (const n of setNames) if (!declared.includes(n)) warn.push('uniform set but not declared (AGSL would throw): ' + n);
  for (const n of declared) if (!setNames.includes(n)) warn.push('uniform declared but never set: ' + n);
  // Global mutable state is illegal in SkSL.
  let depth = 0; const lines = code.split('\n');
  for (const line of lines) {
    if (depth === 0 && /^\s*(float|half|int|bool)[234]?\s+\w+\s*=/.test(line) && !/const/.test(line) && !/\(/.test(line.split('=')[0])) {
      warn.push('global non-const variable: ' + line.trim());
    }
    for (const ch of line) { if (ch === '{') depth++; else if (ch === '}') depth--; }
  }
  return warn;
}

/** Mechanical AGSL -> GLSL ES 3.00 translation. Math is untouched. */
export function agslToGlsl(src) {
  let s = src;
  s = s.replace(/layout\(color\)\s+/g, '');
  s = s.replace(/\b(?:half|float)2x2\b/g, 'mat2').replace(/\b(?:half|float)3x3\b/g, 'mat3');
  s = s.replace(/\b(?:half|float)([234])\b/g, 'vec$1');
  s = s.replace(/\bhalf\b/g, 'float');
  s = s.replace(/vec4\s+main\s*\(\s*vec2\s+(\w+)\s*\)/, 'vec4 agslMain(vec2 $1)');
  return (
    '#version 300 es\nprecision highp float;\nprecision highp int;\n' + s +
    '\nout vec4 fragColor;\nvoid main(){ fragColor = agslMain(vec2(gl_FragCoord.x, iResolution.y - gl_FragCoord.y)); }\n'
  );
}
