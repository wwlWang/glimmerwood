# R100–R102 路线（灵树 · 腐光潮夜 · 「萤」的台词）

2026-10-20 起草（Claude 规划与验收，Codex 实现）。R96–R99 已完成并通过客户端验收（见 [WORK_STATUS](WORK_STATUS.md)）。

**这批要解决的问题：** 玩法现在是“觉醒 → 买技能/碎片 → 三类遗迹 → 两个首领”。缺两样东西：
1. **野外探索的理由**：遗迹以外的世界没有 Ori 的内容；
2. **夜晚的威胁**：R96–R98 的灵灯、灵闪只在遗迹里有用。

所以：R100 在野外放**灵树**（原作里技能就是在灵树那里获得的），R101 加入**腐光潮夜**（让灵灯、灵闪在地表有用武之地），R102 给「萤」加**台词**，把前两者串起来。

通用约定与 [ROADMAP_R96](ROADMAP_R96.md#通用约定每一轮都适用) 相同：Codex 实现并跑全量测试，不做 git 写操作；Claude 审查、提交、并在开发客户端里做实机验收。另外两条：
- **客户端与 gradle 互斥：** 我在用 runDevtoolsClient 验收时，Codex 不跑 gradle；需要跑时先在消息里说，我关掉客户端再回复确认。
- 每轮交付时，附上验收要用到的调试命令或 devtools 字段（下面每轮都列了需要的接口）。

---

## R100 灵树（Spirit Tree）

**原作：** BF / WotW 里 Ori 在灵树下获得新能力。**MC 适配：** 野外稀有生成的发光巨树，树根处有一颗“树心”。精灵形态右键树心，免费学会下一个技能；每棵树每个玩家只能领一次。

### 方块（`block/`，全部原创纹理，用 `scripts/art/build_spirit_tree_textures.py` 程序生成）

| ID | 说明 | 亮度 | 备注 |
| --- | --- | --- | --- |
| `spirit_log` | 灰白树皮带冷蓝发光纹路，可旋转（`RotatedPillarBlock`） | 4 | 掉落自身；可当装饰木 |
| `spirit_leaves` | 半透明淡蓝叶片，`cutout_mipped` | 9 | 不腐烂（不用 `LeavesBlock` 的衰减逻辑，或固定 `persistent=true`）；剪刀可采；默认不掉东西 |
| `spirit_tree_heart` | 嵌在树根的发光核心，带缓慢上升的冷白微粒（`animateTick`） | 15 | 不可破坏（`strength(-1, 3600000)`），`noLootTable`；方块状态 `BLOOMED`（有玩家领过奖励后变为 true，纯视觉：核心更亮，花瓣状粒子） |

三个方块都要有方块物品，放进创造模式物品栏（树心方便测试）；语言键中英文同步。

### 生成

- **用 Feature，不用 Structure：** 新类 `world/SpiritTreeFeature extends Feature<NoneFeatureConfiguration>`。树的全部方块限制在中心 ±14 格以内（Feature 写入范围是 3×3 区块）。
- **数据文件：**
  - `worldgen/configured_feature/spirit_tree.json`
  - `worldgen/placed_feature/spirit_tree.json`：`rarity_filter`（约 1/220 区块，常量放在 JSON 里，并在 Java 注释里写出估算密度）、`in_square`、`heightmap: WORLD_SURFACE_WG`、`biome`
  - `neoforge/biome_modifier/spirit_tree.json`：`neoforge:add_features`，step 为 `vegetal_decoration`，生物群系用新标签 `#orimod:has_spirit_tree`（森林类、平原、草甸、樱花林、针叶林、繁茂洞穴不算）。
- **形状（程序生成，种子来自位置）：**
  - 主干 2×2 或 3×3，高 16–22 格，底部有 4–6 条向外延伸 2–4 格的板根；
  - 上部 4–6 条斜枝，每条末端是半径 3–5 的椭球形树冠（边缘带随机缺口，不要实心球）；
  - 树冠下垂几条发光藤：用原版 `cave_vines` 带发光浆果，长 2–5 格；
  - 树心放在主干朝外一侧的地面高度，前方 3×3×3 清出空地，保证玩家能走到它面前。
- **放置条件（任一不满足就放弃，不生成半截树）：** 中心 5×5 地表高差 ≤ 3；脚下不是液体；树干和树冠范围内只能替换空气、植物、雪层、树叶（`BlockTags.REPLACEABLE_BY_TREES` 或等价判断），遇到其他方块就整体放弃（先检查，再写入）。
- **调试：** `/place feature orimod:spirit_tree ~ ~ ~` 应该可以直接用（原版命令）；如果放置条件让测试场地难以通过，再加 `/ori tree place` 跳过地形检查。

### 树心交互（服务端）

- 右键树心（`useWithoutItem`）：
  - 人形或未觉醒：快捷栏上方提示“灵树在沉睡，以精灵之身前来”；
  - 已领过这棵：提示“这棵灵树已把光给了你”；
  - 否则：**免费解锁下一个技能**：从所有未学会、且前置已满足的技能里，取花费最低的（相同时按枚举顺序），写入 ABILITIES，提示“灵树赐予你：{技能名}”；技能全满时改为给 100 灵光。
- 记录：`SpiritProgress.rewards` 键 `tree/<dim>/<x>/<y>/<z>`（树心坐标）= 1。照 `CellPedestalBlock.siteKey` 的写法。
- 表现：新 `SpiritFxKind.TREE_BLESSING`（一次性：从树心向上的光柱 + 向玩家汇聚的微粒，约 1.5 秒）；声音 `block.amethyst_block.resonate` + `entity.player.levelup`（音高 1.4）；第一次被领取时把 `BLOOMED` 设为 true。
- 规则抽成纯函数 `SpiritTree.gift(int abilities)`，返回要解锁的 Ability 或 null，JUnit 覆盖。

### 与已有系统的衔接

- **「萤」提示：** 新提示 `spirit_tree`（半径 24，排在 `boss_altar` 之后），文案“那棵树在发光……去树根看看”。扫描仍走 R97 的调色板预筛（树心方块）。
- **地图石 / 地图碎片：** 本轮不做（Feature 不能被 `/locate`），在文档里列为后续可选。

### 测试

- JUnit：`gift()` 的选取顺序（前置、花费、并列、全满）；放置条件的纯函数（高差、可替换判断）。
- GameTest：
  1. 在平地上 `place` 后，树心存在且前方 3×3×3 可站立；树干、树冠不超出 ±14 格；
  2. 精灵玩家右键树心，学会一个技能，再右键不再给；
  3. 人形状态右键不给；
  4. 技能全满时给 100 灵光；
  5. 两个不同树心各给一次；
  6. 场地中有不可替换方块（比如石头柱子穿过树冠范围）时整棵放弃，世界不变。
- devtools：`inspect_block` 已够用。另需一个能在指定位置放树的办法（`/place feature` 或 `/ori tree place`）。

---

## R101 腐光潮夜（Gloom Tide）

**MC 适配：** 新月之夜（`moonPhase == 4`）在主世界地表掀起腐光潮。腐光生物在觉醒玩家周围成波出现，直到天亮。灵灯 10 格内不刷潮汐怪，灵闪照着时也不刷，所以“在营地挂一圈灵灯”成了真正的防御手段。

### 规则（服务端，`world/GloomTide`）

- **潮夜判定（纯函数）：** `isTideNight(dayTime, moonPhase, difficulty, doMobSpawning, configEnabled)`：主世界、`dayTime % 24000` 在 [13000, 23000)、`moonPhase == 4`、难度非和平、`doMobSpawning` 为 true、配置开启。
- **配置：** 新增服务端配置（`ModConfigSpec`，SERVER 类型）：`gloomTide.enabled`（默认 true）、`gloomTide.waveInterval`（默认 200 tick）、`gloomTide.maxNearby`（默认 8）。如果项目目前没有服务端配置，就照 `OriClientConfig` 新建 `OriServerConfig`。
- **谁会被潮汐找上：** 已觉醒、生存或冒险模式、在主世界、头顶能看到天空（`level.canSeeSky(blockPosition)`，或 Y ≥ 海平面 − 5），没在挑战/首领战中（如果有现成判断就复用）。
- **波次：** 每个合格玩家每 `waveInterval` tick 一波：1–3 只喷吐者，30% 概率再加 1 只腐光鸦。刷在玩家 16–28 格外的地表（`Heightmap.MOTION_BLOCKING_NO_LEAVES`，脚下是实心方块，不在液体里，光照不限），尝试 10 次找不到合法位置就跳过本只。
- **上限：** 玩家 48 格内已有 `maxNearby` 只潮汐怪就不再刷。
- **防御：**
  - 刷怪点 10 格内有任意灵灯（亮灭都算），就不刷。用 R96 的区段调色板预筛；结果按区段缓存 100 tick，避免每次刷怪都扫描；
  - 刷怪点 16 格内有正在灵闪的玩家，就不刷（复用 R98 的 ACTIVE 集合）。
  - 两条判定都抽成纯函数 + 小的查询接口，JUnit 和 GameTest 都能测。
- **潮汐怪：** `getPersistentData().putBoolean("orimod:tide", true)`；不掉落普通战利品以外的东西，但击杀时灵光掉落 ×1.5（`SpiritProgression` 里的掉落计算按标签判断）。天亮（`dayTime` 离开潮夜区间）时，所有已加载的潮汐怪化作一阵微粒消失（`discard`，不掉落）。
- **黎明奖励：** 一个潮夜里击杀 ≥ 5 只潮汐怪的玩家，天亮时得到 50 灵光。计数存在 `ENERGY_STATE`（瞬时）里，按“第几天”区分，下线重连不保留，这可以接受。
- **广播（每个合格玩家）：** 潮夜开始时，快捷栏上方提示 + 原版 `ambient.cave` 低音高；天亮结束时提示结果（击杀数、奖励）。

### 表现（客户端）

- 新网络包 `GloomTidePayload(boolean active, float intensity)`（单独的通道版本号）。服务端在开始/结束、玩家登录、换维度时发送。
- 潮夜期间：天空和雾色向暗紫偏移（`ViewportEvent.ComputeFogColor` 混合 `0x2A1638`，强度 0.35，渐入/渐出 5 秒）；星星稍暗。关闭特效画质时只做雾色，不做额外粒子。
- 远处（雾边缘附近）偶尔飘过腐光微粒（复用 `GloomLook` 的颜色），每秒 ≤ 4 个，跟随 `FxBudget` 画质档。

### 调试

- `/ori tide start|stop|status`：强制开始或结束当前潮夜（忽略月相，但仍然遵守和平难度与 `doMobSpawning`），`status` 输出是否潮夜、本玩家附近潮汐怪数、本夜击杀数。
- devtools `spirit_state` 的服务端部分加 `tide` 字段：`active`、`nearby`、`kills`、`nextWaveIn`。

### 测试

- JUnit：`isTideNight` 的边界（13000/22999/23000、月相、和平、开关）；生成距离与上限规则；灵灯/灵闪防御的纯函数。
- GameTest：
  1. 强制潮夜后，一波之内在玩家 16–28 格出现潮汐怪，带 tide 标签；
  2. 玩家 10 格内放一盏灵灯、并在四周放满可用地面时，10 格范围内永远不刷（多波统计）；
  3. 达到上限后不再刷；
  4. 和平难度、`doMobSpawning=false`、人形或未觉醒玩家都不刷；
  5. 天亮时潮汐怪全部消失，击杀 ≥ 5 时发奖励，奖励只发一次；
  6. 潮汐怪击杀掉落 1.5 倍灵光。

---

## R102 「萤」的台词

**目标：** 把 R97 的同伴从“只会提示”变成“会陪伴”：关键时刻说一句短话。不做对话树，不做语音。

### 机制

- 复用 R97 的 `CompanionHintPayload` 通道；如果要加字段（比如 `kind = hint | line`），就升通道版本并补解码拒绝测试。
- 新 enum `CompanionLines.Line`：id、语言键、是否“只说一次”、冷却（tick）。只说一次的照提示的写法记进 `rewards`，键为 `line/<id>`。
- 客户端表现与提示相同（快捷栏上方文字 + 光球脉冲），但前缀改成「萤」的名字，颜色用冷白蓝；台词不触发“飞去目标”的动作，除非带了坐标。
- 与提示共享 200 tick 的全局间隔，避免同一时刻刷屏。台词优先级低于提示。

### 台词表（文案都按 R97 的标准：同伴口吻、≤ 30 个汉字、中英文同步）

| id | 时机 | 只说一次 |
| --- | --- | --- |
| `first_ruin_<kind>` | 第一次进入某类遗迹（森林泉 / 溪谷 / 洞穴祭坛） | 是 |
| `boss_start` | 腐光之心 / 腐光翼母 战斗开始 | 否（冷却 6000） |
| `boss_defeated` | 击败任一首领 | 否 |
| `escape` | 逃亡段开始 | 否 |
| `tide_begin` / `tide_end` | 潮夜开始 / 结束 | 否 |
| `tree_found` | 第一次领取灵树赐福 | 是 |
| `low_health` | 生命 < 25%，且 60 秒内没说过 | 否（冷却 1200） |
| `night_one` | 觉醒后的第一个夜晚降临 | 是 |

每个触发点只在已有逻辑里加一行调用（遗迹进入可复用 `RuinLocator`/挑战的进入检测；首领用它们已有的阶段事件），不要为了台词重写那些系统。

### 测试

- JUnit：冷却与只说一次的规则；与提示的优先级。
- GameTest：首领开战 → 收到 `boss_start`；冷却内不重复；`tree_found` 只说一次；人形状态下不说。

---

## 交付节奏

R100 → Claude 审查 + 客户端验收 → 提交 → R101 → … → R102。每轮做完停下报告。Claude 的客户端验收脚本会扩展到 `scripts/verify-r100-r102.py`。
