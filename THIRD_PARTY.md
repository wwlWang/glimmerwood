# 第三方来源与署名

本项目按用户 2026-10-18 的决定仅供私人使用，备份仓库为私有仓库。当前没有公开发布计划。项目自身源码的 `All Rights Reserved` 声明不覆盖下列第三方作品。

## 主角模型

[Ori Rigged](https://sketchfab.com/3d-models/ori-rigged-d217801abaab4687b3aba56d5865e402)，作者 **Random modeler / Seea_Rooster**，模型页面标注 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。下载原件为 `models/ori/3/ori_rigged.glb`，SHA-256 为 `6ed54f4909344667651cf4055878fa6a05c3a667e10cc81dbe24e93ba396b918`。

OriMod 对该模型进行了焊接、减面、骨架精简、权重整理、材质烘焙、动画制作及第一人称手部派生。源文件、每版改动及哈希见 `art/source/character/`、`art/source/first-person/` 和对应 `manifest.json`。原件保留。

Ori 角色、名称及相关原作内容归各自权利人所有。这里记录上传者对模型文件标注的许可，不代表项目已取得原作角色或商标授权。

其他原始参考模型的来源和许可登记在 `models/asset-inventory.json`，它们不自动成为发行资源。

## 代码、渲染与画面依赖

版本、下载位置及 SHA-512 在 `dependencies/lock.json`；实际保留的二进制包另有 `dependencies/prepared.json` 的 SHA-256 校验。

| 组件 | 来源 | 许可记录 |
| --- | --- | --- |
| NeoForge MDK | [NeoForge](https://github.com/neoforged/MDK-1.21.1-ModDevGradle) | 模板许可见 `TEMPLATE_LICENSE.txt` |
| GemRender | [Warfactory-Official/GemRender](https://github.com/Warfactory-Official/GemRender) | 锁定提交的 README 标注 GPL-3；当前包内置 Flywheel 与 KTX 依赖 |
| Flywheel | [Engine-Room/Flywheel](https://github.com/Engine-Room/Flywheel) | 保留在依赖包内的许可与元信息适用 |
| Iris、Sodium、Colorwheel | `dependencies/lock.json` 中各自的上游链接 | 分别记录为 LGPL-3.0-only、Polyform Shield、LGPL-3.0 |
| Photon、LDLib2、KilaGraph | [Low-Drag-MC](https://github.com/Low-Drag-MC) | Photon 源码与制品许可标注存在差异，尚未解决公开再分发条件；LDLib2 为 LGPL-3.0-only，KilaGraph 为 MIT |
| Mellow | [TheCMK/mellow-shader](https://codeberg.org/TheCMK/mellow-shader) | MIT；预设修改单独保存在 `dependencies/shader-settings/` |

Minecraft 反编译源码只用于本机核对接口，位于被忽略的 `.external/`，不进入 Git 或备份仓库。完整本地恢复备份另行保留。
