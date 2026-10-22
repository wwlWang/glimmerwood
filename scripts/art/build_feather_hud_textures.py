"""R113 sprites: Kuro's Feather item icon (the elytra in spirit form) and the spirit-form HUD life / energy cells.

Run: python3 scripts/art/build_feather_hud_textures.py
Writes (all original, stdlib only, matching the navy-outlined soft-gradient style of life_cell / energy_cell):
  assets/orimod/textures/item/kuro_feather.png, kuro_feather_broken.png    16x16 feather, quill bottom-left
                                         (the elytra's look in spirit form; human form keeps the vanilla elytra)
  assets/orimod/textures/gui/sprites/hud/{life,energy}_{full,half,empty}.png  9x9 cells (vanilla heart size)
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets'
OUTLINE = (24, 38, 66)


def png(pixels, size):
    w, h = size
    raw = b''.join(b'\x00' + b''.join(bytes(pixels[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def lerp(a, b, t): return tuple(round(x + (y - x) * max(0, min(1, t))) for x, y in zip(a, b))


def save(path, pixels, size):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(png(pixels, size))


def feather(dim=1.0):
    """Diagonal feather: quill from (2.5,13.5) to the tip at (13.5,2.5); vane wider on the lower-right (trailing) side."""
    S = 16
    ax, ay, bx, by = 2.5, 13.5, 13.2, 2.8
    L = math.hypot(bx - ax, by - ay); ux, uy = (bx - ax) / L, (by - ay) / L; nx, ny = -uy, ux
    inside = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            px, py = x + .5 - ax, y + .5 - ay
            s = (px * ux + py * uy) / L          # 0 at the quill end, 1 at the tip
            d = px * nx + py * ny                 # signed distance from the quill (+ = upper-left, leading side)
            if not 0 <= s <= 1.02: continue
            if s < .22:
                if abs(d) < .55: inside[y][x] = ('quill', s, 0)
                continue
            u = (s - .22) / .8
            half = 3.1 * math.sin(math.pi * min(1, u) ** .75) ** .8
            lead, trail = .75 * half, 1.15 * half
            notch = abs(s - .55) < .05 and d < -trail * .55
            if -trail <= d <= lead and not notch:
                inside[y][x] = ('vane', s, d / (lead if d > 0 else trail))
    pixels = [[(0, 0, 0, 0)] * S for _ in range(S)]
    white, blue, edge_blue, quill = (244, 248, 252), (176, 214, 246), (120, 172, 236), (150, 196, 240)
    for y in range(S):
        for x in range(S):
            cell = inside[y][x]
            if cell is None:
                if any(0 <= y + j < S and 0 <= x + i < S and inside[y + j][x + i] for i, j in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    pixels[y][x] = (*OUTLINE, 255)
                continue
            kind, s, side = cell
            if kind == 'quill': c = quill
            else:
                c = lerp(white, blue, .25 + .45 * abs(side) + .35 * max(0, s - .75) / .25)
                if abs(side) > .82: c = lerp(c, edge_blue, .6)
                if abs((x + .5 - ax) * nx + (y + .5 - ay) * ny) < .5: c = quill   # the shaft through the vane
                if side > 0 and s < .7: c = lerp(c, (255, 255, 255), .35)          # light catching the leading side
            pixels[y][x] = (*(round(v * dim) for v in c), 255)
    return pixels


def cell(kind, fill):
    """9x9 orb: dark outline, soft gradient core; fill 1 = full, .5 = left half, 0 = empty container."""
    S = 9
    hi, lo = ((214, 250, 216), (74, 178, 104)) if kind == 'life' else ((206, 238, 255), (64, 140, 232))
    pixels = [[(0, 0, 0, 0)] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            dx, dy = x - 4, y - 4
            r = math.hypot(dx * .95, dy)
            if r > 4.35: continue
            if r > 3.35: pixels[y][x] = (*OUTLINE, 255); continue
            lit = fill >= 1 or (fill > 0 and x <= 4)
            if not lit: pixels[y][x] = (32, 46, 72, 200); continue
            t = max(0, min(1, (r / 3.35) * .7 + (dy + 3) / 7 * .4))
            c = lerp(hi, lo, t)
            if (x, y) in ((3, 2), (2, 3)): c = (255, 255, 255)   # specular glint
            pixels[y][x] = (*c, 255)
    return pixels


save(ROOT / 'orimod/textures/item/kuro_feather.png', feather(), (16, 16))
save(ROOT / 'orimod/textures/item/kuro_feather_broken.png', feather(.8), (16, 16))
for kind in ('life', 'energy'):
    for name, fill in (('full', 1), ('half', .5), ('empty', 0)):
        save(ROOT / f'orimod/textures/gui/sprites/hud/{kind}_{name}.png', cell(kind, fill), (9, 9))
print('ok')
