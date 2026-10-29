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
