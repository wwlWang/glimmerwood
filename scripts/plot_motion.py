#!/usr/bin/env python3
"""Plot real-client motion recordings (devtools record_motion) as velocity / pose timelines.
Usage: python3 scripts/plot_motion.py <evidence_dir> <out.png> [step ...]"""
import json, sys, math, pathlib
import matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
d = pathlib.Path(sys.argv[1]); out = sys.argv[2]; steps = sys.argv[3:] or ["run", "jump-held", "jump-tap", "dash"]
fig, ax = plt.subplots(len(steps), 2, figsize=(12, 3 * len(steps))); summary = {}
for row, name in enumerate(steps):
    s = [x for x in json.load(open(d / f"{name}.json"))["samples"] if x["side"] == "client"]
    seen = {}; [seen.setdefault(x["tick"], x) for x in s]; s = [seen[t] for t in sorted(seen)]
    t0 = s[0]["tick"]; ts = [x["tick"] - t0 for x in s]; pos = [x["state"]["position"] for x in s]
    hs = [0] + [math.hypot(b[0] - a[0], b[2] - a[2]) * 20 for a, b in zip(pos, pos[1:])]
    ys = [p[1] - pos[0][1] for p in pos]
    an = [x["state"].get("animation", {}) for x in s]
    a = ax[row][0]; a.plot(ts, hs, label="horizontal m/s"); a.plot(ts, ys, label="height (blocks)"); a.set_title(f"{name}: client motion"); a.legend(); a.grid(alpha=.3)
    b = ax[row][1]; b.plot(ts, [x.get("leanPitch", 0) for x in an], label="lean pitch"); b.plot(ts, [x.get("leanRoll", 0) for x in an], label="lean roll")
    b.plot(ts, [x.get("squash", 1) - 1 for x in an], label="squash-1"); b.set_title(f"{name}: pose springs + clip"); b.grid(alpha=.3)
    last = None
    for t, x in zip(ts, an):
        if x.get("base") != last: b.annotate(x.get("base", "?"), (t, 0), fontsize=7, rotation=60); last = x.get("base")
    b.legend(fontsize=7)
    summary[name] = {"peak_mps": round(max(hs), 2), "peak_height": round(max(ys), 3), "speeds": [round(v, 2) for v in hs[:30]]}
fig.tight_layout(); fig.savefig(out, dpi=80); print(json.dumps(summary))
