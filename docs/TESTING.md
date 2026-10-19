# 回归测试

需要 JDK 21、Git LFS 中的两个锁定依赖，以及首次下载 Gradle / Minecraft 依赖的网络连接。

```sh
./gradlew test runGameTestServer --console=plain
```

Linux / macOS 也可执行 `scripts/check.sh`（加 `--devtools` 同时同步可选依赖并编译开发源码）：先做 `git lfs fsck` 与锁定 JAR 实体检查，再运行 `build runGameTestServer`，最后由 `scripts/test-summary.py` 汇总 JUnit / GameTest 数与失败名单，任何失败或缺报告返回非零。

Windows 可用 `gradlew.bat`，或使用本机 socket 兼容包装 `./scripts/gradle.ps1 test runGameTestServer --console=plain`。

## 测试边界

最新 R13 构建与源码摘要见 `evidence/character-r13-builds-2026-10-18/report.json`，两系统仍各通过 262 / 146 项。实机脚本 `verify-character-crossbow.py` 覆盖八组装填、保持、发射和取消，`verify-character-fishing.py` 检查八组主副手与光影组合、潜行和连续移动。已修复第三人称鱼线起点；完整结果与启动失败记录见 [审查记录 R13](AUDIT_FIXES.md#r13连续持弓特殊物品与鱼线)。

| 层次 | 当前内容 | 报告 |
| --- | --- | --- |
| JUnit | 存档编解码与旧字段、价格和前置、刀连击时间线、形态尺寸、三类布局契约 85 项，GLB 动画契约与缺片段回退 61 项、移动规则和协议 92 项、遗迹旋转与指引 24 项；R15 客户端配置修正与升级 9 项、粒子预算与画质 19 项、震动闪光时间线 14 项、特效载荷 8 项，共 312 项 | `build/test-results/test/`、`build/reports/tests/test/` |
| GameTest | 形态与成长 30 项、Bash 22 项、刀目标与伤害 17 项、和平合成 16 项、伤害打断 24 项、会话生命周期 10 项、真实传送入口 9 项、遗迹旋转与生成顺序 18 项、R15 特效载荷路由 8 项，共 154 项 | `build/test-results/gametest.xml` 和控制台 |
| 真实客户端 | 形态、模型、跳跃；自投射物与 Boss 拒绝、有效 Bash、村民保护与敌怪伤害 | 仓库外 `evidence/audit-r1-smoke-v002-2026-10-18/`、`evidence/audit-r2-target-safety-v001-2026-10-18/` |

2026-10-18，Windows JDK 21.0.4 与 WSL Debian / Linux JDK 21.0.8 均通过 262 项 JUnit 和 146 项 GameTest，失败数为零。Linux 使用独立源码目录、构建输出和 Gradle 缓存；Gradle 发行包复用了已下载副本。其直接访问插件仓库曾超时，使用本机现有代理后完成下载和执行。游戏认证服务的后台连接在 Linux 留有超时日志，未影响离线 GameTest；不据此宣称在线连接通过。

R10 在最新渲染代码上重复上述两系统检查，构建与 JAR 检查记录见 `evidence/audit-r10-builds-2026-10-18/report.json`。发行 JAR SHA-256 为 `31d26687c73f7aa692ad1eb50b944fd7d083c77b60d82f0cca54c08aff099778`，不含开发接口和 GameTest。源码摘要与最终静态、动态、飞行和火焰记录一致；原始 Blender / GLB 哈希未变。第一次 Linux 命令误用不存在的 Ubuntu 发行版，保留失败日志，随后在实际 Debian 环境执行通过。

渲染脚本为 `scripts/verify-player-layers.py` 和 `scripts/verify-player-layer-motion.py`，须在专用世界运行，会使用命令布置平台和装备。最终记录分别为 `audit-r10-layers-v004-2026-10-18`（24 组）和 `audit-r10-motion-v003-2026-10-18`（实际按键录像、14 张补充截图、两次资源重载）。额外飞行与火焰夹具的完整脚本随 `audit-r10-effects-v001-2026-10-18`、`audit-r10-fire-spin-v001-2026-10-18` 保存。已发现的起飞失败与未有效触发的旋转均未改记通过，视觉检查范围见 `RENDERING_CHECKPOINT.md`。

R10 含多轮失败和复测，共约 81 MB。148 个截图或大 JSON 已无损压缩：截图保存为 WebP，逐张确认解码后的 RGBA 字节一致；JSON 使用可精确恢复原字节的 gzip。原/新文件哈希、像素哈希与路径映射见 `audit-r10-storage-2026-10-18/manifest.json`。原速 MP4 未重新编码，原失败版本没有删除。

R11 的八组实机鞘翅/激流回归位于 `audit-r11-flight-v004-2026-10-18`，脚本为 `verify-vanilla-flight.py`；前面三轮保留视觉缺陷或测试区流水污染的记录。最新代码再次在 Windows / Linux 通过 262 项 JUnit 与 146 项 GameTest。发行 JAR SHA-256 为 `9c92e27c2680bad7ba9aba2ba81a105c14d7b3e08653bf57086784a0501e4279`，原始模型哈希与开发工具隔离检查通过，详见 `audit-r11-builds-2026-10-18/report.json`。该轮同样使用逐像素核对的 WebP 与可恢复原字节的 gzip，映射保存在 `audit-r11-storage-2026-10-18/manifest.json`。

R12 恢复计划规定的精灵默认隐藏盔甲，真实客户端用 `verify-spirit-appearance.py` 检查两种光影下各五组装备状态和实际牵绳输入；最终 20 张图已查看。证据为 `audit-r12-appearance-v003-2026-10-18`，前两版观察镜头夹具问题保留。两系统 262 / 146 项通过，报告、原始 JUnit XML 与构建/客户端日志保存在 `audit-r12-builds-2026-10-18`。JAR SHA-256 为 `05648dbc7a9439fd050b00ccfdf694e255d82119a6a6018bafddbc3f680f1a08`，无开发或 GameTest 条目，原角色源文件与 GLB 哈希未变。60 张截图逐像素核对后保存为无损 WebP，全轮约 27.59 MB，映射见 `audit-r12-storage-2026-10-18/manifest.json`。

GameTest 位于独立 `src/gametest` 源码集，仅 `runGameTestServer` 加载。夹具使用每例新建的 FakePlayer、真实 ServerLevel、注册项和事件；FakePlayer 默认不会自动 tick，也不会受到伤害，测试涉及相应行为时须显式处理，不能把直接调用当作网络客户端证据。房间模板为 12×8×12，包含完整空气体积和石地板；结构方块坐标比模板原点低一格，玩家脚部使用相对 y=2。

合成用例另建真实生存 ServerPlayer，经 NeoForge `configureMockConnection` 配置 EmbeddedChannel，再正常加入和移出 PlayerList；因此难度变更会通知真实容器。它验证菜单与服务器逻辑，不代表真实客户端连线。对应实机脚本 `verify-peaceful-crafting.py` 已验证背包与工作台四种难度、旧产物清理和切回和平后的自动恢复；截图与数据在 `evidence/audit-r3-peaceful-crafting-v001-2026-10-18/`，约 5.2 MB。

GemRender 只加入客户端运行依赖。它内置的 KTX 模块依赖 LWJGL，不能随服务端 GameTest 加载。普通客户端、开发客户端、服务端各使用对应运行类路径；发行 JAR 不含 GameTest、开发接口或测试模板。构建阶段已检查该隔离。

伤害用例同样使用真实生存 ServerPlayer，在普通 tick 路径走完登录保护后调用 `hurt`，检查实际扣血、动作状态、Bash 目标释放和击退。旧实现复现 14 项误打断；自射箭用例还核对测试服务器禁用 PvP 的原版边界。24 项覆盖七种动作、环境来源、外来/无主箭及受环境伤害后的手动取消。实机脚本 `verify-damage-interruption.py` 的 14 组两端 tick 流全部通过，数据位于 `evidence/audit-r4-damage-v002-2026-10-18/`；v001 的测试准备错误原样保留。

生命周期用例覆盖主动退出、变形、死亡/维度事件、正常停服、孤立会话、完成与重复清理，以及冻结目标速度恢复、停服后兜底和搜索冷却清理。洞穴用例直接使用构建时复制到测试命名空间的正式模板，避免测试副本漂移。GameTest 的平坦生成器没有遗迹候选，不能据它宣称搜索票据通过；该项由 `verify-server-lifecycle.py` 在正常世界生成器中检查，开发端从原版 DistanceManager 独立计数票据。`audit-r5-lifecycle-v003-2026-10-18` 验证搜索、Bash、挑战目标退出与重进、另一存档切换均清理；v002 的窗口焦点失败记录保留。

动画测试读取实际打包 GLB 的 JSON chunk，检查共享契约与有效时长，并逐一移除 58 个要求片段，覆盖 idle 同时缺失和完全无动画的解析结果。实机 `verify-animation-fallback.py` 只临时修改 `build/resources`，对缺 `blade_2_left`、缺 idle/双手持物、零动画三种资源重载，检查左手连击、世界与背包渲染，再恢复完整模型。零动画期间复跑 17 组动作/伤害检查；该轮还修复了弓受击后右键未松开时偶发自动重新蓄力的问题。证据为 `audit-r6-animation-v002-2026-10-18` 及其 `-actions` 目录，四张截图均已查看；主目录约 7.65 MB。临时 GLB 已逐字节恢复并核对 SHA-256，原始模型、导出物和 Blender 文件未改动。静止 T 姿势是资源损坏时的兜底，不计为正常动画质量通过。

移动规则新增 90 项 JUnit，其中 35 项覆盖跳跃优先级、地面/空中次数、土狼窗口与各动作的接管规则；其余覆盖方向、速度边界、15 个固定协议编号的完整数据包往返和未知编号拒绝。模式协议版本升级为 2，存档格式不变。Windows 与 Linux 的最新日志为 `.external/audit-r7-final-build2.log`、`.external/audit-r7-final-linux.log`。

R7 实机复跑基础移动 5 组、边界 10 组、卡顿 6 组、战斗跳跃 3 组、空中动作 11 组和 Bash 8 组，共 43 组。证据为 `audit-r7-movement/edges/combat-jumps/air/bash-v001-2026-10-18` 和 `audit-r7-hitches-v002-2026-10-18`，合计约 6.6 MB；前两类旧脚本仍使用原有状态格式。0–500 ms 卡顿下最高点均为 102.504，服务端仅接受两次跳跃，无预测错配或拒绝。卡顿 v001 在准备阶段因空中切回生存后摔死而失败，现先落在测试平台再切生存；原记录保留。自动跳跃、连续高速位移与真实传送仍是下一轮独立边界。

R8 在修复前实测自动跳跃越过整格方块却未计数，Bash 后 600 ms 停顿造成约 10.24 格连续位移并误耗空中次数。修复后自动跳跃走同一输入与预测流程；真实传送入口发送 RELOCATE，清理两端状态与待确认预测。协议为 3，新增编号 15，九项 GameTest 覆盖短/长/相对传送、Bash、滑翔、排队输入、人形，以及 9/32 格连续移动。

R8 又发现滑翔三 tick 续期在 1500 ms 停顿中到期，使同段下降量由 4.8 增至 29.31 格。续期现为 40 tick，仍由本地即时检查退出条件。最终四种停顿下下降量为 4.876–4.996 格，五种退出条件与 17 组伤害检查通过。此结论仅覆盖实测的 0–1500 ms 单人停顿，不代表任意长卡顿或多人网络验收。

本轮 65 组实机检查均通过：`audit-r8-auto-after-v002`（6）、`audit-r8-motion-after-v003`（8）、`audit-r8-teleport-v001`（3）、`audit-r8-jump-hitches-v001`（6）、`audit-r8-combat-jumps-v001`（3）、`audit-r8-air-v001`（11）、`audit-r8-surfaces-v001`（6）、`audit-r8-glide-exits-v001`（5）、`audit-r8-damage-v001`（17），目录统一追加 `-2026-10-18`，合计约 9.4 MB。初次新 Mixin 目标签名错误导致启动失败，修正后的客户端完整运行；原失败日志与准备阶段的旧坠落状态记录保留。最终跨平台日志为 `.external/audit-r8-final-build.log`、`.external/audit-r8-final-linux.log`。

R9 的 24 项 JUnit 覆盖旋转单元格、连续坐标逆变换、通道包围盒、旧起点候选、完整搜索范围、入口距离剪枝和自然树避让范围。18 项 GameTest 包含 12 种类型/朝向组合、四种引道区块处理顺序、旧 NBT 与非生成环境的树木规则。每次引道读取或写入均由代理检查当前区块范围，正序与倒序输出逐方块相同。初次用 FakePlayer 测试返回位置时，其空实现的 `teleportTo` 导致 12 项夹具失败；改用实际 ServerPlayer 后通过，没有放宽正式返回判定。最终日志为 `.external/audit-r9-trees-win.log`、`.external/audit-r9-trees-linux.log`。

`python scripts/verify-worldgen-coverage.py <新运行名>` 会建立三个独立普通世界，以原版定位实现运行 225 次查询，并保存实际起点、旋转、入口、距离、耗时和源文件哈希。报告目录为 `evidence/<运行名>/`，世界及服务端日志在 `.external/worldgen-audit/<运行名>/`。测试期间不允许修改生成源码或资源。基线为 `audit-r9-density-before-v001-2026-10-18`，最终为 `audit-r9-density-after-v004-2026-10-18`。

| 每类 75 次定位 | 原间距 16 / 分隔 6 | 最终间距 6 / 分隔 3 | 最终距离中位数 |
| --- | --- | --- | --- |
| 林间灵泉 | 60/75，80.0% | 73/75，97.3% | 346 格 |
| 溪谷灵迹 | 44/75，58.7% | 71/75，94.7% | 522 格 |
| 洞穴祭坛 | 34/75，45.3% | 69/75，92.0% | 761 格 |

覆盖率指距离不超过 1500 格；按实际入口统计计数相同。这是固定种子样本的结果，不保证任意出生点。最远洞穴仍为约 2331 格。第一轮旋转实现因噪声采样过重，八次查询耗时 274.7 秒后中止；先筛群系、先查角点、去重高度采样并推迟完整水柱检查后，同八个结果坐标不变，耗时 24.3 秒。该中止报告和间距 8 未达标的完整报告均保留。

模板生成器输出统一 UTF-8/LF，`check-ruins.py` 同时检查 NBT、布局、群系与生成间距的重复生成哈希。第一次检查发现手工改过的间距文件被 Windows 换行重写；修正写入方式后重复生成一致，生成内容未因此改变。完整无命令生存流程仍须单独验收。

最后关闭客户端后再次执行 Windows `clean build runGameTestServer compileDevtoolsJava`，262 / 146 项仍全部通过，日志为 `.external/audit-r9-clean-final-win-v2.log`。此前客户端运行时的 `clean` 因游戏 JAR 被占用而失败，且已经删除部分松散资源；同期远距离搜索记录已明确作废。新的独立世界复测通过，见 `WORLD_CHECKPOINT.md`。发行 JAR 另检查内部布局位置与开发/测试源码隔离。

## R15：客户端设置与特效预算

2026-10-18 云端 Debian 13 / OpenJDK 21.0.12，新检出 `1c58dee` 后先跑基线：262 项 JUnit、146 项 GameTest 通过，`compileDevtoolsJava` 通过。R15 后 `scripts/check.sh --devtools` 结果为 312 / 154 全部通过；发行 JAR 不含开发或 GameTest 条目，SHA-256 `60df1c069d300f653ad81526b53263e21eec5c21df68487ca6218916f1a7dcd4`（云端构建，与 Windows 构建哈希不可直接比较）。Windows 未复跑。

- 配置：`orimod-client.toml` 新增 `configVersion`（缺失视为 v1，加载时升级到 v2 并记日志）、`visuals.quality`（FLUENT / BALANCED / DETAILED，默认均衡）、`visuals.advancedParticles`（默认开）、`feedback.screenShake`（0–100，默认 30，较轻）、`feedback.screenFlash`（0–100，默认 40）。JUnit 直接对真实 `ModConfigSpec` 执行 NeoForge 加载时的修正：v1 文件保留 `keyDefaultsApplied` / `spiritCamera` 并补默认值；越界强度、未知画质和视角名回到默认，其他值不变；新版本写入的更高 `configVersion` 不降级。运动模糊、强景深和动态灯光在模组中不存在，因此没有对应开关，也没有默认开启的效果。
- 粒子：原先五处服务端 `sendParticles`（刀命中、下砸落地、灵光箭尾迹、挑战检查点、路线标记）改为 `SpiritFx.emit`，向 32 格内玩家发送一个固定编号的 `orimod:spirit_fx` 载荷。客户端 `FxBudget` 把每类效果分为关键粒子（强制生成，原版“最少”粒子也保留）与装饰粒子（按画质 0.5 / 1.0 / 1.5 缩放，上限 48，仍走原版粒子等级）。均衡档总数与 R1–R13 实机证据使用的数量一致；关闭高级粒子只保留关键粒子。GameTest 用真实连接玩家检查每类效果只发一个载荷、坐标和来源正确、超出范围不发送，并确认刀命中不再发送原版粒子包；临时改回 `sendParticles` 时该用例失败，恢复后通过。
- 震动与闪光：`ScreenFeedback` 是纯逻辑时间线。精灵形态受击触发震动（100% 时峰值 2.5°，6 tick 二次衰减）和冷白闪光（100% 时 α 0.35，4 tick 线性衰减）；本人下砸落地只触发 0.6 倍震动。两项都只按用户百分比缩放，0 完全不触发，设置中途归零立即停止；形态切换、死亡或断线清空。原版受伤镜头倾斜保持原版设置，独立存在。
- 设置界面：模组列表的“配置”按钮使用 NeoForge 生成界面，分“画面”“震动与闪光”两节，画质显示流畅/均衡/精致；`configVersion` 与按键标记仍保存在文件中但不显示。

云端实机：Xvfb 1920×1080、Mesa llvmpipe（OpenGL 4.5），开发客户端约 18 FPS，光影关闭，无声卡。只说明设置链路与数值行为，不作画面、光影或性能验收。

| 检查 | 结果 | 记录 |
| --- | --- | --- |
| v1 配置启动 | NeoForge 修正文件并记录 “v1 -> v2”，保留 `keyDefaultsApplied = true`、`spiritCamera = "FIRST_PERSON"` | `evidence/settings-r15-manual-v001-2026-10-18/`（含前后文件与日志） |
| 界面修改 | 真实滑块与按钮修改震动、闪光、画质、高级粒子；“完成”后写回文件 | 同目录截图（英文初版、中文 v2/v3） |
| 受击 | 0% 震动 / 100% 闪光时只记录闪光峰值 0.35；100% / 0% 时只有震动峰值 2.5，且相机角度非零 | `settings-r15-script-v004-2026-10-18/results.json` |
| 刀命中粒子 | 均衡+高级：1 关键 + 3 装饰；流畅+高级：1+1；流畅+关闭：1+0 | 同上及手动记录 |
| 重启 | 设置修改后两次客户端进程重启值不变；第二次由脚本 `--expect` 断言 | `settings-r15-restart-v001-2026-10-18/` |

`scripts/verify-client-settings.py <运行名>` 在开发客户端中走真实设置界面完成上述受击与刀命中检查；重启后用 `--expect '{"visuals": "..."}'` 断言保持。脚本前三版因点击过早、刀连击恢复期和死亡动画中的旧僵尸被选中而失败，均为夹具问题，已修正，失败目录未保留到证据（运行在云端，未产生需要保存的游戏状态）。连续截图未捕捉到 4 tick 闪光帧，闪光与震动观感需本机复查；界面截图只覆盖 GUI 4。

## R16：遗迹世界边界

云端 `build runGameTestServer`：337 项 JUnit、164 项 GameTest 通过（R15 为 312 / 154）。新增 `RuinSiteRulesTest`（25）和 `RuinBoundaryGameTests`（10）。自然站点审计 `scripts/verify-ruin-sites.py`：修复前 117 个站点 115 个无问题，修复后 117 / 117；详见 [WORLD_CHECKPOINT](WORLD_CHECKPOINT.md#r16-自然站点审计world02-边界)。

## R17：无命令生存流程（服务端）

337 项 JUnit、167 项 GameTest 通过（新增 `SurvivalFlowGameTests` 3 项）。全新连接的生存玩家（未觉醒、空背包）只通过玩家输入会到达的服务端入口推进：工作台菜单点击制作核心、`useItem` 觉醒、面板领取刀、`LivingDropsEvent` 掉落后 `playerTouch` 拾取（和平改为工作台合成灵光后拾取）、`useItemOn` 使用石台、面板购买能力、挑战首通，最后 `saveWithoutId` → 新 `ServerPlayer.load` 比较觉醒、能力和进度。另测配方书：拿到铜锭解锁核心/指引/灵光配方，紫水晶解锁刀/弓配方。路径与灵光预算见 [SURVIVAL_FLOW](SURVIVAL_FLOW.md)。

## R18：正式效果与声音生命周期

396 项 JUnit、183 项 GameTest 通过（新增 `SpiritFxLifecycleTest` 21 项、载荷与预算参数化用例随新类型增加，`FxLifecycleGameTests` 8 项、`FxGameTests` 新类型路由 8 项）。

- 协议：`orimod:spirit_fx` 版本 2 增加阶段字段：BURST 单发、START 开始或心跳、STOP 结束一项、STOP_ALL 结束该实体全部持续效果；单发类型收到 START/STOP 时拒绝解码。新类型编号 5–12 只追加，R15 的 0–4 不变。
- 服务端：`SpiritFxLifecycle` 在移动 tick 之后按权威状态（滑翔模式、抓钩进行中）计算应有的持续效果，变化时发 START/STOP，每 20 tick 心跳；变身、死亡、换维度、退出发 STOP_ALL，停服清空。动作单发效果在 `signal` 处产生，抓钩每 tick 重发的信号只产生一次钩出效果。
- 客户端：`SpiritFxTracker`（纯逻辑，可单测）记录持续效果；实体消失、三次心跳缺失（60 tick）、离开或切换世界、断线都结束效果并停止循环音。
- 声音：`SpiritSoundCue` 为每一类列出原版声音或静音（尾迹、标记、牵引绳）；刀命中与下砸原有声音并入表中，音量音高不变。

云端实机（Xvfb，光影关闭，无声卡）：`scripts/verify-fx-lifecycle.py` 在开发世界中用真实跳跃键滑翔，确认客户端出现 GLIDE 持续效果和一个循环音，松键、变身、死亡、进入下界后分别在数 tick 内清空，最终 `active=[]`、`loops=0`，开始/结束次数 9/9。能力和平台用开发夹具。v001 因设置界面值在“完成”后才提交、脚本过早读取而失败（夹具问题，见 R19），证据保留在 `evidence/fx-r18-lifecycle-v001-2026-10-18/`；v002 通过。实际声音、粒子观感和动态灯光未验证。

## R19：精灵视角循环按钮

404 项 JUnit、183 项 GameTest 通过（新增 `SpiritCameraSyncTest` 6 项、`ClientConfigTest` 2 项）。`spiritCamera` 改为枚举配置，名称与旧文本值相同，三种旧值均原样加载，未知值回到默认第三人称背后。设置界面根页第一行显示为循环按钮，标签中英文。精灵形态中在菜单改视角会立即应用到当前镜头，按 F5 改视角会写回设置；同一 tick 两者都变时以菜单为准。

云端实机：`fx-r18-lifecycle-v002-2026-10-18` 在精灵形态连续三次“打开设置 → 点循环按钮 → 完成”，设置依次为第三人称背后、正面、第一人称并回到起点，每次实际镜头与设置一致；截图 `settings-camera-cycled.png`。v001 失败是脚本在“完成”之前读取设置（NeoForge 界面在完成时才提交），同时暴露了旧逻辑会用当前镜头覆盖菜单选择，已修复。多 GUI 缩放未截。

## R20：自带光影 Spirit Glow

417 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`，新增 `SpiritGlowSyncTest` 13 项：首次运行、自选包保留、开关应用、Iris 侧变化写回、恢复规则、状态文件读写/损坏回退、打包内容含两份许可证且无 `mellow.png`）。发行 JAR 含 `orimod_shaderpack/spirit_glow.zip`（`zipSpiritGlow` 生成，固定文件顺序与时间戳），不含开发或 GameTest 条目，SHA-256 `d1f14f20a343eb39bf38595bab9d9ff2b078b1c9ec3bfb1dc6a7351f725123ea`（云端构建）。

云端实机（Xvfb 1920×1080、llvmpipe，Iris 1.8.12）：
- `glow-r20-v002-2026-10-18`（`scripts/verify-spirit-glow.py`，Iris 原选 Mellow）：首次运行 `save_off`，Mellow 保留；开 → `Spirit Glow.zip` 管线建立且 `Iris.getStoredError` 为空，白天/夜晚截图；在 Iris 侧关光影 → 开关写为关；Iris 侧再开 → 开关写为开；关 → 恢复 Mellow。v001 在开启后发现记住的原包为空：设置文件保存后 NeoForge 的重载与同时写入的记录竞争，记录已移到独立的 `config/orimod-spirit-glow.properties`，v001 证据保留。
- 未选光影包的新配置：首次运行直接 `enable`；随后经真实设置界面（模组 → 配置 → 画面 → 开关 → 完成）关闭，因无原包而关闭光影；再开启回到 Spirit Glow。
- 自选包被保留时的一次性提示已触发（诊断 `keptPackHint=true`），截图时提示已消失，画面未截到。

## R21：Spirit 配色

420 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新增 `SpiritGlowPackTest` 3 项）。发行 JAR SHA-256 `609a761aa59d48acc1e16587a80e82aef49d24bfbf494a05b00a671252560eb3`（云端构建）。云端实机：固定场景截图（见渲染记录）；JAR 内新包替换旧包后管线正常；把记住的原光影包改为不存在的文件后关闭开关，结果为关闭光影、未选择缺失的包。

## R22：Spirit 风格

421 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；`SpiritGlowPackTest` 新增一项：每个 Spirit 风格选项都在其设置页且有中英文名称、风格与距离雾默认开、油画感默认关、体积光默认开）。云端实机：开发实例分别加载 R21 与 R22 包目录，同场景截图；Iris 日志无编译错误，油画感 + 色阶化变体同样建立管线。

## R23：画质档与光影性能档

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新增 `SpiritGlowProfileTest` 7 项：映射、首次写入、档位切换只换自己的值、保留玩家修改、首次运行前已有的值不接管、默认值收回、文件读写；`SpiritGlowPackTest` 新增 2 项：profile 行等于包默认加模组覆盖值、布尔选项都有普通 `#ifdef`）。发行 JAR SHA-256 `ccb541a2b8a86333500ca94ec79e8868c50887503e2f8063a692f4364b8ab765`（云端构建）。云端实机见渲染记录。

## R24：下界、末地、雨天

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新选项由现有 `SpiritGlowPackTest` 覆盖：在设置页、中英文名称、默认值在可选值内）。发行 JAR SHA-256 `778a254f54c7f53386721a719b2cc6e378e9c695724cf4d9e4ded5a448c26df1`（云端构建）。云端实机截图见渲染记录。

## R25：下界与末地画面

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`，仅光影改动）。云端实机截图与对照见渲染记录。

## R26：洞穴火把范围

430 项 JUnit、183 项 GameTest 通过（仅光影默认值改动，由 `SpiritGlowPackTest` 检查默认值在可选值内）。

## R27：100 次混合移动与效果往返

431 项 JUnit（新增一次性移动标签落地结束的规则测试）、183 项 GameTest 通过。云端客户端 `scripts/verify-movement-endurance.py move12-r27-v006`：100 次混合动作与取消全部通过（种子 20261019；跳 14、冲刺 13、滑翔 18、抓钩 25 次其中到达 6、Bash 6 次另取消 9、蹬墙 5、下砸 4）；修复前 `move12-r27-v005` 13 次标签残留失败保留。v001–v004 为场地搭建与低帧输入过期的夹具问题。`scripts/verify-fx-endurance.py perf02-r27-v001`：10 轮效果往返，活跃效果/循环音每轮回 0，粒子回 0，GC 后堆 622–625 MB，预热后斜率 −0.10 MB/轮。两脚本需开发客户端（`runDevtoolsClient`）与任一开发世界；移动脚本会在 (1000,200,1000) 建场地，效果脚本复用该场地。

发行 JAR（R27，`scripts/build-release.sh`）：两次 `clean jar`（不用 Gradle 构建缓存、各自重新编译）SHA-256 一致 `4e0651a269a897c252a06e97d685bf6c1b0f574550dd39882d66058eea977b16`；提交 `b0f397d` 与只改构建脚本的 `976041e` 构建出相同字节。JAR 含 `orimod_shaderpack/spirit_glow.zip`（含 LICENSE、LICENSE-APACHE、README）与 `THIRD_PARTY.md`，不含开发工具类。云端产物在 `/workspace/oriMod/release/`（不入库）。

## R28：原版存档副本与干净实例

无代码改动，自动检查沿用 R27（431 JUnit / 183 GameTest）。`save04-r28-server-v001`：原版 1.21.1 服务端建世界并做标记 → NeoForge 21.1.248 服务端 + 发行 JAR 打开副本，标记全部保留，三类遗迹可定位。`rel01-r28-clean-client-v001`：portablemc 5.0.6 新建干净客户端（Xvfb + llvmpipe），A 只装 OriMod → 缺依赖界面；B/C 加 GemRender → 进入原版副本；D 加 Sodium + Iris → Spirit Glow 自动启用。

## R29：验收计数、两视角交互、并发卡顿

431 JUnit / 183 GameTest 通过（开发接口改动）。`scripts/check-acceptance-counts.py` 校验验收表 79 行与摘要计数。`play04-r29-v001`：原版交互脚本在开发世界副本 `orimod_dev_play04_r29` 运行，4 组全部断言通过。`perf05-r29-v001`：空场地 vs 技能并发各 40 秒帧时间与服务端刻耗时，见验收表。

## R30：长时间会话

`perf03-r30-seg1/seg2`：见验收表 PERF03。脚本时长为活动时间（单调时钟），盒子挂起期间不计。

FX02 第 1 步：459 JUnit（`SpiritFxDefinitionTest` 28 项：每类效果的导出定义与源一致、首版与 R18 外观一致、分层分配、损坏定义拒绝）、183 GameTest；`check.sh` 末尾运行 `export-spirit-fx.py --check`。
