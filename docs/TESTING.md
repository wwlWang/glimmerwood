# 回归测试

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
