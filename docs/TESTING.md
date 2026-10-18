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
| GameTest | 变身碰撞箱、低顶拒绝恢复人形、购买去重、容量上限、武器领取、奖励幂等、能量扣除，共 30 项 | `build/test-results/gametest.xml` 和控制台 |
| 真实客户端 | 形态同步、第三人称模型、Mellow 开启、按键跳跃 | 仓库外 `evidence/audit-r1-smoke-v002-2026-10-18/results.json` |

2026-10-18，Windows JDK 21.0.4 与 WSL Debian / Linux JDK 21.0.8 均通过 85 项 JUnit 和 30 项 GameTest，失败数为零。Linux 使用独立源码目录、构建输出和 Gradle 缓存；Gradle 发行包复用了已下载副本。其直接访问插件仓库曾超时，使用本机现有代理后完成下载和执行。游戏认证服务的后台连接在 Linux 留有超时日志，未影响离线 GameTest；不据此宣称在线连接通过。

GameTest 位于独立 `src/gametest` 源码集，仅 `runGameTestServer` 加载。夹具使用每例新建的 FakePlayer、真实 ServerLevel、注册项和事件；FakePlayer 默认不会自动 tick，也不会受到伤害，测试涉及相应行为时须显式处理，不能把直接调用当作网络客户端证据。房间模板为 12×8×12，包含完整空气体积和石地板；结构方块坐标比模板原点低一格，玩家脚部使用相对 y=2。

GemRender 只加入客户端运行依赖。它内置的 KTX 模块依赖 LWJGL，不能随服务端 GameTest 加载。普通客户端、开发客户端、服务端各使用对应运行类路径；发行 JAR 不含 GameTest、开发接口或测试模板。构建阶段已检查该隔离。

这批用例尚未覆盖完整挑战生命周期、Bash 资格、刀友方伤害和环境伤害打断；这些将随审查修复补入。世界自然生成、自动跳跃、动画缺片段和完整生存流程仍须专项验证。
