"""Author the three vanilla-block ruin templates and their runtime route contracts.

Requires nbtlib. Deterministic: no random terrain or palette choices enter these assets.
The generated JSON is also read by the server, so safety checks follow the same layout.
"""
from pathlib import Path
import gzip
import io
import json
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/orimod"
ASSETS = ROOT / "src/main/resources/assets/orimod"


class Template:
    def __init__(self, name, size, pad=0):
        self.name, self.size, self.pad = name, (size[0], size[1] + pad, size[2]), pad
        self.blocks = {}
        self.critical = []

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
        target = DATA / "structure/ruins" / (self.name + ".nbt")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(gzip.compress(stream.getvalue(), mtime=0))
        shifted = lambda p: [p[0], p[1] + self.pad, p[2]]
        layout = {"size": self.size, "shrine": shifted(shrine), "safe": shifted(safe), "entry": shifted(entry),
                  "surface_y": surface + self.pad, "critical": self.critical,
                  "clearances": [{"from": shifted(a), "to": shifted(b)} for a, b in clearances],
                  "checkpoints": [{"position": shifted(p), "action": action, "count": count} for p, action, count in checkpoints]}
        write(DATA / "ruin_layout" / (self.name + ".json"), layout)
        write(DATA / "worldgen/structure" / (self.name + ".json"), {
            "type": "orimod:ruin", "kind": self.name, "biomes": "#orimod:has_ruins", "spawn_overrides": {},
            "step": "top_layer_modification", "terrain_adaptation": "none"})
        print(f"{self.name}: {len(blocks)} blocks, {len(palette)} states, {target.stat().st_size} bytes")


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def surface(name, size):
    t = Template(name, size, pad=4)
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


def forest():
    t = surface("forest_spring", (19, 13, 31))
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
    t.export((12, 2, 4), (9.5, 2, 5.5), (9.5, 2, 4.5), 2,
             [((8.5, 4, 8.5), "wall_jump", 1), ((8.5, 6, 15.5), "jump", 2), ((9.5, 5, 25.5), "dash", 1)],
             [((6, 2, 4), (10, 6, 6)), ((6, 4, 7), (10, 7, 10)), ((7, 4, 11), (10, 8, 13)),
              ((7, 6, 14), (10, 8, 17)), ((7, 5, 18), (11, 8, 22)), ((7, 5, 23), (11, 7, 28))])


def creek():
    t = surface("creek_ruins", (21, 17, 35))
    for x in (3, 17):
        for z, height in ((8, 5), (18, 9), (30, 6)):
            t.box((x, 2, z), (x + 1, height, z + 1), "mossy_stone_bricks")
            t.put(x, height + 1, z, "sea_lantern")
    t.important(10, 5, 10, "orimod:bash_anchor")
    t.platform((8, 6, 14), (12, 6, 18))
    t.platform((8, 3, 26), (13, 3, 32))
    t.box((4, 10, 19), (16, 10, 20), "stone_brick_slab", type="bottom", waterlogged="false")
    t.export((13, 2, 4), (10.5, 2, 5.5), (10.5, 2, 4.5), 2,
             [((10.5, 7, 16.5), "bash", 1), ((10.5, 4, 30.5), "glide", 1)],
             [((9, 2, 6), (11, 10, 13)), ((9, 7, 14), (11, 9, 18)),
              ((9, 4, 19), (11, 9, 25)), ((9, 4, 26), (12, 7, 32))])


def cave():
    t = Template("cave_altar", (23, 19, 39))
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
    t.export((8, 2, 15), (11.5, 2, 17.5), (11.5, 13, 1.5), 13,
             [((11.5, 8, 23.5), "grapple", 1), ((11.5, 2, 29.5), "stomp", 1), ((11.5, 2, 32.5), "blade", 1)],
             [((10, 3, 18), (12, 8, 21)), ((10, 8, 22), (12, 10, 25)), ((10, 2, 26), (12, 11, 34))])


if __name__ == "__main__":
    forest(); creek(); cave()
    write(DATA / "tags/worldgen/biome/has_ruins.json", {"replace": False, "values": [
        "minecraft:forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:flower_forest",
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:taiga"]})
    write(DATA / "worldgen/structure_set/spirit_ruins.json", {
        "structures": [{"structure": "orimod:" + name, "weight": 1} for name in ("forest_spring", "creek_ruins", "cave_altar")],
        "placement": {"type": "minecraft:random_spread", "spacing": 16, "separation": 6, "salt": 19721014}})
    for name in ("forest_spring", "creek_ruins", "cave_altar"):
        block = name + "_shrine"
        write(ASSETS / "blockstates" / (block + ".json"), {"variants": {"": {"model": "orimod:block/" + block}}})
        write(ASSETS / "models/block" / (block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": "minecraft:block/respawn_anchor_top", "bottom": "minecraft:block/chiseled_deepslate",
            "side": "minecraft:block/respawn_anchor_side1"}})
