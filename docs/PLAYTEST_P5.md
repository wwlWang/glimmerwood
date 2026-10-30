# Pass 5 广度试玩（2026-10-30，HEAD `cf989da`）

自 `9c8770f` 起没有新代码提交，只有 Pass 4 文档（`c063511..cf989da`）。代码与 Pass 4 相同。

目标：广度优先（人设 / 群系 / 环境状态 / 怪物战斗 / 游戏规则与设置 / 存档生命周期 / UI / 性能 / 物品经济），每块 5–10 分钟，发现就记，不深挖。Headless Xvfb :99 + llvmpipe，`./gradlew --no-daemon runDevtoolsClient`。截图 `/workspace/oriMod/shots/playtest-p5-*`，脚本 `/workspace/oriMod/pt5/`。**不改代码**。

标记：**[新·复现] / [新·怀疑] / [仍存在] / [通过] / [未测：原因]**；严重度 crash / blocker / major / minor / cosmetic。

## P5-0 静态检查 + 启动
- `en_us.json` / `zh_cn.json` 各 574 键，互相齐全，没有中英相同未翻译的值；Java 里所有字面量 `Component.translatable("…")` 键都在 lang 中（动态拼接前缀 30 个，靠游戏内实测覆盖）**[通过]**。
- 启动后客户端停在原版「无障碍引导」首启界面（`AccessibilityOnboardingScreen`，`playtest-p5-onboarding.png`）——options.txt 里 `onboardAccessibility` 被重置过（可能是之前某轮测试清过 options），非模组问题 **[信息]**。
