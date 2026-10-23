# 回归测试

## R115 A 核心建筑化

全量 `build runGameTestServer compileDevtoolsJava`：**1414 JUnit / 826 GameTest，0 失败/错误/跳过**（基线 1410/814）。新增 4 个门洞几何单测、12 个 GameTest：八种旧核心无 BE 的升级与幂等、原碰撞/无邻格写入、存档与客户端 tag、两种首领实际召唤/死亡外观、插钥匙共享进度与立即变空气。原遗迹路线、桥、秘室测试原样通过；本轮没有改 NBT 或 layout。`evidence/r115-a/full-2.log`、`gametest.xml`、`counts.json` 保存证据；full-1 为新增夹具调用了不存在的 Shapes.equal 的编译错误，已换为原版形状差集比较，没有修改任何旧断言。首次 compile.log 另记录方法引用类型推断/缺失 import 两处已修正编译错误。

`check-ruins.py` 默认只读模式通过，17 文件、written=0；`test_check_ruins.py` 12 tests OK。`check-sanctuaries.py` 核对 12 套原创 .blend/GLB/PBR 包装、SHA、单 UV 与资源映射。初次美术实拍发现 Blender 默认 UV 层优先于 palette，水面误采多色：生成前清除原 UV，仅导出 palette；现加入 TEXCOORD_1 拒绝检查，v003 图仅作为故障证据，不能用于外观验收。

旧档验证使用 R114 `orimod_dev_r114natural` 的独立副本（`legacy-copy.json`），原存档不打开。离线读取原区块 NBT：forest (102,74,-166)、creek (205,80,-268)、cave (-529,52,606)、outpost (-285,113,31) 均有原方块、没有对应 BE。实机中三种神龛仍打开 RuinScreen，灵泉仍打开 WellScreen；`inspect_block.sanctuary/clientSanctuary` 记录两端状态，离开 500 格再返回检验 chunk resync。独立展示场以实际 useItemOn 交互驱动地图石、两种祭坛三态、两次插钥石，不用直接修改 BE tag。

客户端使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0`；日志确认 llvmpipe / GL 4.6，Sodium 开启。前两次采样分别因脚本世界名未带 orimod_dev 前缀、初次远处区块生成超出 DevClient 等待而中断，均保留日志；确认状态后才重启采样，未把命令 timeout 当作业务失败。

最终模型实拍：`evidence/r115-a-gallery-v005/`（七种独立核心日夜、地图石激活、祭坛三态、两槽钥石门与开启帧序列）；旧档实拍：`evidence/r115-a-legacy-v003/`（原安全落脚点视角、三座自然遗迹与旧驿站、交互与重同步数据）。`legacy-v001` 的森林镜头被前景石墙遮挡，仅保留中间诊断；legacy-v002 采样进程收到 SIGTERM（143）未完成，验收使用已完整结束的 v003。美术细节最终补齐羽环羽轴/叶片和圆门贴内圈门扇，没有改动玩法。 旧森林神龛位于窄台边缘，新视觉底座有悬出部分；不自动铺地、不增加碰撞，外观是否接受待审查。真实多人和第三方光影包未实测。已清 forceload、leave_world、quit，客户端正常退出。

### A 外观修订（v006）

首轮功能验收通过后，仅改建筑美术、客户端适配与拍摄脚本。`art/source/sanctuaries/v002/` 用 512×64 的单 UV 材质图集：石材面约 16 texel/格、nearest，常用 2×2 圆门预补偿单位模型缩放；林泉加苔点，石材有颗粒/刻槽，12 套发光配色与 v001 逐项相等（`review-emission-check.json`）。根拱基部 0.18→0.27，顶点 +0.5 格，模型最高 2.79；原碰撞不变。首次生成的门 UV 边界断言暴露单位门顶面偏移超出 tile，调整映射偏移后校验通过（`art-v002-build.log` / `art-v002-final.log`）。

真实遗迹补拍暴露了两处展示场看不到的视觉适配：旧门没有朝向状态，从洞穴走廊看到背面，原先无槽；现两面同样显示两槽与共享进度。原腐光祭坛核心在地板层，底座被地板遮挡；现同层至少三个坚固邻格时仅视觉上移一格，AABB 同步扩展。没有地形/碰撞/交互或服务端逻辑变更。`build compileDevtoolsJava` 及 1414 JUnit 重新通过（`review-final-build.log`）；826 GameTest 沿用已验收的上一轮全绿，本次纯视觉修订不重复运行。

最终目录：`evidence/r115-a-gallery-v006/` 和 `evidence/r115-a-insitu-v006/`。实景使用原 R114 自然世界的另一份未开门副本 `orimod_dev_r115site`（`insitu-copy.json`），不会重放模板。`*-prefit-v006` 保留适配前/机位被挡图；首次重拍因 DevClient 同名原始截图拒绝覆盖而停止，归档原始图片后重拍，未把它计作产品测试失败。

## R114 P1/P2（最终全量与客户端审查通过）

最终 `build runGameTestServer compileDevtoolsJava`：**1410 JUnit / 814 GameTest，0失败/错误/跳过**；证据 `evidence/r114-p1/full-tests-10.log/xml`、`full-10-counts.json`、`final-10-junit/`。新增坡地防外溢用例已过，旧陡坡测试按总落差规则改为7格。JUnit比前轮少1是Claude删除弓弦第二只手的专属测试；其运行时代码与手部出口重载也已删除。下文保留中间失败及调查，不以最终全绿冒充原超时根因已证实。

猛击使用 `distance × (1.25 − 0.45·cosθ)`：仍在6格视线范围内，背后目标可选，近似距离优先前方；刚弹出的目标在15 tick或实际落地前暂不参与选取。瞄准窗36 tick。原 `RuinBridgesGameTests` 未改动，`full-tests-3.log` 中原桥连跳/旋转用例已通过。旧灵灯测试要求立即重选同一目标，与新排除规则冲突；现在保留点亮/有效性断言，并检查立即不可重选、真实15 tick后恢复及提前落地恢复。

腐光水改用同ID的LiquidBlock与 source/flowing FluidType，坡距2、每格降2级、10 tick流动、不生成无限源，无可挖轮廓/掉落/桶；旧无LEVEL属性方块按默认0读作源。接触魔法伤害和减速保留；水冲、水中姿态、滑翔和锤落水取消使用NeoForge自定义FluidType查询。三种遗迹及平地腐化地带各运行200 tick，新增腐光水坐标集合为空；本轮未改池塘模板。后续新增5格落差的台阶腐化地带：固定种子114的9个候选池中2格面向更低邻列，生成后保留为土；7格仍有水，200tick后原水源均保留且新增外溢0。旧preflight_steep夹具6格落差在新relief≤6规则下合法，full-tests-9仅该项失败；按Claude确认改成7格，保留失败零写入断言。接触夹具原先错误地断言原版isInWater（NeoForge自定义FluidType单独追踪），之后仅调用player.tick也未触发移动碰撞；改为baseTick刷新浸没状态并实际move，接触伤害回归通过。

相机修前数据在 `evidence/r114-camera-hands-before-v001/results.json` 和 `r114-p1/camera-before-summary.json`：玩家X恒为6022.91，停步后相机X从6019.4063回到6018.9100，后移约0.496格；是旧lag(11/s)与lead(3.5/s)两套过滤器消退速度不同，残留正向前导回缩。新CameraFollow使用单一临界阻尼跟随（ω=22，滞后上限0.55格），取消前导；反向/骤停不越过目标，暂停不积分。JUnit覆盖4/20/60/144FPS、4/6.4/10m/s停止、反向、大速度与暂停；客户端修后 `r114-camera-after-v001`：玩家X恒6020.99，相机X从6016.7574单调收敛至6016.9900，最大反向步长0，未越过目标；Claude已确认#2通过。

`fluid-tests-1/2`保留之前失败；`full-tests-3.log/xml`为812 GameTest中811通过，仅旧灵灯即时重选断言失败；JUnit1381中1失败为我写的加权样例预期错误（身后1格得分1.7，前方2格1.6），改为身后1/前方3，随后复验通过。`full-tests-4.log`是Claude世界生成文件括号错误导致的编译失败，不是测试失败。`full-tests-5.log/xml`通过1399 JUnit / 813 GameTest，纳入后续Blight诊断/裸岩地基后`full-tests-6.log/xml`通过1410 JUnit / 813 GameTest；两处测试修正已通过，正式P1逻辑未为旧断言让步。最新中位数与加密预筛、P2弓出口修正合并后，full-tests-8.log/xml为 **1411 JUnit / 813 GameTest，0失败/错误/跳过**。之前full-tests-7为1411/813中仅潮夜夹具等待失败，原文“Tide fixture ring did not load block/entity data”，未进入spawnWave。其旧等待预算80tick、整测试100tick；没有记录原失败区块明细或旧成功时长。隔离探针等待0tick（FULL获取0.66ms，总7.1ms），200波73甲虫/61鸦/296喷吐者；全量重跑也等待0tick（0.13ms/2.72ms）。准备阶段改为有界400tick、整测试420tick，原FULL/实体数据条件、票据清理、全部波次断言和生产逻辑均不变；新增旧截止点/就绪日志。延长窗口未在隔离/首轮成功运行中用到，因此根因仍未证实，也不归因于新地形预筛（GameTest为FLAT，计时从同步FULL获取后才开始）。详见r114-p1/tide-readiness-investigation.json及alone-2日志/XML；alone首轮因临时模板namespace错误未注册测试，不计为通过，临时探针已移除。 后续full-tests-9潮夜实际准备33tick（FULL0.14ms、总1912.55ms）后通过，仍低于旧80tick上限。 修前第一人称六种持物截图、视频和contact sheet在 `r114-camera-hands-before-v001`；短视频sheet末尾补重复帧，仅供观察已录动作，不作为额外采样。P2运行时代码由Claude负责。

世界生成复查v002使用新存档 `orimod_dev_r114blight2`，seed114114（非v001存档）：原点与西南定位仍为空，东方核心(1256,72,-348)存在；DEBUG未启用，不能将无declined日志当作无拒绝。新存档orimod_dev_r114blight3（v003）开启world包DEBUG后，两空起点均报最终候选TERRAIN；东方核心仍存在。新存档blight4（v004）含逐候选原因及裸岩/砂砾地基修正，仍仅东方有核心；原点中心Y122、失败列Y118，西南各候选也有4格偏差，全部TERRAIN。完整日志见r114-blight-relocate-v004/declined.log。Claude随后改为各列相对地带中位数，先6格粗筛再3格细筛。全新orimod_dev_r114blight5（v005，seed114114）从原点/东方(2048,0)/西南(-2048,2048)重复定位：估计位置(-194,32)/(1249,-350)/(-2240,1072)，只有东方核心(1256,72,-348)存在；原点诊断仍有118对median122，西南新起点(-2227,64,1075)诊断为water地基。完整逐候选日志、三组全景/地面截图在r114-blight-relocate-v005。额外起点(-2217,66,952)declined日志中有两个OK；静态核对发现place内部nextLong与随后declineReason的nextLong是不同形状种子，所以这些诊断尚不能精确代表实际失败计划。已交Claude修复，未宣称世界生成验收通过。 后续修正每候选共用随机种子、移除单列偏差限制（仍总落差≤6）、过滤会漏水的池格并增加海平面+2的预筛；全新orimod_dev_r114blight6/seed114114从同三起点locate，实际核心分别(-190,122,34)、(1256,72,-348)、(-745,68,1887)，三者inspect_block均为gloom_core、生命40。world DEBUG已确认启用，本次三点采样及整个客户端会话declined均0行。六张全景/核心图、定位返回值和日志在r114-blight-relocate-v006；Claude已通过地形与池塘实拍审查。西南灰色裸岩已离线读NBT：主体stone，混gravel/granite/dirt/coal_ore，X[-1944,-1879]、Z[2059,2113]，多为Y71；不在任何附近Ori piece内，暂不归因于模组。见 `r114-blight-relocate-v002/{saved-starts,southwest-surface}.json`。

R114补充实拍：`r114-gloom-fluid-v001/results.json`记录源后第1/2/3格分别LEVEL2/4/6、第4格为空，精灵接触20→18HP；全景补拍`r114-fluid-overview-v002`，首张short-flow机位过低不作全景依据。西南裸岩F3截图确认minecraft:stony_shore。P2最终接线后build/JUnit为1410项全绿；修后七组视频/sheet/motion在`r114-hands-after-v001`，含三次单刀（修后回看motion发现均为stage1，尚非三连）、弓（1发）、空中锤hammer_slam→idle、矛冷却、吃苹果、原版剑，另有左主手/Spirit Glow帧；目前满弓两条前臂交叉遮挡明显，已交Claude调姿，不宣称P2视觉验收通过。视频有丢帧，sheet短尾重复最后录制帧，不以它量测精确动画时间。 后续r114-hands-after-v002补拍五段，真实input_script按键确保服务端blade stage为1/2/3；Claude审查通过三连、锤抬举与矛朝前持握，弓满拉前臂仍穿过视线，交Claude改stringPaw前臂出口后在r114-hands-after-v003补拍idle/满拉/释放单帧及6秒video/sheet/motion；v003向下出屏仍占视线，Claude最终删除弦端第二只手，保留原版精确弓姿势与抬弓/后坐。r114-hands-after-v004的bow-idle/full-draw/release三帧经审查通过；真实满拉35tick、松手射出1发，手部不再交叉。所有实拍llvmpipe + DISPLAY=:0，客户端正常退出、清票、恢复临时world DEBUG配置。


## R114 P0：创飞保护与灵锤接地

修前证据 `evidence/r114-p0/before-tests.log`、before-gametest.xml：新增14项中13项失败（十项保留创造/旁观目标、关闭能力无敌后的鸦冲击、创造玩家受风推、灵锤空中释放立即伤害）。普通创造的原版无敌本来能挡 hurt；`/ori god` 可将能力无敌关掉，不应混同原版规则。五类怪物的目标入口、攻击开始/延续、投射物和范围攻击统一排除创造/旁观，并仍使用普通 hurt 伤害源。生存目标伤害有正向回归。

灵锤新增短暂 hammer_slam 模式：松手后下落，实际地面碰撞时结算一次；水/熔岩、虚空、200tick超时、死亡、换形态/维度、传送/下线清理。沿用 COMBAT_VIEW，不新增协议。客户端下落速度在移动预测末端处理；下落期间不被被动墙附/滑翔接管。33项 gameplay_safety 覆盖上述路径及四武器结束/换槽后跳跃和空中冲刺。

最终P0 `build runGameTestServer compileDevtoolsJava`：**1371 JUnit / 797 GameTest，0失败**，after-tests-final.log、after-gametest-final.xml、final-counts.json；包含Claude的世界生成修正与v014源重烘焙的第一人称v011爪模型。修前两项旧biome_modifier断言由Claude迁至structure/structure_set，未放宽游戏逻辑断言。

#6b/#7记为“下砸修复后未复现”，不声称已证明是同一根因。真实输入数据在 r114-weapons-before-v001、r114-weapons-air-before-v001（60/4FPS）、r114-weapons-edge-v001（8组创造/双击飞行、6组下落/换槽）；后两项移动均可再次触发。高空锤实拍 r114-hammer-contact-v001 从y230开始，下降中维持hammer_slam，最终hammer_land并回idle。集成服务端实体先发生接地碰撞时，随后到达的客户端位置包可在单次诊断采样中仍显示稍高的Y，不能把包位置与早先碰撞tick视为同一瞬间。

世界生成首轮（seed114114）六类locate与12张图在 r114-worldgen-v001，补近景在 r114-worldgen-detail-v002。腐化地带旧起点(-190,122,34)核心缺失，已报告Claude，等待扩大预检范围后的新世界复测。洞穴旁凹坑低点(-520,63,622)在piece外8格；Claude确认cave与approach均NONE，暂按原版噪声洞口处理。P1/P2另续，不计入此检查点。


## R113 第二批：恢复循环与技能轮盘

`RecoveryTuning/RecoveryRules`集中数值与纯函数：精灵脱伤8秒后每秒0.5HP至80%，消耗后4秒回能2/秒至20（RECOVERY为3/秒）；Regenerate按住每20tick扣10能量恢复6HP，受伤含吸收伤害中断并等松键重试。80%不拦其他治疗；人形FoodData保持原版。FoodData.tick、玩家exhaustion/eat与和平aiStep分别门控，避免和平回血绕过新上限。

恢复球4格吸附，出生10tick后可领，满对应资源不拉取/不消费，1200个已加载tick后消失，保存年龄。服务端单消费入口防止两名玩家重复领取，生命球不走灵光汲取。掉落按敌人最大HP分组、每组两类各50%，保留原XP/灵光。

初轮新增GameTest夹具问题留在`evidence/r113-recovery-runtime-v001/full-a-1.log`：新登录玩家60tick保护使伤害/饥饿被拒，嵌入连接不自动驱动正常玩家Post tick。按既有Flash/Damage夹具先tick61次，定时恢复测试显式调用正式Post入口；断言不变。第二轮吸收测试暴露MC 1.21的max_absorption属性限制：直接setAbsorptionAmount(4)实际被钳0，改用真正ABSORPTION效果并先断言吸收值存在（full-a-2.log）。这些是新增夹具修正，未改旧用例断言。

A检查点：`evidence/r113-recovery-runtime-v001/full-a-3.log`，1334 JUnit / 759 GameTest，0失败；吸收效果夹具修正后没有放宽断言。B加普通食物不浪费、金苹果效果保留、naturalRegeneration=false只禁被动生命恢复；三个技能槽的迁移、移动、非法绑定/包版本、250ms手势和死亡复制回归。首轮A+B全量1356/763全过（`evidence/r113-wheel-runtime-v001/full-b-1.log`），随后补“后学技能只填空槽且不重新填用户主动留空槽”回归，随后最终 `build runGameTestServer compileDevtoolsJava` 为 **1357 JUnit / 764 GameTest，0失败、0错误、0跳过**（full-b-3.log，4分15秒）。GUI绕序修正实拍后，再跑full-final.log，仍1357/764全绿；最终源码SHA-256与v003截图记录一致。最终XML/计数在同目录final-counts.json、final-junit/、final-gametest.xml。

本轮另遇既有潮夜 `wave_uses_horizontal_ring_and_persistent_tags: Outside horizontal ring`（full-b-2.log）。夹具Site.mobs原先无归属地取55格内全部潮汐怪，查询范围重叠；改按波次目标玩家UUID与手工夹具UUID筛选，多人用例显式纳入两个玩家，清理也限本夹具怪物。距离断言保留，并新增“查询数=本波生成数”及邻居污染回归；与上轮区块票问题分开记录。原失败未记录具体怪物位置，不能冒称已观测到外来UUID。详见`evidence/r113-wheel-runtime-v001/tide-investigation.json`。

轮盘为不暂停游戏的Screen，打开后原版释放鼠标；G释放由Screen自己的keyReleased接收，不能依赖被setScreen清零的KeyMapping.isDown。250ms使用单调毫秒而非客户端tick；技能绑定包v1校验槽位0..2和目录ID，未学技能在服务端再次拒绝。保留三个旧映射ID以迁移用户改键，界面名称改为技能槽1/2/3。

实拍：`evidence/r113-wheel-capture-v003/`的14个检查点由XTest真实键鼠输入完成，`results.json`保留双端状态：G短按面板、长按轮盘、鼠标视角不变、H绑定、移槽清空、绑定键未松开不误施法；12HP在脱伤间隔内保持，之后至16HP稳定，复原16→20HP且20→10能量；X蓄力松手扣10、Z光爆扣15、H灵闪持续扣能。HUD有正常/缺能/冷却帧。`r113-wheel-extra-v001`补中文轮盘、实际滚轮切换、满血生命球保留与18→20磁吸消费（早于被动恢复）。绑定从v002到客户端重启后的v003保留。所有拍摄llvmpipe / DISPLAY=:0，shader关闭；未实测真实多人或第三方食物/光影模组。

初次capture-v001准备场地时fill已是相同方块返回0，被脚本误判；v002功能已全通过，但轮盘扇区GUI绕序反，被RenderType.gui背面剔除；仅调整顶点绕序，v003补拍确认高亮/底色。六张技能PNG未改动，直接用生成函数在内存复现逐字节一致，见icon-check.txt；资产契约check-assets.log通过。最终退出记录r113-wheel-cleanup-v001（释放按键、移除测试强加载、leave_world、quit）。

## R113 按形态羽毛、主HUD与灵泉选择

新增测试覆盖原版/第三方鞘翅保留、人形耐久/烟花、精灵破损羽毛、双向空中交接/释放/创飞、风口、死亡FORM与原版掉落、灵武器旧耐久不再消耗、灵泉发现/范围/维度/安全校验、协议v1解码拒绝及HUD分组。新增 DeferredRegister 的测试鞘翅用于兼容断言，避免运行时注册导致 Registry is already frozen。

潮夜既有用例曾在全量失败 `gloom_tide.fixed_seed_mixed_waves_include_beetles_with_expected_frequency: Wave size`。原失败日志没有n/nearby/拒绝原因，不能追认任何根因。之后单独运行、原失败坐标(8803014,180,2268086)隔离复测、带临时诊断的全量均通过，都是200波：73甲虫/61鸦/296喷吐者。按审查限时，未继续无限重跑；唯一夹具修正是给该用例覆盖base±30的全部区块自持加载票，等待FULL及实体数据加载后才开始（最多80tick），成功/异常均finally撤掉本次私有区域票（不修改共享FORCED票）。原波次断言、频率区间与生产代码不变。由于未重现原故障，“区块加载竞态”仍是高可能假设而非已证明结论。原始日志/XML、正常隔离、原坐标隔离和诊断全量在 evidence/r113-runtime-v001；临时探针全部移除。 新夹具收尾中还修正两处自身问题：同一Runnable重新排期会被测试任务表删除，改用既有onEachTick；同步生成用例只要求FULL与实体数据可查询，不要求整个外围区块entity-ticking，后者曾导致不必要的等待超时（full-4/full-5/tide-ticket-alone均保留）。

最终 `build runGameTestServer compileDevtoolsJava --console=plain` 为 **1326 JUnit / 746 GameTest，0失败、0错误、0跳过**，4分19秒。证据在 `evidence/r113-runtime-v001/full-6.log`、`final-counts.json`、`final-junit/`、`final-gametest.xml`。`tide-investigation.json`明确保留原故障n/nearby/QUERY原因未知；`check-assets.log`与`texture-check.txt`记录资源检查、8张HUD/羽毛纹理在临时目录生成后逐字节一致。全量后仅移除一个未采用的devtools鼠标探针，另跑compileDevtoolsJava，正式代码/测试未再改变。

实拍脚本 `scripts/verify-r113.py <evidence-id> [flight|beauty|inventory|hud|well|death|all]` 使用真实跳跃/使用键，记录双端状态；XTest（DISPLAY=:0）移动真实光标，工具提示仍由原版库存界面绘制。所有客户端使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0`，与Gradle全量互斥。脚本会修改验收世界，在结束后保留客户端；交接时另执行release_keys、forceload remove all、leave_world、quit。

- `evidence/r113-per-form-v001/`：人形原版FallFlying/背翼及火箭16→15，精灵GLIDE/禁FallFlying/火箭16→16，无装备/破损羽毛，真实空中双向切形。服务端装备名仍为Elytra，精灵客户端为Kuro's Feather。
- `evidence/r113-inventory-v002/`：人形/精灵各有normal与broken两张，均有真实鼠标悬停工具提示；v001没有工具提示，不作为最终名称验收。
- `evidence/r113-per-form-clean-v001/spirit-feather-clean.png`与`evidence/r113-final-captures-v001/human-elytra-full.png`：干净近景；review临时静音Aura/新粒子，仅用于看模型与举羽姿势，默认特效保留在per-form-v001。clean-v001早期人形图身体被裁切，以后补全身图为准。
- `evidence/r113-final-captures-v001/hud-clean.png`及`r113-hud-v001/`：生命/能量/灵光/护甲/XP/饥饿，中毒/凋零/吸收；`r113-well-v001/`：真实右键打开选择界面，点击目的地按钮安全到达；`r113-death-v001/`：真实死亡后复活保持精灵，物品不保留。

本轮没有逐项实拍气泡、冻结、坐骑生命、vanillaHud开关或不同GUI缩放，也没有真实多人联机、其他光影包和第一人称羽毛专门验收；这些不计入已完成视觉覆盖。最终退出记录 `evidence/r113-final-cleanup/`；截图均为原始帧，未修图。


## R112 v014 动作运行时

运行时契约与 promoted GLB/manifest 的 102 个名字及 loop 标志逐项核对。纯函数选择测试区分陆地爬行、水面/水下/踩水/水冲刺、墙附/上攀、梯子、mantle、launch/stomp、划桨、鞘翅、普通物品和施法；用 GemRender 实际 BlendEvaluator 验证 run 与 rise/fall 的权重，防止旧的 50/50 平均稀释。伤害按来源前/后/左右、大伤害/大击退选择，伤害包序号使连续受击重新播放；hurt+recover 时长取资产，不再写死9帧。

`animation_action` 新通道版本1，区分挖掘、放置、投掷、灵矛和服务端批准的蓄力/释放。客户端只可请求前四项、entity 必须为 -1，每玩家每 tick 最多一次；服务端解析不合法 entity/action/hand 拒绝，不据此施加任何战斗效果。远端观察者收到可见动作，旧动作包/能力时序不改。本地 spirit_sand 钻行、mantle 接新片段；远端仍无对应专用状态同步，未声称已解决。

实拍夹具与脚本：`scripts/capture-ori-closeup.py <id> --animation-pass --only all`，`capture_ori_animation_pass.py` 建立隔离地面/墙/梯/窄缝/水池，实际按键驱动角色，不注入片段。所有客户端使用 llvmpipe / DISPLAY=:0、关闭 shader 和自动灵焰。devtools `animation_review {enabled:true}` 只在当前 ClientLevel 隐藏 Aura 和粒子（含拦截入队），保留材质；`{enabled:false}` 或离开该世界自动失效，不写配置、不进入正式 jar。`review_camera` 正侧1.8格、targetHeight .58、follow，盾牌另用前侧机位检查竖直盾面；中心40%宽/80%高裁切避免切掉耳朵与脚，拼4×6，每段另存完整 mp4 和逐帧 pose.json。

v001–v003 是前期小图/夹具修正证据；v004 近景主要动作获审查认可。实拍发现并修复两处运行时问题：静止攀附的 deltaMovement.y 仍有重力残值，旧相位倒放，新实现取插值脚部位移（v004 ladder 的 y=131.8、clipTime=.7762964 在整个保持段不变）；短 mine 结束而 vanilla swing 仍有尾段时误切 blade_1，现在保留语义至尾段结束、真实实体攻击清掉旧标签。补拍还发现零 tick 的 Launch impulse 在输入更新后已变 idle，改用独立的瞬时事件游标消费原服务端结果，防止丢播或被后续 IDLE 重播；原位移/网络格式不改，回归覆盖模式归零、空闲与旧序号。glide 循环层权重 [1,0,0,0]、overlay 空、bodyPitch≈.1，没有共享 V 形层覆盖；开伞之前的 fall 不作为循环造型。格挡盾牌原先将已弯曲的前臂再叠 vanilla shield_blocking 45°/155° display，形成头顶桌板；第三人称格挡用掌心局部补偿抵消双重姿势，盾面随身体朝向、柄仍在动画掌心，左右手几何回归及实拍验证。

首轮完整验证1309 JUnit/736 GameTest全过；攀附/短动作尾段/hand解码拒绝修正后的完整验证为1312/736全过（pre-shield-counts.json、pre-shield-junit、pre-shield-gametest.xml，日志 final-full.log）。最终 `build runGameTestServer compileDevtoolsJava --console=plain` 为 **1315 JUnit / 736 GameTest，0失败、0错误、0跳过**（2分57秒）。日志 final-full-2.log；原始 final-junit/、final-gametest.xml、final-counts.json 已保存。check-assets.py 通过，实际加载 v014 的 SHA256 为48c5e0f1c052296795c626826083cdcfb3d05a6667252e584629dd46acf3aa90。审计文档保留 v013 历史快照，不将其58项旧清单冒充当前契约。

v005 共34段干净近景（index.md/coverage.json 标明实际片段）：主要27段加左右盾、施法、Launch、放置、投掷；除盾牌外按正侧1.8格跟拍。粒子静音本轮拦截17238次入队。launch/place 两段使用 r112-v005-followup 的后补数据，原始失败取样移至 r112-v005-original-launch-place；前者实测 launch 14帧、后者 place 8帧，保留各自源码快照与记录。盾牌左右手另有正/侧/背图 r112-shield-stills-v001。未做所有102个clip逐一输入实拍或真实多人联机验收，不将纯选择单测当成全部场景视觉验收；非核心MC状态及远端钻砂/攀越是后续验证范围。客户端最后清区块票、恢复自动灵焰/相机/界面、退出世界并正常关闭，记录 r112-final-cleanup。Codex 31个文件范围见 r112-runtime-v001/codex-files.txt；Claude 的 v014 GLB/美术源不在该清单。

## R108 反馈 C：Spirit Glow 手臂米黄（根因与修复）

根因有三层：`SpiritSelfLight` 的 block-light 15 在光影的 `lighting.glsl` 被解释为火把色（默认方案6为1/.78/.55）；GemRender第三带的alpha为paint coverage，Ori没有paint贴图所以为0，`gbuffers_spidereyes`按`alphaTestRef`将其丢弃（Iris默认eyes混合还是SRC_ALPHA/ONE，即使只放开alpha test，alpha=0也没有加色贡献）；第三人称的`clrwl_gbuffers.fsh`原先丢弃`clrwl_computeFragment`的材质结果，只给原始base贴图加光。这不是手部没有提交eyes，也不是输出到另一张emission缓冲：hand与eyes都写colortex0，Iris的HandWater程序回退到Hand。此前强制红色eyes探针能画出手臂，只能证明程序可执行，不能说明原材质的alpha通过了测试。源码与反编译依据存于`evidence/r108-hand-shader-source-v001/`；Colorwheel材质输出契约见[官方接口说明](https://djefrey.github.io/colorwheel/reference/miscellaneous/functions/)。

修复仅对显式标记的Ori材质生效：手部在hand pass按base透明度读取emission RGB并一次合成，跳过同材质的重复eyes；身体使用Colorwheel已计算的PBR材质色，不重复乘vertex color/AO。两条路径对发光像素保留原光照亮度和法线明暗，去掉暖色火把染色，普通眼睛、原版手/物品及其他Colorwheel实例不改。标记只在当前Iris配置启用本包时写入；其他包和无光影使用原顶点值。新增两项JUnit覆盖标记与GLSL常量契约、256种环境光组合、原路径alpha/light精确不变。完整`test --rerun build runGameTestServer compileDevtoolsJava --console=plain`：**1214 JUnit / 736 GameTest，0失败、0跳过**，BUILD SUCCESSFUL（3分45秒）；`r108-hand-shader-source-v001/full.log`、`final-counts.json`、`final-junit/`及`final-gametest.xml`保存原始报告。四维度hand/eyes共16个GLSL阶段离线语法通过；实际Iris/Colorwheel编译见client-2.log，包内7个相关文件与源码逐字节一致。

实拍统一llvmpipe / OpenGL4.6 / DISPLAY=:0、角色v013，包含Claude的持握和武器外晕改动。`scripts/capture-hand-shader.py`在固定暗室、午夜、FOV60、1920×1015下取空手/原版剑/四武器、第三人称持刀和原版人形。旧包由df7984a构建产物另存为`Ori Hand Before.zip`（baseline.json校验hand/eyes与HEAD一致），未临时替换主工作区文件。最终`evidence/r108-hand-shader-capture-v003/`共36张，对照与ROI完整数值在`colour.json`：

- 内侧前臂固定ROI [1370,800,1400,840]，持刀RGB=(228.75,216.35,178.16)→(216.93,225.70,224.63)；其余四种持物均从暖黄转冷白。第三人称胸部ROI [967,520,975,555]为(219.13,225.84,223.26)，不要求不同法线/材质部位逐像素相同。
- `before/after-human-empty.png`及`before/after-human-iron_sword.png`两组整帧RGB绝对差严格为0。无光影五种持物的前臂ROI，与body修复前的`r108-hand-shader-none-before-v001`逐像素一致；整帧存在非同步光晕/待机动画差异，不以此声称整帧一致。开启/关闭往返截图也一并保留。
- `after-third-blade.png`是要求的Spirit Glow第三人称持刀近景。hand-only中间版本v002仍显示身体偏黄，因此继续修正Colorwheel输出，v003才是最终对照。

编译与客户端日志在`r108-hand-shader-source-v001`。采样v001因读取Gson省略的null字段irisError而中止，脚本改为get后完成；client-2完成全部取图后退出143，无Java崩溃栈，不归为正常退出或确认OOM。另启动client-3清理测试区块票、恢复自动灵焰，再离开世界退出；结果在`r108-hand-shader-final-cleanup-v002/results.json`。本轮未改GLB/动画/怪物美术，无新增art/source文件；未做git写操作。硬件GPU及第三方光影没有实测，不把软件Mesa验证外推为全驱动兼容保证。

## R108 反馈第四批：五种腐光怪 glTF 运行时

`GloomCreatureVisual` 按实体类型选择五份 GLB，原版躯体渲染仅在该实例已有有效 GPU pose 时让位；关闭 visual、模型缺失/片段契约失败/资源重载时走旧躯体。保留实体原有预警粒子、俯冲落点与地面标记；喷吐者的旧白球/紫雾粒子属于旧躯体，仅在回退时绘制，蓄力聚光仍保留。GemRender PBR 原生读取 emission band，不提高躯体 lightmap；明暗随世界光照，白核和腐光纹路独立自发光。

新增39项JUnit覆盖五种规则、九类倒计时预警、狂怒/受伤/死亡优先级、攻击释放边沿、移动速度、俯仰正负、片段边界，以及实际五份promoted GLB的片段/1秒death契约。新增3项GameTest用原版 ClientboundSetEntityDataPacket 编解码并应用到独立镜像实体，核对鸦/翼母倒计时、两首领狂怒与phase，不改攻击周期。首轮两项测试因手工调用tick()遗漏ServerLevel负责的tickCount++而失败（39期望/40实际），原文 `evidence/r108-monster-runtime-build/full-1.log/xml`；夹具补齐年龄推进，断言不变。第二轮完整 `test --rerun build runGameTestServer compileDevtoolsJava --console=plain` 为 **1211 JUnit / 734 GameTest，零失败、零跳过**（`evidence/r108-monster-runtime-build/full-2.log`、`final-gametest.xml`、`final-junit/`、`final-counts.json`）。包含 v012 角色、v002 怪物和 Claude 修正后的遗迹装饰；六项 bridge_navigation、所有遗迹组以及 defender 均通过。随后最终 v004 全量为 **1212 JUnit / 736 GameTest，零失败、零跳过**（`full-3.log`、`final-v004-*`），包含 Claude 的甲虫有限伤害修复及两条死亡回归，BUILD SUCCESSFUL；五份打包 GLB 与 v004 manifest SHA 一致。

采样中另发现原甲虫 `/kill` 边界：FLIPPED 的 2×倍率将 Float.MAX_VALUE 变成 Infinity，最终 Health/AbsorptionAmount 成为 NaN；isAlive 与 isDeadOrDying 均为假，普通 @e 选择器也漏掉这个实体。临时逐实体诊断确定 Health=NaN 且 visual=legacy，证据 `evidence/r108-monster-runtime-build/capture-v006.log` 与 `r108-monsters-nan-probe-v008/conclusion.txt`。Claude 修复 BeetleRules.scaledDamage：BYPASSES_INVULNERABILITY 不缩放，普通缩放溢出钳至有限最大值；Codex 增加翻倒 / 正面装甲两条 generic_kill GameTest，检查同次死亡、有限生命值和20tick内移除。未改正常2×/0.2×伤害；诊断序列化已移除。

开发接口 `nearby_entities.entities[].visual` 包含 active/clip/time/speed/blend/bodyYaw/pitch/timer/enrage，只有五种腐光怪添加该字段，原id/state等保持不变。`creature_visuals {legacy:true|false}` 用同一实例切换旧/新躯体取图；`creature_action {id,action}` 调真实服务端方法（telegraph，甲虫flip，心ring/slam/enrage，翼母feathers/gust/enrage），不直接指定客户端动画。脚本 `scripts/capture-gloom-visuals.py` 使用固定第三人称机位、llvmpipe+DISPLAY=:0；临时tick rate5只用于捕捉短片段，finally解除freeze并恢复20。`review_camera` 可选 `entity` id，以客户端插值位置冻结相机锚点，避免快速飞行时服务端与客户端坐标差导致近景偏离；不传该项时保持原接口。v002死亡片段由Claude统一为1秒，匹配原有20tick移除，不延长结算。20文件清单为 `evidence/r108-monster-runtime-build/codex-files.txt`，不含Claude提供的GLB/纹理/art源。

实拍证据分开记录资产版本与采样目录版本：`r108-monsters-capture-v002/v003` 是 v002 资产；`v008` 是 v003 资产的46张完整日/夜、旧/新躯体与动作对照；`v009` 是 v004 资产的八类后段预警与 flipped/dive 共10张。v009各张 blend=1，预警实际片段进度约77–87%；Claude确认60%后的保持段可验收，不再精确追70%。飞行动作镜头使用客户端插值位置，鸦 dive 近景正确居中；翼母 telegraph/gust 另补完整翼尖。所有动作均由真实服务端方法触发，未在客户端注入假片段；临时5TPS、冻结仅用于取图。最终挑选目录为 `evidence/r108-monsters-final-v001/`（index.md / results.json 逐图保留来源和SHA）；翼母两图来自v011，其余沿用v009。最终forceload清票、leave_world、quit记录为 `r108-monsters-final-cleanup/results.json`。

前期采样失败保留：v004观战玩家重叠推挤导致翼母选择move，移走玩家并清零初始速度后解决；v005/006/007受上述NaN甲虫污染，修正清场并移除坏实体后v008完成。v003→v004 GLB/贴图由Claude promote，未修改模型源。client-3 在v009采样完成后收到SIGTERM退出143，日志无新Java崩溃报告，不能据此断言OOM或渲染崩溃；两张翼母补拍使用重启后的client-4。

## R108 反馈后续：前冲、支撑、猛击箭头、持物取景、自动灵焰调试

六项交接时合并工作区 **1172 JUnit 全过；731 GameTest 中728通过、3失败**（随后 Claude 修正装饰层，第四批全量已全绿，见上节）。失败均为新遗迹装饰引起的光桥绕行：`bridge_navigation.forest_spring_template`、`forest_spring_outside_grass`、`creek_ruins_outside_grass`，原文与路径保留在 `evidence/r108-feedback-followup-build/final-full-2.log`，XML、JUnit报告和数量为同目录 `final-*`。已交Claude处理，未改装饰层或旧断言。新增 10 JUnit / 8 GameTest（前冲/滞空/支撑 7、箭头投影与远坐标 2、手部坐标 1；真实碰撞地面/边缘/缺口/开闭活板门 5、服务端地面不改速度 1、腾空预算与落地复位 1、自动灵焰 OP 权限和开关 1）。未改旧战斗断言；包含 Claude 的 v012（manifest revision=12、ori.glb SHA256 `6653341480b175bfec47eade62ab01bab485bf138390610fc738dbed68313e8b`）。

`evidence/r108-feedback-followup-v001/three-slashes.json` 记录真实按键三次 blade start，从 z=304.5 到 305.9，净位移 1.400 格；同目录 results.json 的 flame-off/on 同时确认 client/server attachment 同步。`/ori companion flame on|off [player]` 仅 OP，默认当前玩家；会话开关不存档，重新上线/重生恢复默认。读取 `spirit_state.client.companionFlame.enabled`、`server.companionFlame.enabled`，以及 `client.bladeMotion.{groundTicks,holdTicks,step}`。

猛击有效证据为 `r108-bash-probe-v004/end-on.png`、`tilt.png`，JSON 同时记录 BASH_AIM。用原版 `/tick rate 5` 延长取图窗口，结束恢复20；没有更改能力定时器。较早截图有失焦或已松手/超时，不作为箭头通过依据。自动取图脚本 `scripts/verify-feedback-followup.py` 已补聚焦、瞄准状态断言和 finally 恢复 tick rate。

手臂修前 `r108-body-after-v004/none-arm-sword.png` / `Spirit-arm-sword.png`，本轮首采样 `r108-feedback-followup-v001`；最终 `r108-followup-final-captures-v001/{none,Spirit}-spirit_{blade,spear,bow,hammer}.png`。持物与前臂末端必须先从视图坐标变回 ModelView 输入空间：原版 GameRenderer 把相机逆旋转留在手部 PoseStack，Iris HandRenderer 使用另一条路径，直接写视图坐标会使两边位置不同。纯矩阵测试覆盖左右手、多 yaw/pitch 和 identity（Iris）；前臂末端藏到屏幕下方而掌心与武器完整可见。最终截图运行时含只打印所选 shader 名的临时诊断 supplier，截图之后移除；无其他渲染行为变化。

**历史记录：当时尚未修复的米黄问题已在上方反馈 C 节解决。** 固定暗室取景的内侧前臂 ROI [1370,800,1400,840]，无光影平均 RGB=(179.49,194.64,200.39)，Spirit=(228.75,216.35,178.16)，见最终目录 arm-colour.json。`r108-hand-isolation-v002/v003/v004`、`r108-hands-after-v001/Ori-Probe-*`、`r108-emission-combined-v001`、`r108-emission-atlas-v001` 保存独立光影包实验：关闭 eyes 对米黄底色影响很小，hand 底色置黑则手臂变黑；实际 shader 选择为 hand_cutout_diffuse 与 entities_eyes（`r108-feedback-followup-build/client-3-probe.log`）。强制 eyes 颜色/alpha/深度及去掉底色仍不足以区别缓冲顺序、深度与输出目标问题，故不声称已定位最终根因，不合入改光影包或改 emission 的推测。GemRender SurfaceBake 的第三带 alpha 为 paint coverage，不能直接当不透明度，但仅此不能解释全部探针结果。各独立探针相对原始 Spirit Glow.zip 的补丁和 SHA 位于 `r108-feedback-followup-build/shader-probes/`；运行目录里的临时探针包已移除，生产光影未变。

本轮最终全量曾遇到另一处 defender 夹具初始化竞态，原文 `Expected Cooldown ignored to be 1, but was 0`（`evidence/r108-defender-visibility-v001/full-before.log/xml`）。守卫账本已登记1，但紧接着的 AABB 实体查询为空。原全量没有区段可见性日志；自然隔离8次全部通过，同时记录了“已有方块数据/强制票、实体尚未加载且不可 ticking”的窗口。控制实验将实体区段暂时保持 HIDDEN，旧夹具8/8重现同一错误：原版 addFreshEntity 可以成功加入隐藏区段，AABB查询只查可见区段。修法仅限夹具：覆盖 base±13 的全部区块等待 areEntitiesLoaded 与 isNaturalSpawningAllowed，再从0开始原0/100/200/300tick断言；等待100tick失败时也走清理，保留原finally清票/实体/地形恢复。生产 BlightData 与失败后100tick节流不变，原断言全部保留。控制复现和修后结果见同目录 delayed-visibility/ready.log与XML；诊断用的反射、临时测试命名空间及输出全部移除。修后控制实验8/8通过（均等3tick），随后合并全量中本条通过；仅上述三条光桥测试失败。

采样全程 llvmpipe / DISPLAY=:0。client-2 在多次 shader reload 后收到 SIGTERM、退出143，无 Java 崩溃栈，不归为验证通过或确认的 OOM；client-3 完成最终八张截图及 forceload remove all → leave_world → quit，正常退出0。本轮没有改 v012、BladeSlash、FrameRecorder 或怪物资产，没有 git 写操作。


## R108 用户反馈：第一批 A–E

最终合并工作区 **1162 JUnit / 723 GameTest，0失败、0跳过**，含 Claude 并行实现的第二批斩击测试。JDK21执行`test --rerun build runGameTestServer compileDevtoolsJava --console=plain`，JUnit强制重跑；证据`evidence/r108-feedback-build/final-full-2.log`、`final-counts.json`、`final-junit/`和`final-gametest.xml`。第一批新增9 JUnit、5 GameTest：mayfly保留原版跳跃边沿，创造地面/空中、非创造模式授予飞行权限、等待队列期间权限改变、生存空中跳不回退；持物附着随实际姿势权重平滑过渡；弓弦0/.65/.9边界、弓臂范围；手臂满亮度保留原sky light。新增两侧真实GLB骨架测试计算低位单手持物：掌心在大腿旁、刀尖向前下方且不穿地，攻击/投掷权重为0时仍保留原socket校准。测试仅加载GemRender节点数学，不需要图形环境。

A/B/D/E修前为`evidence/r108-feedback-before-v002`，同seed108108、固定位置(304.5,200,304.5)、FOV60、真实按键；最终修后整体前/侧/背、第一人称、GUI及掉落物为`r108-feedback-after-v003`（48张）；左右手近侧/斜侧及弓实际闲置/中蓄/满蓄为`r108-feedback-details-v002`（27张）。`flight-comparison.json`记录：创造人形飞行true→true、创造精灵false→true，精灵创造预测跳次数2→0，生存精灵仍为2。`r108-feedback-grip-probe-v001`保留胸前持物的近侧/斜侧四武器；后续按审查改为单手垂臂，v001细节采样尚有刀尖略向上，v002按实际掌心矩阵修正为向前下方。录制脚本为`capture-feedback-weapons.py`、`capture-feedback-body.py`、`capture-feedback-details.py`，全程llvmpipe / DISPLAY=:0；没有替换工作区为旧资源，也没有修改ori.glb。

C经用户澄清指角色本身。暗室有效修前为`r108-body-before-v002`，最终为`r108-body-after-v004`；v001修前站位误入地板，不作为对照。第一人称原先使用环境lightmap，GUI的GemRender DirectRenderer没有emissive层。修后手臂补block light 15（保留sky light），两处都读取原PBR emission；该atlas的alpha是绘制覆盖率，故使用RGB加法而非再次乘alpha。GUI CPU蒙皮与原GPU底层存在深度精度差，增加polygon offset后消除亮斑；柔光受原GUI scissor限制。普通铁剑保留环境光照。

最终补拍仍保留一项观察限制：`r108-feedback-details-v002/RIGHT-spirit_blade-diagonal.png`单张未见刀身，同组side及LEFT-diagonal清晰；随后不改代码、同机位连续3张（`r108-blade-diagonal-probe-v001`）和重复侧面→斜侧切换6张（`r108-blade-diagonal-probe-v002`）均清楚，完整48张采样的刀前/侧/背也可见。暂未确定该单帧遗漏来源，不据此改动共用渲染管线或声称排除了瞬时漏绘。后6张探针原计划8张，客户端和脚本在第6张后同时SIGTERM退出143；无Java崩溃栈，记录为中断而非通过8次。最终全量后生产源码SHA256保持一致。持物手指仍使用v010，未侵入Claude负责的v011动画任务。中断后只重开测试世界，成功`forceload remove all`→`leave_world`→`quit`，正常退出；证据`r108-feedback-cleanup/results.json`及`cleanup-client.log`。

固定ROI原始PNG统计在`body-comparison.json`：无光影手臂V均值17.30→185.11、物品栏人物171.95→223.33；Spirit Glow下手臂66.52→227.72、物品栏173.43→226.12。V=max(R,G,B)，GUI只统计前后都V>40的像素，排除黑背景/眼睛；手臂为固定矩形，待机姿势微动，数值用于证明亮度恢复而非逐像素画质评级。世界光影仍会为身体/手臂染色，物品栏保留原版独立GUI色调，不声称三处像素颜色完全相同。

本轮全量曾暴露旧`merchant.ruin_async_pay`偶发失败，原文为`Missing or duplicate async guide ... message=closed,busy=true ... removed=true ... expected 1 but was 0`，保存在`final-full.log`及`evidence/r108-merchant-fixture-diagnosis/original-full.xml`。异步测试将嵌入连接玩家/商人移到远处入口，`setPos`不建立玩家加载票。隔离原付款测试4次全部抢在卸载前付完，但其中3次商人随后在第2tick以`UNLOADED_TO_CHUNK`移除；将测试手动派发查询回调延至第4tick，这3次均失败（4次中3失败），证明是回调与区块卸载竞态。生产代码正确关闭已移除商人的会话，不修改交易语义。

只修`MerchantGameTests`夹具：自持结构起点及入口±8全部区块票，覆盖商人和7格离开测试；幂等清理在成功、初始化异常、异步回调异常时恢复structure starts/placement并finally释放自己新增的票。相同延迟探针对照修后4/4通过，且逐tick断言商人未移除。原付款、余额、取消和600tick上限不改；临时延迟、诊断日志、重复用例和专用资源/命名空间均从源码移除。完整前后源码、日志/XML与summary.json在`evidence/r108-merchant-fixture-diagnosis/`。

其他中途失败均已保留：`full-1.log`为Java不允许var复合声明；`final-full.log`的新骨架测试缺少GemRender测试依赖，隔离首次又因JUnit工作目录找不到GLB，已加本地测试依赖并使用既有orimod.projectDir。第一轮客户端被SIGTERM终止（退出码143、无崩溃报告），重启后修前采样完整跑完。R109仍只存于`docs/drafts/r109-checkpoint`，未注册、未进入世界生成。

## R108 视觉打磨

最终 **1138 JUnit / 718 GameTest，0失败、0跳过**。JDK21 执行 `./gradlew test --rerun build runGameTestServer compileDevtoolsJava --console=plain`，JUnit 强制重跑；证据 `evidence/r108-polish-build/final-cyan-full.log`、`final-counts.json`、`final-junit/` 和 `final-gametest.xml`。新增 14 JUnit：开场20tick平滑、yaw最短路径、微小鼠标位移立即取消且不恢复、第三人称不启动、切第三人称终止、结束/断线清理，以及小花占比/饱和度不下降/原 cutout 掩码及盛开模型不压暗、宽光柱贴图/向上连续渐隐、三光配色/依次吸收、站位候选、全局粒子预算/去重/有界/过期。新增 7 GameTest：合法后退、墙/悬空/岩浆/水回退、生物占位避让，以及未加载区块不加票；保持权威锁位及 yaw/pitch。

首轮新用例 `restoration.viewpoint_occupied` 报 `Teleported into another entity`（`full.log`）：原版 `Level.noCollision` 对普通生物不提供所需的目的地排他保证。生产选点补充实际存活生物 AABB 重叠检测，并保留原失败断言、增加不相交断言；最终完整重跑全绿，没有放宽条件。灵叶性能复测：首次打磨全量1175叶/5tick、服务器峰值2.168141ms；引导版1379叶/6tick、服务器峰值2.212533ms；最终青色版 **1135叶 / 5tick，最大批次1.584781ms、最大服务器tick1.816692ms、最终光12**，保留现有256写入/约2ms软预算。

资源复现：`build_spirit_tree_textures.py --check` 8张、`build_wisp_textures.py --check` 6张、新 `build_restoration_texture.py --check` 1张、`export-spirit-fx.py --check` 22定义通过。FX定义/协议未改；程序脚本是原创纹理源，没有新增 `art/source` JSON。

客户端补拍使用 llvmpipe / GL4.6 / DISPLAY=:0。`evidence/r108-polish-after-v001` 保留最初不引导的对照：仅后退仍因原俯仰向下看地面。按 Claude 后续取景决定加入约1秒可打断的引导后，`r108-polish-after-v002/first-person-{20,100,180,260}.png` 分别记录第1/5/9/13秒（实际帧时刻见results.json）：三光在树前可见，随后入树、下半树冠逐层暖金花点/闪光，最后盛开。原始yaw=0、pitch=29.2488；引导目标pitch=-29.139668，后退到(4000.5,120,3990.5)，镜头类型始终第一人称。

最终小花/青色版 `evidence/r108-polish-after-v003` 用同棵树、原锚点和三个指定偏移重新拍摄；`canopy-metrics.json` 记录固定ROI `(640,145)-(1400,560)`、PIL逐像素HSV均值（0–255），排除上方HUD文字，图片不加工。同一机位未盛开S/V为110.77/104.72；第215tick为124.17/156.08（+12.1%/+49.0%）；演出结束后的永久盛开为124.25/156.30（+12.2%/+49.2%）。四朵2×2花占16/106不透明像素，保留原孔隙；青色纹理本身的平均S从0.474升到0.584。Sodium下shade=false/关闭AO的画面效果明确，未使用备选neoforge_data.block_light；方块光照与服务端预算不变。旧大花版同ROI的S/V从110.47/104.75变到80.26/112.74，确有褪色，保留为失败设计对照。

最终补拍脚本状态passed，共16张；wide-260/280实际服务端年龄261/281tick（13.05/14.05秒），能看到光柱由初现到完整亮芯/外晕。第一人称四时刻在最终v003也重拍。XTest真实鼠标中断：开始采样remaining297时引导active=true，发送(24,12)位移后remaining292时active=false/cancelled=true；继续30tick到remaining262，两次yaw/pitch均为3.6000004/24.755848，未继续引导，之后真实Esc正常结算。`results.json.mouse_interrupt`含完整前后状态。v002的首次XTest事件曾被原版ignoreFirstMove吞掉（`guide-capture.log`保留失败原文）；独立mouse-probe-v001证实后续原生事件实际改变yaw。最终脚本先在仪式外预热并断言实际转头，不改生产鼠标语义或放宽中断断言。拍摄完成后清除forceload、leave_world、quit；源码与最终全量的SHA256清单一致。

基础 R108 已在 fbb3c90 由 Claude 完成18项真实输入验收（`r108-accept-v001..v003`）。仪式中伤害被取消，`/kill`也不能触发死亡；自动测试的死亡取消是直接分派事件验证的防御路径，不声称普通实机可达。

## R108 意志之光与复苏

最终 **1124 JUnit / 711 GameTest，0失败、0跳过**。锁位修正后的首轮全量通过（`evidence/r108-final-validation-v001`）；修正R106测试场地加载票并移除临时诊断后，`cleanTest build runGameTestServer compileDevtoolsJava --console=plain`再次完整通过。由于cleanTest后的JUnit命中了构建缓存，又单独执行`test --rerun`，1124项实际重跑通过。最终完整日志、JUnit重跑日志、XML、计数、资源检查和文件清单在 **`evidence/r108-final-validation-v002/`**。

覆盖：旧NBT默认位/所有组合往返及既有进度更新不丢三光；两位真实首领死亡的多人独立实体/拾取/重复领取、实体NBT与待领身份恢复、不加载远处祭坛；三次真实净化逐人触发、离线第三次补发；七种未满位拒绝、未领树/人形拒绝；会话令牌、防重复结算、已复苏世界的其他玩家结局；真实移动包锁位而保留转头、伤害事件保护、取消后恢复受伤；断线/死亡/维度/拆树/清三光取消；旧回执登录清理、OP命令拒绝普通玩家；300tick自然完成、演出途中低/高冠层先后BLOOMED；新放叶片与区块加载初始化、整树写入预算与reset反向；实际40波复苏潮夜仅喷吐者且每波≤2；客户端显示策略锁住会话外Esc、结束、断线无结束包、超时恢复；FX/同伴旧解码器拒绝及复苏包版本、数量、范围、非法枚举/坐标/浮点值拒绝。

性能在GameTest中放置SpiritTreeShape的完整树，等待30tick让初始光照落稳，再开始永久BLOOMED。使用ServerTickEvent.Pre/Post计时，记录有写入的服务器tick以及写入批次自身时间；每tick预算是全维度总量，不是每树256。最后一轮：**1151片叶 / 5tick，最大批次1.410464ms，最大服务器tick2.174345ms，最终光照12**。锁位修正后首轮1441叶/6tick的服务器峰值1.928026ms；此前1109/1406叶的服务器峰值1.318834/1.903848ms；均未触发5ms降级条件，保留9→12和256上限，另有约2ms软时间片。异步光线程CPU时间未单独测量，这些是本机GameTest实测，不代表所有机器的5ms硬保证。reset回退所有测试叶为未BLOOMED/光9；区块不加载邻块。上述为原功能提交时的性能数据；随后 Claude 已完成18项实机功能验收，追加视觉打磨见本文件顶部。

复现检查：`python3 -B scripts/art/build_spirit_tree_textures.py --check`（8张）、`python3 -B scripts/art/build_wisp_textures.py --check`（6张）、`python3 -B scripts/export-spirit-fx.py --check`（22定义），均通过；本轮不运行遗迹生成器、没有遗迹资产变更。

中途失败原文及处理：

- `Cannot get property BooleanProperty{name=bloomed, clazz=class java.lang.Boolean, values=[true, false]} as it does not exist in Block{orimod:spirit_tree_heart}`：新灵叶与树心同名属性不是同一对象；改用共同的BLOOMED属性，后续整树/既有灵树测试通过。
- `new kinds need a new id and test entry ==> expected: <20> but was: <22>`；`expected: <60> but was: <48>`；`sound table misses WISP_COLLECT/RESTORATION`：新增固定ID表及音效登记，RESTORATION粒子基础数量收至既有balanced上限48。
- `Ceremony did not end`：embedded连接测试不自行驱动玩家tick，按既有夹具模式逐tick调用生产PlayerTick处理；没有缩短300tick条件。
- `Purification refused`：新夹具误传空区域，生产代码正确拒绝；改用实际腐化土坐标。
- `Cancellation left invulnerability`：真实新登录玩家有原版出生保护；改用可穿过原版出生保护的虚空伤害，验证仪式自己的事件保护及取消后实际掉血。
- 原R106 `blight.defender_cooldown_cap_and_unload_ownership` 曾报 `Expected 100 tick cooldown too long to be 2, but was 1`。原归档日志已保存在 `evidence/r108-defender-diagnosis-v001/2026-10-21-3.log.gz`。后续已定位并修正夹具，见下段；没有放宽断言。

R106守卫偶发失败的根因与对照实验：测试场地在模板外160格，`snapshot`/`setBlock`临时加载区块，但嵌入连接玩家的`setPos`不建立玩家加载票。隔离原用例8次，**8次全部在100tick失败**：日志记录`now == nextDefender`，说明冷却已到期；±11范围所有区块`getChunkNow == null`，合计**96次候选全部因区块缺失跳过**，首守卫以`UNLOADED_TO_CHUNK`移除、生命仍为12，且按设计保留名额。全量偶发通过依赖外围模板/区块加载布局，不能证明夹具可靠；自动灵焰已由原夹具禁用，与本次失败无关。

修法只在`BlightGameTests.defenderCooldown`：为`base ±13`涉及的全部区块调用`setChunkForced`，覆盖完整守卫搜索框；仅记录本测试新取得的票，成功、初始化异常和100/200/300tick回调异常都走幂等清理，恢复地形/难度、释放自己的票、关闭连接。生成前额外断言区块已加载，原100tick冷却、三只上限、卸载仍占名额、真正销毁可补位断言保持不变。修后隔离**8/8通过**，32次实际生成、0次区块拒绝。产品侧仍保留失败尝试后的100tick节流，防止无合法地面时逐tick反复搜索；本轮不改变守卫玩法语义。证据：`evidence/r108-defender-diagnosis-v001/{unforced,forced}.log`、对应XML、`summary.json`与临时诊断patch；临时日志代码、重复用例和专用命名空间均已移除。

## R107 实机反馈修正

JDK21 `build runGameTestServer compileDevtoolsJava --console=plain` 完整通过：**1066 JUnit / 681 GameTest，0失败、0跳过**；日志 `/tmp/r107-fixes-full.log`。本次全量无失败，较初版新增14 JUnit（11种面板1.5倍价格、台地/入口边界、中英文案契约）及9 GameTest（4朝向×±7高差的真实阶梯/净空/地基/服务可达、群系允许/排除）。原11项面板拒绝改为按新保底语义购买且重复不扣费；2项技能前置测试改为余额不足拒绝、无需技能也能按全价保底，与商人一致。

自然密度使用普通世界、实际 `/locate` 代码，不用平坦GameTest地形。seed107出生点(-96,69,368)，25点为x=-2144/-1120/-96/928/1952，z=-1680/-656/368/1392/2416的笛卡尔积；不筛除海洋。最终中位 **684.83**、最大 **1856.28**；实际入口距离672.98/1826.60，满足审查修订后的1100/2000。完整坐标、289项预检记录和源码哈希在 `evidence/r107-density-v003`；复现命令见DEVELOPMENT及 `scripts/verify-outpost-density.py`（独立新普通世界、与客户端互斥）。

- 旧48/20：289候选仅4个OK，拒绝前三BIOME154、TERRAIN_RELIEF80、ENTRY_HEIGHT38；地形原本起伏≤3、不能比入口高1以上、入口±1，共同压低通过率。
- 36/14+适坡台地/阶梯：1149.44/2598.21，未达标（`r107-density-v001`）。
- 24/10全主世界群系对照：605.68/1489.38，但观感审查要求排除沙漠/恶地/冰刺/蘑菇岛（`v002`）。
- 最终24/10、独立陆地标签：41 OK，拒绝前三BIOME126、TERRAIN_RELIEF119、ENTRY_APPROACH3，其余0。不同spacing对应不同候选位置，拒绝率不是同坐标配对比较。保底由面板商人价×1.5提供，不强制改动世界出生点或安排必出结构。

结构/纹理 `--check` 和 `git diff --check` 通过；模板尺寸及服务点位不变。原商人/指引/潮夜实机结果在 `r107-accept-v001` 与 `r107-debug-v001`；修正后的中英文UI已在llvmpipe / GL4.6 / DISPLAY=:0实拍，`evidence/r107-ui-fixes-v002`记录2语言×2职业和4张图；每个购买按钮width≥textWidth+16。实测英语苔语62/46、路路56/40，中文84/68、78/62。效果行、全文提示、路路说明可读。首个UI探针未先加载测试场地，地板fill没有完成而玩家下落，附近商人列表为空；修正夹具后通过，生产代码未因此修改。语言恢复en_us，forceload已移除、世界和客户端均已正常关闭。新的自然地形外观待Claude复验。

## R107 初版林栖驿站与交易

1052 JUnit / 672 GameTest，0失败、0跳过。完整命令为 JDK21 `./gradlew build runGameTestServer compileDevtoolsJava --console=plain`，用 `python3 scripts/test-summary.py`核对XML；最终日志 `/tmp/r107-full5.log`。结构与纹理复现：锁定nbtlib2.0.4的venv运行 `scripts/art/build_glades_outpost.py --check`，2产物相同；`python3 -B scripts/art/build_merchant_textures.py --check`，2张64×64PNG相同；`git diff --check`。未启动客户端，原遗迹NBT/layout/model没有变化。

- JUnit新增48项（含枚举扩展）：11种碎片价格/精确付款、2048种所有权组合下库存稳定/去重/排除已拥有、游戏日边界、距离/过期/缺钱；报价包往返、未知商品/负价/多于3行/重复商品/旧版本拒绝；6种指引目标往返，旧UTF指引解析器在读取新帧体前拒绝；同伴v7/v8互拒；四旋转、模板布局/群系/48×20数据契约、空旧索引和SavedData往返。
- `merchant.*`：11种真实服务端碎片交易只扣一次、库存已售不补抽、无余额、伪造实体ID/UUID/令牌/日期/库存、6格边界、下线清会话、存档日库存及跨日旧报价拒绝；树/腐地指引跳过已领取/已完成/缺失，查无目标明确反馈且不扣款；未加载目标不触发加载、2048边界；商人伤害/击退/推挤/拴绳/骑乘/和平/绑定；提示与台词间隔和once记录。异步遗迹查询覆盖真实Future完成付款及查询中离开、关界面、余额耗尽、跨日的取消。
- GameTest平坦世界没有遗迹placement，因此异步查询测试在测试源码集内临时注入一个原版random_spread placement并在结束后还原，查询本身仍走生产的加票/Future/结构start/回调。生产源码不反射Minecraft内部。嵌入连接的远处玩家不依赖实体区块tick；夹具明确调用原玩家Post tick入口。
- `outpost.*`：四旋转真实模板分块postProcess/NBT保存/继续生成/重复生成去重，双商人及旋转泉水；真实碰撞形状BFS检查人形1.8高与精灵1.2高均能从地面入口步行到两摊和泉前，外入口±1格高差净空；泉水回满、发现及与遗迹神龛往返；已加载结构引用查询不强载；ProtoChunk日志晋升LevelChunk，attachment NBT保存恢复、加载重放去重。
- 扩展R100/R106已有成功Feature测试，确认登记实际树心偏移及核心坐标；新增驿站入场台词、24固定seed潮夜波次全部零怪（先有无庇护正对照）、32/32.01边界、泉水放置/破坏即失效缓存。原11项面板购买测试及2项技能前置购买测试改为“旧action不能绕过商人”，新增商人正向用例承接旧覆盖；既有owned/equipped兼容测试不变。

中途失败与修正：首轮1052 JUnit通过，666 GameTest两项失败：`Ruin search not asynchronous`（平坦世界没有placement，合法返回找不到）；`Expected Ruin arrival ...`（新测试的目的泉前缺地板，原传送逻辑正确采用泉顶回退）。修正夹具后增加异步场景；嵌入玩家没有收到远处区块实体tick时出现`Async search did not finish`，改为与Flash等测试一致显式调用玩家tick入口。异步完成测试曾报`Missing or duplicate async guide ... was 0`：服务器已完成交易，但嵌入连接的出站队列还未排空，改用既有FxGameTests.drain并等待两tick后验证返回包。另一次全量中既有R106守卫测试偶发`Discarded defender did not free slot ... was 3`，追到同批离线奖励测试替换全局BlightData而未恢复，加入finally恢复原账本；生产净化/守卫代码未改。最终全量全部通过，新增43项GameTest（较629）。

## R106 腐化地带与净化

JDK21执行 `./gradlew build runGameTestServer compileDevtoolsJava --console=plain`：**1004 JUnit / 629 GameTest，0失败、0跳过**（较R105 +46/+50；本次庇护/掉落验证较前版 +1/+6）。最终日志 `/tmp/r106-sanctuary-loot.log`，`python3 scripts/test-summary.py`复核XML；纹理脚本 `python3 -B scripts/art/build_blight_textures.py --check` 检查9张PNG及5份核心动画元数据，`python3 -B scripts/export-spirit-fx.py --check` 检查20个FX定义；`git diff --check`通过。没有运行客户端，也没有重写遗迹资产。

- `BlightRulesTest`：五种有效来源及六种拒绝来源的矩阵；荆棘七种主动清除来源/四种拒绝来源白名单；精确血量的视觉裂纹；水平径向时刻/600tick边界；32格球边界及正负区块边缘索引、守卫100tick冷却和3只上限；64种子重复性、全部10/11/12/13半径、±14范围、1–3池/8–20荆棘/2–4枯树（3–6高）以及避开水/荆棘/树干的入口到核心路径。实测地表273–485格。保存恢复区块游标和有效时间，完成中心不再登记、完成记录紧凑及16人读取上限；worldgen local_modifications、rarity160断言。
- `blight.*` **48项**：11个来源的真实核心/BE血量与保存；真实蓄力灵焰/灵锤/光爆适配，真正飞行的灵矛只伤一次，40tick真实灵闪周期伤8且前18tick无伤。完整Feature内容、五类障碍失败零写、反向灵树互斥；600有效tick全地带复原、每tick读128/写32上限、保留玩家金块、60奖励/个人回执/拆放核心不重奖、完成中心32格潮夜庇护；轴向/光照与复原映射；未加载分区不强载和NBT续跑；四波共享预算；手放核心不凭空给地带奖励；手/箭/工具/活塞/凋灵/龙免疫；剪刀掉自身、荆棘无碰撞和能力清除、精灵接触伤害与跨丛15tick间隔；nearest半径1/1024、越界拒绝、未加载区块零加载、命令找到/未找到；hint与台词入队。
- 荆棘实机反馈回归（本次新增6项GameTest / 11项JUnit）：无敌人时连续30tick真实自动灵焰只打容器，荆棘/裂壁/裂地板/核心保持；真实敌人受灵焰伤害但相邻荆棘保留；精灵站在实际放置的荆棘中，同伴开启并走实体接触检测，30tick内掉血；普通箭、灵弧箭、灵矛真实飞行穿过无碰撞荆棘时清除。既有剪刀用例扩展为主动来源白名单、徒手不能挖和灵刀点击清除。
- 庇护/掉落复验新增：JUnit锁住32格球边界，以及正负区块边缘不能保留旧24格搜索窗口。真实完成净化后，中心玩家24个固定seed波次全部零刷怪；同场旧24格距离查询作为正对照，实际刷出65只，新版为0，防止夹具不可刷怪造成空通过。五种有效来源分别打碎核心，均无任何ItemEntity；运行时空掉落表、原版显式请求掉落的destroyBlock、创造物品仍注册同时断言。
- 后补的真实生命周期用例：18人场景仅快照16且重复begin不扩容；旧账本恢复时离线待发登录即领、已无playerdata者删除、旧待发回放不二次发奖；参与者离开32格后仍按原快照结算；非能力删除核心回收活跃账本；防守者100tick/3只上限、卸载UUID仍占槽、保存后重载并discard腾槽；**真实区块在150有效tick后无票卸载**，账本从NBT恢复且不主动加载，测试模拟玩家再次访问加载该区块后再推进450tick，完成并复原远端。
- 新hint/line和PURIFY自动加入既有枚举往返/资源/质量预算参数化测试（较原套件另增加1个清晰拒绝版本的参数样本等），FX通道8，旧v7解码器拒绝id19；同伴通道/帧头7，v6/v7在读新id前明确互拒。原贴图测试对5种核心动画严格检查12个16×16帧、4tick帧时和插值，其余贴图仍要求单张16×16。

中途失败原文及修正：
- 荆棘回归先跑旧生产逻辑，620项中恰有2项失败：`Automatic flame cleared nearby thorns`、`Companion erased contact hazard`（`/tmp/r106-thorns-before.log`）。删除自动荆棘选择分支，并将范围/单点清除统一收紧为主动来源白名单；接触伤害规则本身没有形态豁免，不需要修改，真实接触回归锁住精灵伤害。
- 协议/新FX资源接入时旧断言报 `expected: <6> but was: <7>`、`expected: <7> but was: <8>`、旧unknown-kind=19被接受，以及PURIFY源文件缺失；同步版本边界、增加旧解析器拒绝用例和源文件后通过。核心改用动画纹理后，旧单帧断言 `expected: <16> but was: <192>`，改为仅对5个核心验证12个16×16帧和mcmeta，没有放宽其他资源。
- 新夹具初次编译 `cannot find symbol: method hold(ServerPlayer)`，应调用现有 `Flash.keepAlive`；实体重建夹具的 `unsetRemoved() has protected access in Entity` 已去掉，多余调用并非生产需求（新建实体本就未删除）。
- `Expected Flash cadence or damage wrong to be 32, but was 40`：嵌入连接夹具只发保活没执行Flash玩家tick，按原FlashGameTests补真实tick入口。后续 `Flash refused` 来自每tick回调和第42tick关闭连接回调同tick无序执行；改为明确排1–40tick输入与tick，第42tick断言并关闭，生产租约/释放规则均未修改。
- `Expected Third defender missing to be 3, but was 2`：测试目标仍在距外摆核心160格以外，防守怪会按原版规则消失，提前腾槽；夹具将目标放回核心旁并让测试守卫持久化来单独验证冷却/占槽。生产守卫不强制持久化，自然消失照常腾槽。

首轮客户端（`evidence/r106-accept-v001`、`evidence/r106-debug-v001/v002`）已通过生成、核心刀免疫/蓄力伤害/持续灵闪摧毁和守卫≤3；灵闪原失败为验收脚本反复松键引发冷却。复验 `evidence/r106-accept-v002` 已通过9/10项：荆棘20→17.2、核心/守卫、渐进净化（土中途剩54，最终清零）、枯木/水恢复、60灵光与外观；24格庇护漏出24–28环带，已改32待专项复验。核心已配置noLootTable且移除路径不产物品；只读检查关闭后的orimod_dev_r106b存档，验收区未见gloom_core物品，截图形状与喷吐者旋转紫晶模型相符，但未在拍照当刻记录实体ID，因此不把目测当作确证。R105此前8项实机检查已由Claude通过（8d954c2），不再列为待验收。

## R105 腐光甲虫

JDK21执行 `./gradlew build runGameTestServer compileDevtoolsJava --console=plain`：**958 JUnit / 579 GameTest，0失败、0跳过**（较R104 +25/+41）。最终日志 `/tmp/r105-full-5.log`，用 `python3 scripts/test-summary.py`核对XML。锁定nbtlib2.0.4运行只读 `scripts/check-ruins.py`：passed、17产物、written=0；`scripts/test_check_ruins.py` **12 tests OK**；`build_beetle_texture.py --check`通过。遗迹生成仅两份layout新增点位，三份NBT相对HEAD字节无差异；原路线hash断言排除新增beetles契约后仍通过。

- `BeetleRulesTest`：身体yaw旋转后的正前/±59/±60/±61/背后扇区，上方与未知/非有限向量，5状态伤害表；15预警、30墙晕/60冷却独立时间轴，60翻倒不续期、恢复40免翻；3/8格边界。潮夜独立概率边界及固定seed105的10000波纯函数统计。
- `gloom_beetle.*` **28项**：真实灵刀前甲/背面（头转向相反仍以身体判定）；箭的速度来自正/后/侧而非射手或当前箭位置；上方伤害。真实Stomp触发、60恢复/40免翻、暴露腹部×2、Bash真实输入和取消/反冲物理；真实Goal开始预警并逐tick检查不移动、目标侧闪后方向不变；7格实际位移、60冷却；实体物理撞墙30晕，正伤害触发翻倒/拒绝伤害不触发；玩家6伤害与击退，羊/喷吐者/掉落物截停但不受伤、不自晕。进水取消预警、最多80tick内从浅水步行出来并恢复水域代价；保存/恢复翻倒、免翻、预警、方向/已冲距离；持久守卫调用原版checkDespawn仍在和平移除。灵闪无特殊效果，12格可见hint边界/人形/一次性/隔墙拒绝，R35普通2/潮汐3颗灵光，调试命令生成。
- `ruin_beetles.*` **12项**：三类×四旋转，旧piece无契约不刷；先处理首点所在区块，再保存恢复并完成全模板，所有点恰好一次。实体宽高不与遗迹碰撞、脚下干燥、位置正确、持久化；重复postProcess不增员，移除守卫后保存/重放也不补刷。采用64×48×64模板隔离遗迹和实体，防止小room模板之外的重叠。
- R101新增1项200波真实spawnWave固定seed105统计：**73甲虫、61鸦、296喷吐者**；每波1–2喷吐者且最多1甲虫/1鸦，1–4总数；已有环带/防御/上限/同tick区块共享/清场全部继续通过。原持久标签往返测试按实际mob.getType()重建，覆盖新组成。
- 同伴payload协议6；新hint中英资源与编解码往返由已有参数化测试覆盖，v5/v6在读取新id之前明确互拒，旧v2也拒绝。devtools仅甲虫新增state，原10个字段和其他实体保持；compileDevtoolsJava通过。
- Python新增无效甲虫契约拒绝：空点、重复点、埋在实体块里的点不可发布，失败仍不改文件；沿用默认只读和显式write幂等/字节复现测试。

中途失败原文和修正：
- 首次JUnit `expected: <UPDRAFT> but was: <GLOOM_BEETLE>`：甲虫枚举插到最前改变了原提示优先级，已移到既有空间提示之后，旧测试保持原断言。
- 首轮 `Flipped movement erased external recoil`、`Never reached IDLE ... travel=0.0 ... CHARGING`：移动夹具使用NoAI，原版连travel也不执行；移动用例改开真实AI，伤害隔离仍用NoAI。`Missing or duplicate generated guards` / `Old piece invented guards`来自room间距小于外摆遗迹尺寸，改用大模板隔离，未改生产去重。
- `Contact damage player: expected 14.0, got 20.0`：新连接真实玩家还有独立的60tick登录无敌，按已有DamageGameTests走61次普通player.tick后再测，并明确NORMAL难度。`Persistent beetle survived peaceful difficulty`来自手动mob.tick没有执行ServerLevel负责调用的checkDespawn，测试补走原版该入口。
- `Beetle failed to walk out of shallow water ... water=true path=Path(length=1)`是真实寻路问题：高水域代价妨碍第一步，离水Goal临时允许水中步行、结束恢复。随后路径虽标记reachable/done，但moveTo的默认1格到达容差让宽甲虫仍停水里，改为createPath(destination,0)。边界测试曾在物理移出水格的同tick读取旧浸水标志，改逐tick等实际isInWater变false（最多80），再检查避水恢复；没有删掉离水断言或开启浮游。

本轮未启动客户端；原模型/纹理预警、翻身插值、音色、真实侧闪与遗迹战斗节奏待Claude实机。坐标、命令和nearby_entities.state用法见DEVELOPMENT。R104最后的围墙水庭/中央猛击/亮桥落脚/出口容器已在evidence/r104-accept-v003通过并提交a06ece7。

## R104 光敏灵台

JDK21执行 `./gradlew build runGameTestServer compileDevtoolsJava --console=plain`：**933 JUnit / 538 GameTest，0失败、0跳过**，较R103增加17/40；`python3 scripts/test-summary.py`复核XML。光桥防绕行修正后的全量日志 `/tmp/r104-no-bypass-full-5.log`（不入库）。锁定nbtlib2.0.4运行只读 `check-ruins.py`（passed、17个产物、written=0）及 `test_check_ruins.py`（11 tests OK）；`build_platform_textures.py --check`验证3张PNG与6个JSON产物。

- PlatformRulesTest：四种光源球边界/非法距离，100tick续时、30tick预警/5tick模型相位，重叠永不强制变实，SavedData往返、区块/期限索引删除及未加载过期清理。模型分别走translucent/solid；语言与提示协议5往返、v4/v5互拒由现有参数化测试覆盖。
- `spirit_platform.*` 22项：灵灯6/7边界与灭灯后完整100tick、实际光爆5/6边界、实际Flash tick沿途点亮；树心10/11边界、两种放置顺序、常亮与拆除宽限；玩家/怪物/物品重叠和5tick内重试、持续光源等候超过100tick、无光过期不再变实、站在上面正式熄灭后碰撞下落；256块上限；拆除和到期回收、缺失方块读档清理、重载不延长期限、已加载边缘初始化不加载邻区；未亮寻路/窒息/遮挡属性、预警所有状态亮度12；真实侦测器不被预警唤醒；平台hint优先级和实际发送；拆亮灯/树心后的平台复查不会留下潮夜防御缓存。
- `ruin_bridges.*` 12项：三类×四旋转，piece契约保存恢复、世界坐标devtools字段、旧piece不推测光桥。森林入口中心及四角（局部XZ偏移(.5,.5)、(.3,.3)、(.3,.7)、(.7,.3)、(.7,.7)）逐点用真实SpiritTargets选取、发送Bash按下/松开输入，要求眼睛到灯距离≤5.5、全桥先亮、真实move落在平台上；每个样本重置夹具的灯/平台状态和目标冷却，避免前个样本掩盖失败。溪谷实际光爆覆盖5级台阶及基础100能量预算；洞穴第一灯不覆盖后段，从入口和已亮中点分别测五个站位的实际选取/视线及≥0.5格余量。四旋转实测最远距离：森林5.205，洞穴第一灯2.212、第二灯4.206。通路以玩家宽高碰撞盒检查行走/保守跳跃弧与双向可达，全灭后检查排水口/落水口/下层回程；原critical锚点未改。
- `bridge_navigation.*` 新增6项：三类×模板内/外围5格填土草至最高平台高度，搜索整个模板实际碰撞面而非只查预设路线；蹲下精灵体型不得暗态绕行，完整玩家体型须亮态双向可达，出口/所有失足点/退路可回入口，原遗迹入口可到桥入口。跳跃包络升高≤1.25、水平总长≤4，支持空中转向；防绕行允许任何下落，安全撤离要求下落≤3或落水。临时恢复森林干岸、洞穴远端台阶时必须识别绕路，恢复后再验证点亮通行。详见DEVELOPMENT。森林新增普通容器同步更新R103数量断言；原有秘室数量仍为1，移除全部容器前后地图石统计不变。
- Python契约锁住原模板尺寸、完整R103之前路线、秘室与挑战净空；新增桥长、灯数、平台、入口出口、净空、双向连接与退路支撑断言，无效光桥禁止发布。

中途失败及修正：第一次JUnit `PlatformRulesTest.ghostAndBlinkModelsUseDifferentLayersButNotDifferentLightLevels` 的 `NoSuchFileException` 来自测试工作目录，改用classpath读取。首轮GameTest的 `Occupied platform became solid`、`Lamp failed to expire`、`Tree removal timer never expired` 来自光源放在旧小模板之外，未获完整区块tick且相邻用例光源串扰；改用64×48×64隔离夹具后通过。`Lit route not traversable both ways` 来自测试把平地行走也强制作为跳跃，误撞悬臂链条；改为平地行走、台阶/空隙才测跳跃弧。`Burst left a shaft step dark` 是夹具站到首阶内部触发了正确的实体安全等待，改在入口中央起手。没有放宽生产半径、重叠判定或生命周期。

防绕行修正中间失败：首轮 `Dark exit cannot retreat forest_spring` 暴露排水口外缺少低落脚位，已补；其他 `Dark exit cannot retreat cave_altar` / `Escape does not return to entry BlockPos{x=17, y=5, z=13}` 来自直线抛物线搜索不能先起跳再前移，改为更宽松但仍限高/限距的跳跃包络。`Expected one secret vessel and two decorative vessels, got 4` 是新增桥头奖励需同步数量断言。敏感性测试随后报 `Search missed deliberately restored dark bypass forest_spring`，补入空中转向后可识别原干岸绕路；没有降低高度差或缩小搜索区域来规避失败。

Claude实机 `evidence/r104-accept-v001` 平台机制/外观10项通过；`r104-bridge-debug-v001` 发现森林入口中心选灯卡在6格边界，旧测试只站偏前的z12.85，漏掉此问题。灯前移后，`evidence/r104-accept-v002` 中央选取/点灯/落脚3项通过，但发现模板外草地能绕行。现改为内部围墙水庭、出口岛普通容器和低排水口，溪谷取消可反爬的返回梯、洞穴封闭远端并仅保留近端上行退路。新布局待实拍复验，此次未启动客户端。自然遗迹和 `ruin_state.bridges` 可用于实拍定位；原版place structure不登记结构引用，适合摆放外观、不适合验证桥查询。完整坐标和调试命令见DEVELOPMENT。

## R103 裂壁与灵光容器

全量命令：`./gradlew build runGameTestServer compileDevtoolsJava --console=plain`（JDK21），**916 JUnit / 498 GameTest，0 失败、0 跳过**；较R102增加31项JUnit、62项GameTest。`python3 scripts/test-summary.py` 已复核XML，最终运行日志 `/tmp/r103-full-3.log`（不入库）。Python使用 `~/.local/opt/orimod-art-venv/bin/python` 的nbtlib2.0.4；`check-ruins.py`只读模式通过（17个产物、written=0），`test_check_ruins.py`的10项回归及R103纹理脚本 `--check` 通过。

- JUnit：2种裂块×6种来源完整矩阵、同类面连接/空气与斜角隔离、8格球与ceil距离延迟、64块上限和快照不扩展、下砸斜下1格/3格外/错误高度、位置种子掉落25–40全范围；新hint/line的语言键/往返、协议4拒绝v2/v3、旧v3拒绝v4。
- `cracked_ruin.*`：12条真实能力命中矩阵；手/钻石镐挖掘、创造拆除，实际TNT与6强度苦力怕爆炸；原版PistonBaseBlock/WitherBoss判定、dragon_immune和enderman_holdable标签及实体破坏拒绝。3×3延迟坍塌、空气隔墙、重复命中不重排、新放/替换不继承旧波、64上限、块状态/计划刻保存往返、不加载缺失区块、下砸斜下与3格外、真正弹出敌人撞墙、取消/普通投射物不算猛击。
- `spirit_vessel.*`：服务端真实手/刀点击、玩家箭和5类能力；普通灵焰敌人优先、6格边界、裂壁视线遮挡；同tick重复命中不重复掉落，first_vessel发送后记录一次；R35磁吸/拾取入账；容器高度/亮度/无物品loot；两种裂纹hint。
- `ruin_secrets.*`：三遗迹×四旋转生成、3个容器/遗迹、封口、critical保留、地图石统计不变、piece元数据保存、世界入口/朝向、命令与devtools共同数据；旧piece无字段不迁移，外部/未加载引用返回空且不加载。Python还锁住R103之前整份路线契约（除新增secret/vessels之外）及无效秘室不能写入仓库。

首轮失败原文：`cracked_ruin.bashed_enemy_real_collision_breaks_wall ... Bashed enemy collision did not break wall`。夹具设NoAI会让Minecraft 1.21.1的isEffectiveAi=false，进而不执行LivingEntity.travel；已改为清空目标、保留真实运动，不改Bash速度。第二轮旧piece用例曾报 `Legacy lookup has invented secrets`：夹具的玩家位置偶尔跨起点区块边界而未登记引用，实际是inRuin=false；已补完整引用并拆开断言，不改查询规则。

尚未启动客户端；裂纹/晶罐/脉动、坍塌声音与波纹、自然遗迹秘室入口、溪谷返回梯、Iris观感待Claude验收。位置和命令见 DEVELOPMENT 的R103表格；旧能力与挑战GameTest保持原样。

## R102 同伴情境台词

`./gradlew build runGameTestServer compileDevtoolsJava --console=plain`（JDK21）：885 JUnit / 436 GameTest，0 失败、0 跳过。新增 CompanionLinesTest、扩充 CompanionHintPayloadTest，以及27项 `companion_lines.*` GameTest；原提示的 GameTest 未改动。

纯函数覆盖11类一次/可重复规则、6000/1200冷却边界、每条台词的最大等待边界（默认240/241、low_health60/61，含boss_start默认240）、低血量下降沿、夜间日内时间、三遗迹记录短路、清队列保留冷却、60tick显示/20tick淡出、中文含前缀≤30字与英文键。协议3覆盖全部hint/line往返、非法kind/id/坐标/布尔/版本、截断包、v2旧包被新解码器拒绝与v3包被旧解析规则拒绝。

GameTest覆盖两个首领真实setTarget开场、同战重锁/保存重载不重复、不同首领受6000tick冷却，真实翼母胜利/心脏逃亡/树心赠礼/潮夜开始结束钩子，功能actionbar包仍发送，最新覆盖/共享间隔/超时无记录，提示在实际PlayerTick→LevelTick顺序中优先，人形/未觉醒/死亡/旁观双重门控，下线不补说和换维度清队列，低血量恢复后再次下降与冷却，首次地表夜晚只说一次/地下不说，三类真实RuinPiece及已加载结构引用、缺失起点不加载、全部记录后的检测短路。新增首棵树/森林遗迹两条用例：实际提示先发送，随后真实claim/入场触发台词；在真实世界时间经过200tick间隔前断言台词仍排队且未记录，200–220tick由LevelTick监听器发送一次并写line/记录。另测low_health在60tick边界保留、61tick后丢弃且不消耗冷却。旧统一过期测试改为241tick。夹具等待真实20tick扫描时刻，不修改游戏计时。

实现中地下夜晚用例曾报 `Underground night speech`，检查发现高度兜底过宽且canSeeSky读取异步天光；改为可见天空并用同步高度图排除遮挡；最终复验结果以上述汇总为准。遗迹实拍需自然生成结构：原版 `/place structure` 不登记区块结构引用，不能验证此检测路径（源码依据见 DEVELOPMENT）。Claude首轮实机12项中9项通过（`evidence/r102-accept-v001/`）；独立行布局、功能消息共存与night_one/tide/low_health/boss_start/人形门控已通过。灵树和遗迹的3项因100tick队列比200tick间隔短而丢失，`evidence/r102-ruin-debug-v001/`补证入场检测正常；修为默认240tick、low_health60tick，等待新世界复验。`scripts/verify-r102.py` 由 Claude 维护；通过 `client.line` 验证文本、剩余tick与功能actionbar共存，截图检查中文/英文和小窗口换行、光球脉冲。只发送网络包不等同于真实客户端观感验收。

## R101 腐光潮夜

最终命令：`./gradlew build runGameTestServer compileDevtoolsJava --console=plain`（JDK 21）。结果：811 JUnit / 409 GameTest，0 失败、0 跳过；报告 `build/test-results/test/`、`build/test-results/gametest.xml`，用 `python3 scripts/test-summary.py` 汇总。

新增 `GloomTideRulesTest` / `GloomTidePayloadTest` 覆盖夜间/月相/开关、水平环带、10/32/32 防御球边界、逐玩家上限、每 tick 区块波次归属、同日与早日回拨结算、1.5 倍灵光取整、100 tick 渐变和画质粒子预算。独立协议 1 拒绝非 0/1 布尔、NaN/Infinity/越界强度、inactive 非零强度和截断包。客户端 Fade 单测包含 payload 先于/晚于 ClientLevel 变更、同维度换实例和 disconnect 清理。

新增 `gloom_tide.*` 34 项 GameTest 使用真实连接玩家、世界和事件：实际 scheduler 首波/间隔，1–4 怪组成与环带/tide NBT，逐只上限与多人同 tick 区块去重，多波灯球排除、树心覆盖环带、灵闪本人及队友防护，灯/心放置移除缓存即时失效、区块卸载与不加载缺失区块，两格净空/水/树冠拒绝，和平/开关/人形/未觉醒/创造/旁观拒绝，地下/首领战排除，命令白天失败与 stop 抑制自然重启，实际击杀与黎明奖励、同日/早日回拨、取消死亡/普通怪不计数、真实额外灵光掉落，保存潮汐怪在非潮夜重载丢弃/潮夜保留，登录/结束/换维度网络包与 devtools 字段。零奖励直接调用验证进度对象不变、无发包；结束摘要由潮夜生命周期单独发送。

夹具还原时间/难度/gamerules/三个 server config 和临时高空地面。首轮代码编译发现 Flash 新方法缺少 ServerLevel import，已补齐；没有测试断言失败。正式 jar 不含 devtools/GameTest。

客户端待 Claude 使用 `scripts/verify-r101.py` 验收（本轮未启动客户端）；用 llvmpipe + `DISPLAY=:0` 启动，客户端运行期间不跑其他 Gradle。观察 `client.tide.intensity` 在约 100 client tick 达到 target=0.35；下界 target=0，回主世界恢复。夜空紫色混合可能提亮原本近黑的天空，按实拍决定是否进一步调整。详细调试命令见 DEVELOPMENT。

需要 JDK 21、Git LFS 中的两个锁定依赖、`sync-dependencies.py` 下载的 Iris 编译接口，以及首次下载 Gradle / Minecraft 依赖的网络连接。

```sh
python3 scripts/sync-dependencies.py
./gradlew test runGameTestServer --console=plain
```

Linux / macOS 也可执行 `scripts/check.sh`（加 `--devtools` 同时同步可选依赖并编译开发源码）：先做 `git lfs fsck` 与锁定 JAR 实体检查，再运行 `build runGameTestServer`，最后由 `scripts/test-summary.py` 汇总 JUnit / GameTest 数与失败名单，任何失败或缺报告返回非零。

Windows 可用 `gradlew.bat`，或使用本机 socket 兼容包装 `./scripts/gradle.ps1 test runGameTestServer --console=plain`。

## 测试边界

最新 R13 构建与源码摘要见 `evidence/character-r13-builds-2026-10-18/report.json`，两系统仍各通过 262 / 146 项。实机脚本 `verify-character-crossbow.py` 覆盖八组装填、保持、发射和取消，`verify-character-fishing.py` 检查八组主副手与光影组合、潜行和连续移动。已修复第三人称鱼线起点；完整结果与启动失败记录见 [审查记录 R13](AUDIT_FIXES.md#r13连续持弓特殊物品与鱼线)。

| 层次 | 当前内容 | 报告 |
| --- | --- | --- |
| JUnit | 存档编解码与旧字段、价格和前置、刀连击时间线、形态尺寸、三类布局契约 85 项，GLB 动画契约与缺片段回退 61 项、移动规则和协议 92 项、遗迹旋转与指引 24 项；R15 客户端配置修正与升级 9 项、粒子预算与画质 19 项、震动闪光时间线 14 项、特效载荷 8 项，共 312 项 | `build/test-results/test/`、`build/reports/tests/test/` |
| GameTest | 形态与成长 30 项、Bash 22 项、刀目标与伤害 17 项、和平合成 16 项、伤害打断 24 项、会话生命周期 10 项、真实传送入口 9 项、遗迹旋转与生成顺序 18 项、R15 特效载荷路由 8 项，共 154 项 | `build/test-results/gametest.xml` 和控制台 |
| 真实客户端 | 形态、模型、跳跃；自投射物与 Boss 拒绝、有效 Bash、村民保护与敌怪伤害 | 仓库外 `evidence/audit-r1-smoke-v002-2026-10-18/`、`evidence/audit-r2-target-safety-v001-2026-10-18/` |

2026-10-18，Windows JDK 21.0.4 与 WSL Debian / Linux JDK 21.0.8 均通过 262 项 JUnit 和 146 项 GameTest，失败数为零。Linux 使用独立源码目录、构建输出和 Gradle 缓存；Gradle 发行包复用了已下载副本。其直接访问插件仓库曾超时，使用本机现有代理后完成下载和执行。游戏认证服务的后台连接在 Linux 留有超时日志，未影响离线 GameTest；不据此宣称在线连接通过。

R10 在最新渲染代码上重复上述两系统检查，构建与 JAR 检查记录见 `evidence/audit-r10-builds-2026-10-18/report.json`。发行 JAR SHA-256 为 `31d26687c73f7aa692ad1eb50b944fd7d083c77b60d82f0cca54c08aff099778`，不含开发接口和 GameTest。源码摘要与最终静态、动态、飞行和火焰记录一致；原始 Blender / GLB 哈希未变。第一次 Linux 命令误用不存在的 Ubuntu 发行版，保留失败日志，随后在实际 Debian 环境执行通过。

渲染脚本为 `scripts/verify-player-layers.py` 和 `scripts/verify-player-layer-motion.py`，须在专用世界运行，会使用命令布置平台和装备。最终记录分别为 `audit-r10-layers-v004-2026-10-18`（24 组）和 `audit-r10-motion-v003-2026-10-18`（实际按键录像、14 张补充截图、两次资源重载）。额外飞行与火焰夹具的完整脚本随 `audit-r10-effects-v001-2026-10-18`、`audit-r10-fire-spin-v001-2026-10-18` 保存。已发现的起飞失败与未有效触发的旋转均未改记通过，视觉检查范围见 `RENDERING_CHECKPOINT.md`。

R10 含多轮失败和复测，共约 81 MB。148 个截图或大 JSON 已无损压缩：截图保存为 WebP，逐张确认解码后的 RGBA 字节一致；JSON 使用可精确恢复原字节的 gzip。原/新文件哈希、像素哈希与路径映射见 `audit-r10-storage-2026-10-18/manifest.json`。原速 MP4 未重新编码，原失败版本没有删除。

R11 的八组实机鞘翅/激流回归位于 `audit-r11-flight-v004-2026-10-18`，脚本为 `verify-vanilla-flight.py`；前面三轮保留视觉缺陷或测试区流水污染的记录。最新代码再次在 Windows / Linux 通过 262 项 JUnit 与 146 项 GameTest。发行 JAR SHA-256 为 `9c92e27c2680bad7ba9aba2ba81a105c14d7b3e08653bf57086784a0501e4279`，原始模型哈希与开发工具隔离检查通过，详见 `audit-r11-builds-2026-10-18/report.json`。该轮同样使用逐像素核对的 WebP 与可恢复原字节的 gzip，映射保存在 `audit-r11-storage-2026-10-18/manifest.json`。

R12 恢复计划规定的精灵默认隐藏盔甲，真实客户端用 `verify-spirit-appearance.py` 检查两种光影下各五组装备状态和实际牵绳输入；最终 20 张图已查看。证据为 `audit-r12-appearance-v003-2026-10-18`，前两版观察镜头夹具问题保留。两系统 262 / 146 项通过，报告、原始 JUnit XML 与构建/客户端日志保存在 `audit-r12-builds-2026-10-18`。JAR SHA-256 为 `05648dbc7a9439fd050b00ccfdf694e255d82119a6a6018bafddbc3f680f1a08`，无开发或 GameTest 条目，原角色源文件与 GLB 哈希未变。60 张截图逐像素核对后保存为无损 WebP，全轮约 27.59 MB，映射见 `audit-r12-storage-2026-10-18/manifest.json`。

GameTest 位于独立 `src/gametest` 源码集，仅 `runGameTestServer` 加载。夹具使用每例新建的 FakePlayer、真实 ServerLevel、注册项和事件；FakePlayer 默认不会自动 tick，也不会受到伤害，测试涉及相应行为时须显式处理，不能把直接调用当作网络客户端证据。房间模板为 12×8×12，包含完整空气体积和石地板；结构方块坐标比模板原点低一格，玩家脚部使用相对 y=2。

合成用例另建真实生存 ServerPlayer，经 NeoForge `configureMockConnection` 配置 EmbeddedChannel，再正常加入和移出 PlayerList；因此难度变更会通知真实容器。它验证菜单与服务器逻辑，不代表真实客户端连线。对应实机脚本 `verify-peaceful-crafting.py` 已验证背包与工作台四种难度、旧产物清理和切回和平后的自动恢复；截图与数据在 `evidence/audit-r3-peaceful-crafting-v001-2026-10-18/`，约 5.2 MB。

GemRender 只加入客户端运行依赖。它内置的 KTX 模块依赖 LWJGL，不能随服务端 GameTest 加载。普通客户端、开发客户端、服务端各使用对应运行类路径；发行 JAR 不含 GameTest、开发接口或测试模板。构建阶段已检查该隔离。

伤害用例同样使用真实生存 ServerPlayer，在普通 tick 路径走完登录保护后调用 `hurt`，检查实际扣血、动作状态、Bash 目标释放和击退。旧实现复现 14 项误打断；自射箭用例还核对测试服务器禁用 PvP 的原版边界。24 项覆盖七种动作、环境来源、外来/无主箭及受环境伤害后的手动取消。实机脚本 `verify-damage-interruption.py` 的 14 组两端 tick 流全部通过，数据位于 `evidence/audit-r4-damage-v002-2026-10-18/`；v001 的测试准备错误原样保留。

生命周期用例覆盖主动退出、变形、死亡/维度事件、正常停服、孤立会话、完成与重复清理，以及冻结目标速度恢复、停服后兜底和搜索冷却清理。洞穴用例直接使用构建时复制到测试命名空间的正式模板，避免测试副本漂移。GameTest 的平坦生成器没有遗迹候选，不能据它宣称搜索票据通过；该项由 `verify-server-lifecycle.py` 在正常世界生成器中检查，开发端从原版 DistanceManager 独立计数票据。`audit-r5-lifecycle-v003-2026-10-18` 验证搜索、Bash、挑战目标退出与重进、另一存档切换均清理；v002 的窗口焦点失败记录保留。

动画测试读取实际打包 GLB 的 JSON chunk，检查共享契约与有效时长，并逐一移除 58 个要求片段，覆盖 idle 同时缺失和完全无动画的解析结果。实机 `verify-animation-fallback.py` 只临时修改 `build/resources`，对缺 `blade_2_left`、缺 idle/双手持物、零动画三种资源重载，检查左手连击、世界与背包渲染，再恢复完整模型。零动画期间复跑 17 组动作/伤害检查；该轮还修复了弓受击后右键未松开时偶发自动重新蓄力的问题。证据为 `audit-r6-animation-v002-2026-10-18` 及其 `-actions` 目录，四张截图均已查看；主目录约 7.65 MB。临时 GLB 已逐字节恢复并核对 SHA-256，原始模型、导出物和 Blender 文件未改动。静止 T 姿势是资源损坏时的兜底，不计为正常动画质量通过。

移动规则新增 90 项 JUnit，其中 35 项覆盖跳跃优先级、地面/空中次数、土狼窗口与各动作的接管规则；其余覆盖方向、速度边界、15 个固定协议编号的完整数据包往返和未知编号拒绝。模式协议版本升级为 2，存档格式不变。Windows 与 Linux 的最新日志为 `.external/audit-r7-final-build2.log`、`.external/audit-r7-final-linux.log`。

R7 实机复跑基础移动 5 组、边界 10 组、卡顿 6 组、战斗跳跃 3 组、空中动作 11 组和 Bash 8 组，共 43 组。证据为 `audit-r7-movement/edges/combat-jumps/air/bash-v001-2026-10-18` 和 `audit-r7-hitches-v002-2026-10-18`，合计约 6.6 MB；前两类旧脚本仍使用原有状态格式。0–500 ms 卡顿下最高点均为 102.504，服务端仅接受两次跳跃，无预测错配或拒绝。卡顿 v001 在准备阶段因空中切回生存后摔死而失败，现先落在测试平台再切生存；原记录保留。自动跳跃、连续高速位移与真实传送仍是下一轮独立边界。

R8 在修复前实测自动跳跃越过整格方块却未计数，Bash 后 600 ms 停顿造成约 10.24 格连续位移并误耗空中次数。修复后自动跳跃走同一输入与预测流程；真实传送入口发送 RELOCATE，清理两端状态与待确认预测。协议为 3，新增编号 15，九项 GameTest 覆盖短/长/相对传送、Bash、滑翔、排队输入、人形，以及 9/32 格连续移动。

R8 又发现滑翔三 tick 续期在 1500 ms 停顿中到期，使同段下降量由 4.8 增至 29.31 格。续期现为 40 tick，仍由本地即时检查退出条件。最终四种停顿下下降量为 4.876–4.996 格，五种退出条件与 17 组伤害检查通过。此结论仅覆盖实测的 0–1500 ms 单人停顿，不代表任意长卡顿或多人网络验收。

本轮 65 组实机检查均通过：`audit-r8-auto-after-v002`（6）、`audit-r8-motion-after-v003`（8）、`audit-r8-teleport-v001`（3）、`audit-r8-jump-hitches-v001`（6）、`audit-r8-combat-jumps-v001`（3）、`audit-r8-air-v001`（11）、`audit-r8-surfaces-v001`（6）、`audit-r8-glide-exits-v001`（5）、`audit-r8-damage-v001`（17），目录统一追加 `-2026-10-18`，合计约 9.4 MB。初次新 Mixin 目标签名错误导致启动失败，修正后的客户端完整运行；原失败日志与准备阶段的旧坠落状态记录保留。最终跨平台日志为 `.external/audit-r8-final-build.log`、`.external/audit-r8-final-linux.log`。

R9 的 24 项 JUnit 覆盖旋转单元格、连续坐标逆变换、通道包围盒、旧起点候选、完整搜索范围、入口距离剪枝和自然树避让范围。18 项 GameTest 包含 12 种类型/朝向组合、四种引道区块处理顺序、旧 NBT 与非生成环境的树木规则。每次引道读取或写入均由代理检查当前区块范围，正序与倒序输出逐方块相同。初次用 FakePlayer 测试返回位置时，其空实现的 `teleportTo` 导致 12 项夹具失败；改用实际 ServerPlayer 后通过，没有放宽正式返回判定。最终日志为 `.external/audit-r9-trees-win.log`、`.external/audit-r9-trees-linux.log`。

`python scripts/verify-worldgen-coverage.py <新运行名>` 会建立三个独立普通世界，以原版定位实现运行 225 次查询，并保存实际起点、旋转、入口、距离、耗时和源文件哈希。报告目录为 `evidence/<运行名>/`，世界及服务端日志在 `.external/worldgen-audit/<运行名>/`。测试期间不允许修改生成源码或资源。基线为 `audit-r9-density-before-v001-2026-10-18`，最终为 `audit-r9-density-after-v004-2026-10-18`。

| 每类 75 次定位 | 原间距 16 / 分隔 6 | 最终间距 6 / 分隔 3 | 最终距离中位数 |
| --- | --- | --- | --- |
| 林间灵泉 | 60/75，80.0% | 73/75，97.3% | 346 格 |
| 溪谷灵迹 | 44/75，58.7% | 71/75，94.7% | 522 格 |
| 洞穴祭坛 | 34/75，45.3% | 69/75，92.0% | 761 格 |

覆盖率指距离不超过 1500 格；按实际入口统计计数相同。这是固定种子样本的结果，不保证任意出生点。最远洞穴仍为约 2331 格。第一轮旋转实现因噪声采样过重，八次查询耗时 274.7 秒后中止；先筛群系、先查角点、去重高度采样并推迟完整水柱检查后，同八个结果坐标不变，耗时 24.3 秒。该中止报告和间距 8 未达标的完整报告均保留。

模板生成器输出统一 UTF-8/LF，`check-ruins.py` 同时检查 NBT、布局、群系与生成间距的重复生成哈希。第一次检查发现手工改过的间距文件被 Windows 换行重写；修正写入方式后重复生成一致，生成内容未因此改变。完整无命令生存流程仍须单独验收。

最后关闭客户端后再次执行 Windows `clean build runGameTestServer compileDevtoolsJava`，262 / 146 项仍全部通过，日志为 `.external/audit-r9-clean-final-win-v2.log`。此前客户端运行时的 `clean` 因游戏 JAR 被占用而失败，且已经删除部分松散资源；同期远距离搜索记录已明确作废。新的独立世界复测通过，见 `WORLD_CHECKPOINT.md`。发行 JAR 另检查内部布局位置与开发/测试源码隔离。

## R15：客户端设置与特效预算

2026-10-18 云端 Debian 13 / OpenJDK 21.0.12，新检出 `1c58dee` 后先跑基线：262 项 JUnit、146 项 GameTest 通过，`compileDevtoolsJava` 通过。R15 后 `scripts/check.sh --devtools` 结果为 312 / 154 全部通过；发行 JAR 不含开发或 GameTest 条目，SHA-256 `60df1c069d300f653ad81526b53263e21eec5c21df68487ca6218916f1a7dcd4`（云端构建，与 Windows 构建哈希不可直接比较）。Windows 未复跑。

- 配置：`orimod-client.toml` 新增 `configVersion`（缺失视为 v1，加载时升级到 v2 并记日志）、`visuals.quality`（FLUENT / BALANCED / DETAILED，默认均衡）、`visuals.advancedParticles`（默认开）、`feedback.screenShake`（0–100，默认 30，较轻）、`feedback.screenFlash`（0–100，默认 40）。JUnit 直接对真实 `ModConfigSpec` 执行 NeoForge 加载时的修正：v1 文件保留 `keyDefaultsApplied` / `spiritCamera` 并补默认值；越界强度、未知画质和视角名回到默认，其他值不变；新版本写入的更高 `configVersion` 不降级。运动模糊、强景深和动态灯光在模组中不存在，因此没有对应开关，也没有默认开启的效果。
- 粒子：原先五处服务端 `sendParticles`（刀命中、下砸落地、灵光箭尾迹、挑战检查点、路线标记）改为 `SpiritFx.emit`，向 32 格内玩家发送一个固定编号的 `orimod:spirit_fx` 载荷。客户端 `FxBudget` 把每类效果分为关键粒子（强制生成，原版“最少”粒子也保留）与装饰粒子（按画质 0.5 / 1.0 / 1.5 缩放，上限 48，仍走原版粒子等级）。均衡档总数与 R1–R13 实机证据使用的数量一致；关闭高级粒子只保留关键粒子。GameTest 用真实连接玩家检查每类效果只发一个载荷、坐标和来源正确、超出范围不发送，并确认刀命中不再发送原版粒子包；临时改回 `sendParticles` 时该用例失败，恢复后通过。
- 震动与闪光：`ScreenFeedback` 是纯逻辑时间线。精灵形态受击触发震动（100% 时峰值 2.5°，6 tick 二次衰减）和冷白闪光（100% 时 α 0.35，4 tick 线性衰减）；本人下砸落地只触发 0.6 倍震动。两项都只按用户百分比缩放，0 完全不触发，设置中途归零立即停止；形态切换、死亡或断线清空。原版受伤镜头倾斜保持原版设置，独立存在。
- 设置界面：模组列表的“配置”按钮使用 NeoForge 生成界面，分“画面”“震动与闪光”两节，画质显示流畅/均衡/精致；`configVersion` 与按键标记仍保存在文件中但不显示。

云端实机：Xvfb 1920×1080、Mesa llvmpipe（OpenGL 4.5），开发客户端约 18 FPS，光影关闭，无声卡。只说明设置链路与数值行为，不作画面、光影或性能验收。

| 检查 | 结果 | 记录 |
| --- | --- | --- |
| v1 配置启动 | NeoForge 修正文件并记录 “v1 -> v2”，保留 `keyDefaultsApplied = true`、`spiritCamera = "FIRST_PERSON"` | `evidence/settings-r15-manual-v001-2026-10-18/`（含前后文件与日志） |
| 界面修改 | 真实滑块与按钮修改震动、闪光、画质、高级粒子；“完成”后写回文件 | 同目录截图（英文初版、中文 v2/v3） |
| 受击 | 0% 震动 / 100% 闪光时只记录闪光峰值 0.35；100% / 0% 时只有震动峰值 2.5，且相机角度非零 | `settings-r15-script-v004-2026-10-18/results.json` |
| 刀命中粒子 | 均衡+高级：1 关键 + 3 装饰；流畅+高级：1+1；流畅+关闭：1+0 | 同上及手动记录 |
| 重启 | 设置修改后两次客户端进程重启值不变；第二次由脚本 `--expect` 断言 | `settings-r15-restart-v001-2026-10-18/` |

`scripts/verify-client-settings.py <运行名>` 在开发客户端中走真实设置界面完成上述受击与刀命中检查；重启后用 `--expect '{"visuals": "..."}'` 断言保持。脚本前三版因点击过早、刀连击恢复期和死亡动画中的旧僵尸被选中而失败，均为夹具问题，已修正，失败目录未保留到证据（运行在云端，未产生需要保存的游戏状态）。连续截图未捕捉到 4 tick 闪光帧，闪光与震动观感需本机复查；界面截图只覆盖 GUI 4。

## R16：遗迹世界边界

云端 `build runGameTestServer`：337 项 JUnit、164 项 GameTest 通过（R15 为 312 / 154）。新增 `RuinSiteRulesTest`（25）和 `RuinBoundaryGameTests`（10）。自然站点审计 `scripts/verify-ruin-sites.py`：修复前 117 个站点 115 个无问题，修复后 117 / 117；详见 [WORLD_CHECKPOINT](WORLD_CHECKPOINT.md#r16-自然站点审计world02-边界)。

## R17：无命令生存流程（服务端）

337 项 JUnit、167 项 GameTest 通过（新增 `SurvivalFlowGameTests` 3 项）。全新连接的生存玩家（未觉醒、空背包）只通过玩家输入会到达的服务端入口推进：工作台菜单点击制作核心、`useItem` 觉醒、面板领取刀、`LivingDropsEvent` 掉落后 `playerTouch` 拾取（和平改为工作台合成灵光后拾取）、`useItemOn` 使用石台、面板购买能力、挑战首通，最后 `saveWithoutId` → 新 `ServerPlayer.load` 比较觉醒、能力和进度。另测配方书：拿到铜锭解锁核心/指引/灵光配方，紫水晶解锁刀/弓配方。路径与灵光预算见 [SURVIVAL_FLOW](SURVIVAL_FLOW.md)。

## R18：正式效果与声音生命周期

396 项 JUnit、183 项 GameTest 通过（新增 `SpiritFxLifecycleTest` 21 项、载荷与预算参数化用例随新类型增加，`FxLifecycleGameTests` 8 项、`FxGameTests` 新类型路由 8 项）。

- 协议：`orimod:spirit_fx` 版本 2 增加阶段字段：BURST 单发、START 开始或心跳、STOP 结束一项、STOP_ALL 结束该实体全部持续效果；单发类型收到 START/STOP 时拒绝解码。新类型编号 5–12 只追加，R15 的 0–4 不变。
- 服务端：`SpiritFxLifecycle` 在移动 tick 之后按权威状态（滑翔模式、抓钩进行中）计算应有的持续效果，变化时发 START/STOP，每 20 tick 心跳；变身、死亡、换维度、退出发 STOP_ALL，停服清空。动作单发效果在 `signal` 处产生，抓钩每 tick 重发的信号只产生一次钩出效果。
- 客户端：`SpiritFxTracker`（纯逻辑，可单测）记录持续效果；实体消失、三次心跳缺失（60 tick）、离开或切换世界、断线都结束效果并停止循环音。
- 声音：`SpiritSoundCue` 为每一类列出原版声音或静音（尾迹、标记、牵引绳）；刀命中与下砸原有声音并入表中，音量音高不变。

云端实机（Xvfb，光影关闭，无声卡）：`scripts/verify-fx-lifecycle.py` 在开发世界中用真实跳跃键滑翔，确认客户端出现 GLIDE 持续效果和一个循环音，松键、变身、死亡、进入下界后分别在数 tick 内清空，最终 `active=[]`、`loops=0`，开始/结束次数 9/9。能力和平台用开发夹具。v001 因设置界面值在“完成”后才提交、脚本过早读取而失败（夹具问题，见 R19），证据保留在 `evidence/fx-r18-lifecycle-v001-2026-10-18/`；v002 通过。实际声音、粒子观感和动态灯光未验证。

## R19：精灵视角循环按钮

404 项 JUnit、183 项 GameTest 通过（新增 `SpiritCameraSyncTest` 6 项、`ClientConfigTest` 2 项）。`spiritCamera` 改为枚举配置，名称与旧文本值相同，三种旧值均原样加载，未知值回到默认第三人称背后。设置界面根页第一行显示为循环按钮，标签中英文。精灵形态中在菜单改视角会立即应用到当前镜头，按 F5 改视角会写回设置；同一 tick 两者都变时以菜单为准。

云端实机：`fx-r18-lifecycle-v002-2026-10-18` 在精灵形态连续三次“打开设置 → 点循环按钮 → 完成”，设置依次为第三人称背后、正面、第一人称并回到起点，每次实际镜头与设置一致；截图 `settings-camera-cycled.png`。v001 失败是脚本在“完成”之前读取设置（NeoForge 界面在完成时才提交），同时暴露了旧逻辑会用当前镜头覆盖菜单选择，已修复。多 GUI 缩放未截。

## R20：自带光影 Spirit Glow

417 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`，新增 `SpiritGlowSyncTest` 13 项：首次运行、自选包保留、开关应用、Iris 侧变化写回、恢复规则、状态文件读写/损坏回退、打包内容含两份许可证且无 `mellow.png`）。发行 JAR 含 `orimod_shaderpack/spirit_glow.zip`（`zipSpiritGlow` 生成，固定文件顺序与时间戳），不含开发或 GameTest 条目，SHA-256 `d1f14f20a343eb39bf38595bab9d9ff2b078b1c9ec3bfb1dc6a7351f725123ea`（云端构建）。

云端实机（Xvfb 1920×1080、llvmpipe，Iris 1.8.12）：
- `glow-r20-v002-2026-10-18`（`scripts/verify-spirit-glow.py`，Iris 原选 Mellow）：首次运行 `save_off`，Mellow 保留；开 → `Spirit Glow.zip` 管线建立且 `Iris.getStoredError` 为空，白天/夜晚截图；在 Iris 侧关光影 → 开关写为关；Iris 侧再开 → 开关写为开；关 → 恢复 Mellow。v001 在开启后发现记住的原包为空：设置文件保存后 NeoForge 的重载与同时写入的记录竞争，记录已移到独立的 `config/orimod-spirit-glow.properties`，v001 证据保留。
- 未选光影包的新配置：首次运行直接 `enable`；随后经真实设置界面（模组 → 配置 → 画面 → 开关 → 完成）关闭，因无原包而关闭光影；再开启回到 Spirit Glow。
- 自选包被保留时的一次性提示已触发（诊断 `keptPackHint=true`），截图时提示已消失，画面未截到。

## R21：Spirit 配色

420 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新增 `SpiritGlowPackTest` 3 项）。发行 JAR SHA-256 `609a761aa59d48acc1e16587a80e82aef49d24bfbf494a05b00a671252560eb3`（云端构建）。云端实机：固定场景截图（见渲染记录）；JAR 内新包替换旧包后管线正常；把记住的原光影包改为不存在的文件后关闭开关，结果为关闭光影、未选择缺失的包。

## R22：Spirit 风格

421 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；`SpiritGlowPackTest` 新增一项：每个 Spirit 风格选项都在其设置页且有中英文名称、风格与距离雾默认开、油画感默认关、体积光默认开）。云端实机：开发实例分别加载 R21 与 R22 包目录，同场景截图；Iris 日志无编译错误，油画感 + 色阶化变体同样建立管线。

## R23：画质档与光影性能档

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新增 `SpiritGlowProfileTest` 7 项：映射、首次写入、档位切换只换自己的值、保留玩家修改、首次运行前已有的值不接管、默认值收回、文件读写；`SpiritGlowPackTest` 新增 2 项：profile 行等于包默认加模组覆盖值、布尔选项都有普通 `#ifdef`）。发行 JAR SHA-256 `ccb541a2b8a86333500ca94ec79e8868c50887503e2f8063a692f4364b8ab765`（云端构建）。云端实机见渲染记录。

## R24：下界、末地、雨天

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`；新选项由现有 `SpiritGlowPackTest` 覆盖：在设置页、中英文名称、默认值在可选值内）。发行 JAR SHA-256 `778a254f54c7f53386721a719b2cc6e378e9c695724cf4d9e4ded5a448c26df1`（云端构建）。云端实机截图见渲染记录。

## R25：下界与末地画面

430 项 JUnit、183 项 GameTest 通过（`scripts/check.sh --devtools`，仅光影改动）。云端实机截图与对照见渲染记录。

## R26：洞穴火把范围

430 项 JUnit、183 项 GameTest 通过（仅光影默认值改动，由 `SpiritGlowPackTest` 检查默认值在可选值内）。

## R27：100 次混合移动与效果往返

431 项 JUnit（新增一次性移动标签落地结束的规则测试）、183 项 GameTest 通过。云端客户端 `scripts/verify-movement-endurance.py move12-r27-v006`：100 次混合动作与取消全部通过（种子 20261019；跳 14、冲刺 13、滑翔 18、抓钩 25 次其中到达 6、Bash 6 次另取消 9、蹬墙 5、下砸 4）；修复前 `move12-r27-v005` 13 次标签残留失败保留。v001–v004 为场地搭建与低帧输入过期的夹具问题。`scripts/verify-fx-endurance.py perf02-r27-v001`：10 轮效果往返，活跃效果/循环音每轮回 0，粒子回 0，GC 后堆 622–625 MB，预热后斜率 −0.10 MB/轮。两脚本需开发客户端（`runDevtoolsClient`）与任一开发世界；移动脚本会在 (1000,200,1000) 建场地，效果脚本复用该场地。

发行 JAR（R27，`scripts/build-release.sh`）：两次 `clean jar`（不用 Gradle 构建缓存、各自重新编译）SHA-256 一致 `4e0651a269a897c252a06e97d685bf6c1b0f574550dd39882d66058eea977b16`；提交 `b0f397d` 与只改构建脚本的 `976041e` 构建出相同字节。JAR 含 `orimod_shaderpack/spirit_glow.zip`（含 LICENSE、LICENSE-APACHE、README）与 `THIRD_PARTY.md`，不含开发工具类。云端产物在 `/workspace/oriMod/release/`（不入库）。

## R28：原版存档副本与干净实例

无代码改动，自动检查沿用 R27（431 JUnit / 183 GameTest）。`save04-r28-server-v001`：原版 1.21.1 服务端建世界并做标记 → NeoForge 21.1.248 服务端 + 发行 JAR 打开副本，标记全部保留，三类遗迹可定位。`rel01-r28-clean-client-v001`：portablemc 5.0.6 新建干净客户端（Xvfb + llvmpipe），A 只装 OriMod → 缺依赖界面；B/C 加 GemRender → 进入原版副本；D 加 Sodium + Iris → Spirit Glow 自动启用。

## R29：验收计数、两视角交互、并发卡顿

431 JUnit / 183 GameTest 通过（开发接口改动）。`scripts/check-acceptance-counts.py` 校验验收表 79 行与摘要计数。`play04-r29-v001`：原版交互脚本在开发世界副本 `orimod_dev_play04_r29` 运行，4 组全部断言通过。`perf05-r29-v001`：空场地 vs 技能并发各 40 秒帧时间与服务端刻耗时，见验收表。

## R30：长时间会话

`perf03-r30-seg1/seg2`：见验收表 PERF03。脚本时长为活动时间（单调时钟），盒子挂起期间不计。

FX02 第 1 步：459 JUnit（`SpiritFxDefinitionTest` 28 项：每类效果的导出定义与源一致、首版与 R18 外观一致、分层分配、损坏定义拒绝）、183 GameTest；`check.sh` 末尾运行 `export-spirit-fx.py --check`。

## R31：正式效果外观

459 JUnit（定义测试改为：第一层为本模组灵光、所有粒子为已知原版简单粒子或本模组附带贴图）、183 GameTest；`check.sh` 校验效果导出与粒子贴图均为最新。`scripts/verify-spirit-fx-look.py fx02-r31-look-v002`：加载与重载检查、13 种效果截图拼图。

## R32：效果可读性

469 JUnit（新增：形状字段解析与默认值、非法形状/颜色/半径被拒、预算只有 1 颗时必显粒子仍落在第一层；编号测试扩到 0–13、持续效果为滑翔、钩索牵引、Bash 瞄准；通道版本 3）、184 GameTest。`scripts/verify-spirit-fx-look.py fx02-r32-look-v002`：14 个定义加载、资源重载后仍 14 个无错误；一次性效果只播一次（与游戏一致，不再叠 6 次），持续效果/拖尾每 tick 播放；第一人称旁观视角距效果约 3 格，分别在约 2 tick（早期）与约 5 tick（中段）截图，带名称标签合成 `shots/r32-fx-look-sheet.png`；再以创造模式 Ori 形态第三人称截 6 张合成 `shots/r32-fx-ori-scale.png`。开发配置的高级粒子临时打开后已恢复。注意 llvmpipe 约 10 FPS，客户端每帧追赶多个 tick，帧间间隔比真实帧率粗。

## R33：Bash 反弹投射物

471 JUnit（`BashRedirectTest` 速度下限/增益/上限）、186 GameTest（新增飞行箭反弹归属与速度、插地箭不改归属）。

## R34：灵魂链接

474 JUnit（`SoulLinkTest` 拒绝原因与安全条件）、189 GameTest（新增：建立链接扣能量并存储、冷却；人形或能量不足时拒绝且不扣能量；头顶被堵的链接不安全）。复活回链接的实机检查见后续客户端验证。

## R35：灵光球

476 JUnit（`SpiritLightOrbTest` 数量与吸附半径）、190 GameTest（新增：人形不吸，精灵形态只吸 2.5 格内的灵光球并朝向玩家）。

## R36：生命格与能量格

477 JUnit（`SpiritCellsTest` 存档键与上限）、192 GameTest（新增：生命格 +2 最大生命、能量格 +10 上限、第 11 个被拒、重复重算不叠加；未唤醒拒绝）。

## R37：同伴灵焰

486 JUnit（`SpiritFlameTest` 开火条件与调参；效果定义 15 个、编号 0–14、通道版本 4）、194 GameTest（新增：不打被动生物、锁定最近僵尸并造成 2 伤害、隔墙不打）。

`scripts/verify-ori-parity.py parity-r37-v001`（云端客户端）：灵魂链接复活回位 True；灵焰对僵尸 18→4。

## R38：灵跃 Launch / Charge Jump

492 JUnit（`LaunchRulesTest` 方向抬升、速度、门槛；协议编号 16；一次性标签含 LAUNCH）、197 GameTest（`ParityGameTests`：无技能或未蓄满不触发、蓄满地面灵跃速度与上升、空中一次并恢复二段跳/冲刺）。

## R39：光爆 Light Burst

494 JUnit（`LightBurstTest` 拒绝原因；技能定义含 light_burst）、199 GameTest（新增：未解锁不能扔、扣 15 能量、冷却、自己的光爆可猛击、爆炸只伤僵尸 6 点不伤猪、只爆一次）。

## R40：灵风口与三段跳碎片

497 JUnit（`UpdraftRulesTest` 加速与上限、三段跳计数；碎片定义与掩码 63）、202 GameTest（新增：风口正上方抬升、隔实心方块或偏离风口不抬升；无碎片只能空中跳一次、装备后两次）。

## R41：灵泉 Spirit Well

499 JUnit（`SpiritWellTest` 排序循环、维度隔离、键解析）、203 GameTest（新增：回满生命能量并有冷却；只有一处时不能传送；两处时落点在第二处神龛前；第二处被毁后无落点）。

## R42：原创射弹敌人：腐光喷吐者

501 JUnit（`GloomSpitterTest`：球速、伤害、射击间隔，生成数据只在主世界）、204 GameTest（新增 `gloom_spitter_spits_bashable_orbs`：喷吐者是 Enemy；吐出的球归属喷吐者、朝向玩家、可 Bash；未反弹时伤害记在喷吐者名下；反弹后命中喷吐者伤害 3 并记在玩家名下）。

## R43：灵光球发光拖尾

504 JUnit（`LightOrbGlowTest`：静止不拖尾，飞行拖尾不超过 3，光晕尺寸范围）、204 GameTest。

## R44：遗迹内的生命格/能量格祭台

504 JUnit、205 GameTest（新增 `flow.ruins_hold_claimable_cell_pedestals`：三种遗迹按不同旋转放置，祭台存在、类型正确、脚下实心、上方是空气；同一玩家只能领一次，换一名玩家仍可领取）。

## R45：真实客户端验证 Bash 反弹投射物

真实客户端 `verify-gloom-bash.py r45-gloom-bash2`：aimed=True，目标 id 等于球 id，释放后球速 (0, 0, 1.568)，喷吐者 12→9，状态 passed；证据在 `evidence/r45-gloom-bash2`（不提交）。

## R46：遗迹滑翔路线加灵风口

206 GameTest（新增 `flow.glide_ruins_lift_over_their_gaps`：两处遗迹旋转 180° 放置，每个灵风口都存在，且上方 6 格处有抬升效果）。

## R47：灵跃接触伤害

504 JUnit、207 GameTest（新增 `parity.launch_contact_hits_enemies_once`：僵尸受约 5 点伤害（有护甲减免），村民不受伤，同一次灵跃不会重复命中）。

## R48：腐光喷吐者外观与攻击预警

505 JUnit（`GloomTelegraphTest`：核心尺寸随蓄力单调变大，不超过 0.55）、208 GameTest（新增 `parity.gloom_spitter_telegraphs_before_spitting`：攻击后进入 14 tick 蓄力，重复攻击不重置，第 14 tick 才吐出归属喷吐者的球）。

## R49：灵锤 Spirit Smash

507 JUnit（`SpiritHammerTest`：落点沿朝向、蓄力和冷却下限、伤害）、209 GameTest（新增 `parity.spirit_smash_hits_ahead_and_downs_flyers`：前方地面目标和空中目标都受伤，空中目标被砸落，地面目标被掀起，身后的目标不受伤）。

## R50：首个原创首领：腐光之心

510 JUnit（`GloomHeartTest`：一半生命时换阶段，二阶段更难，重击只在二阶段出现，每种攻击预警 ≥20 tick）、213 GameTest（新增：祭坛只唤醒一只；齐射和环形在预警结束前不出球，之后分别出 3 颗、8 颗可 Bash 的球；自己的球不伤自己，反弹球造成约 10 伤害；半血进入二阶段、无敌窗口、召唤 2 只喷吐者、重击标记在玩家位置；洞穴遗迹逆时针 90° 放置后祭坛存在，上方 7 格是空气）。

## R51：真实客户端验证灵锤与首领

真实客户端 `verify-boss.py r50-boss2`：状态 passed；灵锤 16→2.16；Bash 尝试 3 次，第 3 次 aimed=True、目标为球 id 217，首领 112→102.32；二阶段召唤 2 只喷吐者。证据在 `evidence/r50-boss2`（不提交）。

## R52：灵锤重砸纳入能力树

511 JUnit（`SpiritHammerTest.needsSpiritFormAndTheSmashAbility`）、214 GameTest（能力面板相关的生成用例自动包含新能力）。

## R53：首领与喷吐者远距离可读性

511 JUnit（`GloomTelegraphTest` 改为核心尺寸 0.45–0.8）、214 GameTest。

## R54：首领逃亡段落与胜利演出

514 JUnit（`EscapeSequenceTest`：前沿匀速扩张且慢于疾跑；需要露天加距离才算逃出；在前沿内算被追上）、215 GameTest（新增 `parity.gloom_heart_escape_then_victory_reward`：开始逃亡后有会话；第一次胜利标为首通、会话结束、多 1 枚生命格；第二次胜利不再给灵格）。

## R55：攀爬 Climb

516 JUnit（`ClimbRulesTest`：需要能力和前进输入，爬升速度低于跳跃，前置冲刺）、216 GameTest。

## R56：水中冲刺与净水呼吸

518 JUnit（`WaterMovementTest`：冲刺沿视线方向，加速有上限）、218 GameTest（新增 `parity.water_dash_breath_and_cooldown`：水中生效、补满氧气、冷却生效、没有能力不生效）。

## R57：钥石与钥石门

219 GameTest（新增 `flow.cave_ruin_keystone_vault_opens_with_two_keystones`：洞穴遗迹顺时针 90° 放置，两个钥石台脚下实心、各只能取一次；门由 4 块组成；密室内有灵格祭台；第一枚钥石后 4 块门都显示 1/2，第二枚后门全部消失，钥石被消耗）。

## R58：真实客户端验证 R52–R57

真实客户端 `verify-r57.py r57-check`（passed）与 `r57-climb2`（攀爬 y205→209.8）。证据在 `evidence/`（不提交）。

## R59：蓄力灵焰 + 腐光水与净化

518 JUnit、223 GameTest（新增：蓄力灵焰命中近处、不伤远处、净化附近腐光水、冷却生效、未学会时拒绝；腐光水造成伤害并减速；溪谷遗迹缺口下至少 20 格腐光水；能力购买测试改为预先拥有其他全部能力，以满足后加能力的前置条件）。

## R60：灵矛 Spirit Spear

224 GameTest（新增 `parity.spirit_spear_pierces_three`：投出成功、冷却生效、对 4 只排队的僵尸只伤 3 只、同一目标不重复、第 3 次穿透后消失、未学会时拒绝）。

## R61：空中威胁：腐光鸦

225 GameTest（新增 `parity.gloom_hawk_telegraphs_then_dives`：是 Enemy；预警 20 tick 内不俯冲；预警结束转入俯冲，速度朝向标记点下方；俯冲接触造成伤害）。

## R62：地图石：遗迹勘察

226 GameTest（新增 `flow.map_stone_surveys_the_cave_ruin`：地图石已放置；勘察到 2 个灵格、2 枚钥石、1 座祭坛；拿走一枚钥石后再勘察只剩 1 枚）。

## R63：Spirit Trials 幽灵回放

528 JUnit（新增 `ChallengeGhostTest` 3 项：采样步长与上限、回放索引与结束、只有更快才替换；新 FX 类型带来的参数化用例）；227 GameTest（`flow.fresh_*_world_to_all_ruins` 断言每处遗迹通关后都留下幽灵轨迹）。

## R64：沙中穿行（安全版 Burrow）

228 GameTest（新增 `flow.spirit_sand_lets_only_burrowers_through`：灵沙和尽头小洞已放置；没有技能的玩家被挡住；灵体形态且会水中冲刺的玩家碰撞为空；僵尸被挡住；灵沙不可破坏；穿行速度和出沙弹出符合常量）。

## R65：R59–R64 真实客户端验证

真实客户端：`scripts/verify-r64.py`（蓄力灵焰净化、灵矛穿透 3 只、腐光鸦预警后俯冲、地图石、灵沙穿行）结果 status ok。

## R66：灵沙小洞奖励

`flow.spirit_sand_lets_only_burrowers_through` 断言小洞里有生命格台；`flow.map_stone_surveys_the_cave_ruin` 改为勘察到 3 个灵格。

## R67：灵弧齐射（Spirit Arc 变体）

533 JUnit（新增 `ArcVolleyTest`：只有潜行、蓄满且能量足够才触发；三个方向都是单位向量，左右对称，夹角 9°）。

## R68：地图碎片

`flow.map_stone_surveys_the_cave_ruin` 新增：地图石激活两次只给 1 片碎片；使用后碎片被消耗，灵光 +5。

## R69：幽灵回放写入存档

533 JUnit（新增 `TrialGhostsTest` 3 项：定点数往返误差、上限与截断数据、保存后再加载）；GameTest 的通关流程仍然断言每处遗迹都留下幽灵。

## R70：第二首领：腐光翼母（空中）

537 JUnit（新增 `GloomMatriarchRulesTest` 4 项）；230 GameTest（新增 `parity.gloom_matriarch_dives_stuns_feathers_and_enrages`：俯冲有预警且对准标记、眩晕时受双倍伤害、眩晕结束后回到待机、羽刃 5 枚、半血进入二阶段并召来两只鸦；`flow.forest_ruin_holds_the_storm_altar`：旋转 90° 后祭坛仍在、只能召唤一只、首次击败奖励只发一次）。

## R71：R66–R70 客户端验证与文档整理

真实客户端：`scripts/verify-r70.py` 结果 status ok（齐射命中 2/3、地图碎片、腐光翼母预警俯冲 4 次、二阶段召来 2 只鸦）；`scripts/check-acceptance-counts.py` 计数一致。

## R72：手感审计

无代码改动。

## R73：运动曲线调校

545 JUnit（新增 `FeelTuningTest` 8 项：3 tick 到全速、2 tick 停下、反向按刹车速率减速、空中保留动量、超速衰减 3–6 tick、松键截断上升、各阶段重力倍率、按住比轻点高且轻点仍能上一格、最大下落速度、冲刺缓出）；230 GameTest。

## ori-r74：动画手感

- `FeelTuningTest`：挤压弹簧会过冲、能回稳；弹簧结果不随帧率变化；朝向沿最短方向转。
- `python3 scripts/feel_anim.py` 输出动画响应曲线。

## ori-r75：镜头与运动特效

- `FeelTuningTest`：FOV 冲击随速度渐增、冲刺时最大；拖尾数量受画质设置约束。

## ori-r76：原创贴图

- `OriginalTexturesTest`：每个模型只引用 orimod 贴图，文件存在且是 16×16。
- `python3 scripts/gen-textures.py` 可重新生成全部贴图。

## ori-r77：实测修正冲刺余速

- `FeelTuningTest`：地面超速在 4–10 tick 内衰减到最高速。
- 真实客户端：`scripts/verify-r76.py`，加上 record_motion 记录跑、跳、冲刺，用 `scripts/plot_motion.py` 画图。

## ori-r78：冲刺、滑翔、Bash 客户端预测

- `scripts/verify-r81.py`：从 record_motion 里取客户端和服务端各自进入动作的第一个 tick。

## ori-r79：第三人称镜头跟随

- `FeelTuningTest`：镜头跟随与帧率无关，前瞻有上限。

## ori-r80：走、跑、疾跑权重混合

- `FeelTuningTest`：权重和为 1，随速度单调变化，步幅连续。

## ori-r81：拖尾改冷色

- `FeelTuningTest`：拖尾头部白、尾部冷蓝，任何位置蓝分量都不低于红分量。

## ori-r82：NeoForge 版本范围放开

- `ModMetadataTest`：NeoForge 范围必须是 `[neo_version,21.2)`，模板不能再写回精确版本，GemRender 和 Flywheel 仍锁定，生成的 mods.toml 是开放区间。
- `scripts/build-release.sh`：jar 里的 mods.toml 必须带这个范围，否则构建失败。

## ori-r83：创造模式标签页与调试命令

- `ContentGameTests`：创造标签页包含全部 orimod 物品；每个物品都有 en_us/zh_cn 名称和物品模型；`/orimod` 在权限 0 时不可见，在权限 2 时 unlock、cells、form、energy、light、giveall、reset 都生效，未知的生物会被拒绝。

## ori-r84：落地伤害与 GemRender 结论

- `ContentGameTests.spirit_form_cancels_fall_damage`：精灵形态取消摔落，人类形态不取消。

## ori-r85：跳跃曲线

- `FeelTuningTest.heldJumpGoesHigherThanTappedAndFallsAreCapped` 按新曲线更新阈值。
- `scripts/feel_curves.py` 输出更新后的 feel-metrics。

## ori-r86：拖尾与同伴光

- `FeelTuningTest.trailIsContinuousAboveThresholdAndRespectsSettings` 更新密度。

## R96：灵灯

2026-10-20 Linux / 用户目录 Temurin 21.0.12.1，`./gradlew build runGameTestServer --console=plain`：
环境补齐后的原基线 **581 JUnit / 236 GameTest** 全过；R96 **591 JUnit / 250 GameTest**，零失败、零跳过。

- `SpiritLanternBlockTest` 新增 3 项：100 tick 时长、7 / 15 亮度映射；新 FX 枚举带来 7 项既有参数化测试（载荷、声音、定义、预算）。FX 编号 16 追加，通道版本为 5，未知编号测试推进到 17。
- `LanternGameTests` 新增 13 项：旧 NBT 无 `Properties` 的默认状态、实际光照映射/形状/无碰撞；非灯和非正时长拒绝；真实计划刻在第 100 tick 熄灭；第 50 tick 续到 150、不影响旁灯、短续时不缩短、计划刻序列化往返；拆掉重放不继承旧到期时间；球形半径/边界/计数；光爆两格内点亮且五格外不亮；蓄力灵焰仅成功释放时点亮；Bash 释放点亮且亮灯仍可选；取消 Bash 不点亮；工作台实际取出两盏灯并消耗全部材料；紫水晶获得时解锁配方书；续亮不重复发 FX 或风铃声音。
- `FxGameTests` 自动增加 `fx.route_lantern_ignite`，确认网络路由。既有 `RuinBoundaryGameTests` 10 项全部通过；没有另跑独立 WorldgenAudit。
- `python3 scripts/art/build_lantern_textures.py --check` 与 `python3 scripts/export-spirit-fx.py --check` 通过，三张纹理可重复生成、17 个效果定义与源一致。物品模型仍继承暗模型。

环境同步新下载并按 `dependencies/lock.json` 校验 SHA-512：`iris-neoforge-1.8.12+mc1.21.1.jar`、`sodium-neoforge-0.6.13+mc1.21.1.jar`、`photon-neoforge-1.21.1-2.2.8-all.jar`、`kilagraph-neoforge-1.21.1-21.1.0.16.jar`、`Mellow v3.4.1a.zip`、`ldlib2-neoforge-1.21.1-2.2.40-all.jar`、`colorwheel-neoforge-1.3.0-beta3+mc1.21.1.jar`；已有 `gemrender-0.1.17.jar` 复验通过。未修改全局 JAVA_HOME/PATH。遗迹检查的 `nbtlib==2.0.4` 安装在用户目录独立 venv，没有新增模组依赖。

失败记录（均未隐去）：

```text
原环境：SpiritGlowIris.java:4: error: package net.irisshaders.iris does not exist
R96 第一次编译：SpiritLanternBlock.java:46: error: cannot find symbol
  symbol:   method getAll()
  location: variable scheduled of type TickContainerAccess<Block>
同轮：SpiritLanternBlock.java:50: error: cannot find symbol
  symbol:   method removeIf((t)->t.typ[...](pos))
  location: variable scheduled of type TickContainerAccess<Block>
R96 第二次编译：LanternGameTests.java:84: error: incompatible types: Tag cannot be converted to ListTag
遗迹重生成检查：AssertionError: Generated assets changed
```

前三轮编译问题分别由标准依赖同步、将公开 tick 接口转换为 Minecraft 1.21.1 实际的 `LevelChunkTicks<Block>`、将保存结果转换为 `ListTag` 解决；没有运行失败的 JUnit/GameTest 用例。

`check-ruins.py` 当前没有只读参数，会无条件调用旧的 `build_ruins.py`。本环境 Python 3.14.7 / nbtlib 2.0.4：三张 NBT 重生成仅 gzip 头第 9 字节 `0a→ff`，其余压缩字节与解压 NBT 均一致；生成器还会移除布局后加的 `cells/updrafts/map_stone/...` 字段，并把三张神龛模型的原创贴图重置为原版贴图。因此该重生成检查**未通过**，并非 R96 改动遗迹。产生的 9 个文件用 `git show HEAD:<path>` 恢复后，另行只读执行其 NBT/布局契约，并对 14 个相关文件与 HEAD 逐字节及 SHA-256 比较，均通过。模板哈希保留：

| 模板 | SHA-256 |
| --- | --- |
| forest_spring.nbt | `e8e425777f9101a79a94ed45a02727de122988a648e561949dfb5158d806eeba` |
| creek_ruins.nbt | `e0d66c48a63357c6c2f6170ceb37cb718612761fab9c698fa3c3419d717562a0` |
| cave_altar.nbt | `1e4bd1ce6ef2edcc597f24374928bd10b3502c2661e2839d911f48a0919f5ac3` |

本机会话原始记录：`/tmp/orimod-r96-sync-dependencies.log`、`/tmp/orimod-r96-baseline.log`（首次环境失败）、`/tmp/orimod-r96-baseline-synced.log`、`/tmp/orimod-r96-build-{1,2,3}.log`、`/tmp/orimod-r96-ruins-restore.json`、`/tmp/orimod-r96-ruins-readonly.json`。临时日志未入库。

未实机验证：灯笼和物品外观、点亮瞬间微粒、呼吸柔光、声音、Iris 组合、GPU 性能；服务端重启/区块卸载只验证了计划刻保存往返，没有完整进程重启实测。配方按 Claude 的修订使用荧石粉及紫水晶解锁，避免灵光自动入账造成材料不可用；详见 ROADMAP_R96。

## R97：同伴「萤」Glimmer

2026-10-20，沿用用户目录 Temurin 21.0.12.1，命令级 `JAVA_HOME` / `PATH`：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" ./gradlew build runGameTestServer --console=plain
python3 scripts/test-summary.py
```

结果：**648 JUnit / 271 GameTest**，零失败、零跳过；较已通过审查的 R96（591 / 250）新增 **57 / 21**。

- `CompanionMotionTest`（12 项）：2 格误差在 1 秒内收敛至 <0.05、超调 <5%；>3 格瞬移并清速度/插值、恰好 3 格仍弹簧跟随；4 tick 攻击后自然返回；10 格提示飞行不被瞬移规则截断、50 tick 后弹簧返回；主人传送打断飞行；提示优先于寻光；30 tick 寻光结束；插值不推进物理；身体朝向、跑步/冲刺/滑翔/游泳锚点；闲置/停战门槛与低血/战斗颜色。
- `CompanionHintsTest`（14 项，含参数化）：已读过滤、优先级、200 tick 边界、时钟倒退保护、严格低于 15% / 消耗 200 tick 内、从未消耗不误报；旧存档/混合奖励键 codec 往返；技能文案对应同一机关条件但已读记录独立；新增滑翔/抓钩两项回归，验证学习后仍可提示且保留 200 tick 间隔；两种语言齐全、中文同伴口吻且 ≤30 个汉字。
- `CompanionHintPayloadTest`（30 项，含参数化）：11 类及 2 种锁定文案往返，通道版本 1；拒绝未知/空/非法变体 ID、超过 32 字符、非 0/1 坐标标志、空间提示缺坐标/个人提示多坐标、截断头/位置、越出 ±30000000 的坐标；复制可变 BlockPos。客户端关闭选项不改变服务端包内容或已读规则。
- `ClientConfigTest` 新增 1 项：旧配置补 `companionHints=true`，保留显式 false，自动设置菜单不隐藏此项；原 unloaded 测试补默认访问验证。
- `CompanionGameTests`（21 项）：20 tick 内灵风口提示并验证载荷坐标/未解锁文案，225 tick 后不重复；人形不发；11 种方块（含两种台、两种祭坛）的半径内/边界/边界外/立方体角落；最近且未读的候选；提示顺序、200 tick 间隔和已解锁文案；真实耗能触发低能量提示；拥有但未装备碎片；旧存档首次记提示及往返；真实命令 dispatcher 的 list 不改状态、reset 保留非提示奖励/灵光/碎片。
- 审查回归：`companion.updraft_locked_then_unlocked_each_once` 与 `companion.grapple_flower_locked_then_unlocked_each_once` 各运行至 455 tick；先收到 locked 版本，超过间隔后不重复，在同一机关旁学会技能，20 tick 内收到正常版本，再过间隔也不重复；两个 reward 键同时保留为 1。该夹具把其他提示都标为已读，同时覆盖“已看完就提前返回”在技能变化后的重新判断。原命令测试增加 locked 键，确认 reset 一并清除。
- 嵌入式玩家连接没有正常网络连接的逐刻调用，定时测试在每个 GameTest tick 调用真实 `ServerPlayer.doTick()`，经 NeoForge 事件总线验证已注册监听器；不是直接调用 `CompanionHints.check()` 替代时序。其余条件矩阵使用同步扫描/判定以隔离干扰。
- 未运行 `check-ruins.py`（按本轮要求）；遗迹 NBT、布局和神龛模型无改动。新网络通道 `orimod:companion_hint` 版本 **1**，原 `spirit_fx` 版本 **5** 未变。没有修改持久化 codec。

首轮失败原文（修复后全部通过；首次全量运行重复了尚未重新编译的两项 JUnit 失败）：

```text
CompanionHintsTest > lowEnergyRequiresRecentSpendAndStrictThreshold(float, float, long, long, boolean) > [2] 15, 100, 200, 0, false FAILED
    org.opentest4j.AssertionFailedError: expected: <false> but was: <true>
CompanionHintsTest > bothLanguagesCoverAllHintsWithShortCompanionVoice() FAILED
    java.nio.file.NoSuchFileException: src/main/resources/assets/orimod/lang/zh_cn.json
companion.scan_storm_altar failed at -11899062, -60, -7089628! Expected Exact radius must include block to be BlockPos{x=-11899055, y=-58, z=-7089623}, but was BlockPos{x=-11899072, y=-58, z=-7089623}
companion.updraft_after_20_ticks_once_even_after_cooldown failed at -11899130, -60, -7089646! Cannot invoke "Object.equals(Object)" because "actual" is null
```

修复原因：`.15f * 100` 略大于 15，阈值改为 double；资源路径使用既有 `orimod.projectDir`；半径夹具改为在目标正上方测试，避免相邻房间同类祭坛更近；嵌入连接补真实 `doTick()`，确保事件实际发生。原始日志在 `/tmp/orimod-r97-test.log`、`/tmp/orimod-r97-full.log`、`/tmp/orimod-r97-full2.log`、`/tmp/orimod-r97-final.log`；审查修复全量复验记录在 `/tmp/orimod-r97-hint-review.log`，本次修复复验无失败；临时文件未入库。

与方案的明确取舍：主动提示/寻光/攻击及返回不执行“距锚点 >3 格归位”，否则飞往 6–12 格目标会被瞬移逻辑覆盖；主人单 tick 位移 >3 格仍立即归位并取消飞行。低血警示覆盖战斗色，战斗花瓣仍加速；花瓣相位连续积分，避免切换战斗时跳位。16 个状态满时为本人让出最远远端玩家的状态，远端继续旧轨道回退。`EnergyState.lastHintSpendAt` 单独记录真实消耗，保留既有回能计时行为。

未实机验证：同伴各状态运动和灵焰光束衔接的观感，花瓣/彗尾/情绪色/亮度脉冲，暗处第一人称地面光池，声音与配置菜单交互，客户端寻光的实际遮挡/拾取表现，多客户端离线/传送/16 人上限与回退，Iris 组合、GPU 性能。地面光池沿用加色贴片，不改变世界方块光照；同伴无碰撞，飞行可能穿过地形。没有开始 R98。

## R98：灵闪

使用用户目录 Temurin 21.0.12.1，最终执行：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" \
  ./gradlew build runGameTestServer --console=plain
python3 scripts/test-summary.py
python3 scripts/export-spirit-fx.py --check
git diff --check
```

结果：**700 JUnit / 302 GameTest**，零失败、零跳过；较 R97（648 / 271）新增 **52 / 31**。18 个效果资源导出一致。未运行 `check-ruins.py`，未修改遗迹 NBT、布局与神龛模型；未执行 git 写操作。

- `FlashTest`（40 项，含参数化）：开启/续用能量门槛、非有限能量、形态/解锁/冷却/蓄力条件、全部移动模式、5/8 tick 保活边界、10/40/20 tick 方块扫描节奏、全部 `MobSpawnType`、16 格距离/维度/实体类型，以及技能价格、位值、前置和耗能/伤害数值。
- `FlashLightTest`（3 项）：5 tick 渐入、8 tick 渐出与插值，渐出中重启不跳变，曝光上限与画质/高级粒子/强度开关。`SpiritKeyDefaultsTest`（2 项）：H 优先、冲突回退、候选全占用时留空。
- 原 FX 参数化测试随 FLASH 扩展 7 项：预算、资源定义、生命周期与协议完整往返；声音清单检查也覆盖新增循环音。`SpiritFxPayloadTest` 验证协议 **6**、未知 kind / phase 与非法一次性 START 解码拒绝。ProgressAction 的 action 扩展不改协议 **1**。
- `flash.*` **29 项 GameTest**：前置/购买、拒绝条件，喷吐者清预警/阻止开火/炫目到期恢复，鸦停止俯冲并向外退避、恢复攻击，双首领伤害且不中断招式，亡灵虚弱与伤害、其他生物不受影响，球形边界与点灯节奏，40/60 tick 净化，真实 `PositionCheck` 总线与原因/距离/维度/租约过滤，FX START/heartbeat/STOP 和重复事件不重复扣能，换形/下线/死亡/换维度/失活输入/移动/菜单/外部伤害中断、环境伤害不中断，四种武器中断，世界卸载隔离，8 tick 到期，50 能量完整耗尽且无回能/20 tick 冷却，其他能力耗尽时的 tick/续包路径。
- 新增蓄力灵焰兼容用例验证“保活过期”和“完全没有保活”都仍可合法 release，费用不变；**`ParityGameTests` 与 `LanternGameTests` 内的原有 ChargeFlame 用例一行未改，全部通过**。另有随枚举增加的 `progression.buy_flash_once` 与 `fx.route_flash` 两项 GameTest。

首轮失败原文（均已修复，未删除或放宽失败断言）：

```text
FxBudgetTest > advancedOffKeepsOnlyEssentialAndNeverZero(SpiritFxKind) > [18] FLASH FAILED
    org.opentest4j.AssertionFailedError: gameplay feedback must stay visible: FLASH ==> expected: <true> but was: <false>
SpiritFxLifecycleTest > persistentKindsAreTheTrailsAndTheBashAimRing() FAILED
    org.opentest4j.AssertionFailedError: expected: <[GLIDE, GRAPPLE_TETHER, BASH_AIM]> but was: <[GLIDE, GRAPPLE_TETHER, BASH_AIM, FLASH]>
flash.interrupt_damage failed at -11051887, -60, -11271851! Flash survived damage
```

修复原因：FLASH 留 1 个必要反馈微粒，装饰关闭时也遵守既有预算契约；持久 FX 清单加入 FLASH；伤害夹具沿用 `DamageGameTests` 的真实玩家 61 次 tick 路径度过登录无敌期，先断言伤害被接受、生命下降，再断言灵闪停止；没有修改伤害打断规则。修复后首轮全量 700 / 300 通过，补其他能力耗尽路径及喷吐者恢复后，最终 700 / 302 通过。日志：`/tmp/orimod-r98-unit.log`、`/tmp/orimod-r98-full.log`、`/tmp/orimod-r98-full-2.log`、`/tmp/orimod-r98-final.log`（临时文件未入库）。

实现取舍与检查边界：

- NeoForge **21.1.248** 依赖源码确认事件为 `MobSpawnEvent.PositionCheck`、结果为 `Result.FAIL`。只检查静态活动玩家集合，不每次遍历所有在线玩家；只抑制 `NATURAL`，`CHUNK_GENERATION` 保留区块初始生态。
- 灯第 1/11/21… tick 扫描，时长 40；水第 40/60/80… tick 扫描，跳过未加载区块。能量最后不足 0.3 时扣清并停止，避免残留不足一 tick 的能量。
- H 注册到 Controls；默认键冲突检查有独立迁移标记，已有安装也会执行。只迁移仍为默认的 Flash，保留其他模组和用户自定义。H 被占时依次尝试 J/K/L/U/Y，全占用则设为未绑定并通知。没有发现需要全局替换 H 的仓库内绑定。
- `SpiritScreen` 已有 `ObjectSelectionList` 滚动、裁剪与滚动条，行高 25、最大视口高 206；现在 14 个技能 + 领刃共 15 行，内容可滚动访问，Flash 排在蓄力灵焰后。此为代码检查，未将实际分辨率/UI 操作记为已验证。
- FLASH 微粒环半径 6，遵守已有资源校验器的半径上限；地面光池与战斗效果半径仍为 7。均衡 2、精细 3 个/ tick，流畅或关闭装饰保留 1 个。曝光遵守既有闪光强度，默认 40 时为最大 3.2%，可调到 8%；流畅画质或高级粒子关闭时为零。光池沿用加色贴片，不改变方块光照。
- 菜单开启通过既有失活移动输入立即通知服务端（含没有服务端容器的 G/Escape），不增加 Flash 停止包；服务器容器也监听 Open。正常松键仍由 8 tick 租约退出。

未实机验证：长按/松键与蓄力灵焰切换手感、按键冲突迁移与重绑、15 行技能面板滚动、第一/第三人称渐变/光池/萤/曝光、循环音、多客户端断线/传送、Iris 和 GPU 性能。没有开始 R99。

本轮改动文件清单（41 个，不含既有未跟踪的 AgentBridge / AGENTS / CLAUDE 文件）：

```text
art/source/effects/spirit/flash.json
docs/ORI_PARITY.md
docs/ROADMAP_R96.md
docs/TESTING.md
docs/WORK_STATUS.md
src/gametest/java/cn/wwl/orimod/gametest/FlashGameTests.java
src/main/java/cn/wwl/orimod/OriMod.java
src/main/java/cn/wwl/orimod/block/GloomWaterBlock.java
src/main/java/cn/wwl/orimod/client/OriModClient.java
src/main/java/cn/wwl/orimod/client/SpiritControls.java
src/main/java/cn/wwl/orimod/client/SpiritKeyDefaults.java
src/main/java/cn/wwl/orimod/client/fx/CompanionMotion.java
src/main/java/cn/wwl/orimod/client/fx/SpiritAura.java
src/main/java/cn/wwl/orimod/client/fx/SpiritFxClient.java
src/main/java/cn/wwl/orimod/client/fx/SpiritScreenFeedback.java
src/main/java/cn/wwl/orimod/combat/ChargeFlame.java
src/main/java/cn/wwl/orimod/combat/Flash.java
src/main/java/cn/wwl/orimod/combat/SpiritCombat.java
src/main/java/cn/wwl/orimod/config/OriClientConfig.java
src/main/java/cn/wwl/orimod/entity/GloomHawk.java
src/main/java/cn/wwl/orimod/entity/GloomSpitter.java
src/main/java/cn/wwl/orimod/fx/FlashLight.java
src/main/java/cn/wwl/orimod/fx/SpiritFxKind.java
src/main/java/cn/wwl/orimod/fx/SpiritFxLifecycle.java
src/main/java/cn/wwl/orimod/fx/SpiritSoundCue.java
src/main/java/cn/wwl/orimod/item/SpiritHammerItem.java
src/main/java/cn/wwl/orimod/item/SpiritSpearItem.java
src/main/java/cn/wwl/orimod/movement/SpiritMovement.java
src/main/java/cn/wwl/orimod/network/SpiritFxPayload.java
src/main/java/cn/wwl/orimod/progression/Ability.java
src/main/java/cn/wwl/orimod/progression/EnergyState.java
src/main/java/cn/wwl/orimod/progression/SpiritProgression.java
src/main/resources/assets/orimod/lang/en_us.json
src/main/resources/assets/orimod/lang/zh_cn.json
src/main/resources/assets/orimod/spirit_fx/flash.json
src/test/java/cn/wwl/orimod/client/SpiritKeyDefaultsTest.java
src/test/java/cn/wwl/orimod/combat/FlashTest.java
src/test/java/cn/wwl/orimod/fx/FlashLightTest.java
src/test/java/cn/wwl/orimod/fx/FxBudgetTest.java
src/test/java/cn/wwl/orimod/fx/SpiritFxLifecycleTest.java
src/test/java/cn/wwl/orimod/fx/SpiritFxPayloadTest.java
```

## R99：新碎片与第四槽

用户目录 Temurin 21.0.12.1 下执行：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" \
  ./gradlew build runGameTestServer --console=plain
python3 scripts/test-summary.py
git diff --check
```

结果：**724 JUnit / 342 GameTest**，零失败、零跳过；较 R98（700 / 302）新增 **24 / 40**。首轮单测、首轮全量、补墙跳预测保护及审查修复后的全量均通过，**无构建或测试失败原文**。日志：`/tmp/orimod-r99-unit.log`、`/tmp/orimod-r99-full.log`、`/tmp/orimod-r99-final.log`、`/tmp/orimod-r99-sticky-review.log`（未入库）。没有运行 `check-ruins.py` 或任何 git 写操作。

- `ShardEffectsTest` 新增 17 项：第四槽 9/10 技能边界与原 1/2/3 槽，19/20 tick 边界的滚动回血限额、满血/堆叠/时钟倒退，荆棘直接近战/投射物/反伤源/有效伤害/距离条件和 9/10 tick 冷却，极限猛击玩家与投射物倍率，吸附下降/悬停/攀爬优先级/上升动量，双语言碎片与槽位文案。
- `SpiritProgressTest` 新增 2 项真实 **NBT** codec 测试：旧 `owned_shards=63`、`equipped_shards=0…63` 的所有组合保留，缺失字段有默认值；`owned_shards=equipped_shards=2047` 连同其他字段完整往返。未知位测试改为 `4095 → 2047`，原来的 255 已全部属于合法碎片位。codec 字段和版本没有改变。
- `ProgressionDefinitionsTest` 补 5 个前置条件；旧碎片位值不变，新位值依次 64 / 128 / 256 / 512 / 1024。
- `shards.*` 新增 35 项 GameTest：直接拾取/磁吸接触/背包自动入账、重复事件不重复收益、灵光容量只结算实际消费、人形/未装备禁用、任务直接加币不触发、生命/能量封顶、跨秒滚动窗口；第四槽门槛与能力减少保留装备/仍可卸下；Bash/Climb 购买前置。
- 战斗用例经真实伤害路径触发 NeoForge `LivingDamageEvent.Post`：近战反伤、同一攻击者冷却及不同攻击者隔离、距离恰好 3 与 3.01，拒绝投射物/间接/THORNS/零伤害/人形/未装备；双真实玩家临时开启 PvP、都装备荆棘，确认只反击一次、不会回弹。夹具先度过 60 tick 登录无敌期，并在结束时还原难度/PvP。
- Bash 用例走实际输入与释放：敌人额外 4 伤害、玩家速度 1.25→1.5，飞行箭 1.6→2.08 并转交归属，插地箭维持原归属与 0.8 推力，取消瞄准无伤害，玩家/盟友/非敌对动物/练习水晶不能成为加伤对象。原 Bash GameTests 未改、全部通过。
- 吸附用例用真实墙块和服务端移动入口：无输入被动下降 −0.042、侧向朝墙悬停 0、按前进攀爬 +0.14、未装备保持 −0.12、潜行脱墙、不耗能、零/正速度保持墙面状态、无输入仍能蹬墙跳；预测墙跳的 8 tick 脱墙期内，共享速度入口不让旧墙滑租约把跳跃覆盖为攀爬或悬停。
- 审查新增 1 项 JUnit：无输入/沿墙输入接受，−0.2 边界及两侧、明确背离的纯函数判定；新增 `sticky_wall_away` / `sticky_wall_away_diagonal` 两项 GameTest：先进入 WALL_SLIDE、保留上一面墙，再按背离方向或点积约 −0.243 的斜向输入，确认退出到 IDLE、下降速度不再受限。斜向用例同时验证 +0.15 偏好不能救回被拒绝的墙。审查前为 723 / 340，修复后为 724 / 342。
- 原碎片购买参数化 GameTest 自动新增 5 项。其共用夹具原来仅授予旧 6 个能力（63），为覆盖 STICKY 的 CLIMB 前置，改为授予当前所有 `Ability` 位；购买/重复扣费断言不变。其他旧 GameTest 文件未修改。

实现取舍与边界：

- 代码核对确认 `pullLight` 本身只施加朝玩家的速度，没有第二次入账。它触发的实际拾取和背包自动兑换统一调用 `collectLight`，先消耗实际获准入账数量，再触发汲取。任务/命令的 `grantLight` 保持单纯货币操作；不改掉落数量、磁吸范围和灵光上限。限速状态跨换形/卸装保留，防止反复装备绕过窗口，随会话结束消失。
- 荆棘使用 NeoForge 21.1.248 的伤害 **Post** 事件和 `getNewDamage()>0`；判定 `directEntity == entity`、LivingEntity 攻击者、非 Projectile/非投射物伤害类型、距离 ≤3。按用户允许的方案使用原版 **THORNS** 伤害源，明确过滤该类型，并在调用 `hurt` 前登记同目标冷却，不会发生双荆棘递归；中英文与 ROADMAP 同步描述为荆棘伤害。
- 极限猛击玩家加速在 `SpiritMovement.releaseBash`；敌人加伤与投射物反弹在 `BashSession.release`。投射物倍率作用于原反弹速度（原上限 4，装备后上限 5.2）；光爆保留原来的直接引爆分支，没有改成反弹。
- 原服务端墙滑主要续租、原客户端实际限速；现在共用 `slidingWall` / `wallVelocity`。装备 Sticky 才允许无输入接触并慢滑；普通墙滑的输入条件不变，Climb 仍优先。`SpiritJumpPrediction` / `SpiritActionPrediction` 也使用同一接触规则；没有增加网络字段或协议版本。
- 审查确认：服务端此前已传入 `s.wallDirection` 并加 0.15，客户端此前传 −1。客户端改传 `state.wallDirection` 的规则与服务端相同，但当前客户端该字段没有同步或本地更新、通常仍为默认 −1，因此尚未实际启用该偏好，也不应称为完整解决状态不一致。按审查要求，本次仅说明，未增加客户端状态维护或同步。Sticky 的退出阈值检查原始点积，独立于该偏好。
- 碎片页为 **11 碎片 + 能量升级，共 12 行**，沿用 `ObjectSelectionList` 的滚动/裁剪；详情可滚动，追加“学会 10 个技能后解锁第 4 槽（当前/10）”或已解锁提示。状态栏沿用已装备数/可用槽数。此为代码检查，未把真实界面操作记为通过。

未实机验证：不同分辨率/GUI 缩放下的碎片列表与文案、客户端延迟下的吸附/攀爬/墙跳衔接、极限猛击的速度观感、完整旧世界升级重启（本轮验证了 NBT codec 与真实服务端逻辑）。停止在 R99，等待审查。

改动文件清单（23 个；不含既有 AgentBridge / AGENTS / CLAUDE 未跟踪文件）：

```text
docs/ORI_PARITY.md
docs/ROADMAP_R96.md
docs/TESTING.md
docs/WORK_STATUS.md
src/gametest/java/cn/wwl/orimod/gametest/ProgressionGameTests.java
src/gametest/java/cn/wwl/orimod/gametest/ShardGameTests.java
src/main/java/cn/wwl/orimod/client/SpiritActionPrediction.java
src/main/java/cn/wwl/orimod/client/SpiritControls.java
src/main/java/cn/wwl/orimod/client/SpiritJumpPrediction.java
src/main/java/cn/wwl/orimod/client/SpiritScreen.java
src/main/java/cn/wwl/orimod/movement/BashSession.java
src/main/java/cn/wwl/orimod/movement/MovementRules.java
src/main/java/cn/wwl/orimod/movement/SpiritMovement.java
src/main/java/cn/wwl/orimod/progression/EnergyState.java
src/main/java/cn/wwl/orimod/progression/Shard.java
src/main/java/cn/wwl/orimod/progression/ShardEffects.java
src/main/java/cn/wwl/orimod/progression/SpiritProgress.java
src/main/java/cn/wwl/orimod/progression/SpiritProgression.java
src/main/resources/assets/orimod/lang/en_us.json
src/main/resources/assets/orimod/lang/zh_cn.json
src/test/java/cn/wwl/orimod/progression/ProgressionDefinitionsTest.java
src/test/java/cn/wwl/orimod/progression/ShardEffectsTest.java
src/test/java/cn/wwl/orimod/progression/SpiritProgressTest.java
```

## R96–R99：客户端验收修复

2026-10-20，用户目录 JDK 21 执行 `./gradlew build runGameTestServer --console=plain`，结果为 **734 JUnit / 344 GameTest，零失败、零跳过**（原 724 / 342，新增 10 / 2）。另执行 `compileDevtoolsJava` 和 `python3 scripts/art/build_lantern_textures.py --check`，均通过。构建日志、最终 XML 和修复前失败记录保存在 `evidence/client-fixes-build-v001/`；没有运行 `check-ruins.py` 或任何 git 写操作。

崩溃回归 `flash.dazzle_running_ranged_goal_survives_entity_ticks` 使用真实连接的生存玩家，启动喷吐者原有 RangedAttackGoal 并断言包装目标处于 running，随后炫目，连续执行六次真实实体 `tick()`。夹具模拟 ServerLevel 的 tickCount 递增，并让第一次进入 Mob 的仅运行目标分支，覆盖 GoalSelector 下一次清理之前的窗口。修复前 344 项 GameTest 中仅此项失败，原文：

```text
flash.dazzle_running_ranged_goal_survives_entity_ticks
(1423742, -60, -11994830) Cannot invoke "net.minecraft.world.entity.LivingEntity.getX()" because "this.target" is null
```

修复移除 `dazzle()` 对内部 `rangedGoal.stop()` 的直接调用，由 `canContinueToUse()` 返回 false 让 GoalSelector 正常停止包装目标；炫目期间目标 `tick()` 直接返回，导航仍立即停止。检查其他实体未发现同类直接停止内部目标的调用。回归最终保持相同的连续 tick 路径并通过。新命令回归 `unlock_all_includes_every_shard_and_preserves_progress` 从旧 `owned=63` 出发，检查全部 Shard 位都解锁，且灵光 17、装备 3 保留。

同伴运动新增 6 项参数化 JUnit：锚点以 4 / 10 m/s 移动 3 秒，分别从普通状态、hint 返回、seek 返回开始，后两者先确认 `returning=true`；最后一秒每 tick 锚点距离均 <2.5 格。原弹簧实现即通过此距离要求，所以未调 ω=9 或改锚点。初版额外要求末端瞬时速度严格等于锚点速度 ±0.05，导致六项失败（4 m/s 测得 3.93359、10 m/s 测得 9.83398）；离散锚点每 tick 跳一步，子步结束瞬时速度与平均速度不完全相等，已去掉这个无关断言，保留逐 tick 距离及有限值检查。对应初版 XML 为 `before-companion.xml`。

真实客户端探测使用专用 `orimod_dev_r96accept` 世界、Mesa llvmpipe、关闭 Iris 光影，截图已查看：

| 证据目录 | 结果 |
| --- | --- |
| `evidence/client-fixes-follow-probe-v001/` | 站立、移动、按疾跑键采样；实际移动速度约 6.4 m/s，稳定锚点距离约 1.15–1.21 格，returning/seek/dart 正常。此客户端采样不等于 10 m/s 实测；10 m/s 由上述 JUnit 覆盖。 |
| `evidence/client-fixes-light-probe-v001/` | 灵灯缓存 3、实际提交绘制 3，排除扫描遗漏。Flash 第三人称旧 bloom 出现长横线，而同场第一人称地面光池无此横线。 |
| `evidence/client-fixes-confirm-v001/` | `moving-source.png` 的后方短彗尾亮点与 `SpiritAura.companion` 的投影 (483, 494) 对应；查询和截图略有帧差。该次插值中心距主人脚底约 2.15 格，tick 状态距锚点约 1.15 格。`flash-third-after.png` 横线消失；`lantern-lit-unlit.png` 中央白芯与两侧冷蓝熄灭灯芯可区分，柔光可见。 |

对“远处光点”的结论：它确实是「萤」，没有证据支持十几格的世界坐标滞后，也没有在该帧被 Ori 的 bloom 盖住；更低处的长粒子带是 `MotionFeel.emit` 的身体尾迹。新增 devtools 运动状态与实际绘制中心/来源/屏幕坐标，便于换镜头复查。没有把未复现的运动故障记为已修复。

光效修改：

- 灯晕从半径 0.9 / alpha 0.25 改为内层 1.4 / 0.4、外层 2.2 / 0.12，仍共用原批次与调色板预筛。熄灭灯芯基色 #5F86B8，点亮仍近白，由生成脚本重建；只有熄灭核心 PNG 的字节变化。最终混合亮灭截图后的查询可能碰上旧 Flash 计划刻使灯熄灭，故该次 `cached=1/rendered=0` 不作持续点亮证据；缓存与绘制均为 3 的独立探测已确认正常路径。
- Flash 硬边来自放大后的朝向相机身体 bloom 平面被地面深度截断。抽取已有向下射线求地表，让这些 billboard 在地面以上 0.45 格内按 smoothstep 渐隐；四个高度带插值，保持原径向纹理 UV。半径 7 的地面光池仍走原径向纹理绘制。新增 4 项 `SoftGlowClipTest` 覆盖大半径的地面透明/UV、旋转镜头、完全埋入与完全高于渐隐区。

验证范围：本轮确认上述同机截图差异与数值，不代表硬件 GPU、开启 Iris 光影、所有地形和镜头角度的验收。没有运行 Claude 正在编写的 `verify-r96-r99.py`，该文件未修改。客户端已正常关闭，留待 Claude 综合复测和审查。

改动文件清单（16 个；不含既有 AgentBridge / AGENTS / CLAUDE 文件、Claude 的验收脚本或忽略的证据目录）：

```text
docs/DEVELOPMENT.md
docs/TESTING.md
docs/WORK_STATUS.md
scripts/art/build_lantern_textures.py
src/devtools/java/cn/wwl/orimod/devtools/DevelopmentBridge.java
src/gametest/java/cn/wwl/orimod/gametest/FlashGameTests.java
src/gametest/java/cn/wwl/orimod/gametest/ProgressionGameTests.java
src/main/java/cn/wwl/orimod/client/fx/CompanionMotion.java
src/main/java/cn/wwl/orimod/client/fx/SpiritAura.java
src/main/java/cn/wwl/orimod/client/render/SoftGlowClip.java
src/main/java/cn/wwl/orimod/client/render/SpiritEnergy.java
src/main/java/cn/wwl/orimod/command/OriCommands.java
src/main/java/cn/wwl/orimod/entity/GloomSpitter.java
src/main/resources/assets/orimod/textures/block/spirit_lantern_core.png
src/test/java/cn/wwl/orimod/client/fx/CompanionMotionTest.java
src/test/java/cn/wwl/orimod/client/render/SoftGlowClipTest.java
```

## R98：低帧率灵闪租约修复

Claude 的 `evidence/r98-flash-lease-probe-lowfps-v001/` 在客户端 fpsLimit=4 时记录能量 100→68.4、近处腐光水未净化；`r98-flash-lease-probe-v001/` 的较高帧率对照可以净化。每五个客户端 tick 的保活会随低帧率成批到达，旧服务端八 tick 租约可能在相邻包之间超时，使下一包重新开始计龄。

修改为 20 tick 兜底租约，客户端松手或输入失效的第一个可处理 tick 发送一次 `flash_stop`，菜单打开时也发送；不等待下一个五 tick 保活时点。服务端 action 在资格检查之前处理，只能停止发送者自己的 Flash，并立即清空租约与起始时间、同步 FX STOP。实际输入线程卡住期间无法提前感知松手，兜底仍以服务端 tick 为界。没有添加停止后的恢复宽限：超过 20 tick 的输入中断仍结束本次施放，明确松手后再次按下不会继承旧净化进度。

新增 GameTest：

- `flash.irregular_ten_to_twelve_tick_keepalives_preserve_age_and_cleanse`：真实服务器时钟推进 60 tick，在 0、10、22、33、43、55 tick 经正式请求入口续租；每 tick 检查 Flash 存活、`flashActiveSince` 等于首次值、没有发出本玩家的 FX STOP/STOP_ALL，第 38 tick 腐光水仍在，第 60 tick 已净化。
- `flash.explicit_stop_is_immediate_idempotent_and_next_hold_starts_fresh`：租约仍有效时连续发送两次 `flash_stop`，立即清空活动状态/租约/计龄，恰好一个 FX STOP，随后的 tick 不再扣能；下一 tick 重新开启使用新的起始时间。

原 `lease_expires_at_eight_without_release_packet` 更名并改验第 19 tick 仍有效、第 20 tick 失效；JUnit 同步覆盖第 12 tick 延迟仍有效、19/20 边界与续租后的新边界。修改原因是租约现在只作丢失输入兜底，正常松手走明确停止包。蓄力灵焰改用独立 `ChargeFlame.INPUT_LEASE_TICKS=8`，并断言原值不变；旧 `ChargeFlameGameTests` 未修改，release 不依赖互斥保活。

全量验证命令（Claude 确认客户端正常退出后执行）：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" \
  ./gradlew build runGameTestServer compileDevtoolsJava --console=plain
python3 scripts/test-summary.py
git diff --check
```

首轮 734 项 JUnit 通过，346 项 GameTest 中新增停止测试有一项夹具失败，原文：

```text
flash.explicit_stop_is_immediate_idempotent_and_next_hold_starts_fresh
Expected Stop edge must end the loop once to be 1, but was 0
```

服务端立即停用、清空计龄和停止扣能的断言已通过；原因是原版 `MinecraftServer.tickChildren` 在世界 tick 内对既有连接 `suspendFlushing()`，到 tick 末尾才 `resumeFlushing()`。将包计数检查移到下一 tick、正式队列已 flush 后，仍断言恰好一条 STOP；不改服务端发送路径。失败日志与 XML 保存在 `evidence/flash-lease-build-v001/first-full.log` / `first-gametest.xml`。

最终全量 **734 JUnit / 346 GameTest，零失败、零跳过**；`build`、`compileDevtoolsJava` 和 `git diff --check` 通过。日志和最终 XML 位于 `evidence/flash-lease-build-v001/`（另有 `/tmp/orimod-flash-lease-final.log`）。本轮未启动客户端，未执行 git 写操作；低帧率实际按键松手、净化和音画连续性留待 Claude 复测。

本次增量涉及 9 个文件：`Flash.java`、`ChargeFlame.java`、`SpiritControls.java`、`SpiritProgression.java`、`FlashTest.java`、`FlashGameTests.java`、`ROADMAP_R96.md`、`TESTING.md`、`WORK_STATUS.md`。没有新增网络字段或效果类型，协议版本不变。

## R100：灵树

2026-10-20，R100 前基线为 734 JUnit / 346 GameTest。用户已确认客户端关闭，本轮使用用户目录 JDK 21：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" \
  ./gradlew build runGameTestServer compileDevtoolsJava --console=plain
python3 scripts/test-summary.py
python3 scripts/art/build_spirit_tree_textures.py --check
python3 scripts/export-spirit-fx.py --check
git diff --check
```

最终 **755 JUnit / 361 GameTest，零失败、零跳过**，较基线新增 **21 / 15**；`build`、`compileDevtoolsJava`、五张纹理复现检查、19 项 FX 导出检查和 `git diff --check` 通过。首轮全量 754 / 359 全过，补遗迹留距和真实斜坡后 755 / 360 全过，交付轮另加树冠空腔障碍回归。日志与最终 XML 保存在 `evidence/r100-build-v001/`（不入库），原日志为 `/tmp/orimod-r100-{unit,full,final,delivery}.log`。没有启动客户端、没有执行 git 写操作或运行 `check-ruins.py`，遗迹资源未改。

测试覆盖：

- `SpiritTreeTest`：完整 14 技能领取顺序；全部 16,384 种现有技能掩码下的前置、最低价与并列枚举优先级；未知位、全满与 100 灵光常量；新树心记录和旧奖励/碎片字段一起 codec 往返。
- `SpiritTreeFeatureTest`：25 点地形采样的 3/4 格边界、湿地拒绝、64 种可替换条件组合；64 个位置的确定性/树形差异、主干高度/枝根数/水平范围、冠叶与藤、3×3×3 入口；木材面相连和藤的上方支撑；worldgen JSON 频率/群系/阶段；灵树 14 格遗迹保护边界且原版树 12 格规则不变。
- 新增 14 项 `spirit_tree.*` GameTest：通过注册的 configured feature 放置平地大树；3 格高差下根部和入口接地；冠叶位置石块、冠内空腔石块、湿地、4 格高差、入口箱子均拒绝且整个 31×35×31 场地逐块不变；正式右键免费学习且同树不可重复、未觉醒/人形拒绝、全技能给 100 灵光、不同树各一次/不同玩家可领取已开花树；剪刀掉叶/无工具不掉、木头掉落、树心无掉落与方块物品存在；23 格树心提示实际发包、25 格不提示、已读不再发送。
- 祝福回归核对新 FX 包数量、来源玩家与树心起点；已有 FX 路由参数化新增 `TREE_BLESSING`。FX 协议 **7**，新增编号 **18**；未知编号从 18 顺延到 19，拒绝将祝福解码成持续 START。提示协议 **2**，新 ID 正常往返且缺少坐标时拒绝；旧未知 ID / 坐标范围 / 截断 / 可选标志拒绝测试继续通过。中英文提示均覆盖并保持同伴口吻、中文不超过 30 字。

首轮唯一失败原文：

```text
OriginalTexturesTest > modelsUseOnlyOriginalTexturesThatExist() FAILED
.../src/main/resources/assets/orimod/textures/block/spirit_log_top.png ==> expected: <16> but was: <32>
754 tests completed, 1 failed
```

原因是首版纹理生成器输出 32×32，不符合 R76 的全局原创纹理契约。改为在 32 单位绘图网格上采样输出 16×16，重新生成五张 PNG；没有修改或放宽原测试。最终无构建/GameTest 失败。

实现取舍与验收边界：

- 方案的“中心 ±14 格”解释为水平方向，才能同时满足主干 16–22 格高度；顶部椭球叶冠可高于主干，最大计划高度 <30 格。位置种子生成弯曲干、板根、分层斜枝、多团空心缺口冠及悬藤，完整描述在 WORK_STATUS 顶部。预检包含树冠完整椭球范围（即使实际树叶留了孔洞），防止硬障碍藏在空腔中。
- 中心 5×5 的最高干燥实心地面决定基座 Y；在允许高差内以木根补足地面到基座的落差，入口也有稳固落脚点。脚下日志/叶片/方块实体不作为自然地基；检查完整后才写入。天然灵树显式复用 `RuinVegetationGuard`，因为原版 TreeFeature 的 mixin 不会自动覆盖自定义 Feature。
- `rarity_filter.chance=220` 是合格群系每区块的尝试率。220×16×16=56,320 平方格/次，平方根约 237 格是名义网格间距，不是平均最近邻距离，也不是成功成树率；预检与遗迹避让会进一步降低实际密度。森林/针叶林标签加平原、向日葵平原、草甸、樱花林；不含繁茂洞穴。
- 叶片为不衰减的普通 Block，使用 LEAVES 标签、剪刀掉落表和带透明孔隙的 cutout_mipped 叶面；不引入树叶随机衰减。BLOOMED 不改变已经为 15 的世界光照，改用更白的核心纹理与花瓣状微粒。
- 新 hint ID 虽未改变载荷字段，旧客户端严格校验 ID，会拒绝 `spirit_tree`，因此提示通道也升到 2。提示未读时将扫描范围扩到 24 格，继续只访问已加载区块并按区段调色板筛选；其他提示各自半径和顺序保持不变。已读后退回原 10 格范围。
- 奖励保存在已有 rewards，不改存档字段；满技能灵光赠送遵守原货币上限。所有方块有创造物品；树心按坐标识别站点，适合命令/创造测试。地图石/地图碎片联动与 `/locate` 本轮未做，后续可选。

验收命令：在干燥开阔平地 `/place feature orimod:spirit_tree ~ ~ ~`；中心北侧 2 格处是树心。`/ori awaken`、`/ori form on` 后空手右键，`/ori status` / devtools `progress_state` 核对能力与成长，`inspect_block` 核对 `bloomed`；`/ori hints reset` 重测提示。多棵中心至少隔 32 格，固定平台示例见 DEVELOPMENT。没有添加绕过检查的 `/ori tree place`，现有原版命令已能通过平地放置。

**未实机验证：** 树形轮廓、板根/斜枝/多冠的观感、叶片透明与原版浆果藤搭配、昼夜发光纹路、祝福光柱/汇聚/声音、自然新世界的成功率与地形适配、Iris/硬件 GPU。自动测试不替代这些视觉与自然生成验收。R100 完成后停止，不进入 R101/R102。

改动文件清单（58 个；不含既有 AgentBridge 文件与忽略的测试证据）：

```text
art/source/effects/spirit/tree_blessing.json
docs/DEVELOPMENT.md
docs/ORI_PARITY.md
docs/TESTING.md
docs/WORK_STATUS.md
scripts/art/build_spirit_tree_textures.py
scripts/export-spirit-fx.py
src/gametest/java/cn/wwl/orimod/gametest/SpiritTreeGameTests.java
src/main/java/cn/wwl/orimod/block/OriBlocks.java
src/main/java/cn/wwl/orimod/block/SpiritTreeHeartBlock.java
src/main/java/cn/wwl/orimod/client/fx/SpiritFxClient.java
src/main/java/cn/wwl/orimod/client/fx/SpiritParticle.java
src/main/java/cn/wwl/orimod/fx/SpiritFxDefinition.java
src/main/java/cn/wwl/orimod/fx/SpiritFxKind.java
src/main/java/cn/wwl/orimod/fx/SpiritSoundCue.java
src/main/java/cn/wwl/orimod/network/CompanionHintPayload.java
src/main/java/cn/wwl/orimod/network/SpiritFxPayload.java
src/main/java/cn/wwl/orimod/progression/CompanionHints.java
src/main/java/cn/wwl/orimod/progression/SpiritTree.java
src/main/java/cn/wwl/orimod/world/OriWorldgen.java
src/main/java/cn/wwl/orimod/world/RuinVegetationGuard.java
src/main/java/cn/wwl/orimod/world/SpiritTreeFeature.java
src/main/java/cn/wwl/orimod/world/SpiritTreeShape.java
src/main/resources/assets/orimod/blockstates/spirit_leaves.json
src/main/resources/assets/orimod/blockstates/spirit_log.json
src/main/resources/assets/orimod/blockstates/spirit_tree_heart.json
src/main/resources/assets/orimod/lang/en_us.json
src/main/resources/assets/orimod/lang/zh_cn.json
src/main/resources/assets/orimod/models/block/spirit_leaves.json
src/main/resources/assets/orimod/models/block/spirit_log.json
src/main/resources/assets/orimod/models/block/spirit_tree_heart.json
src/main/resources/assets/orimod/models/block/spirit_tree_heart_bloomed.json
src/main/resources/assets/orimod/models/item/spirit_leaves.json
src/main/resources/assets/orimod/models/item/spirit_log.json
src/main/resources/assets/orimod/models/item/spirit_tree_heart.json
src/main/resources/assets/orimod/spirit_fx/tree_blessing.json
src/main/resources/assets/orimod/textures/block/spirit_leaves.png
src/main/resources/assets/orimod/textures/block/spirit_log.png
src/main/resources/assets/orimod/textures/block/spirit_log_top.png
src/main/resources/assets/orimod/textures/block/spirit_tree_heart.png
src/main/resources/assets/orimod/textures/block/spirit_tree_heart_bloomed.png
src/main/resources/data/minecraft/tags/block/leaves.json
src/main/resources/data/minecraft/tags/block/logs.json
src/main/resources/data/minecraft/tags/block/logs_that_burn.json
src/main/resources/data/minecraft/tags/block/mineable/axe.json
src/main/resources/data/minecraft/tags/block/mineable/hoe.json
src/main/resources/data/orimod/loot_table/blocks/spirit_leaves.json
src/main/resources/data/orimod/loot_table/blocks/spirit_log.json
src/main/resources/data/orimod/neoforge/biome_modifier/spirit_tree.json
src/main/resources/data/orimod/tags/worldgen/biome/has_spirit_tree.json
src/main/resources/data/orimod/worldgen/configured_feature/spirit_tree.json
src/main/resources/data/orimod/worldgen/placed_feature/spirit_tree.json
src/test/java/cn/wwl/orimod/fx/CompanionHintPayloadTest.java
src/test/java/cn/wwl/orimod/fx/FxBudgetTest.java
src/test/java/cn/wwl/orimod/fx/SpiritFxPayloadTest.java
src/test/java/cn/wwl/orimod/progression/CompanionHintsTest.java
src/test/java/cn/wwl/orimod/progression/SpiritTreeTest.java
src/test/java/cn/wwl/orimod/world/SpiritTreeFeatureTest.java
```


## R100：外观返工

2026-10-20。Claude 已确认首版功能 5/5 通过，截图暴露柱状主干、伞形冠、暖色浆果藤和夜间自发光不足。本轮在客户端关闭后执行；首版基线 755 JUnit / 361 GameTest。

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" \
  ./gradlew build runGameTestServer compileDevtoolsJava --console=plain
python3 scripts/test-summary.py
python3 scripts/art/build_spirit_tree_textures.py --check
git diff --check
```

最终 `build runGameTestServer compileDevtoolsJava` **通过：759 JUnit / 369 GameTest，零失败、零跳过**。新增 4 JUnit / 8 GameTest，`spirit_tree.*` 现为 22 项。七张纹理复现检查通过。本轮不启动客户端、不运行 check-ruins.py、不做 git 写操作；客户端树形、昼夜与 Sodium/Iris 实际渲染留待审查统计后复验。

新增与更新覆盖：

- 64 个位置种子的冠叶跨层≥6、主干底截面≥中段×2、最大水平冠半径在 8–11 之间、较大冠幅/树高≥0.9、冠中心高于外缘；整棵非空气方块六邻接连通，所有藤格不在 crownSpace 中且上方有支撑；已有 ±14、3×3×3 壁龛与树心入口检查保持。
- 灵叶、藤身/藤尖的渲染层及透明孔隙/冷色纹理检查；灵叶的 minecraft:leaves 标签。真实 Feature 测试改为确认自定义灵藤可存活、无 BERRIES、无原版洞穴藤。
- GameTest：悬挂在灵叶下存活、藤下接藤、TIP 随增减更新、移除上方支撑整段脱落、不随机生长；剪刀/精准采集掉自身、其他工具不掉；叶片/藤两状态/树心两状态的 emissiveRendering 为 true、树皮为 false。
- GameTest：负坐标跨区块的三维最近距离选择；半径 1、1024 的恰好边界与越界 0.01 格；非法半径 -1/0/1025/Integer.MAX_VALUE 拒绝；远处未加载区块扫描前后 getChunkNow 仍为空、加载区块总数不变；命令默认/最小/最大半径成功、找到/未找到翻译反馈、距离、入口传送 y 与参数边界。
- `SpiritTreeShapeReportTest` 直接调用生产 Java 函数生成五个固定位置种子的完整逐层面积和侧视图，产物 `build/reports/spirit-tree-shapes.md`；证据副本 `evidence/r100-rework-v001/shapes.md`。主干面积只算最初弯干，排除板根、斜枝和替换为树心的一格；全树面积包含木、叶、藤、树心，排除壁龛 AIR。每棵高度为最高占用 y+1，侧视沿 Z 投影到 XY，不是透视图。

中间失败原文（最终均已修复）：

```text
Tree is too tall for its crown: 32 ==> expected: <true> but was: <false>
Disconnected shape at seed 0: [BlockPos{x=8, y=14, z=-2}, BlockPos{x=8, y=12, z=-2}]
Disconnected wood: [BlockPos{x=0, y=0, z=-6}]
Dome centre does not rise above rim: 63 ==> expected: <true> but was: <false>
Tree is too tall for its crown: 18 ==> expected: <true> but was: <false>
Tree is too tall for its crown: 32 width 19/19 height 22 ==> expected: <true> but was: <false>
class net.minecraft.network.chat.contents.PlainTextContents$1 cannot be cast to class net.minecraft.network.chat.contents.TranslatableContents
```

前三类几何原因分别是外围冠太窄/随机缺口削掉宽肩、疏叶产生孤点、加长板根被入口切断。修正为半径 5 的外围冠、保留冠中部宽肩并限制下部缺口、外冠与肩冠低于中心、六邻接清理孤立块；所有形状断言未放宽。命令失败是测试夹具直接把 sendFailure 返回的红色空组件包装强转为翻译内容，改读其第一个子组件；命令行为不改。

兼容性核对：锁定的 Sodium 0.6.13 在 [LightDataAccess](https://github.com/CaffeineMC/sodium/blob/mc1.21.1-0.6.13/common/src/main/java/net/caffeinemc/mods/sodium/client/model/light/data/LightDataAccess.java) 读取 BlockState.emissiveRendering，并在满亮度分支返回 LightTexture.FULL_BRIGHT；本地 jar 的 FlatLightPipeline 字节码也确认处理该标志。这条光照路径与 cutout/cutout_mipped 的透明层选择独立。原版 LevelRenderer.getLightColor 同样读取 emissiveRendering；hasPostProcess 的调用者是 WorldGenRegion.setBlock，用于标记世界生成后的邻接处理，不是 Bloom 开关。这里只确认代码路径与资源契约，未声称 GPU/Sodium/Iris 实机验收通过。

取舍：悬藤固定长度、不生长、无浆果，以保持树形和全树冷色；新增资源不迁移旧世界里的原版浆果藤。形状整体改为宽冠锥形干，位置种子和 ±14 写入约束保持；不同种子的叶量和缺口会变。树心赠送规则、站点键、整棵预检、自然生成尝试率 1/220 和协议均保持。寻树仅针对已加载区块中的所有树心，不区分人工放置与自然生成，不参与地图石指引。

五个固定位置种子的统计（含坐标所派生的实际 PRNG seed、每个 y 层主干/全树截面积与五张侧视投影）见 `build/reports/spirit-tree-shapes.md` / `evidence/r100-rework-v001/shapes.md`。下面冠幅为占用方块列数，冠底/顶 y 相对基座；高度为最高占用层+1。

| 基座坐标 | 树高 / 主干高 | 冠幅 X×Z | 冠底 / 顶 y | 木 / 叶 / 藤 |
| --- | ---: | ---: | ---: | ---: |
| (0, 70, 0) | 20 / 17 | 21×23 | 9 / 19 | 374 / 1302 / 72 |
| (3000, 120, 3000) | 21 / 17 | 21×21 | 9 / 20 | 335 / 1112 / 70 |
| (2986, 120, 3000) | 21 / 17 | 21×21 | 9 / 20 | 340 / 1160 / 69 |
| (-1015, 70, -115) | 20 / 16 | 21×21 | 9 / 19 | 325 / 1124 / 74 |
| (1200, 80, -300) | 22 / 18 | 21×20 | 11 / 21 | 334 / 1061 / 66 |

所有样本主干截面积：y0 为 24（另有树心 1 格），y1–2 为 25，y3–4 为 21，y5–9 为 9，y10 至主干顶为 4，其上为 0；全树面积逐层不同，见完整统计。首棵侧视图（沿 Z 压缩；# 木、* 叶、| 藤、H 树心，木优先于叶显示）：

```text
19             ******
18         *  *********
17      ****************
16     *********###********
15     *******#####*#*******
14     ********######*******
13     ****##**#####********
12     *****##*#####***#****
11      *****#######*###****
10       | *|#########*****
09       | |||########|*  |
08       | ||| ###### ||  |
07       | | | |####  ||  |
06       | |    ###    |  |
05              ###    |  |
04             #####
03             #####
02            #######
01           ########
00          #####H#####
   -14           0            14
```


相对首版，本次新增/修改的文件：

```text
docs/WORK_STATUS.md
docs/TESTING.md
docs/DEVELOPMENT.md
docs/ORI_PARITY.md
scripts/art/build_spirit_tree_textures.py
src/main/java/cn/wwl/orimod/block/OriBlocks.java
src/main/java/cn/wwl/orimod/block/SpiritVineBlock.java
src/main/java/cn/wwl/orimod/command/OriCommands.java
src/main/java/cn/wwl/orimod/world/SpiritTreeShape.java
src/main/java/cn/wwl/orimod/world/SpiritTreeFeature.java
src/main/java/cn/wwl/orimod/world/SpiritTreeSearch.java
src/main/resources/assets/orimod/blockstates/spirit_vine.json
src/main/resources/assets/orimod/models/block/spirit_vine.json
src/main/resources/assets/orimod/models/block/spirit_vine_tip.json
src/main/resources/assets/orimod/models/item/spirit_vine.json
src/main/resources/assets/orimod/textures/block/spirit_vine.png
src/main/resources/assets/orimod/textures/block/spirit_vine_tip.png
src/main/resources/assets/orimod/lang/en_us.json
src/main/resources/assets/orimod/lang/zh_cn.json
src/main/resources/data/orimod/loot_table/blocks/spirit_vine.json
src/test/java/cn/wwl/orimod/world/SpiritTreeFeatureTest.java
src/test/java/cn/wwl/orimod/world/SpiritTreeShapeReportTest.java
src/gametest/java/cn/wwl/orimod/gametest/SpiritTreeGameTests.java
```

## R100：冠底回归、只读诊断与提前生成

2026-10-20。客户端复验已通过自发光、cutout、冷色固定悬藤、主干锥度及 nearest 命令；外围冠底平整的问题另行修订。当前最终 `build runGameTestServer compileDevtoolsJava` **762 JUnit / 375 GameTest，零失败、零跳过**；较上一轮 759/369 新增 3 JUnit / 6 GameTest。七张纹理 `--check` 通过。无 git 写操作，不运行 check-ruins.py，不进入 R101/R102。

外围冠中心去掉 `min(height-4, ...)` 高度封顶，三枝偏移 -2/0/+2，四枝 -3/+1/-1/+3，首尾也至少相差 2；所有冠团下半径比上半径大 1。保留冠高、锥度、宽高比、半径、±14、六邻接连通、入口、悬藤避开完整 crownSpace 的原约束。全冠最低叶片 y 的极差≥4且众数≤35%，外圈半径≥6再要求众数≤30%。

旧形状替换验证均在 finally 中恢复生产文件：
- 原 64 个种子有 **17/64** 被全冠 35% 检测拒绝，旧最大 41.23%，新版最大 24.72%。
- 截图基座 `(3000,120,3000)` **会漏过全冠规则**：旧 65/266=24.44%，极差 9。新增外圈规则锁住这例：旧 50/157=31.85%，极差 6；新版 38/158=24.05%，极差 10。新形状 64 个种子外圈最高 23.57%。没有放宽规则让旧版通过。
- 单独把生产形状恢复为旧版运行新截图回归，1 test / 1 failed；原文：

```text
org.opentest4j.AssertionFailedError: More than 30% of outer columns share one bottom at BlockPos{x=3000, y=120, z=3000}: {9=2, 10=50, 11=32, 12=40, 13=22, 14=8, 15=3} ==> expected: <true> but was: <false>
```

原始失败日志和新旧统计：`evidence/r100-crown-underside-v001/old-screenshot-regression.log`、`old-flat-regression.log`、`old-rim-shapes.md`、`spirit-tree-shapes.md`、`spirit-tree-undersides.csv`。完整五棵逐 y 层主干/全树截面积、最低叶片分布、XY 侧视投影同步生成在 `build/reports/spirit-tree-shapes.md`。树形数据来自生产 Java 函数；主干面积排除板根/斜枝/树心，全树排除 AIR。

| 基座 | 树高/干高 | 冠幅 X×Z | 冠底/顶 y | 木/叶/藤 | 外圈众数 |
|---|---:|---:|---:|---:|---:|
| 0,70,0 | 21/17 | 22×22 | 7/20 | 372/1375/77 | 17.87% |
| 3000,120,3000 | 21/17 | 21×21 | 7/20 | 336/1182/70 | 24.05% |
| 2986,120,3000 | 21/17 | 22×21 | 7/20 | 343/1204/72 | 22.36% |
| -1015,70,-115 | 20/16 | 21×21 | 7/19 | 320/1164/62 | 20.99% |
| 1200,80,-300 | 22/18 | 21×21 | 9/21 | 330/1179/73 | 18.99% |

预检重构保留原有通过/拒绝规则、判断顺序和写入顺序，仅拆出 `Reason` / `Preflight`；拒绝计划不带部分 writes。GameTest 同时核对平地通过、冠部石块/空腔/原木、水地基、4 格起伏、入口箱子、根部水/落差和构建上限，并比较整片场地未被预检改动；加载检查不生成远处区块。探针类只在 devtools source set，正式 JAR 不含它；支持区域/网格、原因和群系直方图、首个阻挡状态、现存树心标记、全区域调色板树心普查。开发脚本 `probe-spirit-trees.py` 只调用只读动作。

seed 100100 的验收存档复制到 `orimod_dev_r100probe` 后检查；原存档 69 个文件 SHA-256 保持一致。64 点网格 x/z=-224..224、间隔64：6 个合格群系点；整个网格 NOT_DRY=63、FOOT_BLOCKED=1，其中2个是之前人工树造成的灵叶高度图污染。5个未人工放树的合格点为4个原木地基拒绝和1个补根撞原木，尚未进入冠部净空阶段。第一次只读扫描记录47个UNLOADED；之后在副本中独立 forceload 四象限，探针本身不加票、不加载。

同区域间隔32的256点中仅统计**29个合格群系点**：

| 分类 | 数量 | 判据 |
|---|---:|---|
| 原版原木 | 13 | 首个拒绝方块为原版橡木/白桦原木，横跨地基、补根和净空阶段 |
| 水 | 3 | 首个拒绝方块为水 |
| 起伏 | 2 | RELIEF |
| 其他 | 6 | FOOT_UNGROUNDED=4、CLEARANCE_OTHER=1、补根撞到虞美人=1 |
| 通过 | 5 | OK |

按群系：forest 12点（NOT_DRY3、FOOT_BLOCKED3、FOOT_UNGROUNDED2、CLEARANCE_LOG1、RELIEF1、OK2）；birch_forest 17点（NOT_DRY8、FOOT_UNGROUNDED2、CLEARANCE_LOG2、CLEARANCE_OTHER1、RELIEF1、OK3）。原始近树标记保留，32格内已有人工树的点不能当独立未经改动样本。加载数前后3364→3364；64点22.49ms、256点114.55ms。资料：`evidence/r100-preflight-loaded-v001/results.json`、`r100-preflight-dense-v001/results.json`、`r100-preflight-v001/eligible-summary.json`。

按 Claude 决定，biome_modifier 改为 **local_modifications**，稀有度220/起伏3/不替换原木保持。数据测试锁住阶段。核对 NeoForge 21.1.248 的 MC 1.21.1 源码：
- `VegetationPlacements.treePlacementBase` 用 OCEAN_FLOOR；有些外层 placement 带 would_survive，有些在 `TreePlacements.*_CHECKED` 内层过滤。冠顶灵叶能阻挡高度图，但不是树苗地基；新增 GameTest 真实放树后验证冠顶高度、四种树苗不存活及六个 vanilla checked placed feature 拒绝且不改方块。冠洞/冠外列的原版树与冠缘少量叶替换仍可能发生，未宣称全面排斥。
- `ChunkGenerator.applyBiomeDecoration` 按阶段顺序装饰当前区块，邻块没有全局阶段屏障；先前已装饰邻块的原木仍可能阻挡。
- 遗迹 JSON 实际是 **top_layer_modification**，不是 surface_structures。`RuinStructure` 在早期 STRUCTURE_STARTS 用基础地形样本确定模板+入口 pieces；`StructureStart.getBoundingBox` 合并两者，`RuinVegetationGuard` 在 WorldGenRegion 读取该不可变范围并外扩14。模板及入口的清冠在同一水平范围内，提前生成的灵树仍被此保护排除，不会被后来遗迹切开/埋掉。无需改遗迹资源。

探针是装饰完成后 ServerLevel 的 `/place feature` 预检，不运行 WorldGenRegion 专用 RUIN_GUARD，也不能把此时的通过率当作 local_modifications 阶段的天然成功率。自然数量另在新世界普查，不用后装饰预检率外推。

本次增量文件：

```text
src/main/java/cn/wwl/orimod/world/SpiritTreeShape.java
src/main/java/cn/wwl/orimod/world/SpiritTreeFeature.java
src/main/resources/data/orimod/neoforge/biome_modifier/spirit_tree.json
src/devtools/java/cn/wwl/orimod/devtools/DevelopmentBridge.java
src/devtools/java/cn/wwl/orimod/devtools/SpiritTreeProbe.java
src/gametest/java/cn/wwl/orimod/gametest/SpiritTreeGameTests.java
src/test/java/cn/wwl/orimod/world/SpiritTreeFeatureTest.java
src/test/java/cn/wwl/orimod/world/SpiritTreeShapeMetrics.java
src/test/java/cn/wwl/orimod/world/SpiritTreeShapeReportTest.java
scripts/devtools_client.py
scripts/probe-spirit-trees.py
docs/WORK_STATUS.md
docs/TESTING.md
docs/DEVELOPMENT.md
docs/ORI_PARITY.md
```

提前生成的新世界实测（`orimod_dev_r100early100101`，seed **100101**，标准 llvmpipe / `DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority` 启动，GL4.6已核对）：

- 新世界未运行任何放树命令。四条 forceload 加载 x/z=-256..255，共1024个目标区块；第一条因生成超过command桥的10秒观察期限而返回 TimeoutException，随后查询确认已添加，未重复执行。其余三条各返回256。
- 内网格 x/z=-240..240、step32，所有256点的完整±14 footprint都已加载：这些 footprint 覆盖全部目标32×32区块。另对完整512×512矩形按区段调色板逐格普查树心，**3棵**：`(-108,110,-250)`、`(-69,109,-237)`、`(57,72,-11)`。
- `execute positioned 0 100 0 run ori tree nearest 1024` 返回1；对每颗树心中心执行半径1查询也各返回1。没有用 success=true 误判。
- 该新世界后装饰网格153个点属于灵树群系；预检NOT_DRY96、RELIEF22、FOOT_BLOCKED9、FOOT_UNGROUNDED8、CLEARANCE_LOG2、CLEARANCE_OTHER13、OK3。它记录已经长完原版植被的状态，不能用来反推提前生成成功率。前一seed的群系覆盖不同，也不能把0与3视为控制变量实验。
- 网格探针202.61ms、全矩形普查动作56.90ms，加载数均3364→3364。资料：`evidence/r100-early-density-v002/results.json`（网格、普查、命令返回码和加载票据），`evidence/r100-preflight-v001/local-modifications-full.log`（全量），`checks.json`（原验收世界69文件哈希不变及JAR检查）。
- **最终实拍验收通过（Claude）：** 三棵自然树正常；冠底不再连成直线，冠顶收圆；白桦林中入口仍可通行；夜景发光良好。证据 `evidence/r100-rework-natural-v001/`。前序自发光/cutout/灵藤/锥度通过记录在 `r100-rework-accept-v001/`、`r100-rework-accept-v002/`；旧自然密度和放置率在 `r100-rework-density-v001/`、`r100-rework-acceptance-rate-v001/`。没有从一次512×512样本断言长期自然密度。
- 按 `forceload remove all` → `leave_world` → `quit` 关闭客户端。remove all 原版实现固定返回0，不能据此误判失败；关闭后读取新世界 `data/chunks.dat`，Forced 列表确认为空。关闭记录 `evidence/r100-final-close-v001/results.json`。客户端/图形操作统一 llvmpipe + `DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority`；最后只跑差异格式与纹理复现检查，代码未改，不重跑全量。

最终交付文件清单见 `evidence/r100-preflight-v001/changed-files.txt`（74个Codex累计R100文件；排除AgentBridge/AGENTS/CLAUDE和Claude维护的验收脚本）。Claude另将 `scripts/verify-r100-rework.py` 纳入提交，提交操作由Claude完成。
