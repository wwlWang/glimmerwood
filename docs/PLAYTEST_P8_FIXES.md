# P8 修复记录

基于 `docs/PLAYTEST_P8.md` 与试玩反馈。代码：`8bfddc4`（功能）→ `52ba5fe`（R117 测试对齐）→ 本记录所附 Ginso login 补丁。邮箱 `wwlwwl@proton.me`。

## 已修

| 项 | 做法 | 证据 |
| --- | --- | --- |
| A GameTest `structure_reference_waits_for_unloaded_start` | 断言从 DEFER 改为 **ALLOW**（匹配 P7「未加载起点不整列 defer、允许生长」语义）；不回退 P7 growth | GameTest blight 批；全量 `check_full.log`：**1692** GameTest / **1887** JUnit 全绿（`52ba5fe`） |
| B Ginso 逃亡中途存盘退出 | `GinsoEscapeRecovery` 增加 piece+elapsed 快照；`saveProgress` / `disconnect` / `stop` 写入；`login`/`tick` 调 `resume()` 重建 session（对标 Gloom `EscapeSequence`）。**补丁**：`resume()` 在校验水之心前 `level.getChunk(heart)`——login 可能早于玩家 chunk ticket，旧逻辑 `!hasChunkAt` 会误走 fail→COOLDOWN | 实机 `orimod_dev_pt117`：leave 后 NBT `elapsed=167` `escape_version=2`；rejoin **phase=RUNNING**（非 COOLDOWN），elapsed 延续。`shots/playtest-p8fix-ginso-*.png`；`pt10/p8fix_final_notes.txt` `resume_ok: True` |
| C 制图师空站点仍标价 | `MerchantTrading.offerable`：tree/blight 仅当 `FeatureSites.nearest` 有结果才进 offers | 实机远点 `(250000,81,250000)`：`featureSites` 世界内仍有树/腐化，但 **offers=[]**（nearest 不可达）。`shots/playtest-p8fix-cart-nosite.png` |
| D 硬核旁观残留精灵形态 | `SpiritForm`：spectator 时 idle / 清 `FORM`，保留 `AWAKENED` | 实机 hardcore：form True → 死 →「旁观世界」→ `playerGameType=3`，**form=False**，**awakened=True**。`shots/playtest-p8fix-hc-*.png` |
| 载具 | 逃亡中 `EntityMountEvent` 取消 + `dismount`；ride 命令亦被拒 | 实机逃亡中 `ride @s mount` →「无法骑乘…」，`execute on vehicle` 空 |
| logoFile | `mods.toml` + `logo.png`（`8bfddc4`） | 打包元数据；非玩法 |

## 决定

- Ginso 中途退出：**与腐光之心逃亡相同**——只记在线 elapsed，重进续跑，不进 COOLDOWN、不消耗首次通关奖励。真正失败（淹没/超时/出界/心跳块没了）仍 pending recover→安全点+冷却。
- login 时心跳区块未票：强制 `getChunk` 再判断方块；若仍 resumable 则留给 player tick 重试，**禁止**误清快照。
- 制图师：没有可达 FeatureSites 时隐藏 offer，避免「标价点下去 not_found」。
- 硬核旁观：形态关掉，觉醒保留（可再变身逻辑仍在，只是 spectator 不显示精灵形态）。

## 未动（按任务跳过）

第一人称刃、三套改名、Flywheel、JarJar、CI GameTest、自定义音效、Claude 历史、其它 drive-by。从未使用 `/orimod reset @a`。

## 测试

- 全量（修功能后、login 补丁前）：`pt10/check_full.log` — JUnit **1887**/0，GameTest **1692**/0，`BUILD SUCCESSFUL`（~11 min）。login 补丁仅改 `GinsoEscape.resume/login`，未重跑全量。
- R117 / Ginso GameTest：`52ba5fe` 已对齐 logout resume；`gametest_r117` 等见 pt10。
- 实机 Xvfb `runDevtoolsClient`：`pt10/p8fix_final_notes.txt` + `shots/playtest-p8fix-*`。

## 残留 / 未验证

- 成功/失败路径（登顶 / 淹没）本轮未完整重跑（既有 GameTest 覆盖；实机只确认中途 resume + 载具拒绝）。
- 制图师「世界内 trees=0 且 blights=0」的空档未单独建档；用「nearest 不可达 → offers=[]」等价验证。
- `minecraft:oak_boat` 在本环境 `/summon` 无实体类型；载具拒绝改用猪+ride（同一 `mount` 取消路径）。
