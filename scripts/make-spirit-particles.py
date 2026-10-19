"""FX02: generate the spirit particle sprites procedurally (reproducible, no external art).
Writes src/main/resources/assets/orimod/textures/particle/spirit_mote_{0..3}.png (16x16 RGBA since R32:
bright core inside a soft halo, shrinking per frame) and spirit_spark_{0..1}.png (4-point star). Grayscale-white so the
particle colour tints them. Usage: python3 scripts/make-spirit-particles.py [--check]"""
import io, math, pathlib, sys
from PIL import Image
out = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/assets/orimod/textures/particle'

N = 16
def mote(core_r):
    img = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            r = math.hypot(x + .5 - N / 2, y + .5 - N / 2)
            core = max(0.0, 1 - r / core_r) ** .8                    # solid bright centre
            halo = math.exp(-(r / (N * .26)) ** 2) * .55 * max(0.0, 1 - r / (N / 2))  # soft glow to the edge
            a = min(1.0, core + halo)
            img.putpixel((x, y), (255, 255, 255, int(round(255 * a))))
    return img

def spark(size):
    img = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x + .5 - N / 2), abs(y + .5 - N / 2)
            ray = max(0.0, 1 - min(dx, dy) * .7) * max(0.0, 1 - max(dx, dy) / size)
            glow = max(0.0, 1 - math.hypot(dx, dy) / (size * .45))
            img.putpixel((x, y), (255, 255, 255, int(round(255 * min(1.0, ray + glow)))))
    return img

files = {f'spirit_mote_{i}.png': mote(r) for i, r in enumerate([4.2, 3.6, 3.0, 2.3])}
files |= {f'spirit_spark_{i}.png': spark(s) for i, s in enumerate([8.0, 5.6])}
stale = []
for name, img in files.items():
    buf = io.BytesIO(); img.save(buf, 'PNG', optimize=False); data = buf.getvalue()
    target = out / name
    if not target.exists() or target.read_bytes() != data:
        stale.append(name)
        if '--check' not in sys.argv: out.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
if '--check' in sys.argv and stale: print('stale sprites:', stale); sys.exit(1)
print(len(files), 'sprites', 'checked' if '--check' in sys.argv else 'written', stale)
