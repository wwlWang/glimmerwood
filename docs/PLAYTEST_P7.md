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
