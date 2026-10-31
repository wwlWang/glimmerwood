# Pass 6 回归 + 定向 + 乱测（2026-10-31，HEAD `7bfd6dd`）

自 Pass 5 起的新代码提交：`8ae6585`（P5 E：世界完整性、中立安全战斗、冷却持久化）、`f90b766`（P5 F：特征点权威规划、大灵树启动期选址、公开 Iris 依赖）、`e210ef1`（P5 G：精灵物理跟随原版效果、史莱姆、冰、激流）、`7bfd6dd`（P5 H：无冲突按键、统一术语、Ori 进度）。

Headless Xvfb :99 + llvmpipe，`./gradlew --no-daemon runDevtoolsClient`。脚本 `/workspace/oriMod/pt6/`，截图 `/workspace/oriMod/shots/playtest-p6-*`。**不改代码**，只记录。

标记：**[已修复] / [仍存在] / [有变化] / [新·复现] / [新·怀疑] / [通过] / [未测：原因]**；严重度 crash / blocker / major / minor / cosmetic。

## P6-0 新克隆编译 + 全量测试
- **新克隆编译（F3 / AUDIT B1）[已修复]**：`git clone file:///workspace/oriMod/repo /tmp/p6clone`（7bfd6dd，LFS 正常拉到真 jar），克隆里没有任何本地 Iris jar；`./gradlew --no-daemon build -x test` → `compileJava` 成功、出 `orimod-0.1.0-SNAPSHOT.jar`，`compileGametestJava` 也成功。Iris 现在走 `compileOnly 'maven.modrinth:iris:1.8.12+1.21.1-neoforge'`。（说明：本机 Gradle 缓存里已有 Modrinth 依赖，不是“完全离线首次下载”的验证。）
- 盒子上默认 PATH 没有 `java`（`JAVA_HOME` 未设），要手动指向 `~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`，环境问题，非模组问题 **[信息]**。
- 全量 `scripts/check.sh`：为了不和试玩客户端抢同一个 Gradle 项目目录，在同一提交的干净克隆 `/tmp/p6clone` 里跑（与主仓库 7bfd6dd 完全相同），结果见 P6-0b。

## P6-1a 新世界启动 / 大灵树 / locate 空地（`pt6/nw.py`、`pt6/site.py`）
新建 5 个世界（生存/普通，默认地形），计时“点创建 → 能操作”：

| 种子 | 进世界耗时 | 大灵树选址 | 结果 | 重开 |
|---|---|---|---|---|
| 5555 | **19.7 s** | 800 档 4.1 s 没找到 → 2500 档再 4.8 s | `(-856,71,408)`，离出生点 1052 格 | **4.5 s**，不重搜 |
| 116555 | 11.3 s | 3.5 s | `(616,79,-488)` | — |
| 117117 | 11.4 s | 2.1 s | `(-104,70,-488)`，530 格 | — |
| 随机 164836583 | 6.5 s | 0.7 s | `(-312,69,-152)` | 3.5 s |
| 随机 419668862 | 16.1 s | 800 档 5.9 s 没找到 → 2500 档 4.0 s | `(792,72,536)` | — |

- **新世界卡死 38–54 s / 重开 100 s（P5-H）[已修复]**：最慢 19.7 s（llvmpipe + 8 核共享，旁边还在跑全量测试），选址最长 10 s，`loadLevel` 合计最长 15.3 s，在 watchdog 60 s 之内；选好后重开 3–4.5 s 不再重搜。
- **种子 5555 没有大灵树（P5-H）[已修复]**：现在扩到 2500 格选到了，但离出生点 **1052 格**（`great_tree_state.distance`）。新玩家要走一千多格才能到大灵树 **[信息/平衡]**。
- 加载期间画面停在「生成世界」，最多约 10 s 没有进度变化（选址在 `loadLevel` 里同步跑）**[信息]**。
- **locate 指到空地（P4-0 / P5 / AUDIT D1）[有变化，大幅改善但仍存在，minor]**：每个世界从 8 个方向 `execute positioned … run locate`，传送过去等区块加载后用 `spirit_tree_probe` 数心、用 `/ori blight nearest` 找核：
  - 5555：灵树 **8/8**、腐化地 **6/6** 都真实生成。
  - 117117：灵树 **7/8**、腐化地 **5/6**。两个空起点，日志明确写了：
    - `spirit_tree registered start at (772,65,811) could not place its accepted plan: UNEXPECTED_RUIN`
    - `blighted_grounds registered start at (-1772,109,-124) could not place its accepted plan: UNEXPECTED_BLOCK minecraft:mossy_cobblestone at -1768,104,-134`（原版结构的苔石，可能是地牢/废墟）
  - 结论：结构起点仍会先登记、后放置失败，`locate` 照样指过去，只是从 P4 的“很多空点”降到约 1/11（22 个里 2 个）**[仍存在·降级]**。
