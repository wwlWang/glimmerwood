# 回归测试

需要 JDK 21、Git LFS 中的两个锁定依赖，以及首次下载 Gradle / Minecraft 依赖的网络连接。

```sh
./gradlew test runGameTestServer --console=plain
```

Windows 可用 `gradlew.bat`，或使用本机 socket 兼容包装 `./scripts/gradle.ps1 test runGameTestServer --console=plain`。

## 测试边界

| 层次 | 当前内容 | 报告 |
| --- | --- | --- |
| JUnit | 存档编解码与旧字段、价格和前置、刀连击时间线、形态尺寸、三类布局契约 85 项，GLB 动画契约与缺片段回退 61 项、移动规则和协议 92 项、遗迹旋转与指引 24 项，共 262 项 | `build/test-results/test/`、`build/reports/tests/test/` |
| GameTest | 形态与成长 30 项、Bash 22 项、刀目标与伤害 17 项、和平合成 16 项、伤害打断 24 项、会话生命周期 10 项、真实传送入口 9 项、遗迹旋转与生成顺序 18 项，共 146 项 | `build/test-results/gametest.xml` 和控制台 |
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
