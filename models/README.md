# Ori 模型检查

检查日期：2026-10-14。三个 GLB 均已通过 Blender 5.1.2 MCP 导入，并查看正面、侧面和斜侧面的材质预览。原始下载文件没有修改；各文件 SHA-256、来源元信息和结构统计见 [asset-inventory.json](asset-inventory.json)。

## 检查结果

| 目录 | GLB 三角面 | 骨架 / 动画 | 材质 | 用途判断 |
| --- | ---: | --- | --- | --- |
| `ori/1` | 9,496 | 无骨架，无动画 | 3 个材质，1 张眼睛纹理 | 面数符合初始预算；比例更圆，可以作为重新绑定候选 |
| `ori/2` | 57,560 | 无骨架，无动画 | 1 个材质，1 张颜色纹理 | GLB 较重；原始包另有更轻的 OBJ，值得优先评估 |
| `ori/3` | 68,888 | 186 个关节，无动画 | 1 个基础材质，无纹理 | 有完整顶点权重，可用于绑定参考和蒙皮验证；需要减面、精简骨架和补材质 |

3 号 GLB 的 48,262 个导入顶点均有权重，每顶点最多 4 个影响，实际有权重的骨骼为 166 根。骨架多为 `Bone.*` 命名，还需要做语义命名和关节映射。上述计数只说明权重存在；弯曲时是否塌陷、拉丝，尚未做姿态验收。

2 号原始压缩包内的 `source/Ori_LP.zip/Ori.obj` 有 8,594 个顶点、8,654 个面，按面顶点数量计算三角化后为 17,136 面，明显轻于网站转换的 GLB。原包包含颜色纹理，但 OBJ 引用的 `Ori.mtl` 未出现在包内，导入时需要重建材质连接。该 OBJ 目前只做结构统计，尚未做可视导入与绑定测试。

因此应同时保留 **GLB 和原始压缩包**。优先比较 2 号原始低模与 3 号骨架的适配成本，再确定正式底稿；1 号作为低面数备选。当前没有把任何一个直接定为正式角色，也没有把现成骨架视作已完成动画。

## 来源与署名

以下作者与许可来自下载 GLB 内的 `asset.extras`，页面链接由用户提供并与文件内容相符。网页抓取返回 403；尚未独立核实网页当前内容。采用资产时将对应署名随发布包携带，并记录具体修改。

| 目录 | 标题 / 作者 | 来源 | 文件声明许可 |
| --- | --- | --- | --- |
| `ori/1` | Ori from the game "Ori and the Blind Forest" / vincentskc | [Sketchfab 模型](https://sketchfab.com/3d-models/ori-from-the-game-ori-and-the-blind-forest-9154a505ba8143cba354d04ac9f7ac29) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `ori/2` | Ori / Kristian LaGrange | [Sketchfab 模型](https://sketchfab.com/3d-models/ori-be03540404ec4e69b8a2d969865a4c48) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `ori/3` | Ori Rigged / Random modeler | [Sketchfab 模型](https://sketchfab.com/3d-models/ori-rigged-d217801abaab4687b3aba56d5865e402) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |

## 本地预览与保留文件

`review/orimod-candidates.blend` 是新保存的检查副本，含原始默认 Scene 与三个独立候选场景；GLB 的转换轴向和比例暂未归一化。此文件及所有模型二进制暂不入 Git。

`Ku.blend` 已登记哈希；本轮没有打开、修改或用作 Ori 角色底稿。
