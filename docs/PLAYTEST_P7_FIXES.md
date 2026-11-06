# P7 修复记录

基于 `docs/PLAYTEST_P7.md`。代码起点 `bdfac98`（功能基线 `ae7dd71`）。邮箱 `wwlwwl@proton.me`。

## 已修

| 项 | 做法 | 证据 |
| --- | --- | --- |
| A1 setblock 核不扩散 | 1) `BlightEcology.grow` 对已初始化 ecology 补 `frontier()`；2) `BlightProtection.structures` 跳过未加载的结构起点引用（不再整列 DEFER） | GameTest `p5_k.command_core_grows_and_protects_building`；实机 `orimod_dev_p8d`：500 tick 后 `fill … replace orimod:blighted_soil` 得 **54** / **84** 格；`blight_animals_probe` columns≈55–87。截图 `shots/playtest-p8-a1-growth.png` |
| A2 命令拆除刷净化 | `BlightData.removed` 只 `removeCore`，不 `begin`；玩法摧毁仍由 `GloomCoreBlockEntity.hit` 先 `begin` | GameTest `p5_k.command_remove_core_cancels_without_purify`；实机 setblock 核→air 后 status 不再指向该核、650 tick 后 sanctuary=`present:false`、`region={}`。见 `DECISIONS.md` P7 |
| B 林栖商人杀不掉 | `hurt`：`BYPASSES_INVULNERABILITY`（含 `/kill`）与创造模式攻击走 `super.hurt` | GameTest `merchant_invulnerable_unpushable_peaceful_persistent`；实机 survival 拳打仍在、`/kill` 后 `nearby=[]`。`shots/playtest-p8-b-merchant-after-kill.png` |
| C 萤打闲置坚守者 / 蛮兵 | `conditionally_neutral` 加 `warden`、去掉 `piglin_brute`；Warden GameTest 改用 `setAttackTarget`（`getTarget` 读 brain） | GameTest `conditional_warden_*` + `piglin_brute_remains_hostile`；实机 idle warden `eligible=false` hp 500→500；aggro 后 eligible 且 hp 492→476（fired+8）；戴金蛮兵 `eligible=true`、6 格内 hp 46→38。`shots/playtest-p8-c2-*.png` |
| D 和平 `/ori spawn` | 非 `target` 在 Peaceful 直接失败并提示 | 实机：`和平难度下敌对生物会立即消失…`；`commands.orimod.spawn.peaceful` zh/en |
| E 许可措辞 | 去掉「不用于商业用途 / non-commercial」附加限制语气 | `THIRD_PARTY.md`、`README.md` |
| F1 G 面板 | 改成「指引面板」；挑战文案 Bash→猛击 | `zh_cn.json` |
| F2 领取武器 | 背包已有对应武器时显示「已领取」 | `WeaponGift.holding` + `SpiritScreen.claimState` |
| F3 zh 一致性 | 灵光弓、基座、spiritCamera 说明、地图碎片 tooltip、游戏刻等 | `zh_cn.json`（与 en 键对齐） |
| F4 HUD 盖副手 | 「灵光 N」上移到 hotbar 上方 | `SpiritHud.extras` |
| F5 Spirit Glow previousPack | ENABLE 时总是刷新 previous（无包则清空） | `SpiritGlow` tick |

## 决定（摘要）

- 命令/地图拆除腐化之核：取消区域，不进净化、不发奖；只有玩法打到 0 血才 `begin`。
- 林栖商人：生存仍免疫；`/kill` 与创造可清。
- 坚守者条件中立；猪灵蛮兵始终可被萤锁定。

## 未动（按任务跳过）

logoFile、三套名字、Flywheel 钉死、JarJar KTX、CI GameTest、Modrinth 草稿、Claude-Session 历史重写、自定义音效、第一人称刃重做、Splinter 迁移提示、zh_tw/ja。

## 测试

- JUnit：此前 `./gradlew test` → **1887** 通过。
- GameTest（过滤 `orimod_p5_i,orimod_p5`，含 warden `setAttackTarget` 修补后）：**All 202 required tests passed**（`pt8/gametest_p7fix.log`）。
- 完整 1690 套未在本轮末尾重跑（上一轮全量曾因旧 warden 断言失败 2 个；本轮已修断言）。
