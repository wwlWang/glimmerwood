# First-person spirit weapon fixes

Code: `32096bc` + follow-ups. Author `wwlwwl <wwlwwl@proton.me>`. No `/orimod reset`.

## Problems (P7/P8)
1. Spirit blade / spear / hammer in FP were additive white slabs (especially with Spirit Glow / Mellow bloom).
2. Left main-hand blade tip sat mostly off the left edge and read as “upside down”.
3. Empty FP paws stayed full-bright emission mittens even with Spirit Glow off.

## Changes
### `SpiritWeaponRenderer`
- FP path draws a **thin cyan core + soft rim only** (no stacked outer/mid/motes/orbs). Third-person / GUI keep the denser world look.
- `firstPersonTip(...)` shortens the authored tip so perspective does not fling it off the side of the screen.
- Blade / bow `firstperson_*` Y raised (4.8 / 4.2) so the mid-weapon sits above the hotbar.

### `SpiritHands`
- Emission pass runs only when Spirit Glow is active; otherwise paws use ambient packed light.
- Palm halo quieted.
- Left FP spirit weapons get a small inward nudge in `beginHeld` (shared pose with the grip paw; do not nudge again in `heldItem`).

### `SpiritEnergy`
- Optional translucent FP render types remain available (`glow/streak/ribbon(boolean)`) but FP weapons stay on additive single-layer after translucent proved too faint.

## Tests
- `SpiritWeaponFpToneTest`, updated `SpiritWeaponRigTest` / `SpiritHeldPoseTest` (blade/bow framing + left/right mirror).
- Targeted render suite green.

## Shots
Under `/workspace/oriMod/shots/`:
- Before: `playtest-p7-fp-blade.png`, `playtest-p7-fp-left-blade.png`, `playtest-p7-fp-blade-sprint.png`
- After (shaders off): `playtest-fp-blade-right-idle.png`, `playtest-fp-blade-left-idle.png`, `playtest-fp-spear-*-idle.png`, `playtest-fp-hammer-*-idle.png`, `playtest-fp-bow-*-idle.png`, `playtest-fp-empty-*-idle.png`

## Residual
- With **Spirit Glow / Mellow** on, bloom still brightens the core; FP is better but not flat-lit. Recommend verifying both with shaders on and off.
- Left FP still sits near the edge by design (vanilla arm ±0.56); the nudge helps mid-blade readability but tip can still clip at high FOV / sprint bob.
- First-person weapon “actions” (swing / draw / charge) still use vanilla matrices; only hammer plunge adds a pose. No custom FP swing choreography in this pass.
- Keybind conflict toast and tutorial tips cluttered verification shots; unrelated to FP rendering.
