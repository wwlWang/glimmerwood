# P5 试玩 / 审计问题修复计划

来源：另一路试玩代理的报告（`docs/PLAYTEST_R117.md` Pass 4、`docs/PLAYTEST_P5.md`、`docs/AUDIT_P5_CODE.md`），用户要求“先核实，确有其事的找合理方案修掉”。基线：`bff08b8`。

## 工作约定

- **先核实，再修。** 每一项先用代码阅读或实机复现确认，并在本文件对应条目后面标注 `[确认]`、`[未复现]` 或 `[部分]`，附上证据（代码位置、日志、截图）。`[未复现]` 的条目不改代码，写明尝试过的复现步骤。
- 按批次推进（E → F → G → H）。每批结束跑含地形的完整全量 `./gradlew build runGameTestServer --offline`，Claude 审查后提交。
- 冻结基线（森林 forest-terrain-pre-kernel、ginso-v2 资源、遗迹 manifest、结构 NBT）一律不准改。实机只用 `DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1`，关掉客户端之后才能编译。Codex 不做 git 写操作。
- 第一人称遵循原版 ItemInHandRenderer 基线，不要另外摆展示姿势。

初始审查记录（本轮独立核实见各 E 条目，部分结论有修正）：Claude 已经先读代码确认了以下几项：E2（`SpiritFlame`/`LightBurst`/`AirMovement`/`SpiritTargets` 自动选目标只判断 `instanceof Enemy`，僵尸猪灵属于 Enemy；`CombatTargets.canHit` 有正确的中立生物规则，但这几处都没用它）、E3（`energyFlashUntil` 用的是 `player.tickCount`）、E9（`ruin_guide.json` 配方是铜锭 + 纸）。

---

## 批次 E — 世界完整性与战斗（最严重）

### E1 关键方块会被水、岩浆、活塞、TNT 毁掉，或被生存玩家拆掉

**核实（bff08b8）：[确认]。** 已核对全部 45 个注册方块的硬度、爆炸抗性、活塞反应及实际流体行为，详见 [E1 全表与木材共享说明](../evidence/p5-fix/E/E1_BLOCKS.md)。默认状态实机矩阵：水/岩浆分别替换 15/45，活塞实际推动 23/45；独立 TNT 样本中 ginso_wood、great_spirit_wood、great_spirit_root 均被炸成空气。原报告“19 种”“这些都 noLootTable”不是本基线的准确总表，不能照搬。生存可挖由 `OriBlocks.java:43–46,63–64` 的硬度 2、默认挖掘逻辑与各自掉落表确认。

**审定实施：** 按方块种类保护，不做来源标记或旧存档位置迁移。进度功能块统一拒绝水/岩浆（LiquidBlockContainer + bucket replacement）、PushReaction.BLOCK、抗爆 3600000，保持原碰撞。没有生存获取途径的台座、祭坛/神龛、心等设不可挖；有掉落或配方的 Bash 锚点、抓钩花、上升气流、光平台、金索接落地板/藤蔓保持可挖可回收。抓钩花/上升气流有自身掉落，核实表先前“可合成”描述纠正为“可掉落”。三类地标木 ginso_wood、great_spirit_wood、great_spirit_root 仅防爆、防活塞，仍可挖。共享 spirit_leaves、spirit_vine 及自然装饰不动。保留灵灯爆发、钥石开门、技能破墙/下砸、核心净化。冻结资源不改。

验收：关键方块实际流水/岩浆、活塞、TNT 与生存采掘；另测动态/拼装状态、特殊交互不受环境保护影响。

### E2 萤的灵焰等自动攻击会打中立生物（僵尸猪灵）

**核实（bff08b8）：[确认]。** `SpiritFlame.java:43`、`LightBurst.java:56`、`AirMovement.java:131` 均直接按 Enemy 选伤害目标，和平的僵尸猪灵也满足；`CombatTargets.java:14–22` 才检查 NeutralMob 是否对该玩家生气/有攻击目标。实机：不攻击、关闭生物 AI 的僵尸猪灵 AngerTime=0，开启伴随灵焰后生命 20→8；关闭灵焰时初始为 20（[操作记录](../evidence/p5-fix/E/e2-live.json)）。额外发现 `Flash.java:123–135` 按 UNDEAD 标签伤害，也需走同一敌对规则。`SpiritTargets.java:87` / `BashedImpact.java:29` 是 Bash 选择/撞墙跟踪，并非直接伤害；`ShardEffects.java:80–86` 的强力 Bash 伤害已经调用 canHit，不能把无伤害抓取等同自动攻击。

**审定实施：** 所有真正造成伤害的入口（SpiritFlame、LightBurst、AirMovement 下砸、Flash 对亡灵）统一走 CombatTargets.canHit。无 owner 的 LightBurst 只打存活、非旁观的 Enemy 且不是 NeutralMob。Bash 抓取和撞墙跟踪不改。验收覆盖平静中立生物不受伤/不被激怒、敌对目标仍受伤。

### E3 能量不足的闪红在死亡或换维度后一直卡着

**核实（bff08b8）：[确认]。** `SpiritCombatClient.java:22–36,79` 的静态 energyFlashUntil 使用玩家 tickCount，只在 player=null 时清零；到期后也不清除旧值。实机拒绝后再死亡，客户端 playerTicks 从 10819 重置到 48，能量已补满 100/100，HUD 仍红（[截图](../evidence/p5-fix/E/p5e-e3-full-energy-after-respawn.png)、[记录](../evidence/p5-fix/E/e3-live.json)）。换维度同根因由代码确认，本轮未另跑维度往返。修复应同时处理玩家替换/世界替换及 awaitingAttackUntil，不能只处理退出菜单。

**审定实施：** 闪红和 awaitingAttackUntil 使用客户端单调 long tick；login、disconnect 及玩家/世界身份替换统一复位，不能依赖观察到 player=null。JUnit 测时钟和身份边界，实机复核重生。

### E4 连续换世界时冷却卡住（静态字段里存的是游戏时间）

**核实（bff08b8）：[确认，代码]。** `SpiritControls.java:246,267–277` 的 waterDashReadyAt、`SpiritMantle.java:21,35–60` 的 readyAt 都是静态世界 gameTime 截止值，退出/换世界没有重置。旧世界 now=100000 时留下 100020，新世界 now=100 会拒绝到追平旧时间。另发现 `SpiritControls.java:168,212–216` 的 wallJumpAt 同样残留，负时间差持续触发 0.25 空中操控倍率（本来只应持续 7 tick）；现有 owner/world 切换分支第 50–54 行未重置它。本轮未重复报告里的跨存档实机测速；已逐项审阅客户端计时字段，分类见 [README](../evidence/p5-fix/E/README.md)。

**审定实施：** 统一生命周期清理 waterDashReadyAt、wallJumpAt、SpiritMantle 全部活动态与期限、运动预测缓存。保留非世界时间的渲染/面板职责，不扩大到未发现问题的模块。

### E5 存档退出再进，冷却全部清零（灵泉可以白嫖补满）

**核实（bff08b8）：[确认]。** `SpiritData.java:26–28,35–36` 中 ENERGY_STATE / SKILL_READY 无 serializer，NeoForge AttachmentInternals.copyAttachments 会跳过无 serializer 的附件，死亡与非死亡 Clone 均无法复制。实机同一口井：gameTime 61536 补到 100；61545 再用被冷却挡住（能量 0.8，只有被动回能）；退出重进，61586 再用立刻 100，距离首次仅 50 tick，远小于 1200（[记录](../evidence/p5-fix/E/e5-live.json)）。soulLinkReadyAt、spearReadyAt、burstReadyAt 等同属 EnergyState。**修复建议：** 单独持久化实际冷却期限并 copyOnDeath；SKILL_READY 是 `SpiritSkills.java:26–28` 每 tick 派生的 UI 镜像，应从已保存冷却重建，不单独保存另一份真相。蓄力/治疗进行态保持临时；已保存的祭坛/钥石/树心每日限制不混入此次附件迁移。

**审定实施：** 独立 SpiritCooldowns attachment 持久化功能冷却，统一主世界 game time，copyOnDeath，缺字段默认就绪。EnergyState 只保留临时动作、治疗和租约。SKILL_READY 由真实期限推导，并换算到当前维度显示时钟，不存两份。验收 NBT 读写、死亡/非死亡 Clone、跨维度时钟与实机重进。

### E6 金索树逃亡期间可以用灵泉传送离开

**核实（bff08b8）：[确认，实机]。** `SpiritWell.java:73–81` 检查 SpiritChallenge 与 Restoration，却漏掉 GinsoEscape.active。自然 Course 3 / Escape 2 金索树的存档副本内手放并发现两口井（心旁 5049,76,4314；远处 5161,76,4312），分别在 PREPARE、RUNNING 通过真实灵泉界面点目标；RUNNING 的 elapsed=60 时能打开界面，elapsed=75 判 OUTSIDE，回到 5048.5,76,4314.5 并冷却（[记录](../evidence/p5-fix/E/e6-running-live.json)、[截图](../evidence/p5-fix/E/p5e-e6-running-well.png)）。这是手放井复现，不声称已找到同位置的天然井。建议在公共 canTravel 前置拒绝并提示，覆盖打开界面与提交传送两条路径。

**审定实施：** 在灵泉打开界面和最终传送请求的共用校验中拒绝 GinsoEscape PREPARE/RUNNING，并显示中英文 actionbar。拒绝不能移动玩家或使逃亡进入 OUTSIDE。

### E7 水之心拒绝开始时，手里的遗迹指南也被触发

**核实（bff08b8）：[未复现]。** 同一金索树，重置技能后只解锁 Dash+Bash，主手指南，真实 key.use 单点 1 次，再单点 5 次、连续按住 20 tick；均显示缺少攀爬/牵引/滑翔，未启动指南搜索（locator cooldowns/tickets/searches 全为 0，客户端日志无搜索消息）。[重复点击截图](../evidence/p5-fix/E/p5e-e7-repeat-denial.png)、[记录](../evidence/p5-fix/E/e7-repeat.json)。`GinsoWaterHeartBlock.java:18–20` 无论 start 成败都 sidedSuccess；原版 MultiPlayerGameMode.performUseItemOn 在结果 consumesAction 后直接返回，不继续物品 use。按约定不改代码；若原报告用了独立 use_item 调用、射线移开后第二次按键或潜行，需要原始输入序列另核，不把这些推测写成既定原因。

**审定实施：** 未复现，不改代码。

### E8 和平难度召唤腐光之心，仍提示“苏醒了”

**核实（bff08b8）：[确认]。** `GloomAltarBlock.java:36–61` 未检查难度，创建实体后无条件激活祭坛并发 altar_woke。实机和平难度点击独立祭坛：无存活 GloomHeart，BE 短暂 ACTIVE，后由 `SanctuaryBlockEntity.java:46–51` 回到 DORMANT，但 activated=true 残留（[初始](../evidence/p5-fix/E/e8-live.json)、[稳定后](../evidence/p5-fix/E/e8-settled.json)）。修复必须在创建/激活前拒绝，避免仅改文字。

**审定实施：** 和平难度在创建 Boss / 激活祭坛前拒绝，显示中英文 actionbar；activated 不置位。

### E9 遗迹指南能用铜和纸合成，绕过 50 灵光的售价

**核实（bff08b8）：[部分]。** 铜+纸配方属实，但不存在“50 灵光一份指南物品”的商人商品：`MerchantRules.java:17` 实为遗迹定位 40、灵树 60、腐化地 50；`MerchantTrading.java:34–53` 发定位 payload、不发指南物品。灵光同时有实物和数值，`SpiritProgression.java:151–156,264–268` 将已觉醒玩家库存灵光按 1:1 自动入账。`git log --follow` 显示配方自 c8b254e 引入，彼时 WORLD_CHECKPOINT 明写“初次赠送，丢失铜纸重制，G 面板有同入口”，当前第 272–281 行仍赠送、第 70 行仍有免费面板搜索。指南 Mode.ANY（遗迹+哨站）与商人 Mode.RUINS 有交集，不是 50 灵光腐化地定位的替代品。

**我不同意：直接删配方就能修复这里的经济重叠。** 免费赠送和免费面板搜索仍在，删配方只破坏丢失后补回路径。建议把本项改为“免费基础导航与制图师付费定位的差异化”，先保留指南配方；最小方案可撤下与免费导航重复的付费遗迹定位，只保留树/腐化地服务，或另审更有价值的付费精确定位。取舍已审定，见下方实施规则。

**审定实施：** 保留铜纸指南配方。免费 RuinLocator 已跳过完成的遗迹（按 clearedKey 过滤），因此付费遗迹定位没有审定的差异化价值；撤下该项，仅保留灵树 60 / 腐化地 50 定位。旧商品请求拒绝，商人流程不再启动或取消指南的异步遗迹搜索。

---

## 批次 F — 世界生成与构建

### F1 `locate` / 遗迹指南指到空地（生灵之树、腐化地）
[确认] `8ae6585` 正常噪声世界实测：5555 / 116555 / 5005，生灵之树分别 10/14、7/16、9/14 实际生成，腐化地 11/11、13/14、11/15。实际推进到 FULL 并核对心/核心 journal 与存活方块，失败包含起伏、净空、湿地、遗迹占位；证据 `evidence/p5-fix/F/baseline/`。
根因（审计 D1）：结构阶段只粗略检查地形，到 feature 阶段放不下时就不生成，但结构起点已经登记了。
方案：让**结构阶段的判定成为最终判定**。
- 在 `findGenerationPoint` 里，用 `chunkGenerator.getBaseColumn` / `getBaseHeight`（只看噪声地形，不看装饰）做完整的列预检，覆盖 feature 阶段所有可能失败的条件：起伏、净空、根能否着地、水面等。
- feature 阶段改为**一定放得下**：碰到普通植被就清掉；根不够长就用根或原木柱补到地面；只有在遇到无法自适应的情况（比如撞上其他结构）时才放弃，而且放弃要极少见。
- 验收：对多个种子扫描所有 `locate` 结果，每个登记过的起点都要真的生成了树或腐化地（报告成功率，目标 100%，至少 ≥99%）；冻结基线不变；离线布局探针的用法参考记忆中的 offline-layout-probe。
- 实施验收：三种子实放 **81/81**，各类型/种子的数量均不低于基线；地下支撑穿越水/空气最多 48 格，不因深空腔拒绝。完整四项计数、48 点 locate、出生区/跑图对照及已审定 636 ms 例外见 `evidence/p5-fix/F/README.md`；最终 JUnit **1870/1870**、含地形 GameTest **1541/1541**。

### F2 新世界第一次启动，主线程卡约 30 秒（大灵树选址）
[确认] 5555 首次主线程搜索 93.906 秒，重进 162.817 秒；99.7% 为原版 NoiseChunk 密度采样，静态初始化不足 0.2 ms。审定方案：单遍取两种高度、原采样点先主干后根部、保守群系预筛；`loadLevel` TAIL、watchdog 启动前完成 800→2500；存搜索版本/阶段/NO_SITE，已有选址不重选。ServerStarted 只发布。完整方案与调用栈见 `evidence/p5-fix/F/F2_PLAN.md`。
方案：先测量（选址的真实耗时、调用栈），再提方案交我审，不要直接改。可选方向：把选址挪到工作线程并且不阻塞 tick；把计算拆成多 tick 增量完成；按种子缓存结果。要确保专用服务器的看门狗（默认 60 秒）不会触发。
- 实施验收：四种子 800 阶段与原选点一致，首次连续扩到 2500；默认 60000 ms watchdog 下均进入正常 tick，5555 重进不重搜。详见 F/README.md 和 F2_PLAN.md。

### F3 新克隆的仓库直接编译失败（Iris 包被 git 忽略）
[确认] 编译依赖原为被忽略的本地 Iris jar。公开 Maven 的 `maven.modrinth:iris:1.8.12+1.21.1-neoforge` 与本地文件 SHA-1 一致；改为 compileOnly，运行时仍为可选依赖。
方案：Iris 改为从公开 maven（比如 Modrinth maven）以 `compileOnly` 方式引入，或者写成可选依赖并由反射隔离，不再依赖本地被忽略的 jar。验收：在临时目录里 `git clone` 一份新仓库，`./gradlew build` 能通过（第一次需要联网下载依赖；如果沙箱没有网络就告诉我，由我来跑）。
- 实施验收：最终工作树独立干净导出（无本地 Iris jar、无构建缓存）编译与 JUnit **1870/1870** 通过；提交 f90b766 后审查方从 GitHub 实际 `git clone` + `git lfs pull`，`compileJava compileGametestJava` 通过，克隆中无 Iris jar。

---

## 批次 G — 移动与状态效果

**实施完成，待审提交（2026-10-31）**：G6→G3→G1→G2→G4→G5 按审定方案完成。最终 JUnit **1877/1877**、含地形完整 GameTest **1568/1568**；客户端实测通过，冻结资源 **33/33** 未变。改动清单、数值及两组 HUD 截图见 [G 交付记录](../evidence/p5-fix/G/README.md)。下列核实数值是修复前记录。

### G1 史莱姆块

**核实 [部分]**：精灵 3 格落下后 360 tick 仍约 0.963 格循环反弹，人形已停稳；30 格“潜行＋冲刺”下砸反弹 43.199 格，锤砸本轮为 26.301 格，未复现“锤砸高于起点”。原报告 +13 格实际是技能下砸。根因与已审定方案见 [G 核实 G1](../evidence/p5-fix/G/README.md)。

- 精灵形态站在史莱姆块上会一直小幅弹跳：要像原版人形一样，几次之后就停稳。
- 按核实纠正：+13 格来自技能下砸。技能下砸与锤砸统一按真实起点、当前 GRAVITY 的原版自由落体速度限制反弹；30 格对照均为约 14.614 格，不超过起点。
- 验收：GameTest 记录弹跳次数和最大反弹高度。

### G2 摩擦与减速方块

**核实 [部分]**：精灵石头/冰/浮冰/蓝冰均 0.320 格/tick，释放惯性都是 0，确认冰面问题。未觉醒、觉醒及精灵切回的人形均正常（蓝冰释放滑 2.528 格，石头 0.333 格），人形回归未复现；浆果、网、雪、灵魂沙普通行走均已减速，不为这些未复现项加特判。数值/文件行号/修复与验证见 [G 核实](../evidence/p5-fix/G/README.md)。

实施：friction ≤0.6f 保留 Ori 原曲线；高滑度地面保留原版摩擦惯性，普通输入顶速 ≤0.32×属性倍率。实机冰/蓝冰释放惯性 2.511/2.852 格，均超过人形的 70%；石头及人形回归保持原值。未复现的减速方块问题不加特判。

### G3 药水效果对精灵形态无效

**核实 [部分]**：速度/缓慢属性已变化但精灵仍固定 0.320；缓降有作用但被固定重力补偿污染（20 tick 下落精灵 5.532 格，人形 1.819）。跳跃提升、JUMP_STRENGTH、海豚恩惠及缓降＋滑翔均有效，不重复实现这些原版路径。完整属性与形态对照见 [G 核实](../evidence/p5-fix/G/README.md)。

实施：MOVEMENT_SPEED/0.1，排除原版疾跑 modifier，最低倍率 0.15；只缩放普通目标速度与加速度。垂直整形读取原版有效重力，缓降下降交还原版。跳跃、海豚恩惠、滑翔回归通过。

### G4 精灵在水下不缺氧，却还显示原版氧气泡

**核实 [确认]**：已学水中冲刺时服务端空气恒 300，仍显示十气泡；未学技能与人形正常耗氧。仅按真实永久呼吸资格隐藏，不能笼统隐藏精灵或满空气玩家。截图及修复与验证见 [G 核实](../evidence/p5-fix/G/README.md)。

精灵形态如果确实不会消耗空气，就隐藏 AIR_LEVEL 这一层 HUD。

### G5 深呼吸碎片在学会水中冲刺后完全无效

**核实 [确认；保守方案已批准]**：水中冲刺费用为 0，不新增费用。未学技能保持耗氧减半（约 15→30 秒）；已学后走 SWIM_SPEED 属性，游泳 +25%，精灵额外推进与上限使用同一倍率。冲刺初速/14 tick 冷却不变。说明随解锁状态切换，卸下/切形态/死亡/重进清理临时 modifier。见 [G 实施与验证](../evidence/p5-fix/G/README.md)。

按上述审定版本实现：碎片在学技能前后都有收益，说明随解锁状态动态切换。原建议中的“水中冲刺能量消耗减半”因原费用为零而不采用。

### G6 精灵形态雨中激流航程过长

**核实 [确认]**：同条件各 3 次，落地距离精灵 94.697 / 人形 16.582 格，峰高均 30.878 格；旋转结束后精灵仍飞约 49 格。首帧 shared spin flag 尚为 false，地面摩擦逆运算已把 1.50 推力放大成 2.69；需从 startAutoSpinAttack 开始覆盖整段惯性，不能只检查旋转标志。见 [G 核实](../evidence/p5-fix/G/README.md)。

来源：`docs/PLAYTEST_P5.md` P5-J。原报告在雨中、仰角 60°、激流 III、蓄力 20 tick 后松开：精灵升高 30.9 格，最终水平约 93 格；人形水平约 12 格、升高约 17 格。本轮独立对照见上，原报告部分数值未复现。
- 先确认 `isAutoSpinAttack` 期间及其结束后的惯性是否仍叠加精灵低重力、低空气阻力。
- 审定方向：从两端 startAutoSpinAttack 起完整交还原版运动，直到落地或入水/飞行/换形态等终止；不缩放原版推力。首帧推力必须相同，包含旋转结束后的惯性，总航程差 ≤10%。
- GameTest 在相同起点、天气、装备、仰角、蓄力时间下对比两种形态的水平航程、最大升高和落地时间；保留雨中激流正常可用。

---

## 批次 H — 按键、文字与外观

> 核实与实现已完成，H1/H3 方案、H1/H5/H6/H7/H8 实机图已审通过；含地形完整全量 exit 0：JUnit 1881/1881、GameTest 1580/1580，均无失败或跳过。详见 `evidence/p5-fix/H/README.md`。
>
> 审定键位：冲刺 V、猛击/牵引 B、面板 G、灵魂链接 N、光爆 Z、蓄力灵焰 H、闪光 J、切换形态 M。既有 options 绑定逐条保留；删除两套自动挪键，冲突只提示一次具体键与双方动作。
>
> 审定术语：货币「灵光 / Spirit Light」、资源「能量 / Energy」；腐光 Gloom 与腐化 Blight 分开，生态核心/材料为 Blight Core / Blight Residue；碎片「坚韧 / Resilience」。只改显示文本。zh/en 各 608 key，集合与占位符一致；旧称残留详见 `TERMS_RESIDUALS.md`。
>
> H6 [部分]：横向外观是凝光武器本体，并非无限光束；原版持剑/三叉戟姿态也横向。非玩家持有者改用普通 handheld 图标。H9 [未复现]：两种 HUD 模式均已过滤旁观者，保持代码。

### H1 按键冲突
冲刺默认在 C、蓄力灵焰默认在 X，正好和原版的保存、加载快捷栏键重了；Bash 在 R 上又和 Iris 冲突，自动改 Iris 的逻辑只跑一次。
方案：列出模组所有按键的默认值，以及原版和 Iris 的默认键，挑一组**完全不冲突**的默认键交我审。改完之后删掉“自动修改 Iris 键位”的逻辑，不再去碰别的模组的设置。启动时如果检测到冲突，只给一次 toast 提示。

### H2 提示文字把按键写死了
所有“按住 X”“按 R”“按 G”这类文字，改用 `Component.keybind(...)` 或当前绑定键的显示名。用 grep 把所有写死的键名都找出来。

### H3 用词混用
“灵光 / 光点”“灵能 / 能量”“腐光 / 腐化”：先出一张术语表，列出每对词的所有用法和它们指的是不是同一个概念，交我拍板后再统一修改 lang。不同的概念（比如腐光之心这个 Boss 名，和腐化地这种地形）不能强行合并。

### H4 意志之光页面显示 `minecraft:overworld` 这种原始维度名
改为本地化的维度名（原版维度用原版翻译键，模组维度补翻译），没有翻译的回退到格式化后的路径名。

### H5 精灵身上的箭按人形尺寸渲染
插在身上的箭、蜂刺等改为按精灵模型缩放和定位；如果做不好，就对精灵形态隐藏这些层。

### H6 盔甲架拿着能量武器时，会伸出一道很长的横向光束
先查清这道光束是哪个渲染路径画出来的（物品模型的 display，还是武器发光层假设持有者是玩家）。非玩家持有时用正确的变换，或者不画这层特效。

### H7 碎片界面是原版灰色面板，和其他青色面板风格不统一
保留原版样式的格子和拖放交互，只把外框和底色换成模组统一的青色面板风格。

### H8 新玩家缺少引导
- 精灵核心物品加上 tooltip：说明它是什么、怎么用。
- 加一组原版进度（独立的 Ori 页签），覆盖主线：获得精灵核心 → 首次化灵 → 首个技能 → 首次碰到遗迹或灵树 → 金索树 → 腐化之核 → 世界复苏。每条描述写清楚下一步去哪里、做什么。
- 不加强制弹窗和教程关卡。

### H9 旁观模式下显示旧的左侧能量条
批次 D（`bff08b8`）已修复，只需复核。

---

## 批次 I — 第二轮试玩核实与修复（基线 7bfd6dd）

实施与验证记录：`evidence/p5-fix/I/README.md`。I1–I4 核实后直接实施；I5 不改；I6 以调查为先。

> 已完成：干净源码导出初始和运行结束后均无 evidence 目录，含地形完整全量 exit 0：JUnit **1881/1881**、GameTest **1625/1625**，均无失败或跳过。实机四武器 × 十目标 × 两种能量状态 **80/80**；三种子真实 FULL 实放由 **81/89** 提升至 **89/89**，登记数未减少。33 个冻结文件逐字节一致；I6 连续观察未复现，未改调度。

- **I1 [确认] 条件中立生物被萤先手攻击**：有 owner 的 `CombatTargets.canHit` 与无 owner 的入口都把猪灵/蜘蛛当成普通 Enemy。增加 `orimod:conditionally_neutral` 标签；仅攻击针对 owner 的目标/报复/愤怒状态，无 owner 不猜测敌对关系。检查 Goal、Brain ATTACK_TARGET 和 ANGRY_AT。
- **I2 [确认] 武器抢占交互**：`SpiritCombatClient.input` 在原版方块/实体交互分派之前取消 use 输入。把武器的能量/冷却拒绝放到原版 `MultiPlayerGameMode.useItem` 后备路径，方块/实体成功交互优先；四把武器逐项验证。
- **I3 [确认] 干净克隆报告目录不存在**：`BlightGrowthGameTests` 直接写被忽略的 evidence 路径。改至 build/test-results，GameTest 报告写入统一创建父目录；最终在初始无 evidence 的干净源码导出运行含地形全量。
- **I4 [确认] locate 空起点**：多种子真实 FULL 基线复现苔石及 UNEXPECTED_RUIN。林间营地的预测使用未扩展的 piece 盒，实放使用 terrain_adaptation 扩展后的总盒，口径不一致；同时发现雪块/细雪及浅岩浆湖池的后期生成差异。修复后按相同种子/查询网格复扫，实放率 100%，实放数不得低于基线。冻结布局、NBT 和优先级保持不变。
- **I5 [审定不改] 自然树木/叶子的保护策略**：沿用 E1 审定范围。
- **I6 [未复现] 萤偶尔几分钟不攻击**：没有持续目标锁；每个就绪 tick 重新选择 6 格内视线通畅目标。冷却为主世界 game time 的 14 tick。检查遮挡、形态/觉醒/开关、目标筛选与渲染区别；实机连续 3610 服务端 tick、257 次攻击无分钟级停火，未调整生产调度。

---

## 交付

每批完成后在 `evidence/p5-fix/<批次>/README.md` 写：每条的核实结论、改动、测试和截图。证据目录不提交。
