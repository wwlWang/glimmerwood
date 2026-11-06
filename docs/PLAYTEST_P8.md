# 试玩 / 审查 第八轮（P8）

日期：2026-11-06（Asia/Shanghai）。功能基线 HEAD `5310e3a`（P7 修复）；文档提交另计。
方式：**只测不修**。复查 P7 修复 + 全量 check + 宽面混沌。脚本 `/workspace/oriMod/pt8/`，截图 `/workspace/oriMod/shots/playtest-p8-*`（不入库）。
标记：**[已修]** 确认仍好，**[未修]** 仍在，**[新]** 本轮新发现，**[回归]** 修好又坏，**[怀疑]** 未完全复现。

## P8-0 范围

1. 逐条回归 `docs/PLAYTEST_P7_FIXES.md` A1–F5。
2. `./scripts/check.sh` 全量（worktree `wt-p8-check` @ `5310e3a`）。
3. 宽面混沌与 P7 跳过项现状备注。

世界：`orimod_dev_p8_r1`（seed 8808）+ 同档内远坐标补测。

---

## P8-1 P7 修复回归

### A1 `/setblock` 腐化之核扩散 — **[已修]**
- 浮空草平台上核可注册（`ori blight nearest` 命中，status `active`）。
- **测量陷阱**：`fill … blighted_soil replace blighted_soil` 在本环境会报「没有方块被填充」（同块替换不计）。改用 `dirt replace orimod:blighted_soil`：
  - `(16000,81,16000)`，`tick sprint 500` 后 **成功填充 80 个**腐化地，另有 **2** 格 `gloom_thorns`。
  - region columns 9→81，`newColumns=80`。
- 截图 `playtest-p8-a1-proper-growth.png`。
- 与 P7 实机（p8d 54/84）一致：**命令核会扩散**。

### A2 命令拆除不刷净化 — **[已修]**
- setblock 核 → setblock air：该点 `region={}`；700 tick 后 **无** `purifying`/`completed`（`A2 bad_purify=False`）。

### A2b 玩法摧毁仍净化（防回归） — **[已修][无回归]**
- 降 health 后灵光爆发等命中：方块消失 → `purifying` → 650 tick 后 `completed`。

### B 林栖商人 — **[已修]**（首轮假阴性已澄清）
- 生存拳击：仍在。
- `/kill`：反馈「杀死了苔语 · 碎片商」，`nearby=[]`。
- 创造：`/damage … player_attack by @s` 显示造成伤害；随后实体消失（与 `/kill` 时序交错，**创造拳击手感依赖准星，devtools `attack_entity` 缺参 NPE**）。虚空 tp 可清。
- 首轮 B 失败是距离/计数问题，不是回归。

### C 萤目标
- 闲置坚守者：`eligible=false`，fired 不变。**[已修]**
- 200 tick 内实体常变为 None（再次消失）。**[未修]** 同 P7。
- 激怒：AngerTime+拳击未能稳定 `eligible=true`（怪先消失）。**aggro 实机未拿到**；GameTest `setAttackTarget` 路径 P7 已过。
- 戴金猪灵蛮兵：`eligible=true`，46→32，fired↑。**[已修]**
- 戴金普通猪灵：`eligible=false`，16→16。**[已修][无回归]**

### D 和平 `/ori spawn` — **[已修]**
- 明确拒绝并提示改难度；困难下 `beetle` 正常生成。

### E 许可措辞 — **[已修]**
- README / THIRD_PARTY 无「非商业 / non-commercial」。

### F2 领取武器 — **[已修]**
- 背包有刃：控件「已领取」，无「领取武器」按钮。
- 领取一次可拿到刃；`claimedWeapons` 位已置位。清空后再开面板，「已领取」仍出现；未再刷出第二把（二次点击未打到独立按钮）。

### F4 HUD 盖副手 — **[已修]**
- `grant_light 500` + 副手盾：截图 `playtest-p8-f4-hud-offhand.png` 中副手盾清晰可见，「灵光 500」在经验条左侧，不压副手格。

### F5 Spirit Glow previousPack — **[已修]**
- 开 Spirit Glow → 切 Mellow → 再开：diagnostics `previousPack=Mellow v3.4.1a.zip`。
- 关闭后恢复 Mellow（pack 正确）。
- 注：devtools `iris_pack` 缺 name 时 NPE（工具问题）。

---

## P8-2 全量 check.sh

worktree `/workspace/oriMod/wt-p8-check` @ `5310e3a`。

| 项 | 结果 |
| --- | --- |
| JUnit | **1887** 通过 / 0 失败 / 0 错误 |
| GameTest | **1692** 完成；**1** 个 required 失败 |
| 总结果 | **BUILD FAILED**（~9.5 min GameTest 段） |

### 失败用例 — **[新][与 P7 A1 修复直接相关]**
- `r117_blight_ledger.structure_reference_waits_for_unloaded_start`
- 断言：未加载的结构起点引用应为 `DEFER`
- 实际：`ALLOW`（P7 为避免整列永久卡住，对未加载起点改为跳过）
- 日志：`Expected Unknown start treated as empty to be DEFER, but was ALLOW`
- **含义**：玩法上 A1 修好了，但对应 GameTest **未改断言**，全量门禁红灯。修测试或收紧跳过条件二选一（本轮不改代码）。

---

## P8-3 宽面 / 混沌

| 项 | 结果 |
| --- | --- |
| 粉末雪 / 蜂蜜 / 灵魂沙气泡柱 | 能站上/进入；精灵 movement mode 多记为 `idle`（短测）。截图 `chaos-powder` / `chaos-fluids` |
| keepInventory=false 死亡 | 本轮 `ori god` 未真正关死，死亡掉落 **未有效验证** |
| 冲刺中途 `/kill` | 复活后 form 仍精灵、mode idle，无卡死 |
| 中英切换 | `language` API 调用成功并截图；`resourceReloads` 计数未变（工具计数疑似不准） |
| GUI scale 1/4 | 截图正常，无崩溃 |
| 创造能量 0 射弓 | 能量保持 0（不扣成负） |
| 左手第一人称刃 | 截图 `fp-left-blade.png`（形态仍偏过曝/朝向问题 **[未修]** 同 P7） |
| F3+T / reload_resources | reloads 0→1，无 `reloadError` |
| locate×24 | blight nearest 稳定；tree nearest 在已加载区常「没有」（需区块/世界有树） |
| 制图师重复买 | 只召唤了村民，未跑通交易 UI；进度里有 `ownedShards` 等。**仍建议对照代码：tree/blight 有买过记录，遗迹指引可能无**（P7 遗留） |
| Splinter | 打开碎片页 `ShardScreen`；本轮未完成装备+满蓄三连实弹（UI 限制） |
| 极限/超平坦/放大化 | **未开新世界**（时间盒） |
| 逃亡续玩 / 坐船金索 | **本轮未复测**（P7 已过；工具成本高） |

### 日志归类（run-devtools latest，本会话增量）
- `Can't keep up` 多条（tick sprint / 负载）— 环境噪音
- Iris CompatibilityTransformer 缺 in 变量 — Spirit Glow / Mellow 切换时
- 无新的 OriMod ERROR / 崩溃报告

### P7 跳过项现状（仍未修，仅备注）
- **无 logoFile**：toml 模板无 `logoFile`（路径在 `src/main/templates/META-INF/neoforge.mods.toml`）
- **三套名字**：Glimmerwood / OriMod / `orimod` 仍在
- Flywheel 钉死、JarJar KTX、CI 无 GameTest、Modrinth 草稿、Claude-Session 历史、自定义音效、zh_tw/ja — 未动

---

## P8-4 汇总（严重 → 轻微）

### 严重 / 门禁
1. **[新]** 全量 GameTest 红灯：`structure_reference_waits_for_unloaded_start` 仍期望 `DEFER`，与 P7「未加载结构引用跳过」冲突。实机扩散已验证；需改测试或改回策略。

### 中等
2. **[未修]** 召唤的坚守者约数秒内消失，aggro 萤测不稳。
3. **[未修]** 第一人称灵光刃过曝 / 左手朝向怪异。
4. **[未修]** 无自定义音效；字幕显示原版名。
5. **[未修]** 三套名字 + 模组列表无图标。

### 轻微 / 工具
6. devtools `attack_entity` / 无参 `iris_pack` NPE。
7. `fill same replace same` 不能用来数腐化地（改用换成别的方块计数）。
8. 死亡掉落、制图师重复买、Splinter 实弹、极限/超平坦 — 本轮未完成。

### 确认仍好（P7 修复）
A1 扩散、A2 命令拆核不净化、A2b 玩法净化、B `/kill`、C 闲置不打 + 蛮兵可打 + 金猪灵安全、D 和平拒绝、E 措辞、F2 已领取、F4 HUD、F5 previousPack。

---

## 未测 / 原因
- 联机、聊天框：devtools 限制
- 真机显卡 / Win/macOS
- Modrinth 公开页（仍 404/草稿）
- 极限、超平坦、放大化、长时间卡顿
- 制图师重复扣钱实机、Splinter 满蓄三连实弹、金索/腐光逃亡本轮复测

---

## P8-cont-0 GameTest 复核（过滤 `orimod_r117`）

日期续：2026-11-06（Asia/Shanghai）。代码基线仍 `5310e3a`；本段只确认门禁，**不改代码**。

- 命令：`./gradlew --no-daemon runGameTestServer -PgameTestNamespaces=orimod_r117`（worktree `wt-p8-check`）
- 结果：**BUILD FAILED**；`120 GAME TESTS COMPLETE`；**1 required failed**
- 失败：`r117_blight_ledger.structure_reference_waits_for_unloaded_start`
- 断言原文：`Expected Unknown start treated as empty to be DEFER, but was ALLOW`
- 与全量 P8-2 / P7 A1 修复冲突结论一致：**仍红灯**。

日志：`/workspace/oriMod/pt9/gametest_r117.log`（不入库）。


---

## P8-cont-1 制图师重复购买（实机）

世界：`orimod_dev_p8_r1`。制图师 = `glades_merchant` job=1（路路），空手 `key.use` 打开（持弓会吞掉交互）。

### 库存
- 报价只有 **`tree`(60)**、**`blight`(50)**。**没有** `ruin` / 遗迹指引商品（与旧笔记「遗迹指引无回执」对照：商品已不存在）。
- 截图 `playtest-p8cont-cart-open.png` / `cart5-20100-open.png`。

### 腐化定位（有有效 FeatureSites）
在 `(20100,100,20100)` `place feature orimod:blighted_grounds` 成功后：

| 次 | 按钮 | 灵光 | message | 回执 |
| --- | --- | --- | --- | --- |
| 1 | 购买 · 50 灵光 | 3300→3250（−50） | `purchased` | 新增 `guide/minecraft:overworld/blighted_grounds/...`；指引激活 |
| 2 | **免费恢复指引**（price=0） | 3250→3250（0） | `already_located` | 无新回执 |

**结论：同目标第二次购买不扣费。** 旧「双扣」在 blight 路径上 **未复现**（代码回执生效）。截图 `cart5-20100-b1/b2`。

### 生灵之树定位
- 多次 `place feature orimod:spirit_tree` / 远离结构站位购买：均为 `not_found`，灵光不变。
- `featureSites.trees` 计数有值，但 `nearest` 在已加载且树心缺失、或站点不在 2048 内时找不到目标。
- **树路径已在 P8-cont-3 打通**（与 blight 相同回执逻辑）。

### UX **[新]**
无有效目标时按钮仍显示「购买 · 60/50」，点击只回 `not_found`、不扣费——易误以为坏掉或可刷。

### 顺带
- 第一轮空手失败是因为热键栏还握着弓（交互被拉开弓吞掉）。

---

## P8-cont-2 Splinter 实弹

装备 `splinter`（`equippedShards` 含对应 bit）。对着石墙满蓄：

| 条件 | 能量 | 箭数 | pickup NBT |
| --- | --- | --- | --- |
| 满蓄 + 能量 40 | 40→20（耗 20） | **3** | 全部 `0b`（DISALLOWED） |
| 满蓄 + 能量 20 | 20→0 | **3** | `0b` |
| 满蓄 + 能量 8 | 8→~15（回复/未扣满额） | **0**（未射出有效箭） | — |
| 短蓄（hold≈5）+ 能量 40 | 40→28 | **1** | `0b` |

走到箭上：背包无 `arrow` 物品。**不可拾取确认。**

### 中途打断
- **蓄力中 `/kill`**：进 DeathScreen；重生后精灵形态、能量回满（非卡蓄力）。截图 `splinter-mid-death`。
- **蓄力中存盘退出再进**：约 4.2 s 进世界；形态仍在，能量仍 40（蓄力未 consummate）。截图 `splinter-mid-logout`。

脚本/截图：`/workspace/oriMod/pt9/p8cart*.py`、`playtest-p8cont-spl_*`。


---

## P8-cont-3 制图师 · 生灵之树双买（补完）

`place feature orimod:spirit_tree 20200 100 20200` 成功后树心 `[20200,100,20198]`，`featureSites.trees` 5→6。

| 次 | 按钮 | 灵光 | message |
| --- | --- | --- | --- |
| 1 | 购买 · 60 灵光 | −60 | `purchased` + 回执 `guide/.../spirit_tree/...` |
| 2 | 免费恢复指引 | 0 | `already_located` |

**树 / 腐化 二次购买均不双扣。** 遗迹指引商品不在路路库存中。

---

## P8-cont-4 逃亡续玩 / 坐船

### 金索（`orimod_dev_pt117`）
- 开逃：`READY`→`RUNNING`（`pendingRecovery=true`）。
- **坐船**：`summon boat` 成功；`ride @s mount` → **「Dev无法骑乘橡木船」**，无载具。**拒绝上船确认（仍好）。**
- **中途存盘退出再进（~2.7–4 s）**：`phase=COOLDOWN`，`pendingRecovery=false`，`receipt=0`。**未续跑，直接进冷却。**  
  **[新][中等]** 与腐光之心续跑不一致；中途退出等于放弃本局且进冷却。

### 腐光之心（`orimod_dev_pt116p3`）
- 杀心后 `gloomEscape.active=true`（elapsed 145）。
- 退出再进（3.9 s）：仍 `active=true`，elapsed 224，半径增大。**续跑仍好。**

截图：`ginso-start/boat2/reopen`，`gloom-before2/after2`。

---

## P8-cont-5 死亡掉落（keepInventory=false）

正确关掉 `ori god`（abilities.invulnerable=`0b`）并 `effect clear` 后：

- 持刃/弓/矛/锤，蓄矛中 `/kill` → DeathScreen。
- 重生后背包 **空**；死亡点附近 **4 个** `minecraft:item`。
- 精灵形态仍在，`energy=100`，`mode=idle`，无卡死。

**结论：精灵武器按原版掉落；中途投矛不卡状态。**（P8-3 未完成项已补。）

---

## P8-cont-6 极限模式

`create_world` 名必须匹配 `orimod_dev_[A-Za-z0-9_-]{1,48}`（否则 `Invalid test-world name`）。

世界 `orimod_dev_p8_hc3`，hardcore=true，启动 ~8 s。

- 死亡 UI：**旁观世界 / 标题屏幕**（无「重生」）— 极限正确。
- 点旁观 → `playerGameType=3`（旁观），abilities `mayfly/flying/invulnerable`。
- **[轻]** 旁观时 `spirit_form` 仍为 true、`awakened` true（形态标志未清）。

截图 `hc-death` / `hc-after`。

---

## P8-cont-7 超平坦 / 放大化

devtools `create_world` **写死** `WorldPresets.createNormalWorldDimensions`；改类型需 GUI。创建界面顶栏点到「世界」页后可循环 **世界类型**。

| 类型 | 启动 | 出生 | 结构 locate | 精灵 |
| --- | --- | --- | --- | --- |
| 超平坦 | ~1.1 s | y≈−60 | 附近无 great_tree / forest_spring / ginso / spirit_tree | setup 成功 |
| 放大化 | ~1.1 s 进世界 | y≈114 | **locate 命令超时**（devtools observation timeout） | `spirit_state` 一度 KeyError（负载） |

无冻死/崩溃。放大化下远距 locate 在本环境不可靠。截图 `flat-spawn` / `amp-spawn` / `create-ui`。

---

## P8-cont-8 汇总补丁（相对 P8-4）

### 新发现
1. **[新][中等]** 金索逃亡中途存盘 → **COOLDOWN 而非续跑**（腐光之心仍续跑）。
2. **[新][轻]** 制图师在无有效 FeatureSites 目标时仍显示标价按钮，点击 `not_found`。
3. **[新][轻]** 极限旁观后精灵形态标志仍为 true。
4. **[新][工具]** 放大化世界 `/locate` 易超时；`create_world` 名称白名单 + 无世界类型参数。

### 仍开着（未变）
- GameTest `structure_reference_waits_for_unloaded_start` 期望 DEFER、实为 ALLOW（P8-cont-0）。
- 第一人称刃过曝、无自定义音效、三套名字/无图标、Flywheel 钉死、CI 无 GameTest、Modrinth 草稿、会话链接历史等（P7 跳过项）。

### 确认仍好 / 本轮补完
- 制图师 tree/blight **二次不扣费**；无遗迹商品。
- Splinter 满蓄 20 能量 → 3 箭，短蓄 1 箭，pickup=0，中途死/退出不坏档。
- 金索拒船；腐光之心续跑。
- keepInventory=false 精灵武器掉落干净。
- 极限死→旁观 UI。
- 超平坦/放大化可进、精灵可开。

文档 HEAD 随本提交；代码基线仍 **`5310e3a`**。脚本 `/workspace/oriMod/pt9/`，截图 `playtest-p8cont-*`（不入库）。

