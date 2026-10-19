"""FX02: generate the spirit particle sprites procedurally (reproducible, no external art).
Writes src/main/resources/assets/orimod/textures/particle/spirit_mote_{0..3}.png (8x8 RGBA, soft
round glow shrinking per frame) and spirit_spark_{0..1}.png (4-point star). Grayscale-white so the
particle colour tints them. Usage: python3 scripts/make-spirit-particles.py [--check]"""
import io, math, pathlib, sys
from PIL import Image
out = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/assets/orimod/textures/particle'

def mote(radius):
    img = Image.new('RGBA', (8, 8))
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + .5 - 4, y + .5 - 4) / radius
            a = max(0.0, 1 - d) ** 1.6
            core = max(0.0, 1 - d * 2.2)
            v = int(round(200 + 55 * core)); img.putpixel((x, y), (v, v, 255, int(round(255 * min(1, a * 1.15)))))
    return img

def spark(size):
    img = Image.new('RGBA', (8, 8))
    for y in range(8):
        for x in range(8):
            dx, dy = abs(x + .5 - 4), abs(y + .5 - 4)
            a = max(0.0, 1 - min(dx, dy) * 1.4) * max(0.0, 1 - max(dx, dy) / size)
            img.putpixel((x, y), (255, 255, 255, int(round(255 * a))))
    return img

files = {f'spirit_mote_{i}.png': mote(r) for i, r in enumerate([3.9, 3.4, 2.8, 2.1])}
files |= {f'spirit_spark_{i}.png': spark(s) for i, s in enumerate([4.0, 2.8])}
stale = []
for name, img in files.items():
    buf = io.BytesIO(); img.save(buf, 'PNG', optimize=False); data = buf.getvalue()
    target = out / name
    if not target.exists() or target.read_bytes() != data:
        stale.append(name)
        if '--check' not in sys.argv: out.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
if '--check' in sys.argv and stale: print('stale sprites:', stale); sys.exit(1)
print(len(files), 'sprites', 'checked' if '--check' in sys.argv else 'written', stale)
