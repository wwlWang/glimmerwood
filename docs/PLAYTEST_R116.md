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
