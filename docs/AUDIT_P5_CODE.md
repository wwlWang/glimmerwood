# AUDIT P5：代码层 / 服务端排查（只查不改）

日期：2026-10-30（Asia/Shanghai）。基线 `cf989da`（main）。方式：读代码 + 在独立 worktree（`/workspace/oriMod/p5-wt`，detached）里跑构建、GameTest、专用服务器。未改 `src/`，未提交任何临时测试。

标注：**[确认]** = 运行/测试复现；**[代码]** = 读代码得出，逻辑明确但未在游戏里复现；**[怀疑]** = 可能有问题，需进一步验证。

## A. 客户端静态状态 / 计时（Concurrency & tick）

### A1 [中][代码，高置信] 死亡/换维度后能量条一直红 —— 根因
- `client/SpiritCombatClient.java:22-26,36`：`energyFlashUntil = mc.player.tickCount + 8`，`energyFlashing()` 用 `player.tickCount < energyFlashUntil` 判断。
- 重生和换维度会新建 `LocalPlayer`，`tickCount` 从 0 重新计；但 `energyFlashUntil` 是静态字段，还停在旧值。复位分支 `:79` 只在 `mc.player == null` 时跑，重生/换维度过程中 `mc.player` 不会是 null，所以不会复位。
- 结果：只要死前（tickCount = N）有过一次「能量不足」拒绝，重生后就会红 N+8 tick。这和 P4 里「复活 20 秒后还红」对得上（N 约等于 400，也就是死前 20 秒左右被拒绝过一次）。
- 复现：先能量不足按一次技能，然后马上 `/kill`，重生后看能量条。
- 同类写法：`HUD` 只有 `SpiritHud.java:36,70` 读它，其他地方没有。

### A2 [中][代码] 客户端冷却用 game time 存在静态字段里，跨存档不复位
- `client/SpiritControls.java:246,272-273`：`waterDashReadyAt` 是静态字段，值为 `level.getGameTime() + DASH_COOLDOWN`。
- `client/SpiritMantle.java:21,49,60`：`readyAt` 是静态字段，值为 `now + MANTLE_COOLDOWN_TICKS`，`now` 就是 game time。
- 两个都没有 logout 复位。在同一个客户端进程里，从老存档（game time 很大）退出，再进一个新存档（game time 小），水中冲刺和攀上沿会在本地被一直判成「冷却中」。持续时间等于两个存档 game time 的差，可以是几小时。服务端不受影响，只是客户端不发、不做。
- 复现：存档 A 用 `/time add` 不行（那改的是 dayTime）。要在 A 里待久一点，或者直接进一个老存档，在水里用一次冲刺，退回标题，再新建世界学会水中冲刺，然后按冲刺。

### A3 [低][代码] 服务端会话冷却不存档，重登、死亡、末地回归都会清零
- `progression/EnergyState.java` 整个是不序列化的附件（`spirit/SpiritData.java:35-36`），里面有 `wellReadyAt`（灵泉回满，冷却 1200 tick）、`soulLinkReadyAt`、`spearReadyAt`、`burstReadyAt` 等。
- `SKILL_READY`（`SpiritData.java:26-28`）同样不序列化。
- 单人「保存并退出再进」就能让灵泉立刻再回满一次（免费回血回能量）。危害低，记一下。

## B. 构建 / 测试基础设施

### B1 [低][确认] 新 clone / worktree 直接跑 `scripts/check.sh` 编译失败
- `build.gradle:43` 用 `compileOnly files('libs/runtime/iris-neoforge-1.8.12+mc1.21.1.jar')`，这个 jar 被 gitignore 了，不在 LFS 里。
- `scripts/check.sh` 只有加 `--devtools` 才跑 `sync-dependencies.py`，而且是在 gradle 之后跑。
- 现象：`SpiritGlowIris.java:4 package net.irisshaders.iris does not exist`，9 个编译错误，BUILD FAILED。TESTING.md:635 写了前置条件，但 check.sh 自己没检查，报错也不直观。

## C. 专用服务器（dedicated server）

用 worktree 的 `./gradlew runServer`（headless，`--nogui`，离线模式，用 RCON 发命令）启动了 3 个新世界：seed 116555 两次，seed 117117 一次。

### C1 [通过][确认] 能正常加载，没有客户端类问题
- 启动到 `Done` 都没有 `ClassNotFound`、`RuntimeDistCleaner`、`NoSuchMethod`，也没有 mixin apply 失败。`orimod.mixins.json` 的 `client` 段在服务端会正确跳过。
- 全程日志 0 条 ERROR。没有配方、进度、战利品表、结构或 tag 的解析错误。
- 普通代码里引用客户端类的地方（`SpiritSpear:38`、`LightBurst:40`、`GloomShot:25`、`GloomHeart:214`，以及各 payload 里的 `cn.wwl.orimod.client.*`）都放在 `isClientSide` 或 `FMLEnvironment.dist.isClient()` 判断后面，lambda 是懒解析的，服务端不会加载到。
- 控制台/RCON 执行 `/ori status`、`/ori light 5` 会提示「A player is required to run this command here」。`/orimod reset @a` 在没人在线时提示「No player was found」。都不会崩。
- 服务端不要求 GemRender/Flywheel（`neoforge.mods.toml` 里是 `side="CLIENT"`），这是对的。

### C2 [中][确认] 新世界第一次启动时，大灵树选址会卡住服务器主线程约 30 秒
- `world/GreatTreeSites.java:47-52,77+`：`started(ServerStartedEvent)` 在主线程同步跑 `choose()`，会对几千个候选点采样噪声高度。
- 实测：`selection 30340 ms`（seed 116555）和 `32078 ms`（同 seed 的新存档），rejected 计数约 1300。测试时盒子上另有一个开发客户端在跑，CPU 有竞争，所以单独跑会快一些，但还是同一个量级。
- 影响：单人新建世界时，进入世界要多等约 30 秒。专用服务器上 `ServerStartedEvent` 已经在 watchdog 的计时范围里，机器慢的话可能触发 `max-tick-time`（默认 60 秒）直接把服务器杀掉（这一条是[怀疑]，没测到 60 秒）。
- 另外，日志里的 `spawn BlockPos{x=0, y=64, z=0}` 说明专用服务器上取到的 spawn 是默认值，选址是以 (0,0) 为中心算的。单人存档的 spawn 一般不在原点，所以两边的大灵树离出生点的距离不一样。这一条是[怀疑]，没深挖。

## D. 世界生成

### D1 [中][确认] `locate` 指到空地（spirit_tree / blighted_grounds）—— 根因
- 机制：`world/FeatureSiteStructure.java:60-89` 的 `findGenerationPoint` 只用噪声高度（`WORLD_SURFACE_WG`）做粗筛，粗筛通过就登记结构起点。真正放置时，`world/FeatureSitePiece.java:63-82` 的 `postProcess` 会对实际方块再跑一次 feature 预检（`SpiritTreeFeature.preflight` / `BlightedGroundsFeature.preflight`）。最多试 3 个候选点，全部被拒就只打一条 **DEBUG** 日志（`:81`），但结构起点还留着。`/locate`、导引和地图石都读结构起点，所以会指到空地。
- 实测（seed 117117，就是 P4 用的那个世界）：P4 报的两个空起点都复现了。`/locate` 给出 `(0,16)` 和 `(240,-400)`，加载区块后 debug.log 里是：
  - `spirit_tree start at (6,80,16) declined: FOOT_UNGROUNDED at 10,80,13 / CLEARANCE_OTHER at -1,81,23 / RELIEF at 12,78,20`
  - `spirit_tree start at (253,69,-385) declined: CLEARANCE_OTHER at 250,72,-382 / RELIEF at 252,74,-388 / RELIEF at 244,92,-388`
  - 用 `execute if block` 查了，`CLEARANCE_OTHER` 那两格是 **#minecraft:dirt**，也就是山坡本身挡住了树的空腔或树冠空间，不是植被，也不是遗迹保护（RUIN_GUARD）。
- 结论：粗筛对灵树只采样半径 2（`Site.SPIRIT_TREE sampleRadius=2, maxRelief=3`），而 feature 的落脚和净空检查要覆盖约半径 7（`SpiritTreeShape.RADIUS`），还有根部下探的限制。缓坡能过粗筛，到了放置阶段就被拒。腐化地面同理（粗筛 r12，但 feature 还要看实际土壤盘和水）。
- 代价：只要有一次被拒，导航就会扑空，而且日志看不出来（INFO 级别不打印）。修法有几种，比如粗筛半径和真实形状对齐、放置失败时让导航跳过（记录被拒的位置）、或者把 DEBUG 日志改成 INFO，这里只列出来不动手。
- 附带[怀疑]：被拒时如果原因被判成「可能是植被」（`vegetationMayCause` 对 RELIEF/CLEARANCE_OTHER 都返回 true），会先砍掉半径 14 内的原版树叶和原木再重试（`FeatureSitePiece:72,117-134`）。如果失败的真实原因是地形，就会白白砍出一块空地。这次两个点都在樱花丘陵和草甸，周围本来就没树，所以没能确认。

### D2 [低][代码] 平坦、超平坦和不生成结构的世界
- `GreatTreeSites.started` 在 `FlatLevelSource` 或 `generateStructures=false` 时直接 return，不会选址，不会报错。超平坦里 orimod 结构本来就不会生成（结构集受预设限制）。
- 生物群系 tag 只列主世界生物群系，所以下界和末地不会生成。其他模组的自定义维度如果用了 `#minecraft:is_forest` 这类生物群系，结构也会生成进去。`GreatTreeSites.load` 只在 `Level.OVERWORLD` 发布选址，自定义维度里 `reserved()` 恒为 false，那里的灵树和遗迹不会给大灵树让位。危害很低。

## E. 网络与安全（所有 C2S payload 逐个看过）

总体：服务端校验做得扎实，没有找到能刷资源、越权解锁或者让服务器崩溃的点。单人用的话这一块基本可以放心。
- `MovementInputPayload` → `SpiritMovement.accept`（`:38-51`）：会拒掉 NaN 和 Inf，摇杆值 clamp 到 [-1,1]，pitch 限制在 ±90，yaw 会 wrap，sequence 必须单调递增，客户端带来的 gameTick 最多补偿 2 tick。能力、次数、冷却都由服务端判断。**[代码]**
- `ProgressActionPayload` → `SpiritProgression.request`（`:43-137`）：字符串长度在解码时就限制了（24/32）。价格、前置条件、已拥有状态、槽位上限（`slot >= slots(player)`）都是服务端判断。光点数 clamp 在 0 到 1,000,000（`SpiritProgress:32`），负数和溢出都进不来。`shard_slot` 的格式解析也有防护。**[代码]**
- `MerchantBuyPayload`：构造时校验商品 ID 和 entityId。服务端用 `MerchantRules.refusal` 校验距离、报价日期和 token。**[代码]**
- `WellSelectPayload` → `SpiritWell.travel`：要求在出发灵泉旁边（`USE_RANGE`）、区块已加载、方块类型正确、两端都已发现、不在挑战或复苏中，还有 40 tick 冷却。**[代码]**
- `SkillBindPayload`：解码时限制 `slot < 3`，未知技能直接 DecoderException 断开。`bind()` 只接受已学会的技能。**[代码]**
- `AnimationActionPayload`（双向）：只做外观，同一 tick 只接受一次，只转发给追踪该玩家的人。**[代码]**
- `ChallengeActionPayload`：要求距离 ≤ 6、区块已加载、方块类型正确、满足能力条件、路线完整。**[代码]**

### E1 [低][代码] 切换形态（ToggleSpiritPayload）没有频率限制
- `SpiritForm.toggle`（`spirit/SpiritForm.java:15-36`）每次切换都会 `refreshDimensions`、发 FX（`SpiritFxLifecycle.formChanged`）、发 actionbar 消息。客户端按键本身有节流，但用改过的客户端每 tick 发一个包，就会每 tick 向周围玩家广播一次形态特效。单人无所谓，多人的话算一个小 DoS 和闪屏点。
### E2 [低][代码] `restoration_skip`、`exit_challenge`、`regenerate_stop` 在任何状态下都接受
- 这是设计，只能结束自己的会话，不能越权。记一下。
### E3 [信息] 灵泉传送（`well_travel`）不检查金索逃脱是否在进行
- `SpiritWell.canTravel`（`world/SpiritWell.java:68-75`）检查了挑战和复苏，但没检查 `GinsoEscape` 会话。逃脱中如果能走到灵泉旁边，就能传走。离开以后逃脱会被判失败并清理（P4 测过「走出去」的情况），不会卡死，只是缺一条更明确的拒绝提示。[怀疑：树里是否真有可用的灵泉没核实]

## F. 存档与持久化

- `SpiritProgress.CODEC`（`progression/SpiritProgress.java:13-23`）：每个字段都是 `optionalFieldOf` 带默认值，缺字段的旧存档能加载。紧凑构造器会把各个字段 clamp 或掩码（Shard.MASK、WeaponGift.MASK、capacity 0..3、wisps&7），`ShardSlots.normalize` 会修复重复或非法的槽位。**[代码，通过]**
- copyOnDeath 规则（`spirit/SpiritData.java`）：觉醒、形态、能力、技能绑定、进度和灵魂链接死亡后保留。能量（ENERGY）死亡后回满，这是设计。挑战和金索的恢复点（`CHALLENGE_RECOVERY`/`GINSO_ESCAPE_RECOVERY`）死亡后不保留，因为死亡本身就会结束会话，所以没问题。非死亡的 clone（末地回归）会复制所有可序列化附件，这是 NeoForge 的默认行为。
- 不序列化的附件（MOVEMENT、COMBAT、ENERGY_STATE、FEATHER_STATE、SKILL_READY 等）在重登、死亡和末地回归时都会重置，也就是 A3。服务端的 `MovementState.sequence` 重置成 0，但客户端的 `inputSequence` 是静态并且一直递增，所以不会出现「重登后输入全被当成旧包丢掉」的情况。**[代码，通过]**
- 静态 Map 泄漏：服务端的 `SESSIONS`、`SEARCHES`、`COOLDOWNS`、`FROZEN`、`ACTIVE` 都会在 logout、`ServerStopping` 或 `ServerStopped`（`OriServerLifecycle`）里清理。`AnimationActionPayload.LAST` 是 WeakHashMap。世界生成缓存的 key 用 RandomState 弱引用，或者挂在 registry 的结构实例上，会随存档释放。客户端缓存在 logout 或换 level 时会 clear。**没发现**跨存档或跨玩家的泄漏，唯一的例外是 A1 和 A2 这两个静态计时字段。

## G. 数据与资源

- `en_us`/`zh_cn` 都是 574 个 key，一一对应，没有缺失，占位符 `%s` 的数量也一致。代码里写死的 key 全部存在。动态拼接的 key（`challenge.orimod.ended.*`、`message.orimod.ginso.reason.*`、`*.energy/locked/not_spirit` 等）抽查过，后缀都齐全。**[确认，脚本]**
- 模型、方块状态和贴图的引用全部存在，所有 JSON 都能解析。没有 loot table 的 18 个方块都是 `noLootTable()` 加不可破坏或者有意不掉落。服务端加载时 0 条数据错误（见 C1）。**[确认]**
### G1 [低][确认] 中文术语混用
- Spirit Light：大多数地方叫「灵光」（15 处），但 `screen.orimod.shard_buy`「购买%s · %s 光点」、`screen.orimod.shard_capacity`「提升能量上限 · %s 光点」叫「光点」。也就是 P4 在碎片界面看到的混用，根源就是这两个 key。`challenge.orimod.started` 和 `route.forest_spring` 里的「光点」指路线上的亮点，含义不同，但更容易让人混淆。
- Energy：HUD 和界面用「能量」（约 30 处），技能说明和提示用「灵能」（`message.orimod.hammer_energy`、`charge_flame.energy`、`spear.energy`、`tooltip.orimod.energy_cell`、`shard.orimod.energy_harvest` 等 9 处）。
- Gloom：生物和水都叫「腐光」，但 `block.orimod.gloom_core`（英文 Gloom Core）叫「腐化之核」，和 `blighted_soil`「腐化之土」归成了同一个词。
### G2 [低][确认] 有两条文本把按键写死了，改键后会对不上
- `ability.orimod.charge_flame.description`「按住 X 聚集灵焰」、`hint.orimod.bash_anchor`「对准灵灯按R」。其他文本用的都是 `%s` 加 `Component.keybind`。这是 P4「技能键名对不上」的一部分来源。
### G3 [低][代码] 默认按键和原版冲突
- `SpiritControls.java:25,31`：Dash = C，Charge Flame = X，和原版「保存/加载快捷栏」（C/X，创造模式下按 C/X + 数字）冲突，控制设置里会标红。
- Bash = R 和 Iris 重载冲突的修复（`SpiritKeyDefaults.java:36-53`）只在 `KEY_DEFAULTS_APPLIED=false` 时跑一次，跑完就把标志位设成 true。所以第一次启动后才装 Iris、F8 到 F10 都被占用（这种情况会静默放弃）、或者用户在 Controls 里点了「重置按键」，冲突都会回来，而且不会再修。这很可能就是 P3 #1「之后冲突又回来」的原因。[代码，原因有多个候选，没逐个复现]

## H. 配置与模组兼容

- 服务端配置（`config/OriServerConfig.java`）：所有值都是 `defineInRange`，越界时 NeoForge 会自动改回并保存。`ginsoEscapeWaterSpeedMultiplier` 的范围 0.5..2.0 和 `GinsoEscapeRules.validMultiplier` 以及 `GinsoEscapePayload` 的校验一致。按 0.5× 估算，最坏的 `limitTicks` 约 9000，低于 payload 上限 12000，不会因为构造 payload 时抛 IllegalArgumentException 把 tick 弄崩。**[代码]**
- 客户端配置：`configVersion` 是 1..MAX，`LEGACY=1`、`CURRENT=2`，迁移只跑一次，之后 `plan.changed()=false`，不会出现保存后再重载的死循环。枚举值非法时 NeoForge 会改回默认。**[代码]**
- GemRender/Flywheel 在 toml 里是 `required + side=CLIENT`。客户端缺这两个时会显示 FML 的缺依赖界面，不会崩。服务端不需要。Iris 只通过 `ModList.isLoaded("iris")`（`SpiritGlow.java:17`）进入 `SpiritGlowIris`，没装 Iris 时不会加载这个类。**[代码]**
- 没有 JEI/EMI/Jade 插件。`orimod:peaceful_shapeless`（灵光，仅和平难度）这种自定义配方类型在 JEI 里可能看不到或者显示不对。[信息，没装 JEI 测]
- Pehkui 这类改体型的模组：`PlayerDimensionsMixin` 只按 vanilla 默认尺寸调高度，entity scale 之后会照常叠上去，理论上兼容。[代码，未实测]
- [信息] 配方：`ruin_guide.json` 用铜锭加纸就能合成遗迹指南，制图师卖 50 灵光一份，可以合成就绕开了购买。可能是故意的，记一下。

## I. 全量测试（worktree，`scripts/check.sh`）

| 轮次 | JUnit | GameTest | 结果 |
|---|---|---|---|
| 1（22:02） | 1816 / 0 失败 | 1397 / **1 失败** | BUILD FAILED |
| 2（22:27，`--rerun-tasks`） | 1816 / 0 失败 | 1397 / 0 失败 | BUILD SUCCESSFUL |

- 第 1 轮失败的是 `great_tree.ground_knots_cast_real_light`（`src/gametest/.../GreatTreeScaleGameTests.java:76-82`），报错「Only emissive, no real ground light」，第 2 轮通过，判定为 **flaky**。原因推测：放下方块后只等 20 tick 就断言 `getBrightness(BLOCK) >= 13`，光照引擎是异步排队的，CPU 忙的时候（当时开发客户端也在跑）20 tick 内可能还没传播完。[怀疑，没单独重复跑]
- 新 worktree 第一次跑会失败（B1），需要先把 `libs/runtime` 里被忽略的 jar 补上。
