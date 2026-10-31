# Pass 6 回归 + 定向 + 乱测（2026-10-31，HEAD `7bfd6dd`）

自 Pass 5 起的新代码提交：`8ae6585`（P5 E：世界完整性、中立安全战斗、冷却持久化）、`f90b766`（P5 F：特征点权威规划、大灵树启动期选址、公开 Iris 依赖）、`e210ef1`（P5 G：精灵物理跟随原版效果、史莱姆、冰、激流）、`7bfd6dd`（P5 H：无冲突按键、统一术语、Ori 进度）。

Headless Xvfb :99 + llvmpipe，`./gradlew --no-daemon runDevtoolsClient`。脚本 `/workspace/oriMod/pt6/`，截图 `/workspace/oriMod/shots/playtest-p6-*`。**不改代码**，只记录。

标记：**[已修复] / [仍存在] / [有变化] / [新·复现] / [新·怀疑] / [通过] / [未测：原因]**；严重度 crash / blocker / major / minor / cosmetic。

## P6-0 新克隆编译 + 全量测试
- **新克隆编译（F3 / AUDIT B1）[已修复]**：`git clone file:///workspace/oriMod/repo /tmp/p6clone`（7bfd6dd，LFS 正常拉到真 jar），克隆里没有任何本地 Iris jar；`./gradlew --no-daemon build -x test` → `compileJava` 成功、出 `orimod-0.1.0-SNAPSHOT.jar`，`compileGametestJava` 也成功。Iris 现在走 `compileOnly 'maven.modrinth:iris:1.8.12+1.21.1-neoforge'`。（说明：本机 Gradle 缓存里已有 Modrinth 依赖，不是“完全离线首次下载”的验证。）
- 盒子上默认 PATH 没有 `java`（`JAVA_HOME` 未设），要手动指向 `~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`，环境问题，非模组问题 **[信息]**。
