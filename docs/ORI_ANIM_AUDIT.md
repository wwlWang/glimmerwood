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
