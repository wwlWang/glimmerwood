# R112 动作运行时盘点

基线：`a06a6ba`，角色 v013；静态阅读于 2026-10-22。本文只记录运行时接线、资产契约和建议，不修改代码/资产，不启动 Gradle、客户端或 Blender。官方 BF/WotW 动作参考与新片段造型由 Claude 另行评审；下文“可用”仅表示存在可达实现，不代表已符合原作或通过本轮观感验收。

实读 `ori.glb`、`art/source/character/manifest.json` 和 `animation_contract.json`：**三者都是58项且名称集合相等，不是63项**。GLB SHA-256：`e70ef449a124909f2ca50756f30fa51a7abcaf48f74f6eddfade989d08e9a1b4`。文末列出全部58项。

等级：**缺失**＝没有专属状态/动作，或本应出现的动作被错误分支/遮罩吞掉；**借用**＝用另一动作、通用交互或程序替代；**可用**＝已有对应片段或明确的程序实现。建议 clip 名是候选接口名，不是已批准的资产清单。

## 先修接线，再评片段的关键发现

1. **陆地爬行确实被当成游泳。** `SpiritAnimation.java:147`直接接受`isVisuallySwimming()`，没有排除`isVisuallyCrawling()`。MC/NeoForge 1.21.1 的后者本来就是前者加“不在可游泳流体中”。因此1格缝隙中的爬行进入`family=swim`、`next=swim`，实际`idle + SpiritPoses.swim + bodyPitch/bodyDrop`，不是爬行动作。静止时程序俯角目标仍约1 rad，不能把它视作水平贴地爬行。
2. **完整`run`不是当前跑步成品。** `next`始终是`walk`；没有overlay时`gaitMask=belowUpper`，`run`的spine子树关键帧不参与，上肢仍来自walk/持握程序层（root等上游变换仍会影响全身）。而walk基础权重1未扣除runWeight，最大runWeight=1时，两份override在重叠骨骼上归一为各1/2，然后再加`runPose`和`secondary.gait`。所以单独修改v013的run摆臂，不等于正常游戏跑步就使用它。接入：`SpiritAnimation.java:428`、`:437`、`:453`。
3. **跳跃也不是纯rise/fall。** `next=apex`基础权重1，随后叠加rise/fall，未用`airWeights()[1]`降低apex。最大rise或fall权重1时重叠骨骼仍各占1/2；再叠加全身缩放、倾斜和腿部收拢。接入：`SpiritAnimation.java:458`。上述归一规则已只读核对本地GemRender 0.1.17的`Crossfade.write/emit`、`BlendEvaluator.accumulate/finish`字节码；它不是后写层直接覆盖前写层。
4. **R55墙面向上攀爬不会选择`climb`。** 运动仍发布`WALL_SLIDE`，视觉只以`abs(vy)<.03`区分hold/slide；向上爬也播wall_slide。原版梯子/藤蔓/脚手架的`onClimbable()`才选climb。接入：`SpiritMovement.java:157`、`SpiritAnimation.java:211`、`:217`。
5. **特殊状态统一禁止手部overlay，覆盖面过大。** hurt/recover、swim/spin、elytra、原版climb、wall hold/slide、glide、bash、grapple、stomp、dash、double/wall jump等均不在`handsAvailable`白名单。普通空中apex却允许武器overlay。部分状态并未被服务端战斗规则禁止（例如普通攀爬、游泳、墙附、滑翔），可能“伤害有了，挥砍没了”。此外水中hurt即使选中，swim程序仍占主要骨骼，受伤不易看见。接入：`SpiritAnimation.java:271`、`:424`；`SpiritCombat.java:39`、`:44`。
6. **借用片段比注释显示的还多。** 原版普通攻击/挖掘/放方块/Q/雪球/珍珠/鱼竿挥手均落入`getAttackAnim`→单臂blade_1；锤蓄力/释放借blade_3；灵矛挥手借blade_3。`SpiritAnimation.java:341`的“正在使用灵矛”分支在当前`SpiritSpearItem`正常输入下不可达：该物品即时投掷，不`startUsingItem`，也没有SPEAR UseAnim。
7. **受伤只有统一0.3秒hurt和0.25秒recover选择窗口。** hurt资产9帧（30fps，0.3秒，含端点则10次采样），不是9个游戏tick；没有方向、伤害类型、强度、硬直或击飞状态。recover资产0.4秒，正常选择窗口仅0.25秒。骑乘/睡眠/激流优先于hurt；水中还有程序覆盖问题。接入：`SpiritAnimation.java:176`、`:200`。
8. **新增GLB片段不登记契约会静默回idle。** `SpiritClips`只缓存`AnimationContract.REQUIRED`，`get()`不向模型查额外名字。只加manifest/GLB不够，且这种“有片段但不在契约”的名字不会出现在missingClips警告中。

## 源码索引与观察范围

表中的`文件.java:行`均以本次基线为准，主要路径如下：

| 简写文件 | 路径/职责 |
|---|---|
| SpiritAnimation.java | [client/render/SpiritAnimation.java](../src/main/java/cn/wwl/orimod/client/render/SpiritAnimation.java)，base、overlay、各程序层调度 |
| SpiritPoses.java / SpiritPoseLayer.java | [SpiritPoses](../src/main/java/cn/wwl/orimod/client/render/SpiritPoses.java)、[SpiritPoseLayer](../src/main/java/cn/wwl/orimod/client/render/SpiritPoseLayer.java)，程序姿势/骨骼遮罩 |
| SpiritSecondary.java / SpiritArmPose.java | [SpiritSecondary](../src/main/java/cn/wwl/orimod/client/render/SpiritSecondary.java)、[SpiritArmPose](../src/main/java/cn/wwl/orimod/client/render/SpiritArmPose.java)，附属骨骼/腿部弹簧、手部接触IK |
| SpiritPlayerVisual.java | [SpiritPlayerVisual](../src/main/java/cn/wwl/orimod/client/render/SpiritPlayerVisual.java)，世界角色、整体旋转缩放、诊断 |
| FeelTuning.java | [movement/FeelTuning.java](../src/main/java/cn/wwl/orimod/movement/FeelTuning.java)，步态/速度映射/程序层常量 |
| SpiritMovement.java / MoveMode.java | [SpiritMovement](../src/main/java/cn/wwl/orimod/movement/SpiritMovement.java)、[MoveMode](../src/main/java/cn/wwl/orimod/movement/MoveMode.java)，服务端运动状态 |
| SpiritControls.java / SpiritMantle.java | [SpiritControls](../src/main/java/cn/wwl/orimod/client/SpiritControls.java)、[SpiritMantle](../src/main/java/cn/wwl/orimod/client/SpiritMantle.java)，本地运动/翻沿 |
| SpiritCombat.java | [combat/SpiritCombat.java](../src/main/java/cn/wwl/orimod/combat/SpiritCombat.java)，三连时序和COMBAT_VIEW |
| SpiritClips.java / AnimationContract.java | [SpiritClips](../src/main/java/cn/wwl/orimod/client/render/SpiritClips.java)、[AnimationContract](../src/main/java/cn/wwl/orimod/asset/AnimationContract.java)，解析与回退 |

本表主体是**世界中的第三人称Ori躯体**。第一人称`SpiritHands.java:38/48/70`使用独立的`paw_*_{open,grip}.glb`静态手网格和原版equip/use/swing矩阵，不执行`SpiritAnimation`；新增全身clip不会自动改变第一人称动作。背包`SpiritInventory.java:64`另走idle + hold左右 + lowered程序姿势，也不执行世界状态机。第一人称光弧`client/fx/BladeSlash.java`、手持武器伸缩/发光、屏幕受伤反馈和粒子不是全身骨骼clip。

## Base：实际优先级与可达选择

`SpiritAnimation.write()`逐帧读取运动/玩家状态；下面按`next`的if/else顺序列出，**前一行可压过后一行**。只有最后的普通空中与地面分支令`special=false`。普通淡入0.2秒，快速切换0.09秒；片段时钟按帧dt积分（dt上限0.1秒），不是所有动作都与服务端计时器严格同步。

| 条件 / next | 实际片段与层 | 等级；候选改进 | 接入点 |
|---|---|---|---|
| `!isAlive` → death | death非循环，停末帧；不加secondary；无方向分类 | 可用；若设计需要可增death_air/death_water | SpiritAnimation.java:202、:539 |
| 非精灵、仍在10tick外观过渡 → transform_out | 0.5秒片段；可见性由SpiritAppearance控制 | 可用；保留 | SpiritAnimation.java:203；SpiritAppearance.java:29 |
| 睡眠 → sleep | sleep循环，模型yaw取床方向 | 可用；起床单独wake可选 | SpiritAnimation.java:204；SpiritPlayerVisual.java:165 |
| 任何乘客 → sit | 所有载具同一sit；无武器overlay | 借用；ride_mount/ride_seated/row | SpiritAnimation.java:205 |
| 激流spin → swim | 实际idle + swim/dart=1 +整体纵轴旋转 | 借用；riptide程序家族独立命名 | SpiritAnimation.java:206、:407；SpiritPlayerVisual.java:177 |
| 受伤后<.3秒 → hurt | hurt；连续伤害只更新lastHurtAt，同名next不自动重开clip | 借用；hurt_front/back/left/right、hurt_air，独立事件序号 | SpiritAnimation.java:176、:207、:242 |
| 随后<.55秒 → recover | recover实际最多约.25秒 | 借用；按受伤方向/强度接回基础姿态 | SpiritAnimation.java:208 |
| swim family → swim | **idle**作为base，21骨骼程序swim覆盖，整体俯仰 | 借用；区分swim_surface/swim_underwater/crawl | SpiritAnimation.java:209、:233、:467 |
| 鞘翅 → elytra | 实际glide，之后glide程序姿态覆盖，整体沿视角俯仰 | 借用；elytra_loop/elytra_flare或专属程序姿态 | SpiritAnimation.java:210、:233、:551 |
| 原版onClimbable → climb | climb循环；播放速度clamp(abs(vy)*10,.2,2)，上下均正播 | 可用但粗糙；climb_idle/up/down，静止停相位 | SpiritAnimation.java:211、:238 |
| bash_aim | bash_aim循环；暂无按目标方向伸手的接触约束 | 可用；方向/目标程序层 | SpiritAnimation.java:212 |
| grapple | grapple循环；不按绳端/抓取点做手部IK | 可用；grapple_reach/pull/release | SpiritAnimation.java:213 |
| stomp | stomp循环；没有独立下砸落地专属clip | 可用（下行）；补stomp_land | SpiritAnimation.java:214 |
| dash | dash_start非循环，超过.3秒则停末帧 | 可用；dash_loop/exit可选 | SpiritAnimation.java:215 |
| 本地mantle | 未过沿climb（2.4倍），过沿land_light；另有倾身/收腿/缩放 | 借用；mantle_rise/over，与progress绑定；同步给远端 | SpiritAnimation.java:143、:216、:238、:581；SpiritMantle.java:26 |
| wall_slide，abs(vy)<.03 | wall_hold循环（包括Sticky挂壁） | 可用；增加墙面法线对齐/接触 | SpiritAnimation.java:217 |
| wall_slide，其余vy | wall_slide循环，**向上爬也在此** | 借用；wall_climb_up/down，与运动方向分支 | SpiritAnimation.java:217；SpiritMovement.java:157 |
| shot未过期 | 表下的one-shot | 可用；改独立事件优先级，避免同帧覆盖 | SpiritAnimation.java:218、:640 |
| glide | glide循环 +程序glide +整身.42rad俯角 | 可用（存在双重制作层）；统一clip/程序分工 | SpiritAnimation.java:219、:467；FeelTuning.java:118 |
| 非地面且不在水中 | next=apex，rise/fall override按vy混合 +弹簧 | 借用（并非纯rise/fall）；修权重后再评跳跃 | SpiritAnimation.java:220、:458 |
| 进入精灵<.6秒，且走到地面分支 | transform_in；会被空中/水中/shot等更高优先级挡住 | 可用；若要求必播则独立变身层 | SpiritAnimation.java:226；SpiritAppearance.java:24 |
| 潜行 | crouch循环，不按行走距离推进脚步 | 借用；crouch_idle/crouch_walk | SpiritAnimation.java:227 |
| 地面水平速度>.22m/s | next=walk +run/sprint混合及程序层 | 借用；修步态权重/遮罩；再决定sprint | SpiritAnimation.java:191、:228、:428 |
| 地面静止，距上次移动≤9秒 | idle | 可用；保留 | SpiritAnimation.java:229 |
| 地面静止>9秒 | alert（不是敌人感知触发） | 可用；如需战斗警觉，另接combat_alert | SpiritAnimation.java:197、:229 |

### One-shot触发（只存一个shot槽）

| 触发 | clip / 保留窗口 | 等级；建议 | 接入点 |
|---|---|---|---|
| 新预测/新action：jump | jump_start .15秒，.3秒资产压缩2倍，起跳后约3tick | 可用；不要延长成空中蹲伏 | SpiritAnimation.java:160、:168、:244 |
| double_jump | double_jump .5秒 +squash/secondary.jump | 可用；翻转轨迹和上层遮罩需实拍 | SpiritAnimation.java:165 |
| wall_jump | `movement.left<0 ? wall_jump_r : wall_jump_l`，.4秒 | 可用但方向借输入；建议按墙法线/蹬离方向选 | SpiritAnimation.java:166 |
| bash_launch | bash_launch .4秒 | 可用；姿势未按全3D发射矢量独立对齐 | SpiritAnimation.java:167 |
| 离开dash且新mode非stomp | dash_end .25秒（资产.3秒） | 可用；统一结束时序 | SpiritAnimation.java:172 |
| 进入/离开glide（离开时仍空中） | glide_open/glide_close .25秒（资产各.3秒） | 借用/部分被程序覆盖；glideMix上升期间会逐渐挤掉open主要骨骼 | SpiritAnimation.java:173、:424 |
| 落地、非水中 | vy<-0.6时land_heavy .5秒，否则land_light .3秒；快跑落地另走masked landRun | 可用；按强度/移动方向做land_run，不必新增每档 | SpiritAnimation.java:177 |
| 从静止变移动 | start .12秒，.3秒资产压缩播放 | 可用；移动检测是实际位移，也会响应推力 | SpiritAnimation.java:196、:244 |
| 从移动变静止 | stop .30秒（资产.4秒） | 可用；可加skid/急停条件 | SpiritAnimation.java:195 |
| 静止、地面、无shot且角速度>75°/s | turn_left/right .5秒 | 可用；按转角幅度驱动 | SpiritAnimation.java:197 |

`trigger()`对同名.22秒内的请求去重，但不同shot直接覆盖；本帧顺序为动作边沿→mode边沿→受伤清shot→落地→start/stop→turn。没有并行事件队列。`LAUNCH / GRAPPLE_END / STOMP_LAND / RESUME / RELOCATE / JUMP_REJECT`没有专属shot处理，见`MoveMode.java:7`及`SpiritAnimation.java:164`。传送>3格或帧时差>.25秒会重置部分观察值/清shot，不能把跨传送采样误判为丢clip（`:116`）。

## Overlay：武器、使用物品与普通挥手

允许overlay的状态：所有`special=false`（普通空中、地面walk/crouch/idle/alert/transform_in），以及白名单`start/stop/turn_left/turn_right/jump_start/land_light/land_heavy`。其余禁用；mantle过沿因借land_light反而允许overlay，而上升climb不允许。

左右手约定：模型朝glTF +Z，`*_l`在-X，是**玩家右侧**；`*_r`是玩家左侧。无后缀交互clip为右手，`_left`为左手；`SpiritAnimation.java:638/652`、`SpiritPlayerVisual.java:155`、`character_animations.py:438`一致，不能按骨名后缀直译。

| 优先条件 | 实际表现 | 等级；建议 | 接入点 |
|---|---|---|---|
| COMBAT_VIEW.mode=blade | blade_1/2/3及_left，上半身spine子树；服务端startedAt/stage决定scrub，swingCurve重映射；躯干扭转/前倾kick/耳尾flick，第三刀squash | 可用；空中/上下斩另分clip；保留伤害时序 | SpiritAnimation.java:303；SpiritCombat.java:28 |
| 正在装弩 | hold/_left单臂 +SpiritArmPose.crossbow双手接触，装填比例取实际chargeDuration | 借用（有专属程序接触）；可保留IK，补crossbow_load/release | SpiritAnimation.java:292、:322；SpiritArmPose.java:112 |
| 正在拉弓（原版/灵弓） | <1秒bow_charge，否则bow_hold；上半身；俯仰/偏航瞄准、极端角度接触IK | 可用；确保新clip保留瞄准/双手接触契约 | SpiritAnimation.java:325、:505；SpiritArmPose.java:41 |
| 正在蓄灵锤 | blade_3前22%随12tick蓄力scrub并微动；上半身、后仰/扭腰 | 借用；hammer_charge/hold | SpiritAnimation.java:329；SpiritHammerItem.java:30 |
| 正在用灵矛（代码分支） | use_item采到95%停住；**当前物品正常路径不进入** | 缺失（死接线）；删误导分支或接真正的spear_charge（若设计新增蓄力） | SpiritAnimation.java:341；SpiritSpearItem.java:19 |
| 原版SPEAR UseAnim（蓄三叉戟） | 日志overlay=throw_ready/_left，**不是GLB clip**；程序throwReady单臂抬到肩旁 +扭腰 | 借用；trident_charge/release，保留肩旁握点 | SpiritAnimation.java:345、:476；SpiritPoses.java:101 |
| 其他持续使用：BLOCK | block/_left单臂 | 可用；方向性block_hit可另增 | SpiritAnimation.java:351 |
| 其他持续使用：EAT/DRINK/SPYGLASS/TOOT_HORN/BRUSH等 | use_item/_left单臂，按绝对now循环；仅望远镜另做接触IK | 借用；eat/drink/horn/brush，按使用开始时刻/周期驱动 | SpiritAnimation.java:351；SpiritArmPose.java:97 |
| 灵锤停止使用后的.32秒 | blade_3的22%→末帧，上半身；砸击扭腰/屈身/缩放 | 借用；hammer_slam/recover，成功释放与取消分开 | SpiritAnimation.java:264、:354 |
| 弓停止使用后的.3秒 | bow_release/_left，上半身，瞄准逐渐退回 | 可用但仅检测use边沿；bow_cancel与成功射击可分开 | SpiritAnimation.java:263、:368 |
| 普通swing且当前挥手物品是灵矛 | blade_3/_left，上半身、swingCurve+扭腰 | 借用；spear_throw，显式发布投掷事件避免靠swing猜 | SpiritAnimation.java:371、:374 |
| 其他普通swing | blade_1/_left，仅该臂；普通拳、剑、挖掘、交互挥手同路 | 借用；punch/mine/interact/toss，区分输入语义 | SpiritAnimation.java:383 |
| 静止持灵矛，无其他overlay | 轻微spine twist/arch +单手lowered | 可用（程序）；保留，和spear_throw衔接 | SpiritAnimation.java:388 |
| 任一自由手持物 | 不是hold GLB：cradle/单手lowered程序，.15秒渐变+步幅bob；诊断overlay会写hold/hold_left/hold_both | 可用；明确诊断名≠实际clip | SpiritAnimation.java:395、:412、:495；SpiritItemGrip.java:22 |
| 弩已装填但没正在使用 | 检测主/副手charged item，程序IK持续瞄准；不依赖GLB单独crossbow_hold | 借用；保留IK或提供专属持弩clip | SpiritAnimation.java:278、:292、:535 |

注意：bow/hammer release依靠“上一帧isUsing→这帧不再using”，未验证服务器是否真的生成箭/砸击。过早松开、能量不足、切物取消也可能出现释放外观（前提handsAvailable）；这属于需要确认的动画语义，不是本次修改战斗规则的授权。灵矛即时use在客户端返回success会走原版swing，服务端拒绝时也不能据动画断定成功。

## 程序层：单独看GLB时看不到的动作

| 层 | 输入与实际作用 | 等级；建议边界 | 接入点 |
|---|---|---|---|
| swimMix / bodyPose | .18秒渐变；21个指定骨骼rotation覆盖，keepOut从其他override挪出权重；base/swim clip被替换成idle | 借用；分crawl/water，确定新swim片段与程序只保留一套主姿势 | SpiritAnimation.java:403、:424、:467；SpiritPoseLayer.java:29 |
| cruise | 3D实测速度.8→2.6m/s映射0→1；.3秒平滑；划水频率.6→1.05Hz、漂浮角度→沿速度躺平 | 可用；细分水面与水下；上下方向应用速度俯角 | FeelTuning.java:115、:123、:131；SpiritAnimation.java:409 |
| dart | 速度11.5→15m/s收拢四肢，进入.06秒/退出.35秒；spin强制1；划水频率降到15%，整身伸长12% | 借用（速度推断水冲）；建议water_dash事件驱动+速度衔接 | SpiritAnimation.java:407、:410、:592；SpiritPoses.java:32 |
| glideMix / bodyPose | 精灵滑翔与鞘翅共享V形手臂、后拖腿姿势；缓慢摆动 | 借用；分羽毛抓握和无羽鞘翅程序/clip | SpiritPoses.java:55；SpiritAnimation.java:471 |
| 整体bodyPitch/bodyDrop | 水中沿速度；spin/elytra按视角；glide=.42rad；绕.5格中点倾转、最多下移.22格 | 可用；新增clip若自身root躺平，要避免再次旋转90° | SpiritAnimation.java:549；SpiritPlayerVisual.java:172 |
| walk/run/sprint | stride=.8/2/2.6格，walk相位控制run；sprint只有权重/步幅/前倾，没有sprint clip；现有权重/遮罩问题见前 | 借用；先修权重及上身来源 | SpiritAnimation.java:235、:428；FeelTuning.java:268 |
| runPose | additive胸前倾、头颈回补、抬大腿/折膝；无摆臂；硬编码run左膝相位.75/右膝.25 | 可用但依赖旧片段相位；新run必须重新核对，避免双重前倾/抬腿 | SpiritAnimation.java:452；SpiritPoses.java:114 |
| secondary.gait | 采样walk/run腿部32相位，按骨骼gain再加一遍偏离rest的旋转（thigh/knee/ankle） | 借用补强；新片段足够有力时应减弱/去掉，不盲叠 | SpiritSecondary.java:119、:130、:176 |
| airWeight / airLean / airScale / legTuck | apex上叠rise/fall，速度前倾、上升拉长/下落压缩，腿弹簧收拢；无方向/输入意图 | 可用程序但clip份额不对；修归一，再评jump形态 | SpiritAnimation.java:458、:575、:587；SpiritSecondary.java:322 |
| landRun | 快速落地保留walk/run，腿上采land_light中20%～65%段 +前倾/压缩kick | 借用；land_run专属过渡或速度参数化 | SpiritAnimation.java:179、:445 |
| mantle | 本地SpiritMantle.overLip二段：climb/land_light + lean/tuck/squash；没使用progress scrub | 借用；独立mantle两段、远端可读状态 | SpiritAnimation.java:143、:216、:581；SpiritMantle.java:28 |
| facing / leanPitch / leanRoll | 空闲时朝速度平滑转身，使用/战斗等busy时回身体yaw；按加速/转速/起落弹簧倾斜 | 可用；墙附对齐、受击朝向应单独处理；避免与新root转动叠加 | SpiritAnimation.java:547 |
| squash | Y缩放s，XZ=1/sqrt(s)；跳/落地/第三刀/锤释放kick；跑步bounce；水冲沿身体轴拉长 | 可用；新片段root缩放必须对账 | SpiritAnimation.java:587；SpiritPlayerVisual.java:169 |
| swingTwist / spineArch | blade阶段、锤蓄/砸、矛投的程序扭转/弓背；30/s平滑；叠在片段上 | 可用；新战斗clip要决定谁负责扭腰，不能双倍 | SpiritAnimation.java:303、:329、:354、:374、:490 |
| held / lowered / throwReady | 持物两侧独立三骨骼，步态bob；武器含锤/弓/弩改低位；普通小物胸前托举 | 可用；与新idle/武器ready共享握点契约 | SpiritAnimation.java:412、:476、:495；SpiritPoses.java:64、:81、:101 |
| look / bow aim / breath | 头yaw±45°，普通pitch[-40,45]；弓±85°、上半身aim、补偿片段固有yaw；地面低速breath | 可用；新弓片段改变固有yaw后需改补偿.12rad | SpiritAnimation.java:505 |
| 手部接触IK | 弓大俯角>25°矫正双手；望远镜贴眼；弩主手握持/空闲辅手随装填；解完写整姿态Snapshot | 可用；骨名/掌心轴不可只改资产不改IK | SpiritArmPose.java:41、:64、:112、:143 |
| secondary弹簧 | 每耳3节、每触须3节、尾5节，基于实测加速度/转向/落速/风流；含固定rest V/尾卷/触须后掠；除death均叠加 | 可用；新sleep/hurt/翻滚片段需检查是否应衰减；不是tail_sway GLB | SpiritSecondary.java:45、:73、:278；SpiritAnimation.java:539 |

mask只挪出override权重，后面的additive仍可再改变同骨骼。`bodyPoseMask`不含root，但含spine/四肢和附属骨骼根；因此水中hurt/滑翔open不是完全没采样，而是主要骨骼旋转被程序接管，不能以`baseClip`名称证明画面已表现出来。

## 原版MC状态逐项盘点

以下均假设玩家保持精灵形态；第一人称默认仍沿用原版手部矩阵，表内是第三人称躯体。缺少独立事件识别的项目必须先补运行时，再增加clip。

| MC状态 | 当前表现 | 等级 | 建议clip/程序层 | 接入点 |
|---|---|---|---|---|
| 爬行（1格缝，isVisuallyCrawling） | 陆地进入swim family，idle+划水/漂浮俯角；禁止持物overlay | 缺失（误分类） | crawl_idle/crawl_move；family先分crawl，不要只检查水中 | SpiritAnimation.java:147、:209、:551 |
| 潜行站立 | crouch循环 +一般呼吸/secondary/持物 | 可用 | crouch_idle可沿用 | SpiritAnimation.java:227、:531 |
| 潜行行走 | 仍crouch，不进入walk步幅，脚步不随位移 | 缺失 | crouch_walk及速度相位 | SpiritAnimation.java:227、:431 |
| 梯子 | onClimbable→climb；站住也至少.2倍播放，向下不倒播 | 可用（未分向） | climb_idle/up/down；按垂直位移，不按abs速度最低值 | SpiritAnimation.java:211、:238 |
| 原版藤蔓 | 同climb，无抓取点/墙法线IK | 借用 | 共用climb家族 +接触程序 | SpiritAnimation.java:211；SpiritArmPose.java（目前仅手持物接触） |
| 脚手架 | 同climb，包括支架内部，未区分水平站立/上升/下降 | 借用 | scaffold_move或climb family分支 | SpiritAnimation.java:211 |
| 游泳（冲刺/普通悬水） | visuallySwimming或在水中离地且非climbable均swim；idle+程序划水 | 借用 | swim_surface/swim_underwater；选择主动画来源 | SpiritAnimation.java:148、:233、:467 |
| 潜水 | 仍swim，bodyPitch根据3D运动方向俯转；无潜入/出水边沿 | 借用 | swim_dive/swim_surface_exit或水位过渡层 | SpiritAnimation.java:551；FeelTuning.java:131 |
| 水面静止漂浮 | 低速cruise=0，慢划水、俯角约57°；不是独立踩水clip | 借用 | tread_water / swim_idle；水面高度约束 | SpiritPoses.java:32；FeelTuning.java:115 |
| 水底站立/涉浅水 | 没有visuallySwimming且onGround时走地面walk/idle；无wade | 借用 | wade_walk可选 | SpiritAnimation.java:148、:224 |
| 激流riptide | idle+强制dart姿态，按视角躺平、-75°/tick纵轴旋转；保留原版旋风效果层 | 借用但可用 | riptide_start/loop/end或独立程序家族 | SpiritAnimation.java:147、:407；SpiritPlayerVisual.java:177；SpiritVanillaLayers.java:64 |
| 鞘翅 | glide clip+共享羽毛滑翔姿势，按视角躺平；原版鞘翅装备层保留 | 借用 | elytra_glide/brake；和羽毛抓握分开 | SpiritAnimation.java:210；SpiritVanillaLayers.java:99 |
| 骑马 | sit统一坐姿，无缰绳/身体随骑乘步态反应 | 借用 | ride_mount + reins接触（若需要） | SpiritAnimation.java:205 |
| 坐船 | sit，无专属坐船躯体、不能显示武器overlay | 借用 | ride_boat | SpiritAnimation.java:205、:271 |
| 船中划桨 | 仍sit，玩家手臂不读Boat paddle状态；船模型自身划桨不等于玩家划桨 | 缺失 | row_left/right或双臂程序同步划桨相位 | SpiritAnimation.java:205、:271 |
| 矿车 | 同sit，未区分矿车座位宽度/握持 | 借用 | ride_seated，可共用调好后的船/车坐姿 | SpiritAnimation.java:205 |
| 骑猪 | 同sit，无持胡萝卜钓竿专属骑乘动作 | 借用 | ride_mount +骑乘允许手部层 | SpiritAnimation.java:205、:271 |
| 睡觉/醒来 | sleep循环、按床yaw；醒来直接回普通base，无wake | 可用（睡）/缺失（醒） | wake可选，保留床方向 | SpiritPlayerVisual.java:167；SpiritAnimation.java:204 |
| 吃 | use_item单臂循环，没按食物接嘴做IK/咀嚼；第一人称仍原版吃动画矩阵 | 借用 | eat，手到口接触层 | SpiritAnimation.java:351；SpiritArmPose.java:71 |
| 喝 | 同use_item，与吃/工具使用无区别 | 借用 | drink，杯口倾转与嘴接触 | SpiritAnimation.java:351 |
| 盾牌格挡 | block左右单臂循环；无block_hit方向或破盾反应 | 可用（举盾）/缺失（受击） | block_hit/break，按事件驱动 | SpiritAnimation.java:351 |
| 弩装填/装好待射 | hold借用 +双臂IK拉弦/托弩；装好保持瞄准；射击无独立release片段 | 借用（装填程序可用） | crossbow_release；保留实际装填时长 | SpiritAnimation.java:278、:322；SpiritArmPose.java:112 |
| 三叉戟蓄力 | throwReady程序肩旁持枪；释放不在wasBow/wasHammer中，没有专属throw_release | 借用（预备）/缺失（释放） | trident_release；激流成功与普通投掷分开 | SpiritAnimation.java:345、:476；SpiritPoses.java:101 |
| 钓鱼竿甩出/收杆/等待 | 甩/收的原版swing借blade_1；等待普通cradle；线端点会跟实际握点 | 借用 | fishing_cast/reel/idle | SpiritAnimation.java:371、:383；SpiritHeldItems.java:72；SpiritFishingLineMixin.java:16 |
| 望远镜 | use_item +贴眼IK，身体/头随视角，遮罩淡入 | 可用（程序接触） | spyglass_hold可选；不要替换掉贴眼约束 | SpiritAnimation.java:292、:351、:535；SpiritArmPose.java:97 |
| 山羊角 | use_item，没有号角端口贴嘴或吹气姿态 | 借用 | horn_use +口部接触 | SpiritAnimation.java:351 |
| 刷子 | use_item，无刷扫往返节奏或刷头接触点 | 借用 | brush_loop/brush_start | SpiritAnimation.java:351 |
| 持续左键挖掘 | 原版连续swing→单臂blade_1，没有挖掘/工具分支或命中块朝向 | 借用 | mine_pick/mine_shovel或tool_swing程序参数 | SpiritAnimation.java:371、:383 |
| 放置方块/成功右键交互 | 原版success swing→单臂blade_1，看起来像挥砍 | 借用 | interact/place | SpiritAnimation.java:383 |
| 扔物品Q | MC处理成功drop后确实调用mainhand swing；通用blade_1（若剩下的仍是灵矛会落blade_3判断） | 借用 | toss_item，单独事件来源避免事后用手中物品猜 | SpiritAnimation.java:374、:383；MC Minecraft.java:2001 |
| 雪球投掷 | 即时use成功触发普通swing，借blade_1，不走throwReady | 借用 | throw_small；投掷事件 | SpiritAnimation.java:345、:383；MC SnowballItem.java:45 |
| 末影珍珠投掷 | 同雪球；传送发生时观察器清部分状态，无传送落地专属动作 | 借用 | throw_small / teleport_arrive（可选） | SpiritAnimation.java:116、:383；MC EnderpearlItem.java:43 |
| 细雪中 | 无isInPowderSnow分支；按实际地面/速度选walk/air等 | 缺失 | wade_snow或挣扎程序；不必为每方块新增clip | SpiritAnimation.java:200、:428 |
| 冰冻 | 不读取ticksFrozen/isFullyFrozen，无发抖；伤害时仍统一hurt | 缺失 | shiver additive，避免盖过战斗 | SpiritAnimation.java:176、:539 |
| 着火 | 无onFire状态分支；火焰外观不是骨骼动作，伤害边沿仍hurt | 缺失 | burn_react/受伤类型层，按需求避免持续抢动作 | SpiritAnimation.java:176、:200 |
| 被击退/击飞 | 伤害边沿hurt，结束后按实际速度walk或air；没有knockback方向、倒地或翻滚恢复 | 缺失 | hit_air/knockback/knockdown/get_up | SpiritAnimation.java:176、:191、:220、:547 |
| 摔落伤害 | 通用hurt优先，落地shot可能被压到过期；无摔伤专属反应 | 借用 | land_hurt/heavy_recover；协调同帧hurt/land | SpiritAnimation.java:176、:177、:207、:218 |
| 溺水 | 未学Water Dash时每次伤害仍hurt，但swim层仍覆盖多数骨骼；学会后WaterMovement持续回气，正常不会溺水 | 借用/部分不可见 | hurt_water/drown_react；先修水中hurt遮罩 | SpiritAnimation.java:207、:424；WaterMovement.java:20、:34 |
| 死亡 | death非循环.9秒，secondary停；没有按死因/姿态分类 | 可用 | 保留基础death，可加air/water变体 | SpiritAnimation.java:202、:539 |
| 重生 | FORM不copyOnDeath，正常回人形；SoulLink只移动复活点；再次变精灵才transform_in，无Ori revive片段 | 缺失（若期望Ori复活演出） | revive/respawn；先明确形态语义 | SpiritData.java:14、:19；SoulLink.java:69；SpiritAppearance.java:24 |
| 不死图腾救命 | 不进入death，无专属Ori动画；原版图腾效果/统一hurt | 缺失 | revive_totem additive/短片段 | SpiritAnimation.java:176、:202 |
| 攀爬时受伤 | 原版climb/wall的base被hurt→recover暂时替代，之后回climb/wall；没有手不脱墙的受伤层 | 借用 | hurt_climb masked +保持抓墙接触 | SpiritAnimation.java:207、:211、:217 |
| 空中攻击 | 普通apex时允许上半身blade三连/通用单臂，腿仍空中混合；double_jump/wall_jump/bash_launch期间反而不允许overlay | 借用 | blade_air_1/2/3；明确空中特殊动作取消/叠加策略 | SpiritAnimation.java:220、:271、:303、:458 |
| 创造飞行 | 没有abilities.flying分支，通常走apex/rise/fall；mayfly修复只处理输入而非飞行动作 | 借用 | hover/fly_move可选 | SpiritAnimation.java:220；SpiritMovement.java:53 |

MC定义/输入行为从锁定依赖的本地源码JAR只读核对：`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_afbadac5ccebca85f268cc8c658934b1e496edcb_output.jar`。其中`Entity.java:2386/2390`定义swim/crawl；`LivingEntity.java:3350`还把非实际飞行的FALL_FLYING pose视作visuallySwimming；`Player.java:419`在站/蹲放不下时选SWIMMING；`Minecraft.java:1632/1712/2001`分别是连续挖掘、使用成功挥手、Q丢物挥手。未凭名称猜测这些输入是否产生动画。

## Mod能力与战斗逐项盘点

| 能力/动作 | 当前表现 | 等级 | 建议clip/程序层 | 接入点 |
|---|---|---|---|---|
| dash地面/空中 | dash_start停末帧→dash_end，整体前倾/缩放；二者共用 | 可用 | dash_ground/air可按设计合并；结束按事件 | SpiritAnimation.java:172、:215 |
| bash瞄准/弹出 | bash_aim→bash_launch；目标箭头/特效不改变骨骼 | 可用 | 目标方向/发射方向程序层，非仅速度水平yaw | SpiritAnimation.java:167、:212 |
| glide | glide_open/loop/close +共享程序glide，主要骨骼会覆盖开合clip | 借用（混合来源） | feather_glide_start/loop/end，保留抓羽毛双手关系 | SpiritAnimation.java:173、:219、:467 |
| grapple | grapple循环，grapple_end没有专属事件clip，退出靠空中/落地 | 可用（拉行）/缺失（结束） | grapple_reach/pull/release | SpiritAnimation.java:164、:213；MoveMode.java:9 |
| stomp | stomp下行loop；stomp_land无专属clip，取普通land_light/heavy/landRun | 借用（落地） | stomp_start/loop/land，落地冲击事件 | SpiritAnimation.java:177、:214 |
| wall_slide | wall_slide/hold按abs(vy)，不按真实墙法线设置抓取点 | 可用但缺接触 | wall_attach/slide；墙面IK或至少法线朝向 | SpiritAnimation.java:217、:564 |
| wall_climb（R55） | 上行仍wall_slide，vy≈0才wall_hold | 缺失 | wall_climb_up/down/idle | SpiritMovement.java:157；SpiritAnimation.java:217 |
| Sticky壁挂 | 同wall_hold；没有额外片段需求，推离墙是运动规则 | 可用 | 共用wall_hold，和接触程序整合 | SpiritAnimation.java:217 |
| wall_jump | wall_jump_l/r .4秒，方向取left输入；无抓墙脚点蹬离约束 | 可用 | 按墙法线选方向/倾斜，片段可保留 | SpiritAnimation.java:166 |
| double_jump | .5秒翻转 +squash/耳尾；期间禁武器overlay | 可用 | 与空中攻击取消策略一致 | SpiritAnimation.java:165、:271 |
| mantle | 本地climb→land_light +程序；远端看不到mantling标志，只能从位置/旧MoveMode推普通动作 | 借用 | mantle_rise/over，增加可同步视觉状态 | SpiritAnimation.java:143、:216；SpiritMantle.java:26 |
| Launch（存在）地面蓄力/发射 | 蹲下蓄力只crouch；发布LAUNCH后没有对应shot，按vy走apex/rise；空中Launch同理 | 缺失 | launch_charge/launch_release/launch_air | SpiritMovement.java:137、:236；MoveMode.java:12；SpiritAnimation.java:164、:220 |
| Burrow / spirit_sand | 客户端只改速度及进出boost；不发视觉mode；通常普通air或地面姿态，没沿钻行方向收体 | 缺失 | burrow_enter/loop/exit；本地状态与远端同步 | SpiritControls.java:285；SpiritSandBlock.java:29、:41 |
| 水下dash | 没有MoveMode.DASH，发DASH特效；由实测3D速度令dart收体/减慢划水，速度退去后展开 | 借用（已有程序） | water_dash_start/loop/end或事件驱动dart | SpiritControls.java:296；WaterMovement.java:39；SpiritAnimation.java:407 |
| blade地面三连 | 专属blade_1/2/3，右/左手；upperBody，腿保留移动；扭腰/前倾/光弧/前冲另层 | 可用 | 调整片段前先确认上身遮罩与程序twist；保持12/13/18tick战斗总时长 | SpiritAnimation.java:303；SpiritCombat.java:28、:61 |
| blade空中三连 | 仍地面同三个上身clip；服务端有限滞空不对应专属骨骼状态 | 借用 | blade_air_*，根据战斗意图选clip，不改伤害窗口 | SpiritAnimation.java:303；SpiritCombat.java:71 |
| spear投掷 | 即时use→swing；当前手持灵矛时借blade_3上半身；“灵矛蓄力use_item”分支正常不可达 | 借用 | spear_throw +成功投掷事件/手别 | SpiritSpearItem.java:19；SpiritSpear.java:76；SpiritAnimation.java:374 |
| hammer蓄力/释放 | blade_3前22%蓄12tick、余段.32秒砸出；普通左键打击仍blade_1单臂 | 借用 | hammer_charge/hold/slam/recover，取消与成功区分 | SpiritAnimation.java:329、:354、:383；SpiritHammerItem.java:51 |
| hammer空中释放 | 同锤overlay，物理向下；不进入stomp视觉家族 | 借用 | hammer_air_slam/land，基于实际释放事件 | SpiritHammerItem.java:89；SpiritAnimation.java:354 |
| bow蓄力/释放 | 现有bow_charge/hold/release及左右手、IK；灵弓/普通弓同家族 | 可用 | 保留接触/瞄准，失败/取消不假放箭 | SpiritAnimation.java:325、:368；SpiritBowItem.java:37 |
| spirit_core持有/使用 | 持有普通cradle；即时切形态/觉醒，transform_in/out只在对应base条件可见；球体发光不是手部clip | 借用（持有）/可用（变身） | core_present/awaken可选，避免与GUI/切形态抢镜 | SpiritCoreItem.java:17；SpiritAnimation.java:203、:226、:495 |
| 自动灵焰 | 同伴发射，玩家无额外clip | 可用（主体是同伴） | 不必强加玩家施法手势 | SpiritAnimation.java:200（无对应条件）；client/fx/CompanionMotion.java |
| Charge Flame蓄力/爆发 | X键独立计数/保活与粒子，不驱动COMBAT_VIEW或UseAnim；躯体继续普通状态 | 缺失 | charge_flame_charge/hold/release，显式视觉阶段 | SpiritControls.java:228；SpiritAnimation.java:254 |
| Light Burst | Z键发请求/抛光弹；无全身施法clip | 缺失 | light_burst_throw | SpiritControls.java:374；SpiritAnimation.java:254 |
| Flash | 持续光池/同伴升起/屏幕提亮；躯体无独立flash姿态 | 缺失（若需施法动作） | flash_start/hold/end；走动时用additive | SpiritControls.java:253；SpiritAnimation.java:254 |
| Soul Link | 能力/特效，不切专属base/overlay | 缺失 | soul_link_cast可选 | SoulLink.java:58；SpiritAnimation.java:200 |
| 受伤 | 统一hurt/recover，没有source方向/类型；特殊家族遮挡见上 | 借用 | 定向hurt +按场景mask | SpiritAnimation.java:176、:207 |
| 硬直 | 没有同步hitstun视觉状态；hurt计时不是硬直系统 | 缺失 | stagger，只在真实逻辑事件存在时接 | SpiritAnimation.java:207；CombatState.java:6 |
| 击飞/倒地 | 位移→hurt后普通air；无knockdown/get_up | 缺失 | knockback_air/knockdown/get_up；需要事件来源 | SpiritAnimation.java:220、:547 |
| 死亡/复活 | death有；普通重生回人形，没有Ori revive；R108“复苏”是世界仪式不是角色复活动画 | 可用（死亡）/缺失（复活） | revive独立设计；仪式pose若增加接RestorationClient会话 | SpiritAnimation.java:202；SpiritData.java:19；RestorationClient.java:20 |

## 58个已打包clip：使用状态

下表按名字分组，但每个实际名称均列出。标“可用”仍须服从上文优先级/遮罩；`run/rise/fall`是混合层，不是同名next。时长来自GLB animation sampler输入accessor的max，不是仅抄脚本声明。

| 实际clip | 时长（秒） | 资产loop | 运行时用途 / 等级 | 接入点 |
|---|---:|---|---|---|
| `alert` | 2.000 | 是 | 地面静止/久静base；idle还作为swim与缺失片段回退；**可用** | SpiritAnimation.java:200、229、233 |
| `apex` | 0.600 | 是 | 空中混合；apex未扣权；**借用** | SpiritAnimation.java:220、458 |
| `bash_aim` | 1.000 | 是 | 猛击瞄准base/弹出shot；**可用** | SpiritAnimation.java:167、212 |
| `bash_launch` | 0.400 | 否 | 猛击瞄准base/弹出shot；**可用** | SpiritAnimation.java:167、212 |
| `bend` | 2.000 | 是 | 无正常世界玩家状态接入；建议明确预览用途或移出运行契约；**缺失（未接入）** | SpiritAnimation.java:200、302、694 |
| `blade_1` | 0.600 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `blade_1_left` | 0.600 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `blade_2` | 0.667 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `blade_2_left` | 0.667 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `blade_3` | 0.900 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `blade_3_left` | 0.900 | 否 | 三连上身；1另供普通swing，3另供锤/矛；**可用/借用** | SpiritAnimation.java:303、329、354、374、383 |
| `block` | 1.000 | 是 | 盾牌单臂格挡；**可用** | SpiritAnimation.java:351 |
| `block_left` | 1.000 | 是 | 盾牌单臂格挡；**可用** | SpiritAnimation.java:351 |
| `bow_charge` | 1.000 | 否 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `bow_charge_left` | 1.000 | 否 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `bow_hold` | 1.000 | 是 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `bow_hold_left` | 1.000 | 是 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `bow_release` | 0.300 | 否 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `bow_release_left` | 0.300 | 否 | 弓蓄力/维持/释放上身；**可用** | SpiritAnimation.java:325、368 |
| `climb` | 1.000 | 是 | 原版梯藤攀爬；mantle上升借用；**可用/借用** | SpiritAnimation.java:211、216、238 |
| `crouch` | 2.000 | 是 | 潜行静止/行走共用；**借用** | SpiritAnimation.java:227 |
| `dash_end` | 0.300 | 否 | 冲刺主体/结束shot；**可用** | SpiritAnimation.java:172、215 |
| `dash_start` | 0.300 | 否 | 冲刺主体/结束shot；**可用** | SpiritAnimation.java:172、215 |
| `death` | 0.900 | 否 | 死亡/睡眠/所有载具统一坐姿；**可用/借用** | SpiritAnimation.java:202、204–205 |
| `double_jump` | 0.500 | 否 | 起跳、二段跳、左右蹬墙shot；**可用** | SpiritAnimation.java:164–168 |
| `fall` | 0.800 | 是 | 空中混合；apex未扣权；**借用** | SpiritAnimation.java:220、458 |
| `glide` | 1.200 | 是 | 滑翔主体/开合；elytra借用且程序姿势可盖过；**可用/借用** | SpiritAnimation.java:173、219、233、467 |
| `glide_close` | 0.300 | 否 | 滑翔主体/开合；elytra借用且程序姿势可盖过；**可用/借用** | SpiritAnimation.java:173、219、233、467 |
| `glide_open` | 0.300 | 否 | 滑翔主体/开合；elytra借用且程序姿势可盖过；**可用/借用** | SpiritAnimation.java:173、219、233、467 |
| `grapple` | 0.800 | 是 | 钩索拉行/下砸循环；**可用** | SpiritAnimation.java:213–214 |
| `hold` | 1.000 | 是 | 世界装弩借用；背包持物仍用；世界静止持物改程序层；**可用/借用** | SpiritAnimation.java:322；SpiritInventory.java:64 |
| `hold_left` | 1.000 | 是 | 世界装弩借用；背包持物仍用；世界静止持物改程序层；**可用/借用** | SpiritAnimation.java:322；SpiritInventory.java:64 |
| `hurt` | 0.300 | 否 | 通用受伤/恢复；未按方向分类且recover窗口截短；**借用** | SpiritAnimation.java:176、207–208 |
| `idle` | 3.000 | 是 | 地面静止/久静base；idle还作为swim与缺失片段回退；**可用** | SpiritAnimation.java:200、229、233 |
| `jump_start` | 0.300 | 否 | 起跳、二段跳、左右蹬墙shot；**可用** | SpiritAnimation.java:164–168 |
| `land_heavy` | 0.500 | 否 | 落地shot；light另用于跑动落地及mantle过沿；**可用/借用** | SpiritAnimation.java:177、216、445 |
| `land_light` | 0.300 | 否 | 落地shot；light另用于跑动落地及mantle过沿；**可用/借用** | SpiritAnimation.java:177、216、445 |
| `recover` | 0.400 | 否 | 通用受伤/恢复；未按方向分类且recover窗口截短；**借用** | SpiritAnimation.java:176、207–208 |
| `rise` | 0.800 | 是 | 空中混合；apex未扣权；**借用** | SpiritAnimation.java:220、458 |
| `run` | 0.800 | 是 | walk基础 + run覆盖混合；旧权重与上身遮罩见前；**借用** | SpiritAnimation.java:428–453 |
| `sit` | 2.000 | 是 | 死亡/睡眠/所有载具统一坐姿；**可用/借用** | SpiritAnimation.java:202、204–205 |
| `sleep` | 3.000 | 是 | 死亡/睡眠/所有载具统一坐姿；**可用/借用** | SpiritAnimation.java:202、204–205 |
| `start` | 0.300 | 否 | 移动起止/原地快速转身shot；**可用** | SpiritAnimation.java:195–197 |
| `stomp` | 0.600 | 是 | 钩索拉行/下砸循环；**可用** | SpiritAnimation.java:213–214 |
| `stop` | 0.400 | 否 | 移动起止/原地快速转身shot；**可用** | SpiritAnimation.java:195–197 |
| `swim` | 1.000 | 是 | 有资产，正常swim选择却改取idle+程序；无正常世界直接播放；**缺失（未接入）** | SpiritAnimation.java:233、467 |
| `tail_sway` | 2.000 | 是 | 无正常世界玩家状态接入；建议明确预览用途或移出运行契约；**缺失（未接入）** | SpiritAnimation.java:200、302、694 |
| `transform_in` | 0.600 | 否 | 精灵形态进出；**可用** | SpiritAnimation.java:203、226 |
| `transform_out` | 0.500 | 否 | 精灵形态进出；**可用** | SpiritAnimation.java:203、226 |
| `turn_left` | 0.500 | 否 | 移动起止/原地快速转身shot；**可用** | SpiritAnimation.java:195–197 |
| `turn_right` | 0.500 | 否 | 移动起止/原地快速转身shot；**可用** | SpiritAnimation.java:195–197 |
| `use_item` | 0.800 | 是 | 通用持续使用单臂；灵矛using分支正常不可达；**借用** | SpiritAnimation.java:341、351 |
| `use_item_left` | 0.800 | 是 | 通用持续使用单臂；灵矛using分支正常不可达；**借用** | SpiritAnimation.java:341、351 |
| `walk` | 1.000 | 是 | walk基础 + run覆盖混合；旧权重与上身遮罩见前；**借用** | SpiritAnimation.java:428–453 |
| `wall_hold` | 1.000 | 是 | 壁挂/滑落；上爬也错误选slide；**可用/借用** | SpiritAnimation.java:217 |
| `wall_jump_l` | 0.400 | 否 | 起跳、二段跳、左右蹬墙shot；**可用** | SpiritAnimation.java:164–168 |
| `wall_jump_r` | 0.400 | 否 | 起跳、二段跳、左右蹬墙shot；**可用** | SpiritAnimation.java:164–168 |
| `wall_slide` | 1.000 | 是 | 壁挂/滑落；上爬也错误选slide；**可用/借用** | SpiritAnimation.java:217 |

## 新clip登记与缺失回退

1. **资产源**：`scripts/art/character_animations.py:13/151`的CLIPS、author分支、帧长/FPS/loop；非对称交互加入`:438`镜像列表并核对“无后缀=右手、_left=左手”。Blender仅更新动作不会替运行时自动选择。新方向/环境变体的名字由设计确认后使用。
2. **生成/导出/promote**：`scripts/art/build_character.py:104/109`生成动画metadata、版本目录manifest、GLB与哈希；`scripts/art/character_export.py:16`导出ACTIONS；`scripts/art/promote_character.py:10/27`把当前导出与manifest登记到运行资源/`art/source/character/manifest.json`。新源文件须在交付列出。本文没有执行这些写操作。
3. **运行时白名单**：必须更新`src/main/resources/assets/orimod/models/entity/animation_contract.json`的required。`AnimationContract.java:15`读入后检查idle存在、无空名/重复；契约文件损坏是抛异常，不是安静回退。`SpiritClips.java:43`只遍历这份清单，**即使GLB存在额外clip，不在清单也取不到**。
4. **名称/时长验证与回退**：`SpiritClips.java:35`要求`model.animation(name)!=null`且duration有限、>0。必需clip缺失/无效→idle；idle也无效→1秒无动作程序`ori_missing_rest`（rest pose）。没有“左手片段缺失就镜像右手”或“blade_2缺失就blade_1”规则。每个loaded model的WeakHashMap缓存只验证一次，资源重载得到新model再验证/警告；`missingClips`可在诊断看到。
5. **调度与循环**：在`SpiritAnimation.java:200/302`或明确程序层接状态；同时检查`:694 loops()`。运行时循环由这份switch控制，**不自动读取manifest的loop字段**。`throw_ready`、`hold_both`、`elytra`等诊断标签不一定是资产名，不能照着日志盲加GLB。
6. **动画时序**：clip时长、客户端scrub、服务端动作窗口要分别核对。blade按12/13/18tick逻辑周期和windup2/3/4tick驱动`swingCurve`；manifest中的contact/events并未被`SpiritAnimation`用作伤害触发。锤依赖blade_3的22%姿势，bow补偿固有yaw±.12rad，runPose依赖左右膝相位.75/.25——更换片段后这些旧假定需要同步检查，而不是只改时长。
7. **纯程序层**：`SpiritPoseLayer/GltfAnimation.procedural`直接构建的姿势不必登记GLB/manifest（例如throwReady）；但骨名/遮罩/坐标约定需测试。当前程序层只驱动列出的骨骼，新增骨不能仅靠命名自动参与。

现有检查入口（本轮只读、未执行）：

| 检查 | 覆盖范围与限制 | 文件:行 |
|---|---|---|
| AnimationContractTest | 打包GLB必需clip完整、各clip可播放；逐个删名字均能检出并回idle/rest；不检查每个玩家状态是否实际选中 | src/test/java/cn/wwl/orimod/asset/AnimationContractTest.java:17/36/50 |
| check-assets.py | manifest与导出名字严格相等、运行契约子集、哈希/骨骼/采样有限性/循环首尾闭合；不会验证run的运行时mask | scripts/check-assets.py:49/75/78 |
| FeelTuningTest | 步态、air、swing曲线等纯函数；不是真实SpiritAnimation端到端选择 | src/test/java/cn/wwl/orimod/movement/FeelTuningTest.java |
| SpiritPoseLayerTest / SpiritCarryPoseTest | 程序骨骼层与真实GLB握持几何；片段重做后可暴露坐标/姿势变化 | src/test/java/cn/wwl/orimod/client/render/SpiritPoseLayerTest.java、SpiritCarryPoseTest.java |
| verify-character-*.py | 既有特殊物品、弩、鱼竿、瞄准、步态、第一人称/背包实机脚本；不能替代crawl/wall_climb/水中hurt等本轮新增场景 | scripts/verify-character-special-items.py 等 |

建议R112先提取纯状态选择/层权重函数并补选择回归：陆地crawl与swim分流；R55向上爬墙；原版爬梯静止/下行；hurt与swim/ride/climb组合；空中攻击与double_jump的策略；重叠层归一后run/rise/fall的实际份额；灵矛即时use；放置/Q/挖掘与战斗分流；缺少/未登记新clip。随后才按官方参考调整片段与程序幅度，避免“源片段很好、运行时没播出来”。

## 给美术实拍的诊断提示

`SpiritPlayerVisual.diagnostics()`（`:69`）暴露`base/overlay`逻辑名、`baseClip/overlayClip`实际解析名、`clipTime`、gait权重、`secondary.pose.family/swim/glide/dart/cruise/bodyPitch/run/throw`、`landRun/mantle/twist/spineArch`、`missingClips`。它们仍不是最终逐骨骼贡献：例如base=swim/baseClip=idle，程序覆盖后画面是划水；overlay=hold而overlayClip可空，因为走程序持握。第一人称本机body不可见时该诊断会返回visible=false，不能据此判断手部模型缺失。

本轮只新增本文；未重跑1214/736，也没有把上一轮全绿当作新增状态已经验证。运行时语义由本次源码、锁定MC源码和GemRender字节码核对；方向/接触/叠加幅度的具体视觉结论仍需后续固定机位实拍。
