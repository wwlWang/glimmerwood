# R115 B 大灵树：外形检查点与内部接入

外形 v003 已通过两颗种子的审查；以下保留三个外形检查点的历史证据。内部与玩法已接入、正在验证，完整 B 验收未完成。旧的复苏个人回执不变；未完成仪式的玩家改到大灵树之心举行。

## 选址与定位

`GreatTreeSite` 是主世界 SavedData，保存原点、实际出生点、形状种子、形状版本和“已开始生成”标记。服务器初次启动完成出生点选择后，优先在 300–800 格环内选择森林/平原等适宜群系；排除湿地面、高山、海洋以及过大的高差。读取 `.mca` 位置表并检查现有 chunk holder，完整树冠及安全边界的任一区块已存在时整个候选都拒绝；外圈后备上限为 2500 格，供已开发的旧世界使用。选中后先落盘，再将不可变快照提供给生成线程，避免崩溃留下树而丢失选址。加载旧站点在 Level.Load 发布，确保邻接区块补生成前即可读取。

`GreatTreePlacement` 自定义 placement 仅在持久化的一个区块返回 true。原版 `ChunkGenerator.findNearestMapStructure` 只处理 random_spread/concentric_rings，故 `GreatTreeLocateMixin` 在其 RETURN 处补入唯一站点；混合结构 tag 保留原版候选并比较距离。`/locate` 自身未改，仍使用原版坐标、可点击传送和距离消息。

OP 命令：`/ori great_tree site` 报告坐标、出生点距离、生成标记及写入采样；`repick` 只允许旧选择尚未被任何区块生成触及；`force_place` 通过正常 chunk worldgen 加载所选区域，不向已生成建筑直接盖模板。不允许生成后重选，以免出现第二棵。子命令挂在 `/orimod` 主命令树，`/ori` 保留原重定向。

## 外形与切片

`GreatTreeShape` 是纯 Java 体素生成器，形状版本保存在 piece 中。九条锥形板根、弯曲空心主干、九根承托主枝、三层分离冠团与稀疏冷光悬藤；叶团采用厚壳，降低不可见内部叶片及光照工作。叶/藤沿用原创纹理；新树皮为16×16暗色纹理，全表面不露浅色断面。vein 状态仅在独立透明遮罩层以 NeoForge 顶点满光照绘制冷光纹路，树皮仍受环境光照，无新增第三方美术。

`GreatTreePiece.postProcess` 只写当前 chunk 的 clip；坐标计算始终相对同一个保存原点，不按区块重新随机。地表是向中心渐高的土丘和延伸到当地地面的根底，不铺矩形台，也不削平外围地形。入口与空心轴已留空，内部步行路线尚未放置。遗迹、驿站、先祖之树、腐化地带生成起点避开预约区域，原版树生成也复用该预约区预筛。

美术清单 `art/source/great_spirit_tree/v003/manifest.json` 记录 Java 源 SHA、已有方块调色板和两参考种子的体素统计；`python3 scripts/art/build_great_tree_manifest.py --check` 使用同一个纯生成器校验，不写资源；`build_great_tree_textures.py --check` 校验纹理源和包。这里是运行时体素美术，没有重复导出的 GLB。

## 验证和实拍

新增单测覆盖形状尺寸、连续空心入口、切片总量一致、确定性、悬藤支撑、唯一记录往返、负坐标 Anvil 索引和损坏文件保守拒绝。GameTest 覆盖实际写入 clip 以外不变以及已生成标记的存档恢复。远处拍摄使用新建存档的自然结构，不用 `/place`。

首轮全量暴露命令别名注册回归：`Incorrect argument for command at position 4: ori <--[HERE]`。原因是新子命令注册到重定向别名，已改到主命令树；原甲虫命令测试未改动。日志 `evidence/r115-b-shape-v001/full.log` 保留。修后全量见 `full-02.log`：1421 JUnit / 828 GameTest，0 失败、0 跳过。

`scripts/capture-great-tree.py <新证据目录>` 创建种子 115115/115116 的两份新存档。客户端必须以 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority` 启动。Devtools `great_tree_state` 返回出生点、唯一坐标、距离、生成标记及每块的 writes/shapeMs/writeMs；`command` 可显式传 `captureOutput:true` 记录真实命令反馈，默认行为不变。

## 第二版外形修订

第一版 115115 实拍已确认距离体量、夜光、地形根基和8.04ms写入预算，但冠团相连成蘑菇，枝梢露出浅色端面，根与暗主干色差大。按审查将冠团分为4个低层、3个中层、2个高层，最低层伸得最远；每团由自己可见的粗臂承托，枝梢结束于冠团内。主干下段加宽约15格，向上渐收约9格。根、干、枝使用同一暗树皮和上行冷光纹路，入口改为双根拱与上升土坡。

新增纯投影检查锁住天空窗口及外露枝臂，不只统计冠高。第一版捕获程序在完成115115后收到进程退出143，未出Java崩溃报告；日志保留在client.log，第二版两颗种子都使用新存档重新拍摄，不复用旧piece。

第二版全量 `build runGameTestServer compileDevtoolsJava` 已通过：**1423 JUnit / 828 GameTest，0失败、错误、跳过**。证据为 `evidence/r115-b-shape-v002/full.log`、`counts.json`、`gametest.xml`；新增两项形状回归（天空缺口/可见枝臂、树干锥度/三段光纹）。

该日志保留一条 `Unprimed heightmap: OCEAN_FLOOR_WG 9 6`：来自 great_tree 的直接写入夹具，在 `ServerLevel` 的成品 `LevelChunk` 查询仅世界生成阶段通常具备的高度图。映射源码 `ChunkAccess.getHeight` 在 IDE + LevelChunk 下先记 ERROR，然后调用 `Heightmap.primeHeightmaps` 并继续读取；实际结构生成使用 `WorldGenRegion`。此条不是测试失败，未通过调整断言或抑制日志掩盖；自然生成客户端日志另行核对。

## 第三版外形修订（两种子已通过审查）

第二版分离冠团与光纹通过方向审查，但圆球轮廓显得机械；即使体素包围盒约68格，画面仍显窄。第三版每个冠簇由五个重叠子椭球构成，宽高比以约1.6–2为目标，径向外缘下垂，低层最大最扁、中高层大小和高度错开；表面用低频扰动破除光滑轮廓。粗枝改成三次Bezier弯曲，在树冠下先分一次叉，两枝端收在冠内。第二版主干、板根、入口和光纹材质保留。

纯形状八种子回归现同时要求X/Z冠幅60–74格（原X下限50）。参考115115：冠幅68×67，冠叶Y34–71；115116：70×68，Y34–69。此处为直接形状种子，不冒充实际世界的派生形状种子。首次叶壳过薄仅7004/7042叶，原有“足够体量”断言如实失败（shape-test.log）；加厚冠簇壳后9209/9276叶，9项形状测试全过（shape-test-02.log），没有降低原断言。

上节高度图夹具在第三版显式建立WG高度图，模拟正常世界生成前置条件；生产生成代码和边界断言未变。第二版采样按Claude新指示中止，尚未完成第二个种子；第三版将重新建两份存档，不沿用旧结构起点。

第三版最终全量同样 **1423 JUnit / 828 GameTest，0失败/错误/跳过**，`evidence/r115-b-shape-v003/full.log`（4m48s）、`counts.json`、`gametest.xml`；高度图告警未再出现。两个艺术复现 `--check` 和 `git diff --check` 全过。最新美术源为 `art/source/great_spirit_tree/v003/`，v001/v002仅留历史对照，交付清单不包含历史版本。

## 第三版实拍与验收

Claude 已通过两颗种子的冠簇、弯曲分叉粗枝、天空缺口、夜间贯通光纹、选址和切片性能；批准进入内部与玩法。`evidence/r115-b-shape-capture-v003/` 共18图，每种子：140/155格日夜远景、入口远视/地面近视、根部正反侧、基部俯视。均为新存档自然生成，实际派生形状均约69×67冠幅、叶顶距基部68格。两份世界重载后原点不变，原版locate与OP命令完整输出在results.json。

| 世界种子 | 原点 | 出生点距离 | 切片最大耗时 | 最重切片写入/耗时 | 首次纯形状构建 |
|---|---|---|---|---|---|
|115115|(648,67,376)|662.12|7.18ms|9540格/5.11ms|94.79ms|
|115116|(-40,100,-696)|656.39|5.08ms|10127格/5.08ms|57.11ms|

每棵36个裁剪切片（包括无写入边角）。计时“写入阶段”包含本切片高度查询/体素扫描，不含单次纯形状构建；不是服务器整tick计时。详细采样在`evidence/r115-b-shape-v003/capture-summary.json`。Sodium开启、无光影，llvmpipe/GL4.6，DISPLAY=:0；根部/光纹近景已核对。拍摄结束正常清票、离开、quit，记录shutdown.json。首次两个历史捕获的中断日志保留，验收只使用已完整passed的v003。


## 内部与玩法接入（第二版实拍待验收）

`GreatTreeInterior` 是独立纯布局，覆盖在已通过的 v003 外部形状上，不重新随机冠形、树干或板根。相对原点：入口 `(0,0,-18)`，两格宽三圈盘梯上升24格；大厅地板 y23、脚位 y24，树心 `(2,25,1)` 在三格宽发光根台上；根洞能量格祭坛 `(-1,-8,3)`。楼梯使用原版半格台阶，每级间两格平踏步，不需要跳跃、猛击、攀爬或任何未学技能；反向同样可走。入口改为短而外扩的单道根拱，外半段露天；每圈盘梯三处采光窗、每六步一处实际亮度14的发光根结，最高天窗贯通冠层。根洞以根木封边，防止随机洞穴或地下水闯入。室内用独立浅根木及单像素暗青细纹，收束线仅留在大厅地面，不改已通过的外部贯通光纹。

新 `great_tree_heart` 仍为一格交互/碰撞核心，共享 Sanctuary BE/模型读取但不改 A 的12套资产。第二版原创五根弯曲根笼托起带尖端的六棱冷光晶体和三簇卫星晶，共1676三角面；只做缓慢旋转/漂浮。独立包内 `models/sanctuary/great_heart_{roots,crystal}.glb`，根木16texel/格，nearest采样；发光纹路/晶体单独emission pass，根木吃世界光。完整3×3×3.7渲染AABB防止错误剔除。大厅是不规则拱顶，有三条贯穿叶冠的采光井。

源与复现：`scripts/art/build_great_heart.py` → `art/source/great_tree_heart/v002/`（原创网格JSON、两GLB、六张PBR贴图、manifest）；`build_great_tree_interior.py` 和 `build_great_tree_root_textures.py` → `art/source/great_tree_interior/v002/`（布局/调色板/三张原创16px纹理/manifest）。均支持只读 `--check`；外部树皮/形状两个 `--check` 不变。简化的方块模型仅作异步GLB加载失败时的fallback及物品显示，同样由当前生成器复现；世界内正常渲染由新BE负责。

仪式只在新树心触发，要求觉醒、精灵形态和三光，不再要求某棵先祖树的赐福记录。先祖赐福、已有奖励记录不变；携三光重复使用时明确提示前往大灵树。仪式安全站位改在大厅北侧2–4格，保留真实支撑面、扫掠碰撞、生物占位和未加载拒绝检查。锁位/自由视角/取消/奖励回执沿用 R108。7–11秒的临时冠层覆盖扩至40格、相对心部高度12→46；仍与世界复苏/重置共享256写入、2ms批次预算。

首次发现只读持久唯一坐标，120格内检查已加载射线路径，看到大树本体才触发；不扩大逐格提示扫描。`hint/great_tree` 和 `line/great_tree_found` 各记录一次，服从原提示开关、间隔及队列优先级；萤只朝地标前飞4格，避免飞出视野。潜行使用地图石可直接指引根部入口，不生成目标区块、不扣灵光；普通使用仍指向下一处遗迹。Companion 协议9→10，Guide协议2→3，并测试旧协议在读入新ID前拒绝。未新增NPC商品。

`great_tree_state` 除原选址/切片计时外新增 `entrance`、`heart`、`reward`、`ascent`、`descent`，坐标均为世界坐标。拍摄脚本 `capture-great-tree-interior.py` 新建自然世界，入口到大厅及下行根洞只用真实前进键和转视角，路径中不传送、不注入速度、不跳跃。初始定位与独立先祖拒绝夹具使用命令，证据单独记录。

初次纯路径测试抓到转角覆盖台阶和大厅地板封住最后一段头顶的问题；生成器已保留转角原台阶并为盘梯穿越大厅地板留空，断言未降低。`interior-03.log` 保留修复过程；完整测试与实拍结果见以下记录。


内部第二轮全量已通过：**1433 JUnit / 831 GameTest**（`evidence/r115-b-interior-v001/full-02.log`、`gametest-02.xml`）。盘梯双向、根洞双向使用原版玩家尺寸扫掠碰撞与最大0.6格台阶，技能位为0；新树心无需先祖赐福即可结算500，重复结算拒绝；先祖仍送技能但不能举行仪式。原R108的300tick时序、锁位、取消和重置全绿。

`full-01.log` 的新增远场查询夹具误把 `ServerPlayer.setPos` 自己引发的加载算入被测查询：1625→2274发生于夹具定位；`visible()` 后仍2274、`guide()` 后仍2274（`R115_DISCOVERY_CHUNKS`）。将基线放到定位完成之后，不改“不加载新块”的断言。旧小树外部后退站位单测/危险地面夹具按新大厅距离调整，仍拒绝墙、虚空、岩浆、水和生物占位。实际写入及服务器总tick基准仍保留R108整棵先祖树回归：1429叶、7tick、批次2.001ms、服务器2.754ms（同日志），不冒充大树性能。


第一版内部功能实拍：`evidence/r115-b-interior-capture-v002/` 完整三圈真实按键上楼；`v003/` 从实际走到的大厅继续，伟大树心结算500、9292/9292加载叶片盛开，原路下楼进入根洞，能量格物品0→1→1，先祖树拒绝仪式。`v001`视频相对目录解析错误、`v002`用人形1.62眼高去点精灵视角导致点到台座，均是拍摄脚本问题，保留原失败记录；v003用实际眼高完成。Claude对功能没有阻塞，但指出第一版楼梯太黑、室内粗光纹、方晶和箱形大厅：第二版据此修照明、材质、晶体/拱顶和入口，功能证据不代替新版视觉验收。


第二版新增单测锁住每级台阶头顶5格曼哈顿距离内有根结，以及每圈三条向外贯通的窗口、无旧VEIN粗纹、顶井到y82贯通。新增GameTest检查根结真实亮度14及相邻空气传播≥13、树心BE的存档/更新包和移除清理。`evidence/r115-b-interior-v002/full-01.log` 保留一条新夹具材质计数失败：切片取样区改为内根木，原测试仅数外树皮导致“Fixture did not intersect the trunk”。计数纳入两类木材；同一取样、所有越界写入断言保持不变。


`full-02.log` 树相关测试全部通过，但旧 `gloom_tide.scheduler_enrolls_once_and_honors_wave_interval` 偶发“First scheduled wave missing”。没有在失败当刻取得每个候选点拒绝原因，因此不把根因冒称为已证明。静态检查发现其61×61场地同样超出room票范围，却不像频率用例那样等待区块/实体数据；将已有 `loadedRing` 私有票和400tick准备期复用于此用例，保留所有波次/间隔断言及生产刷怪逻辑。票在成功、异常和超时时都撤销，报告中的准备耗时与后续全量结果单列。


## 第二版内部最终证据（待Claude视觉验收）

最终全量 `evidence/r115-b-interior-v002/full-03.log`：**1434 JUnit / 832 GameTest，零失败、错误、跳过**，3m40s。潮夜两项准备分别0/71tick，实耗2.32/1178.23ms；仅补夹具准备条件，波次断言/生产行为不变。生产src逐文件SHA-256与最终全量留档一致。

`evidence/r115-b-interior-capture-v004/`：全新世界 `orimod_dev_r115_b_interior_capture_v004_115115`，出生点(48,70,96)，原点(648,67,376)，出生距离662.12；locate与OP site完整消息在results.json。24张原始图和9段mp4/frames.json，入口和每圈楼梯均有日夜图；人形技能位0、无跳跃，从入口真实前进到y91大厅，再原路下楼至y59根洞。视频有少量丢帧，不以视频帧数计仪式时长；位置轨迹单独保留。树心两端Sanctuary BE存在；真实右键演出完成+500，9228/9228加载叶片盛开；根洞能量格物品0→1→1。大厅晶体、根笼、拱顶、窗口与薄线内根木实拍可见；透过窗/顶井仍能看到已批准的外部粗光纹，未更改外树皮材质。

`evidence/r115-b-interior-gallery-v004/`补5张：大厅宽景日夜、拱顶、根洞宽景、先祖树拒绝文案。宽景机位用命令定位，仅作视觉补图，不冒充实走；实走看主目录视频。主目录最后的先祖拒绝测试夹具未清空气，被自然树挡住，原“active=false”断言不充分，因此那一张不作拒绝证据。补拍清理独立夹具空气后，确认hit=(694,67,376)、领取tree/回执，再次真实右键显示“Bring the three wisps to the great Spirit Tree. Ancestral Trees grant blessings only.”且仪式未开。主拍摄脚本已增加这两个前置断言；无产品改动。

最大内部切片为chunk(40,23)，世界x640..655/z368..383，含中央主干/三圈盘梯/大厅和树心；**10443写、22.422299ms**。其余切片最大4.216599ms（36切片包含空边角），纯形状首次计算56.73ms单独计时。它是一次性worldgen worker的写入阶段（包括扫描/高度查询），不是服务器整tick。未做采样分析，不能归因于晶体、BE或光照。

三个写入路径均用`Block.UPDATE_CLIENTS`（2），不含`UPDATE_NEIGHBORS`。核对当前NeoForge映射源码：WorldGenRegion在ProtoChunk只放DUMMY BE标签；ProtoChunk.setBlockState仅在INITIALIZE_LIGHT之后才checkBlock，正常结构FEATURES早于该阶段。因此正常新生切片不逐方块重算光照，后续原版光照初始化照常进行。OP强制在成品LevelChunk放置仍需正常光照更新，不能省掉。未发现安全且有意义的低成本flag改动，保持现状；Claude已接受单个约22ms重切片。

环境：llvmpipe/GL4.6、DISPLAY=:0、Sodium开启、无shader。拍完forceload remove all→leave_world→quit，退出证据`r115-b-interior-shutdown-v002/`。未实测真实多人或第三方光影。五个艺术生成器只读--check、git diff --check通过；完整交付清单`evidence/r115-b-interior-v002/files.txt`，新美术源仅外形v003/内部v002/树心v002，历史版本不在清单。B停止等待审查，C/D未开始。
