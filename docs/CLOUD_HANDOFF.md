# 云端开发交接

交接日期：2026-10-18。仓库为 `wwlWang/oriMod`，保持私有，仅供用户自己玩。用户已要求把当前工作提交备份，交给另一个 Agent 继续开发；本机开发客户端保持停止。

## 从这里接手

1. 先读 [WORK_STATUS](WORK_STATUS.md) 和 [ACCEPTANCE_STATUS](ACCEPTANCE_STATUS.md)，再按待做事项查对应检查记录。完整范围及验收条件分别在 [PROJECT_PLAN](PROJECT_PLAN.md)、[ACCEPTANCE](ACCEPTANCE.md)。
2. 用户明确要求所有开发都在 `main`，不要新建功能分支或留下待合并分支。当前 `main` 包含 R13 和本次 R14 WIP 交接；R13 基线为 `6ab274785ac969e6a31c5b0de3aee38126140958`，标签 `ori-r13`。接手前同步最新主分支，提交及推送前再次核对远端，保留其他 Agent 的改动；发生分叉时合并到 `main` 并验证，禁止强推覆盖。
3. 先确认环境、LFS 和基线检查，再选下面一个范围完整实现。交回已推送到 `main` 的提交号、测试结果和待本机验证步骤，不因没有桌面环境而把实机项写成通过。

> 2026-10-18/19 云端已完成 R15–R27（标签 `ori-r15` … `ori-r27`），逐轮记录见 [WORK_STATUS](WORK_STATUS.md) 与 [TESTING](TESTING.md)，决定见 [DECISIONS](DECISIONS.md)。R20 自带光影 Spirit Glow，R21 为其默认 Spirit 配色，R22 加 Spirit 风格（调色、距离色雾、体积光），R23 让特效画质同时选择光影性能档，R24 检查下界/末地/雨天并修正下界配色，R25/R26 熔岩饱和与火把范围，R27 100 次混合移动（修复一次性标签残留）与效果往返内存采样；包源码在 `shaderpacks/spirit-glow/`；改光影后由 `zipSpiritGlow` 重新打包，`scripts/verify-spirit-glow.py` 复查切换。云端 llvmpipe 可以运行光影管线，但观感与性能仍需本机。

首版尚未完成。79 项产品验收中，目前 17 项通过、52 项部分验证、10 项未测。R0–R13 的修复记录见 [AUDIT_FIXES](AUDIT_FIXES.md)。

## 云端准备

需要 JDK 21、Git LFS、Python 3，以及下载 Gradle、Minecraft、NeoForge 依赖的网络。项目固定 Minecraft 1.21.1、NeoForge 21.1.248、Gradle 9.2.1；使用仓库 Wrapper。私有仓库访问由云端平台提供，不提交账号令牌。

在仓库根目录执行：

```sh
git lfs install --local
git lfs pull
git lfs fsck
java -version
./gradlew build runGameTestServer --console=plain
```

`build` 包含 JUnit；GameTest 报告为 `build/test-results/gametest.xml`，JUnit 报告在 `build/test-results/test/`。R13 在 Windows / Linux 各通过 262 项 JUnit、146 项 GameTest，零失败；这是历史基线，云端需报告自己的执行结果。

编译开发工具或运行图形客户端前，额外执行：

```sh
python3 scripts/sync-dependencies.py
./gradlew compileDevtoolsJava --console=plain
# 具备可用图形环境时再启动：
./gradlew runDevtoolsClient --console=plain
```

锁定的 GemRender 和 Flywheel 已通过 LFS 保存，无须重新构建渲染库。可选库由同步脚本按锁文件下载并校验；不要因为下载失败换成最新依赖或关闭哈希检查。普通构建和 GameTest 不需要可选画面库。依赖损坏时保留错误记录，按锁文件恢复。

本次交接检查了本地 26 个 LFS 条目，`git lfs fsck` 通过。源码 ZIP 可能只有指针，请使用 Git 检出和 LFS。Git 中已包含当前身体/手部 v010、技术样例 v008、采用的下载原件、正式运行资产及其生成脚本。

## 建议云端首轮：正式设置与持久化

> 2026-10-18 R15 已完成本节可在云端完成的部分：设置入口、版本化配置、画质/高级粒子/震动/闪光及其实际行为、旧配置与重启持久化，见 [TESTING](TESTING.md#r15客户端设置与特效预算)。仍待：光影包首次启用与外部 Iris 变化的生命周期、动态灯光（无实现）、本机观感复查。

无图形环境时，优先推进 ENV05、VIS05、VIS06、SAVE01/SAVE05 对应的设置实现。这是尚未完成且能拆成独立提交的工作；真实画面效果和菜单可用性留待有客户端时验证。

- 入口代码：`src/main/java/cn/wwl/orimod/config/OriClientConfig.java` 目前只有按键默认值标记和精灵视角；客户端注册在 `client/OriModClient.java`，现有界面可参考 `client/SpiritScreen.java`，视角保存见 `client/SpiritControls.java`。
- 按计划实现可操作的设置入口、保存及旧配置默认值。三档画质为流畅/均衡/精致，默认均衡；运动模糊和强景深默认关闭，震动较轻。提供计划中的光影、动态灯光、高级粒子、震动与闪光控制。已有自选光影和按键要保留。
- 开关必须接到实际行为。正式效果尚未存在的部分，明确记录依赖和未实现范围；不能只加字段就宣称功能完成。Iris/Photon 的可选集成须隔离客户端类，保留未安装可选库时的启动路径。
- 开发接口 `DevelopmentBridge` 的 `shaders` 操作已调用 `IrisCompat.invalidate()`；正式设置入口及外部 Iris 设置变化仍缺同等生命周期处理。相关现状见 [RENDERING_CHECKPOINT](RENDERING_CHECKPOINT.md)。
- 为默认值、旧配置、边界值和状态切换补有意义的测试；完成相应构建与回归。提供菜单操作及重启检查步骤，更新验收表时区分实现、自动测试和实机验证。

后续缺口还包括正式技能效果/声音、完整角色动作与原版交互、世界高度边界、无命令生存流程、性能路线和干净实例安装。具体条件以验收表为准；先交回一轮可复查的改动，再扩大范围。

## R14 留下了什么

本次 WIP 没有修改 `src/main` 的正式玩法逻辑：

| 文件 | 当前状态 |
| --- | --- |
| `src/devtools/java/cn/wwl/orimod/devtools/DevelopmentBridge.java` | 新增只读准星命中、生存状态和乘坐/睡眠等状态；`compileDevtoolsJava` 已通过 |
| `scripts/verify-vanilla-interactions.py` | 用实际准星、按键和原版容器检查两种形态、第一/第三人称的挖掘、放置、开箱、交易；矩阵未跑完 |
| `docs/WORK_STATUS.md` | 保存暂停点；本次补上交接入口 |

v001 交易退出时使用 `close_screen`，绕过原版容器关闭流程，剩余绿宝石未返回。v002 已改成 `gui_key` 的 Esc（256）。人形两个视角各完成六条记录，精灵第一人称只到挖掘结束，共 14 条；客户端在测试中正常保存并退出，不能当作完整通过。

截图仅查看了两张挖掘前画面，其余尚未完成视觉复查。`results.json` 仍为原始 `running`，另有 `pause.json` 标注中断。脚本的 `finally` 会继续请求客户端，连接断开时可能再次抛错，未能保存最终状态；后续应先完善这个异常收尾，保留原始错误。

继续实机检查时使用新的证据目录名，例如 `character-r14-interactions-v003-YYYY-MM-DD`。脚本会清空物品栏、改造 `(0,100,0)` 附近的平台、放置方块和村民，必须在专用测试世界运行。它依赖已觉醒角色等夹具前置，不是从全新生存世界自动建好的通关脚本。云端没有本机存档，需单独建立夹具。

## 现有验证与本机资料

- R13 修复第三人称鱼线与竿尖脱开，完成八组弩操作及八组鱼线组合复测；连续持弓录像已抽帧检查。完整角色验收仍未完成，详见 [CHARACTER_CHECKPOINT](CHARACTER_CHECKPOINT.md)。
- R13 发行 JAR SHA-256 为 `7cb35a66c2863329d89f6881c57d070eab8e791fb3ddb157b9f6bd71b62e7fbb`，未包含开发工具或 GameTest。交接 WIP 不能据此声明自己新增部分已完成运行验收。
- 原始证据在本机 `D:/ProjectData/oriMod/evidence/`，通过仓库的 `evidence/` 使用；运行世界、录像、失败日志和 `.external/` 不上传 Git。云端读不到这些文件时，沿用“历史记录”描述并生成自己的证据。
- R14 目录为 `character-r14-fixture-2026-10-18`、`character-r14-interactions-v001-2026-10-18`、`character-r14-interactions-v002-2026-10-18`。最新世界副本是 `orimod_dev_interactions_r14_20261018`；原世界 `orimod_dev_character_20261018` 保留。2026-10-18 19:29:38 已保存所有维度，客户端退出码 0。
- 审查前完整本地备份在 `E:/Backups/oriMod/pre-audit-20261018-130144/`，15934 个文件逐一核对 SHA-256。云端无需该备份即可构建当前代码；它用于恢复历史本地材料。

## 开发约定与交回要求

首版限定单人，见 [ADR-001](ADR-001-SINGLE-PLAYER.md)。继续保留当前玩法、圆润角色和渲染依赖方案；不要扩大为联机项目、替换角色或通过降低验收条件消除未完成项。

`src/main` 是交付模组，`src/devtools` / `src/gametest` 是独立源码集。发行包不得带本地测试接口。游戏操作使用已有开发 API；用户没有授权 Computer Use。接口令牌只保存在运行目录，不能写进日志、提交或交接消息。

先保存原始美术文件再实验；修改 Blender 资产要产生可编辑源文件、对应导出和视觉证据。没有图形环境时不要凭代码更改动画数值后认定外观已改善。对不同图形环境分别记录结果，构建及无界面测试无法替代本机光影、动画、声音和性能验收。

按任务直接提交到 `main`，保留失败与复测记录，更新 `WORK_STATUS.md`、受影响的检查记录和 `ACCEPTANCE_STATUS.md`。完成后推送主分支，交回提交号、实际运行的命令及结果、改动影响和待本机执行的步骤。仓库保持私有，不要把运行存档、私密令牌或大体积证据强制加入 Git。
