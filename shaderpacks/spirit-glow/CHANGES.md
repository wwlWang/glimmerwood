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
   atmospheric fog 0.25 → 0.5 for depth layering, bloom 0.4 → 0.55 for soft glow (0.7 since item 5), minimum light
   0.15 → 0.2 for readable caves, vignette 0 → 0.5, dynamic shadows on at 2048 with entity shadow
   distance 0.5 (the preset previously verified on Iris 1.8.12), hand-held lights on.
5. Spirit Style (oriMod R22), all under the new "Spirit Style" option screen and switchable off with
   `SPIRIT_STYLE` / `SPIRIT_DEPTH_HAZE`:
   - `program/final.fsh` `spirit_grade`: darks tinted deep blue (teal / purple selectable), yellow-green
     foliage shifted towards emerald/teal, saturation shaped by luminance (more in the mid tones, less
     in highlights so glows stay near white), a soft highlight shoulder; warm saturated pixels (torches,
     lava, sunset) are kept out of the cool grade as focal accents. Optional posterize.
   - `program/final.fsh` `spirit_painterly`: optional 4-sector Kuwahara smoothing (off by default),
     used instead of CAS sharpening when on.
   - `global/atmosphere/fog.glsl`: overworld depth haze that cools from teal to blue-violet with
     distance and lowers distant contrast (not under water, halved in rain).
   - Defaults: god rays on, bloom 0.55 → 0.7.
6. Profiles (oriMod R23): `profile.FAST/FANCY/FABULOUS` in `shaders.properties` now list the same
   13 options the mod sets for its Fluent / Balanced / Detailed effect quality. FANCY equals the pack
   defaults (god rays and dynamic shadows on); FAST keeps bloom and dynamic shadows (1024 map) but turns off
   god rays, reflections, water normals and volumetric clouds; FABULOUS adds SSAO, TAA, SMAA, sharpening and
   finer god rays. Spirit Style options read by `#if defined` / `#elif defined` were changed to plain
   `#ifdef`, so Iris lists them as toggles again (`IMAGE_SHARPENING`, `SPIRIT_DEPTH_HAZE`).
