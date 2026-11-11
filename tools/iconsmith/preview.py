"""Glyph previewer: translates the restricted Kotlin glyph DSL to Python and renders PNG sheets.

usage:
  python preview.py detail OUT.png id1 id2 ...    (zoom + line plate + dot plate, with QA overlays)
  python preview.py overview OUT.png [id ...]     (dense plate overview)
  python preview.py check                         (metrics for every glyph + lint)
"""
import re
import sys
import math
import glob
import os
from contextlib import contextmanager

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

GLYPH_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "feature", "icons", "src", "main", "java", "com", "dotos", "feature", "icons", "glyph")
S = 16  # supersample px per grid unit
U = 24


# ---------------------------------------------------------------- mock android canvas
class St:
    def __init__(self):
        self.img = Image.new("L", (U * S, U * S), 0)
        self.d = ImageDraw.Draw(self.img)
        self.rots = []
        self.sw = 2.0
        self.round = True
        self.pstack = []


st = St()


def T(x, y):
    for (a, px, py) in reversed(st.rots):
        r = math.radians(a)
        c, s = math.cos(r), math.sin(r)
        dx, dy = x - px, y - py
        x, y = px + dx * c - dy * s, py + dx * s + dy * c
    return (x, y)


def _fill(pts, v=255):
    st.d.polygon([(T(*p)[0] * S, T(*p)[1] * S) for p in pts], fill=v)


def _disc(p, r, v=255):
    x, y = T(*p)
    x *= S
    y *= S
    st.d.ellipse([x - r, y - r, x + r, y + r], fill=v)


def _stroke(pts, closed, v=255):
    r = st.sw / 2 * S
    tp = [T(*p) for p in pts]
    n = len(tp)
    segs = list(zip(tp, tp[1:])) + ([(tp[-1], tp[0])] if closed else [])
    for (a, b) in segs:
        ax, ay, bx, by = a[0] * S, a[1] * S, b[0] * S, b[1] * S
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy)
        if L < 1e-9:
            continue
        nx, ny = -dy / L * r, dx / L * r
        st.d.polygon([(ax + nx, ay + ny), (bx + nx, by + ny), (bx - nx, by - ny), (ax - nx, ay - ny)], fill=v)
    idxs = range(n) if closed else range(1, n - 1)
    for i in idxs:
        x, y = tp[i][0] * S, tp[i][1] * S
        st.d.ellipse([x - r, y - r, x + r, y + r], fill=v)
    if not closed and st.round:
        for i in (0, n - 1):
            x, y = tp[i][0] * S, tp[i][1] * S
            st.d.ellipse([x - r, y - r, x + r, y + r], fill=v)


def _circle_pts(cx, cy, r, n=96):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def _arc_pts(cx, cy, r, a0, sweep):
    n = max(2, int(abs(sweep) / 3) + 1)
    return [(cx + r * math.cos(math.radians(a0 + sweep * i / n)), cy + r * math.sin(math.radians(a0 + sweep * i / n))) for i in range(n + 1)]


def _rrect_pts(l, t, r, b, rad):
    rad = min(rad, (r - l) / 2, (b - t) / 2)
    if rad <= 0:
        return [(l, t), (r, t), (r, b), (l, b)]
    pts = []
    pts += _arc_pts(r - rad, t + rad, rad, -90, 90)
    pts += _arc_pts(r - rad, b - rad, rad, 0, 90)
    pts += _arc_pts(l + rad, b - rad, rad, 90, 90)
    pts += _arc_pts(l + rad, t + rad, rad, 180, 90)
    return pts


def line(x1, y1, x2, y2):
    _stroke([(x1, y1), (x2, y2)], False)


def polyline(*xy, closed=False):
    assert len(xy) >= 4 and len(xy) % 2 == 0, "polyline needs x,y pairs"
    pts = [(xy[i], xy[i + 1]) for i in range(0, len(xy), 2)]
    _stroke(pts, closed)


def circle(cx, cy, r, filled=False):
    if filled:
        _fill(_circle_pts(cx, cy, r))
    else:
        _stroke(_circle_pts(cx, cy, r), True)


def dot(cx, cy, r=1.2):
    _fill(_circle_pts(cx, cy, r))


def rect(l, t, r, b, radius=0.0, filled=False):
    pts = _rrect_pts(l, t, r, b, radius)
    if filled:
        _fill(pts)
    else:
        _stroke(pts, True)


def arc(cx, cy, r, start, sweep, filled=False):
    pts = _arc_pts(cx, cy, r, start, sweep)
    if filled:
        _fill([(cx, cy)] + pts)
    else:
        _stroke(pts, False)


def clearCircle(cx, cy, r):
    st.d.polygon([(T(*p)[0] * S, T(*p)[1] * S) for p in _circle_pts(cx, cy, r)], fill=0)


def clearRect(l, t, r, b, radius=0.0):
    st.d.polygon([(T(*p)[0] * S, T(*p)[1] * S) for p in _rrect_pts(l, t, r, b, radius)], fill=0)


def dotRow(y, *xs, r=1.2):
    for x in xs:
        dot(x, y, r)


def set_stroke(w):
    st.sw = w


def set_caps(v):
    st.round = v


def listOf(*a):
    return list(a)


class PathB:
    def __init__(self):
        self.subs = []  # [pts, closed]
        self.pos = None

    def _cur(self):
        if not self.subs:
            raise RuntimeError("path op before moveTo")
        return self.subs[-1][0]


def _pb():
    if not st.pstack:
        raise RuntimeError("path op outside path{}")
    return st.pstack[-1]


def moveTo(x, y):
    pb = _pb()
    pb.subs.append([[(x, y)], False])
    pb.pos = (x, y)


def lineTo(x, y):
    pb = _pb()
    pb._cur().append((x, y))
    pb.pos = (x, y)


def cubicTo(x1, y1, x2, y2, x3, y3):
    pb = _pb()
    p0 = pb.pos
    cur = pb._cur()
    for i in range(1, 25):
        t = i / 24
        mt = 1 - t
        cur.append((mt ** 3 * p0[0] + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t ** 3 * x3,
                    mt ** 3 * p0[1] + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t ** 3 * y3))
    pb.pos = (x3, y3)


def quadTo(x1, y1, x2, y2):
    pb = _pb()
    p0 = pb.pos
    cur = pb._cur()
    for i in range(1, 17):
        t = i / 16
        mt = 1 - t
        cur.append((mt * mt * p0[0] + 2 * mt * t * x1 + t * t * x2, mt * mt * p0[1] + 2 * mt * t * y1 + t * t * y2))
    pb.pos = (x2, y2)


def close():
    pb = _pb()
    pb.subs[-1][1] = True
    pb.pos = pb.subs[-1][0][0]


def _draw_path(pb, filled):
    for pts, closed in pb.subs:
        if filled:
            _fill(pts)
        else:
            if len(pts) == 1:
                continue
            _stroke(pts, closed)


@contextmanager
def path(filled=False):
    pb = PathB()
    st.pstack.append(pb)
    try:
        yield
    finally:
        st.pstack.pop()
        _draw_path(pb, filled)


@contextmanager
def fillStroke():
    pb = PathB()
    st.pstack.append(pb)
    try:
        yield
    finally:
        st.pstack.pop()
        _draw_path(pb, True)
        _draw_path(pb, False)


@contextmanager
def rotated(deg, px=12.0, py=12.0):
    st.rots.append((deg, px, py))
    try:
        yield
    finally:
        st.rots.pop()


MOCK = dict(line=line, polyline=polyline, circle=circle, dot=dot, rect=rect, arc=arc, clearCircle=clearCircle,
            clearRect=clearRect, dotRow=dotRow, set_stroke=set_stroke, set_caps=set_caps, listOf=listOf,
            moveTo=moveTo, lineTo=lineTo, cubicTo=cubicTo, quadTo=quadTo, close=close, path=path,
            fillStroke=fillStroke, rotated=rotated, cos=lambda a: math.cos(a), sin=lambda a: math.sin(a),
            sqrt=math.sqrt, PI=math.pi, abs=abs, min=min, max=max, range=range)


# ---------------------------------------------------------------- kotlin -> python
SKIP_FUNS = {"fillStroke"}


def strip_comments(t):
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    t = re.sub(r"//[^\n]*", "", t)
    return t


def match_brace(t, i):
    d = 0
    for j in range(i, len(t)):
        if t[j] == "{":
            d += 1
        elif t[j] == "}":
            d -= 1
            if d == 0:
                return j
    raise ValueError("unbalanced braces")


def kt_expr(s):
    s = re.sub(r"(?<![\w.])(\d+\.\d+|\d+)f\b", r"\1", s)
    s = re.sub(r"\btrue\b", "True", s)
    s = re.sub(r"\bfalse\b", "False", s)
    s = re.sub(r"\.toFloat\(\)", "", s)
    s = re.sub(r"\.toInt\(\)", "", s)
    s = re.sub(r"\bkotlin\.math\.", "", s)
    s = s.replace("&&", " and ").replace("||", " or ")
    return s


def kt_range(e):
    e = e.strip()
    m = re.match(r"^(.+?) until (.+)$", e)
    if m:
        return f"range(int({kt_expr(m.group(1))}), int({kt_expr(m.group(2))}))"
    m = re.match(r"^(.+?)\.\.(.+)$", e)
    if m:
        return f"range(int({kt_expr(m.group(1))}), int({kt_expr(m.group(2))}) + 1)"
    return kt_expr(e)


def convert_body(body, indent0=1):
    body = body.replace(";", "\n").replace("{", "\n{\n").replace("}", "\n}\n")
    lines = [l.strip() for l in body.split("\n")]
    lines = [l for l in lines if l]
    out = []
    ind = indent0
    block_stack = []
    i = 0

    def emit(s):
        out.append("    " * ind + s)

    while i < len(lines):
        l = lines[i]
        nxt = lines[i + 1] if i + 1 < len(lines) else ""
        if l == "{":
            raise ValueError("stray {")
        if l == "}":
            ind -= 1
            if block_stack:
                block_stack.pop()
            i += 1
            continue
        if l == ",":
            i += 1
            continue
        opener = nxt == "{"
        s = l.rstrip(",")
        if opener:
            m = re.match(r"^for \((\w+) in (.+)\)$", s)
            if m:
                emit(f"for {m.group(1)} in {kt_range(m.group(2))}:")
            elif re.match(r"^if \((.+)\)$", s):
                emit("if " + kt_expr(re.match(r"^if \((.+)\)$", s).group(1)) + ":")
            elif s == "else":
                ind -= 0
                emit("else:")
            else:
                m = re.match(r"^(\w+)(\(.*\))?$", s)
                if not m:
                    raise ValueError("cannot convert block header: " + s)
                name = m.group(1)
                args = kt_expr(m.group(2)) if m.group(2) else "()"
                emit(f"with {name}{args}:")
            ind += 1
            block_stack.append(1)
            i += 2
            # peek: empty block -> pass
            if i < len(lines) and lines[i] == "}":
                emit("pass")
            continue
        m = re.match(r"^(?:val|var) (\w+)(?:\s*:\s*\w+)?\s*=\s*(.+)$", s)
        if m:
            emit(f"{m.group(1)} = {kt_expr(m.group(2))}")
        elif re.match(r"^strokeWidth\s*=\s*(.+)$", s):
            emit(f"set_stroke({kt_expr(re.match(r'^strokeWidth\s*=\s*(.+)$', s).group(1))})")
        elif re.match(r"^roundCaps\s*=\s*(.+)$", s):
            emit(f"set_caps({kt_expr(re.match(r'^roundCaps\s*=\s*(.+)$', s).group(1))})")
        else:
            emit(kt_expr(s))
        i += 1
    return "\n".join(out) if out else "    pass"


def load_all():
    """returns (glyph_ids_in_order, python source namespace)"""
    glyphs = []  # (id, file, pybody)
    funs = []
    for f in sorted(glob.glob(os.path.join(GLYPH_DIR, "*.kt"))):
        base = os.path.basename(f)
        if base in ("GlyphScope.kt", "DotGlyphs.kt"):
            continue
        t = strip_comments(open(f, encoding="utf-8").read())
        for m in re.finditer(r"fun\s+(?:GlyphScope\.|Path\.)?(\w+)\s*\(([^)]*)\)\s*\{", t):
            name = m.group(1)
            if name in SKIP_FUNS:
                continue
            end = match_brace(t, m.end() - 1)
            params = []
            for p in [x.strip() for x in m.group(2).split(",") if x.strip()]:
                pm = re.match(r"^(\w+)\s*:\s*[\w.<>?]+(?:\s*=\s*(.+))?$", p)
                if not pm:
                    raise ValueError(f"{base}: cannot parse param '{p}' in fun {name}")
                params.append(pm.group(1) + (("=" + kt_expr(pm.group(2))) if pm.group(2) else ""))
            funs.append((name, params, convert_body(t[m.end():end]), base))
        for m in re.finditer(r"Glyph\(\s*\"([a-z0-9_]+)\"\s*\)\s*\{", t):
            end = match_brace(t, m.end() - 1)
            glyphs.append((m.group(1), base, convert_body(t[m.end():end], 2)))
    src = []
    for name, params, body, base in funs:
        src.append(f"def {name}({', '.join(params)}):\n{body}\n")
    ns = dict(MOCK)
    exec("\n".join(src), ns)
    return glyphs, ns


def render_glyph(gid, base, body, ns):
    global st
    st = St()
    # module-level functions use the global `st` of this module; ns functions reference MOCK closures that
    # look up `st` via module globals, so just rebind it here.
    code = f"def _g():\n    pass\n"
    code = f"def _g():\n{body.replace(chr(10) + '        ', chr(10) + '    ') if False else body}\n"
    # body is indented with 2 levels (8 spaces) -> wrap in a with-block-free function
    code = "def _g():\n    if True:\n" + body + "\n"
    exec(code, ns)
    ns["_g"]()
    return st.img


# ---------------------------------------------------------------- analysis
def analyze(img):
    a = np.array(img.resize((U * 8, U * 8), Image.BOX)) > 127
    if not a.any():
        return dict(empty=True), a, None
    ys, xs = np.where(a)
    x0, x1, y0, y1 = xs.min() / 8, (xs.max() + 1) / 8, ys.min() / 8, (ys.max() + 1) / 8
    info = dict(x0=x0, x1=x1, y0=y0, y1=y1, w=x1 - x0, h=y1 - y0, cx=(x0 + x1) / 2, cy=(y0 + y1) / 2)
    # holes
    inv = ~a
    lab, n = ndimage.label(inv)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]])))
    holes = []
    for k in range(1, n + 1):
        if k in border:
            continue
        m = lab == k
        dt = ndimage.distance_transform_edt(m)
        holes.append(2 * dt.max() / 8)
    info["holes"] = sorted(holes)
    # narrow gaps: background features narrower than 2 units that are not tiny corner noise
    r = 8
    yy, xx = np.ogrid[-r:r + 1, -r:r + 1]
    disc = (xx * xx + yy * yy) <= r * r
    bg = np.pad(~a, r, constant_values=True)
    opened = ndimage.binary_opening(bg, structure=disc)[r:-r, r:-r]
    narrow = (~a) & ~opened[:a.shape[0], :a.shape[1]]
    lab2, n2 = ndimage.label(narrow)
    gap = np.zeros_like(a)
    tot = 0.0
    for k in range(1, n2 + 1):
        m = lab2 == k
        ar = m.sum() / 64.0
        if ar >= 1.2:
            gap |= m
            tot += ar
    info["gap"] = tot
    return info, a, gap


def font(sz):
    try:
        return ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", sz)
    except Exception:
        return ImageFont.load_default()


def dot_preview(img, size):
    """18x18 dot-grid resample of the 24-unit mask drawn into a size x size square."""
    g = 18
    m = np.array(img.resize((g * 4, g * 4), Image.BOX)).astype(float) / 255.0
    cov = m.reshape(g, 4, g, 4).mean(axis=(1, 3))
    out = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(out)
    pitch = size * 4 / g
    for iy in range(g):
        for ix in range(g):
            c = cov[iy, ix]
            if c < 0.12:
                continue
            r = pitch / 2 * 0.98 * math.sqrt(min(1.0, c * 1.15))
            cx, cy = (ix + 0.5) * pitch, (iy + 0.5) * pitch
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    return out.resize((size, size), Image.LANCZOS)


def plate(img, D, k, dotstyle):
    p = Image.new("RGB", (D, D), (0, 0, 0))
    ImageDraw.Draw(p).ellipse([0, 0, D - 1, D - 1], fill=(30, 30, 30))
    g = int(D * k)
    if dotstyle:
        m = dot_preview(img, g)
    else:
        m = img.resize((g, g), Image.LANCZOS)
    off = (D - g) // 2
    white = Image.new("RGB", (g, g), (245, 245, 245))
    p.paste(white, (off, off), m)
    return p


def zoom_tile(img, info, gap, gid, W=192):
    base = img.resize((W, W), Image.LANCZOS)
    rgb = Image.merge("RGB", (base, base, base))
    d = ImageDraw.Draw(rgb)
    sc = W / U
    for i in range(0, U + 1, 4):
        c = (60, 60, 140) if i in (0, 12, 24) else (40, 40, 70)
        d.line([(i * sc, 0), (i * sc, W)], fill=c)
        d.line([(0, i * sc), (W, i * sc)], fill=c)
    # safe box 3..21
    d.rectangle([3 * sc, 3 * sc, 21 * sc, 21 * sc], outline=(90, 60, 30))
    if gap is not None:
        g = np.array(Image.fromarray((gap * 255).astype(np.uint8)).resize((W, W), Image.NEAREST)) > 0
        arr = np.array(rgb)
        arr[g] = (220, 40, 40)
        rgb = Image.fromarray(arr)
    return rgb


def make_detail(items, out, cols=4):
    tw, th = 330, 236
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * th), (14, 14, 14))
    d = ImageDraw.Draw(sheet)
    f = font(12)
    for n, (gid, img, info, gap) in enumerate(items):
        x, y = (n % cols) * tw, (n // cols) * th
        sheet.paste(zoom_tile(img, info, gap, gid), (x + 6, y + 6))
        sheet.paste(plate(img, 96, 0.62, False), (x + 204, y + 6))
        sheet.paste(plate(img, 96, 0.62, True), (x + 204, y + 108))
        if "empty" in info:
            txt = gid + " EMPTY"
        else:
            hs = ",".join(f"{h:.1f}" for h in info["holes"][:4])
            txt = f"{gid}  {info['w']:.1f}x{info['h']:.1f} c({info['cx']:.1f},{info['cy']:.1f}) h[{hs}] g{info['gap']:.1f}"
        d.text((x + 6, y + 204), txt, fill=(220, 220, 220), font=f)
    sheet.save(out)


def make_overview(items, out, cols=12, D=84):
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (D + 8) + 8, rows * (D + 34) + 8), (14, 14, 14))
    d = ImageDraw.Draw(sheet)
    f = font(10)
    for n, (gid, img, info, gap) in enumerate(items):
        x, y = 8 + (n % cols) * (D + 8), 8 + (n // cols) * (D + 34)
        sheet.paste(plate(img, D, 0.62, False), (x, y))
        sheet.paste(plate(img, 36, 0.62, True), (x, y + D + 2))
        d.text((x + 38, y + D + 2), gid[:11], fill=(200, 200, 200), font=f)
    sheet.save(out)


def main():
    cmd = sys.argv[1]
    glyphs, ns = load_all()
    ids = [g[0] for g in glyphs]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        print("DUPLICATE IDS:", dup)
    want = sys.argv[3:] if len(sys.argv) > 3 else ids
    items = []
    errors = 0
    for gid, base, body in glyphs:
        if gid not in want:
            continue
        try:
            img = render_glyph(gid, base, body, ns)
        except Exception as e:  # noqa
            print("ERROR in glyph", gid, ":", repr(e))
            errors += 1
            continue
        info, a, gap = analyze(img)
        items.append((gid, img, info, gap))
    if cmd == "detail":
        make_detail(items, sys.argv[2])
    elif cmd == "overview":
        make_overview(items, sys.argv[2])
    elif cmd == "dots":
        D = 84
        cols = 12
        rows = (len(items) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (D + 8) + 8, rows * (D + 22) + 8), (14, 14, 14))
        d = ImageDraw.Draw(sheet)
        f = font(10)
        for n, (gid, img, info, gap) in enumerate(items):
            x, y = 8 + (n % cols) * (D + 8), 8 + (n // cols) * (D + 22)
            sheet.paste(plate(img, D, 0.62, True), (x, y))
            d.text((x, y + D + 2), gid[:12], fill=(200, 200, 200), font=f)
        sheet.save(sys.argv[2])
    elif cmd == "check":
        print(f"{len(items)} glyphs rendered, {errors} errors, {len(ids)} defined")
        for gid, img, info, gap in items:
            if "empty" in info:
                print("EMPTY", gid)
                continue
            warn = []
            if abs(info["cx"] - 12) > 0.8 or abs(info["cy"] - 12) > 0.8:
                warn.append(f"off-centre ({info['cx']:.1f},{info['cy']:.1f})")
            long = max(info["w"], info["h"])
            if long > 18.6:
                warn.append(f"too big {long:.1f}")
            if long < 13.5:
                warn.append(f"small {long:.1f}")
            if info["x0"] < 2.5 or info["y0"] < 2.5 or info["x1"] > 21.5 or info["y1"] > 21.5:
                warn.append("near edge")
            if info["holes"] and info["holes"][0] < 2.4:
                warn.append(f"tiny hole {info['holes'][0]:.1f}")
            if info["gap"] > 3.0:
                warn.append(f"narrow gaps {info['gap']:.1f}")
            if warn:
                print(f"{gid:16s} {info['w']:.1f}x{info['h']:.1f}  " + "; ".join(warn))


if __name__ == "__main__":
    main()
