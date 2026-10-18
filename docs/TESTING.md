# 回归测试

需要 JDK 21、Git LFS 中的两个锁定依赖，以及首次下载 Gradle / Minecraft 依赖的网络连接。

```sh
./gradlew test runGameTestServer --console=plain
```

Windows 可用 `gradlew.bat`，或使用本机 socket 兼容包装 `./scripts/gradle.ps1 test runGameTestServer --console=plain`。

## 测试边界

| 层次 | 当前内容 | 报告 |
| --- | --- | --- |
| JUnit | 存档编解码与旧字段、价格和前置、刀连击时间线、形态尺寸、三类布局契约，共 85 项 | `build/test-results/test/`、`build/reports/tests/test/` |
| GameTest | 形态与成长 30 项、Bash 22 项、刀目标与伤害 17 项、和平合成 16 项、伤害打断 24 项，共 109 项 | `build/test-results/gametest.xml` 和控制台 |
| 真实客户端 | 形态、模型、跳跃；自投射物与 Boss 拒绝、有效 Bash、村民保护与敌怪伤害 | 仓库外 `evidence/audit-r1-smoke-v002-2026-10-18/`、`evidence/audit-r2-target-safety-v001-2026-10-18/` |

2026-10-18，Windows JDK 21.0.4 与 WSL Debian / Linux JDK 21.0.8 均通过 85 项 JUnit 和 109 项 GameTest，失败数为零。Linux 使用独立源码目录、构建输出和 Gradle 缓存；Gradle 发行包复用了已下载副本。其直接访问插件仓库曾超时，使用本机现有代理后完成下载和执行。游戏认证服务的后台连接在 Linux 留有超时日志，未影响离线 GameTest；不据此宣称在线连接通过。

GameTest 位于独立 `src/gametest` 源码集，仅 `runGameTestServer` 加载。夹具使用每例新建的 FakePlayer、真实 ServerLevel、注册项和事件；FakePlayer 默认不会自动 tick，也不会受到伤害，测试涉及相应行为时须显式处理，不能把直接调用当作网络客户端证据。房间模板为 12×8×12，包含完整空气体积和石地板；结构方块坐标比模板原点低一格，玩家脚部使用相对 y=2。

合成用例另建真实生存 ServerPlayer，经 NeoForge `configureMockConnection` 配置 EmbeddedChannel，再正常加入和移出 PlayerList；因此难度变更会通知真实容器。它验证菜单与服务器逻辑，不代表真实客户端连线。对应实机脚本 `verify-peaceful-crafting.py` 已验证背包与工作台四种难度、旧产物清理和切回和平后的自动恢复；截图与数据在 `evidence/audit-r3-peaceful-crafting-v001-2026-10-18/`，约 5.2 MB。

GemRender 只加入客户端运行依赖。它内置的 KTX 模块依赖 LWJGL，不能随服务端 GameTest 加载。普通客户端、开发客户端、服务端各使用对应运行类路径；发行 JAR 不含 GameTest、开发接口或测试模板。构建阶段已检查该隔离。

伤害用例同样使用真实生存 ServerPlayer，在普通 tick 路径走完登录保护后调用 `hurt`，检查实际扣血、动作状态、Bash 目标释放和击退。旧实现复现 14 项误打断；自射箭用例还核对测试服务器禁用 PvP 的原版边界。24 项覆盖七种动作、环境来源、外来/无主箭及受环境伤害后的手动取消。实机脚本 `verify-damage-interruption.py` 的 14 组两端 tick 流全部通过，数据位于 `evidence/audit-r4-damage-v002-2026-10-18/`；v001 的测试准备错误原样保留。

这批用例尚未覆盖完整挑战生命周期。世界自然生成、自动跳跃、动画缺片段和完整生存流程仍须专项验证。
