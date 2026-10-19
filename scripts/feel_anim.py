#!/usr/bin/env python3
"""Plot R74 animation-feel responses from FeelTuning constants (squash, lean, ears, crossfade)."""
import re, sys, json, pathlib
import matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
src = pathlib.Path(__file__).resolve().parents[1] / "src/main/java/cn/wwl/orimod/movement/FeelTuning.java"
C = {k: float(v) for k, v in re.findall(r"([A-Z_]+) = (-?[\d.]+)f?", src.read_text())}
out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/workspace/oriMod/shots")
def spring(x, target, k, c, kick=0, t=1.0, dt=1/60):
    v = kick; xs = []
    for i in range(int(t / dt)):
        for _ in range(4):
            h = dt / 4; v += (k * (target(i * dt) - x) - c * v) * h; x += v * h
        xs.append(x)
    return [i * dt for i in range(len(xs))], xs
fig, ax = plt.subplots(2, 2, figsize=(11, 7))
t, s = spring(1, lambda _: 1, C["SQUASH_STIFFNESS"], C["SQUASH_DAMPING"], -C["SQUASH_LAND"] * .9)
t2, s2 = spring(1, lambda _: 1, C["SQUASH_STIFFNESS"], C["SQUASH_DAMPING"], C["SQUASH_JUMP"])
ax[0][0].plot(t, s, label="land (vy -0.5)"); ax[0][0].plot(t2, s2, label="jump"); ax[0][0].axhline(1, color="gray", lw=.5)
ax[0][0].set_title("squash/stretch Y scale (before: none, 1.0)"); ax[0][0].legend()
lean = lambda tt: min(C["LEAN_MAX"], C["LEAN_SPEED"] * (6.4 if tt > .1 else 0) / 6.4 * 1.0 + (C["LEAN_ACCEL"] * 20 if .1 < tt < .25 else 0))
t, l = spring(0, lean, C["POSE_STIFFNESS"], C["POSE_DAMPING"])
ax[0][1].plot(t, l); ax[0][1].set_title("lean pitch (rad) on run start at 0.1s (before: 0)")
ear = lambda tt: C["EAR_FALL"] * .5 if .2 < tt < .6 else 0
t, e = spring(0, ear, C["EAR_STIFFNESS"], C["EAR_DAMPING"])
ax[1][0].plot(t, e); ax[1][0].set_title("ear fall flow (rad), fall 0.2-0.6s (before: none)")
ts = [i / 100 for i in range(50)]
for f, n in ((.11, "before .11s linear-ish"), (C["ANIM_CROSSFADE"], "after %.2fs smooth" % C["ANIM_CROSSFADE"])):
    ax[1][1].plot(ts, [min(1, x / f) ** 2 * (3 - 2 * min(1, x / f)) for x in ts], label=n)
ax[1][1].set_title("clip crossfade weight"); ax[1][1].legend()
for a in ax.flat: a.set_xlabel("s"); a.grid(alpha=.3)
fig.tight_layout(); out.mkdir(parents=True, exist_ok=True); fig.savefig(out / "feel-anim.png", dpi=90)
m = {"squash_min_land": round(min(s), 3), "stretch_max_jump": round(max(s2), 3), "squash_settle_s": next(t[i] for i in range(len(s)-1, 0, -1) if abs(s[i]-1) > .01),
     "lean_peak_rad": round(max(l), 3), "ear_peak_rad": round(max(e), 3), "crossfade_s": C["ANIM_CROSSFADE"]}
(out / "feel-anim.json").write_text(json.dumps(m, indent=1)); print(m)
