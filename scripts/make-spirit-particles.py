"""FX02: generate the spirit particle sprites procedurally (reproducible, no external art).
Writes src/main/resources/assets/orimod/textures/particle/spirit_mote_{0..3}.png (R88: 32x32, 4x supersampled
gaussian light with no hard core edge, so motes read as soft light instead of blocky blobs; shrinking per frame),
spirit_spark_{0..1}.png (4-point star) and, for the body aura and condensed-energy weapons, textures/misc/soft_glow.png
(64x64 gaussian) and textures/misc/energy_streak.png (16x64: bright spine fading to soft edges and ends). Grayscale-white so the
particle colour tints them. Usage: python3 scripts/make-spirit-particles.py [--check]"""
import io, math, pathlib, sys
from PIL import Image
out = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/assets/orimod/textures/particle'

N = 32
SS = 4                                                            # supersampling per axis

def _render(n, w, h, fn):
    img = Image.new('RGBA', (w, h))
    for y in range(h):
        for x in range(w):
            a = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    a += fn(x + (sx + .5) / SS, y + (sy + .5) / SS)
            a = min(1.0, max(0.0, a / (SS * SS)))
            img.putpixel((x, y), (255, 255, 255, int(round(255 * a))))
    return img

def mote(core):
    """core: relative size of the bright centre (1 = largest frame). Gaussian core + wide soft halo, zero at the edge."""
    def f(px, py):
        r = math.hypot(px - N / 2, py - N / 2) / (N / 2)          # 0 centre .. 1 edge
        edge = max(0.0, 1 - r) ** 1.5
        return (math.exp(-(r / (.24 * core)) ** 2) * .95 + math.exp(-(r / .55) ** 2) * .45) * edge
    return _render(N, N, N, f)

def spark(size):
    def f(px, py):
        dx, dy = abs(px - N / 2) / (N / 2), abs(py - N / 2) / (N / 2)
        ray = math.exp(-(min(dx, dy) / .05) ** 2) * max(0.0, 1 - max(dx, dy) / size) ** 1.4
        glow = math.exp(-(math.hypot(dx, dy) / (size * .22)) ** 2)
        return ray * .9 + glow
    return _render(N, N, N, f)

def soft_glow():
    M = 64
    def f(px, py):
        r = math.hypot(px - M / 2, py - M / 2) / (M / 2)
        return math.exp(-(r / .45) ** 2) * max(0.0, 1 - r) ** 1.2
    return _render(M, M, M, f)

def energy_streak():
    W, H = 16, 64
    def f(px, py):
        u = abs(px - W / 2) / (W / 2); v = py / H                 # u across, v along
        along = math.sin(math.pi * min(1.0, max(0.0, v))) ** .6
        return (math.exp(-(u / .16) ** 2) * .9 + math.exp(-(u / .55) ** 2) * .45) * along * max(0.0, 1 - u) ** .8
    return _render(W, W, H, f)

files = {f'spirit_mote_{i}.png': mote(c) for i, c in enumerate([1.0, .85, .7, .55])}
files |= {f'spirit_spark_{i}.png': spark(s) for i, s in enumerate([1.0, .7])}
files |= {'../misc/soft_glow.png': soft_glow(), '../misc/energy_streak.png': energy_streak()}
stale = []
for name, img in files.items():
    buf = io.BytesIO(); img.save(buf, 'PNG', optimize=False); data = buf.getvalue()
    target = out / name
    if not target.exists() or target.read_bytes() != data:
        stale.append(name)
        if '--check' not in sys.argv: target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
if '--check' in sys.argv and stale: print('stale sprites:', stale); sys.exit(1)
print(len(files), 'sprites', 'checked' if '--check' in sys.argv else 'written', stale)
