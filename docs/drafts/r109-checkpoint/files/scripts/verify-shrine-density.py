"""Fresh normal-world R109 audit: rejection reasons and a fixed 5x5 vanilla /locate grid.
No client is started. Do not run while runDevtoolsClient or another Gradle task is active.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('run_id')
p.add_argument('--seed', type=int, default=109)
args = p.parse_args()
if not args.run_id.replace('-', '').replace('_', '').isalnum():
    p.error('run_id must contain only letters, digits, hyphens and underscores')
out = ROOT / 'evidence' / args.run_id
work = ROOT / '.external' / 'worldgen-audit' / args.run_id
out.mkdir(parents=True, exist_ok=False)
work.mkdir(parents=True, exist_ok=False)
paths = sorted([*ROOT.glob('src/main/java/cn/wwl/orimod/world/*.java'),
                *ROOT.glob('src/main/resources/data/orimod/worldgen/**/*.json'),
                *ROOT.glob('src/main/resources/data/orimod/tags/worldgen/**/*.json')])
def fingerprints():
    return {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in paths}
before = fingerprints()
(out / 'source-sha256.json').write_text(json.dumps(before, indent=2) + '\n')
(work / 'eula.txt').write_text('eula=true\n')
(work / 'server.properties').write_text(
    f'level-seed={args.seed}\nlevel-name=world\nlevel-type=minecraft:normal\ngenerate-structures=true\n'
    'online-mode=false\nserver-ip=127.0.0.1\nserver-port=25575\nview-distance=2\nsimulation-distance=2\n'
    'spawn-protection=0\nmax-tick-time=-1\n')
cmd = (['powershell', '-NoProfile', '-File', str(ROOT / 'scripts/gradle.ps1')]
       if os.name == 'nt' else [str(ROOT / 'gradlew')])
cmd += ['runWorldgenAudit', '-PworldgenAuditMode=shrine', f'-PworldgenAuditDir={work}', '--console=plain']
with (out / 'launch.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(f'Gradle failed; see {out / "launch.log"}')
if fingerprints() != before:
    raise SystemExit('Worldgen inputs changed during audit; start a fresh run_id')
report = json.loads((work / 'shrine-audit.json').read_text())
(out / f'seed-{args.seed}.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k not in ('probes', 'rows')}, indent=2))
if report.get('status') != 'complete' or len(report['rows']) != 25 or not report.get('passes'):
    raise SystemExit('Coverage failed: need 25 samples, median <= 1500')
