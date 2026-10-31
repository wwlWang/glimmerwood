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

## P6-1b 客户端生命周期 / 冷却（`pt6/lc_lib.py`、`lc3.py`、`lc4.py`）
- **换存档后水中冲刺失效（P5-F / AUDIT A2）[已修复]**：同一客户端进程先进老存档 `pt116p3`（gametime 149128），水池冲刺 8 tick 位移 3.95 / 3.75 / 3.75；退回标题进 `p6_r1`（gametime 2262），同样按法 **3.75 / 3.95 / 3.75**，正常。
  - （我的第一版脚本在新世界里没等区块加载就 `fill` 水池、并在觉醒前切形态，得到 0.0，是脚本问题，已重跑排除。）
- **死亡 / 换维度后能量条一直红（P4-3 / P5-F / AUDIT A1）[已修复]**：活 15 秒后手持灵矛 0 能量右键（能量条变红）→ `/kill` → 重生 → 补满，5 秒后整条是蓝的；再拒绝一次 → 传送到下界 → 补满 5 秒后也是蓝的 `playtest-p6-lc-energy-sheet.png`（上：拒绝瞬间红；中：重生 5 秒；下：下界 5 秒）。
- **存档退出再进冷却清零、灵泉白嫖（AUDIT A3 / E5）[已修复]**：手放 `glades_well`，第 1 次右键：能量 6→100、生命 16→20 并打开灵泉界面；第 2 次被冷却挡住（只有自然回能 9.8）；**退出重进后第 3 次仍被挡住**；`/kill` 重生后第 4 次也被挡住。冷却按主世界 gameTime 计算，`/time set 0` 不影响。
- **[新·复现，minor] 手持灵矛时右键灵泉没反应**：同一口井，主手拿灵矛时右键 6 次都没打开灵泉界面也没补满（`lc3.py`，屏幕一直是游戏画面），空手右键马上打开。灵矛“按住举起”的使用动作抢在方块交互前面。玩家拿着矛走到灵泉/圣所前右键会以为井坏了。其他“右键方块”（箱子、门、商人）是否也被抢见 P6-3。

## P6-1c 世界完整性（水 / 岩浆 / 活塞 / TNT / 苦力怕 / 凋灵头，`pt6/grief6.py`、`ctl.py`）
新世界 `p6_r1`，38 种模组方块各一排，每排一种破坏方式（`mobGriefing true`）：

| 方式 | 被毁 / 38 | 被毁的方块 |
|---|---|---|
| 上方放水 | 7 | spirit_vine、ginso_vine、ginso_fungus、gloom_thorns（见下：是头顶支撑被水替换后掉落，不是被水冲）+ 3 种遗迹圣所（单放会自己消失，见下） |
| 旁边+上方岩浆 10 s | 7 | 同上 |
| 活塞推 | 10 | spirit_log、spirit_leaves、spirit_vine、withered_spirit_log、ginso_leaves、ginso_canopy、ginso_fungus（+ 圣所） |
| TNT 贴脸 | 10 | 同活塞那 7 种（+ 圣所） |
| 充能苦力怕 / 蓝色凋灵头 | 0（+ 圣所） | — |

- **一桶水冲掉水之心 / 生命格台座 / 灵灯 / Bash 锚 / 抓钩花 / 上升气流 / 光平台（P5-A）[已修复]**：这些方块对水、岩浆都不动了；生存模式拿水桶直接对着它们倒，也倒不进去。
- **活塞 / TNT 毁掉金索树、大灵树木头、生命格台座、锚点（P3-9 / P5-A）[已修复]**：ginso_wood、great_spirit_wood、great_spirit_root、ginso_landing、ginso_root_lip、两种细胞台座、锚点、抓钩花、上升气流、光平台都推不动、炸不掉。
- **[仍存在·按设计]** 生灵之树的 `spirit_log / spirit_leaves`、`withered_spirit_log`、金索树叶 `ginso_leaves / ginso_canopy`、`ginso_fungus` 仍能被活塞推走、被 TNT 炸掉（修复计划里写明“共享树叶/自然装饰不动”）。`ginso_fungus` 如果是金索攀爬路线上的落脚点，就仍可能被炸断路线 **[信息，未核实是否路线块]**。
- 对照（`ctl.py`）：藤蔓/真菌/荆棘在头顶支撑保留时，旁边放水 60 tick 不受影响，所以上表里的“被水冲”是支撑没了，不算问题；**3 种遗迹圣所（forest/creek/cave_shrine）用 `setblock` 单独放 60 tick 后自己变成空气**（不属于组装好的遗迹就自删），所以本表对圣所的结论无效 **[未测：要在真实遗迹里测]**。
- 生存模式手拿水桶右键灵灯 `spirit_light_vessel`：灵灯直接变成空气，是灵灯“右键爆发”的设计交互 **[信息]**。
- 生存可挖（代码确认，devtools 的按住攻击不触发持续挖掘）：水之心、台座、祭坛、圣所、灵泉、金索根唇硬度 -1 不可挖；锚点 / 抓钩花 / 上升气流 1.5、光平台 / 金索落脚 / 三种地标木 2，可挖并有掉落 **[与修复计划一致]**。
