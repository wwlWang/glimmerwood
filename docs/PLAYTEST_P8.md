# 试玩 / 审查 第八轮（P8）

日期：2026-11-06（Asia/Shanghai）。HEAD `5310e3a`（P7 修复刚落地）。
方式：**只测不修**。复查 P7 修复 + 全量 check + 宽面混沌。脚本 `/workspace/oriMod/pt8/`，截图 `/workspace/oriMod/shots/playtest-p8-*`（不入库）。
标记：**[已修]** 确认仍好，**[未修]** 仍在，**[新]** 本轮新发现，**[回归]** 修好又坏，**[怀疑]** 未完全复现。

## P8-0 范围

1. 逐条回归 `docs/PLAYTEST_P7_FIXES.md` A1–F5。
2. `./scripts/check.sh` 全量（worktree `wt-p8-check`）。
3. 宽面：极限/超平坦/放大化、死亡掉落、能力中途 kill / 跨维度、腐化长时与动物、制图师重复买、locate、Spirit Glow 冷启、F3+T、中英切换、GUI 缩放、创造能量、Splinter、逃亡续玩、坐船/骑乘、左手第一人称、第三人称盾、和平↔困难 Boss、粉末雪/蜂蜜/灵魂沙/气泡柱、日志归类、P7 跳过项现状备注。

## P8-1 P7 修复回归（世界 `orimod_dev_p8_r1` seed 8808，HEAD 功能基线 `5310e3a`）

脚本 `pt8/p8_regress.py`，截图 `shots/playtest-p8-*-r1.png` / `f2/f4/f5-*`。

### A1 `/setblock` 腐化之核扩散
- 浮空草平台 `(50000,90,50000)`：核 **能注册**（`ori blight nearest` 命中；status `active` total=1）。
- `tick sprint 800` 后 `lastReads=338` 但 **`lastWrites=0`，`blighted_soil` 填充计数 0**。
- **[新]/[怀疑回归]** 相对 P7 实机 `orimod_dev_p8d`（500 tick → 54/84 土）。本轮平台是挖空后的浮空草皮；自然地表补测见 P8-2。
- 截图 `playtest-p8-a1-growth-r1.png`。

### A2 命令拆除不刷净化
- setblock 核 → setblock air：该坐标 `region={}`；700 tick 后 **无** `purifying`/`completed`（`A2 bad_purify=False`）。
- 探针随后只看到仍 active 的 A1 核（50000），属邻近残留，不是被拆核进入净化。
- **[已修]** 命令拆除取消路径成立（即使本轮该核几乎没长土）。

### A2b 玩法摧毁仍净化（防回归）
- 核 health 改 4 后用灵光爆发等命中：方块消失，status **`purifying` → 650 tick 后 `completed`**。
- **[已修][无回归]** 玩法打穿仍走 `begin` 净化。

### B 林栖商人
- 生存拳击：仍在（1→1）**[已修]**。
- `/kill` 后仍 1；创造模式攻击后仍 1。**[回归] 或 [复现失败]** — 与 P7 实机「kill 后 nearby=[]」不符。P8-2 用 `attack_entity` / `damage generic_kill` / 虚空 复测。

### C 萤目标
- 闲置坚守者：`eligible=false`，200 tick 内 fired 不变；随后实体变为 None（又消失）。**闲置不打 [已修]**；消失问题 **[未修/怀疑]** 同 P7。
- 激怒路径：本轮 AngerTime+拳击后列表已空（怪先消失），**aggro 实机未拿到**。
- 戴金猪灵蛮兵：`eligible=true`，46→32，fired 25→32。**[已修]**
- 戴金普通猪灵：`eligible=false`，16→16。**[已修][无回归]**

### D 和平 `/ori spawn`
- `beetle`/`heart`：明确拒绝「和平难度下敌对生物会立即消失…」。**[已修]**
- 改回困难后 `beetle` 正常生成。

### E 许可措辞
- `README.md` / `THIRD_PARTY.md` 无「非商业/non-commercial」命中。**[已修]**

### F2 领取武器
- 背包有灵光刃：控件出现「已领取」，无「领取武器」。**[已修]**
- 清空背包后：又出现「领取武器」。是否可再领一次免费刃 → P8-2 点击验证。截图 `playtest-p8-f2-claim-panel.png`。

### F4 HUD / 盾
- 已 `grant_light 500` + 副手盾；截图 `playtest-p8-f4-hud-offhand.png`、`f4-shield-3p.png`（目视数字是否仍压副手，见截图）。

### F5 Spirit Glow previousPack
- 开启 Spirit Glow → 切 Mellow → 再开 Spirit Glow：diagnostics **`previousPack=Mellow v3.4.1a.zip`**。
- 关闭后 shaders **恢复 Mellow**（enabled=true, pack=Mellow）。**[已修]**
- 注：`iris_pack` 无 name 参数时 NPE（devtools 小问题，不影响玩法）。

