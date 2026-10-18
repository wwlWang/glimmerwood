#!/usr/bin/env python3
"""Generate Spirit Glow's colour-grading LUT from the pack's identity LUT.

Every pixel of img/luts/neutral.png stores its own input colour, so grading the
pixels with grade() yields a LUT that applies grade() on screen. Deterministic:
same input -> byte-identical output. Usage: make-spirit-lut.py [pack_dir]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PACK = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "shaderpacks/spirit-glow")
SRC = PACK / "shaders/img/luts/neutral.png"
DST = PACK / "shaders/img/luts/spirit.png"

SHADOW_TINT = np.array([-0.008, 0.014, 0.040])   # colored darks: blue-teal instead of grey, without crushing dark reds
HIGHLIGHT_TINT = np.array([0.015, 0.025, 0.010])  # soft luminous highlights, slightly cyan-warm
SATURATION = 1.04
TEAL_SHIFT_DEG = 8.0                              # pull foliage greens a little towards teal
CONTRAST = 0.06


def rgb_to_hsv(c):
    r, g, b = c[..., 0], c[..., 1], c[..., 2]
    mx, mn = c.max(-1), c.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    m = d > 1e-6
    rc = np.where(m, (mx - r) / np.where(m, d, 1), 0)
    gc = np.where(m, (mx - g) / np.where(m, d, 1), 0)
    bc = np.where(m, (mx - b) / np.where(m, d, 1), 0)
    h = np.where(mx == r, bc - gc, np.where(mx == g, 2 + rc - bc, 4 + gc - rc))
    h = np.where(m, (h / 6) % 1, 0)
    s = np.where(mx > 1e-6, d / np.where(mx > 1e-6, mx, 1), 0)
    return np.stack([h, s, mx], -1)


def hsv_to_rgb(c):
    h, s, v = c[..., 0], c[..., 1], c[..., 2]
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    sel = [np.stack(x, -1) for x in ((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))]
    out = np.zeros_like(c)
    for k in range(6):
        out = np.where((i == k)[..., None], sel[k], out)
    return out


def grade(c):
    luma = c @ np.array([0.2126, 0.7152, 0.0722])
    # gentle S-curve on the upper half only, so darks are tinted rather than crushed
    c = c + CONTRAST * np.maximum(c - 0.5, 0) * (1 - np.abs(2 * c - 1))
    # tint fades out towards pure black so very dark materials keep their own hue
    w_shadow = ((1 - luma) ** 2 * np.clip(luma / 0.08, 0.35, 1))[..., None]
    w_high = (luma ** 3)[..., None]
    c = c + w_shadow * SHADOW_TINT + w_high * HIGHLIGHT_TINT
    hsv = rgb_to_hsv(np.clip(c, 0, 1))
    hue_deg = hsv[..., 0] * 360
    green = np.clip(1 - np.abs(hue_deg - 110) / 50, 0, 1)  # 60..160 deg
    hsv[..., 0] = ((hue_deg + green * TEAL_SHIFT_DEG) / 360) % 1
    hsv[..., 1] = np.clip(hsv[..., 1] * SATURATION, 0, 1)
    return np.clip(hsv_to_rgb(hsv), 0, 1)


def main():
    src = np.asarray(Image.open(SRC).convert("RGB"), dtype=np.float64) / 255.0
    out = np.round(grade(src) * 255).astype(np.uint8)
    Image.fromarray(out, "RGB").save(DST, optimize=False, compress_level=9)
    print(f"wrote {DST} {out.shape}")


if __name__ == "__main__":
    main()
