# Modrinth 项目页素材

建项目或更新项目页时直接取用。

| 字段 | 值 |
| --- | --- |
| 类型 | Mod |
| 名称 | Glimmerwood |
| URL（slug） | `glimmerwood` |
| 简介（Summary） | An unofficial Ori-inspired adventure mod: spirit form, flowing movement, light weapons, ruins and bosses. |
| 图标 | [icon.png](icon.png)（512×512） |
| 长描述 | [description.md](description.md)，整段粘贴 |
| 环境 | Client and server（单人游戏两端都需要） |
| 主分类 | Adventure, Game Mechanics, Worldgen |
| 附加分类 | Mobs, Equipment, Magic |
| 许可 | GPL-3.0-only |
| Issues | https://github.com/wwlWang/glimmerwood/issues |
| Source | https://github.com/wwlWang/glimmerwood |

## 画廊

按顺序上传 `docs/images/` 里的截图，第一张设为 Featured：

| 文件 | 标题 | 说明 |
| --- | --- | --- |
| spirit-form.jpg | Spirit form | The spirit form and its companion light. |
| forest-spring.jpg | Forest spring ruin | One of three ruin challenges. |
| great-spirit-tree.jpg | Great Spirit Tree | A towering tree with its own climb and heart chamber. |
| matriarch.jpg | Gloom Matriarch | Boss encounter. |
| spirit-glow-dusk.jpg | Dusk | With the optional Spirit Glow shader. |

截图均为开发客户端开启 Spirit Glow 实拍。

## 版本

版本由 GitHub Actions 的 **Release** 工作流上传（`scripts/publish-modrinth.py`）：仓库变量 `MODRINTH_PROJECT_ID`，仓库 secret `MODRINTH_TOKEN`（Modrinth PAT，至少勾选 Create versions）。依赖只列 Iris、Sodium（可选）；GemRender 与 Flywheel 已内嵌。
