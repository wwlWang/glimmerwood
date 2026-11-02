# 第三方来源、许可与署名

## 声明

本项目自 2026-11-01 起公开源码。项目自身的代码与自制资源按 [GPL-3.0-only](LICENSE) 发布，不用于商业用途。下列第三方作品不适用该许可，各自遵循原许可。

本项目是非官方的同人作品，与 Microsoft、Xbox Game Studios、Moon Studios 无关，也未获其授权或认可。Ori 角色、名称及相关原作内容归各自权利人所有。项目不包含 Moon Studios 的原始贴图、模型、音乐或音效：声音只使用原版 Minecraft 音效事件，怪物、建筑、方块与界面资源由 `scripts/art/` 等脚本程序化生成或自制。

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## 主角模型（CC BY 4.0）

- 原作：[Ori Rigged](https://sketchfab.com/3d-models/ori-rigged-d217801abaab4687b3aba56d5865e402)
- 作者：Random modeler（[Seea_Rooster](https://sketchfab.com/Seea_Rooster)）
- 许可：模型页面与文件元信息标注 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- 原件：`models/ori/3/ori_rigged.glb`，SHA-256 `6ed54f4909344667651cf4055878fa6a05c3a667e10cc81dbe24e93ba396b918`，未修改

OriMod 对该模型进行了焊接、减面、骨架精简、权重整理、材质烘焙、动画制作及第一人称手部派生。改编后的模型、贴图与动画同样按 **CC BY 4.0** 提供（不并入 GPL），包括 `art/source/character/`、`art/source/first-person/`、`art/source/deformation-sample/` 及模组内的 `ori_*` 角色资源。每版改动与哈希见对应 `manifest.json`。

这里记录的是上传者对模型文件标注的许可，不代表项目已取得原作角色或商标授权。其他候选模型只登记在 `models/asset-inventory.json` 和 [models/README](models/README.md)，原件不随仓库或模组分发。

## 随仓库分发的依赖

### GemRender

- 文件：`libs/runtime/gemrender-0.1.17.jar`（Git LFS），SHA-256 `28759c5d10b4d873b4b5fdae905d319d067519511069b93005414c0e13e69d7f`
- 上游：[Warfactory-Official/GemRender](https://github.com/Warfactory-Official/GemRender)
- 许可：GPL-3（上游 README 与 jar 内 `neoforge.mods.toml`）。jar 本身没有附许可全文；GPL-3 全文与本仓库 [LICENSE](LICENSE) 相同。
- 对应源码：上游提交 [`839c927f7086ee21bb67bd9a68edcb595d043d49`](https://github.com/Warfactory-Official/GemRender/tree/839c927f7086ee21bb67bd9a68edcb595d043d49)。该 jar 由此提交本地构建，仓库中没有记录对其源码的修改。构建记录见 `dependencies/lock.json` 的 `sourceBuilds`。
- jar 内置 Flywheel 1.0.6（MIT）和 LWJGL KTX 3.3.3（[BSD-3-Clause](https://www.lwjgl.org/license)）。

OriMod 运行时需要 GemRender，但不把它打进自己的 jar，而是作为独立模组并排安装。

### Flywheel

- 文件：`libs/compile/flywheel-neoforge-1.21.1-1.0.6.jar`，只用于编译
- 上游：[Engine-Room/Flywheel](https://github.com/Engine-Room/Flywheel)
- 许可：MIT，原许可文本保留在 jar 内的 `META-INF/LICENSE.md`

### NeoForge MDK

构建脚本与项目模板来自 [NeoForged MDK](https://github.com/neoforged/MDK-1.21.1-ModDevGradle)，MIT，许可见 `TEMPLATE_LICENSE.txt`。

## 不随仓库分发的开发依赖

以下组件只在开发和测试时，由 `scripts/sync-dependencies.py` 从上游（Modrinth CDN）下载，并按 `dependencies/lock.json` 中的 SHA-512 校验；Iris 的编译接口另由 Gradle 从 Modrinth Maven 获取。本仓库与 OriMod 发行包都不包含它们。

| 组件 | 上游 | 许可（上游标注） |
| --- | --- | --- |
| Iris | [IrisShaders/Iris](https://github.com/IrisShaders/Iris) | LGPL-3.0-only |
| Sodium | [CaffeineMC/sodium](https://github.com/CaffeineMC/sodium) | Polyform Shield 1.0.0 |
| Colorwheel | [djefrey/Colorwheel](https://github.com/djefrey/Colorwheel) | LGPL-3.0 |
| Photon | [Low-Drag-MC/Photon](https://github.com/Low-Drag-MC/Photon) | 源码与制品标注不一致（CC BY-NC-SA 4.0 / GPL-3.0），因此不做任何再分发 |
| LDLib2 | [Low-Drag-MC/LDLib2](https://github.com/Low-Drag-MC/LDLib2) | LGPL-3.0-only |
| KilaGraph | [Low-Drag-MC/KilaGraph](https://github.com/Low-Drag-MC/KilaGraph) | MIT |

运行时它们都是可选的：OriMod 只在检测到 Iris 时调用其接口切换光影，Photon 只通过反射检测。

## 随模组附带的光影包 Spirit Glow（MIT）

`shaderpacks/spirit-glow/` 是 TheCMK 的 [Mellow](https://codeberg.org/TheCMK/mellow-shader)（[Modrinth](https://modrinth.com/shader/mellow)）改编版，基于上游提交 `3ad9445`（与 v3.4.1a 发行包一致）。构建时由 `zipSpiritGlow` 打成 `orimod_shaderpack/spirit_glow.zip` 放入模组 JAR。它默认关闭，玩家在模组设置中打开后才复制到 Iris 的 `shaderpacks/Spirit Glow.zip`。

- 许可：MIT，光影包整体（含我们的修改）继续按 MIT 提供。包内 `LICENSE` 在原文之上加了改编声明，原 MIT 文本（Copyright (c) 2026 TheCMK）未改；`LICENSE-APACHE` 原样保留。
- 按作者要求：注明出处并附链接（包内 `README.md`、光影设置页 INFO 行）；不使用 Mellow 名称作为包名，不带 Mellow 的截图、标志和 `mellow.png` 调色表。Spirit Glow 不是 Mellow 官方版本，也未获作者背书。
- 改动清单见包内 `CHANGES.md`，导入范围见 `UPSTREAM.md`。调色表 `img/luts/spirit.png` 由 `scripts/shader/make-spirit-lut.py` 从包内中性表生成。
- 预设修改单独保存在 `dependencies/shader-settings/`。

## Minecraft

Minecraft 反编译源码只在本机用于核对接口，位于被忽略的 `.external/`，不进入仓库，也不随模组分发。
