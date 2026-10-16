# 当前工作状态

2026-10-16：按用户“继续口牙”恢复工作。渲染小样、玩家变身基础、七组移动能力与成长交互已实现，并完成局部实机验证。灵光刃三连与灵弓蓄力规则也已通过局部实机检查；探索和正式美术尚未完成，不能视为首版交付。

## 已验证

- 开发源码、包、资源 ID 和启动任务已按职责重命名；使用 `devtools`、`DevelopmentBridge`、`CharacterPreview`、`SpiritMotePreview` 和 `FrameRecorder`。阶段编号只用于计划与验收记录。
- 主工程构建、开发源码编译及资产契约检查通过。发行 JAR 不包含开发工具。
- GemRender、Flywheel、Iris、Colorwheel、Mellow 与 Photon 已在同一个开发客户端启动，角色蒙皮和五段技术动画可以显示。
- 角色杂色已定位到无贴图材质采样默认方块图集。导出脚本增加独立 PNG 及 GLB 图片的 `extras.resourceLocation`；光影开、关两条路径的实机截图均显示正确的纯色材质。
- 最新资产源文件：`art/source/deformation-sample/ori-deformation-v008.blend`。重复导出与上一版 GLB、PNG 哈希一致，下载原件未改动。正式玩家临时使用同一灰模，通过 `scripts/art/export_player_model.py` 导出到主资源目录；正式动作和材质仍待制作。
- Mellow 开启动态阴影后，角色几何阴影正常；光影开关后 Photon 缓存失效处理已实测，角色与效果恢复。此项目前验证的是开发接口，正式设置和外部 Iris 切换生命周期仍待接入。
- 循环效果在连续五次资源重载后恢复，窗口从 1280×720 切换到 1920×1080 后正常，没有重复实例。证据见 `evidence/rendering-2026-10-15/` 和 `docs/RENDERING_CHECKPOINT.md`。
- 1080p、12 区块、关闭垂直同步的静态角色与粒子场景采样约 60 秒：平均 126.6 FPS，帧时间 P95 10.86 ms、P99 16.32 ms。该结果不代表完整玩法性能验收。
- 原速视频 `evidence/rendering-2026-10-15/animation-stream.mp4` 为 1920×1080、20 秒、600 输出帧；实际采集 562 帧，38 帧沿用前帧。已查看抽帧，尚未完成正式动作质量验收。
- 精灵核心、觉醒状态、形态网络请求、碰撞尺寸与第三人称灰模已经接入主模组。客户端与集成服务器均确认精灵站立高度 1.2 格、视点 1.04 格；恢复人形后为 1.8 格、1.62 格。
- 未觉醒时切换被拒绝；核心使用可觉醒并变身；低矮顶棚下拒绝恢复人形；开阔处连续十次切换后生命和手中物品保持不变；存档退出重进后觉醒、形态与尺寸保持一致。原始状态证据在 `evidence/form-2026-10-15/`。

## 尚未完成的检查与实现

- 二段跳、滑墙、蹬墙、冲刺、Bash、滑翔、牵引与下砸已实现。次数、薄墙、输入缓冲、离地宽限、复位限制、菜单输入、空中切形态、目标取消、半砖与楼梯下砸、入水取消和部分坠落规则已实测，细节和剩余矩阵见 `docs/MOVEMENT_CHECKPOINT.md`。正式解锁入口已接入 G 面板并验证购买、前置与防重复扣费。
- 本轮形态测试通过开发接口给予核心；正式合成与负面状态保留尚未验收。一次正常死亡重生已确认觉醒、冲刺解锁保留，形态恢复人形；完整死亡矩阵仍待执行。
- 玩家第一人称手部、持物、特殊姿态、无光影阴影、正式材质与动画尚未完成。当前灰模只用于技术验证。
- 干净检出重建、完整依赖许可清单、变形关节近景和正式性能路线仍待完成。完整首版范围继续以 `PROJECT_PLAN.md` 与 `ACCEPTANCE.md` 为准。

## 恢复顺序

1. 查看本文件、`docs/RENDERING_CHECKPOINT.md` 和当前工作区变更。不要重做已验证项或将局部测试当作全项通过。
2. 用 `scripts/gradle.ps1 runDevtoolsClient --console=plain` 启动，再运行 `python scripts/open-dev-world.py orimod_dev_form`，等待首次资源加载完成再打开世界。该世界种子为 8675309；平台在 y=99，玩家安全点为 (0.5,100,0.5)。测试脚本会改动自己的局部障碍物；旧渲染测试世界 `orimod_dev_compatibility` 保留。
3. G 面板、购买、五种碎片效果与领取已验证，见 `docs/PROGRESSION_CHECKPOINT.md`。刀弓规则和生命周期证据见 `docs/COMBAT_CHECKPOINT.md`，模型和动画仍为临时素材。接下来实现三类灵迹、挑战、和平奖励与指引。
4. 补齐正式合成和死亡恢复检查，推进计划中的资产、成长、战斗、探索、设置与最终验收。

变身实机截图：`run-devtools/devtools/spirit-player-first-transformation.png`。最新客户端日志：`.external/combat-final-runtime.log`。源码、脚本与检查记录按本地 Git 检查点保存；原始下载模型、Blender 修订文件、运行实例与大体积实机证据仍保留在本地，未纳入普通 Git。禁止擅自使用 Computer Use；继续使用 MCP、文件和游戏开发接口。
