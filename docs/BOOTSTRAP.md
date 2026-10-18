# 工程初始化记录

日期：2026-10-14。本文保留初始化时的环境与结果，不描述当前完成状态；当前依赖、测试与验收范围见 [README](../README.md)、[TESTING](TESTING.md) 和 [统一验收状态](ACCEPTANCE_STATUS.md)。

## 来源与固定版本

基于 [NeoForge 官方 1.21.1 ModDevGradle 模板](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)，取自提交 `3bca8f1e0756c001e4b71f266c328d1568d29363`。保留其 Gradle Wrapper 与模板 MIT 许可，按本项目重写构建配置和入口，移除演示物品、方块、配置和日志。没有继承模板仓库历史或远程地址。

模板当时的 NeoForge 默认值为 21.1.259；本项目按已确认的兼容性计划固定 21.1.248。Minecraft 为 1.21.1，ModDevGradle 为 2.0.148，Gradle 为 9.2.1，Java 为 21。渲染、动画、特效依赖尚未接入。

Gradle 发行包 SHA-256：`72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`，来自 Gradle 官方发行校验文件并写入 Wrapper 配置。

## 本机验证

- Git 2.46.0.windows.1；Microsoft OpenJDK 21.0.4。
- 首次构建在 Gradle 与守护进程连接时失败，栈底为 `UnixDomainSockets.connect` / `Invalid argument: connect`。
- 为该进程设置 `jdk.net.unixdomain.tmpdir` 到项目 `.gradle/socket-tmp` 后继续构建成功，耗时 3 分 22 秒。
- `compileJava`、资源生成和 JAR 打包通过；当前没有测试源，`test NO-SOURCE` 不代表玩法验收。
- 初始化 JAR 的 SHA-256 为 `404ed3a3da105bc071a776bc57294b2bd09a89d4dcc8b779f9902bc5f8e86604`。
- `scripts/gradle.ps1 runClient --console=plain` 启动真实客户端；模组列表包含 `OriMod 0.1.0-SNAPSHOT`，公共与客户端入口均记录 ready，资源重载、声音引擎和纹理图集初始化完成。证据保存于 `evidence/bootstrap-2026-10-14/client-latest.log`。
- 实际图形设备为 NVIDIA GeForce RTX 4070 Laptop GPU，驱动 610.47，OpenGL 4.6。当前没有安装计划中的光影、模型渲染器或特效依赖，因此这不是性能基准结果。
- 未操控客户端进入世界、未采集游戏画面；ENV01 的进入世界要求尚未验收。日志有原版命令歧义、缺少山羊号角声音、emissive shader 的 Sampler2 等警告，尚无本模组异常。
- IDEA 诊断调用将两个入口文件标记为不具备分析条件（skipped），不能记为 IDE 检查通过；编译结论来自实际 Gradle 构建。

## MCP 与资产

Blockbench 5.2.1 / MCP 插件 1.10.0 可读取能力；Blender 5.1.2 可通过 MCP 执行 Python、导入 GLB 并返回多视角预览。Blender 插件未提供新版协议状态接口，实际基础调用可用，当前未更新或重启用户软件。

所有操作使用 MCP、文件工具和命令行，未使用 Computer Use。模型预览在新增的 `OriMod_Asset_Review_*` 场景中进行，原始 `Scene` 和下载文件保留。

模型来源、面数、骨架、材质和选择建议见 [模型检查](../models/README.md)，原始文件哈希见 `models/asset-inventory.json`。
