# Forest Spring · LayoutVersion 1 · first art checkpoint

Generator: scripts/art/build_forest_spring.py. 39 × 32 × 55. LayoutVersion 0 resources are never regenerated; their pre-edit hashes are in src/test/resources/ruins/forest_spring_legacy.json (f3cc83c).

An open spring cloister rather than an enclosed hall. The front root arch frames the central pool; a worn pale path forks to the shrine and map courts. The storm courtyard has its own broad view and a tall root halo, behind the pool. Asymmetric attached canopy shoulders and broken stone uprights retain sky and sight lines.

The spring is an authored two-deep source pool with a clay bed and a stone rim. The light-bridge reward island is isolated; submerged return steps lead to the near bank. A real Bash lantern illuminates the bridge before the launch lands. The west wall-jump / double-jump / dash course remains available alongside the main walking route. A west cracked-wall chamber preserves the secret, while a new optional east treasury takes the two new keys. The old forest did not have a key vault; this addition applies only to version 1.

Shrine (7,6,17), map (31,6,17), storm altar (19,6,46) use owned B4 parts. The east vault door (29,6,40) uses nine west-facing owned parts. Return safe point (10.5,6,16.5), front entry (19.5,6,0.5). No marker or shrine inference may silently read the old contract.

Permanent light comes from inset sap knots, luminous leaf accents and sparse thin lichen. Containers and the unlit Bash lantern are excluded from the minimum-light guarantee. Runtime tests use actual block light with all containers removed, including walkable roofs/ledges and focus approaches.

Both floor and footprint are adapted to surface terrain; the cave roof-burial rule is not used. The approach height is an explicit layout field so legacy surface pieces retain their old low threshold. New starts keep the four-block relief limit, dry-ground preflight, generated-chunk guard and landmark exclusions. A new forest piece extends authored bottom dirt through small carver pockets only when a sturdy floor exists within eight blocks; untouched terrain columns and legacy pieces are excluded.

Source plan and manifest are deterministic package counterparts. Captures and test evidence belong in evidence/playtest-c-forest-v001, not in the shipped assets.
