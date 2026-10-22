# Ori animation reference (R112)

Target look for every Ori clip, taken from the games (Blind Forest / Will of the Wisps). These notes come from
watching the games and from published interviews. No official pose sheet is public: the art books and the Moon
Studios / Airborn concept art cover the world and enemies, and the Ori pages on DeviantArt and ArtStation are fan
work. Studio notes that matter (Rock Paper Shotgun, "How animation powers Ori and the Will of the Wisps"):
24 fps keys shown at 60, a physics layer that drives the ears and tail from body momentum, strong squash and
stretch (a strike stretches the arm to about 150% and springs it back), and hit-stop. The worst failure they name
is a jerky transition.

## Baseline body language

Ori never stands straight. The neutral stance is digitigrade and loaded: knees bent, hips low, the spine tipped
forward, a big head held up, and the arms loose and a little forward with soft elbows. The tail is a long, heavy
curve that trails and lags. It is never a rigid stick. The ears lie back with speed and flop up when Ori falls.
Every action is anticipation, then a fast commit, then an eased follow-through.

## Move by move: game vs. our v013 clip

| Move | In the games | v013 (side render, R112 audit) |
|---|---|---|
| Idle | Low crouched stance, weight shifting foot to foot, breathing, ear twitches, a look around. | Stands straight up on tiptoe, legs nearly crossed. Too upright. |
| Run | Low, leaning hard into the stride (about 30°). Arms swept *back* and low, swinging only a little. Long, fast strides with a flight phase. Tail streams out flat behind. | Lean is fine, but the arms reach forward and up on every stride, so Ori reads as a zombie. Tail is rigid. |
| Jump / rise | Crouch, then explode upward. Arms swing up and back, one knee tucks, the body stretches. | Arms reach straight forward and the pose barely changes. |
| Apex | A brief tuck: knees up, arms gathered, ears lifting. | Almost the same as rise. |
| Fall | Arms up and out for balance, legs reaching down, ears and tail flowing upward. | Upright stance with arms forward. No sense of falling. |
| Double jump | A quick front somersault (a spin on the triple jump). | Good: a tucked front flip. Keep it. |
| Land | Deep squash with arms forward for balance, then a spring back up into the run. | OK. |
| Wall cling / slide | Ori sticks *to* the wall: body pressed flat, hands and feet splayed on it, looking back over the shoulder. Claws drag on the slide. | Ori stands in the air with both hands raised. No wall contact at all. |
| Wall jump | Kicks off with both legs and twists away from the wall into a spin. | OK-ish. Needs a push-off. |
| Climb (ladder / vines) | A cat-like scramble: alternating hand-over-hand reach and foot pushes, body close to the surface. | Stands upright and paddles its hands. |
| Swim (underwater) | Streamlined and horizontal. A dolphin kick with both legs together and a whole-body wave. Arms swept back along the body, tail trailing. | Horizontal, but the tail sticks straight up and the limbs paddle like a dog. Not used in game either (swim plays idle plus a procedural tilt). |
| Swim (surface) | Head and shoulders up, quick dog-paddle with the forearms, legs kicking below. | Missing. |
| Water dash | A corkscrew torpedo: arms forward, body stretched, rolling. | Missing. |
| Glide | Both arms straight up holding Kuro's feather, body hanging and swaying, legs dangling. | Arms are not overhead; reads as standing. |
| Dash | Body near horizontal, arms swept back, legs trailing. | Good. |
| Bash aim / launch | Curled ball while aiming, then a stretched arrow with a spin. | Aim is weak. Launch is good. |
| Grapple | One arm thrown out toward the target, then pulled along that line, then a flip on arrival. | Both arms up, standing. |
| Stomp | Front flip into a feet-first plummet, arms up, then an impact crouch. | Standing upright. |
| Launch | Coiled ball charging, then a rocket: arms forward and body stretched. | Not animated (falls through to the air poses). |
| Spirit Edge (blade) | Three hits: a horizontal slash, a backhand, then a heavy overhead or spin. The whole body commits with a step and twist. Air slash is a forward spin. | Arm-only motion, little body; no air version. |
| Spirit Smash (hammer) | A two-handed overhead heave. On the ground it smashes into the floor; in the air Ori spins down. | Borrows blade_3. |
| Spike (spear) | An overhand javelin throw with a big wind-up and body twist. | Borrows blade_3. |
| Spirit Arc (bow) | Draw, hold, release with a little recoil. | OK. |
| Hurt | A flash, a hard recoil away from the hit, limbs flung, then a quick recovery. A heavy hit tumbles Ori. | Barely moves; nearly identical to idle. One direction only. |
| Death | Ori crumples and dissolves into light. | Falls over sideways. OK for now. |
| Crouch | Very low, ears back, tail wrapped. | Stands almost upright. |

## Minecraft-side states with no Ori version yet

Codex's runtime audit has the full list: docs/ANIMATION_AUDIT_RUNTIME.md. These are the ones that need real
clips: crawling in 1-block gaps (it currently plays the swim branch on land), sneak-walk, eating and drinking,
mining, throwing, the shield block, riding and rowing, the horn, the brush, and directional knockback.

## v014 (R112)

The clips are rebuilt on a key-pose framework. `scripts/art/character_poses.py` holds named joint parameters that
are eased between key poses. `scripts/art/character_clips.py` holds the clip library, and same-named clips there
replace the legacy authoring in `character_animations.py`. Everything starts from one loaded stance (`STANCE`):
hips low, knees bent, spine forward, arms soft.

- **Reworked:** idle, alert, crouch, walk, run (arms swept back, stride centred under the lean), start, stop,
  jump_start, rise, apex, fall, both landings, wall_hold and wall_slide (Ori faces and grips the wall), both wall
  jumps, climb, swim, glide (feather held overhead), glide_open and glide_close, bash_aim (curled ball), grapple
  (one arm reaching), stomp, the blade combo (stepping and twisting, matched to the swing curve), block, hurt,
  recover, death (crumples and curls up), sleep (curled up).
- **New:** crouch_walk, crawl, wall_climb, mantle, swim_surface, swim_idle (treading water), swim_dash, elytra,
  burrow, stomp_start, launch_charge, launch, blade_air, throw_ready, spear_throw, hammer_charge, hammer_smash,
  hurt_back, hurt_side, hurt_heavy, eat, drink, horn, brush, mine, place, throw, boat_row, cast_charge,
  cast_release, flash.
- **Body frame:** swim, swim_dash, elytra and burrow have no root pitch. The runtime lays the body along the
  velocity or the view. swim_surface, swim_idle and crawl carry their own body angle.
- **Iteration:** `scripts/art/preview_animations.py` re-authors from the saved .blend and renders side and front
  strips in about 7 s with software GL. It now clears the leftover pose before authoring, which had skewed every
  preview rotation.
