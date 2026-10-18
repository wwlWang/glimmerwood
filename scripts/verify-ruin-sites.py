"""WORLD02 site audit: inspect real generated ruins (entry, approach, landing, checkpoints, water, lava)."""
import argparse
import json
import os
from collections import Counter
from pathlib import Path
import subprocess

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
rows = []
for seed in args.seeds:
    directory = worlds / str(seed)
    directory.mkdir()
    (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (directory / "server.properties").write_text(
        f"level-seed={seed}\nlevel-name=world\nlevel-type=minecraft:normal\ngenerate-structures=true\n"
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=25576\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-tick-time=-1\n", encoding="utf-8")
    command = (["powershell", "-NoProfile", "-File", str(ROOT / "scripts" / "gradle.ps1")] if os.name == "nt" else [str(ROOT / "gradlew")])
    command += ["runWorldgenAudit", f"-PworldgenAuditDir={directory}", "-PworldgenAuditMode=site", "--console=plain"]
    print(f"Site audit world {seed}", flush=True)
    with (directory / "launch.log").open("w", encoding="utf-8") as log:
        process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    path = directory / "ruin-sites.json"
    if process.returncode or not path.exists():
        raise SystemExit(f"World {seed} failed (exit {process.returncode}); see {directory / 'launch.log'}")
    report = json.loads(path.read_text(encoding="utf-8"))
    (destination / f"seed-{seed}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if report.get("status") != "complete":
        raise SystemExit(f"World {seed}: {report.get('error')}")
    for row in report["rows"]:
        row["seed"] = seed
    rows.extend(report["rows"])
issues = Counter(issue.split(":")[0] + ":" + issue.split(":")[1] if ":" in issue else issue for row in rows for issue in row["issues"])
summary = {
    "run": args.run_id, "seeds": args.seeds, "sites": len(rows),
    "clean": sum(1 for row in rows if not row["issues"]),
    "byKind": {k: sum(1 for r in rows if r["kind"] == k) for k in sorted({r["kind"] for r in rows})},
    "issues": dict(issues),
    "waterEdge": sum(1 for r in rows if 0 <= r.get("waterWithin6", -1) <= 6),
    "entryY": [min(r["entryY"] for r in rows), max(r["entryY"] for r in rows)] if rows else None,
    "failing": [{k: r[k] for k in ("seed", "kind", "origin", "rotation", "biome", "issues")} for r in rows if r["issues"]],
}
(destination / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(json.dumps({k: summary[k] for k in ("sites", "clean", "byKind", "issues", "waterEdge", "entryY")}, ensure_ascii=False))
raise SystemExit(0 if summary["clean"] == summary["sites"] else 1)
