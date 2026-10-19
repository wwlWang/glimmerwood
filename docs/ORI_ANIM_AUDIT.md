# Ori animation audit (R93)

Full animation pass requested by the user after R92 (multi-segment ear/feeler/tail already shipped). Sources were read directly; we do **not** rip official assets — only retune procedural feel and authored clip blending.

## Sources consulted

| Source | URL | What we took |
| --- | --- | --- |
| JXVD — Ground Movement (WotW) | http://jxvd.games/Ori-Ground-Movement | Small accel ramp; **instant stop** on input release; run starts ~80% stick; turnaround anim is visual-only |
| JXVD — Jumping (WotW) | http://jxvd.games/Ori-Jumping | Jump force immediate; release → full gravity (variable height); min ~15 ms / max ~350–450 ms; horizontal speed does **not** affect jump height; angled jump ~41%; **land→run special transition** |
| JXVD — Wall Movement | http://jxvd.games/Ori-Wall-Movement | Wall cling zeros momentum; climb velocity set instantly; wall jump in/out/neutral; mantle at top |
| Rock Paper Shotgun — How animation powers WotW | https://www.rockpapershotgun.com/how-animation-powers-ori-and-the-will-of-the-wisps | BF = 2D 30 fps sprites; WotW = 3D 60 fps **blended states** + **physics-driven ears/tail follow-through** + heavy squash-and-stretch |
| Digital Foundry / Switch tech | https://www.digitalfoundry.net/articles/digitalfoundry-2020-ori-and-the-will-of-the-wisps-switch-inside-an-impossible-port | Soft-body / secondary animation retained; squash-stretch on every joint on leap and land |
| User screenshots (official marketing) | `shots/ref-ori-*.png` | White-core soft bloom; V-swept ears; feelers back over head; curled tail; ready lean |

## Bone / secondary verification (R92 → R93)

Skeleton in `ori.glb` (CC BY 4.0 community model):

| Appendage | Bones | Channels (R92+) | Notes |
| --- | --- | --- | --- |
| Ears | `ear_l/r_1..3` (3+3) | lift + swing ×2 | Rest V via `EAR_REST_*` |
| Feelers | `antenna_l/r_1..3` (3+3) | pitch + roll ×2 | Rest swept back |
| Tail | `tail_1..5` (5) | lift + yaw | Rest droop→curl |
| Legs (R93) | `thigh_l/r`, `knee_l/r` | shared tuck spring | Air tuck / land crouch |

`SpiritSecondary` drives **10 SpringChains → 34 bone angles** plus rest silhouette offsets; R93 adds **4 leg drivers**. Evidence: `scripts/verify-r92.py` / `verify-r93.py` chain plots (`shots/r9*-chains.png`) — tip peaks after root and overshoots on turn/land.

R92 client numbers (before R93 retune): land squash only ~0.87 min; ear tip on land ~±0.46 rad; turn tip/root lag ok (45/50 samples) but tip amplitude capped by `TAIL_CHAIN_LIMIT` 0.42.

## Clip inventory (spirit form)

Authored clips in `ori.glb` (58): alert, apex, bash_aim/launch, bend, blade_1..3 (+_left), block, bow_*, climb, crouch, dash_start/end, death, double_jump, fall, glide(+open/close), grapple, hold, hurt, idle, jump_start, land_light/heavy, recover, rise, run, sit, sleep, start, stomp, stop, swim, tail_sway, transform_*, turn_*, use_item, walk, wall_hold/slide/jump_l/r.

Runtime blender (`SpiritAnimation`):

| State | How selected | Blend notes |
| --- | --- | --- |
| idle / alert | grounded, speed low; alert after 9s idle | breath additive on spine |
| walk / run / sprint | base=`walk`; run weighted by `gaitWeights(speed)` on lower body | R80/R87 |
| jump_start, rise/apex/fall | shot trigger + airborne base apex with `airWeights(vy)` | R88 |
| land_light / land_heavy | touchdown by `|lastVertical|` | crossfade fast |
| double_jump, wall_jump_*, dash_*, glide* | mode / prediction triggers | |
| bash_aim, grapple, stomp, climb, swim, crouch, sit, sleep, death | mode / vanilla flags | |
| combat overlays | blade / bow / hammer / spear / hold | upper-body masks |

Procedural (not clips): facing-from-velocity, lean pitch/roll, squash-stretch spring, secondary SpringChains, look/aim additives, R93 leg tuck.

## Feel vs Ori (issues)

| Topic | Ori (from sources) | Ours before R93 | Action |
| --- | --- | --- | --- |
| Ground stop | Instant on release | 2 ticks (`GROUND_DECEL` 0.18) | **Fix:** `GROUND_DECEL = TOP_SPEED` (1-tick stop) |
| Land squash | Strong whole-body squash | ~13% dip (0.87), ears ok | **Fix:** stronger `SQUASH_LAND`, wider clamp, bigger secondary land kick |
| Run bounce | Clear vertical cadence | `GAIT_BOUNCE` 0.035 (subtle) | **Fix:** ~0.07 |
| Air stretch / tuck | Stretch on rise, tuck on fall | Always stretched by `abs(vy)` | **Fix:** asymmetric stretch/tuck + knee/thigh drivers |
| Ear/tail follow-through | Soft, tip lags, big land flop | Present but tip limited / kicks mild | **Fix:** softer tip K, higher G/limits/kicks/drag/gait sway |
| Land→run transition | Special anim when holding X | land clip then walk crossfade | **Defer:** need authored land_run clip; current fade acceptable |
| Knee bend in run cycle | Deep flex in WotW 3D | Authored run clip only | **Partial:** procedural air/land tuck; re-key run deferred (no official rip) |
| Wall mantle / climb polish | Instant climb vel, mantle forces | Basic wall_slide/hold clips | **Defer** (movement, not this anim pass) |
| Weapon energy pose | Soft light weapons | R90 energy renderer | **Defer** further weapon anim polish |

## R93 changes (code)

1. `FeelTuning` — instant ground stop; stronger land/jump secondary kicks; softer tip springs; higher chain limits; larger run bounce; asymmetric `airScale(vy)`; leg tuck constants.
2. `SpiritAnimation.feel` — use `airScale` for squash target.
3. `SpiritSecondary` — drive `thigh_*` / `knee_*` from a leg-tuck spring (fall tuck, land crouch kick).
4. `scripts/verify-r93.py` — extended state screenshots + chain amplitude checks.
5. Tests — SpringChain lag still holds; new `airScale` / stop-in-one-tick assertions.

## Remaining gaps vs Ori

- No screen-space bloom without a shader pack (additive aura is an approximation).
- Run/walk authored clips are community-model cycles — knee depth in the cycle itself is not WotW-faithful; only procedural overlays help.
- No dedicated land-into-run clip.
- Secondary does not collide with terrain (tail can clip blocks).
- Iris / real GPU bloom not verified in this pass.

## R94 — deferred items done

User (after R93): "修，修啊，别一直问口牙！弄完一起看" — finish everything R93 deferred in one batch.

| Item | What changed | Client evidence (`verify-r94.py r94-v003`) |
| --- | --- | --- |
| Land → run | Touchdown above 2.2 m/s (still running, as WotW's held-stick land) no longer plays the full-body `land_*` clip. The run base keeps going (fast crossfade from `apex`), a masked lower-body `land_light` absorb layer fades over 0.26 s, body dives forward (lean kick), squash/leg tuck/ear kicks scaled down. Standing landings unchanged. | `land_run_full_land_clip=false`, absorb weight max 0.95; control drop still `land_heavy`; `shots/r94-landrun-*` |
| Gait knee depth | The walk/run clips' own thigh/knee/ankle bend (sampled per phase from `ori.glb`, 32 samples) is re-applied on top, scaled by gait (walk 0.22, run 0.38, +0.18 sprint; ≤0.5 rad extra). In phase with the authored stride, so it deepens rather than fights it. | `gaitBones=6`, extra up to 0.50 rad; `shots/r94-run-knee-*` |
| Wall mantle | New `SpiritMantle` (client). Airborne/wall-sliding, pushing into a wall whose lip is ≤1.05 above the feet with room above and on top: pop with the minimal speed that clears lip+0.14 (`MovementRules.mantleLaunch`), then push 0.2 b/t onto the ledge. Anim: `climb` (2.4×) on the rise, `land_light` over the lip, forward pitch 0.55 rad, knee tuck, stretch→squash. A new jump press / crouch / other action cancels instantly (against the wall that press is a wall jump). | 2-high wall: y path 0→2.14→2.0 on top in ~9 ticks; cancel → `wall_jump_l`; `shots/r94-mantle-*` |
| Weapons | Blade combo: windup/hit/recovery remapped to the clip's measured raise peak (24%) and strike (45%) — slow coil, fast snap, eased settle; torso twist coils then whips (±0.34 rad, stage 2 mirrored, stage 3 ×1.3); hit lean kick + ear/tail flick. Hammer: hold frame fixed to the real raise peak (R88 held 32% = already striking); back arch with breathing while charging; slam pitch + squash + ear flop on release. Spear: ready twist/bob while held; throw = overhand `blade_3` with coil/whip. Energy weapons flare (+70% light, wider halo on the blade) through swings. R91 condensed-light renderer kept. | twist −0.17..0.31 (blade), arch −0.26..0.36 (hammer), spear −0.20..0.25; `shots/r94-blade-*`, `r94-hammer-*`, `r94-spear-*` |

Ear/feeler/tail chains untouched (still 34 bone angles).

### Still not done

- No authored land→run / mantle / weapon clips — all procedural layers over the community model's clips.
- Hammer wind-up reads best from the side; from the front the head is mostly behind the body.
- Secondary still has no terrain collision; true Iris bloom not verified; client only checked on Xvfb + llvmpipe (low FPS, so screenshots step a few ticks).

## R95 — pose overrides (run / swim / glide + elytra / held items)

User (after R94): "弄完一起看" — one jar covering the four pose targets. Model unchanged (CC BY community `ori.glb`); no official assets used. Reference frames were pulled from public 480p videos only to check silhouettes (`shots/r95-ref-*.png`, not shipped).

### Sources

| Source | What we took from it |
| --- | --- |
| WotW Swim Dash (`ZMlYqYiM5Ls`) | Under water Ori lies along the motion, body long; the dash is a tucked dart (arms back along the body, legs together), with ears and tail trailing straight behind. No upright paddling. |
| Kuro's Feather / glide (`LrfuGa4im1Y`) | The feather is held overhead in both hands, so the arms make a high, wide V and the legs trail slightly bent. The torso tips about 25° into the airflow. Ears and tail stream back. |
| Luma Pools (`QwT5M2V2IeI`) | Surface/slow swim: the head stays up while the body slopes down, with a soft, slow breast-stroke paddle and a small flutter kick. |
| E3 2019 trailer (`2reK8k8nwBc`) | Run silhouette: chest leans forward, knees lift high, ears swept back, tail level and lagging. |
| orithegame.com/media | Lists the same E3 2019 / 2017 trailers plus gallery stills. Only checked against the trailer above; nothing downloaded from it. |

### What changed

- **`SpiritPoseLayer` / `SpiritPoses` (new).** These are procedural override layers that write absolute local rotations from angles given in the model frame (+Z forward, +Y up, rest T-pose). A pose replaces what is under it: GemRender averages override layers and caps weights at 1, so every lower layer (base crossfade, run, land-run, air, overlay) has its mask scaled by `1 − share` on the posed bones (`keep[]`).
- **Root-cause fix found while tuning:** the glb node chain scales world matrices by about 0.08 (0.159 × 0.01 × 51.3), and JOML's `getNormalizedRotation` returned a non-unit, wrong quaternion for those matrices. So the pose axes were wrong: the forearm "bend" was really a twist about the bone. `SpiritPoseLayer.restRotation` now normalizes the columns first (JUnit `SpiritPoseLayerTest`). With the fix the in-game blended local rotations match the targets exactly (debug `-Dorimod.debugPose=true` → `secondary.poseError` = 0). The held-torch grip went from 0.36 to 0.68 m, against 0.70 in the offline preview.
- **Body pitch.** A spring-driven whole-body pitch around the hip pivot (0.5 m) lays the upright authored pose down:
  - swim follows the velocity (π/2 when level, ±1.2 rad when diving or rising, about 1.0 rad when treading);
  - spin and elytra use π/2 + look pitch (±80°);
  - glide uses 0.42 rad.
- **RUN** (additive, weight = run + sprint gait, on feet only): chest lean 0.12, extra knee lift 0.42 / fold 0.5 peaking at each leg's forward swing, neck and head counter-tilted to keep the gaze level. Lean speed 0.24, run gait gain 0.5, secondary drag 1.15, so the ears and tail stream and overshoot more.
- **SWIM** (override; base switches to idle because the authored swim clip is a dog-paddle):
  - Cruise/float: a slow soft paddle (0.6 → 1.05 strokes/s, elbows bent) and a small flutter kick.
  - Water dash (3D speed 11.5 → 15 m/s): a dart tuck that rises in 0.06 s and decays over 0.35 s. Arms go back along the body, legs together, ears/tail/feelers trail flat, and the body stretches by 12%.
- **GLIDE / FEATHER, also used for elytra:** arms raised in a high, wide V with the elbows soft, torso into the airflow, legs trailing with the knees bent. The secondary "medium" sets ears and tail to stream (glide 0.6, elytra 1.0) and switches gravity sag off for elytra.
- **HOLD ITEM:**
  - Any item in a free hand gets a chest cradle: upper arm forward and down, elbow folded 1.85 rad, hand curled in, with a small walk bob. A soft spirit-light halo sits at the grip.
  - Trident charge no longer uses the vanilla two-hand spear stretch: the throw-ready pose holds it over the shoulder, close to the head.
  - The R94 spirit spear and the weapon swings are unchanged.

### Evidence (`scripts/verify-r95.py`, client on Xvfb + llvmpipe)

- `r95-before-v001` against `r95-after-v004`, status ok.
- Sheets: `shots/r95-sheet-before.png` and `shots/r95-sheet-after.png`. Per-shot `shots/r95-{before,after}-*.png`.
- After, max values per scene:

| Scene | Result |
| --- | --- |
| Run | run pose 1.0 |
| Swim | swim 1.0, dart 1.0, body pitch 1.57 |
| Tread | swim 1.0, pitch up to 1.82 while sinking |
| Glide | glide 1.0, pitch 0.42 |
| Elytra | glide 1.0, pitch 1.83 |
| Hold | cradle 1.0, throw 1.0 |

### Still not done

- Everything is still a procedural layer over community-model clips; nothing is hand-keyed.
- The trident's ready angle points up and back rather than level.
- Elytra shows the vanilla wing model on Ori's back.
- The tread pose is only approximate.
- Not checked with Iris, nor on real GPU frame rates.
