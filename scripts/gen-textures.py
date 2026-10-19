#!/usr/bin/env python3
"""R76: reproducible original 16x16 textures in one soft, Ori-like painted palette.

Every texture is drawn procedurally (seeded), rendered at 4x, box-downsampled for soft shading,
then alpha-snapped and outlined so it stays crisp at 16x. No vanilla or third-party pixels are used.
Run: python3 scripts/gen-textures.py   (writes assets/orimod/textures/{item,block}/*.png)
"""
import math, random, pathlib
from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/orimod/textures"
S, K = 16, 4                       # final size, supersample factor
N = S * K
P = dict(                          # shared palette
    night=(20, 30, 58), deep=(31, 52, 92), teal=(47, 111, 143), sky=(111, 200, 255), glow=(159, 232, 255),
    white=(244, 251, 255), gold=(255, 210, 122), amber=(232, 160, 70), leaf=(127, 224, 160), moss=(70, 128, 92),
    gloom=(90, 26, 108), violet=(138, 58, 184), stone=(74, 90, 106), slate=(107, 125, 140), pale=(170, 186, 196),
    sand=(216, 196, 138), dune=(184, 154, 90), bark=(92, 70, 60), parch=(226, 214, 180), ink=(47, 111, 143))

def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def rgba(c, a=255): return (*c, a)

def finish_item(big, outline=P["night"]):
    img = big.resize((S, S), Image.BOX)
    px = img.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    out = img.copy(); po = out.load()
    for y in range(S):
        for x in range(S):
            if px[x, y][3]: continue
            if any(0 <= x + dx < S and 0 <= y + dy < S and px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = rgba(outline, 255)
    return out

def premult_canvas(): return Image.new("RGBA", (N, N), (0, 0, 0, 0))

def orb(d, cx, cy, r, inner, outer, hi=P["white"]):
    for i in range(int(r * K), 0, -1):
        t = i / (r * K)
        c = mix(inner, outer, t ** 1.4)
        d.ellipse([cx * K - i, cy * K - i, cx * K + i, cy * K + i], fill=rgba(c))
    d.ellipse([(cx - r * .45) * K - K * .9, (cy - r * .45) * K - K * .9, (cx - r * .45) * K + K * .9, (cy - r * .45) * K + K * .9], fill=rgba(hi))

def poly(d, pts, c): d.polygon([(x * K, y * K) for x, y in pts], fill=rgba(c))

def shade_poly(img, pts, light, dark, axis=(1, 1)):
    """Fill polygon with a diagonal light->dark gradient (painted look)."""
    mask = Image.new("L", (N, N), 0); ImageDraw.Draw(mask).polygon([(x * K, y * K) for x, y in pts], fill=255)
    grad = Image.new("RGBA", (N, N))
    gp = grad.load()
    for y in range(N):
        for x in range(N):
            t = (x * axis[0] + y * axis[1]) / (N * (abs(axis[0]) + abs(axis[1])))
            gp[x, y] = rgba(mix(light, dark, max(0, min(1, t))))
    img.paste(grad, (0, 0), mask)

def sparkle(img, x, y, c=P["white"]):
    px = img.load()
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= x + dx < S and 0 <= y + dy < S: px[x + dx, y + dy] = rgba(c if (dx, dy) == (0, 0) else mix(c, P["glow"], .5))

# ---------------------------------------------------------------- items
def spirit_light():
    im = premult_canvas(); d = ImageDraw.Draw(im); orb(d, 8, 8.5, 5.2, P["white"], P["sky"]); im = finish_item(im, P["deep"])
    sparkle(im, 12, 3); return im

def life_cell():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    shade_poly(im, [(8, 1.5), (13.5, 7), (8, 14.5), (2.5, 7)], mix(P["white"], P["leaf"], .3), P["moss"])
    poly(d, [(8, 3.5), (11, 7), (8, 11), (5, 7)], mix(P["leaf"], P["white"], .5)); d.ellipse([7 * K, 5 * K, 9 * K, 7 * K], fill=rgba(P["white"]))
    return finish_item(im, P["night"])

def energy_cell():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    shade_poly(im, [(8, 1), (13, 5), (13, 11), (8, 15), (3, 11), (3, 5)], P["glow"], P["teal"])
    poly(d, [(8, 3.5), (10.5, 6), (10.5, 10), (8, 12.5), (5.5, 10), (5.5, 6)], mix(P["sky"], P["white"], .55))
    poly(d, [(8, 3.5), (5.5, 6), (5.5, 7.5), (8, 5.5)], P["white"])
    return finish_item(im, P["night"])

def keystone():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    shade_poly(im, [(4, 2), (12, 2), (14, 8), (8, 15), (2, 8)], mix(P["slate"], P["pale"], .4), P["stone"])
    d.ellipse([5 * K, 5 * K, 11 * K, 11 * K], fill=rgba(P["gold"])); d.ellipse([6.5 * K, 5.5 * K, 11.5 * K, 10.5 * K], fill=rgba(mix(P["slate"], P["pale"], .3)))
    im = finish_item(im, P["night"]); sparkle(im, 7, 6, P["white"]); return im

def map_fragment():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    poly(d, [(2, 3), (7, 2), (9, 4), (14, 2.5), (13.5, 9), (14.5, 13.5), (9, 14), (6, 12.5), (2.5, 14), (3, 8)], P["parch"])
    shade_poly(im, [(9, 4), (14, 2.5), (13.5, 9), (14.5, 13.5), (9, 14)], mix(P["parch"], P["sand"], .3), P["dune"], (1, 0))
    im = finish_item(im, P["bark"]); px = im.load()
    for x, y in ((4, 6), (5, 7), (6, 7), (7, 8), (8, 9), (9, 9), (10, 10)): px[x, y] = rgba(P["ink"])
    px[11, 6] = rgba(P["gold"]); px[11, 11] = rgba(P["violet"]); return im

def ruin_guide():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    shade_poly(im, [(3, 2), (13, 2), (13, 14), (3, 14)], P["slate"], P["stone"])
    im = finish_item(im, P["night"]); px = im.load()
    for x, y in ((8, 4), (8, 5), (6, 6), (10, 6), (7, 7), (9, 7), (8, 8), (8, 9), (6, 10), (10, 10), (8, 11)): px[x, y] = rgba(P["glow"])
    px[8, 8] = rgba(P["white"]); return im

def spirit_core():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    d.ellipse([1.5 * K, 5.5 * K, 14.5 * K, 10.5 * K], outline=rgba(P["sky"]), width=K)
    orb(d, 8, 8, 4.2, P["white"], P["glow"])
    return finish_item(im, P["deep"])

def blade():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    shade_poly(im, [(14.5, 1.5), (15, 3), (6.5, 11.5), (4.5, 9.5), (13, 1)], P["white"], P["sky"], (-1, 1))
    poly(d, [(3, 9), (7, 13), (6, 14), (2, 10)], P["teal"]); poly(d, [(1.5, 12.5), (3.5, 14.5), (2.5, 15.5), (0.5, 13.5)], P["gold"])
    poly(d, [(3, 12), (4.5, 13.5), (3.5, 14.5), (2, 13)], P["deep"])
    return finish_item(im, P["night"])

def bow(stage=0):
    im = premult_canvas(); d = ImageDraw.Draw(im)
    pull = (0, 1.2, 2.2, 3)[stage]
    d.arc([1 * K, 1 * K, 15 * K, 15 * K], 180 + 10, 270 + 80, fill=rgba(P["glow"]), width=int(1.6 * K))
    d.arc([1.6 * K, 1.6 * K, 14.4 * K, 14.4 * K], 190, 340, fill=rgba(P["white"]), width=int(.6 * K))
    a, b = (2.6, 13.0), (13.0, 2.6); m = (7.8 - pull * .7, 7.8 - pull * .7)
    d.line([(a[0] * K, a[1] * K), (m[0] * K, m[1] * K), (b[0] * K, b[1] * K)], fill=rgba(P["gold"]), width=K)
    if stage:
        d.line([(m[0] * K, m[1] * K), ((m[0] + 7) * K, (m[1] + 7) * K)], fill=rgba(P["white"]), width=K)
    return finish_item(im, P["deep"])

def hammer():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    d.line([(2.5 * K, 13.5 * K), (10 * K, 6 * K)], fill=rgba(P["teal"]), width=int(1.7 * K))
    shade_poly(im, [(8, 1.5), (14.5, 8), (12, 10.5), (5.5, 4)], P["white"], P["sky"], (-1, 1))
    poly(d, [(9, 4.5), (11.5, 7), (10.5, 8), (8, 5.5)], P["gold"])
    return finish_item(im, P["night"])

def spear():
    im = premult_canvas(); d = ImageDraw.Draw(im)
    d.line([(1.5 * K, 14.5 * K), (10.5 * K, 5.5 * K)], fill=rgba(P["teal"]), width=int(1.4 * K))
    shade_poly(im, [(15, 1), (13.5, 6), (11.5, 6.5), (9.5, 4.5), (10, 2.5)], P["white"], P["glow"], (-1, 1))
    poly(d, [(9, 6), (10, 7), (8.5, 8.5), (7.5, 7.5)], P["gold"])
    return finish_item(im, P["night"])

# ---------------------------------------------------------------- blocks (opaque tiles)
def noise_tile(seed, base, dark, light, grain=.18, blobs=6, amount=.75):
    r = random.Random(seed); im = Image.new("RGBA", (S, S)); px = im.load()
    centers = [(r.uniform(0, S), r.uniform(0, S), r.uniform(2, 5)) for _ in range(blobs)]
    wrap = lambda d: (d + S / 2) % S - S / 2           # tileable
    vals = [[sum(math.exp(-(wrap(x - cx) ** 2 + wrap(y - cy) ** 2) / (2 * rr * rr)) for cx, cy, rr in centers) for x in range(S)] for y in range(S)]
    lo = min(map(min, vals)); hi = max(map(max, vals)) or 1
    for y in range(S):
        for x in range(S):
            t = max(-1, min(1, ((vals[y][x] - lo) / (hi - lo + 1e-9) * 2 - 1) * amount + r.uniform(-grain, grain)))
            px[x, y] = rgba(mix(base, light, t) if t > 0 else mix(base, dark, -t))
    return im

def bricks(seed, base, dark, light, rows=4):
    im = noise_tile(seed, base, dark, light, .12, 5); px = im.load(); h = S // rows
    for row in range(rows):
        y0 = row * h; off = (S // 4) if row % 2 else 0
        for x in range(S): px[x, y0] = rgba(mix(dark, P["night"], .3))
        for x in (off % S, (off + S // 2) % S):
            for y in range(y0, y0 + h): px[x, y] = rgba(mix(dark, P["night"], .3))
        for x in range(S): 
            if y0 + 1 < S: px[x, y0 + 1] = rgba(mix(px[x, y0 + 1][:3], light, .35))
    return im

def rune(im, pts, c, glow=None):
    px = im.load()
    if glow:
        for x, y in pts:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < S and 0 <= y + dy < S and (x + dx, y + dy) not in pts: px[x + dx, y + dy] = rgba(mix(px[x + dx, y + dy][:3], glow, .45))
    for x, y in pts: px[x, y] = rgba(c)
    return im

def ring(cx=7.5, cy=7.5, r=4.5):
    return [(x, y) for y in range(S) for x in range(S) if abs(math.hypot(x - cx, y - cy) - r) < .55]

def frame(im, c, inset=0):
    px = im.load()
    for i in range(inset, S - inset):
        for x, y in ((i, inset), (i, S - 1 - inset), (inset, i), (S - 1 - inset, i)): px[x, y] = rgba(c)
    return im

def glow_tile(seed, core, edge):
    im = Image.new("RGBA", (S, S)); px = im.load(); r = random.Random(seed)
    for y in range(S):
        for x in range(S):
            t = min(1, math.hypot(x - 7.5, y - 7.5) / 9) + r.uniform(-.06, .06)
            px[x, y] = rgba(mix(core, edge, max(0, min(1, t))))
    return im

def blocks():
    st, sl, pa = P["stone"], P["slate"], P["pale"]
    out = {}
    out["ruin_stone"] = bricks(1, sl, st, pa)
    out["ruin_stone_dark"] = noise_tile(2, st, mix(st, P["night"], .5), sl)
    for name, accent, seed in (("forest", P["leaf"], 3), ("creek", P["sky"], 4), ("cave", P["amber"], 5)):
        top = frame(noise_tile(seed, sl, st, pa, .1), mix(st, P["night"], .4))
        out[f"{name}_shrine_top"] = rune(top, ring() + [(7, 7), (8, 7), (7, 8), (8, 8)], accent, accent)
        side = bricks(seed + 10, sl, st, pa)
        out[f"{name}_shrine_side"] = rune(side, [(7, y) for y in range(5, 12)] + [(8, y) for y in range(5, 12)], accent, accent)
    out["shrine_bottom"] = noise_tile(9, st, mix(st, P["night"], .5), sl)
    out["pedestal_base"] = frame(noise_tile(11, mix(pa, sl, .3), sl, pa, .08, amount=.5), st)
    out["pedestal_top"] = rune(frame(noise_tile(12, mix(pa, sl, .3), sl, pa, .08, amount=.5), st), ring(7.5, 7.5, 5.5), P["glow"])
    out["gem_life"] = glow_tile(13, mix(P["white"], P["leaf"], .3), P["moss"])
    out["gem_energy"] = glow_tile(14, mix(P["white"], P["sky"], .3), P["teal"])
    out["gem_keystone"] = glow_tile(15, mix(P["white"], P["gold"], .3), P["amber"])
    out["keystone_door"] = rune(frame(bricks(16, sl, st, pa, 2), mix(st, P["night"], .4)), [(x, y) for x, y in ring(7.5, 7.5, 3.5)], P["gold"], P["gold"])
    out["keystone_door_half"] = frame(bricks(17, sl, st, pa, 2), mix(st, P["night"], .4))
    out["map_stone_top"] = rune(frame(noise_tile(18, sl, st, pa, .1), st), ring(7.5, 7.5, 5) + [(7, y) for y in range(2, 14)] + [(x, 7) for x in range(2, 14)], P["glow"], P["sky"])
    out["map_stone_side"] = rune(bricks(19, sl, st, pa), [(4, 5), (5, 6), (6, 6), (7, 7), (8, 8), (9, 8), (10, 9), (11, 10)], P["glow"], P["sky"])
    out["spirit_sand"] = noise_tile(20, P["sand"], P["dune"], mix(P["sand"], P["white"], .5), .12, 9, amount=.8)
    rune(out["spirit_sand"], [(3, 4), (11, 2), (6, 11), (13, 12)], mix(P["glow"], P["white"], .4))
    out["storm_altar_top"] = rune(frame(noise_tile(21, P["deep"], P["night"], P["teal"], .1), P["night"]), [(9, 2), (8, 3), (7, 4), (6, 5), (7, 6), (8, 7), (9, 8), (8, 9), (7, 10), (6, 11), (5, 12)], P["glow"], P["sky"])
    out["storm_altar_side"] = rune(bricks(22, mix(sl, P["deep"], .4), st, pa), [(x, 3) for x in range(3, 13)], P["sky"])
    out["gloom_altar_top"] = rune(frame(noise_tile(23, mix(P["gloom"], P["night"], .4), P["night"], P["violet"], .15), P["night"]), ring(7.5, 7.5, 4), mix(P["violet"], P["white"], .3), P["violet"])
    out["gloom_altar_side"] = bricks(24, mix(st, P["gloom"], .45), P["night"], mix(P["violet"], sl, .5))
    out["gloom_water"] = rune(noise_tile(25, P["gloom"], mix(P["gloom"], P["night"], .6), P["violet"], .1, 8), [(3, 4), (4, 4), (5, 5), (10, 9), (11, 9), (12, 10), (6, 12), (7, 12)], mix(P["violet"], P["white"], .35))
    out["gloom_water_side"] = noise_tile(26, mix(P["gloom"], P["night"], .4), P["night"], P["gloom"], .2, 6)
    out["updraft_top"] = rune(glow_tile(27, P["white"], P["teal"]), ring(7.5, 7.5, 6), P["slate"])
    out["updraft_side"] = rune(bricks(28, mix(sl, P["teal"], .3), st, pa, 2), [(x, 12) for x in range(S)], P["glow"])
    out["bash_anchor_core"] = glow_tile(29, P["white"], P["sky"])
    out["bash_anchor_accent"] = noise_tile(30, P["teal"], P["deep"], P["sky"], .1, amount=.6)
    out["grapple_flower_core"] = glow_tile(31, mix(P["white"], P["leaf"], .4), P["leaf"])
    out["grapple_flower_accent"] = noise_tile(32, P["moss"], mix(P["moss"], P["night"], .5), P["leaf"], .1, amount=.6)
    return out

ITEMS = dict(spirit_light=spirit_light, life_cell=life_cell, energy_cell=energy_cell, keystone=keystone, map_fragment=map_fragment,
             ruin_guide=ruin_guide, spirit_core=spirit_core, spirit_blade=blade, spirit_hammer=hammer, spirit_spear=spear,
             spirit_bow=lambda: bow(0), spirit_bow_pulling_0=lambda: bow(1), spirit_bow_pulling_1=lambda: bow(2), spirit_bow_pulling_2=lambda: bow(3))

def main():
    (ROOT / "item").mkdir(parents=True, exist_ok=True); (ROOT / "block").mkdir(parents=True, exist_ok=True)
    for name, fn in ITEMS.items(): fn().save(ROOT / "item" / f"{name}.png")
    for name, im in blocks().items(): im.save(ROOT / "block" / f"{name}.png")
    print(len(ITEMS), "items,", len(blocks()), "blocks")

if __name__ == "__main__": main()
