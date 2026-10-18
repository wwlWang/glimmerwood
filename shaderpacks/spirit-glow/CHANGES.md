# Spirit Glow changes relative to Mellow 3ad9445

1. Renamed in the option screen and language files (en_US, zh_CN, fr_FR); the INFO line credits the
   original author. The custom-sky comment no longer names the original pack.
2. Removed the `mellow.png` colour LUT; LUT slot 9 now points at `img/luts/spirit.png`, generated from
   the pack's identity LUT by `scripts/shader/make-spirit-lut.py` in the oriMod repository.
3. New default colour scheme 6 "Spirit" in `lib/colors.glsl` (schemes 1–5 unchanged and still selectable):
   blue moonlight and brighter night ambient so nights stay playable, teal-tinted sky top and night
   horizon, violet-pink dusk, teal water. Language entries `value.COLOR_SCHEME.6`.
4. Changed defaults in `lib/settings.glsl`: colour LUT on with `spirit.png` (blue-teal darks that fade
   out near black, foliage greens pulled slightly towards teal, mild saturation and highlight contrast),
   atmospheric fog 0.25 → 0.5 for depth layering, bloom 0.4 → 0.55 for soft glow, minimum light
   0.15 → 0.2 for readable caves, vignette 0 → 0.5, dynamic shadows on at 2048 with entity shadow
   distance 0.5 (the preset previously verified on Iris 1.8.12), hand-held lights on.
