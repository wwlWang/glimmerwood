# R116 试玩记录（playtest pass, 2026-10-26）

基线 `f3cc83c` (R116)，headless Xvfb :99，llvmpipe 软渲染（低 FPS 属预期）。世界 `orimod_dev_pt116`。
截图：`/workspace/oriMod/shots/playtest-r116-*`。每个区域测完即追加，标注 **[复现]** / **[怀疑]**。

## 环境
- 启动前清理了上次中断遗留的 `gradlew runDevtoolsClient`（pid 472850/472907）。
- 日志噪声（非 bug）：OpenAL 无设备（Xvfb 无声卡）、GLSL 4.60 shader compiler error（llvmpipe 4.5）、mixin 对未安装 Mek/IE 等的 Error loading class 警告。

## 区域 1：移动（devtools input_script + record_motion，`/ori unlock all`）
- 地面跳：峰值 1.62 格 / 13 tick 空中，下落逐 tick 加速（-0.098→-0.47 …）**[通过]**
- 潜行跳：潜行按住时可起跳，峰值同上 **[通过]**
- 二段跳（顶点附近第二次按）：峰值 2.28；单点按松不触发滑翔；按住第二跳进入 glide，匀速下沉 ≈0.12/tick **[通过，滑翔匀速是设计]**
- 蹬墙：wall_slide → wall_jump 触发，离墙 ≈3 格 **[通过]**
- 翻上沿（mantle）：墙顶高于脚 2、3 格可翻上；4 格退化为蹬墙。lastAction 不记录 mantle（仅客户端 SpiritMantle），无法由状态字段验证，只由终点位置推断 **[通过/信息]**
- 游泳：不按疾跑 = 站姿下沉（原版行为）；疾跑+前进 → `SWIMMING`、速度正常、第三人称为俯冲姿势 **[通过]**
- 截图：`shots/playtest-r116-swim-*.png`、`fp-*`、`tpf-*`

## 区域 1b：落地接跑
- 疾跑中起跳落地：水平速度全程 0.32/tick，无停顿 **[通过]**

## 区域 2：第一人称双骨手臂 / 持物
- 空手/方块/刃/弓/锤/矛/箭 一人称截图：白色手臂 + 能量武器，姿态正常，无原版人形手臂闪现 `shots/playtest-r116-fp-sheet.png` **[通过]**
- 精灵形态第三人称（前视）：光晕、耳尾、多段形态正常；但**白天光晕过曝，持有的武器在背面/前视图都看不见**（`tpf-crops2.png`、`tp-sheet.png`）**[怀疑 / cosmetic]**：无法确认第三人称握持武器是否渲染，未做侧视特写。
- 切 `form off/on`：命令正常；本轮自动截图未捕获到原版人形闪帧 **[未复现]**

## 区域 3：命令 / 创造栏 / 技能轮盘 / HUD / 设置
- `/ori` 全部子命令（awaken/form/unlock/giveall/energy/light/cells/god/locate ×3/spawn ×6/reset/status/great_tree/blight）均返回成功；非法参数（`cells life 99`、`locate bogus`、`spawn bogus`）正确报错 **[通过]**
- `/ori status` 通过 `sendSystemMessage` 输出，devtools captureOutput 只能看到“已应用到 1 名玩家”（`commands.orimod.done` 为 `/orimod %s`）——**cosmetic**：反馈把内部参数名当命令名显示（`/orimod all`、`/orimod off`、`/orimod fill`），不是用户输入的 `/ori ...`。
- `/ori giveall` **不幂等且会溢出**：清空后单次 giveall 36 件进背包，另有 22 件掉在脚下（共 58 件 > 36 格）；再调一次又整套再给 **[复现，minor]**。
- 创造栏：「OriMod 精灵」标签在第 2 页，图标/物品齐，`orimod:ginso_root_lip`（金索苔根护沿）GUI 图标比格子高，**溢出到上方 `spirit_sand` 的格子里**（`playtest-r116-creative-tab2-zoom.png`）**[复现，cosmetic]**。
- 技能轮盘（长按 G）：打开、四扇区、热键徽标与槽位一致（复原 Z/光爆 X/蓄力灵焰 H）**[通过]**。
- 设置：客户端设置→画面→「Spirit Glow 光影」标签是中英混排 **[cosmetic]**；开启后 Iris 加载 Spirit Glow.zip，bloom/阴影正常渲染，关闭后恢复，无异常日志（仅 Iris 的 tag 缺失 WARN：`minecraft:bars/chains/carpets/lightning_rods/wooden_shelves`，来自 Iris 版本与 MC 版本不匹配，非本模组）**[通过]**
- 死亡/重生（被自己刷出的 heart/matriarch 杀死）：死亡界面正常；重生后保留觉醒/形态/技能槽，血量满 26（3 生命格），背包只剩 `ruin_guide`（keepInventory=false，物品掉落）**[通过]**

## 区域 2b：武器动作（补充）
- 一人称：锤蓄力（举过头）、弓拉满（双手）、刃挥砍均有明显姿态（`fp-action-sheet.png`）**[通过]**
- 操作语义：锤=按住右键蓄力 12 tick，矛=右键即投（耗能 12，冷却内无反应），弓=按住右键；**左键点击锤/矛无任何效果**（设计如此，ORI_PARITY 未规定左键）**[信息]**
- 第三人称侧视（`tpside-sheet.png`）：武器可见在下垂的手上；日间过曝光晕下细节弱 **[cosmetic，怀疑]**

## 区域 4：结构（`/ori locate` + 光谱观察，世界 seed 同 `orimod_dev_pt116`）
- 注意：运行的客户端是 `f3cc83c`；中途 origin 又推了 `0aaf077`（forest_spring 重建），**此处 forest_spring 看到的是旧版 LayoutVersion 0**，新版未验证。
- cave_altar（LayoutVersion 1，origin [46,30,198]，rot CLOCKWISE_180）：入口→阶梯→前厅→神龛室→地图室→抓钩廊→钥石门→祭坛，10 个固定机位截图 `cave-sheet1/2.png`，无缺块/悬空/穿模，苔藤与光源布置正常 **[通过]**；`descending-ribs`（下降阶梯）亮度很低，几乎全黑（`cave-descending-ribs.png`）**[cosmetic，主观]**。未做真实输入通关（由 GameTest 覆盖）。
- creek_ruins（layoutVersion 0）：水渠、石墙、开花大树生成正常 **[通过]**
- forest_spring（旧版 v0）：生成正常 **[通过]**
- 灵树 Spirit Tree（-200,72,-424，ShapeVersion 2，topBound 296）：远景树干/冠/垂须渲染正常；`great_tree_state` 返回 ascent/heart/reward 坐标齐全 **[通过]**；未走内部（长耗时，GameTest 覆盖）。
- 金索树 Ginso（-640,~,-1168）：外形/垂苔渲染正常 **[通过]**
- 建筑方块放置（use_block 在平台上放 15 种物品：glades_well / forest_spring_shrine / creek_ruins_shrine / cave_altar_shrine / map_stone / great_tree_heart / keystone_door / gloom_altar / storm_altar / 三种基座 / bash_anchor / grapple_flower / updraft）：全部成功放置，多方块整体出现，无半截 **[通过]** `place-overview.png`

## 区域 6：日志与资源扫描
- `latest.log` 约 380 行：模组自身 **0 异常 / 0 缺纹理 / 0 缺模型**；仅有环境噪声（OpenAL 无设备、GLSL 4.60 编译提示、Mixin 对未装模组的 Error loading class、Iris 对旧 MC 的 tag 缺失、Flywheel 后端回退、Sodium/Iris 强制禁用 sky mixin）。
- GemRender 唯一一条 `Failed to load texture: gemrender:atlas/.../gloom_heart.glb`（FileNotFoundException，只出现一次，TextureManager WARN，gloom_heart 实体仍渲染）——**[怀疑 minor]** 图集纹理在注册前被 TextureManager 抢先请求；该警告每次加载世界可能复现。
- `scripts/check-assets.py`：6 个 GLB 全部 passed；en_us/zh_cn 536 键完全对齐，代码里引用的静态 lang 键均存在。
- crash-reports 目录无今日新文件。

## 区域 7：全量测试
- `cleanTest build runGameTestServer compileDevtoolsJava` 在 `436cef0`（= R116 `f3cc83c` + `0aaf077` forest_spring 重建 + 本文档提交）上：**BUILD SUCCESSFUL，1682 JUnit / 1090 GameTest，0 失败 / 0 跳过**（日志 `/workspace/oriMod/pt116/full-final.log`，1m54s 完成）。

## 补充：羽毛 / 滑翔
- 胸甲槽装鞘翅（精灵形态下的“羽毛”）：二段跳后按住跳跃进入 glide，下沉约 0.125/tick 匀速并保持前进，`feather.wearing/permitted` 正确；松键后回落 **[通过]**

## 本轮未覆盖
- 挑战垫落地检测、钥石门真实通关、灵树/金索内部真实输入攀登（由既有 GameTest/脚本覆盖，本轮未在客户端实测）
- 原版人形闪帧（形态切换时）未捕获；Iris 真 bloom 之外的光影包未测
- forest_spring 新版（`0aaf077`）视觉未看；仅看了旧版

---

# Pass 2: items & levels（第二轮，2026-10-26，基线 `b787787`，世界 `orimod_dev_pt116p2` seed 116116）

Xvfb :99 + llvmpipe；截图 `/workspace/oriMod/shots/playtest-r116-p2-*`。每个区域测完追加。**[复现]** / **[怀疑]**。

## P2-A 无作弊起步（真实输入）
- 生存模式，`/give` 只发原版材料（铜锭/玻璃/铁锭，且拆成 9 个单件格）→ 工作台真实点击合成「精灵核心」→ 右键觉醒。**[通过]**
- 觉醒后：赠送 `ruin_guide`，觉醒时自动弹出成长面板（SpiritScreen）；面板打开期间 attack 不触发刃（`mc.screen != null` 取消），关面板后恢复 **[信息]**。
- 面板 `claim blade` → 刃入包；刃三连真实 attack 击杀 3 只僵尸 → 灵光 +6（2/只，符合 SURVIVAL_FLOW），另掉恢复球（RecoveryOrb，设计）**[通过]**。
- 夜间平台刷怪（苦力怕/骷髅/蜘蛛）会把测试玩家杀死；人形/精灵未区分，属原版规则，非 bug。

## P2-B 物品（真实使用）
- 生命格/能量格：右键 12 个 → 吸收 10 个封顶，剩 2 个未消耗并提示 `cell_full`；生命 20→40、能量上限 100→200 **[通过]**。
- 灵矛：精灵形态下右键，能量 −12/次、冷却 24 tick，3 只僵尸被穿透击杀 **[通过]**；人形形态下无任何反应（有 `not_spirit` 提示）。
- 灵击之锤：按住 use 12+ tick 松开 → −8 能量，僵尸受 ~10 伤害 **[通过]**。
- 灵弓：按住 25 tick 松开 → −12 能量、`bowShots`+1，命中 **[通过]**。
- 技能键：槽位1(Z)=复原(按住-10)、槽位2(X)=光爆(−15，击中僵尸 9 伤害)、槽位3(H)=蓄力灵焰(−10)；`key.orimod.light_burst` 实际是「技能槽 1」，不是光爆（命名易混，信息）**[通过]**。
- 生命格封顶后重进存档：**[复现，minor/major] 10 个生命格时最大生命 40，重进世界后当前生命被夹到 20（半管）**——`SpiritCells.login` 只在加载后补回属性修饰，不补血；每次登录都会丢掉额外心（保存前 40/40 → 登录后 20/40）。
- 钥石门：`keystone_door` 物品放置为 3×3，需靠近（超出触及范围放置静默失败）；空手点击无反应(只显示 need 提示)，插第 1 把 filled=1，存档→退出→重进 `filled=1` 保持，第 2 把全门消失，钥石逐把消耗 **[通过]**。

## P2-C 挑战垫（真实客户端，第一次实测）
- creek_ruins（`-76,67,215`）：用 RuinScreen「开始挑战」→ 传到起点 → 落到第 1 垫（`-63.5,72,218.5`）checkpoint 0→1，落第 2 垫完成；首通：灵光 +15(发现)+30，生命格+能量格各 1 进背包（背包满则掉落脚边，不丢）；`best` 记录；再次进入显示「已通关 1 次，最好 17.40 s」，重复通关 +3（`clear/..=2`）**[通过]**。
- 横向 2.1 格外落地不算，垫上才算；走出路线（离路线 / 水 / `y<1`）→ 立刻回起点并取消会话（`ended.fall`）**[通过]**。
- forest_spring（新 v1，origin `-1152,61,112`，shrine `-1145,67,129`）：3 垫（wall_jump/jump×2/dash）依次落地即清，+30；`discover` 15 **[通过]**；整条路线手玩可达性未验证（只测了落地判定；GameTest 覆盖）。
- 观察：垫的判定只看位置+落地，没有强制建议动作；改走别的方式（落点命中即可）也算「即兴」，符合 R88 的设计。

## P2-D cave_altar（v1）真实输入行走
- 脚本从模板 NBT 算出可步行路径（起点 entry→altar，75 格），`walk_route`（只用鼠标转向+W）真的 453 tick（22 s）走完入口→阶梯→前厅→…→祭坛，无卡死、无碰撞问题 **[通过]**；截图 `cave-walk-end.png`。
- 祭坛（`gloom_altar`）无需任何能力/挑战就能走到并唤醒（只检查「已觉醒」）。设计确认：[怀疑] 若期望「祭坛在抓钩/下砸之后」，现在是纯步行直达（BFS：入口→祭坛不依赖任何能力）。钥石 #1（10,9,35）与锁门区也是步行可达；钥石 #2（38,11,46）和 life cell（34,11,46）在抓钩平台上（需抓钩）。
- **Gloom Heart 击杀后的逃生（EscapeSequence）：[复现，major/怀疑平衡]** 玩家在祭坛附近杀死 Heart → 「污染前沿」半径 3+0.2·t（4 格/s），前沿内缓慢 II + 每 10 tick 3 伤害，且必须「天空可见 + 距祭坛 ≥12」才算逃生。cave_altar 祭坛到地表出口直线 ≈60 格、路径 ≈75+ 格（含上楼阶梯跳跃）。实测：生命上限 40，从离祭坛 15.7 格处击杀并冲刺跑，被前沿追上（t≈140）后在 t=240 死亡（前沿内每秒掉 6 血），未逃出（脚本控制器比真人慢，可能偏悲观）。贴着祭坛杀死 Heart 则 130 tick 内即死。逃生不成 = 无奖励，需再唤醒祭坛。→ 需要真人 + 冲刺/冲刺技能再验证；基础 20 血玩家几乎不可能。
- 祭坛可重复唤醒（`phase=DEFEATED` 仍可再次召唤），且 `EscapeSequence.victory` 每次逃生都 `grantLight(+100)`，只有生命格/能量格是首次限定 → **[怀疑，balance] 可无限刷 100 灵光**。

## P2-E 前哨/商人/传送井/上升气流/地图石/容器（真实使用）
- 商人（通过 `key.use` 对准实体打开，devtools 无 entity-interact）、制图师购买（40 光→异步导引）、泉井传送、虚空容器(+40 灵光)、地图石(+10 一次，二次无奖励) **[通过]**，截图 `*-cartographer/well-screen/merchant-*/map-stone`。
- 上升气流：单个通风口滑翔升力 ≈12 格；**叠放多个通风口会阻挡跳跃与滑翔**（每个都带碰撞）——[信息/minor] 作者若想做高烟囱要用单口+多格 shaft，不要叠方块。
- 钥石：踏板（KeystonePedestal）是一次性、不补、无配方。**[复现/代码确认，major soft-lock]** 钥石丢失/被误用（如放到别的踏板）后，该世界的钥石门及其后的能量格（cave_altar v1 门 `x=15,z=43..45` 守 `(7,6,44)`）永久不可达；per-world，非 per-player。建议：踏板可回收或给钥石一个合成/商人兜底。

## P2-F 灵树（Spirit Tree）
- 真实输入整段上升：基部 → 气流柱 → 瞭望台 y≈290 **[通过]**，`gt-ascent / gt-lookout`。
- 从 y=280 摔到根部缓冲垫：落地 y≈96.25、HP 40.0、fallDistance 0 **[通过]**（缓冲垫有效，零伤害）。

## P2-G 腐化地面/净化（Blight）
- `locate structure orimod:blighted_grounds` → `-512,-384`；`/ori blight nearest` 在区块未加载时返回「没有腐化之核」（设计，需靠近加载），到达后 7.8 格内可找到核 **[通过]**。
- 核 40 HP；锤击 10/次（真实按住 use 20 tick 松开）→ damage 0→1→2→3→破，4 击；破核后 `BlightData.begin` 开始 600 tick 净化，期间逐只刷防守者（限 3）；结束后 +60 灵光、`purify/…` 奖励键、`line/first_purify` **[通过]**。
- 近战空手/普通方块打核只会 `reject`（火花声）；用 attack 打核不掉血，符合 R-设计。

## P2-H 刷怪蛋
- gloom_beetle / gloom_hawk / gloom_heart / gloom_matriarch / gloom_spitter 蛋均能正常刷出（heart 悬空 ~9 格、matriarch 悬空 ~13 格，推测是悬浮 boss，高度需确认）**[通过]**。
- **[复现，minor] `practice_target` 刷怪蛋无用**：蛋被消耗，实体在 `PracticeTarget.tick` 里因 `owner==null` 立刻被 `discard()`——创造栏里点了什么也没有。建议从创造栏移除或给提示。
- 刷怪蛋 spawner 对 heart/matriarch 没有校验祭坛（可在任意处召唤 boss，仅 creative 内容，info）。

## P2-I Ginso Tree（仅代码+API，未走完整行程）
- `ginso_state`：origin `-632,71,-1160` height 109，shapeVersion 3 / courseVersion 2 / escapeVersion 1，逃生路线 66 个洪水 box、`interactive:true`、`referenceSeconds` ≈36.5 s **[已读数据，未实跑]**。
- 内部分区 bash(20–38)→grapple(38–56)→wind/glide(56–78) 对应能力链 DASH→BASH→GLIDE→GRAPPLE，grapple 区之前玩家必已拥有 glide（前置），无顺序卡死 **[代码确认]**。
- **[未测]** Ginso 整条课程的真实手玩、水心逃生/涨水。

---

# Pass 3: exploratory/chaos（第三轮，2026-10-29，基线 `cd1670b`，世界 `orimod_dev_pt116p3` seed 116333）

自上轮（`d19bc5f`）起 origin 新增 4 个提交：`6f05c02` creek_ruins 重建（v1 溪谷）、`4c11df2` 钥石绑定/祭坛门控/生命格/练习靶蛋修复、`ab655da` 逃生宽限期 + 固定钥石台、`cd1670b` 重生后跳跃卡住修复。本轮用 `./gradlew --no-daemon runDevtoolsClient`（Xvfb :99，llvmpipe）。证据：`/workspace/oriMod/shots/playtest-r116-p3-*`，运动记录 `run-devtools/devtools/pt116p3-*.json`。
**不改代码**，只记录。**[复现]** / **[怀疑]** / **[已修复]** 标注。

## P3-0 旧问题复验
- `/ori giveall`：空包一次 36 件进背包、地面 0 件掉落；第二次 giveall 不再溢出 **[已修复]**。
- 命令反馈文字：现在是 `/ori awaken：已应用到 1 名玩家`、`/ori form on：…`、`/ori unlock all：…` **[已修复]**。
- 其余（钥石卡死 / 10 生命格重进 HP / practice_target 蛋 / ginso 图标）见下方各区域。

## P3-1 输入乱按 / 表面 / 效果（自动化客户端，生存，精灵形态，`/ori unlock all`）
- 随机乱按（跳/冲刺/猛击/潜行/疾跑/方向/技能1-3/切形态，每 2 tick 一次，共 3000 tick、6 段）：无异常、无 NaN、`predictionRejections=0`、`unconfirmed/pending=0`，`predictionMismatches` 只个位数 **[通过]**。
- **[工具限制，非 bug]** devtools `input_script` 超过约 300 步会 `REQUEST_TOO_LARGE`，静默无效；`save_motion` 文件名含 `.` 会 `Invalid evidence filename`。
- 表面（冲刺+二段+冲刺键+滑翔键）：灵魂沙/冰/蓝冰/粘液/蜂蜜/岩浆/蛛网/细雪/甜浆果丛/仙人掌/脚手架全部无卡死、无异常；岩浆块/甜浆果/仙人掌正常掉血；蛛网里冲刺仍触发 **[通过]**。
- 梯子/藤蔓：前进即爬（约 20 tick 后），连续按跳可爬，爬行中切形态不丢状态 **[通过]**。
- 载具（船/矿车/猪/马/炽足兽）：骑乘中 `form` 键被忽略（`toggle_form` 不生效，无任何提示），能力键不触发、无异常，下车后恢复 **[信息]**。
- 药水/属性：`jump_boost 6` 跳高 6.8 格（受影响）；`jump_strength=0` 跳高 0；**速度/缓慢/`movement_speed` 属性对精灵形态走路速度完全无效**（走和疾跑都 0.31 格/tick；人形 slowness 3 → 0.08、speed 6 → 0.45）**[复现，minor/设计问题]**：意味着逃生前沿的「缓慢 II」对精灵形态没有减速效果，也无法被药水/属性调节（见 P3-D）。缩放 4 倍、`step_height`、`gravity`、`safe_fall_distance` 未见异常；gravity 0.01/0.5 与 scale 0.1 的跳跃数据因文件名错误未保存，补测见下。

## P3-2 玩家会做的怪事（续）
- 游戏模式：创造/冒险/旁观/生存切换（含脚本中途连续切换）无异常；**创造模式下精灵的跳跃用原版（1.25 格，无二段跳/滑翔）**，冲刺仍可用（`MovementRules.ownsJump(..., mayfly)` 的设计；创造飞行双击跳照常）**[信息]**。旁观模式无能力，飞行正常。
- 药水：jump_boost 生效；levitation / slow_falling / darkness / blindness / nausea / weakness 全部无异常；slow_falling 下滑翔下沉 0.01/tick。
- 属性：`scale` 4 / 0.1、`step_height` 2、`gravity` 0.5/0.01、`safe_fall_distance` -50、`jump_strength` 0 均无崩溃、无卡死（0 → 跳高 0，符合预期）。
- 载具：见 P3-1。维度切换（滑翔中 `execute in the_nether/the_end/overworld run tp`）：能力状态清空为 idle、`predictionRejections=0`、无异常；在下界/末地落地正常 **[通过]**。
- 1 格高缝隙：精灵形态 1.2 格高，与人形一样进不去 1 格高通道，2 格高通道可走；`pose` 保持 STANDING，无爬行姿态（与原版玩家一致）**[通过/信息]**。
- tp 进实心石块：能力键（跳/冲刺/猛击）无异常，窒息伤害照常（20→9，约每 10 tick 1 点）；不会被推出（块心）。**[信息]**
- 天花板 2 格高下跳/二段跳/冲刺：无穿模、无卡顶；虚空/`/kill` 中途技能：重生后保留觉醒/形态，状态 `idle` **[通过]**。
- 变量跳高语义（**非 bug，澄清**）：按住跳键 2–3 tick = 小跳（0.94 格），按住满 = 1.62 格；二段跳窗口 = 空中（约 13 tick）；在落地前 1 tick 按二段不触发。乱按脚本里“二段跳不出现”是因为第一下是短按小跳后落地 **[设计]**。
- 重进世界：10 生命格存档→重进 HP 40→40（**[已修复]**，之前 40→20）；滑翔中途退出再进，位置恢复到地面、`mode=idle`、能力键正常 3/3 次 **[通过]**。

## P3-3 钥石 / 练习靶 / 首领 / 逃生
- 洞穴祭坛（seed 116333，origin [-288,37,-226] CCW90，第二座 [-242,36,-106] CW180）：钥石台 (-253,46,-236) 生存模式连按 80 tick 不掉；`use` 取到钥石，物品组件 `orimod:keystone_ruin` = `overworld/cave_altar/1/<origin>/COUNTERCLOCKWISE_90`（绑定）；再次使用提示「重新凝聚」；`/tick sprint 24500` 过 1 天后台子 `full=true` 自动补回 **[已修复：钥石软锁]**。
- `practice_target` 刷怪蛋：对着地面 use → 生成 `practice_target`（12 HP，存活 >65 s、可被打）**[已修复]**。
- Gloom Matriarch（`storm_altar`，需 `use_block` 近距，≥4.5 格外放置/使用静默失败）：召唤后悬浮 ≈6 格巡场，重复使用不会重复召唤；会话中退出世界→重进，首领和祭坛 `ACTIVE` 保持；`/damage` 每次最多扣 2 点（伤害封顶）；`/kill` 首领→祭坛 `DEFEATED`、掉 will_wisp + 2 只 gloom_hawk + 物品；再次使用不会重召 **[通过]**。**[信息]** 用 `setblock` 直接摆的 `storm_altar` 是 `assembled=false`，使用无反应（只能用物品放置）。
- Ginso Tree（seed 116333，origin (72,71,904)，高 108）：水心 (72,165,904) 真实 use → `PREPARE`→`RUNNING`；`walk_route` 走 63 点、19.4 s 内到出口，outcome `SUCCESS`、+100 灵光 +1 能量格、HP 40/40；再次触发同路线不再给奖励（`rewardLight 0`）；逃生中关形态 → 继续 RUNNING，不影响；逃生中 `/tp` 走 → 立刻回到安全点、outcome `OUTSIDE`；站着不动 → 水涨至淹没、outcome `SUBMERGED`、HP 40→37、回安全点，不死亡 **[通过]**。
- 精灵沙（`spirit_sand` 6×6×7 块）：直接走进去就穿过并被推出（出口 +0.85 速度）；冲刺穿过；从 30 格高落上去；tp 到沙块中心 → 约 30 tick 内被推出到 +z 面；人形形态落在沙块顶 **[通过]**。
- 腐光水：2 点魔法伤害 / ~10 tick + 缓慢 I（精灵形态不减速，见 P3-1）；`setblock` 移除池边后会向外扩散 1 格即停（短距离扩散，设计）**[通过]**；在池内停下不动会持续掉血致死（40 HP 约 20 s），池里没有出水提示 **[信息]**。

## P3-4 腐光之心逃生（cave_altar，真人式输入，HEAD `d774b92`，含宽限期 `ab655da`）
方法：生存、Normal、20 HP（基础，无生命格）、`/kill` 心脏（祭坛 `(-228,44,-249)`，逃生原点 `(-227.5,45,-248.5)`），随后立刻用**真实按键**（W+疾跑+跳，鼠标 yaw 由脚本每 ~30 ms 校正；冲刺键 `key.orimod.dash` 每 30 tick 一次）沿 75 点步行路线跑（祭坛→出口 `(-288,66,-248.5)`，直线 ≈58 格，路线 74 步、上升 22 格，含 1:1 长楼梯）。服务端 `gloomEscape.elapsed/radius` 取自 `spirit_state`，证据 `pt116/escape_*.json`。
| 尝试 | 用时（tick / 秒） | 前沿宽限后最小领先距离 | HP |
|---|---|---|---|
| 精灵形态 疾跑+跳+冲刺 | 288 / 14.4 s | 31 格 | 20→20 |
| 精灵形态 疾跑+跳（无冲刺） | 336 / 16.8 s | 21 格 | 20→20 |
| 精灵形态 只走（不疾跑）+跳 | 318 / 15.9 s | 26 格 | 20→20 |
| 人形 疾跑+跳 | 288 / 14.4 s | 30 格 | 20→20 |
| 人形 只走 | 387 / 19.4 s | 11 格 | 20→20 |
| 精灵 + **站在祭坛旁 10 s 再跑** | 死亡（前沿 `elapsed 160` 起 2 HP/10 tick，约 2.5 s 内被打穿） | −12 | 死 |
| 第一次（脚本拖延 5.6 s 才起跑，出口前一度卡楼梯拐角约 40 tick） | 仍然成功，逃出 elapsed 413，领先 6.6 格 | 6.6 | 17 |
- **结论 [已修复，逃生可行]**：宽限期后 4 格/秒的前沿对 20 HP 基础玩家：立刻起跑、走着去都能逃出；即使路线卡了 ~2 s 也成功。窗口大约 = 宽限 8 s + 路线用时 ≈ 15–20 s，前沿 `elapsed 413`（20.6 s）时半径 ≈ 54 格才赶到出口附近，**最紧的人形纯走仍领先 11 格 ≈ 2.8 s**。原先「几乎不可能」的怀疑已不成立。
- 首次胜利：`胜利！获得 100 灵光、一枚生命格和一枚能量格`；之后每次 `+10 灵光`（receipt `escape/gloom_heart/<dim>/<pos>`）——**[已修复，无限刷 100 灵光；仍可重复刷 10/次]** `boss/gloom_heart` 每次逃生 +1。
- 精灵形态走和疾跑速度相同（~0.31 格/tick，约 6.2 格/s），疾跑键对精灵形态无加速（与 P3-1 一致）；人形疾跑也 ≈6.2 格/s（288 tick 与精灵同），走 4.3 格/s。
- 站着不动期间（宽限内）每 ~2 s 掉 3 HP：来自竞技场里的 3 只 `gloom_spitter`（Heart 战/召唤时附带，非前沿；宽限提示期间 20 HP 玩家站着 8 s 就掉到 ~9 HP），是怪的伤害不是前沿 **[信息]**。前沿起 20→0 约 2.5 s。
- **[复现，minor] Peaceful 难度下召唤 Gloom Heart：** 提示「腐光之心苏醒了！」，祭坛转 `ACTIVE`，但心脏作为怪物立刻被 Peaceful 清理（无实体），玩家得到误导的提示；改回 Normal 后再点才正常召唤。建议 Peaceful 时提示「需要非和平难度」或禁止消耗祭坛状态。
- **[信息]** 死亡后（无 keepInventory）首次胜利发的生命格/能量格在背包里，随死亡掉落——符合原版规则；但 boss/逃生奖励是「首次限定」，丢了就不再发（`CLEARED` 已记）。**[怀疑 minor]** 建议首次奖励改成不会丢的形式或二次补发。

## P3-5 意志之光（Will Wisps）/ 复苏仪式（Restoration）端到端（世界 p3，HEAD `0f0512d`）
- **为什么上一轮「净化后复苏仍 false」：设计，不是 bug。** 复苏需要 3 枚意志之光（`WillWisp` 位 7）：`HEART`（击杀腐光之心逃生时在场）、`WING`（击杀腐光母翼 `storm_altar`）、`EARTH`（**累计 3 处不同腐化地面**净化后才出）；且仅在**大灵树心**（`great_tree_heart`，不是小灵树 `spirit_tree_heart`）使用，且需觉醒+精灵形态。上轮只净化了 1 处、没有另外两枚，状态就是 `restored:false`。
- 真实走完整条链 **[通过]**：已有 HEART+WING（mask 3）→ 三处腐化地面（`locate` → `ori blight nearest` → 灵击之锤 4–5 下，600 tick 净化后各 +60 灵光、`purify/…` 奖励键 ×3）→ 第 3 处净化后 `EARTH` 意志之光在核处生成（`pending=[earth]`，位置 `(-321.5,69.35,627.5)`，截图 `earth-wisp`），走过去 3 格即拾取 `mask 3→7`、`wisps.purifications=3`。
- 大灵树（origin `(136,70,344)`，heart `(138,95,345)`）：入口 `(136,70,320)` 前有 1 格高 `great_spirit_root`（需跳上，纯 W 的 `walk_route` 会 `blocked`；用「走+疾跑+卡住跳」脚本，约 20 s 走完 22 格上升到心室 `(135.5,93.9,343.8)`）。对准 heart 真实 `use`：`restoration.active=true`，300 tick（15 s）倒计时，镜头光环/光柱/树冠开花（截图 `restore-sheet`），结束 `completed:true / restored:true`、`bloomChunks 171 / writes 256`、+500 灵光（532→1032）、成就 toast；再次使用 → 拒绝（`complete`），不会重复发奖 **[通过]**。
- 截图序列：`restore-before / restore-3s / restore-8s / restore-13s / restore-sheet`、`restore-outside`。
- **[信息]** `spirit_core` 在手上右键（空气或被拒绝的方块上）会切换精灵形态——我测复苏后“莫名变回人形”就是这个（设计，但手持核时点已完成的 heart 会顺带切形态）。
- **[复现，minor] `locate structure orimod:blighted_grounds` 指向的点不一定有腐化地面：** 5 个定位点中 4 个在 34–43 格内有核，`(432,-144)`（该世界）站到中心、512 格内 `ori blight nearest` 无核、地面是正常桦木林（preflight 拒绝了摆放，但结构起点仍被 locate 记录）。玩家按地图石/导引去会扑空。
- **[信息]** 手动 `setblock orimod:gloom_core` 的核可被锤破，但没有区域登记 → 不触发净化、不给灵光（只掉一个物品）。每次命中有约 48 tick 冷却（连点相隔 24 tick 只有一半命中）。
- **[复现，minor] 精灵形态忽略「缓降」药水的自由落体减速：** 人形 slow_falling 下降速每 tick +0.01（15 tick −0.14），精灵形态 −0.37（15 tick）→ −0.57（25 tick），与未加药水时一致；仅滑翔时才受影响。（可能是有意：精灵坠落曲线自定义；但药水/属性类玩法应有说明。）
- 日志：期间出现 3 次客户端 `Received synced attachments from unknown entity`（客户端先于实体收到附件同步，复活/传送时），4 次 `Can't keep up`（llvmpipe 高负载），无模组异常 **[信息]**。

## P3-6 光平台 / Bash 与抓钩 / 裂墙裂地 / 技能打腐化之核（平台 `(0,200,0)`，真实按键）
按键对应（默认绑定，易混）：`Z = key.orimod.light_burst` → 技能槽 1（默认「再生」），`X = key.orimod.charge_flame` → 槽 2（默认**光爆**），`H = key.orimod.flash` → 槽 3（默认**蓄力灵焰**）；`R` Bash/抓钩；`C` 冲刺，空中「潜行+C」= 下砸（Stomp）。**[信息/可用性]** 键名与实际技能不一致（见 P2-B），玩家在控制设置里看到「光爆=Z」实际却是再生。
- **光平台 `spirit_platform`** **[通过]**：默认虚影无碰撞；光爆(X) 在 5 格半径内把 5 块全部点亮（+4 tick 内），保持 100 tick，最后 30 tick `fading`+`blink` 交替闪烁，然后消失；站在上面等它到期 → 平台熄灭后玩家掉下（104 tick 时 y 201→200.48→200）。点亮的 bash_anchor（灯笼）也会点亮 6 格内平台（与锚同高差 1 时边缘 6 格那块因距离平方 37>36 不亮，正常）；锚熄灭后平台仍会多亮一段（续租 100 tick）。槽 1 再生、槽 3 蓄力灵焰不点亮平台（设计：只有 Flash/Burst/灯笼/灵树）。
- **Bash 锚** **[通过]**：正前方 3 格锚，点按 R 3 tick 即弹射（越过锚 ~5 格）；按住 20 tick 进入 `bash_aim` 再发射；斜上 45° 锚把人弹到 y+3.9、水平 14 格；**身后**的锚也会被选中（「Bash 全向就近」设计）；中间隔石墙 → 不选中；7 格外（>6）→ 不触发；空按无目标 → 无事。4 个锚连续每 6 tick 狂按 R 120 tick：一路弹到 z=10.7，`predictionRejections=0`。
- **抓钩花 `grapple_flower`** **[通过]**：前上方 5 格，R → `grapple`→`grapple_end`，拉到 y+3.2 后落回地面。
- **[信息] 客户端 `movement.bashes / grapples` 计数始终为 0**（模式切换正常），调试统计字段可能只在服务端累计，devtools 读数误导。
- **裂墙 `cracked_ruin_wall`（7×5=35 块）**：光爆(X，6 格外) → 35 块全塌；蓄力灵焰(H 按住 16–20 tick 松开) 距离 2.5 格 → 全塌，**4.5 格外无效**（半径 3.5，设计）；灵击之锤贴近 → 全塌；灵矛（热栏，点按 use）6 格外命中 → 全塌；刃攻击、Bash → 无效 **[通过，符合 `CrackedRuin.breaks` 规则]**。**[工具注意]** `select_slot` 只能 0–8，矛默认在背包 10 号位，第一次测“矛无效”是因为没在手上。
- **裂地 `cracked_ruin_floor`（7×3）**：光爆/锤 → 不破（设计：地板只认 Stomp）；从 12 格高「潜行+C」下砸 → 21 块全塌、玩家掉到 y=196 **[通过]**。
- **技能打真实腐化之核（`(-333,70,-729)`，40 HP）**：蓄力灵焰 −12、光爆 −8、灵矛 −6、刃 0、灵弓箭 0、下砸 0（只清荆棘）、锤 −10 **[通过，与 `BlightRules.damage` 一致]**。锤在 0.8/1.5 格内都能打到。（中途一次锤距离 2.5 格没掉血，未复现，疑为切栏后输入未到，记 [怀疑/未复现]。）

## P3-7a creek_ruins v1 手玩（HEAD `b8de170`，客户端重启后同世界 p3）
站点：`orimod:creek_ruins` origin `(-110,106,128)` CW90，bounds x -184..-102 / y 82..149 / z 120..180，layoutVersion 1。模板坐标经旋转换算：神龛 `(-132,116,139)`、安全点 `(-134.5,116,140.5)`、Bash 垫 `(-145,121,140)`、滑翔终点 `(-164,125,146)`、能量格台 `(-165,125,148)`、上升气流 `x -155..-152, z 144..146, y116`、暗门 `(-154,115,135)`。
- 外观（远景/鸟瞰 `p3-creek-overview-a/b`）：庭院、水渠、两棵开花树、拱门、坡边过渡自然，**未见悬空/插进地形的遗迹块** **[通过]**。夜间神龛与水渠可辨认，不算全黑 `p3-creek-night-sheet` **[通过]**。
- 真实输入步行：入口 `(-109.5,116,150.5)` → 神龛 `walk_route` 36 点 189 tick 无卡 **[通过]**；神龛 → Bash 高台：要多次「卡住就跳」（台阶 1 格），**不用 Bash 也能走上 Bash 垫所在的 y121 高台** **[信息/设计]**——Bash 垫只是落点判定，可以绕开 Bash。
- 挑战流程：神龛 `use` → RuinScreen「开始挑战」（要求 Bash+滑翔，限时 90 s）→ 留在安全点开始。
  - **起步 Bash 不顺手 [复现，minor/关卡手感]**：锚 `(-139,119,140)` 立在 1 格高 `great_spirit_root`（y118）上。从地面（y116）起跳+Bash，朝高台方向（俯仰 −15/−20）发射会被锚下的根块挡住、原地掉回；俯仰 −45/−60 只能把人弹到根顶 y119（垂直只 +2）。实际可行路线 = **两次 Bash**：先跳+Bash 上根顶，再在根顶跳+Bash（−30°）飞到 y121 高台（`pt116p3-creekbash*.json`）。第一次挑战因摸索超时：**90 s 超时 → 结束会话、回安全点** **[通过]**。
  - 第 2 段：高台助跑→二段跳→按住滑翔→进上升气流悬停升到 y≈129 → 朝西滑到终点：完成「溪谷灵迹 完成！用时 10.75 秒，获得 30 灵光」，灵光 1047→1077 `p3-creek-after-glide4` **[通过]**。直接掠过气流柱只升 ~1 格，必须在柱内停住（柱宽 3×4），设计如此。
  - **[复现，minor] 开始挑战不检查「要求」能力**：本存档当时未学滑翔（只有 dash/bash）也能开挑战，到滑翔段才发现做不了，只能等超时或手动「退出当前挑战」。建议开始前提示缺哪个能力。
  - 挑战中掉进下层庭院（y116，非水）不会重置，只有水/离开路线/超时才回起点；路线外地面都是庭院内，**不卡死**（走回神龛可「退出当前挑战」）**[通过]**。
- 能量格台 `energy_cell_pedestal` `(-165,125,148)`：use 一次得 1 枚能量格，再 use 不再给 **[通过]**；地图石 `(-121,116,136)` use 无界面（本世界已领过，见 P2-E）**[信息]**。
- **[复现，major（装 Iris 时）] 默认键位冲突：Bash = R 与 Iris「重新加载光影包」= R 同键。** 本运行时 `options.txt` 里 `key_iris.keybind.reload:key.keyboard.r` 与 `key_key.orimod.bash:key.keyboard.r` 同时存在，按 Bash 时聊天栏出现「已重新加载光影包！」`p3-creek-after-bash`（开着光影时每次 Bash 都会整包重载、卡顿）。`SpiritKeyDefaults` 只在 `keyDefaultsApplied=false` 时迁移一次 Iris 键；该标志已为 true（之前跑过），之后 options 被重置/后装 Iris 就不会再迁移，也无冲突提示。
- 日志：仅 1 条 `Can't keep up`（加载世界时），无模组异常。

## P3-7b forest_spring v1 手玩（重建 `0aaf077`）
站点：origin `(-2730,61,-256)` CW90，bounds x -2792..-2722 / y 45..92 / z -264..-210，layoutVersion 1。换算：神龛 `(-2747,67,-249)`、安全点 `(-2745.5,67,-245.5)`、垫 0 蹬墙 `(-2754.5,69,-246.5)`、垫 1 二段跳 `(-2757.5,71,-247.5)`、垫 2 冲刺 `(-2761.5,69,-248.5)`、能量格台 `(-2770,67,-222)`、风暴祭坛 `(-2776,67,-237)`、钥石台 `(-2759,71,-249)` / `(-2759,69,-237)`、暗室裂墙 `x -2769..-2767, z -246, y67–69`。
- 外观（`p3-fs-overview-a/b`）：泉池、庭院、拱门、树冠、坡边过渡正常，未见悬空/插地块 **[通过]**。
- 真实输入步行：入口 `(-2729.5,67,-236.5)` → 安全点 `walk_route` 26 点，无需跳，无卡 **[通过]**。
- 挑战（RuinScreen 文案：「要求：冲刺。依次在根墙蹬墙后落到第一座光台；使用二段跳到第二座；冲刺抵达终点」）：真实按键（西向前进 + 每 9 tick 跳一次）从 `(-2748.5,67,-246.5)` 起 **只用跳/二段跳 30 tick 内连过垫 0 和垫 1**，蹬墙不是必需；从垫 1 跳+冲刺会越过终点落到西侧墙顶（y72），回走落到垫 2 → 完成 **[通过]**（`pt116p3-fs4/5/6-*.json`，`p3-fs-finish`）。
  - **[复现，minor/文案] 「要求」只写冲刺**，而路线说明要蹬墙和二段跳；开始时同样不检查能力（同 P3-7a）。
  - **[复现，minor/关卡] 暗室没有顶**：裂墙 `cracked_ruin_wall`（3×3，需光爆/锤破）后面的暗室四壁只到 y71–72、内部 y72 以上全空。我在挑战中从垫 1 继续二段跳+滑翔，**越过墙顶直接掉进暗室**（`(-2768.5,67,-246.5)`，`p3-fs-after-cp`），绕开了「打破裂墙」这一步；掉进去后出不来时只能等挑战超时或蹬墙出去。
  - 挑战中途（同 P3-7a）用 `/tp` 不会取消会话；落在庭院地面（非水）不重置。
- 能量格台、风暴祭坛、两个钥石台方块都在（`inspect_block`）**[通过]**；BFS（模板）显示能量格台 `(34,6,40)` 纯步行不可达，未实测怎么上去 **[未测]**。
- 野外：站点周围白天有 `gloom_beetle`（布局 beetles 刷点，设计）。

## P3-7c 林间前哨（Glades outpost）商人（`(-3280,~,-560)`，入口 `(-3280,69,-545)`、泉井 `(-3261,69,-545)`）
- 两位商人 `glades_merchant`（制图师路路 `(-3266.5,69,-552.5)`、碎片商苔语 `(-3266.5,69,-536.5)`），对准实体按真实 `use` 打开 MerchantScreen **[通过]**。
- 制图师：三条指引（未完成遗迹 40 / 未领取光祖之树 60 / 未净化地带 50）连点三次 → 灵光 1168→1018（**三笔都扣了 150**），但同时只能有一条指引，最后剩下的是「腐化地带 `(-2633,67,-379)`」**[复现，minor]**：前两条指引买完就被覆盖、钱白花，也没有「将替换当前指引」提示。另外每次点完提示都是「正在寻找附近尚未通关的灵迹……」（异步定位），三种指引用的是同一句文案 **[cosmetic]**。
- 碎片商：已拥有全部碎片时显示「你已拥有全部碎片」**[通过]**。`/ori reset` 后**同一天内**仍显示「已拥有全部碎片」（`GladesMerchant` 按「日+玩家」缓存库存，缓存时已拥有全部，reset 后不刷新）**[复现，cosmetic/仅调试命令触发]**；`time add 24000` 后出现 3 个碎片（120/100/160）。
- 购买「灵能吸取」120：灵光 300→180，「购买成功」，该行变为「已经解锁，无需再次购买」、按钮 `active=false`，再点无扣费 **[通过]** `p3-shard-bought / shard-rebuy`。第三行说明在面板里被截成「…反弹飞行中投射物的速…」，悬停有完整提示 **[cosmetic]**。
- 测完恢复：`/ori unlock all`、灵光 +1000。
- 补记：forest_spring 挑战完成消息「林间灵泉 完成！用时 21.25 秒，获得 30 灵光」（聊天记录 `p3-bed-tp`）。

## P3-8 原版物品 / 玩家动作（精灵形态，生存，平台 `(0,200,0)`）
**[工具注意]** devtools `key` 动作长按 `key.use` 不会让原版物品进入使用状态（弓/弩/三叉戟都没反应）；改用 `input_script` 的 `use` 按下/松开才正常。下面结果都是 `input_script`。
- 末影珍珠：飞 13 格、传送、扣 5 点 **[通过]**；紫颂果：吃完随机传送（一次从 y200 平台传到地面 y119，原版行为）**[通过]**。
- 不死图腾（副手）：`/damage 50` → 剩 2 HP + 吸收/抗火/再生，图腾消耗，形态保持 **[通过]**；虚空里图腾不生效（原版：虚空伤害绕过图腾），重生后保留觉醒/形态 **[通过]**。
- 桶：水桶放水、空桶收回，精灵/人形都正常；岩浆桶放/收正常 **[通过]**（第一次失败是被水流推走，测试问题）。
- 盾：主手盾按住 use → `usingItem=true` 持续 100 tick；一人称举盾正常。第三人称正面：**盾牌是原版人形尺寸，把 1.2 格高的精灵整个挡住**（`p3-shield-tp-zoom`）**[cosmetic]**。副手盾一人称占了左半个屏幕 `p3-offhand-shield-empty` **[cosmetic，主观]**。
- 原版弓、弩（装填后再按射出）、三叉戟投掷：精灵/人形都能射出，消耗箭 **[通过]**。激流三叉戟：站水里精灵/人形都能激流（上冲约 6 格，`spinAttack=true`）**[通过]**；雷雨天在平台（y200）和 y119 山地都不湿，没法测「雨中激流」（高处/寒冷群系下雪不下雨，原版）**[未测]**。
- 钓鱼竿：抛竿出浮标、再按收回 **[通过]**。
- 进食：苹果/紫颂果吃得下去、物品消耗；**精灵形态饥饿值不变**（`SpiritPlayerFoodMixin` 让精灵形态不吸收营养、hunger 255 也不掉饱食度），设计如此 **[信息]**。
- 睡觉：`use_block` 床 → `sleeping=true`、`pose=SLEEPING`，第三人称精灵躺在床上 `p3-bed-tp` **[通过]**；本世界 `doDaylightCycle=false`，100 tick 后被叫醒但不跳夜（原版规则），跳夜没法测 **[信息]**。
- **[复现，minor] 精灵形态一人称里火把不显示**：火把在主手或副手时，手是空的（右手空手姿势/左手空），换人形就看得到；副手图腾、苹果、盾、灯笼都看得到（灯笼大半在屏幕外）。`p3-offhand-sheet.png`、`p3-offhand-sheet2.png`。
- 溺水：精灵形态在 17 格深的水底静止 400 tick，`air` 一直 300、不掉血——`WaterMovement` 把精灵形态的空气补满，**精灵不会溺水**（设计，对标 WotW）**[信息]**。按住跳 + 冲刺：约 40 tick 上浮 16 格出水 **[通过]**。
- 岩浆（3 格深池，池边与岩浆面齐平）：精灵形态按住跳+前进+每 12 tick 冲刺，和人形一样只浮起约 1.5 格、约 50 tick 死亡；**冲刺在岩浆里没有任何位移**（`lastAction=dash` 但坐标不变）**[信息]**，精灵不比人形更难，也不更容易逃出。
- 日志：全程无模组异常。
