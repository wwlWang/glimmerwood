# 文档索引

这些文档是开发过程中的记录，大多按轮次（R*）和试玩阶段（P*）写成，保留了当时的结论和失败原文。玩家只需要看根目录的 [README](../README.md)。

## 先读这些

| 文档 | 内容 |
| --- | --- |
| [WORK_STATUS](WORK_STATUS.md) | 当前工作状态与后续项 |
| [DECISIONS](DECISIONS.md) | 用户决定与开发决定 |
| [DEVELOPMENT](DEVELOPMENT.md) | 开发客户端、devtools 接口与实拍脚本 |
| [TESTING](TESTING.md) | 测试范围、全量结果与报告路径 |
| [CLOUD_HANDOFF](CLOUD_HANDOFF.md) | 云端开发交接 |

## 计划与设计

| 文档 | 内容 |
| --- | --- |
| [PROJECT_PLAN](PROJECT_PLAN.md) | 项目计划大纲 |
| [ADR-001](ADR-001-SINGLE-PLAYER.md) | 首版按单人集成服务器交付 |
| [ORI_PARITY](ORI_PARITY.md) | 原作机制对照 |
| [SURVIVAL_FLOW](SURVIVAL_FLOW.md) | 无命令生存流程 |
| [ROADMAP_R96](ROADMAP_R96.md) · [R100](ROADMAP_R100.md) · [R103](ROADMAP_R103.md) · [R106](ROADMAP_R106.md) · [R109](ROADMAP_R109.md) | 分阶段路线 |
| [ANIMATION_REFERENCE](ANIMATION_REFERENCE.md) | 动作参考 |

## 验收与审查

| 文档 | 内容 |
| --- | --- |
| [ACCEPTANCE](ACCEPTANCE.md) / [ACCEPTANCE_STATUS](ACCEPTANCE_STATUS.md) | 首版验收计划与逐项状态 |
| [AUDIT_FIXES](AUDIT_FIXES.md) | 2026-10-18 审查修复 |
| [AUDIT_P5_CODE](AUDIT_P5_CODE.md) | P5 代码层排查 |
| [FEEL_AUDIT](FEEL_AUDIT.md) | 手感审计；模拟曲线在 [feel/](feel/) |
| [ORI_ANIM_AUDIT](ORI_ANIM_AUDIT.md) / [ANIMATION_AUDIT_RUNTIME](ANIMATION_AUDIT_RUNTIME.md) | 动画审计 |

## 实现与实机记录

| 文档 | 内容 |
| --- | --- |
| [BOOTSTRAP](BOOTSTRAP.md) | 工程初始化 |
| [CHARACTER_CHECKPOINT](CHARACTER_CHECKPOINT.md) | 角色资产与持物 |
| [MOVEMENT_CHECKPOINT](MOVEMENT_CHECKPOINT.md) | 移动 |
| [COMBAT_CHECKPOINT](COMBAT_CHECKPOINT.md) | 战斗 |
| [PROGRESSION_CHECKPOINT](PROGRESSION_CHECKPOINT.md) | 成长 |
| [WORLD_CHECKPOINT](WORLD_CHECKPOINT.md) | 遗迹与挑战 |
| [RENDERING_CHECKPOINT](RENDERING_CHECKPOINT.md) | 渲染联调 |

## 试玩与反馈

| 文档 | 内容 |
| --- | --- |
| [FEEDBACK_FIXES](FEEDBACK_FIXES.md) | 用户实测反馈修复 |
| [PLAYTEST_R116](PLAYTEST_R116.md) · [R117](PLAYTEST_R117.md) · [R117 反馈](PLAYTEST_R117_FEEDBACK.md) | R116–R117 试玩 |
| [PLAYTEST_P5](PLAYTEST_P5.md) · [P5 修复计划](PLAYTEST_P5_FIX_PLAN.md) · [P6](PLAYTEST_P6.md) | P5–P6 试玩 |

## 其他目录

- [drafts/](drafts/)：未合入的方案草稿；`r109-checkpoint/` 是暂停中的 R109 灵战祭坛实现快照，未注册进游戏。
- [benchmarks/](benchmarks/)：世界生成性能模型与数据。
- [feel/](feel/)：`scripts/feel_curves.py` 输出的手感曲线与指标。
- [modrinth/](modrinth/)：Modrinth 项目页素材（简介、长描述、图标、画廊说明）。
- [images/](images/)：README 使用的游戏截图（开发客户端 + Spirit Glow 实拍）。
