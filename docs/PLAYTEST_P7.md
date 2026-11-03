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
