"""Batch 5 concept mockups: three directions for redesigning the forest spring ruin, as placeable templates.

Each concept grows out of its own landform instead of sitting on a flat pad, and tells why it is a ruin.
Run with the art venv: python scripts/art/ruin_concepts.py <world_dir>
Writes <world_dir>/generated/orimod/structures/concept_<name>.nbt for `/place template orimod:concept_<name>`.
Concept only: no route contract, nothing is written into the mod's resources.
"""
import gzip
import io
import math
import random
import sys
from pathlib import Path
import nbtlib as nbt

W, H, D = 37, 34, 45


class Site:
    def __init__(self, seed):
        self.blocks = {}
        self.rng = random.Random(seed)
        self.seed = seed

    def put(self, x, y, z, name, **props):
        if 0 <= x < W and 0 <= y < H and 0 <= z < D:
            if ':' not in name: name = 'minecraft:' + name
            self.blocks[x, y, z] = (name, tuple(sorted((k, str(v)) for k, v in props.items())))

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z)); return b[0] if b else None

    def solid(self, x, y, z):
        b = self.get(x, y, z)
        return b is not None and not any(k in b for k in ('air', 'water', 'vine', 'leaves', 'fern', 'grass', 'carpet', 'lily', 'flower', 'azalea'))

    def box(self, a, b, name, **props):
        for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
            for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
                for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                    self.put(x, y, z, name, **props)

    def ball(self, c, r, name, chance=1.0, **props):
        R = int(math.ceil(r))
        for dx in range(-R, R + 1):
            for dy in range(-R, R + 1):
                for dz in range(-R, R + 1):
                    if dx * dx + dy * dy + dz * dz <= r * r and self.rng.random() < chance:
                        self.put(round(c[0] + dx), round(c[1] + dy), round(c[2] + dz), name, **props)

    def line(self, a, b, r, name, **props):
        """A thick limb from a to b; radius may taper (r = (start, end))."""
        r0, r1 = r if isinstance(r, tuple) else (r, r)
        n = max(2, int(math.dist(a, b) * 2))
        for i in range(n + 1):
            t = i / n
            p = [a[k] + (b[k] - a[k]) * t for k in range(3)]
            rr = r0 + (r1 - r0) * t
            if rr < .6: self.put(round(p[0]), round(p[1]), round(p[2]), name, **props)
            else: self.ball(p, rr, name, **props)

    def noise(self, x, z, scale, octave=0):
        """Smooth value noise in [0, 1]."""
        def h(i, j):
            v = math.sin(i * 127.1 + j * 311.7 + self.seed * 74.7 + octave * 19.3) * 43758.5453
            return v - math.floor(v)
        gx, gz = x / scale, z / scale
        i, j = math.floor(gx), math.floor(gz); fx, fz = gx - i, gz - j
        sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
        a = h(i, j) + (h(i + 1, j) - h(i, j)) * sx
        b = h(i, j + 1) + (h(i + 1, j + 1) - h(i, j + 1)) * sx
        return a + (b - a) * sz

    def terrain(self, height, top='grass_block', water_level=None):
        self.height = {}
        for x in range(W):
            for z in range(D):
                h = max(1, min(H - 4, round(height(x, z))))
                self.height[x, z] = h
                self.box((x, 0, z), (x, h - 4, z), 'stone')
                self.box((x, max(0, h - 3), z), (x, h - 1, z), 'dirt')
        for x in range(W):
            for z in range(D):
                h = self.height[x, z]
                steep = max(abs(h - self.height.get((x + dx, z + dz), h)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                n = self.noise(x, z, 4, 7)
                if steep >= 2:
                    self.box((x, max(0, h - 3), z), (x, h, z), 'tuff' if n > .5 else 'andesite' if n > .25 else 'stone')
                else:
                    self.put(x, h, z, 'moss_block' if n > .62 else ('coarse_dirt' if n < .12 else top))
        for x in range(W):
            for z in range(D):
                h = self.height[x, z]; n = self.noise(x, z, 4, 7)
                if water_level is not None and h < water_level:
                    self.put(x, h, z, 'mud' if n > .5 else 'gravel')
                    self.box((x, h + 1, z), (x, water_level, z), 'water', level=0)

    def ground(self, x, z):
        return self.height[max(0, min(W - 1, x)), max(0, min(D - 1, z))]

    def wall(self, a, b, base, top, thick=1, ruin=.35):
        """A broken stone wall: jagged eroded top, gaps, cracked/mossy mix, slabs on the broken edge."""
        n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
        for i in range(n + 1):
            x = a[0] + (b[0] - a[0]) * i // max(n, 1); z = a[1] + (b[1] - a[1]) * i // max(n, 1)
            erosion = self.noise(x * 1.7, z * 1.7, 3.5, 3)
            h = round(top - (top - base) * max(0, erosion - (1 - ruin)) / ruin * 1.0) if erosion > 1 - ruin else top
            for t in range(thick):
                xx, zz = (x + t, z) if a[1] != b[1] else (x, z + t)
                g = min(self.ground(xx, zz), base)
                for y in range(g, h + 1):
                    r = self.rng.random()
                    self.put(xx, y, zz, 'mossy_stone_bricks' if r < .4 else 'cracked_stone_bricks' if r < .55 else 'stone_bricks')
                if h > base and self.rng.random() < .5: self.put(xx, h + 1, zz, 'stone_brick_slab', type='bottom')

    def column(self, base, top_pos, name='stone_bricks', r=.0):
        self.line(base, top_pos, r, name)

    def drape(self, chance=.35, name='vine'):
        """Vines on exposed faces, hanging down a few blocks."""
        sides = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0)}
        for (x, y, z), (b, _) in list(self.blocks.items()):
            if not any(k in b for k in ('brick', 'log', 'wood', 'stone')) or self.rng.random() > chance: continue
            for face, (dx, dy, dz) in sides.items():
                vx, vz = x - dx, z - dz
                if self.get(vx, y, vz) is None and self.rng.random() < .3:
                    opposite = face
                    for k in range(self.rng.randint(1, 4)):
                        if self.get(vx, y - k, vz) is not None: break
                        self.put(vx, y - k, vz, name, **{opposite: 'true'})
                    break

    def dress(self, ferns=.035, carpet=.05, flowers=.012):
        for x in range(W):
            for z in range(D):
                y = max((yy for (xx, yy, zz) in [(x, yy, z) for yy in range(H)] if self.get(x, yy, z)), default=None)
                if y is None or y >= H - 1: continue
                top = self.get(x, y, z)
                if top not in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:mossy_stone_bricks', 'minecraft:coarse_dirt'): continue
                r = self.rng.random()
                if r < carpet: self.put(x, y + 1, z, 'moss_carpet')
                elif r < carpet + ferns: self.put(x, y + 1, z, 'fern' if self.rng.random() < .6 else 'short_grass')
                elif r < carpet + ferns + flowers: self.put(x, y + 1, z, 'lily_of_the_valley' if self.rng.random() < .5 else 'blue_orchid')

    def tree(self, base, height, r, log='spirit_log', leaves='spirit_leaves', roots=7, canopy=6.5, lights=4, lean=(0, 0)):
        """A great old tree: flared trunk, buttress roots gripping whatever is beneath, a lit, layered canopy."""
        bx, by, bz = base
        top = (bx + lean[0], by + height, bz + lean[1])
        for i in range(height + 1):
            t = i / height
            c = (bx + lean[0] * t, by + i, bz + lean[1] * t)
            rr = r * (1.0 + .6 * max(0, .25 - t) / .25) * (1 - .35 * t)
            self.ball(c, rr, log, axis='y')
        for k in range(roots):
            a = k * math.tau / roots + self.rng.uniform(-.3, .3)
            reach = self.rng.uniform(r * 2.2, r * 3.6)
            mid = (bx + math.cos(a) * reach * .5, by + self.rng.uniform(1.5, 3), bz + math.sin(a) * reach * .5)
            end = (bx + math.cos(a) * reach, by - 1, bz + math.sin(a) * reach)
            end = (end[0], min(end[1], self.ground(round(end[0]), round(end[2]))), end[2])
            self.line((bx, by + 3, bz), mid, (r * .55, r * .4), log, axis='y')
            self.line(mid, end, (r * .4, .5), log, axis='y')
        for k in range(5):
            a = k * math.tau / 5 + self.rng.uniform(-.4, .4)
            start = (top[0], top[1] - self.rng.uniform(2, 6), top[2])
            end = (start[0] + math.cos(a) * canopy * .9, start[1] + self.rng.uniform(2, 4), start[2] + math.sin(a) * canopy * .9)
            self.line(start, end, (r * .45, .5), log, axis='y')
            self.ball(end, canopy * .55, leaves, chance=.85, persistent='true') if 'spirit' not in leaves else self.ball(end, canopy * .55, leaves, chance=.85, bloomed='false')
        self.ball((top[0], top[1] + 2, top[2]), canopy * .7, leaves, chance=.85, **({'bloomed': 'false'} if 'spirit' in leaves else {'persistent': 'true'}))
        for k in range(lights):
            a = self.rng.uniform(0, math.tau)
            self.put(round(top[0] + math.cos(a) * canopy * .5), round(top[1] + self.rng.uniform(-1, 2)), round(top[2] + math.sin(a) * canopy * .5), 'pearlescent_froglight', axis='y')

    def export(self, path):
        palette, index, blocks = [], {}, []
        for pos, state in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if state not in index:
                index[state] = len(palette)
                v = {'Name': nbt.String(state[0])}
                if state[1]: v['Properties'] = nbt.Compound({k: nbt.String(s) for k, s in state[1]})
                palette.append(nbt.Compound(v))
            blocks.append(nbt.Compound({'pos': nbt.List[nbt.Int](pos), 'state': nbt.Int(index[state])}))
        f = nbt.File({'DataVersion': nbt.Int(3955), 'size': nbt.List[nbt.Int]([W, H, D]), 'palette': nbt.List[nbt.Compound](palette),
                      'blocks': nbt.List[nbt.Compound](blocks), 'entities': nbt.List[nbt.Compound]()})
        s = io.BytesIO(); f.write(s)
        path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(gzip.compress(s.getvalue()))
        print(path.name, len(blocks), 'blocks')


def air_fill(site):
    for x in range(W):
        for z in range(D):
            for y in range(H):
                if (x, y, z) not in site.blocks: site.blocks[x, y, z] = ('minecraft:air', ())


def temple():
    """A: the shrine an old spirit tree swallowed. A hillside shrine whose back hall the tree grew through;
    its roots split the walls, heaved the floor and now carry the climb. Why ruined: the tree outlived it."""
    s = Site(11)
    s.terrain(lambda x, z: 4 + z * .2 + 3 * s.noise(x, z, 9) + 1.5 * s.noise(x, z, 4, 1))
    fx0, fx1, fz0, fz1 = 9, 27, 8, 34
    level = lambda z: 6 + (max(z, fz0) - fz0) // 9 * 3
    for x in range(fx0, fx1 + 1):  # three terraces down the slope; the tree has heaved and split the floor
        for z in range(fz0, fz1 + 1):
            L = level(z)
            s.box((x, L + 1, z), (x, H - 1, z), 'air')
            if s.noise(x, z, 3, 5) < .8:
                s.box((x, s.ground(x, z) - 2, z), (x, L, z), 'stone_bricks' if s.rng.random() < .6 else 'mossy_stone_bricks')
            else:
                s.box((x, L - 3, z), (x, L, z), 'air'); s.put(x, L - 4, z, 'water', level=0)
            s.height[x, z] = L
    for z in range(fz0, fz1 + 1, 9):  # stairs between terraces
        if z == fz0: continue
        for x in range(17, 21):
            s.put(x, level(z - 1) + 1, z - 1, 'mossy_stone_brick_stairs', facing='south')
            s.put(x, level(z - 1) + 2, z - 2, 'air')
    for z in range(2, fz0):  # worn approach stair cut into the hillside
        for x in range(16, 22):
            y = 2 + z * 4 // fz0 + 1
            s.box((x, y - 2, z), (x, y, z), 'mossy_stone_bricks'); s.box((x, y + 1, z), (x, H - 1, z), 'air')
            s.height[x, z] = y
    for z0 in (fz0, fz0 + 9, fz0 + 18):
        s.wall((fx0, z0), (fx0, min(z0 + 8, fz1)), level(z0) + 1, level(z0) + 9, thick=2)
        s.wall((fx1 - 1, z0), (fx1 - 1, min(z0 + 8, fz1)), level(z0) + 1, level(z0) + 8, thick=2)
    s.wall((fx0, fz1), (fx1, fz1), level(fz1) + 1, level(fz1) + 11, thick=2, ruin=.25)
    s.wall((fx0, fz0), (15, fz0), level(fz0) + 1, level(fz0) + 6, thick=1, ruin=.6)
    s.wall((22, fz0), (fx1, fz0), level(fz0) + 1, level(fz0) + 7, thick=1, ruin=.5)
    for x in (fx0 + 3, fx1 - 4):  # inner colonnade, two fallen
        for z in (13, 21):
            s.column((x, level(z), z), (x, level(z) + 7, z), 'stone_bricks', .6)
    s.line((fx0 + 3, level(26) + 1, 26), (fx0 + 9, level(26) + 2, 31), .6, 'cracked_stone_bricks')
    s.line((fx1 - 4, level(26) + 1, 26), (fx1 - 10, level(26) + 1, 23), .6, 'mossy_stone_bricks')
    s.tree((18, level(29) + 1, 29), 17, 3.0, log='spirit_log', leaves='spirit_leaves', roots=8, canopy=8, lights=6, lean=(-1, -2))
    s.drape(.45)
    s.dress()
    return s


def spring():
    """B: the bridge that fell into the spring. A stream cut a ravine under an old arched causeway; the middle
    spans collapsed into the pool, leaving tilted piers and stepping stones to cross. Why ruined: the water won."""
    s = Site(23)
    def h(x, z):
        d = abs(x - 18 - 3 * math.sin(z / 7)) / 18
        return 2 + 16 * d ** 1.4 + 2 * s.noise(x, z, 6) + z * .06 + max(0, z - 36) * 2.2  # high back lip for the fall
    s.terrain(h, water_level=6)
    lip = max(s.ground(x, D - 1) for x in range(16, 21))
    for z in range(37, D):  # notch in the back lip; the fall pours down its face into the pool
        for x in range(17, 20):
            s.box((x, s.ground(x, z) + 1, z), (x, H - 1, z), 'air')
            s.box((x, 6, z), (x, lip - 1, z), 'water', level=0) if z == 37 else s.box((x, s.ground(x, z) - 1, z), (x, lip - 1, z), 'water', level=0)
    deck = 15
    for x in range(2, W - 2):  # causeway deck spanning the ravine, broken in the middle
        if 13 <= x <= 22: continue
        for z in (20, 21, 22):
            s.box((x, deck, z), (x, deck, z), 'mossy_stone_bricks' if s.rng.random() < .5 else 'stone_bricks')
        if s.rng.random() < .5: s.put(x, deck + 1, 20, 'stone_brick_wall')
    for x in (6, 11, 25, 30):  # standing piers with arch shoulders
        s.box((x, s.ground(x, 21) - 2, 20), (x + 1, deck - 1, 22), 'stone_bricks')
    for x0, lean in ((14, 2.5), (19, -3)):  # fallen piers lying tilted in the pool
        s.line((x0, 4, 21), (x0 + lean, 12, 21 + lean * .6), 1.0, 'mossy_stone_bricks')
    s.line((12, 15, 21), (16, 9, 23), .8, 'cracked_stone_bricks')  # snapped span hanging into the gap
    for z in range(6, 36, 3):  # stepping stones along the stream
        x = 18 + round(3 * math.sin(z / 7)) + s.rng.randint(-1, 1)
        s.box((x, 3, z), (x + 1, 7, z + 1), 'mossy_cobblestone')
    for x in range(W):
        for z in range(D):
            if s.get(x, 6, z) == 'minecraft:water' and s.get(x, 7, z) is None and s.rng.random() < .07:
                s.put(x, 7, z, 'lily_pad')
    s.tree((4, s.ground(4, 41), 41), 12, 1.6, log='birch_log', leaves='azalea_leaves', roots=5, canopy=5, lights=2)
    s.tree((33, s.ground(33, 5), 5), 10, 1.4, log='birch_log', leaves='flowering_azalea_leaves', roots=5, canopy=4.5, lights=2)
    s.drape(.4)
    s.dress()
    return s


def treehouse():
    """C: the spirit village the Gloom ate half of. Plank walks ring three living trees; the gloom side is dead
    wood, blighted soil and thorns, its walkways broken and hanging. Why ruined: the decay is still spreading."""
    s = Site(37)
    s.terrain(lambda x, z: 5 + 2.5 * s.noise(x, z, 8) + 1.2 * s.noise(x, z, 3, 2))
    trees = [((10, 24), 16, 2.2, False), ((26, 16), 15, 2.0, False), ((25, 34), 14, 2.0, True)]
    for (x, z), height, r, gloom in trees:
        s.tree((x, s.ground(x, z), z), height, r, log='withered_spirit_log' if gloom else 'spirit_log',
               leaves='dark_oak_leaves' if gloom else 'spirit_leaves', roots=6, canopy=5, lights=0 if gloom else 4)
    for (x, z), height, r, gloom in trees:  # ring platforms
        y = s.ground(x, z) + 6
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                d = math.hypot(dx, dz)
                if r + .5 < d <= 5 and not (gloom and s.noise(x + dx, z + dz, 2.5, 9) < .55):
                    s.put(x + dx, y, z + dz, 'spruce_planks' if not gloom else 'stripped_dark_oak_wood', axis='y')
        for a in range(0, 360, 90):
            px, pz = x + round(math.cos(math.radians(a)) * 5), z + round(math.sin(math.radians(a)) * 5)
            if not gloom or a < 180: s.line((px, s.ground(px, pz), pz), (px, y - 1, pz), .0, 'spruce_fence')
            if not gloom: s.put(px, y + 1, pz, 'lantern')
    def walk(a, b, broken):
        n = int(math.dist(a, b))
        for i in range(n + 1):
            t = i / n
            if broken and .4 < t < .7: continue
            p = [round(a[k] + (b[k] - a[k]) * t) for k in range(3)]
            p[1] = round(a[1] + (b[1] - a[1]) * t - 1.2 * math.sin(math.pi * t))
            s.put(p[0], p[1], p[2], 'spruce_slab', type='bottom')
            if i % 3 == 0: s.put(p[0], p[1] + 1, p[2], 'chain', axis='y')
    y0 = [s.ground(x, z) + 6 for (x, z), *_ in trees]
    walk((15, y0[0], 22), (21, y0[1], 17), False)
    walk((26, y0[1], 21), (25, y0[2], 29), True)
    for x in range(W):  # gloom creeping from the back corner
        for z in range(D):
            spread = math.hypot(x - 30, z - 40) / 18 + .3 * s.noise(x, z, 3, 4)
            if spread < 1:
                g = s.ground(x, z)
                s.put(x, g, z, 'blighted_soil')
                if spread < .7 and s.rng.random() < .25: s.put(x, g + 1, z, 'orimod:gloom_thorns')
    s.drape(.3)
    s.dress(ferns=.06, carpet=.12)
    return s


CONCEPTS = {'temple': temple, 'spring': spring, 'treehouse': treehouse}

if __name__ == '__main__':
    world = Path(sys.argv[1])
    for name, build in CONCEPTS.items():
        site = build(); air_fill(site)
        site.export(world / 'generated/orimod/structures' / f'concept_{name}.nbt')
