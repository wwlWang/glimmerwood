# 精灵形态与原版 MC 规则：独立判断稿

基线：`d899ce0`（R112 已提交）；2026-10-22。作者：Codex。**讨论稿，不是已批准的实现方案。** 本轮只读运行时代码及本机 MC 1.21.1 / NeoForge 源码，未运行客户端、Gradle 或实机实验。以下“现状”是静态路径结论；需要实测的交互明确另列。不把 R112 新动画等同于新玩法。

My independent view is: **应该优先统一精灵的移动规则、生命/能量循环和重试体验，同时保留 MC 的建造、物品与多人世界规则。** 换羽毛姿势但继续执行火箭鞘翅物理，只完成了外观；反过来一次删掉饥饿、护甲、XP 和物品掉落，会拆掉整个 MC 生存经济。建议以 WotW 的战斗/资源体验为主，明确保留 BF 的 Soul Link，承认这是一套经过选择的混合规则。

## 证据口径与工作量

- 仓库链接带当前行号；MC 原版位置用 `MC:包路径/文件.java:行` 表示，来自已解析的 NeoForge 源码 JAR：`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_afbadac5ccebca85f268cc8c658934b1e496edcb_output.jar`。这是本机依赖源码，不需要重新跑 Gradle。仓库注册入口与 mixin 列表也已核查，不能仅凭一个类里没找到方法就断言“完全原版”。
- Ori 对照优先用发行商手册和 Xbox 官方资料；具体补给/碎片规则使用社区 Wiki，链接随对应结论提供。BF、BF Definitive Edition（DE）和 WotW 分开写。官方资料没有给出的精确伤害、无敌帧、帧数，不据印象填数。
- **S**：局部规则/表现与少量回归；**M**：跨客户端/服务端或多个资源系统，需多人和兼容验证；**L**：改变经济、死亡或装备体系，需要迁移、专项测试和实机调参。不是工期承诺。
- “需拍板”表示玩法取舍需要决定，不表示本轮应执行。实现时人形默认维持原版；会影响双方的规则由服务端决定，HUD 外观才由客户端选择。不开启全服 `keepInventory` / 关闭全服 `naturalRegeneration` 来代替精灵专属规则。

## 现状速览

| 项目 | 代码确认的状态 | 独立建议 | 量级 | 需用户拍板 |
| --- | --- | --- | --- | --- |
| 鞘翅/烟花 | 原版飞行优先于 Ori 空中动作；已有另一套羽毛滑翔 | 精灵默认羽毛规则，保留明确的原版远行途径 | M | **是** |
| 摔落 | 精灵已免摔落伤害 | 保留；别重复实现，别扩成全环境免伤 | S（验证） | 否 |
| 饥饿/自然回血 | FoodData 原版，跳跃等仍可耗饥饿 | 补给齐全后，精灵停用饥饿生存循环 | L（整套） | **是** |
| 生命恢复 | 灵泉、生命格、两种回血碎片；无 Regenerate/独立生命球 | 补主动恢复与独立补给球 | M–L | **是** |
| 能量恢复 | 停手 2 秒后自动回至满值 | 保留开放世界兜底，但不要无限免费治疗循环 | M–L | **是** |
| HUD | 原版 HUD + 左下角额外能量条 | 生命/能量主显示，其他仍生效的指标保留 | M | 布局确认 |
| 死亡/掉落 | 进度保留、物品/XP 原版掉落、复活回人形 | 先保证形态/安全重生；全保物另定 | L | **是** |
| 护甲/附魔 | 护甲只隐藏模型，数值仍生效 | 默认兼容保留；纯碎片防御作为独立后续选择 | S（说明）/L（替代） | **是** |
| 冲刺/体力 | 地面已用自己的速度曲线；原版饥饿/冲刺标志仍在 | 无新增体力条，清理双重语义 | M | 小 |
| 溺水 | 未学 Water Dash 时原版；学后精灵无限呼吸 | 保留能力门槛；是否拆 Water Breath 后议 | S/M | 拆技能需 |
| XP/灵光 | 两本账；灵光死亡保留 | 保持分离，不自动兑换 | S（HUD） | 合并需 |
| 剑/斧与灵刃 | 剑斧原版冷却，灵刃已有独立三连 | 优先提升灵武器；不统一取消剑斧冷却 | S（维持）/L（改造） | 改造需 |
| 灵武器物品性 | 刀/弓会耗耐久，锤/矛不耗；领用有回执 | 后续统一成能力凭证，先解决丢失/损坏不一致 | M | **是** |
| 受击/环境危险 | 伤害冷却仍属 MC，只有动作中断与特定危险自定义 | 先测连续受击，不全局加“冲刺无敌” | M | 改战斗需 |
| 睡眠/幻翼 | 原版睡觉和失眠；灵泉不等于睡觉 | 保留跳夜；可让主动灵泉休息算休息 | S–M | **是** |
| 挖掘/建造/代步 | 原版玩法保留，部分遗迹方块另设能力门槛 | 保留，不为了像 Ori 全局禁建/禁骑 | S（维持） | 禁用需 |

## 1. 鞘翅、羽毛、烟花：先解决两套飞行争夺输入

**现状。** [SpiritControls.java:64](../../src/main/java/cn/wwl/orimod/client/SpiritControls.java#L64) 在 Ori 消耗空中跳跃前，主动检查胸槽 `canElytraFly` 并发送 `START_FALL_FLYING`。一旦鞘翅起飞，[SpiritMovement.java:31](../../src/main/java/cn/wwl/orimod/movement/SpiritMovement.java#L31) 的 `canMove` 返回 false，Ori 的地空移动控制退出。R112 只在 [SpiritAnimationRules.java:22](../../src/main/java/cn/wwl/orimod/client/render/SpiritAnimationRules.java#L22) 选了 `elytra` 片段，未换物理。原版鞘翅层也仍允许渲染：[SpiritVanillaLayers.java:67](../../src/main/java/cn/wwl/orimod/client/render/SpiritVanillaLayers.java#L67)。

烟花没有精灵分支：`MC:world/item/FireworkRocketItem.java:59` 按 `isFallFlying()` 使用并消费火箭，`MC:world/entity/projectile/FireworkRocketEntity.java:121` 沿视线持续加速；`MC:world/entity/LivingEntity.java:2282` 保留俯冲、拉升和撞墙伤害。与此同时，模组已有慢降/可操纵的 GLIDE：[AirMovement.java:88](../../src/main/java/cn/wwl/orimod/movement/AirMovement.java#L88)、[MovementRules.java:131](../../src/main/java/cn/wwl/orimod/movement/MovementRules.java#L131)，水平输入计算有 `.5` 格/tick 上限，另有灵风口上升。它不是未实现的能力。

**Ori 对照。** 羽毛用于缓降、横移和乘风上升，不能等同于 MC 靠俯仰换速度的机翼，更没有烟花推进链。BF 手册明确把羽毛和风道绑定；WotW 也有 Glide。[发行商手册，第 5 页](https://manuals.thqnordic.com/OriAndTheBlindForest/Ori_PC_QuickstartGuide_online.pdf)、[Kuro’s Feather](https://oriandtheblindforest.fandom.com/wiki/Kuro%27s_Feather)

**建议。** 默认精灵空中操作归 GLIDE，胸槽鞘翅不要再抢二段跳；用实际羽毛表现、慢降、风场和现有 Bash/Launch 组成移动。仅把鞘翅替换成羽毛模型不算玩法达标。GLIDE 解锁条件保留，拥有鞘翅不偷偷赠送永久技能。

MC 远行价值有三种取舍，我推荐 **A**：

| 选择 | 规则 | 判断 |
| --- | --- | --- |
| **A：精灵羽毛，人形远行** | 原版鞘翅/火箭在人形完整保留；精灵用羽毛、灵泉传送 | 边界最清楚，改动适中；代价是长途旅行要切人形 |
| B：精灵内显式远行模式 | 另有明确操作进入原版鞘翅模式，必须穿鞘翅；耐久/火箭照旧 | 适合坚持全程 Ori 外形的用户；两套控制权仍需维护，量级增加 |
| C：烟花改羽毛“风冲” | 用一次脉冲代替持续推进，重新定距离/冷却/消耗 | 可玩，但这是本模组新能力，不是原作；不建议本批同时设计 |

选 A 时，精灵在空中拿火箭使用应明确提示“不用于羽毛推进”，不扣火箭；地面烟花、弩射烟花、发射器照旧。飞行中变身必须交接速度与状态，不能只关 `FallFlying` 造成骤停/服务器纠正。已有灵泉网络可降低远行成本，但目前仅逐个循环目的地，不能假称已能代替任意远行（见第 15 项）。

**风险。** 不删除/替换鞘翅 ItemStack，保留耐久、附魔、绑定诅咒及存档。其他模组通过 `canElytraFly` 提供的翅膀须有可解释的兼容策略，不能按所有胸甲强行拦截。服务端和本地预测同规则；原版飞行统计/数据包条件仅在真实原版飞行时触发，不能伪造。创飞 `mayfly` 仍走已修好的原版双击跳。**M；A/B/C 必须拍板。**

## 2. 摔落伤害：已经解决，不应再立一轮“去除摔伤”

**现状。** [AirMovement.java:99](../../src/main/java/cn/wwl/orimod/movement/AirMovement.java#L99) 对精灵的 `LivingFallEvent` 直接取消并清 `fallDistance`；STOMP 另行处理冲击。[SpiritLandingParticlesMixin.java:21](../../src/main/java/cn/wwl/orimod/mixin/SpiritLandingParticlesMixin.java#L21) 也清落地计量。人形保留原版。鞘翅撞墙的 `flyIntoWall` 是另一伤害路径，不属于免摔伤。

**Ori 对照。** 普通落地并不采用 MC 的按落差扣血规则；危险来自落点的刺、污水、深渊等。社区实玩记录也区分 WotW 无普通摔伤与剧情坠落。[WotW 玩家讨论（辅助证据）](https://steamcommunity.com/app/1057090/discussions/0/1750151146487495551/?ctp=112&l=indonesian)、[BF 危险物](https://oriandtheblindforest.fandom.com/wiki/Dangers_%28Ori_and_the_Blind_Forest%29)

**建议。** 保留现状。验收“高台落地不伤、落到荆棘照伤、虚空照常处理、STOMP 仍命中”。不要将 `fall` 的豁免扩大到窒息、火焰、虚空、管理员伤害。临落地切形态现在可规避摔伤，这是形态能力的既有效果，要不要加切形态限制属于另一项玩法决定。

**风险。** 依赖落差的成就/数据包可能受清零影响；其他模组的非 `fall` 伤害不保证被豁免，也不应默认拦截。无存档迁移。**S（回归）；保留无需拍板，扩大免伤需拍板。**

## 3. 饥饿与自然回血：真正与 Ori 冲突的生存规则

**现状。** 没有 FoodData、食物消耗或自然回血替换。`MC:world/entity/player/Player.java:292` 调 `FoodData.tick`；`MC:world/food/FoodData.java:39` 处理饱食回血、低食物饥饿伤害与消耗。不是只有 HUD 还像 MC：它仍真的影响生命。自定义跳跃仍调 [SpiritMovement.java:183](../../src/main/java/cn/wwl/orimod/movement/SpiritMovement.java#L183) 的 `player.jumpFromGround()`，进入 `MC:world/entity/player/Player.java:1565` 的饥饿消耗。模组生命恢复是叠加在其上的 [SpiritProgression.java:225](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L225)，不是替代。

**Ori 对照。** 正常探索围绕生命、能量与补给，没有 MC 那种长期消耗的饥饿/饱和度生存条；也没有吃饱后按 FoodData 自动回血的循环。BF 故事里的饥荒不能当作有这一套可操作机制的依据。BF 生命恢复依赖补给等明确来源。[Health](https://oriandtheblindforest.fandom.com/wiki/Health)

**建议。** 等第 4–5 项补给闭环可用后，对精灵暂停饥饿代谢、饥饿伤害和食物驱动自然回血；人形保持原版。不要每 tick 强塞 food=20，那会白送回血、破坏旧饱和度，并使切形态刷新食物。已有 FoodData 留存，切回人形恢复原有语义；显式进食、药水、金苹果效果不能被“取消全部 heal”一起吃掉。

折中上我不推荐把所有面包/肉自动按饱食值换 HP：这会引入另一套治疗表且农场产量可压过 Regenerate。首版让农业继续服务人形、村民、动物、酿造；是否让部分食物成为精灵的补给配方单独定价。不能在补给仍稀缺时先删自然回血。

**风险。** 食物/营养模组、`naturalRegeneration`、和平难度回血、进食成就与附魔效果要逐路径区分；死亡和切形态不能清洗/复制资源。多人必须服务端权威，不得由客户端隐藏饥饿条自行宣告免饥饿。**L（与恢复系统一批）；需要拍板：精灵是否正式脱离农业维生。**

## 4. 回血来源：生命球、Regenerate、灵泉各司其职

**现状。** [OriItems.java:10](../../src/main/java/cn/wwl/orimod/item/OriItems.java#L10) 注册的是灵光、生命格、能量格，没有独立的临时生命/能量补给球；[Ability.java:7](../../src/main/java/cn/wwl/orimod/progression/Ability.java#L7) 没有 Regenerate。已有四条恢复路径：

| 路径 | 当前效果与证据 |
| --- | --- |
| 灵泉/神龛 | [SpiritWell.java:44](../../src/main/java/cn/wwl/orimod/world/SpiritWell.java#L44)：已觉醒玩家血和能量补满，共用 1200 tick 冷却；不是每泉独立冷却 |
| 生命格 | [SpiritCells.java:19](../../src/main/java/cn/wwl/orimod/progression/SpiritCells.java#L19)：永久 +2 最大 HP，同时 heal(2)，最多 10 个；不是可重复消费的“小血球” |
| LIFE_HARVEST | [ShardEffects.java:44](../../src/main/java/cn/wwl/orimod/progression/ShardEffects.java#L44)：拾取灵光每点恢复 .5 HP，滑动 20 tick 窗口最多 1 HP；任务直接发币不算 |
| RESILIENCE | [SpiritProgression.java:212](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L212)：受伤后等 80 tick，花 6 能量 heal(1)，200 tick 治疗冷却，一次挂起恢复；不是百分比减伤 |

**Ori 对照。** WotW 的 Regenerate 是消耗能量的主动治疗，Wiki 给出 1 能量格恢复 3 生命格；不能把这个比率直接照搬到本模组 100 点能量/20 HP。灵泉恢复生命能量，BF DE 增加泉间快速旅行。原作 Resilience 是减伤碎片，并非受伤后自动回血。[Regenerate](https://oriandtheblindforest.fandom.com/wiki/Regenerate)、[Spirit Well](https://oriandtheblindforest.fandom.com/wiki/Spirit_Well)、[Resilience](https://oriandtheblindforest.fandom.com/wiki/Resilience)

**建议。** 建立三层：战斗中移动拾生命球；空档主动耗能 Regenerate；安全营地灵泉补满。球与永久生命格明确区分名称/外观，满血不错误增加上限，拾取由服务端消费一次，协作时规定归属。Regenerate 有可读的引导时间，受攻击/换维度/退出会话中止，结算与扣能同一事务；不是按键即免伤满血。灵泉允许安全区可靠重试，现有一分钟节流可改为“离战后短交互”，但须防 Boss 旁无限泉战术。

RESILIENCE 不在这个更新里悄悄改掉已购碎片效果；先记录偏差，若恢复原作减伤，需要公告/迁移并与护甲叠加计算一起定。LIFE_HARVEST 也是当前近似实现，不能在新增球后又对同一奖励双计回血。

**风险。** MC 的 `heal` 事件、药水/金苹果/信标继续有效；血量显示使用真实 attribute，不另建第二套 HP。刷怪塔/容器是否可刷补给、多玩家一球多领、死亡中断和商店价格均需测试。旧世界也必须有当场可得的来源，不能只靠新生成结构。**M–L；治疗强度、泉限制、碎片改义需要拍板。**

## 5. 能量：当前“等一等就满”会决定 Regenerate 是否失衡

**现状。** [SpiritProgression.java:225](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L225)：已觉醒且活着，花能量后等 40 tick，每 4 tick 回 1（RECOVERY 则 1.5），可回到上限；即约 5/7.5 点每秒。未要求保持精灵形态。Flash 活跃期间暂停。另有 ENERGY_HARVEST 每灵光 +2 能量、灵泉和能量格。

**Ori 对照。** 能量有可拾取补给和泉，补给的位置/时机参与关卡和战斗；WotW 的 Regenerate 使能量选择同时影响输出与生命。BF 的 Energy Efficiency、WotW 的 Energy Harvest/Catalyst/Overflow 都围绕有来源的恢复设计。[BF 能力树](https://oriandtheblindforest.fandom.com/wiki/Ability_Tree_%28Ori_and_the_Blind_Forest%29)、[WotW 碎片](https://oriandtheblindforest.fandom.com/wiki/Spirit_Shards)

**建议。** 不把自动回能立即全删：MC 没有原作那样密集布置的补给。中期改成“脱战低位兜底 + 能量球/资源点 + 灵泉”，兜底恢复够一次基础解谜操作，但低于一次 Regenerate 成本；常规补满靠明确来源。具体底线、法术费用要一起算，示意阈值不是已决定的平衡数。若用户偏休闲，也可保留满回，但应明说接受“脱战后可反复主动回血”，这和原作资源压力不同。

**风险。** 需要覆盖老区、和平、海上远行、核心净化和持续灵闪，不可制造能量软锁；死亡返能和人形等待都要纳入，否则新限制可被变身绕过。RECOVERY/ENERGY_HARVEST 的价值会改变。**M–L；是否保留无限脱战恢复需拍板。**

## 6. HUD：统一主信息，而不是隐藏仍会杀人的状态

**现状。** [SpiritCombatClient.java:76](../../src/main/java/cn/wwl/orimod/client/SpiritCombatClient.java#L76) `registerAboveAll` 追加左下能量条、中央蓄弓/连击字样。没有取消原版生命、饥饿、护甲或经验层；全局 mixin 清单也没有 HUD 替换。生命仍是 MC HP，生命格增加真实 MAX_HEALTH：[SpiritCells.java:43](../../src/main/java/cn/wwl/orimod/progression/SpiritCells.java#L43)。

**Ori 对照。** 主要资源读数是生命格、能量格和灵光/成长，而非心、鸡腿、护甲与等级多层并排；BF 灵光承担升级进度，WotW 为货币。[BF HUD 与成长](https://en.wikipedia.org/wiki/Ori_and_the_Blind_Forest)、[Spirit Light](https://oriandtheblindforest.fandom.com/wiki/Spirit_Light)

**建议。** 精灵默认绿色生命格 + 蓝色能量格 + 灵光数；只重绘真实 HP/吸收量，不把两颗心的数据变成另一套血量。当前 100+ 的能量需确定显示分组与小数填充，不能画上百个格。保留快捷栏和物品计数，因为采矿/建造仍在。护甲、原版 XP 采用紧凑辅助行；饥饿在第 3 项真正切换规则前必须可见。气泡、冻结、火焰、中毒、凋零、吸收心、坐骑生命和跳跃蓄力都不能丢。

**风险。** 高生命整合包、GUI 缩放、其他 HUD 模组、F1、旁观/创飞、手柄和字幕布局；服务端不允许客户端改生命规则。保留“一键原版 HUD”兼容选项，隐藏原版层时只拦目标层。无存档数值迁移。**M；视觉布局确认即可，若连数值一起换则需要玩法拍板。**

## 7. 死亡与掉落：重试体验与运输经济要分开

**现状。** [SpiritData.java:17](../../src/main/java/cn/wwl/orimod/spirit/SpiritData.java#L17)：AWAKENED/ABILITIES/PROGRESS/SOUL_LINK `copyOnDeath`，FORM 没有，死后回人形；ENERGY 不复制，回默认 100，不是动态最大值。PROGRESS 包含灵光、碎片、奖励回执。[SoulLink.java:69](../../src/main/java/cn/wwl/orimod/progression/SoulLink.java#L69) 在普通重生后查安全链接并传送，可跨维度；坏链接清掉，退回原版位置。它不会保存/恢复背包或世界快照。`MC:world/entity/player/Player.java:693,1843` 与 `MC:server/level/ServerPlayer.java:1458` 仍按 `keepInventory` 处理物品、诅咒和 XP；模组未替换这些路径。[SpiritCells.java:53](../../src/main/java/cn/wwl/orimod/progression/SpiritCells.java#L53) 重生重应用生命格并补满血。

**Ori 对照。** BF 是回 checkpoint/Soul Link，WotW 转为自动检查点；常规模式不要求跑回死亡处捡一背包装备。**这不等于死前刚获取的所有进度一定保留**：原作有保存/回档语义，也有一命模式。MC 多人世界不能照搬整世界回滚。[Death](https://oriandtheblindforest.fandom.com/wiki/Death)、[WotW 保存机制](https://oriandtheblindforest.fandom.com/wiki/Ori_and_the_Will_of_the_Wisps)

**建议。** 第一层先做“死时是精灵，复活仍为精灵；安全点可达；动作/输入/镜头状态彻底复位”，尊重 Hardcore 与服务器死亡规则。第二层默认只保证已学能力/碎片/灵光和灵武器可恢复，普通资源保留 MC 掉落。若目标明确是 Ori 冒险服，再提供服务端选择的精灵冒险保物规则，决定普通物品和 XP 是否一起保留。

全背包保留的代价要直接告诉用户：挖满矿后自杀可当回城，死亡几乎失去运输风险，且可在危险前切精灵规避掉落。不能声称加一个冷却就自动解决。不要全服改 gamerule，不要先生成掉落再复制旧背包；墓碑/灵魂绑定模组必须只能有一方持有物品。即使保物也继续发真实死亡事件/统计，保留既有挑战、潮夜等清理路径。

**风险。** 存档新字段默认值、跨维度/床/重生锚优先级、未加载点、安全检测、消失诅咒、死亡事件顺序、背包扩展/饰品槽、物品复制、战利品数据包、多人 boss 结算、Hardcore。原 SoulLink 安全性只检查两格空间/地面，不等同于无岩浆/刺/敌人的安全营地，需补验证。**L；全保物与 XP 必须拍板。**

## 8. 护甲与附魔：现在是看不见的 MC 防具，不是碎片替代

**现状。** [SpiritVanillaLayers.java:69](../../src/main/java/cn/wwl/orimod/client/render/SpiritVanillaLayers.java#L69) 明确只隐藏 `HumanoidArmorLayer`，保留装备属性。没有精灵专属护甲/Protection 伤害模型。碎片仅精灵形态装备生效：[Shard.java:22](../../src/main/java/cn/wwl/orimod/progression/Shard.java#L22)。上限 1→4 槽由技能进度决定：[SpiritProgression.java:23](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L23)。现有 RESILIENCE 不是减伤，所以直接停用护甲并没有等价替代。

**Ori 对照。** BF 用生命成长、能力树防御升级；WotW 用有限槽碎片组合，例如 Resilience 减伤，并没有铁甲→钻石→下界合金装备阶梯。[Xbox 官方 WotW 说明](https://news.xbox.com/en-us/2020/03/11/ori-and-the-will-of-the-wisps-available-now/)、[Resilience](https://oriandtheblindforest.fandom.com/wiki/Resilience)

**建议。** 默认先保留 MC 防御数值并在面板明确“外观隐藏，装备有效”；以碎片增加动作/策略，而非立即剥夺旧存档已投入的附魔装备。若用户要纯 Ori 规则，单列防御重平衡：只在精灵形态替换防御贡献、保留物品本身，重新校准原版怪/Boss 伤害与生命格、提供替代减伤，并决定水下速掘/冰霜行者/灵魂疾行等功能附魔是否保留。不是移除 `ARMOR` attribute 就算做完。

**风险。** 不能通过强制脱装备处理绑定诅咒；模组护甲的飞行/氧气/套装技能未必来自原版护甲属性；Protection 与减伤碎片叠加可过强；PvP、装备成就、数据包 predicate 与装备耐久仍有意义。新旧世界不能静默采用不同伤害公式。**维持 S，替代 L；是否让采矿附魔退出精灵成长主线需拍板。**

## 9. 冲刺与体力：不是“再加一个精灵耐力条”

**现状。** [SpiritControls.java:181](../../src/main/java/cn/wwl/orimod/client/SpiritControls.java#L181) 的 `feel` 独立控制普通地空速度，[FeelTuning.java:207](../../src/main/java/cn/wwl/orimod/movement/FeelTuning.java#L207) 目标 `.32` 格/tick（6.4 格/秒），不按 `isSprinting()` 切档；末尾清原版前进加速度。它已不是普通 MC 步行/疾跑速度模型。`MC:client/player/LocalPlayer.java:1106` 的冲刺标志/食物门槛仍在，水中等不由 `canMove` 接管的状态仍受影响。**不能说“食物不足必然让当前 Ori 地面跑不快”**，也不能把 sprint 动画标签当作速度证据。

Dash 是 [SpiritMovement.java:256](../../src/main/java/cn/wwl/orimod/movement/SpiritMovement.java#L256) 的冷却/空中次数控制，不扣能量；[MovementRules.java:10](../../src/main/java/cn/wwl/orimod/movement/MovementRules.java#L10) 为 5 tick 动作、12 tick 冷却。

**Ori 对照。** 核心是连续跑跳、空中动作次数和特定能力消耗，并没有通用跑步耐力条。BF 普通 Dash 与消耗能量的 Charge Dash 要区分；不能把“冲刺”都归为一种耗能动作。[发行商操作手册](https://manuals.thqnordic.com/OriAndTheBlindForest/Ori_PC_QuickstartGuide_online.pdf)、[BF 能力树](https://oriandtheblindforest.fandom.com/wiki/Ability_Tree_%28Ori_and_the_Blind_Forest%29)

**建议。** 保持无体力的基本跑跳；第 3 项落地时统一冲刺标志、饥饿消耗和水中门槛。不要为了让 Ctrl 有作用再造更快一档。测试速度/缓慢药水、蜘蛛网、灵魂沙和其他模组速度 attribute：固定 `TOP_SPEED` 未读取移动速度 attribute，存在“不完全接受原版速度增益”的风险，但本轮未实测最终 travel 效果，不能据此宣布所有药水已失效。

**风险。** 本地预测、原版 sprint packet/FOV、游泳条件、斩击前冲和外部击退不能打架；不能以把所有速度效果清零来修手感。无需新存档资源。**M；保持无耐力无需大决策，新速度档/属性换算需定规则。**

## 10. 溺水与水中移动：原作也有氧气，别一觉醒就免溺水

**现状。** [WaterMovement.java:20](../../src/main/java/cn/wwl/orimod/movement/WaterMovement.java#L20) 仅对精灵 + WATER_DASH + 水中生效，[WaterMovement.java:34](../../src/main/java/cn/wwl/orimod/movement/WaterMovement.java#L34) 每服务端 tick 补至最大空气。未解锁仍有 MC 溺水；药水、呼吸附魔等原版路径未被移除。Water Dash 还同时承载灵砂钻行资格：[SpiritSandBlock.java:29](../../src/main/java/cn/wwl/orimod/block/SpiritSandBlock.java#L29)。

**Ori 对照。** BF 净水后可以游泳但仍需换气；Water Breath 才解锁清水呼吸。WotW 把 Water Breath、Swim Dash 与 Burrow 作为不同能力，不能把“净化水”“无限空气”“水冲刺”混为一件事。[BF 危险物](https://oriandtheblindforest.fandom.com/wiki/Dangers_%28Ori_and_the_Blind_Forest%29)、[Water Breath](https://oriandtheblindforest.fandom.com/wiki/Water_Breath)、[WotW 能力表](https://oriandtheblindforest.fandom.com/wiki/Ori_and_the_Will_of_the_Wisps)

**建议。** 保留早期氧气危险，HUD 做清楚；不优先改溺水伤害倍率。后续若精确复刻，拆独立 Water Breath/Burrow，并让旧档已学 WATER_DASH 的玩家继承原有三种权益，不剥夺已能走的路线。世界普通水保持普通水，已有 `gloom_water` 表达局部腐化，不把所有海洋变成剧情毒水。

**风险。** 拆能力涉及 bitmask、解锁数/第四碎片槽、协议和老存档，工作量大于改一个判断；水下生存模组与自定义液体需要定义范围。**保留 S，拆分 M；拆技能/新增价格需拍板。**

## 11. 原版经验与灵光：保持两本账更合理

**现状。** [SpiritProgress.java:8](../../src/main/java/cn/wwl/orimod/progression/SpiritProgress.java#L8) 独立存 `light`，上限 1,000,000；[SpiritProgression.java:181](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L181) 给被已觉醒玩家击杀的 Enemy **追加**灵光掉落，不替换原版 XP。拾取灵光走 `collectLight`，不会 `giveExperiencePoints`；技能/碎片购买扣灵光。原版经验仍用于附魔、铁砧、经验修补，死亡规则也不同。

**Ori 对照。** BF 灵光攒成技能点；WotW 灵光直接是商人货币。两代也不是一个机制。[Spirit Light](https://oriandtheblindforest.fandom.com/wiki/Spirit_Light)

**建议。** 延续 WotW 式灵光货币，MC XP 保留为 MC 工艺经济。HUD 区分命名/颜色/用途；不要把 XP 球换皮后既加 XP 又加同额灵光，也不默认双向兑换。否则村民交易、熔炉与 XP 农场会替代全部 Ori 探索奖励，且能间接把死亡保留的灵光换成可修装备的经验。

**风险。** 取消 XP 会破坏 Mending、铁砧和等级条件数据包；自动汇率会创造循环套利。现有两本账无迁移需求。**S（显示）；合并 L 且需要用户明确选择，我不推荐。**

## 12. 原版剑/斧冷却与灵刃：不应全局取消攻击冷却

**现状。** [SpiritCombat.java:81](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L81) 只拦主手 `SPIRIT_BLADE` 的原版攻击；[SpiritCombatClient.java:23](../../src/main/java/cn/wwl/orimod/client/SpiritCombatClient.java#L23) 也只接管该物品。剑/斧仍走 `MC:world/entity/player/Player.java:1222` 的攻击强度、暴击、附魔/横扫；`ATTACK_SPEED` 决定冷却。灵刃已是时间窗三连，固定基础伤害 4/5/7、扇形、视线、单挥单目标去重、前冲/空斩，不是“原版剑换皮”：[SpiritCombat.java:28](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L28)、[SpiritCombat.java:176](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L176)。

**Ori 对照。** WotW Spirit Edge/Smash 是主动武器动作，伤害服从动作节奏，而不是 MC 蓄满攻击强度条。BF 主攻则是需输入的 Spirit Flame，不该把 BF/WotW 混成“所有物品都无限连点”。[Xbox WotW 官方说明](https://news.xbox.com/en-us/2020/03/11/ori-and-the-will-of-the-wisps-available-now/)、[Spirit Smash](https://oriandtheblindforest.fandom.com/wiki/Spirit_Smash)

**建议。** 默认保持原版剑斧、盾、弩的原生机制，把灵刃作为 Ori 战斗入口。原因是 MC 剑斧还有屠宰/PvP用途，而 [CombatTargets.java:16](../../src/main/java/cn/wwl/orimod/combat/CombatTargets.java#L16) 的灵武器范围攻击刻意保护玩家、村民、宠物与非敌对动物。统一接管会连这些用途一起改变。

若用户要“精灵拿任何剑都 Ori 式挥砍”，作为可选武器适配层另立项：每类武器配置前摇/收招、倍率、附魔触发、盾牌克制和目标规则，动作时序仍在服务端。不能只把 `getAttackStrengthScale` 恒置 1，否则高伤斧连点 DPS 失控。灵刃直接 `target.hurt` 也不能宣称所有剑附魔自然生效，要明确支持范围。

**风险。** 战斗模组、攻击事件、伤害属性、附魔 data components、PvP、杀敌成就、武器统计、友军与宠物误伤。**保留 S；泛化到剑斧 L，需拍板。**

## 13. 遗漏但重要：灵武器为什么还会用坏、丢失？

**现状。** [SpiritBladeItem.java:13](../../src/main/java/cn/wwl/orimod/item/SpiritBladeItem.java#L13) 继承铁质 SwordItem；[SpiritCombat.java:197](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L197) **每成功命中一个目标**耗 1 耐久，多目标一刀多耗。弓注册 384 耐久，每次射击耗 1：[OriItems.java:16](../../src/main/java/cn/wwl/orimod/item/OriItems.java#L16)、[SpiritCombat.java:129](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L129)。锤/矛没有这套耐久。面板刀/弓领用有永久 claimedWeapons 回执：[SpiritProgression.java:108](../../src/main/java/cn/wwl/orimod/progression/SpiritProgression.java#L108)，丢了不等于可重新免费领用，仍可按现有配方重新制作。

**Ori 对照。** WotW 的灵武器是已学能力，不是会用坏掉在地上的工具；Spirit Smash 普通攻击不耗能。本模组锤需蓄力并扣 8 能量：[SpiritHammerItem.java:30](../../src/main/java/cn/wwl/orimod/item/SpiritHammerItem.java#L30)，这是既有设计偏差，不是从 MC 继承的规则。[Spirit Smash](https://oriandtheblindforest.fandom.com/wiki/Spirit_Smash)

**建议。** 在“灵武器作为能力”的方向上统一刀/弓不损坏；ItemStack 仍作为 MC 快捷栏的持有凭证，保留原版工具耐久。死亡/丢失后有受校验的重新凝聚方式，后续再考虑能力轮盘。旧刀/弓的耐久与附魔数据保留，不直接替换物品。锤是否改为无能耗慢挥要另测核心净化、裂壁和战斗强度，不能搭便车改。

**风险。** 重领凭证可丢给别人/交易/刷配方材料，需要决定凭证是否绑定及能否回收；经验修补/耐久附魔投入价值变化。不能每次登录送一把导致库存无限增长。**M；不损坏、重领和锤耗能各需拍板。** 这一项比全局改剑斧冷却更接近原作且范围可控。

## 14. 受击、击退、接触伤害和环境危险

**现状。** [SpiritCombat.java:202](../../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java#L202) 在外部攻击实际伤害后中断动作；[DamageInterrupts.java:12](../../src/main/java/cn/wwl/orimod/combat/DamageInterrupts.java#L12) 排除自身/单纯环境掉血。R112 定向 hurt 只是表现，不改变 `MC:world/entity/LivingEntity.java:1190` 的伤害冷却/更大伤害差额结算。普通 Dash 没有注册通用免伤；明确无敌是复苏仪式特例。腐光荆棘每实体共用 15 tick 接触间隔：[GloomThornsBlock.java:16](../../src/main/java/cn/wwl/orimod/block/GloomThornsBlock.java#L16)；腐光水用原版受伤冷却：[GloomWaterBlock.java:36](../../src/main/java/cn/wwl/orimod/block/GloomWaterBlock.java#L36)。

**Ori 对照。** 原作危险有接触伤害、持续危险与致命陷阱之分，受伤反馈和重试位置组成节奏；不是“精灵免疫所有环境”，也不能从高速 Dash 推导出任何时候都应该无敌。[BF 危险物](https://oriandtheblindforest.fandom.com/wiki/Dangers_%28Ori_and_the_Blind_Forest%29)、[WotW 敌人](https://oriandtheblindforest.fandom.com/wiki/Enemies_%28Ori_and_the_Will_of_the_Wisps%29)

**建议。** 先实测群殴/箭雨/刺丛能否脱离，必要时只对精灵在特定动作中增加明确、有限的受击恢复或不叠加击退窗口。保留原版熔岩、窒息、冻结、燃烧、毒、凋零语义；为 Ori 遗迹设计局部危险和可靠安全点，不把所有敌人重叠都改成额外触碰扣血。自定义接触怪避免同时叠 MC 近战与接触两次伤害。

**风险。** 免伤不能挡 `/kill`、虚空或 BYPASSES 标签；PvP、盾牌、图腾和其他模组伤害冷却是高风险交界。大改免伤需服务端可重复测试，不能仅凭动画看起来僵硬就改数值。**M；新增无敌窗口/致命陷阱规则需拍板。**

## 15. 灵泉、床、回城与失眠：保留 MC 世界时间

**现状。** 灵泉 [SpiritWellBlock.java:22](../../src/main/java/cn/wwl/orimod/world/SpiritWellBlock.java#L22) 记录发现，普通使用恢复、潜行使用传送；[SpiritWell.java:60](../../src/main/java/cn/wwl/orimod/world/SpiritWell.java#L60) 从当前泉到同维度按 key 排序的下一个泉，不是任意选目的地，也不设置 Soul Link/床复活点。睡眠/失眠未有精灵规则替换；[SpiritAnimationRules.java:37](../../src/main/java/cn/wwl/orimod/client/render/SpiritAnimationRules.java#L37) 的 sleep 只提供动作。潮夜依赖真实 `dayTime`，跳到白天会结束：[GloomTide.java:68](../../src/main/java/cn/wwl/orimod/world/GloomTide.java#L68)。

**Ori 对照。** BF DE 的泉间旅行与安全点能缩短回头路；原作没有 MC 按人数睡觉跳全世界夜晚的机制。[Spirit Well](https://oriandtheblindforest.fandom.com/wiki/Spirit_Well)

**建议。** 增加已发现泉的目的地选择，提高长途往返价值，能配合羽毛替代鞘翅的决定。新泉可由玩家明确设为安全复活点，不能一碰就覆盖私人 Soul Link。床继续服务多人跳夜；主动在泉休息可考虑重置精灵的原版失眠计时，让探索不必反复铺床，但不要替其他玩家跳时间、不要自动让潮夜胜利。

**风险。** 删掉/堵塞的泉、未加载点、同维度限制、传送冷却、探索发现记录、换维度和重生锚；失眠重置影响幻翼膜来源，可保留原版获取途径。**M（目的地/复活点），S（明确休息）；失眠豁免/覆盖存档点需拍板。**

## 16. 挖掘、建造、地形穿行：应保留的 MC 核心

**现状。** [SpiritAnimationInteractionMixin.java:30](../../src/main/java/cn/wwl/orimod/mixin/SpiritAnimationInteractionMixin.java#L30) 仅观察成功挖掘/放置来播动画，没有替换破坏速度或工具规则。普通世界仍可采矿、放块、水桶、红石。裂壁是局部能力门：[CrackedRuinBlock.java:14](../../src/main/java/cn/wwl/orimod/block/CrackedRuinBlock.java#L14)；[SpiritSandBlock.java:18](../../src/main/java/cn/wwl/orimod/block/SpiritSandBlock.java#L18) 明确仅灵砂可钻，不穿普通沙土。

**Ori 对照。** Ori 是作者布置的能力关卡，BF 手册明确 Bash/Charge Flame 等与障碍交互；WotW Burrow 针对可钻介质。MC 的自由改造地形没有一对一对应。[发行商手册](https://manuals.thqnordic.com/OriAndTheBlindForest/Ori_PC_QuickstartGuide_online.pdf)、[WotW 能力表](https://oriandtheblindforest.fandom.com/wiki/Ori_and_the_Will_of_the_Wisps)

**建议。** 全局保留建造/挖矿，把“必须会某技能”限定在明示材质和试炼内。不要为了秘室不可绕过，就把所有精灵变成冒险模式或全遗迹永久不可破坏；也不把 WATER_DASH 自动扩成穿越所有沙/细雪。相关关卡若要防放块绕过，单独做范围清楚的试炼契约与安全出口。

**风险。** 领地保护、工具模组、采矿疲劳、掉落表、玩家建筑、自动化。无存档破坏是首要边界；有权限玩家仍遵守既有 OP/创造调试规则。**维持 S，局部试炼 M；全球禁建/穿地 L 且需拍板，我不推荐。**

## 17. 骑乘、激流、末影珍珠、盾与其他物品：不是每项差异都要删除

**现状。** [SpiritMovement.java:31](../../src/main/java/cn/wwl/orimod/movement/SpiritMovement.java#L31) 遇骑乘、水、熔岩、攀爬方块、鞘翅、创飞会交回相关原版移动；[SpiritAnimationRules.java:19](../../src/main/java/cn/wwl/orimod/client/render/SpiritAnimationRules.java#L19) 为激流、划船、坐姿单独选表现。末影珍珠/投掷药水只是成功使用后播 throw：[SpiritAnimationInteractionMixin.java:42](../../src/main/java/cn/wwl/orimod/mixin/SpiritAnimationInteractionMixin.java#L42)。盾的 R112 修正仅是握持方向，未把 BLOCK 变成 Ori 招架。

**Ori 对照。** Ori 的主要跨越工具是自身技能；没有 MC 的自由骑马、铁路物流、珍珠、通用持盾战斗等完整体系。这里“没有直接对应”不等于“必须删掉”。WotW 的 Ku 段落也不能拿来论证应该删除所有载具。[WotW 能力概览](https://oriandtheblindforest.fandom.com/wiki/Ori_and_the_Will_of_the_Wisps)

**建议。** 这些保留为 MC 工具，解决输入/碰撞冲突即可。关卡里若珍珠/载具能跳过已声明的试炼目标，限制该试炼，而非世界所有精灵；物品使用速度惩罚按用途区分，不能一律拿掉举盾减速。动画已变 Ori 不代表三叉戟可以免费当 Launch。

**风险。** 挂载、载具拥有权、坐骑生命 HUD、传送事件、珍珠成就、投掷药水数据包、自动化铁路。**保留 S，试炼限制 M；全局禁用需拍板且不推荐。**

## 18. 范围外但会影响“像不像”的两个现存混合设计

这两项不是原版 MC 继承，单列以免被当作本轮必须改。

**同伴自动攻击。** [SpiritFlame.java:20](../../src/main/java/cn/wwl/orimod/combat/SpiritFlame.java#L20) 目前 6 格范围、14 tick 冷却、2 伤害，不需玩家按攻击；没有敌人时打视线内容器。BF 官方说明的 Spirit Flame 需反复按键。可讨论把主动输入与自动瞄准分开，同时保留已验收的容器手感；但这是推翻早先确认过的辅助战斗选择，需要单独批准。[BF 手册](https://manuals.thqnordic.com/OriAndTheBlindForest/Ori_PC_QuickstartGuide_online.pdf) **M；需拍板，不能以修 MC 规则为名暗改。**

**移动状态外的减速兼容。** 自定义地面 feel 补偿原版摩擦（[SpiritControls.java:186](../../src/main/java/cn/wwl/orimod/client/SpiritControls.java#L186)、[SpiritControls.java:221](../../src/main/java/cn/wwl/orimod/client/SpiritControls.java#L221)），固定目标速度与冰/药水/模组属性可能有非直观组合。Ori 的精确停步是目的；MC 的滑冰、减速药水、击退也应有约定。建议先实测并列效果表，再决定保留哪些，不把 `slow` 标签随便翻译成“角色不够灵活”。**M（测试后定）；涉及外部状态效果需拍板。**

## 推荐顺序与第一批边界

**第一批：羽毛规则与读数统一（建议先做，M）。**

1. 用户先选择第 1 项 A/B；我推荐 A。精灵默认羽毛、去掉鞘翅抢空中跳，明确火箭的行为，验证人形远行/变身交接/创飞/多人。不是只换模型。
2. 生命/能量主 HUD，保留仍有效的饥饿/护甲/XP 辅助读数；不改 HP/货币存档。保留原版 HUD 兼容选项。
3. 锁住已有免摔伤、Water Dash 呼吸规则；顺带显示其解锁前后差别。这里没有新的大玩法，不重复立项。

这批能马上回应用户的鞘翅例子，又不先拆生存补给。验收至少包含精灵正常二段跳/羽毛、风口升起、羽毛态火箭不扣、人形鞘翅推进、空中变身、低能量与吸收/中毒/氧气 HUD、未解锁水呼吸会溺水。

**第二批：完整恢复循环（L，必须一套交付）。** 生命/能量补给球 + Regenerate + 灵泉休息/目的地选择 + 精灵停用饥饿代谢/食物回血 + 明确的自动回能兜底。先保证老世界、和平和远途可持续，再去掉饥饿主显示。不要分成“先饿不死，几轮之后再补治疗”。

**第三批：死亡重试与灵武器（M–L）。** 先形态/安全点恢复和灵武器不损坏/可重凝聚，保物政策由用户决定。不要同时重写所有背包、墓碑、XP 和世界存档。

**第四批：防御与战斗经济（L，可选）。** 要不要让碎片正式替代护甲、恢复 Resilience 原作减伤、锤取消能耗/新增方向攻击。做之前测裸精灵与现有装备的 TTK/承伤，安排旧档过渡。原版剑斧仍建议保留本职；泛化 Ori 连击优先级低于上述项。

**明确不建议第一批做：** XP 与灵光合并、所有原版武器无冷却、所有环境免伤、所有方块禁止破坏、全世界死亡回档、所有沙/细雪可穿行、只隐藏饥饿却保留其伤害。这些不能因为“原作没有 MC 某机制”就直接删除。

需要用户回答的主要取舍只有五组：**羽毛与远行 A/B；精灵是否退出饥饿循环；保留多大自动恢复；死亡保留哪些物品/XP；护甲是否继续提供战斗数值。** 其余可在既定方向里实现和验证。

## 与 Claude 对照后的收敛（仍待用户决定，不是开工指令）

以上保留 Codex 最初的独立判断与排序，便于追溯。Claude 读稿后的调整已接受，后续排期以本节为准：

- **第一批**：鞘翅 A（精灵羽毛、人形保留原版远行）与羽毛输入/物理规则；生命/能量主 HUD 并保留有效的辅助指标；死前为精灵则复活仍为精灵；灵刃/灵弓停止损耗耐久，与锤/矛一致；灵泉目的地选择，作为 A 的往返旅行补偿。
- 将死亡形态保持作为 **S** 的独立小改动，不夹带背包/XP 保留、重生点优先级重写或世界回档。仍要验证安全碰撞与动作清理。刀/弓只先取消耐久消耗，保存原有物品及数据；**重凝聚/重领规则不进入首批**。
- **给用户的可选加项**：已学 GLIDE 时，穿戴鞘翅可让羽毛横移更快/下降更慢，仅穿戴期间生效。这是对 MC 装备价值的折中，不恢复俯冲换速或烟花推进，也不冒称原作规则。是否采用与具体增益尚未决定。
- **第二批**：饥饿 + 回血 + 回能的完整恢复循环，等待用户决定精灵饥饿和自动恢复取舍。
- 共识维持：摔伤已解决；XP/灵光分账；原版剑斧保留；护甲默认保留。死亡保物、护甲替代是用户单独选择的方向。

Claude 将向用户收集决定，再另行派发实现任务。本轮仅新增本讨论文件；未改运行时代码、资产、配置，未启动客户端/Gradle，未做 git 写操作。


## R113 用户决定与第二批恢复设计（2026-10-22，后续变更以本节为准）

前文是讨论过程。用户最终选择按形态区分：**人形鞘翅完全原版，精灵形态改羽毛**，不是全局删除原版飞行。`minecraft:elytra` 身份与物品数据不变；图标/默认名称按本地玩家形态，背部翅膀按被渲染者形态。人形烟花、耐久、破损限制保留；精灵仍需 GLIDE，穿戴升级到横向上限 0.65、下降钳位 −0.07（基础 0.5/−0.12，均为 travel 前的格/tick 参数）。从原版飞行切精灵有 40 tick 接管窗口，速度渐退至羽毛上限；随后按住跳跃且拥有技能才继续。反向变身只进入下落，不自动开鞘翅。创飞不接管。

第一批同时交付真实生命/能量 HUD、死亡时 FORM 保留、刀弓正常损耗为零、同维度已发现灵泉目的地选择。**背包/XP 仍照原版掉落、护甲数值保留、XP 与灵光分账**。第二批A恢复循环已获用户批准并进入实现；B轮盘在A测试检查点之后接入。

### 第二批最终数值（已批准并实现）

| 项目 | 建议数值/语义 | 接入点 |
| --- | --- | --- |
| 精灵饥饿 | 暂停同一份 FoodData 的 food/saturation/exhaustion/tickTimer；既不耗饥饿、不饿伤，也不吃饱自然回血。变人形后继续原值；不通过把 food 强设 20 来伪装 | 在 Player 的 FoodData.tick 与 causeFoodExhaustion 调用处按 FORM 跳过；FoodData.addExhaustion 的其他模组直接调用需审查。只拦玩家所属 FoodData，不影响其他玩家 |
| 营养与药效 | 精灵进食不改变冻结营养数值、不触发食物回血；药水、金苹果的状态效果仍保留，金苹果的营养增量不改变冻结快照。人形全原版 | 食物使用/finishUsingItem，与 FoodData.eat 的拥有者关联；同时测试饥饿药效、和平自然回血、切形态期间正在吃东西 |
| 基础 Regenerate | 觉醒即有；引导 20 tick，成功完成时原子扣 10 能量、回 6 HP（3 个生命格），不超过 maxHealth；未损血不启动。实际受到 >0 伤害中断；移动可慢行，切形态、换维度、死亡、退出、主动松键立即取消。中断无治疗、无扣费；按住可逐段重复，受伤中断后需松键重按 | 新服务端会话与纯 refusal；动画用 cast_charge/cast_release；伤害事件单独监听，不依赖当前仅 RESILIENCE 写入的 lastHurt。网络按键保活+明确停止，20 tick 超时兜底，不用客户端自由计时发 heal |
| 旧档补发 | 作为 awakened 派生基础能力，不花灵光、不占现有 14 技能的学习计数/碎片槽判定；若选用新 ability 位，登录/觉醒迁移必须只补此位 | SpiritProgression.awaken/login、Ability 面板；不能让额外基础能力提前开启第4槽 |
| 被动回能 | 已觉醒的两种形态且没有持续耗能：最后支出后等 80 tick，以 2 能量/秒恢复，**只补到 20**；RECOVERY 可使兜底速度变为 3/秒，不抬高上限。达到20后不再自动回满；人形不提供快速回满的切形态绕过 | SpiritProgression.tick 替换现有5/7.5能量每秒到满的逻辑；Flash/Regenerate 会话中暂停。人形同样上限20，以免切形态套利；不改变人形FoodData |
| 敌人补给预算 | 仅敌对生物的有效死亡，沿用现有有觉醒玩家归属的掉落条件。n=clamp(ceil(maxHealth/20),1,4)：每组独立50%掉生命球、50%掉能量球；每颗2 HP/5能量，最多8个实体/死亡，20HP敌人期望1HP与2.5能量。不按当前缺口增发，不按多人复制 | SpiritProgression.drops 增加独立 RecoveryOrb 实体，原 Spirit Light 与 XP 掉落保持原量；高血量首领也有上限 |
| 补给吸附/消费 | 觉醒精灵4格内短程吸附（0.25格/tick，出生10tick后可收集），接触时服务端检查剩余需求并一次消费；已满则不吸附该类，留给队友。生命球不经 SpiritLight 的 LIFE_HARVEST 路径，防重复治疗；实体保存kind/已存活tick，1200个有效tick后消失，区块存取不重置寿命 | 新 entity 的 server tick/playerTouch，共用原子 consume；不得同时走 ItemEntityPickup 与磁吸两套增量。死亡掉落和玩家主动丢弃分开 |
| 灵泉 | 现有1200 tick补满冷却、生命/能量补满保留；目的地选择使用第一批结果 | SpiritWell.refill，不改变传送发现与安全校验 |

### 20 能量兜底的无软锁核算

- 蓄力灵焰每次10能量、核心受12伤；40血核心可分4次打完，总耗40，核心不会因间隔恢复。20上限允许两次一组，缺口可等兜底或拾球，不要求连续60能量。
- 光爆15、灵矛12、灵锤8，都低于20；裂壁/灵灯能用蓄力灵焰一次10，猛击点灯本身零耗能。20不是“只能治疗、不能做谜题”的死循环。
- 灵闪每tick0.3，净化水起点age40，需要约12.3能量，20可持续约66tick；连续光照净化可完成。核心每20tick受4，单次满兜底可打约12–16血，再次恢复后继续。零能量恢复到20需14秒（4秒延迟+10秒恢复）；从零恢复到净化所需13约10.5秒。
- **权衡必须明说**：低位自动回能+Regenerate意味着玩家可在安全处缓慢无限恢复生命，这是避免旧世界/和平模式断粮的保底，不是完全照搬 Ori“能量必须拾取”。若不接受，需要可再生环境补给与保底路径整包替代，不能仅把20改成0。
- 测试应从0能量、无敌人、无道具、旧已觉醒存档出发，实际完成核心、光爆点灯、灵闪净化水及一次Regenerate；覆盖受伤中断、丢包/延迟、双人同帧触球、满血满能、读档/卸载/死亡与切形态。FoodData 的四字段在长时间精灵移动前后逐字节相同，人形恢复后正常变化。

以上数值已于R113第二批确认。补给球仅为额外奖励，主要保底是精灵脱伤回血：最后实际伤害（含吸收值被扣）后160tick，0.5HP/秒，只补到最大生命80%；人形生命恢复全原版。80%不限制碎片、药水、Regenerate、球、灵泉或其他模组显式治疗。和平难度原版的额外回血/回食物分支对精灵也跳过。人形FoodData恢复原值继续正常运行。

### B：技能轮盘（A检查点全绿后接入）

G短按在松开时开面板；单调时钟满250ms进入轮盘，松G关闭且不补开面板。轮盘冻结鼠标视角，悬停/滚轮选技能，Z/X/H按下绑定所选，不施放。失焦/换世界取消手势。技能目录初始Light Burst/Charge Flame/Flash/Regenerate；前3项替换原固定键映射，保留玩家改键和Z/X/H肌肉记忆；服务端持久槽位、死亡复制、只同步本人，未学拒绝、重复技能移动。Regenerate由觉醒派生，首次填空槽，否则给一次换槽提示。三槽HUD使用Claude提供的32×32技能图标，缩放16px；不增加鼠标施法或武器槽。


审查补充：精灵普通食物在canEat处拒绝使用、不消耗；canAlwaysEat食物保留（含金苹果药效），营养仍冻结。被动生命恢复尊重naturalRegeneration；主动治疗与球/泉水不受该规则限制。技能绑定另外持久保存已见技能集合，后学会的新技能仅填第一个空槽并提示绑定键；已占槽不替换，主动移出的旧技能不在下次登录自动回来。
