"""Check the authored NBT/JSON contract and deterministic reconstruction, without running MC."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
import nbtlib

root = Path(__file__).resolve().parents[1]
data = root / 'src/main/resources/data/orimod'
names = ('forest_spring', 'creek_ruins', 'cave_altar')
paths = [data / folder / (name + suffix) for name in names for folder, suffix in
         [('structure/ruins', '.nbt'), ('worldgen/structure', '.json')]]
layouts = root / 'src/main/resources/orimod/ruin_layout'
paths += [layouts / (name + '.json') for name in names]
paths += [data / 'worldgen/structure_set/spirit_ruins.json', data / 'tags/worldgen/biome/has_ruins.json']
before = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
subprocess.run([sys.executable, str(root / 'scripts/art/build_ruins.py')], check=True)
assert before == {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}, 'Generated assets changed'
for name in names:
    nbt = nbtlib.load(data / 'structure/ruins' / (name + '.nbt'))
    layout = json.loads((layouts / (name + '.json')).read_text())
    assert list(map(int, nbt['size'])) == layout['size']
    assert len(nbt['entities']) == 0
    palette = [str(s['Name']) for s in nbt['palette']]
    blocks = {tuple(map(int, b['pos'])): palette[int(b['state'])] for b in nbt['blocks']}
    for point, block in blocks.items(): assert all(0 <= point[i] < layout['size'][i] for i in range(3))
    for anchor in layout['critical']: assert blocks[tuple(anchor['position'])] == anchor['block']
    assert blocks[tuple(layout['shrine'])] == 'orimod:' + name + '_shrine'
    for area in layout['clearances']:
        for x in range(area['from'][0], area['to'][0] + 1):
            for y in range(area['from'][1], area['to'][1] + 1):
                for z in range(area['from'][2], area['to'][2] + 1):
                    assert blocks.get((x, y, z), 'minecraft:air') in ('minecraft:air', 'orimod:bash_anchor', 'orimod:grapple_flower'), (name, (x, y, z))
    print(name, 'contract passed')
print(json.dumps({'status': 'passed', 'sha256': before}, indent=2))
