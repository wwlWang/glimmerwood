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
