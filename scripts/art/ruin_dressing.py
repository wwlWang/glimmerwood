"""Batch 5 dressing: turn each ruin into a shrine an ancient spirit tree has swallowed (direction A, 2026-10-22).

Runs inside Template.export after every functional block is placed. It only writes where nothing on the route, a
secret, a bridge, an arena, a lift or a reward could be touched: `protected` holds those cells (absolute template
coordinates, with headroom and a margin); everything placed by the functional layout is kept unless it is plain
ground, which may change material but stays solid. The story each ruin tells: the shrine came first, the tree
outlived it — roots split the walls and heave the floor, the crown shades the court, moss and lichen took the rest.
"""
import math
import random

GROUND = {"minecraft:moss_block", "minecraft:stone", "minecraft:grass_block"}
SOFT = ("air", "leaves", "vine", "fern", "grass", "carpet", "lichen", "flower", "lily", "azalea", "orchid", "roots", "moss_carpet")


class Dresser:
    def __init__(self, t, protected, seed):
        self.t, self.protected, self.rng = t, protected, random.Random(seed)
        self.functional = dict(t.blocks)
        self.W, self.H, self.D = t.size
        self.seed = seed
        self.region = lambda p: True  # the cave narrows this to its hall: nothing may land on the world above it

    # --- low-level writes ---
    def free(self, p):
        if p in self.protected or not (0 <= p[0] < self.W and 0 <= p[1] < self.H and 0 <= p[2] < self.D) or not self.region(p): return False
        old = self.functional.get(p)
        return old is None or old[0] == "minecraft:air"

    def put(self, p, name, **props):
        p = tuple(int(round(v)) for v in p)
        if not self.free(p): return False
        if ":" not in name: name = "minecraft:" + name
        self.t.blocks[p] = (name, tuple(sorted((k, str(v)) for k, v in props.items())))
        return True

    def reground(self, p, name, **props):
        """Swap a ground block's material (stays solid): allowed even under the route, never on an anchor."""
        old = self.t.blocks.get(p)
        if old and old[0] in GROUND and p not in self.anchors:
            if ":" not in name: name = "minecraft:" + name
            self.t.blocks[p] = (name, tuple(sorted((k, str(v)) for k, v in props.items())))

    def get(self, p):
        b = self.t.blocks.get(tuple(p)); return b[0] if b else None

    def solid(self, p):
        b = self.get(p)
        return b is not None and not any(k in b for k in SOFT) and "water" not in b

    def noise(self, x, z, scale, octave=0):
        def h(i, j):
            v = math.sin(i * 127.1 + j * 311.7 + self.seed * 74.7 + octave * 19.3) * 43758.5453
            return v - math.floor(v)
        gx, gz = x / scale, z / scale
        i, j = math.floor(gx), math.floor(gz); fx, fz = gx - i, gz - j
        sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
        a = h(i, j) + (h(i + 1, j) - h(i, j)) * sx; b = h(i, j + 1) + (h(i + 1, j + 1) - h(i, j + 1)) * sx
        return a + (b - a) * sz

    def ball(self, c, r, name, chance=1.0, **props):
        R = int(math.ceil(r))
        for dx in range(-R, R + 1):
            for dy in range(-R, R + 1):
                for dz in range(-R, R + 1):
                    if dx * dx + dy * dy + dz * dz <= r * r and self.rng.random() < chance:
                        self.put((c[0] + dx, c[1] + dy, c[2] + dz), name, **props)

    def limb(self, a, b, r, name, **props):
        r0, r1 = r if isinstance(r, tuple) else (r, r)
        n = max(2, int(math.dist(a, b) * 2))
        for i in range(n + 1):
            t = i / n; p = [a[k] + (b[k] - a[k]) * t for k in range(3)]; rr = r0 + (r1 - r0) * t
            if rr < .6: self.put(p, name, **props)
            else: self.ball(p, rr, name, **props)

    # --- pieces ---
    def great_tree(self, base, height, r, canopy, lean=(0, 0), roots=8, hanging=True, lights=6):
        """Flared spirit-log trunk; buttress roots that run along the ground and over walls; a crown of spirit leaves
        lit from inside by pearlescent froglights, with spirit vines hanging from its underside."""
        bx, by, bz = base
        top = (bx + lean[0], by + height, bz + lean[1])
        for i in range(height + 1):
            t = i / height
            rr = r * (1 + .7 * max(0, .25 - t) / .25) * (1 - .3 * t)
            self.ball((bx + lean[0] * t, by + i, bz + lean[1] * t), rr, "orimod:spirit_log", axis="y")
        for k in range(roots):
            a = k * math.tau / roots + self.rng.uniform(-.3, .3)
            reach = self.rng.uniform(r * 2.4, r * 4.2)
            mid = (bx + math.cos(a) * reach * .5, by + self.rng.uniform(1, 2.5), bz + math.sin(a) * reach * .5)
            end = (bx + math.cos(a) * reach, by - 1, bz + math.sin(a) * reach)
            self.limb((bx, by + 3, bz), mid, (r * .6, r * .4), "orimod:spirit_log", axis="y")
            self.limb(mid, end, (r * .4, .5), "orimod:spirit_log", axis="y")
        crown = []
        for k in range(5):
            a = k * math.tau / 5 + self.rng.uniform(-.4, .4)
            start = (top[0], top[1] - self.rng.uniform(2, 5), top[2])
            end = (start[0] + math.cos(a) * canopy * .85, start[1] + self.rng.uniform(1.5, 3.5), start[2] + math.sin(a) * canopy * .85)
            self.limb(start, end, (r * .45, .5), "orimod:spirit_log", axis="y")
            crown.append(end)
        crown.append((top[0], top[1] + 2, top[2]))
        # Old living crown: mostly azalea, flowering in places, with the spirit tree's own cyan leaves only as accents
        # (an all-spirit crown read as a neon crystal in game). Spirit leaves still bloom with the restoration.
        R = int(math.ceil(canopy * .55))
        for c in crown:
            for dx in range(-R, R + 1):
                for dy in range(-R, R + 1):
                    for dz in range(-R, R + 1):
                        if dx * dx + dy * dy + dz * dz > (canopy * .55) ** 2 or self.rng.random() > .88: continue
                        q, roll = (c[0] + dx, c[1] + dy, c[2] + dz), self.rng.random()
                        if roll < .25: self.put(q, "orimod:spirit_leaves", bloomed="false")
                        else: self.put(q, "flowering_azalea_leaves" if roll < .5 else "azalea_leaves", persistent="true", distance="7", waterlogged="false")
        for k in range(lights):
            c = crown[k % len(crown)]
            self.put((c[0] + self.rng.randint(-1, 1), c[1] - 1, c[2] + self.rng.randint(-1, 1)), "pearlescent_froglight", axis="y")
        if hanging:
            for c in crown:
                for _ in range(6):
                    x, z = c[0] + self.rng.uniform(-canopy * .5, canopy * .5), c[2] + self.rng.uniform(-canopy * .5, canopy * .5)
                    y = int(c[1])
                    while y > 0 and self.get((round(x), y, round(z))) not in (None, "minecraft:air"): y -= 1
                    if "leaves" not in (self.get((round(x), y + 1, round(z))) or ""): continue
                    n = self.rng.randint(2, 5)
                    for k in range(n):
                        if not self.put((x, y - k, z), "orimod:spirit_vine", tip="true" if k == n - 1 else "false"): break

    def wall(self, a, b, base, top, thick=1, ruin=.45):
        """A shrine wall the roots broke: eroded jagged top, breaches down to its footing, cracked/mossy mix."""
        n = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
        for i in range(n + 1):
            x = a[0] + (b[0] - a[0]) * i // n; z = a[1] + (b[1] - a[1]) * i // n
            e = self.noise(x, z, 5.5, 3)
            h = top if e < 1 - ruin else round(top - (top - base + 1) * (e - (1 - ruin)) / ruin)
            for k in range(thick):
                xx, zz = (x + k, z) if a[1] != b[1] else (x, z + k)
                for y in range(base, h + 1):
                    r = self.rng.random()
                    self.put((xx, y, zz), "mossy_stone_bricks" if r < .45 else "cracked_stone_bricks" if r < .65 else "stone_bricks")
                if h >= base and self.rng.random() < .35: self.put((xx, h + 1, zz), "mossy_stone_brick_slab", type="bottom")

    def column(self, x, z, base, height, fallen_to=None):
        if fallen_to is None:
            for y in range(base, base + height): self.put((x, y, z), "chiseled_stone_bricks" if y == base + height - 1 else "stone_bricks")
            self.put((x, base + height, z), "mossy_stone_brick_slab", type="bottom")
        else:
            self.limb((x, base, z), fallen_to, .0, "mossy_stone_bricks")

    def rubble(self, count, y, area):
        (x0, z0), (x1, z1) = area
        for _ in range(count):
            p = (self.rng.randint(x0, x1), y, self.rng.randint(z0, z1))
            if self.solid((p[0], y - 1, p[2])):
                self.put(p, self.rng.choice(("mossy_cobblestone", "cobblestone", "mossy_stone_bricks", "mossy_cobblestone_slab")),
                         **({"type": "bottom"} if False else {}))

    def mounds(self, deck, height=3):
        """Natural ground creeping over the edges of the old deck so the ruin sinks into the land, not onto a pad."""
        for x in range(self.W):
            for z in range(self.D):
                edge = min(x, z, self.W - 1 - x, self.D - 1 - z)
                lift = (height + 1) * self.noise(x, z, 4.5, 1) - edge * .9
                if edge > 0: lift -= .6
                for k in range(int(lift)):
                    y = deck + 1 + k
                    if not self.solid((x, y - 1, z)): break
                    n = self.noise(x, z, 3, 2)
                    self.put((x, y, z), "rooted_dirt" if k < int(lift) - 1 else ("moss_block" if n > .5 else "grass_block"))

    def bank(self, deck):
        """The foundation's outer faces read as an earthen bank (moss and grass on top), not a stone plinth, wherever
        the land outside sits lower than the deck."""
        for x in range(self.W):
            for z in range(self.D):
                if 0 < x < self.W - 1 and 0 < z < self.D - 1: continue
                for y in range(0, deck):
                    p = (x, y, z)
                    if self.t.blocks.get(p, ("",))[0] != "minecraft:stone" or p in self.anchors or p in self.protected: continue
                    n = self.noise(x + y * 3, z, 2.2, 6)
                    self.t.blocks[p] = ("minecraft:" + ("rooted_dirt" if n < .3 else "coarse_dirt" if n < .45 else "dirt"), ())
                top = (x, deck, z)
                if self.t.blocks.get(top, ("",))[0] in ("minecraft:moss_block", "minecraft:stone") and top not in self.protected:
                    self.t.blocks[top] = ("minecraft:grass_block", (("snowy", "false"),))

    def regrind(self, deck):
        """The deck floor reads as heaved flagstones, moss and soil instead of one moss slab."""
        for x in range(self.W):
            for z in range(self.D):
                p = (x, deck, z); n = self.noise(x, z, 2.6, 4); m = self.noise(x, z, 6, 5)
                if m < .45:
                    self.reground(p, "mossy_stone_bricks" if n < .5 else "cracked_stone_bricks" if n < .62 else "stone_bricks")
                elif n < .2: self.reground(p, "coarse_dirt")
                elif n > .8: self.reground(p, "rooted_dirt")

    def greenery(self, density=.07):
        for (x, y, z), (b, _) in list(self.t.blocks.items()):
            if b not in ("minecraft:moss_block", "minecraft:grass_block", "minecraft:mossy_stone_bricks", "minecraft:rooted_dirt",
                         "minecraft:coarse_dirt"): continue
            if self.get((x, y + 1, z)) not in (None, "minecraft:air"): continue
            r = self.rng.random()
            if r < density * .5: self.put((x, y + 1, z), "moss_carpet")
            elif r < density * .8: self.put((x, y + 1, z), "fern")
            elif r < density * .9: self.put((x, y + 1, z), "lily_of_the_valley")
            elif r < density: self.put((x, y + 1, z), "short_grass")

    def lichen(self, chance=.05):
        """Glow lichen on shaded stone faces: the ruin's light, replacing the old sea-lantern grid."""
        faces = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "down": (0, -1, 0)}
        for (x, y, z), (b, _) in list(self.t.blocks.items()):
            if not any(k in b for k in ("stone_bricks", "spirit_log", "deepslate", "cobblestone")) or self.rng.random() > chance: continue
            for face, (dx, dy, dz) in faces.items():
                p = (x - dx, y - dy, z - dz)
                if self.get(p) in (None, "minecraft:air") and self.free(p):
                    self.put(p, "glow_lichen", **{face: "true", "waterlogged": "false"}); break

    def vines(self, chance=.18):
        sides = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
        for (x, y, z), (b, _) in list(self.t.blocks.items()):
            if not any(k in b for k in ("stone_bricks", "spirit_log")) or self.rng.random() > chance: continue
            for face, (dx, _, dz) in sides.items():
                vx, vz = x - dx, z - dz
                if self.get((vx, y, vz)) in (None, "minecraft:air") and self.rng.random() < .35:
                    for k in range(self.rng.randint(1, 4)):
                        if self.get((vx, y - k, vz)) not in (None, "minecraft:air") or not self.put((vx, y - k, vz), "vine", **{face: "true"}): break
                    break


def protected_cells(t, shrine, safe, entry, clearances, checkpoints):
    """Every cell the dressing must leave alone, in absolute template coordinates."""
    W, H, D = t.size
    cells = set()
    def box(a, b, grow=0, up=0):
        for x in range(min(a[0], b[0]) - grow, max(a[0], b[0]) + grow + 1):
            for y in range(min(a[1], b[1]), max(a[1], b[1]) + up + 1):
                for z in range(min(a[2], b[2]) - grow, max(a[2], b[2]) + grow + 1):
                    cells.add((x, y, z))
    def point(p, grow=1, up=2):
        p = [int(math.floor(v)) for v in p]; box(p, p, grow, up)
    for a, b in clearances: box(t.shifted(a), t.shifted(b), grow=1, up=1)
    for p, _, _ in checkpoints: point(t.shifted(p), 2, 3)
    for p in (shrine, safe, entry): point(t.shifted(p), 2, 3)
    for anchor in t.critical: point(anchor["position"], 1, 2)
    f = t.features
    for c in f.get("cells", []): point(c["position"], 1, 2)
    for p in f.get("updrafts", []): box(p, p, 1, H)  # the whole lift column stays open to the sky
    for key, radius in (("map_stone", 1), ("storm_altar", 7), ("boss_altar", 8)):
        if key in f: point(f[key], radius, 6 if radius > 1 else 2)
    for p in f.get("vessels", []): point(p, 1, 2)
    for p in f.get("keystones", []): point(p, 1, 2)
    for p in f.get("beetles", []): point(p, 2, 2)
    if "spirit_sand" in f:
        a, b = f["spirit_sand"]; box(a, (b[0] + 2, b[1], b[2]), 1, 2)
    for key in ("vault", "gloom_water"):
        if key in f: box(f[key]["from"], f[key]["to"], 1, 3)
    if "secret" in f: box(f["secret"]["from"], f["secret"]["to"], 1, 1)
    for bridge in f.get("bridges", []):
        # The dark bridge must stay the only way to its reward: nothing new to stand on anywhere near it, any height
        # (a dressed wall top beside the forest water court once made a walkable bypass).
        nodes = [bridge["entry"], bridge["exit"], *bridge["platforms"], *bridge["lanterns"], *bridge["escape"]]
        lo = [min(int(math.floor(n[i])) for n in nodes) for i in range(3)]
        hi = [max(int(math.floor(n[i])) for n in nodes) for i in range(3)]
        box((lo[0], 0, lo[2]), (hi[0], H - 1, hi[2]), grow=3)
    # The entry stair cut and its approach in front of the template.
    x0 = W // 2 - 3
    box((x0, 0, 0), (x0 + 6, H - 1, 6))
    return cells


def forest(t, protected):
    d = Dresser(t, protected, 101); deck = t.pad + 1
    d.anchors = {tuple(a["position"]) for a in t.critical}
    d.regrind(deck); d.bank(deck)
    # The shrine: side walls in pieces, a tall back wall the tree burst through, a broken colonnade.
    d.wall((0, 2), (0, 30), deck + 1, deck + 7, thick=1, ruin=.55)
    d.wall((18, 2), (18, 30), deck + 1, deck + 6, thick=1, ruin=.55)
    d.wall((1, 30), (17, 30), deck + 1, deck + 10, thick=1, ruin=.35)
    for x, z in ((2, 8), (16, 8), (16, 14), (2, 20)): d.column(x, z, deck + 1, 6)
    d.column(16, 20, deck + 1, 0, fallen_to=(12, deck + 1, 23))
    # The ancient tree grows through the back-right of the court; its crown shades the whole shrine.
    d.great_tree((16, deck, 11), 15, 2.3, 7.5, lean=(-2, 3), roots=7, lights=7)
    d.great_tree((14, deck, 27), 9, 1.4, 4.5, lean=(-1, 0), roots=5, lights=3)  # a younger tree split the back wall
    d.mounds(deck, 3); d.rubble(24, deck + 1, ((1, 2), (17, 29)))
    d.vines(.2); d.lichen(.06); d.greenery(.09)


def creek(t, protected):
    d = Dresser(t, protected, 202); deck = t.pad + 1
    d.anchors = {tuple(a["position"]) for a in t.critical}
    d.regrind(deck); d.bank(deck)
    d.wall((0, 2), (0, 34), deck + 1, deck + 8, thick=1, ruin=.6)
    d.wall((20, 2), (20, 34), deck + 1, deck + 7, thick=1, ruin=.6)
    d.wall((1, 34), (19, 34), deck + 1, deck + 11, thick=1, ruin=.4)
    # Here the tree stands at the creek's head; roots arch over the water and grip the old pillars.
    d.great_tree((16, deck, 29), 16, 2.5, 8, lean=(-2, -1), roots=9, lights=7)
    d.mounds(deck, 3); d.rubble(22, deck + 1, ((1, 2), (19, 33)))
    d.vines(.22); d.lichen(.06); d.greenery(.08)


def cave(t, protected):
    """Underground variant: the tree is above; its roots pierced the vault ceiling and hang through the hall,
    pillars of root along the walls, lichen glow instead of lanterns."""
    d = Dresser(t, protected, 303)
    d.region = lambda p: 1 <= p[0] <= 21 and 2 <= p[1] <= 12 and 14 <= p[2] <= 37
    d.anchors = {tuple(a["position"]) for a in t.critical}
    ceiling = 12
    rng = d.rng
    for k in range(14):  # hanging roots from the ceiling, thick at the top, tapering, some reaching the floor
        x, z = rng.randint(2, 20), rng.randint(15, 36)
        length = rng.randint(3, 10)
        d.limb((x, ceiling, z), (x + rng.uniform(-1.5, 1.5), ceiling - length, z + rng.uniform(-1.5, 1.5)), (1.2, .4), "orimod:spirit_log", axis="y")
        if rng.random() < .5: d.put((x, ceiling - length - 1, z), "orimod:spirit_vine", tip="true")
    for x, z in ((2, 18), (20, 24), (2, 33), (20, 35)):  # roots that grew down the walls into the floor
        d.limb((x, ceiling, z), (x, 2, z + rng.uniform(-2, 2)), (1.3, .9), "orimod:spirit_log", axis="y")
    for _ in range(18):  # broken stalactite ceiling stones
        x, z = rng.randint(2, 20), rng.randint(15, 36)
        d.put((x, ceiling, z), "pointed_dripstone", vertical_direction="down", thickness="tip", waterlogged="false")
    d.rubble(30, 2, ((2, 15), (20, 36)))
    d.lichen(.09); d.vines(.08)


DRESS = {"forest_spring": forest, "creek_ruins": creek, "cave_altar": cave}


# --- Dark-bridge bypass guard: a Python port of RuinBridgeNavigationGameTests.WalkGraph (crouched Ori, any drop) ---
EMPTY_KEYS = ("air", "water", "vine", "lichen", "fern", "short_grass", "lily_of", "orchid", "spirit_platform", "bash_anchor",
              "ladder", "flower", "azalea_leaves_x")
SHAPES = {"minecraft:moss_carpet": [(0, 0, 0, 1, 1 / 16, 1)], "minecraft:lily_pad": [(0, 0, 0, 1, 1.5 / 16, 1)],
          "minecraft:chain": [(6.5 / 16, 0, 6.5 / 16, 9.5 / 16, 1, 9.5 / 16)],
          "minecraft:pointed_dripstone": [(5 / 16, 0, 5 / 16, 11 / 16, 1, 11 / 16)],
          "orimod:spirit_light_vessel": [(3 / 16, 0, 3 / 16, 13 / 16, 10 / 16, 13 / 16)]}


def shape(state):
    name, props = state
    if any(k in name for k in EMPTY_KEYS): return []
    if name in SHAPES: return SHAPES[name]
    if name.endswith("_slab"):
        kind = dict(props).get("type", "bottom")
        return [(0, 0, 0, 1, .5, 1)] if kind == "bottom" else [(0, .5, 0, 1, 1, 1)] if kind == "top" else [(0, 0, 0, 1, 1, 1)]
    return [(0, 0, 0, 1, 1, 1)]


class WalkGraph:
    def __init__(self, blocks, size, rim, deck, body=1.0):
        W, H, D = size
        self.body, self.boxes = body, {}
        for (x, y, z), state in blocks.items():
            s = shape(state)
            if s: self.boxes[x, y, z] = [(x + a, y + b, z + c, x + d, y + e, z + f) for a, b, c, d, e, f in s]
        for x in range(-rim, W + rim):
            for z in range(-rim, D + rim):
                if x < 0 or z < 0 or x >= W or z >= D:
                    for y in range(0, deck + 1): self.boxes[x, y, z] = [(x, y, z, x + 1, y + 1, z + 1)]
        self.columns = {}
        for (cx, cy, cz), bs in self.boxes.items():
            for b in bs:
                px, pz = cx + .5, cz + .5
                if b[0] <= px <= b[3] and b[2] <= pz <= b[5]:
                    n = (px, b[4], pz)
                    if self.clear(n): self.columns.setdefault((cx, cz), set()).add(n)

    def clear(self, p):
        x0, y0, z0, x1, y1, z1 = p[0] - .299, p[1] + .001, p[2] - .299, p[0] + .299, p[1] + self.body - .001, p[2] + .299
        for x in range(math.floor(x0), math.floor(x1) + 1):
            for y in range(math.floor(y0), math.floor(y1) + 1):
                for z in range(math.floor(z0), math.floor(z1) + 1):
                    for b in self.boxes.get((x, y, z), ()):
                        if b[0] < x1 and b[3] > x0 and b[1] < y1 and b[4] > y0 and b[2] < z1 and b[5] > z0: return False
        return True

    def segment(self, a, b):
        steps = max(1, math.ceil(math.dist(a, b) * 8))
        return all(self.clear(tuple(a[k] + (b[k] - a[k]) * i / steps for k in range(3))) for i in range(steps + 1))

    def jump(self, a, b, h):
        if b[1] - a[1] > h: return False
        apex = (a[0], a[1] + h, a[2]); across = (b[0], apex[1], b[2])
        if not self.segment(a, apex) or not self.segment(across, b): return False
        if self.segment(apex, across): return True
        if abs(a[0] - b[0]) + abs(a[2] - b[2]) > 4: return False
        xf, zf = (b[0], apex[1], a[2]), (a[0], apex[1], b[2])
        return (self.segment(apex, xf) and self.segment(xf, across)) or (self.segment(apex, zf) and self.segment(zf, across))

    def path(self, start, goal):
        start = (start[0] + .5, float(start[1]), start[2] + .5); goal = (goal[0] + .5, float(goal[1]), goal[2] + .5)
        if start not in self.columns.get((math.floor(start[0]), math.floor(start[2])), ()): return []
        seen, queue = {start: None}, [start]
        while queue:
            a = queue.pop(0)
            if a == goal:
                out = []
                while a is not None: out.append(a); a = seen[a]
                return out[::-1]
            ax, az = math.floor(a[0]), math.floor(a[2])
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if dx * dx + dz * dz > 16: continue
                    for b in self.columns.get((ax + dx, az + dz), ()):
                        if b in seen or b[1] - a[1] > 1.25: continue
                        if any(self.jump(a, b, h * .25) for h in range(6)): seen[b] = a; queue.append(b)
        return []


def prune_bridge_bypasses(t, functional):
    """Remove dressing blocks that let a crouched Ori reach a dark bridge's exit without the bridge (both test
    variants: bare template, and grass up to the bridge deck around it). Returns the removed cells."""
    removed = []
    for bridge in t.features.get("bridges", []):
        deck = max(p[1] for p in bridge["platforms"])
        for rim in (0, 5):
            for _ in range(200):
                path = WalkGraph(t.blocks, t.size, rim, deck).path(bridge["entry"], bridge["exit"])
                if not path: break
                culprits = []
                for n in path:
                    cell = (math.floor(n[0]), math.ceil(n[1]) - 1, math.floor(n[2]))
                    if cell in t.blocks and t.blocks[cell] != functional.get(cell) and functional.get(cell, ("minecraft:air",))[0] == "minecraft:air":
                        culprits.append(cell)
                if not culprits:
                    raise ValueError(f"{t.name}: dark bridge bypass without dressing: {path}")
                for c in culprits:  # drop the foothold and everything dressed above it in that column
                    for y in range(c[1], t.size[1]):
                        p = (c[0], y, c[2])
                        if p in t.blocks and t.blocks[p] != functional.get(p) and functional.get(p, ("minecraft:air",))[0] == "minecraft:air":
                            t.blocks[p] = functional.get(p, ("minecraft:air", ())); removed.append(p)
    return removed
