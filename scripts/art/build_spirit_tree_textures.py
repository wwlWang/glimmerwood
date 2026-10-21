"""R100: original spirit-tree bark, porous leaves, cold hanging vines and root hearts; deterministic standard-library PNGs."""
from pathlib import Path
import argparse
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/orimod/textures/block"
SIZE = 16


def noise(x, y):
    return ((x * 374761393 ^ y * 668265263) * 1274126177 & 0xffffffff) / 0xffffffff


def rgba(rgb, alpha=255):
    return tuple(max(0, min(255, round(v))) for v in rgb) + (alpha,)


def bark(x, y):
    grain = 12 * math.sin(x * 1.7 + math.sin(y * .21)) + (noise(x, y) - .5) * 18
    seam = min(abs((x - (7 + 2 * math.sin(y * .3))) % 16),
               abs((x - (7 + 2 * math.sin(y * .3))) % 16 - 16))
    if seam < .8:
        return rgba((190, 238, 255))
    if seam < 1.7:
        return rgba((96, 168, 198))
    if (x + y // 7) % 11 == 0:
        grain -= 25
    return rgba((160 + grain, 175 + grain, 181 + grain))


def end(x, y):
    distance = math.hypot(x - 15.5, y - 15.5)
    ring = math.sin(distance * 2.4) * 13
    if abs(x - 15.5 - 1.3 * math.sin(y * .3)) < .9:
        return rgba((185, 236, 251))
    return rgba((167 + ring, 185 + ring, 191 + ring))


def leaves(x, y):
    # Separate pointed leaflets and fine stems leave open sky between them.
    for cx, cy, angle in [(7, 8, -.65), (20, 6, .65), (24, 19, .6), (10, 24, -.45), (12, 15, .3)]:
        dx, dy = x - cx, y - cy
        u, v = dx * math.cos(angle) + dy * math.sin(angle), -dx * math.sin(angle) + dy * math.cos(angle)
        if abs(u) / 8 + (v / 4) ** 2 < 1:
            light = 18 * (1 - abs(v) / 4) + 12 * noise(x, y)
            return rgba((105 + light, 185 + light, 216 + light), 210)
    if abs(x - (15 + math.sin(y * .2) * 3)) < .65:
        return rgba((170, 224, 242), 220)
    return (0, 0, 0, 0)


def bloomed_leaves(x, y):
    # Retain every original cutout hole. Four 2x2-pixel flowers (on the output
    # 16x16 grid) punctuate a saturated cyan canopy, rather than bleaching it.
    base = leaves(x, y)
    if not base[3]:
        return base
    px, py = x // 2, y // 2
    for cx, cy in ((2, 3), (10, 3), (6, 8), (12, 10)):
        if cx <= px < cx+2 and cy <= py < cy+2:
            return rgba((255, 236, 171) if (px, py) == (cx+1, cy+1) else (238, 255, 255), base[3])
    light = base[2]-216
    return rgba((80+light*.3, 230+light*.5, 252+light*.1), base[3])


def vine(x, y, tip):
    # A slender wavy stem and alternating pointed blue-white leaflets; no warm berry shapes.
    stem = 16 + 2 * math.sin(y * math.pi / 16)
    if (not tip or y < 24) and abs(x - stem) <= 1.1:
        return rgba((167, 226, 251))
    for i, cy in enumerate((5, 13, 21) if tip else (5, 13, 21, 29)):
        direction = -1 if i % 2 == 0 else 1
        cx = 16 + 2 * math.sin(cy * math.pi / 16) + direction * 4
        dx, dy = x - cx, y - cy
        u, v = dx * direction, dy - dx * direction * .5
        if abs(u) / 6 + (v / 3.5) ** 2 < 1:
            brightness = 18 * (1 - abs(v) / 3.5)
            return rgba((146 + brightness, 213 + brightness, 235 + brightness))
    # The last leaflet is a pointed droplet of light, still attached to the stem.
    if tip and 22 <= y <= 30 and abs(x - 15) < (31 - y) * .45:
        return rgba((222, 247, 255))
    return (0, 0, 0, 0)


def heart(x, y, bloomed):
    dx, dy = (x - 15.5) / 15.5, (y - 15.5) / 15.5
    radius, angle = math.hypot(dx, dy), math.atan2(dy, dx)
    petal_edge = .51 + .15 * math.cos(angle * 5)
    if radius < petal_edge:
        base = (235, 250, 255) if bloomed else (115, 199, 232)
        return rgba(tuple(c * (1 - radius * .2) for c in base))
    if radius < petal_edge + .09:
        return rgba((164, 231, 252) if bloomed else (72, 143, 183))
    return bark(x, y)


def png(pixel):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    # Sample the 32-unit drawing grid into the project's 16x16 block-texture format.
    raw = b"".join(b"\0" + bytes(c for x in range(SIZE) for c in pixel(x * 2, y * 2)) for y in range(SIZE))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    images = {"spirit_log": bark, "spirit_log_top": end, "spirit_leaves": leaves,
              "spirit_leaves_bloomed": bloomed_leaves,
              "spirit_vine": lambda x, y: vine(x, y, False),
              "spirit_vine_tip": lambda x, y: vine(x, y, True),
              "spirit_tree_heart": lambda x, y: heart(x, y, False),
              "spirit_tree_heart_bloomed": lambda x, y: heart(x, y, True)}
    if not args.check:
        OUT.mkdir(parents=True, exist_ok=True)
    for name, pixel in images.items():
        path, data = OUT / (name + ".png"), png(pixel)
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                raise SystemExit(f"Stale spirit tree texture: {path}")
        else:
            path.write_bytes(data)
        print(f'{"Verified" if args.check else "Generated"} {path.relative_to(ROOT)}')


if __name__ == "__main__":
    main()
