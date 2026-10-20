# R96–R99 路线（灵灯 · 同伴「萤」· 灵闪 · 新碎片）

2026-10-20 起草（Claude 规划，Codex 实现）。R95 之前主要在打磨动画手感；[ORI_PARITY](ORI_PARITY.md#剩余缺口按价值排序) 剩余缺口第 1、2、3、4 项都没动。本路线按“先小后大、后一轮复用前一轮接口”排序，四轮各自独立可发布。

## 通用约定（每一轮都适用）

- **分工：** Codex 写代码、跑构建和测试；Claude 审查 diff、提交并推送 `main`（Codex 不做任何 git 写操作）。每轮交付时 Codex 报告：改动文件清单、JUnit / GameTest 数量（基线 R95：581 / 236）、失败项原文、没做或偏离方案的地方。
- **构建：** `./gradlew build runGameTestServer --console=plain`。GameTest 报告在 `build/test-results/gametest.xml`。
- **代码风格：** 跟周围代码一致。新类、新常量在 Javadoc 开头写 `R9x:`，说明对应的原作出处和 MC 适配；可调参数写成 `public static final` 常量，玩法规则抽成纯静态函数方便 JUnit 测。不引入新依赖。
- **语言文件：** `zh_cn.json` 与 `en_us.json` 同步加键；描述写具体数字（参照已有碎片描述的写法）。
- **存档兼容：** 旧存档必须能直接读。新增持久化优先放进已有的 `SpiritProgress.rewards`（`Map<String,Integer>`），不改 codec 字段；必须改 codec 时用 `optionalFieldOf` 加默认值。
- **网络：** 客户端只发输入，伤害、消耗、冷却、目标都由服务端判定（与 `CombatInputPayload` 一致）。
- **文档：** 每轮在 [WORK_STATUS](WORK_STATUS.md) 顶部加一条（格式照 R95）；更新 [ORI_PARITY](ORI_PARITY.md) 对应行和“剩余缺口”；[TESTING](TESTING.md) 记录新测试。没在真实客户端看过的视觉效果写“未实机验证”，不写通过。
- **资源：** 纹理、模型、声音全部原创或复用原版 MC 资源，不用 Moon Studios 原件。纹理可以用 Python 脚本程序生成 16×16 PNG，脚本放 `scripts/art/`。

---

## R96 灵灯（Spirit Lantern）—— ORI_PARITY 缺口 3

**目标：** 把现在的 Bash 锚点（`orimod:bash_anchor`，一个恒定 12 级光的简单方块）做成原作那种灵灯：平时是微光的悬挂灯笼，被猛击、光爆、蓄力灵焰打到时亮起几秒。顺便让玩家能合成灵灯，自己铺 Bash 路线。

### 方块

- 新类 `block/SpiritLanternBlock extends Block`，**注册 ID 保持 `bash_anchor` 不变**：遗迹 NBT、`RuinKind` 的关键锚点检查、`SpiritTargets`（33、52 行）都按这个 ID 认方块。只把 `OriBlocks.BASH_ANCHOR` 从 `registerSimpleBlock` 改成 `register(..., SpiritLanternBlock::new)`，类型改为 `DeferredBlock<SpiritLanternBlock>`。
- 方块状态加 `BooleanProperty LIT`，默认 `false`。NBT 模板里没有这个属性的方块会取默认值，不需要重建遗迹模板；`scripts/check-ruins.py` 的哈希不应变化，请跑一次确认。
- 亮度：未点亮 7，点亮 15（原来恒为 12）。洞穴遗迹会因此变暗一点，这是有意的：点亮灯笼要变成一件有反馈的事。
- 碰撞保持 `noCollission`；轮廓形状（`getShape`）给一个居中的小灯笼体积（约 6×10×6 像素），方便瞄准和挖掘。
- 公共 API：`public static boolean ignite(ServerLevel level, BlockPos pos, int ticks)`：不是灵灯返回 false；已点亮则只延长；设 `LIT=true`，`scheduleTick(pos, this, ticks)`；`tick()` 里熄灭。常量 `LIT_TICKS = 100`。
- `public static int igniteAround(ServerLevel level, Vec3 centre, double radius, int ticks)`：在半径内点亮所有灵灯，返回点亮数。用 `BlockPos.betweenClosed` 扫描包围盒，按距离过滤。R98 灵闪也要用。

### 触发点

| 来源 | 位置 | 行为 |
| --- | --- | --- |
| 猛击从灵灯弹出 | `SpiritMovement` / `BashSession` 里释放 Bash、目标是方块锚点的分支 | `ignite(pos, LIT_TICKS)` |
| 光爆爆炸 | `LightBurst` 的爆炸处理 | `igniteAround(爆点, LightBurst.RADIUS + 1, LIT_TICKS)` |
| 蓄力灵焰释放 | `ChargeFlame.release` | `igniteAround(玩家, ChargeFlame.RADIUS + 1, LIT_TICKS)` |

### 表现

- 模型：两个方块模型 `bash_anchor.json`（暗）和 `bash_anchor_lit.json`（亮），blockstate 按 `lit` 切换。外形：上方挂钩 + 四根细框柱 + 中心发光核心方块，使用 `cutout` 渲染类型。纹理：程序生成，框为暗紫水晶色，核心未点亮为淡蓝 `#9EC8FF`，点亮为近白 `#F2F8FF`。生成脚本 `scripts/art/build_lantern_textures.py`。
- 物品模型继承暗模型。
- 点亮瞬间：在 `SpiritFxKind` 加 `LANTERN_IGNITE`（一次性 burst，冷白蓝微粒向外扩散 0.4 s），声音用原版 `block.amethyst_block.chime`，音高约 1.5。
- 点亮期间的柔光：客户端在 `SpiritAura.render` 同一批次里，对玩家 32 格内 `LIT=true` 的灵灯画一个呼吸的柔光 billboard（半径 0.9，alpha 0.25）。扫描要有上限：每 10 tick 用已加载区块做一次范围扫描并缓存位置列表，最多 32 个。

### 配方

- `data/orimod/recipe/bash_anchor.json`：有序配方，1 个原版灯笼 + 2 个紫水晶碎片 + 1 个荧石粉 → 2 个灵灯。
- 配方书解锁进度：拿到紫水晶碎片即解锁（照 R17 已有配方的写法）。2026-10-20 实现时经 Claude 调整：灵光会自动入账，不适合作为工作台材料，改用荧石粉。

### 测试

- JUnit：`SpiritLanternBlockTest`，测点亮时长常量、亮度映射（7 / 15）。
- GameTest（`ParityGameTests` 或新文件 `LanternGameTests`）：
  1. `ignite` 后 `LIT=true`，100 tick 后熄灭；点亮中再次 `ignite` 会延长熄灭时间。
  2. 光爆在灵灯 2 格内爆炸会点亮它，5 格外不会。
  3. 蓄力灵焰释放点亮半径内的灵灯。
  4. 猛击从灵灯弹出后它被点亮（复用 `BashGameTests` 的夹具）。
  5. 点亮状态的灵灯仍然是合法 Bash 锚点（`SpiritTargets` 不受 `LIT` 影响）。
  6. 配方：工作台合成得到 2 个灵灯。
- 跑一次 `RuinBoundaryGameTests` / `WorldgenAudit` 确认遗迹关键锚点检查不受影响。

---

## R97 同伴「萤」（Glimmer）—— ORI_PARITY 缺口 1（V5）

**目标：** 现在的同伴是一个按固定圆轨道绕肩的光点（`SpiritFxClient.companion()`：`gameTime*0.08` 的正圆）。改成一个有“生命感”的原创小光灵：跟随有惯性、攻击时会冲出去、状态会变色，还会在玩家第一次遇到机关时飞过去提示。不做实体，不复刻 Sein 模型；中文名「萤」，英文名 Glimmer。

### 1. 运动模型（客户端）

新类 `client/fx/CompanionMotion`：

- 每个精灵玩家一份状态（`Int2ObjectMap<State>`，按实体 ID 存，玩家离开 48 格或下线时清除）：`pos`、`vel`、`prevPos`、`dartTarget`、`dartTicks`、`seekTarget`、`seekTicks`、`mood`。每个客户端 tick 更新一次，渲染时在 `prevPos` 和 `pos` 之间按 partial tick 插值。
- **锚点：** 玩家右肩后上方，相对身体朝向（`yBodyRot`）：右 0.45、上 `0.9 × 身高`、后 0.25。叠加慢速 Lissajous 漂移（x、y、z 振幅 0.12 / 0.06 / 0.1，周期 3.1 / 2.3 / 3.7 s），看起来是在“悬浮呼吸”，而不是匀速转圈。
- **跟随：** 临界阻尼弹簧，`ω = 9 rad/s`，用半隐式欧拉积分，子步长 ≤ 1/40 s。与锚点距离超过 3 格（传送、末影珍珠）时直接瞬移到锚点并清零速度。
- **状态偏移：**
  - 跑步（水平速度 > 4 m/s）：锚点再后移 0.35，让它拖在后面；
  - 冲刺：只靠弹簧滞后，不额外处理；
  - 滑翔 / 鞘翅：锚点移到头顶上方 0.3，水平偏移减半；
  - 游泳：锚点下移到胸口高度。
- **攻击冲刺：** 客户端收到 `SPIRIT_FLAME` 的 FX 事件、并且来源是这位玩家时，设 `dartTarget = 命中点`、`dartTicks = 4`。冲刺期间锚点改成“当前锚点到目标的 35% 处”，结束后弹簧自然拉回。
- **对外接口：** `CompanionMotion.position(Player, float partial)`；状态还没建立时退回旧的 `SpiritFxClient.companion()`。`SpiritAura`（画光球）和 `SpiritFxClient`（灵焰光束起点，178–181 行）都改用它。`companion()` 本身保留，作为回退并供现有测试使用。

### 2. 外观（客户端，`SpiritAura` 里同一批次绘制）

- 保留现有三层（外晕 0.7、光晕 0.3、核心 0.1）。
- **花瓣：** 3 个小光点（半径 0.035）在倾斜 25° 的平面内绕核心转，轨道半径 0.16，周期 1.2 s，彼此相位差 120°。
- **彗尾：** 速度超过 4 m/s 时，沿速度方向画一个拉长的柔光四边形（长度 = `min(速度 × 0.06, 0.5)`），代替现在每两 tick 一个微粒的拖尾；慢速时仍用原来的微粒拖尾。
- **情绪色（`mood`）：** 在两种颜色间平滑过渡（时间常数 0.3 s）：
  - 平静：`0xF2F8FF`；
  - 战斗（3 s 内发射过灵焰）：`0xBDF3FF`，核心 alpha +0.05，花瓣转速 ×1.6；
  - 主人低血（< 30% 最大生命）：在平静色和 `0xFFD9A0` 之间做 1.5 s 周期的慢脉冲；
  - 黑暗（光球所在方块的方块光与天空光取较大值 < 5）：核心和光晕亮度 ×1.3，并在光球正下方地面加一个半径 3 的柔光池（复用 `SpiritAura.groundLight` 的做法，抽出一个带半径参数的版本）。
- 第一人称下仍然不画自己的光球（保持现有规则），但第一人称时自己同伴的地面光池要画，让玩家在洞里能感觉到它在。

### 3. 引导提示（服务端判定 + 客户端表现）

新类 `progression/CompanionHints`（服务端）+ `network/CompanionHintPayload(String id, Optional<BlockPos> pos)`（发往客户端）。

- **提示表**（enum `Hint`，每项含：id、触发条件、语言键 `hint.orimod.<id>`）：

  | id | 触发条件（精灵形态） | 文案要点 |
  | --- | --- | --- |
  | `updraft` | 6 格内有灵风口 | 学会滑翔后乘风上升；未学会时提示“学会滑翔后再来” |
  | `bash_anchor` | 8 格内有灵灯 | 按 R 猛击灵灯借力 |
  | `grapple_flower` | 10 格内有抓钩花 | 已学抓钩时提示用法，否则提示需要学会的技能 |
  | `spirit_sand` | 6 格内有灵沙 | 水中冲刺可以钻过灵沙 |
  | `gloom_water` | 6 格内有腐光水 | 有毒；蓄力灵焰或光爆能净化 |
  | `keystone_door` | 6 格内有钥石门 | 需要钥石，遗迹里有灯柱 |
  | `map_stone` | 6 格内有地图石 | 用它勘察遗迹 |
  | `cell_pedestal` | 6 格内有生命格 / 能量格台 | 跑酷到顶取格 |
  | `boss_altar` | 10 格内有腐光祭坛 / 风暴祭坛 | 首领在此，先备好能量 |
  | `low_energy` | 能量 < 15% 且 10 s 内消耗过 | 停手两秒能量会回复 |
  | `shard_slot` | 第一次拥有碎片且未装备 | 在 G 面板装备碎片 |

- **判定频率：** 每 20 tick 对每个精灵玩家检查一次；方块扫描用玩家周围半径 10 的立方体，只看 `level.hasChunkAt` 的位置。一次最多发一条提示，同一玩家两条提示之间至少间隔 200 tick。
- **只提示一次：** 已发提示记在 `SpiritProgress.rewards` 里，键为 `"hint/<id>"`、值为 1（不改 codec）。未学技能时单独记 `hint/updraft_locked` / `hint/grapple_flower_locked`，学会后正常用法提示仍可触发；两个版本各提示一次、共用 200 tick 间隔（2026-10-20 审查修订）。规则写成纯函数 `CompanionHints.pick(Set<String> seen, EnumSet<Hint> conditions, int abilities, long now, long lastHintAt)`，按当前技能对应的实际 id 查已读，返回 `Hint` 或 null；扫描预筛和提前返回使用同一判定。
- **客户端表现：** 收到提示后，`CompanionMotion` 设 `seekTarget = pos`（有坐标时），光球飞到方块上方 1 格并停留 2.5 s 后回来；同时在快捷栏上方显示文字（`displayClientMessage(..., true)`），光球做一次亮度脉冲，播放 `block.amethyst_block.resonate`（音高 1.8、音量 0.4）。
- **开关：** `OriClientConfig` 加 `companionHints`（默认 true）。关闭时客户端不显示文字，也不飞去；服务端照常记为已提示，避免重新打开后弹出一堆旧提示。设置界面加对应开关。
- **调试：** 在 `command/OriCommands` 的 `/orimod` 命令树（与 `form`、`unlock`、`energy` 同级）加 `hints reset`（清除 `rewards` 里所有 `hint/` 键）和 `hints list`。

### 4. 寻光（客户端，纯表现）

战斗结束 5 s 以上、玩家水平速度 < 0.5 m/s 时，每 8 s 找一次 12 格内最近、可见的灵光掉落物（`SpiritLightItem` 的 `ItemEntity`），飞过去绕它转 1.5 s 再回来。有引导提示正在进行时不寻光。

### 测试

- JUnit `CompanionMotionTest`：从 2 格外开始收敛，1 s 内误差 < 0.05；超调 < 5%；距离 > 3 格时瞬移；冲刺 4 tick 后能回到锚点。
- JUnit `CompanionHintsTest`：已看过的不再返回；间隔不到 200 tick 不返回；多个条件同时满足时按表中顺序取第一个；`low_energy` 只在消耗后 10 s 内触发。
- GameTest：精灵玩家站在灵风口旁 20 tick 后 `rewards` 里有 `hint/updraft`，并且只发一次；人形状态不触发；旧存档（没有 hint 键）读取正常。
- 性能：同伴状态最多 16 个，超出的玩家退回旧轨道。

---

## R98 灵闪（Flash）—— ORI_PARITY 缺口 4

**目标：** WotW 的 Flash：按住发出一圈强光，持续耗能，伤害并压制腐光生物，点亮灵灯，净化腐光水。放到 MC 里有实际用途：按住期间周围不刷怪，可以当作随身“火把”探洞。

### 规则

- `Ability.FLASH(8192, 40, CHARGE_FLAME)`；技能树里排在蓄力灵焰之后。
- 按键 `key.orimod.flash`，默认 **H**；在 `SpiritKeyDefaults` 里照现有做法检查冲突。按住生效。
- **输入：** 按住期间客户端每 5 tick 发一次 `ProgressActionPayload("flash", "")`；服务端每次收到就把 `flashUntil` 设为 `now + 8`。松开或丢包时，最多 8 tick 自动停止，不需要单独的停止包。状态放在 `ENERGY_STATE` 里（加 `flashUntil`、`flashActiveSince`）。
- **开启条件（纯函数 `Flash.refusal(...)`，写法照 `ChargeFlame.refusal`）：** 精灵形态、已学会、能量 ≥ 5、不在 Bash 瞄准 / 冲刺 / 抓钩 / 下砸中、没有在蓄力灵焰。
- **消耗：** 每 tick 0.3 能量（每秒 6）；能量降到 0 时停止，并进入 20 tick 冷却。期间暂停自然回能（把 `lastSpent` 更新为当前时间）。
- **效果（服务端，半径 `RADIUS = 7`）：** 实体每 tick 用 AABB 预筛，再按球形距离过滤；方块扫描按下面的独立节奏执行。
  - 腐光生物（`GloomSpitter`、`GloomHawk`，以及首领召唤出的小怪）：每 10 tick 受 1 点 `indirectMagic` 伤害，并处于“炫目”状态：喷吐者重置攻击计时、不能开始预警；腐光鸦打断俯冲、往远离玩家的方向退开。给这两个实体各加一个 `dazzle(int ticks)` 方法实现。
  - 首领（`GloomHeart`、`GloomMatriarch`）：只受伤害，每 20 tick 1 点，不受炫目影响。
  - 原版亡灵（`EntityTypeTags.UNDEAD`）：虚弱 I 持续 40 tick，每 20 tick 受 0.5 点伤害。
  - 其他生物不受影响。
  - 灵灯：首 tick 调用 `SpiritLanternBlock.igniteAround(level, 玩家位置, RADIUS, 40)`，之后每 10 tick 一次，复用 R96 区段调色板预筛与续亮。
  - 腐光水：第 40 tick 首次净化，之后每 20 tick 一次，半径 3（复用 `GloomWaterBlock.cleanse(level, center, 3)`，跳过未加载区块）。
  - **刷怪压制：** 已核对 NeoForge 21.1.248：监听 `MobSpawnEvent.PositionCheck`，同维度 16 格内的自然生成 `Monster` 设为 `Result.FAIL`（实际 API 没有 `DENY`）。事件只遍历当前灵闪玩家集合，下线/世界卸载/服务器停止清理；判定抽成纯函数。只拦 `NATURAL`，不拦 `CHUNK_GENERATION`，以保留初始区块生态；刷怪笼、刷怪蛋、命令、增援也不受影响。
- **与其他系统：** 灵闪中可以正常移动、跳跃、滑翔；使用灵刃、灵弓、灵锤、灵矛时自动停止（与 `DamageInterrupts` 的打断规则保持一致）；变回人形、死亡、打开菜单时立即停止。
- **蓄力灵焰兼容：** `charge_flame_hold` / `charge_flame_cancel` 仅维护互斥用的短租约；原有按住 12 tick 后松手释放规则不变，保活缺失/延迟/过期不得参与 release 的拒绝判定。ProgressAction 只新增 action 字符串，协议仍为 1；新增 FLASH kind 后 FX 协议为 6。

### 表现

- `SpiritFxKind.FLASH`：用 `SpiritFxTracker` 的生命周期（开始 / 持续 / 结束），持续音效用 `SpiritLoopSound`，音源可选原版 `block.beacon.ambient`（音高 1.6）。
- 光：`SpiritAura` 对灵闪中的玩家把 bloom 半径 ×2.5、alpha ×1.6，用 0.25 s 渐入、0.4 s 渐出；地面光池半径扩到 7。外圈每 tick 生成 2–3 个向外飘散的冷白微粒。
- 同伴「萤」：灵闪中光球升到头顶正上方 0.6，核心变成纯白、花瓣半径 ×1.5。
- 屏幕：本人画面整体轻微提亮（在 `SpiritScreenFeedback` 加一个“曝光”参数，最大 +8%），关闭特效画质时不提亮。

### 测试

- JUnit `FlashTest`：拒绝条件逐条测试；耗能和每秒伤害的数值；“8 tick 内没收到输入就停止”的规则；刷怪压制的距离判定。
- GameTest：灵闪伤害喷吐者并使其进入炫目；首领不受炫目；能量从 50 降到 0 后自动停止并进入冷却；半径内的灵灯被点亮；持续 40 tick 后腐光水被净化；人形状态拒绝。

---

## R99 新碎片 —— ORI_PARITY 缺口 2

**目标：** 加 5 个碎片，并给后期玩家开第 4 个碎片槽，让装备组合有取舍。

### 存档改动

- `SpiritProgress` 构造器里的 `ownedShards &= 63` 改为 `&= 2047`，同时更新注释。
- 第 4 个碎片槽：`SpiritProgression.slots(player)` 在已学技能数 ≥ 10 时返回 4（`Integer.bitCount(ABILITIES)`）；规则写成纯函数。卸下逻辑不变；槽位减少时（理论上不会发生）保留已装备的碎片，只是不能再装新的。

### 碎片

| 枚举 | bit | 前置 | 效果 | 实现位置 |
| --- | --- | --- | --- | --- |
| `LIFE_HARVEST` 生机汲取 | 64 | 无 | 每收集 1 个灵光恢复 0.5 生命，任意滚动 20 tick 最多恢复 1 | `SpiritProgression.collectLight`，拾取与背包自动入账共用 |
| `ENERGY_HARVEST` 灵能汲取 | 128 | 无 | 每收集 1 个灵光恢复 2 能量，封顶容量 | 同上，磁吸后的接触仍走拾取 |
| `ULTRA_BASH` 极限猛击 | 256 | BASH | 猛击弹射速度 ×1.2；被猛击的敌人受 4 点伤害；反弹投射物速度 ×1.3 | `BashSession` / `SpiritMovement` 的释放分支 |
| `THORN` 荆棘 | 512 | 无 | 被 3 格内的生物近战实际伤害时，对攻击者造成 2 点原版 thorns 伤害，同一攻击者 10 tick 冷却；排除 THORNS 防递归 | NeoForge 21.1.248 `LivingDamageEvent.Post`，复用 `SpiritProgression.damaged` |
| `STICKY` 吸附 | 1024 | CLIMB | 贴墙下滑速度 ×0.35；按住朝墙方向时可以停在墙上不下滑（不耗能） | `SpiritMovement` 墙面滑落逻辑 |

- 冷却和限速状态放在 `ENERGY_STATE`（瞬时、不存档）。
- 碎片说明照已有写法写具体数字，中英文同步。
- 灵光球的获取总量会因为生机汲取 / 灵能汲取变得更有价值，但不改掉落数量。
- 实现补充：拾取/背包按实际扣除的物品数结算，命令与任务奖励的直接货币不触发汲取。荆棘要求 `directEntity == entity`、LivingEntity 攻击者、非投射物、实际伤害 >0。吸附被动下降上限 −0.042；向墙悬停与原攀爬 +0.14 共用服务端/客户端规则，跳跃/滑翔预测也用同一接触判定，墙跳后的 8 tick 脱墙期保留。
- 审查修复：被动吸附要求原始输入点积 `> STICKY_WALL_DOT`（−0.2），不输入/沿墙输入可保持，明确背离则松开。先检查该阈值，再加上一面墙的 0.15 选墙偏好，偏好不能覆盖退出意图。

### 测试

- JUnit：`SpiritProgress` 旧存档（`owned_shards = 63`）与新存档（`2047`）的 codec 往返；第 4 槽规则；生机汲取的每秒上限；荆棘的同目标冷却。
- GameTest：拾取灵光的回血 / 回能量；极限猛击对敌人造成伤害；荆棘反伤、冷却内不重复；吸附时下滑速度降低；第 4 槽在学会 10 个技能后才能装第 4 个碎片。

---

## 之后的候选（本轮不做，只记录）

- **灵树（Spirit Tree）**：世界中稀有生成的发光巨树结构，每棵给一次技能点或一个碎片，替代部分灵光购买，让探索本身带来成长。
- **腐光潮夜**：月相为新月的夜晚，腐光生物在地表成群出现，灵闪和灵灯成为守夜手段。
- **灵哨（Spirit Sentry）**：放置一个自动对敌射击的小光塔，消耗能量维持。
- **同伴对话**：「萤」在首领战前后、首次进入遗迹时说一句短台词（原创文案）。

## 交付节奏

1. **R95.5（环境）：** Codex 在用户目录装 JDK 21（不需要 sudo，例如 Temurin 21 解压到 `~/.local/opt/jdk-21`，只在构建命令里设置 `JAVA_HOME`），LFS 由 Claude 拉取后，先执行 `python3 scripts/sync-dependencies.py` 下载并校验 Iris 等锁定依赖，再跑一次基线 `./gradlew build runGameTestServer`，确认 581 / 236 全过；有失败先报告，不改代码。
2. 然后按 R96 → R97 → R98 → R99 依次实现。每轮完成后停下来报告，等 Claude 审查、提交后再开始下一轮。
