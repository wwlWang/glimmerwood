# P7 修复记录

基于 `docs/PLAYTEST_P7.md`。代码起点 `bdfac98`（功能基线 `ae7dd71`）。邮箱 `wwlwwl@proton.me`。

## 已修

| 项 | 做法 | 证据 |
| --- | --- | --- |
| A1 setblock 核不扩散 | 1) `BlightEcology.grow` 对已初始化 ecology 补 `frontier()`；2) `BlightProtection.structures` 跳过未加载的结构起点引用（不再整列 DEFER） | GameTest `p5_k.command_core_grows_and_protects_building` PASS；实机新档复查 |
| A2 命令拆除刷净化 | `BlightData.removed` 只 `removeCore`，不 `begin`；玩法摧毁仍由 `GloomCoreBlockEntity.hit` 先 `begin` | GameTest `p5_k.command_remove_core_cancels_without_purify`；见 `DECISIONS.md` P7 行 |
| B 林栖商人杀不掉 | `hurt`：`BYPASSES_INVULNERABILITY`（含 `/kill`）与创造模式攻击走 `super.hurt` | `MerchantGameTests.merchant_invulnerable_unpushable_peaceful_persistent` |
| C 萤打闲置坚守者 / 蛮兵 | `conditionally_neutral` 加 `warden`、去掉 `piglin_brute` | `P5IGameTests` conditional_warden_* + `piglin_brute_remains_hostile` |
| D 和平 `/ori spawn` | 非 `target` 在 Peaceful 直接失败并提示 | `commands.orimod.spawn.peaceful` zh/en |
| E 许可措辞 | 去掉「不用于商业用途 / non-commercial」附加限制语气 | `THIRD_PARTY.md`、`README.md` |
| F1 G 面板 | 改成「指引面板」；挑战文案 Bash→猛击 | `zh_cn.json` |
| F2 领取武器 | 背包已有对应武器时显示「已领取」 | `WeaponGift.holding` + `SpiritScreen.claimState` |
| F3 zh 一致性 | 灵光弓、基座、spiritCamera 说明、地图碎片 tooltip、游戏刻等 | `zh_cn.json`（620 keys，与 en 对齐） |
| F4 HUD 盖副手 | 「灵光 N」上移到 hotbar 上方 | `SpiritHud.extras` |
| F5 Spirit Glow previousPack | ENABLE 时总是刷新 previous（无包则清空） | `SpiritGlow` tick |

## 未动（按任务跳过）

logoFile、三套名字、Flywheel 钉死、JarJar KTX、CI GameTest、Modrinth 草稿、Claude-Session 历史重写、自定义音效、第一人称刃重做、Splinter 迁移提示、zh_tw/ja。

## 测试

- JUnit：`./gradlew test` → **1887** 通过（本轮）。
- GameTest：已扩展/调整上述用例；完整 1690 套需 `runGameTestServer` / `scripts/check.sh`（实机批次另记）。
