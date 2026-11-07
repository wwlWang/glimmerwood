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

---

# FP motion pass (swing / draw / charge)

Follow-up to the residuals above. Base stays vanilla: native arm placement (`±0.56, -0.52, -0.72`), equip drop and
`GameRenderer#bobView` are untouched; only the per-item action part of `ItemInHandRenderer#renderArmWithItem` is
replaced, through NeoForge's sanctioned `IClientItemExtensions#applyForgeHandTransform` (no new mixin, no extra layer
stacked on top of vanilla's swing). Non-spirit holders, the spirit core and any other item fall through to vanilla.

## Code
- `SpiritFirstPersonMotion` (pure): `Pose` = yaw/roll/pitch + offset at the hand, zero = vanilla rest, mirrored
  exactly for the left hand (yaw, roll, dx flip). `Memory` remembers the pose on screen so every action starts there.
- `SpiritFirstPersonHand`: per-arm channel, wired in `SpiritWeaponRenderer.register`.

| Weapon | Action | Motion |
| --- | --- | --- |
| Blade (main hand, combo) | stage 1 / 2 / 3 | Driven by the combat timeline (`SpiritAttackPose.Timeline`, same anchor as 3rd person). Wind-up cocks, the cut runs exactly over BladeSlash's 3-tick crescent sweep and lands at `recoveryStart`, recovery eases to rest by `duration`. Stage 1 high→low toward the weapon side, stage 2 rises back across, stage 3 lifts and chops down. Chained stages start from the shown recovery pose (no snap). |
| Blade (off-hand, armour stand, combat unavailable) | swing | Vanilla default swing reproduced exactly (`vanillaSwing`). Main-hand blade clicks never mine (SpiritCombatClient cancels vanilla attack), so held-against-block clicks are combo clicks. |
| Hammer | hold | Eases into a cocked pose over the 12-tick wind-up, slow 1.5° breath when full. |
| Hammer | release | Holds the cocked pose ≤4 ticks for the server's swing, then a quadratic slam into an impact hold and lift back; plunge (air smash) still layers on. Release before full wind-up just settles. |
| Spear | attack swing | Forward thrust and return instead of the sword chop. |
| Spear | hold / throw | Vanilla SPEAR raise eased in over 2.5 ticks; pull eases out (same 0→1 endpoints over 10 ticks); after a ≥4-tick raise the release lowers over 4 ticks with a forward follow-through. |
| Bow | draw | Vanilla BOW matrix byte-for-byte at full raise, eased in over 2.5 ticks and out over 4; draw curve is `BowItem#getPowerForTime`, so arm and string agree. Left-click keeps vanilla's swing. |
| All four | sprint | Screen-space inward tuck up to 0.05 (and 0.02 down) proportional to vanilla's `bob` (max 0.1), applied before any arm rotation. |

## Bloom
- `SpiritGlow.shadersOn()` = any Iris pack enabled (Spirit Glow, Mellow, …). Then first-person weapon alphas ×0.72
  (`fpBloomScale`). Third person and GUI are never trimmed.
- First-person bow additionally: limb-tip orbs ×0.55, grip orb ×0.6, string ×0.8 (the largest additive blobs during a draw).

## Findings
- The hand pass projects with a fixed 70° (`GameRenderer#renderItemInHand` → `getFov(..., false)`); the FOV slider and
  sprint FOV do **not** move first-person items. Tip clipping at "high FOV / sprint" is from view bob sway, hence the tuck.
- The bow still draws no paw in first person (deliberate since P5 J, like vanilla); hand alignment here is the eased
  vanilla raise only.

## Tests (automated, headless)
- New `SpiritFirstPersonChoreographyTest` (30 cases) + shared `SpiritFirstPersonFrame` fixture (70° projection, JSON
  display incl. ItemTransform's left-hand Y/Z negation, `bobView`, left nudge). Covers: stage start/end poses, cut timed
  to the crescent, no jumps at 0.05-tick sampling (incl. chained stages), slash directions per hand, blade tip/grip framed
  for every stage, hammer wind→hold→strike→rest continuity and on-screen head incl. plunge, spear thrust depth, bow/spear
  use = vanilla at full weight and identity at zero, bow arm curve = `BowItem#getPowerForTime`, exact left mirror,
  sprint-tuck keeps the tip inside at full bob for both hands, bloom trim FP-only and bounded.
- Full gate `scripts/check.sh` (build + JUnit + headless GameTest server + FX/sprite checks): JUnit 1919 / 0 failed,
  GameTest 1692 / 0 failed.
- Calibration: the fixture reproduces the existing right-blade idle shot (`playtest-fp-blade-right-idle-noshader.png`,
  tip ≈ (0.94, 0.22) NDC measured vs (0.95, 0.24) computed).

## Visual verification still required
Not done in this pass (no desktop). Minimum checklist, shaders off and Spirit Glow on, right- and left-handed:
1. Blade: three-hit combo (stage 1/2/3 crescents line up with the blade), single hit, mining a block.
2. Hammer: hold to full wind-up, release on ground; same from the air (plunge).
3. Spear: left-click thrust; hold-raise then throw; tap throw.
4. Bow: start draw, full draw, release; left-click.
5. Sprint forward ~3 s holding blade and spear (tip should stay inside the edges).
6. Third person + hotbar/inventory icons: brightness unchanged with shaders on.

---

# Visual pass r1 follow-up (orimod_dev_fp_motion_r1)

Observed in game (computerUse): right blade, hammer ground/air, spear thrust/raise/throw, bow draw/release, shaders off
and Spirit Glow on. Full combo not triggered reliably (only stage 1 recovery seen); left hand and sustained sprint untested.

## Findings
- **Combo opener snap (fixed).** A blade click swings locally at once (`setSwingHand(true)`), but the stage arrives
  from the server a tick or more later. In that gap the hook took the vanilla-swing path, then snapped to stage 1
  starting from rest. Now `SpiritCombatClient.comboPendingSince()` marks a click made from idle (not on an armour
  stand); the blade cocks toward stage 1 at once (`bladePending`, 2 ticks) and stage 1 continues from that pose. A
  refused click settles back after the 12-tick await window.
- **Idle tip is not clipped at 16:9.** In the Spirit Glow screenshot (1274x720 viewport) the visible beam runs
  (1146,570)->(1206,420) and continues under the tutorial toast; extended, it ends ~30 px inside the right edge, which
  the projection fixture reproduces within a few px (new regression test). The "beam toward the edge" is the toast
  covering the last third of the blade.
- **Aspect caveat (not changed).** The hand pass uses a fixed 70° vertical FOV, so horizontal framing depends on window
  aspect: the idle right tip at NDC x≈0.95 (16:9) becomes ≈1.06 at 16:10 and ≈1.27 at 4:3, i.e. clipped on narrower
  windows. Not observed (test window is 16:9); left as a design decision because it changes the approved idle framing.
- **Brightness.** Weapon beams use `rendertype_eyes`, which Iris routes to `gbuffers_spidereyes` (unlit
  `texture * glcolor`, written without `to_linear` into the pack's linear colour buffer), then the pack's bloom (0.7) and
  highlight shoulder. Measured peak beam pixels: Spirit Glow ≈ (190–197, 208–215, 216–221) vs shaders off
  ≈ (206–216 grey) in the earlier shot, so with the ×0.72 trim the core is no brighter than unshaded; the white core is
  the authored look. The paw is a separate path not covered by the trim: hand material tag 254 → `gbuffers_hand` with
  self-light (block 15, neutralised) + GemRender emission band, by design matching Ori's world body; measured
  ≈ (207–229) vs ≈ 102–191 shaders off (different scenes). No shared `SpiritEnergy` change is warranted: it would also
  dim third person, the body aura and the companion. If the paw should be dimmer in FP, that is a per-paw decision for
  `SpiritHands.draw` (self-light floor), not a glow change; it needs an A/B in one scene first.

---

# Wiring audit r2 (blade stages, handedness, sprint, aspect)

Source-level audit of the real NeoForge 21.1.248 call order: `renderHandsWithItems` (view sway) → `renderArmWithItem`
push → `applyForgeHandTransform` (`SpiritFirstPersonHand.apply`: aspect inset + sprint tuck translation, arm, choreography)
→ `renderItem` (`SpiritHeldHandMixin`: left-arm nudge for non-bow weapons) → item display transform (left hand negates
Y/Z rotation, X translation) → BEWLR. Projection fixtures (`SpiritFirstPersonFrame`) follow that order.

## Fixed
- **Late stage arrival popped (all three stages).** The hand's timeline starts at the server's elapsed time on the first
  frame it sees a stage (a fraction of a tick normally, ticks under latency), and `blade()` past the wind-up uses the
  fixed cocked pose, so a chained stage 2/3 or a delayed opener jumped from the pose on screen. `Memory.arrive`
  cross-fades from the shown pose over 1.5 ticks only when the first frame is late (> .05 tick); on-time stages are
  unchanged. Tests: all stages chained at lag 0/.3/1.3/2.5/4 with no frame jump, blend endpoints, and a guard that the
  raw timeline would jump.
- **Vanilla swing popping in after a combo.** Every blade click calls `player.swing`; a click late in stage 3 is shown
  by the combo pose, but its six-tick swing outlived the combo and the hook switched to vanilla's swing mid-way.
  `SwingGate` keeps such a swing on the settle path until it finishes; plain mining swings stay vanilla.
- **Narrow windows clipped the right weapon.** Hand FOV is fixed 70° vertical, so 16:10 / 4:3 push the right blade tip
  to NDC 1.05 / 1.26 (spear 0.84 / 1.01, hammer head 0.96 / 1.15). `aspectInset` shifts the weapon sideways in view
  space (before the arm transform) so the blade/spear tip and hammer head keep their 16:9 screen position; zero at 16:9
  and wider, so the approved framing is unchanged. Bow keeps vanilla framing (its use transform is vanilla's). Reference
  x per hand is pinned against the item JSON by a test.

## Checked, no change
- Channels are keyed by physical arm; main/off hand derives from `player.getMainArm()`, so left main hand gets the combo
  and pending cock, an offhand blade never does (combo and pending are main-hand only; attack swings only the main hand).
  Left-arm nudge is keyed by arm, so it applies to a left offhand and to a left main hand alike.
- Sprint bob: `bobView` is camera-level; the tuck is a pure translation inward (side-mirrored) and down, before the arm.

## Devtools aid
- `record_pose` samples now include `firstPerson.hands.{right,left}`: item, action, stage, elapsed, pending, arrivalLag,
  inset and the shown pose. `scripts/verify-fp-combo.py <id>` drives a deterministic attack cadence (ticks 0 / 4 / 12 →
  stages 1-2-3) through `input_script` for right/left main hand, offhand blade, sprinting, and 16:10 / 4:3 windows, and
  reports stages seen, offhand actions, insets and the largest per-frame pose change. Not run in this round.

## Verified vs unverified
- Verified: JUnit projection / timing fixtures, full `scripts/check.sh` (see commit message).
- Unverified in game: left main hand, offhand blade, sprint, 16:10 / 4:3, full three-stage chain on screen. Shader
  brightness screenshots on the llvmpipe renderer are not reliable and were not used.
