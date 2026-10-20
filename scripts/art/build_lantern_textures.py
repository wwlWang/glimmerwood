"""R96: original 16x16 amethyst lantern textures. Standard library only; --check verifies reproducibility."""
from pathlib import Path
import argparse
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/orimod/textures/block"


def png(pixel):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    raw = b"".join(b"\0" + bytes(channel for x in range(16) for channel in (*pixel(x, y), 255)) for y in range(16))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def frame(x, y):
    facet = ((x * 3 + y * 5) % 11 - 5) * 2
    edge = 22 if x in (1, 14) or y in (1, 14) else 0
    if (x, y) in ((4, 3), (11, 9), (5, 12)):
        return (139, 119, 180)
    return tuple(max(0, min(255, c + facet + edge)) for c in (53, 37, 76))


def core(x, y, lit):
    distance = math.hypot(x - 7.5, y - 7.5) / 10.61
    shade = 1 - .27 * max(0, distance - .2)
    rgb = (242, 248, 255) if lit else (95, 134, 184)  # Unlit #5F86B8; visibly colder/darker than the lit core.
    return tuple(round(c * shade) for c in rgb)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    images = {
        "spirit_lantern_frame.png": png(frame),
        "spirit_lantern_core.png": png(lambda x, y: core(x, y, False)),
        "spirit_lantern_core_lit.png": png(lambda x, y: core(x, y, True)),
    }
    if not args.check:
        OUT.mkdir(parents=True, exist_ok=True)
    for name, data in images.items():
        path = OUT / name
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                raise SystemExit(f"Stale lantern texture: {path}")
        else:
            path.write_bytes(data)
        print(f'{"Verified" if args.check else "Generated"} {path.relative_to(ROOT)}')


if __name__ == "__main__":
    main()
