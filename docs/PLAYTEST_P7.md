# 试玩 / 审查 第七轮（P7）

日期：2026-11-03（Asia/Shanghai）。HEAD `ae7dd71`（origin/main，远端已改名 `wwlWang/glimmerwood`，历史已重写）。
方式：**只测不修**。新克隆构建 + 公开发布审查 + 实机回归 + 混沌测试。脚本 `/workspace/oriMod/pt7/`，截图 `/workspace/oriMod/shots/playtest-p7-*`（不入库）。
标记：**[新]** 新问题，**[回归]** 曾修好又坏，**[未修]** P6 遗留仍在，**[已修]** 确认修复，**[怀疑]** 未完全复现。

## P7-1 新克隆构建与发行 jar

- `git clone https://github.com/wwlWang/glimmerwood.git`（匿名、LFS 自动拉取真 jar），`./gradlew --no-daemon build`：**BUILD SUCCESSFUL，1 分 01 秒**（Gradle 用户缓存已热）。只有 2 条 deprecation note。
- 产物 `build/libs/orimod-0.0.1-alpha.jar` 24.7 MB / 1418 项；`META-INF/jarjar/gemrender-0.1.17.jar` 已内嵌，`LICENSE`、`THIRD_PARTY.md` 已打入。`neoforge.mods.toml`：license `GPL-3.0-only`，version `0.0.1-alpha`，issueTrackerURL / displayURL 指向 glimmerwood。
- **[新] 模组列表无图标**：toml 没有 `logoFile`，jar 里也没有 logo；`docs/modrinth/icon.png`（512²）已有却没进 jar。模组列表里 OriMod 是空白方块。
- **[新] 名称三套并存**：仓库/Release/Modrinth 叫 Glimmerwood，游戏内 displayName `OriMod`、modId `orimod`、jar 名 `orimod-0.0.1-alpha.jar`、创造标签「OriMod 精灵」、设置标题「精灵设置 / Ori Spirit Settings」、按键分类「Ori 精灵能力 / Ori Spirit Abilities」、进度根「Ori · 精灵之路」。README 和 Modrinth 页都要专门解释“游戏里还叫 OriMod”。公开版改名后，玩家在模组列表里找不到 Glimmerwood。
- **[新][怀疑] GemRender 的 KTX 原生库可能只加载一份**：GemRender 内层 `metadata.json` 用同一个 identifier `org.lwjgl:lwjgl-ktx` 登记了 5 个 jar（Java 包 + linux/windows/macos/macos-arm64 natives）。NeoForge JarJar 按 identifier 去重，每个 identifier 只选一个 jar，所以多半只有其中一个被加载，Windows/macOS 的 natives 可能被丢掉。只有 GemRender 实际用到 KTX 纹理时才会出问题；本项目只在 Linux 实测过，属于未覆盖风险。
- **[新] Flywheel 被钉死在 `[1.0.6]`**（OriMod toml 和 GemRender toml 都是精确版本）。目前 Create 6.0.10 内嵌的也是 1.0.6，所以能共存；但只要 Create 或其他模组升到 Flywheel 1.0.7，就会直接因依赖冲突无法启动。
- **[新] 发行 jar 偏大**：24.7 MB。其中 `ori.glb` 6.3 MB，四只第一人称爪子 glb 各约 0.93 MB（合计 3.7 MB），5 个怪物 glb 各约 1 MB。
- MANIFEST.MF 只有 25 字节，没有 Implementation-Version / Title（小问题）。

## P7-2 公开发布审查（README / 许可 / CI / Modrinth / 历史）

- 仓库已公开（GitHub API：`private:false`，license GPL-3.0，topics 含 `ori`）。Release `v0.0.1-alpha`（预发布）挂在 `0145aae`，资产：`orimod-0.0.1-alpha-0145aaee08.jar`、`.sha256`、`SHA256SUMS`、`LICENSE`、`THIRD_PARTY.md`。CI 最近 8 次都成功。
- Modrinth：Release 日志写着 `Modrinth version 0.0.1-alpha uploaded: nYkKKLoR`，但公开 API 查 `v2/v3 version/nYkKKLoR`、`project/SyO5C83J`、`project/glimmerwood` 都是 404，搜索 glimmerwood 也是 0 条。项目应该还是草稿或待审，这一轮看不到公开页面，没法核对。
- **[新] Release 落后 HEAD，而且无法再发同版本号**：Release 用的是 `0145aae`，之后的 `ae7dd71`（标题改成 Glimmerwood）只改了标题和文档，jar 内容等价。release.yml 遇到已有 `v$version` tag 会直接退出，所以 HEAD 上的任何修复都必须先改版本号才能发版。属于设计如此，记录备查。
- **[新] CI 实际只跑 JUnit，而且常常整条命中缓存**：最近一次 CI 日志 `compileJava FROM-CACHE / test FROM-CACHE`，总共 15 s，GameTest 不在 CI 里（ci.yml 注释承认这一点）。README 写“推送到 main 后自动构建并运行 JUnit”是准确的，但 1690 个 GameTest 没有任何自动守门。
- **[新] `actions/setup-java@v5` 不是最新大版本**（已有 v6.1.0）。提交 `6ad14a4` 的说明是“更新到当前大版本”，checkout/cache/upload-artifact/setup-gradle 都升了，唯独漏了 setup-java。
- **[新] README 有两段“客户端设置”**：第一段（安装第 4 步之后）列了 Spirit Glow；第二段（“开发与检查”里）没有 Spirit Glow、精灵光晕、原版 HUD、萤的提示、精灵视角，和实际设置项对不上。
- **[新] README 命令表漏了 `beetle`**：`/ori spawn` 实际支持 `beetle|spitter|hawk|heart|matriarch|target`。另外 `tide/tree/blight/great_tree/wisp/hints/companion/restoration/ruin secrets` 等子命令完全没写。
- **[新] README / Modrinth 的遗迹名和游戏内对不上**：文档写“森林泉、溪谷遗迹、洞穴祭台”，游戏里是「林间灵泉 / 溪谷灵迹 / 洞穴祭坛」；“祭台”和“祭坛”混用。
- **[新][许可] “不用于商业用途”与 GPL 冲突**：THIRD_PARTY 开头写“按 GPL-3.0-only 发布，不用于商业用途”，README 英文写 “non-commercial”。GPL 第 7 条不允许附加限制，这句话没有法律效力，还容易让人误以为附加了 NC 条款。建议改成“作者不以营利为目的”之类的表述。
- **[新][商标] 公开文本和游戏内大量使用原作专有名词**：Ori（按键分类「Ori 精灵能力」、进度根「Ori · 精灵之路」、核心说明「查看 Ori 主线指引」、设置标题 “Ori Spirit Settings”）、Spirit Light、Spirit Well、Bash、Ginso（金索树 / 金索逃亡）、Kuro（库罗之羽）、Wisp(s)、Glades 等，GitHub topics 也有 `ori`。项目已加非官方声明，但公开发布后仍有被 DMCA 或下架的风险；“Glimmerwood”改名只改了外壳。
- **[新][许可] 角色模型来源风险**：主角模型来自 Sketchfab 的 “Ori Rigged”（上传者标注 CC BY 4.0）。它本身是受版权保护角色的同人建模，上传者的 CC BY 不能覆盖角色版权。THIRD_PARTY 已经承认这一点，但 README 写“本项目不包含 Moon Studios 的原始…模型”，读起来像已经完全规避了风险。
- 历史扫描：298 个提交作者都是 `wwlwwl <wwlwwl@proton.me>`；没有 token、私钥或 `D:/`、`C:\Users` 路径；没有大于 2 MB 的普通 blob。
  - **[新] 提交信息仍带 AI 会话链接**：44 个提交消息里有 `Claude-Session: https://claude.ai/code/session_…`（2 个不同链接）和 `Co-Authored-By: Claude …`，已经公开。
  - **[新] 文档里的云端路径**：`docs/WORK_STATUS.md`（17 处）、PLAYTEST_*、`scripts/feel_anim.py` 里有 `/workspace/oriMod/...` 绝对路径。不算泄密，但对公开读者是噪音。
- `.gitattributes` 把所有 `*.png` 放进 LFS（包括模组贴图和 `docs/modrinth/icon.png`）。README 已经提醒不要用 GitHub 源码 ZIP，可以接受；但 GitHub 网页上无法直接预览这些贴图。
- `scripts/check.sh` 用 `command -v java` 判断 JDK：只设置 JAVA_HOME、PATH 里没有 java 时会直接报 “JDK 21 required”，而 `./gradlew` 在同样环境下能正常构建（小问题）。
- Modrinth 描述用 `<h2 id="zh">` 做锚点跳转。Modrinth 的 Markdown 清洗器可能去掉 `id` 属性，顶部的“中文 · English”跳转可能失效。**[怀疑]**，页面未公开，无法验证。

## P7-3 P6 遗留回归（第一批）

客户端：`ae7dd71` 的 runDevtoolsClient，Xvfb + llvmpipe（12–60 FPS）。新世界 `orimod_dev_p7_main`（seed 7007）。**devtools 建的新世界默认是和平难度**：敌对生物刚召唤就被清掉，第一次测中立生物时因此出了假结果，改成 `/difficulty hard` 重测。

- **能量 0 时手持武器右键被吞 [已修]**：灵光刃 / 弓 / 锤 / 矛 × 能量 0 / 200，共 8 组，逐一右键 20 种方块：箱子、工作台、熔炉、铁砧、附魔台、木桶、织布机、锻造台、砂轮、林栖灵泉、制图台、切石机、酿造台都能打开界面；门、拉杆、栅栏门、按钮、活板门、中继器都能切换（`pt7/a2.py`–`a4.py`）。村民、流浪商人、林栖商人在 0 能量时都能打开交易界面；马在 0 能量时可以骑乘（`a5.py`、`a6.py`）。
- **伙伴灵焰 / 中立生物 [已修]**（`b.py`、`b2.py`，困难，不开无敌）：
  - 戴金头盔时，2 只猪灵 + 猪灵蛮兵 200 tick 内不掉血。
  - 不戴金时，猪灵 16→4/6，蛮兵 50→42（被灵焰打，正常）。
  - 白天蜘蛛和洞穴蜘蛛不掉血；夜晚蜘蛛 16→4、洞穴蜘蛛 12→2。
  - 狼、蜜蜂、羊驼、北极熊不掉血。
- **伙伴灵焰仍会主动打坚守者 [未修]**：开着 `/ori god` 时坚守者不会把玩家当目标，200 tick 内仍从 498 掉到 456。不开无敌时，召唤出的坚守者约 80 tick 后连同实体一起消失，灵焰开关结果都一样，这一项没测清楚。
- **[新] 猪灵蛮兵也被归为“有条件中立”**：标签 `conditionally_neutral` 包含 `piglin_brute`。原版的蛮兵不管戴不戴金都会主动攻击，现在灵焰要等它先锁定玩家才会还手，比原版保守（小问题）。
- **[新] 林栖商人杀不掉，`/kill` 却报告成功**：`GladesMerchant.hurt()` 永远返回 false，连 `/kill` 用的通用击杀伤害也拦住了。`/kill @e[type=orimod:glades_merchant]` 提示“杀死了 8 个实体”，8 个商人仍然站着；`kill @e[type=!player]` 同样清不掉。只有传送到虚空才能去掉。商人卡进墙里、岩浆里，或者被刷怪蛋 / 召唤命令刷多了，玩家没办法清理。
- **金索逃亡中骑乘 [已修]**：逃亡开始后，用 `/ride` 骑矿车、马、猪都会被拒（“Dev无法骑乘…”），之后正常判 `SUBMERGED` 失败（`c.py`，世界 pt117）。船没有单独测：我的 summon 写错了实体名，但拦截走的是通用的 EntityMountEvent。
- **腐光之心逃亡中存档退出 [已修]**（`d.py`，世界 pt116p3）：杀死心脏后逃亡 active（elapsed 141）→ 退出重进 → active，elapsed 173（离线时间不计），半径继续扩大 → 跑出 12 格并露天 → 胜利，灵光 1047→1147，rewards 记下 `escape/gloom_heart/...` 首通。
- **`/setblock` 放的腐化之核仍不扩散 [未修]**（K 的提交说明声称“能注册并扩散”）：
  - 平原天然草方块上 `/setblock orimod:gloom_core`，`/ori` 状态显示 region 已注册（`total 1, state active`）。
  - `tick sprint` 18000 tick 后，41×19×41 范围内 `blighted_soil` 和 `gloom_thorns` 都是 0，`lastWrites 0`，`elapsed 0`。
  - 在草方块平台、冰面上放置也一样。
  - 截图 `playtest-p7-plains-setblock-core2.png`。
- **[新] 用 `/setblock` 把已注册的核换成别的方块，地带会自己“净化完成”**：状态变成 `purifying` → 600 tick 后 `completed`，不需要打核。玩家挖不动核（硬度 -1，防爆 3600000），所以只有命令能触发；地图作者删核时要知道会走一遍完成流程。
