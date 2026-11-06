# 试玩 / 审查 第八轮（P8）

日期：2026-11-06（Asia/Shanghai）。HEAD `5310e3a`（P7 修复刚落地）。
方式：**只测不修**。复查 P7 修复 + 全量 check + 宽面混沌。脚本 `/workspace/oriMod/pt8/`，截图 `/workspace/oriMod/shots/playtest-p8-*`（不入库）。
标记：**[已修]** 确认仍好，**[未修]** 仍在，**[新]** 本轮新发现，**[回归]** 修好又坏，**[怀疑]** 未完全复现。

## P8-0 范围

1. 逐条回归 `docs/PLAYTEST_P7_FIXES.md` A1–F5。
2. `./scripts/check.sh` 全量（worktree `wt-p8-check`）。
3. 宽面：极限/超平坦/放大化、死亡掉落、能力中途 kill / 跨维度、腐化长时与动物、制图师重复买、locate、Spirit Glow 冷启、F3+T、中英切换、GUI 缩放、创造能量、Splinter、逃亡续玩、坐船/骑乘、左手第一人称、第三人称盾、和平↔困难 Boss、粉末雪/蜂蜜/灵魂沙/气泡柱、日志归类、P7 跳过项现状备注。

