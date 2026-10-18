"""Run normal dedicated worlds through vanilla /locate and retain all 225 samples."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("run_id")
parser.add_argument("--seeds", nargs="+", type=int, default=[8675321, 8675322, 8675323])
args = parser.parse_args()
if not args.run_id.replace("-", "").replace("_", "").isalnum():
    raise SystemExit("run_id must contain only letters, digits, hyphens and underscores")
destination = ROOT / "evidence" / args.run_id
destination.mkdir(parents=True, exist_ok=False)
worlds = ROOT / ".external" / "worldgen-audit" / args.run_id
worlds.mkdir(parents=True, exist_ok=False)
reports = []
def source_hashes():
    paths = sorted([*ROOT.glob('src/main/java/cn/wwl/orimod/world/*.java'),
                    *ROOT.glob('src/main/resources/data/orimod/worldgen/**/*.json'),
                    *ROOT.glob('src/main/resources/data/orimod/tags/worldgen/**/*.json'),
                    *ROOT.glob('src/main/resources/orimod/ruin_layout/*.json'),
                    *ROOT.glob('src/main/resources/data/orimod/structure/ruins/*.nbt')])
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
fingerprint = source_hashes()
(destination / 'source-sha256.json').write_text(json.dumps(fingerprint, indent=2) + '\n', encoding='utf-8')
started = time.monotonic()
for seed in args.seeds:
    if source_hashes() != fingerprint:
        raise SystemExit('Worldgen inputs changed during this batch; start a fresh run_id')
    directory = worlds / str(seed)
    directory.mkdir()
    (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (directory / "server.properties").write_text(
        f"level-seed={seed}\nlevel-name=world\nlevel-type=minecraft:normal\ngenerate-structures=true\n"
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=25575\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-tick-time=-1\n", encoding="utf-8")
    command = (["powershell", "-NoProfile", "-File", str(ROOT / "scripts" / "gradle.ps1")] if os.name == "nt" else [str(ROOT / "gradlew")])
    command += ["runWorldgenAudit", f"-PworldgenAuditDir={directory}", "--console=plain"]
    print(f"Starting normal world {seed}: {directory}", flush=True)
    with (directory / "launch.log").open("w", encoding="utf-8") as log:
        process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    report_path = directory / "worldgen-audit.json"
    if process.returncode or not report_path.exists():
        raise SystemExit(f"World {seed} failed (exit {process.returncode}); inspect {directory / 'launch.log'}")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    (destination / f"seed-{seed}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if report.get("status") != "complete" or len(report["rows"]) != 75:
        raise SystemExit(f"World {seed} did not complete all 75 queries")
    reports.append(report)
    print(f"Finished {seed}: {report['elapsedSeconds']:.1f}s", flush=True)
rows = [row for report in reports for row in report["rows"]]
if source_hashes() != fingerprint:
    raise SystemExit('Worldgen inputs changed during this batch; start a fresh run_id')
summary = {"run": args.run_id, "seeds": args.seeds, "elapsedSeconds": time.monotonic() - started, "kinds": {}}
for kind in sorted({row["kind"] for row in rows}):
    selected = [row for row in rows if row["kind"] == kind]
    distances = sorted(row["distance"] for row in selected if row["found"])
    coverage = sum(row["within1500"] for row in selected) / len(selected)
    summary["kinds"][kind] = {"samples": len(selected), "within1500": sum(row["within1500"] for row in selected), "coverage": coverage,
                              "medianDistance": distances[len(distances) // 2] if distances else None,
                              "maxDistance": max(distances, default=None), "passes": coverage >= .9}
summary["passes"] = all(kind["passes"] for kind in summary["kinds"].values())
(destination / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2), flush=True)
