"""Author the three ruin templates and their route/secret contracts.

Requires the nbtlib version in requirements-art.txt and an explicit output root.
The generated JSON is also read by the server, so safety checks follow the same layout.
Use check-ruins.py --write to publish generated assets to the repository.
"""
import argparse
from pathlib import Path
import gzip
import io
import json
import nbtlib as nbt

NAMES = ("forest_spring", "creek_ruins", "cave_altar")
# Preserve the original templates' gzip envelope (OS=10, TOPS-20), independently
# of the host OS / Python version. This byte describes no Minecraft semantics.
GZIP_OS = 0x0a


def compressed_nbt(content):
    compressed = bytearray(gzip.compress(content, compresslevel=9, mtime=0))
    compressed[9] = GZIP_OS
    return bytes(compressed)


class Template:
    def __init__(self, root, name, size, pad=0):
        self.root = root
        self.data = root / "src/main/resources/data/orimod"
        self.name, self.size, self.pad = name, (size[0], size[1] + pad, size[2]), pad
        self.blocks = {}
        self.critical = []
        # R44 onward: RuinPiece.placeCells adds these after placing the base NBT.
        # Keep them in the route contract, not in the palette / template blocks.
        self.features = {}

    def shifted(self, position):
        return [position[0], position[1] + self.pad, position[2]]

    def cell(self, position, kind):
        self.features.setdefault("cells", []).append({"position": self.shifted(position), "kind": kind})

    def updrafts(self, a, b):
        self.features["updrafts"] = [self.shifted((x, y, z))
                                    for x in range(a[0], b[0] + 1)
                                    for y in range(a[1], b[1] + 1)
                                    for z in range(a[2], b[2] + 1)]

    def put(self, x, y, z, name, **properties):
        if ":" not in name:
            name = "minecraft:" + name
        assert 0 <= x < self.size[0] and 0 <= y + self.pad < self.size[1] and 0 <= z < self.size[2]
        self.blocks[x, y + self.pad, z] = (name, tuple(sorted((k, str(v)) for k, v in properties.items())))

    def box(self, a, b, block, **properties):
        for x in range(a[0], b[0] + 1):
            for y in range(a[1], b[1] + 1):
                for z in range(a[2], b[2] + 1):
                    self.put(x, y, z, block, **properties)

    def platform(self, a, b):
        self.box(a, b, "mossy_stone_bricks")
        cx, cz = (a[0] + b[0]) // 2, (a[2] + b[2]) // 2
        if a[1] > 2:
            self.box((cx, 2, cz), (cx + 1, a[1] - 1, cz + 1), "mossy_stone_bricks")
        self.put(cx, b[1], cz, "sea_lantern")

    def important(self, x, y, z, block):
        self.put(x, y, z, block)
        self.critical.append({"position": [x, y + self.pad, z], "block": block if ":" in block else "minecraft:" + block})

    def secret(self, shell, interior, seal, entrance, facing, kind, vessel, decorations):
        """An optional side branch, fully inside the old template footprint; never a challenge checkpoint.

        Geometry is baked into NBT. Coordinates are exported for new pieces to snapshot in their saves.
        R44-R70 runtime placements and the existing route contract remain untouched.
        """
        self.box(*shell, "deepslate_bricks" if self.name == "cave_altar" else "mossy_stone_bricks")
        self.box(*interior, "air")
        self.box(*seal, "orimod:cracked_ruin_" + kind)
        self.put(*vessel, "orimod:spirit_light_vessel")
        for pos in decorations:
            self.put(*pos, "orimod:spirit_light_vessel")
        self.features["secret"] = {
            "from": self.shifted(shell[0]), "to": self.shifted(shell[1]),
            "interior": [self.shifted(p) for p in interior], "seal": [self.shifted(p) for p in seal],
            "entrance": self.shifted(entrance), "facing": facing, "blocks": kind, "vessel": self.shifted(vessel)}
        self.features["vessels"] = [self.shifted(p) for p in decorations]

    def light_bridge(self, entry, exit, platforms, lanterns, escape, facing):
        """R104 optional route: entry/exit/escape are feet positions; platforms/lamps are block positions."""
        for pos in platforms:
            self.put(*pos, "orimod:spirit_platform", lit="false", fading="false", blink="false")
        for pos in lanterns:
            self.put(*pos, "orimod:bash_anchor", lit="false")
        self.features["bridges"] = [{"entry": self.shifted(entry), "exit": self.shifted(exit),
            "platforms": [self.shifted(p) for p in platforms], "lanterns": [self.shifted(p) for p in lanterns],
            "escape": [self.shifted(p) for p in escape], "facing": facing}]

    def export(self, shrine, safe, entry, surface, checkpoints, clearances):
        self.important(*shrine, "orimod:" + self.name + "_shrine")
        palette, indexes, blocks = [], {}, []
        for position, state in sorted(self.blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            if state not in indexes:
                indexes[state] = len(palette)
                value = {"Name": nbt.String(state[0])}
                if state[1]:
                    value["Properties"] = nbt.Compound({k: nbt.String(v) for k, v in state[1]})
                palette.append(nbt.Compound(value))
            blocks.append(nbt.Compound({"pos": nbt.List[nbt.Int](position), "state": nbt.Int(indexes[state])}))
        file = nbt.File({"DataVersion": nbt.Int(3955), "size": nbt.List[nbt.Int](self.size),
                         "palette": nbt.List[nbt.Compound](palette), "blocks": nbt.List[nbt.Compound](blocks),
                         "entities": nbt.List[nbt.Compound]()})
        stream = io.BytesIO()
        file.write(stream)
        target = self.data / "structure/ruins" / (self.name + ".nbt")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(compressed_nbt(stream.getvalue()))
        shifted = self.shifted
        layout = {"size": self.size, "shrine": shifted(shrine), "safe": shifted(safe), "entry": shifted(entry),
                  "surface_y": surface + self.pad, "critical": self.critical,
                  "clearances": [{"from": shifted(a), "to": shifted(b)} for a, b in clearances],
                  "checkpoints": [{"position": shifted(p), "action": action, "count": count} for p, action, count in checkpoints]}
        layout.update(self.features)
        write(self.root / "src/main/resources/orimod/ruin_layout" / (self.name + ".json"), layout)
        write(self.data / "worldgen/structure" / (self.name + ".json"), {
            "type": "orimod:ruin", "kind": self.name, "biomes": "#orimod:has_ruins", "spawn_overrides": {},
            "step": "top_layer_modification", "terrain_adaptation": "none"})
        print(f"{self.name}: {len(blocks)} blocks, {len(palette)} states, {target.stat().st_size} bytes")


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def surface(root, name, size):
    t = Template(root, name, size, pad=4)
    w, h, d = size
    t.box((0, -4, 0), (w - 1, 0, d - 1), "stone")
    t.box((0, 1, 0), (w - 1, h - 1, d - 1), "air")
    t.box((0, 1, 0), (w - 1, 1, d - 1), "moss_block")
    # Contained shallow spring with a dry entry, exits along both edges.
    t.box((3, 1, 7), (w - 4, 1, d - 4), "water", level="0")
    for x in (1, w - 2):
        for z in range(3, d - 1, 5):
            t.put(x, 2, z, "azalea")
            t.put(x, 1, z, "sea_lantern")
    for x in range(w // 2 - 2, w // 2 + 3):
        # Reach terrain up to four blocks below the deck, including the low side
        # of an accepted slope. The previous three steps exposed a climbable-only
        # foundation wall when the entrance was on that side.
        for z in range(5):
            t.box((x, -4, z), (x, z - 3, z), "mossy_stone_bricks")
            t.put(x, z - 3, z, "mossy_stone_brick_stairs", facing="south", half="bottom", shape="straight", waterlogged="false")
            t.box((x, z - 2, z), (x, 1, z), "air")
    return t


def forest(root):
    t = surface(root, "forest_spring", (19, 13, 31))
    # Two root arches frame the route; the inner face is the wall-jump surface.
    t.box((5, 2, 4), (5, 8, 12), "stripped_dark_oak_log", axis="y")
    for y in (3, 5, 7):
        t.important(5, y, 9, "stripped_dark_oak_log")
    t.box((13, 2, 8), (14, 8, 10), "dark_oak_log", axis="y")
    t.box((5, 9, 8), (14, 9, 10), "dark_oak_log", axis="x")
    t.box((4, 10, 7), (15, 10, 11), "azalea_leaves", persistent="true")
    t.platform((7, 3, 7), (10, 3, 10))
    t.platform((7, 5, 14), (10, 5, 17))
    t.platform((7, 4, 23), (11, 4, 28))
    for x, z in ((3, 24), (14, 22)):
        t.box((x, 2, z), (x, 6, z), "dark_oak_log", axis="y")
        t.box((x - 1, 7, z - 1), (x + 1, 7, z + 1), "flowering_azalea_leaves", persistent="true")
    t.cell((5, 9, 12), "life")  # R44: reward above the wall-jump roots.
    t.updrafts((8, 2, 20), (10, 2, 21))  # R46: lift below the final glide gap.
    t.features["map_stone"] = t.shifted((5, 2, 3))  # R62: beside the entry.
    t.features["storm_altar"] = t.shifted((4, 2, 29))  # R70: rear storm arena.
    # R103: east of the middle route, away from the root arch, shrine, cells and storm arena.
    t.secret(((13, 1, 14), (17, 5, 18)), ((14, 2, 15), (16, 4, 17)),
             ((13, 2, 15), (13, 4, 17)), (13, 2, 16), "west", "wall", (15, 2, 16), [(2, 2, 6), (16, 2, 29)])
    t.put(12, 1, 16, "mossy_stone_bricks")  # Dry footing in front of the cracked doorway.
    # R104 interior water court. Walls rise four blocks above the deck, independent of outside terrain.
    # The reward island is three blocks above the pool floor: the low drain is an exit, not a bypass.
    t.box((3, 1, 14), (5, 1, 21), "water", level="0")
    t.box((3, 2, 14), (5, 7, 23), "air")
    for x in (2, 6):
        t.box((x, 1, 13), (x, 7, 24), "mossy_stone_bricks")
    for z in (13, 24):
        t.box((2, 1, z), (6, 7, z), "mossy_stone_bricks")
    t.box((4, 4, 13), (4, 6, 13), "air")  # Raised entrance; original root/route stays east of it.
    t.box((4, 1, 6), (4, 1, 9), "mossy_stone_bricks")  # Dry approach from the original entry bank.
    t.box((4, 1, 10), (4, 2, 10), "mossy_stone_bricks")
    t.box((4, 1, 11), (4, 3, 12), "mossy_stone_bricks")
    t.box((3, 0, 22), (5, 3, 23), "mossy_stone_bricks")
    t.box((2, 1, 14), (2, 2, 14), "air")  # Two-high drain returns to the western outer bank.
    t.put(1, 1, 14, "air")  # Step outside the drain before jumping up; do not jump into its low lintel.
    t.box((4, 7, 18), (4, 7, 23), "stripped_dark_oak_log", axis="z")
    t.put(4, 6, 18, "chain", axis="y", waterlogged="false")
    t.put(4, 4, 23, "orimod:spirit_light_vessel")
    t.features["vessels"].append(t.shifted((4, 4, 23)))  # Ordinary reward; neither a secret nor a map-stone target.
    t.light_bridge((4, 4, 13), (4, 4, 22), [(4, 3, z) for z in range(14, 22)], [(4, 5, 18)],
                   [(4, 1, z) for z in range(14, 22)] + [(3, 1, 14), (2, 1, 14), (1, 1, 14), (0, 2, 14)], "south")
    t.export((12, 2, 4), (9.5, 2, 5.5), (9.5, 2, 4.5), 2,
             [((8.5, 4, 8.5), "wall_jump", 1), ((8.5, 6, 15.5), "jump", 2), ((9.5, 5, 25.5), "dash", 1)],
             [((6, 2, 4), (10, 6, 6)), ((6, 4, 7), (10, 7, 10)), ((7, 4, 11), (10, 8, 13)),
              ((7, 6, 14), (10, 8, 17)), ((7, 5, 18), (11, 8, 22)), ((7, 5, 23), (11, 7, 28))])


def creek(root):
    t = surface(root, "creek_ruins", (21, 17, 35))
    for x in (3, 17):
        for z, height in ((8, 5), (18, 9), (30, 6)):
            t.box((x, 2, z), (x + 1, height, z + 1), "mossy_stone_bricks")
            t.put(x, height + 1, z, "sea_lantern")
    t.important(10, 5, 10, "orimod:bash_anchor")
    t.platform((8, 6, 14), (12, 6, 18))
    t.platform((8, 3, 26), (13, 3, 32))
    t.box((4, 10, 19), (16, 10, 20), "stone_brick_slab", type="bottom", waterlogged="false")
    t.cell((3, 11, 18), "energy")  # R44: above the tall illuminated pillar.
    t.updrafts((9, 2, 22), (11, 2, 23))  # R46: lift between the two platforms.
    # R59: only existing water under the glide route is corrupted at placement.
    t.features["gloom_water"] = {"from": t.shifted((6, 1, 19)), "to": t.shifted((14, 1, 26))}
    t.features["map_stone"] = t.shifted((7, 2, 4))  # R62: beside the entry.
    # R103: optional western deck, with a room in the existing four-block foundation pad.
    # Keep the original Bash/Glide platform floors, support pillars and corrupted creek unchanged.
    t.secret(((1, -2, 23), (5, 2, 27)), ((2, -1, 24), (4, 1, 26)),
             ((2, 2, 24), (4, 2, 26)), (3, 2, 25), "up", "floor", (3, -1, 25), [(2, 2, 6), (18, 2, 32)])
    t.box((4, -1, 25), (4, 1, 25), "ladder", facing="west", waterlogged="false")  # Return without another ability.
    # R104 east shaft, independent of the original Bash/Glide route and the western secret.
    t.box((13, 1, 10), (18, 1, 14), "mossy_stone_bricks")
    t.box((13, 2, 10), (13, 8, 14), "mossy_stone_bricks")
    t.box((18, 2, 10), (18, 8, 14), "mossy_stone_bricks")
    t.box((14, 2, 14), (17, 5, 14), "mossy_stone_bricks")
    t.box((14, 6, 14), (17, 6, 15), "mossy_stone_bricks")
    t.box((12, 6, 15), (13, 6, 15), "mossy_stone_bricks")  # Exit rejoins existing upper platform.
    # Drop into water to leave the dark shaft; a ladder here would solve the ascent backwards.
    t.put(17, 1, 13, "water", level="0")
    t.light_bridge((14, 2, 10), (15, 7, 14), [(14, 2, 11), (15, 3, 11), (16, 4, 11), (16, 5, 12), (15, 6, 12)], [],
                   [(17, 1, 13), (17, 2, 12), (16, 2, 12), (15, 2, 12), (14, 2, 12), (14, 2, 11), (14, 2, 10)], "south")
    t.features["beetles"] = [t.shifted(p) for p in ((15, 2, 5), (1, 2, 20))]
    t.export((13, 2, 4), (10.5, 2, 5.5), (10.5, 2, 4.5), 2,
             [((10.5, 7, 16.5), "bash", 1), ((10.5, 4, 30.5), "glide", 1)],
             [((9, 2, 6), (11, 10, 13)), ((9, 7, 14), (11, 9, 18)),
              ((9, 4, 19), (11, 9, 25)), ((9, 4, 26), (12, 7, 32))])


def cave(root):
    t = Template(root, "cave_altar", (23, 19, 39))
    t.box((0, 0, 0), (22, 13, 38), "deepslate_bricks")
    t.box((1, 2, 14), (21, 12, 37), "air")
    t.box((6, 12, 0), (16, 18, 13), "air")
    # Ten broad descending steps; the 4-block-high corridor is carved with them.
    for z in range(2, 14):
        floor = max(1, 13 - z)
        t.box((9, floor + 1, z), (13, min(18, floor + 4), z), "air")
        t.box((9, floor, z), (13, floor, z), "polished_deepslate")
    t.box((8, 12, 0), (14, 12, 1), "mossy_stone_bricks")
    for x in (7, 15):
        t.box((x, 12, 1), (x, 16, 1), "mossy_stone_bricks")
        t.put(x, 17, 1, "sea_lantern")
    t.box((7, 17, 1), (15, 17, 1), "mossy_stone_bricks")
    for x in (2, 20):
        for z in (16, 22, 28, 35):
            t.put(x, 3, z, "sea_lantern")
            t.put(x, 2, z, "calcite")
    t.platform((8, 7, 22), (14, 7, 25))
    t.important(11, 9, 21, "orimod:grapple_flower")
    t.put(11, 9, 23, "orimod:grapple_flower")
    t.platform((8, 1, 27), (14, 1, 31))
    t.important(11, 1, 29, "sea_lantern")
    vault_from, vault_to = (1, 2, 18), (4, 4, 21)
    sand_from, sand_to = (14, 2, 12), (17, 3, 13)
    t.cell((8, 8, 25), "life")  # R44: edge of the grapple platform.
    # R57: inside the hollow vault; R66: the pocket two blocks past the R64 tunnel.
    t.cell((vault_from[0] + 1, vault_from[1], vault_from[2] + 1), "energy")
    t.cell((sand_to[0] + 2, sand_from[1], sand_to[2]), "life")
    t.features["boss_altar"] = t.shifted((16, 1, 33))  # R50: arena floor.
    t.features["keystones"] = [t.shifted(p) for p in ((2, 4, 28), (20, 4, 16))]
    # R57: a two-by-two door in the east wall, below the vault ceiling.
    t.features["vault"] = {
        "from": t.shifted(vault_from), "to": t.shifted(vault_to),
        "door": [t.shifted((vault_to[0], y, z))
                 for y in range(vault_from[1], vault_to[1])
                 for z in range(vault_from[2] + 1, vault_to[2])]}
    t.features["map_stone"] = t.shifted((7, 2, 15))  # R62: beside the shrine.
    t.features["spirit_sand"] = [t.shifted(sand_from), t.shifted(sand_to)]
    # R103: south of (not inside or through) the key vault; east-facing three-block-thick seal.
    t.secret(((1, 1, 22), (7, 5, 26)), ((2, 2, 23), (4, 4, 25)),
             ((5, 2, 23), (7, 4, 25)), (7, 2, 24), "east", "wall", (3, 2, 24), [(5, 2, 16), (18, 2, 35)])
    # R104 east gallery: two lamps with overlapping reach; first cannot illuminate its far half.
    # The lower floor returns to the entrance only; the raised far alcove has no rear/side entrance.
    for x in (17, 19):
        t.box((x, 2, 19), (x, 6, 31), "deepslate_bricks")
    t.box((17, 7, 19), (19, 7, 32), "deepslate_bricks")
    t.box((17, 2, 32), (19, 6, 32), "deepslate_bricks")
    for z in (18, 31):
        t.box((18 if z == 31 else 17, 2, z), (18 if z == 31 else 19, 3, z), "polished_deepslate")
    t.box((17, 2, 17), (19, 2, 17), "polished_deepslate")
    t.put(18, 2, 19, "polished_deepslate")  # One return step, only at the near mouth.
    t.light_bridge((18, 4, 18), (18, 4, 31), [(18, 3, z) for z in range(19, 31)], [(18, 5, 20), (18, 5, 28)],
                   [(18, 3 if z == 19 else 2, z) for z in range(19, 31)], "south")
    t.features["beetles"] = [t.shifted(p) for p in ((15, 2, 18), (6, 2, 30))]
    t.export((8, 2, 15), (11.5, 2, 17.5), (11.5, 13, 1.5), 13,
             [((11.5, 8, 23.5), "grapple", 1), ((11.5, 2, 29.5), "stomp", 1), ((11.5, 2, 32.5), "blade", 1)],
             [((10, 3, 18), (12, 8, 21)), ((10, 8, 22), (12, 10, 25)), ((10, 2, 26), (12, 11, 34))])


def build(root):
    data = root / "src/main/resources/data/orimod"
    assets = root / "src/main/resources/assets/orimod"
    forest(root); creek(root); cave(root)
    write(data / "tags/worldgen/biome/has_ruins.json", {"replace": False, "values": [
        "minecraft:forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:flower_forest",
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:taiga"]})
    write(data / "worldgen/structure_set/spirit_ruins.json", {
        "structures": [{"structure": "orimod:" + name, "weight": 1} for name in NAMES],
        "placement": {"type": "minecraft:random_spread", "spacing": 6, "separation": 3, "salt": 19721014}})
    for name in NAMES:
        block = name + "_shrine"
        # R76: each ruin has its own original top/side, with one shared base.
        theme = name.split("_", 1)[0]
        write(assets / "blockstates" / (block + ".json"), {"variants": {"": {"model": "orimod:block/" + block}}})
        write(assets / "models/block" / (block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"orimod:block/{theme}_shrine_top", "side": f"orimod:block/{theme}_shrine_side",
            "bottom": "orimod:block/shrine_bottom"}})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", required=True, type=Path,
                        help="Generate a src/main/resources tree here; no implicit repository writes.")
    build(parser.parse_args().output_root)
