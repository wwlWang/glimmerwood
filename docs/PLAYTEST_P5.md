# Pass 5 广度试玩（2026-10-30，HEAD `cf989da`）

自 `9c8770f` 起没有新代码提交，只有 Pass 4 文档（`c063511..cf989da`）。代码与 Pass 4 相同。

目标：广度优先（人设 / 群系 / 环境状态 / 怪物战斗 / 游戏规则与设置 / 存档生命周期 / UI / 性能 / 物品经济），每块 5–10 分钟，发现就记，不深挖。Headless Xvfb :99 + llvmpipe，`./gradlew --no-daemon runDevtoolsClient`。截图 `/workspace/oriMod/shots/playtest-p5-*`，脚本 `/workspace/oriMod/pt5/`。**不改代码**。

标记：**[新·复现] / [新·怀疑] / [仍存在] / [通过] / [未测：原因]**；严重度 crash / blocker / major / minor / cosmetic。

## P5-0 静态检查 + 启动
- `en_us.json` / `zh_cn.json` 各 574 键，互相齐全，没有中英相同未翻译的值；Java 里所有字面量 `Component.translatable("…")` 键都在 lang 中（动态拼接前缀 30 个，靠游戏内实测覆盖）**[通过]**。
- 启动后客户端停在原版「无障碍引导」首启界面（`AccessibilityOnboardingScreen`，`playtest-p5-onboarding.png`）——options.txt 里 `onboardAccessibility` 被重置过（可能是之前某轮测试清过 options），非模组问题 **[信息]**。

## P5-A 玩家人设（新世界 `orimod_dev_p5new`，seed 5005，生存/普通）
- **新玩家（零命令）**：出生在海岛（`(-125,71,-681)`），64 格内没有任何模组实体/方块；最近的模组结构：驿站 437 格、灵树 582、森林之泉 750、溪谷 810。屏幕上只有原版教程提示。捡到铜锭后右上角弹出原版「新配方已解锁」吐司（图标是灵光）`playtest-p5-a-copper-toast.png`，这是新玩家第一次接触模组的唯一入口 **[信息/UX]**。
  - **[新·复现，minor/UX]** 精灵核心没有任何 tooltip（lang 里只有名字），新玩家在配方书里看到「精灵核心」不知道是什么、做了有什么用；没有任何进度（advancement）引导——整个模组只有 1 个真实进度 `orimod:restoration`，其余 6 个是配方解锁用的隐藏进度。完成主义者在进度界面里几乎看不到模组内容。
  - **[新·复现，minor]** 萤的提示把按键写死在文字里：`hint.orimod.bash_anchor`「对准灵灯**按R**」、`hint.orimod.shard_slot`「**按G**打开面板」。改键（或为了躲开 Iris 的 R 冲突而改键）后提示仍然说 R/G。
  - 觉醒：手持核心右键 → 成长面板自动弹出，技能槽 1 自动放「再生」**[通过]** `playtest-p5-a-awaken.png`。
- **速通玩家（乱按 2 分钟）**：`/ori unlock all` + 竞技场（5 个 Bash 锚、抓钩花、原木墙），15 个键每 2 tick 随机按放，5 段 × 500 tick：出现 dash / bash_aim / launch / wall_slide，`predictionRejections 0`、`mismatches 0`，无界面残留；服务端 MSPT 平均 3.3–4.8 ms、最高 28 ms；客户端 FPS 45–57（llvmpipe）；堆内存 GC 后 543 → 578 MB（无明显泄漏）；日志 0 条新警告 **[通过]**。
- **建造者（采掘）**：`key.attack` 按住 120 tick 连泥土都挖不掉——devtools 的按键保持不会触发原版持续挖掘 **[未测：工具限制]**。改从代码看硬度：`spirit_log / great_spirit_wood / ginso_wood / ginso_landing / ginso_root_lip / withered_spirit_log / spirit_platform` 硬度 2（可徒手挖），`ginso_vine / ginso_fungus` 秒破，`spirit_leaves / ginso_leaves / ginso_canopy` 0.2 —— **金索树、大灵树整棵都能被生存玩家砍掉**（P3 只提了遗迹机关）**[代码确认，minor]**。
- **破坏者（水/岩浆/火/活塞/TNT/苦力怕）**，每种模组方块一排，`gamerule mobGriefing true`：
  - **[新·复现，major] 流水/岩浆会冲掉「不可破坏」的关键方块**：在旁边或上方放一格水（或岩浆），40 tick 后这些方块直接变成水/岩浆、不掉落：**`ginso_water_heart`（金索水之心）**、**`life_cell_pedestal`（生命格台座）**、`spirit_light_vessel`（灵灯，硬度 -1）、`bash_anchor`、`grapple_flower`、`updraft`、`spirit_platform`、`spirit_vine`、`ginso_vine`、`ginso_fungus`、`great_tree_landing`。一桶水就能永久毁掉金索树逃亡入口或遗迹路线（这些方块 noLootTable，也拿不回来）。地图石、钥石门、灵树之心、腐化之核、裂墙不受影响。
  - 火：所有模组木头/树叶都不可燃，20 秒火烧无变化 **[通过/设计]**。
  - **[仍存在·扩大] 活塞**：除 P3 的锚/气流/藤外，`life_cell_pedestal`、`spirit_log`、`great_spirit_wood`、`ginso_wood`、`ginso_landing`、`ginso_root_lip`、`spirit_platform` 等 19 种都能被推走。
  - TNT：贴着引爆，19 种被炸掉（含 `life_cell_pedestal`、全部金索/大灵树木头、平台、锚、抓钩花）；充能苦力怕同样（大灵树木头扛住了）。地图石/钥石门/台座类/灵树之心/腐化之核/水之心扛住爆炸 **[信息：金索树、遗迹路线可被炸]**。
