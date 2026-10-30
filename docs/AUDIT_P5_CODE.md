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
