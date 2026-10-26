# Cave altar · architecture revision 002

Generator: `scripts/art/build_cave_altar.py`. Packaged template remains the unreleased
LayoutVersion 1 resource, 47 × 32 × 71. Revision 001 sources and the LayoutVersion 0
NBT/contract baseline remain intact.

The entrance has a terrain-adapted carved tuff portal, an inset luminous root mark
and two unequal ruined piers. This frontage is saved by the approach piece's
`CarvedEntrance` flag (absent/false retains the original approach). Below it, three
short flights turn on two broad landings, then open into the vestibule. The entry,
return point, rewards, owned multi-block cores and challenge checkpoints retain
the version-1 coordinates.

Seven connected wall roots replace the dense ceiling ribs. Each has a two-block
base, a thinner bending stem and fence-sized tips joining the vault. Knot emission
is limited to two per root and separated by at least four blocks. The grapple
support is plain wood with two inset sap knots, five blocks apart, framing the
flower. Root wood falls from 805 to 183 blocks; the 14 fence tips
are separate, connected details. There are no floating root cubes.

A worn polished-deepslate spine and cross-path lead to the three focal spaces;
dark tile borders and sparse moss replace large random pale floor patches. The
altar retains its half-step dais and now sits against an open carved stone halo,
with two low flanking piers. The ring frames the existing altar without enlarging
its collision or changing boss/challenge behavior.

Permanent light comes from sparse vault/floor lichen, rooted glow knots and 21 hanging
berry-moss tips, each physically attached to the vault. The offline light flood
ignores breakable light vessels. An independent GameTest removes every vessel,
waits for the real block-light engine, then measures standable integer/half-block
surfaces: all interior walking surfaces >= 1, and surfaces within three blocks
of shrine/map/altar/door and the grapple takeoff pad >= 7. The flower and its six
neighbouring cells also remain >= 7. Two low berry-moss tips flank the launch pad
outside the grapple ray; they are independent of vessels and preserve all
collision, headroom and ability distances. No sky-light contribution is counted.

Run `scripts/art/build_cave_altar.py --check` with the project's nbtlib Python
venv. `manifest.json` contains root paths, descent coordinates, lights and the
packaged NBT hash; `plan.txt` contains the top cut and two sections. Review images
and numerical measurements are in `evidence/playtest-c-cave-v002/` and its capture
directory, not embedded in the distributable assets.

Lichen attaches vertically to the same chunk column. Cross-chunk side attachments
can be removed during vanilla template neighbour-shape validation before the next
slice supplies their wall; the second validation run reproduced that at a stair
landing. Vertical attachment removes that generation-order dependency.
