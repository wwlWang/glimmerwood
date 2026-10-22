"""R113 batch 2: skill-wheel icons (32x32), drawn on a shared round badge so the wheel reads as one set.

Run: python3 scripts/art/build_skill_icons.py
Writes (all original, stdlib only, navy outline + soft gradients like the HUD cells):
  assets/orimod/textures/gui/skills/{light_burst,charge_flame,flash,regenerate}.png   learned skill icons
  assets/orimod/textures/gui/skills/empty_slot.png                                    unbound slot frame
  assets/orimod/textures/gui/skills/locked.png                                        unlearned skill frame
Shapes are signed-distance fields sampled 4x4 per pixel, then box-filtered down (soft edges, no external libs).
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/orimod/textures/gui/skills'
S, SS = 32, 4
C = S / 2
OUTLINE = (24, 38, 66)


def png(pixels):
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in pixels)
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', S, S, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def over(dst, src, a):
    """Straight-alpha 'over' of colour src with coverage a onto dst (r,g,b,a floats 0..1)."""
    if a <= 0: return dst
    da = dst[3]; oa = a + da * (1 - a)
    if oa <= 0: return (0, 0, 0, 0)
    rgb = tuple((s * a + d * da * (1 - a)) / oa for s, d in zip(src, dst[:3]))
    return rgb + (oa,)


def render(layers):
    """layers: list of fn(x, y) -> (rgb 0..255, alpha 0..1) or None, painted bottom to top."""
    out = []
    for py in range(S):
        row = []
        for px in range(S):
            acc = [0.0, 0.0, 0.0, 0.0]
            for sy in range(SS):
                for sx in range(SS):
                    x, y = px + (sx + .5) / SS, py + (sy + .5) / SS
                    c = (0.0, 0.0, 0.0, 0.0)
                    for layer in layers:
                        hit = layer(x, y)
                        if hit: c = over(c, tuple(v / 255 for v in hit[0]), hit[1])
                    # premultiplied accumulation for a correct box filter
                    acc[0] += c[0] * c[3]; acc[1] += c[1] * c[3]; acc[2] += c[2] * c[3]; acc[3] += c[3]
            n = SS * SS; a = acc[3] / n
            rgb = (0, 0, 0) if a <= 0 else tuple(acc[i] / acc[3] for i in range(3))
            row.append(tuple(round(v * 255) for v in rgb) + (round(a * 255),))
        out.append(row)
    return out


def cover(d):
    """Signed distance (negative inside) to coverage, ~1 sample-pixel soft edge."""
    return max(0.0, min(1.0, .5 - d * 1.6))


# ---- shared badge -------------------------------------------------------------------------------
R_OUT, R_RING = 15.2, 13.4


def badge(top, bottom, ring, dim=1.0):
    def disc(x, y):
        r = math.hypot(x - C, y - C)
        if r > R_OUT + 1: return None
        return OUTLINE, cover(r - R_OUT)
    def ring_layer(x, y):
        r = math.hypot(x - C, y - C)
        if r > R_OUT: return None
        t = (y - (C - R_RING)) / (2 * R_RING)
        return tuple(v * dim for v in lerp(ring, lerp(ring, (255, 255, 255), .35), 1 - t)), cover(r - (R_OUT - 1.1))
    def fill(x, y):
        r = math.hypot(x - C, y - C)
        if r > R_RING + 1: return None
        t = (y - (C - R_RING)) / (2 * R_RING)
        col = lerp(top, bottom, t)
        col = lerp(col, (255, 255, 255), .10 * max(0, 1 - math.hypot(x - C + 3, y - C + 4) / 9))
        return tuple(v * dim for v in col), cover(r - R_RING)
    return [disc, ring_layer, fill]


def glow(cx, cy, radius, col, strength):
    def layer(x, y):
        r = math.hypot(x - cx, y - cy)
        if r > radius: return None
        return col, strength * (1 - r / radius) ** 2
    return layer


def outlined(sdf, fill_fn, width=1.1):
    """Navy outline + gradient fill for a shape given by sdf(x, y)."""
    def edge(x, y):
        d = sdf(x, y)
        return (OUTLINE, cover(d - width)) if d < width + 1 else None
    def body(x, y):
        d = sdf(x, y)
        return (fill_fn(x, y, d), cover(d)) if d < 1 else None
    return [edge, body]


# ---- primitive SDFs -----------------------------------------------------------------------------
def circle(cx, cy, r): return lambda x, y: math.hypot(x - cx, y - cy) - r


def star(cx, cy, points, r_out, r_in, rot=0.0, sharp=1.0):
    """Smooth n-point star: radius oscillates between r_in and r_out with angle."""
    def f(x, y):
        dx, dy = x - cx, y - cy
        a = math.atan2(dy, dx) - rot
        k = (math.cos(a * points) + 1) / 2
        k = k ** sharp
        return math.hypot(dx, dy) - (r_in + (r_out - r_in) * k)
    return f


def union(*fs): return lambda x, y: min(f(x, y) for f in fs)


def subtract(a, b): return lambda x, y: max(a(x, y), -b(x, y))


# ---- icons --------------------------------------------------------------------------------------
NAVY_TOP, NAVY_BOTTOM = (36, 62, 104), (18, 30, 58)


def inside_badge(sdf, margin=1.6):
    """Clip a shape to the badge interior so nothing pokes past the ring."""
    return lambda x, y: max(sdf(x, y), math.hypot(x - C, y - C) - (R_RING - margin))


def light_burst():
    """A thrown orb of light with a tapering comet tail: Light Burst is a projectile that detonates."""
    hx, hy = 19.4, 12.6                       # orb centre; tail runs toward the lower left
    ux, uy = -0.70, 0.71                      # unit vector along the tail
    def tail(x, y):
        px, py = x - hx, y - hy
        s = px * ux + py * uy                 # distance along the tail
        d = abs(-px * uy + py * ux)           # distance across it
        width = 4.6 * max(0.0, 1 - s / 15.5) ** 1.2
        return max(d - width, -s, s - 15.5)
    orb = circle(hx, hy, 4.6)
    sparks = [circle(25.2, 8.4, .9), circle(24.6, 17.8, .8), circle(14.4, 7.2, .7)]
    gold_hi, gold, gold_lo = (255, 253, 232), (255, 220, 118), (232, 150, 48)
    def orb_fill(x, y, d): return lerp(gold_hi, gold, math.hypot(x - hx + 1.4, y - hy + 1.4) / 5.4)
    def tail_fill(x, y, d):
        s = (x - hx) * ux + (y - hy) * uy
        return lerp((255, 236, 170), gold_lo, s / 15)
    def spark_fill(x, y, d): return (255, 244, 200)
    layers = badge(NAVY_TOP, NAVY_BOTTOM, (92, 132, 196)) + [glow(hx, hy, 12, (255, 210, 110), .6)]
    def tail_layer(x, y):
        d = inside_badge(tail)(x, y)
        if d > 1: return None
        along = ((x - hx) * ux + (y - hy) * uy) / 15.5
        return tail_fill(x, y, d), cover(d) * max(0.0, 1 - along) ** 1.3
    layers += [tail_layer] + outlined(orb, orb_fill, .9)
    for sp in sparks: layers += outlined(sp, spark_fill, .6)
    return layers


def cone(x0, y0, r0, x1, y1):
    """Tapered capsule from a round end (x0,y0,r0) to a point (x1,y1)."""
    def f(x, y):
        vx, vy = x1 - x0, y1 - y0; L2 = vx * vx + vy * vy
        t = max(0.0, min(1.0, ((x - x0) * vx + (y - y0) * vy) / L2))
        px, py = x0 + vx * t, y0 + vy * t
        return math.hypot(x - px, y - py) - r0 * (1 - t) ** 1.1
    return f


def smooth_union(k, *fs):
    def f(x, y):
        d = fs[0](x, y)
        for g in fs[1:]:
            e = g(x, y); h = max(0.0, min(1.0, .5 + .5 * (e - d) / k))
            d = e * (1 - h) + d * h - k * h * (1 - h)
        return d
    return f


def charge_flame():
    """Spirit flame held until it swells: a blue-white flame with a tall centre tongue and two side licks."""
    outer = smooth_union(2.2, circle(15.8, 19.8, 6.0), cone(15.8, 18.4, 5.6, 17.4, 4.6),
                         cone(11.6, 19.0, 3.0, 8.8, 9.4), cone(20.0, 18.6, 2.8, 22.8, 10.6))
    inner = smooth_union(1.4, circle(15.8, 21.0, 3.2), cone(15.8, 20.2, 3.0, 16.6, 10.8))
    def outer_fill(x, y, d): return lerp((70, 124, 236), (150, 214, 255), (y - 6) / 18)
    def inner_fill(x, y, d): return lerp((214, 240, 255), (255, 255, 255), (y - 11) / 11)
    sparks = [circle(7.4, 16.6, .9), circle(24.6, 16.4, .8), circle(20.8, 6.4, .8)]
    def spark_fill(x, y, d): return (210, 240, 255)
    layers = badge(NAVY_TOP, NAVY_BOTTOM, (92, 150, 220)) + [glow(15.8, 18, 12, (110, 190, 255), .6)]
    layers += outlined(outer, outer_fill, 1.0) + outlined(inner, inner_fill, .0)
    for sp in sparks: layers += outlined(sp, spark_fill, .6)
    return layers


def sparkle(cx, cy, r):
    """Four-point sparkle with concave sides (an astroid), the classic 'shine' shape."""
    def f(x, y):
        dx, dy = abs(x - cx) / r, abs(y - cy) / r
        v = dx ** (2 / 3) + dy ** (2 / 3)
        return (v ** 1.5 - 1) * r * .55
    return f


def flash():
    """Sustained protective light: a large white sparkle with two smaller ones, bathed in a halo."""
    big, s1, s2 = sparkle(14.6, 15.4, 10.4), sparkle(23.0, 9.0, 3.6), sparkle(22.0, 22.8, 2.6)
    def big_fill(x, y, d): return lerp((255, 255, 255), (190, 224, 255), math.hypot(x - 14.6, y - 15.4) / 9)
    def small_fill(x, y, d): return (232, 244, 255)
    return (badge((44, 74, 120), (20, 34, 64), (140, 190, 240))
            + [glow(14.6, 15.4, 13, (200, 230, 255), .75)]
            + outlined(s2, small_fill, .7) + outlined(s1, small_fill, .7) + outlined(inside_badge(big, .8), big_fill, .9))


def regenerate():
    """Energy turned into life: a green life orb with a leaf, ringed by rising blue energy motes."""
    orb = circle(C, 17.4, 6.6)
    leaf = lambda x, y: (lambda u, v: math.hypot(u / 1.0, v / 2.0) - 2.1)(
        (x - 19.2) * math.cos(.7) + (y - 9.2) * math.sin(.7), -(x - 19.2) * math.sin(.7) + (y - 9.2) * math.cos(.7))
    stem = lambda x, y: max(abs(x - 16.6 - (11.6 - y) * .45) - .6, abs(y - 10.4) - 1.8)
    plus_v = lambda x, y: max(abs(x - C) - 1.1, abs(y - 17.4) - 3.4)
    plus_h = lambda x, y: max(abs(x - C) - 3.4, abs(y - 17.4) - 1.1)
    motes = [circle(7.6, 21.4, 1.3), circle(8.8, 13.2, 1.0), circle(24.6, 20.2, 1.2), circle(23.8, 26.0, .9)]
    def orb_fill(x, y, d): return lerp((168, 244, 150), (52, 170, 84), math.hypot(x - 14, y - 14.6) / 9)
    def leaf_fill(x, y, d): return lerp((190, 250, 160), (84, 196, 96), (x - 16) / 6)
    def plus_fill(x, y, d): return (246, 255, 240)
    def mote_fill(x, y, d): return (150, 210, 255)
    layers = badge((30, 70, 72), (14, 34, 44), (96, 180, 140)) + [glow(C, 17, 12, (130, 240, 150), .5)]
    layers += outlined(union(stem), leaf_fill, .7) + outlined(leaf, leaf_fill, .8) + outlined(orb, orb_fill, .9)
    layers += outlined(union(plus_v, plus_h), plus_fill, .0)
    for m in motes: layers += outlined(m, mote_fill, .7)
    return layers


def empty_slot():
    """Unbound slot: the badge ring only, with a faint centre dot."""
    dot = circle(C, C, 1.6)
    def dot_fill(x, y, d): return (88, 116, 160)
    return badge((28, 44, 76), (16, 24, 46), (76, 104, 150), dim=.9) + outlined(dot, dot_fill, .0)


def locked():
    """Unlearned skill: dimmed badge with a small padlock."""
    body = lambda x, y: max(abs(x - C) - 5.0, abs(y - 19.0) - 3.8)
    shackle = lambda x, y: max(abs(math.hypot(x - C, y - 14.4) - 3.4) - .9, y - 15.4)
    shackle_legs = lambda x, y: max(abs(abs(x - C) - 3.4) - .9, abs(y - 15.2) - 1.2)
    hole = circle(C, 18.6, 1.1)
    def metal(x, y, d): return lerp((168, 180, 204), (104, 116, 142), (y - 10) / 14)
    def hole_fill(x, y, d): return (30, 40, 62)
    return (badge((30, 36, 52), (18, 22, 34), (80, 88, 110), dim=.85)
            + outlined(union(shackle, shackle_legs), metal, .8) + outlined(body, metal, .9)
            + outlined(hole, hole_fill, .0))


ICONS = {'light_burst': light_burst, 'charge_flame': charge_flame, 'flash': flash,
         'regenerate': regenerate, 'empty_slot': empty_slot, 'locked': locked}

if __name__ == '__main__':
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, build in ICONS.items():
        (ROOT / f'{name}.png').write_bytes(png(render(build())))
        print('wrote', ROOT / f'{name}.png')
