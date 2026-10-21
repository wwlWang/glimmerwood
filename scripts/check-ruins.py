"""Check generated ruin assets without changing the repository; --write publishes them."""
import argparse
import gzip
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parents[1]
NAMES = ("forest_spring", "creek_ruins", "cave_altar")


def content(path):
    """Ignore JSON formatting/key order and gzip envelope metadata, not NBT data."""
    raw = path.read_bytes()
    if path.suffix == ".nbt":
        return gzip.decompress(raw)
    if path.suffix == ".json":
        return json.loads(raw.decode("utf-8"))
    raise ValueError(f"Unsupported generated asset: {path}")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_contracts(root):
    data = root / "src/main/resources/data/orimod"
    layouts = root / "src/main/resources/orimod/ruin_layout"
    for name in NAMES:
        nbt = nbtlib.load(data / "structure/ruins" / (name + ".nbt"))
        layout = json.loads((layouts / (name + ".json")).read_text(encoding="utf-8"))
        require(list(map(int, nbt["size"])) == layout["size"], f"{name}: template/layout size mismatch")
        require(len(nbt["entities"]) == 0, f"{name}: template contains entities")
        palette = [str(s["Name"]) for s in nbt["palette"]]
        blocks = {tuple(map(int, b["pos"])): palette[int(b["state"])] for b in nbt["blocks"]}

        def within(point):
            return len(point) == 3 and all(0 <= point[i] < layout["size"][i] for i in range(3))

        for point in blocks:
            require(within(point), f"{name}: block outside template: {point}")
        for anchor in layout["critical"]:
            require(blocks.get(tuple(anchor["position"])) == anchor["block"],
                    f"{name}: missing critical anchor: {anchor}")
        require(blocks.get(tuple(layout["shrine"])) == "orimod:" + name + "_shrine",
                f"{name}: missing shrine")
        for area in layout["clearances"]:
            for x in range(area["from"][0], area["to"][0] + 1):
                for y in range(area["from"][1], area["to"][1] + 1):
                    for z in range(area["from"][2], area["to"][2] + 1):
                        require(blocks.get((x, y, z), "minecraft:air") in
                                ("minecraft:air", "orimod:bash_anchor", "orimod:grapple_flower"),
                                f"{name}: blocked clearance: {(x, y, z)}")
        # R44–R70 additions are runtime placements, not base NBT block assertions.
        points = [layout["safe"], layout["entry"]]
        points += [c["position"] for c in layout["checkpoints"]]
        for cell in layout["cells"]:
            require(cell["kind"] in ("life", "energy"), f"{name}: invalid cell kind: {cell}")
            points.append(cell["position"])
        for key in ("updrafts", "keystones", "spirit_sand"):
            points += layout.get(key, [])
        for key in ("boss_altar", "map_stone", "storm_altar"):
            if key in layout:
                points.append(layout[key])
        for key in ("vault", "gloom_water"):
            if key in layout:
                area = layout[key]
                require(all(a <= b for a, b in zip(area["from"], area["to"])),
                        f"{name}: reversed {key} bounds")
                points += [area["from"], area["to"]] + area.get("door", [])
        for point in points:
            require(within(point), f"{name}: route feature outside template: {point}")
        secret = layout["secret"]
        def cuboid(a, b):
            return {(x,y,z) for x in range(a[0],b[0]+1) for y in range(a[1],b[1]+1) for z in range(a[2],b[2]+1)}
        room = cuboid(secret["from"], secret["to"])
        seal = cuboid(*secret["seal"])
        interior = cuboid(*secret["interior"])
        require(all(within(p) for p in room), f"{name}: secret outside old template")
        require(seal <= room and interior <= room and not seal.intersection(interior), f"{name}: invalid secret geometry")
        require(tuple(secret["vessel"]) in interior, f"{name}: secret vessel outside chamber")
        require(all(blocks.get(p) in ("minecraft:air", "minecraft:ladder", "orimod:spirit_light_vessel") for p in interior),
                f"{name}: blocked secret chamber")
        require(secret["blocks"] in ("wall", "floor") and secret["facing"] in ("west", "east", "up"), f"{name}: invalid secret kind/direction")
        require(tuple(secret["entrance"]) in seal, f"{name}: entrance not in seal")
        require(all(blocks.get(p) == "orimod:cracked_ruin_" + secret["blocks"] for p in seal), f"{name}: missing secret seal")
        require(len(seal) <= 64, f"{name}: seal exceeds collapse cap")
        for p in [secret["vessel"], *layout["vessels"]]:
            require(within(p) and blocks.get(tuple(p)) == "orimod:spirit_light_vessel", f"{name}: missing vessel: {p}")
        require(len(layout["vessels"]) in (2,3), f"{name}: wrong decorative vessel count")
        protected = {tuple(map(int,p)) for p in points}
        protected.update(tuple(a["position"]) for a in layout["critical"])
        for area in layout["clearances"]:
            protected.update(cuboid(area["from"],area["to"]))
        if "vault" in layout:
            protected.update(cuboid(layout["vault"]["from"],layout["vault"]["to"]))
        if "spirit_sand" in layout:
            a,b = layout["spirit_sand"]
            protected.update(cuboid(a,[b[0]+2,b[1],b[2]]))
        require(not room.intersection(protected), f"{name}: secret intersects existing route feature")
        bridges=layout.get("bridges",[])
        require(len(bridges)==1, f"{name}: expected one light bridge")
        for bridge in bridges:
            platforms={tuple(p) for p in bridge["platforms"]}
            require(len(platforms)=={"forest_spring":8,"creek_ruins":5,"cave_altar":12}[name], f"{name}: wrong bridge length")
            require(all(within(p) and blocks.get(p)=="orimod:spirit_platform" for p in platforms), f"{name}: missing light platform")
            require(not platforms.intersection(protected|room), f"{name}: bridge intersects protected route")
            require(all(blocks.get(tuple(p))=="orimod:bash_anchor" for p in bridge["lanterns"]), f"{name}: missing bridge lamp")
            require(len(bridge["lanterns"])=={"forest_spring":1,"creek_ruins":0,"cave_altar":2}[name], f"{name}: wrong bridge lamp count")
            empty={"minecraft:air","orimod:bash_anchor","minecraft:ladder"}
            nodes={tuple(bridge["entry"]),tuple(bridge["exit"])}|{(x,y+1,z) for x,y,z in platforms}
            for x,y,z in nodes:
                require(all(blocks.get((x,yy,z),"minecraft:air") in empty for yy in (y,y+1)), f"{name}: blocked bridge headroom {(x,y,z)}")
            def connected(start,end,points):
                seen={start};queue=[start]
                while queue:
                    a=queue.pop()
                    for b in points-seen:
                        if abs(a[1]-b[1])<=1 and (a[0]-b[0])**2+(a[2]-b[2])**2<=4:
                            seen.add(b);queue.append(b)
                return end in seen
            a,b=tuple(bridge["entry"]),tuple(bridge["exit"])
            require(connected(a,b,nodes) and connected(b,a,nodes), f"{name}: light bridge cannot be traversed both ways")
            for x,y,z in bridge["escape"]:
                require(within((x,y,z)), f"{name}: escape outside template")
                require(all(blocks.get((x,yy,z),"minecraft:air") in empty|{"orimod:spirit_platform","minecraft:water"} for yy in (y,y+1)), f"{name}: blocked unlit escape")
                require(blocks.get((x,y,z))=="minecraft:ladder" or blocks.get((x,y-1,z),"minecraft:air") not in empty|{"orimod:spirit_platform","minecraft:water"}, f"{name}: unsupported escape")
        beetles=layout.get("beetles",[])
        require(len(beetles)==(0 if name=="forest_spring" else 2),f"{name}: wrong beetle count")
        require(len({tuple(p) for p in beetles})==len(beetles),f"{name}: duplicate beetle point")
        for x,y,z in beetles:
            require(within((x,y,z)) and blocks.get((x,y,z))=="minecraft:air" and blocks.get((x,y+1,z))=="minecraft:air",f"{name}: blocked beetle point")
            require(blocks.get((x,y-1,z),"minecraft:air") not in ("minecraft:air","minecraft:water","orimod:spirit_platform"),f"{name}: unsupported beetle point")
        print(name, "contract passed")


def check(root=ROOT, write=False):
    # The subprocess cannot fall back to the repository: its destination is
    # mandatory, and -B also prevents Python bytecode writes next to the sources.
    with tempfile.TemporaryDirectory(prefix="orimod-ruins-") as directory:
        generated = Path(directory)
        subprocess.run([sys.executable, "-B", str(ROOT / "scripts/art/build_ruins.py"),
                        "--output-root", str(generated)], check=True)
        validate_contracts(generated)
        paths = sorted(p.relative_to(generated) for p in generated.rglob("*") if p.is_file())
        require(bool(paths), "Generator produced no assets")
        expected = {p: content(generated / p) for p in paths}
        written = 0
        if write:
            # Validate all outputs before copying any; unchanged files keep their mtimes.
            for path in paths:
                target, source = root / path, generated / path
                if not target.exists() or target.read_bytes() != source.read_bytes():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(source.read_bytes())
                    written += 1
        differences = []
        for path in paths:
            try:
                if content(root / path) != expected[path]:
                    differences.append(f"{path}: content differs")
            except (OSError, ValueError, EOFError) as error:
                differences.append(f"{path}: {error}")
        if differences:
            print(json.dumps({"status": "failed", "mode": "write" if write else "check",
                              "differences": differences}, indent=2))
            return 1
        if write:
            require(all((root / p).read_bytes() == (generated / p).read_bytes() for p in paths),
                    "Published assets differ from generated bytes")
        hashes = {str(p): hashlib.sha256((root / p).read_bytes()).hexdigest() for p in paths}
        print(json.dumps({"status": "passed", "mode": "write" if write else "check",
                          "files": len(paths), "written": written, "sha256": hashes}, indent=2))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true",
                        help="Explicitly replace repository assets with validated generated bytes.")
    return check(write=parser.parse_args().write)


if __name__ == "__main__":
    sys.exit(main())
