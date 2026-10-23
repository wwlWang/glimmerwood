# 开发与验收工具

## R115 B 大灵树（外形阶段）

唯一站点、原版 locate 补充和按区块裁剪生成的实现说明见 [R115 B 外形记录](drafts/R115_B_SHAPE.md)。OP 命令 `/ori great_tree site|repick|force_place`；已触及的站点不可重选。Devtools `great_tree_state` 返回站点/出生点/写入耗时；`command` 可传 `captureOutput:true` 收集真实反馈。内部玩法待外形批准后接入。


## R115 A 建筑模型与旧档升级

灵泉、三种遗迹神龛、两座首领祭坛、地图石、钥石门保留原注册 ID / 原碰撞 / 原交互。`SanctuaryBlock` 提供共享 BE；`SanctuaryMigration` 在区块加载时先用 section palette `maybeHas` 预筛，只对含核心的区段补缺失 BE，不查询邻区块。服务端在发区块包前补建，客户端加载/重同步也能补建；`getUpdateTag` 和更新包同步地图石与祭坛的外观状态。旧档已领取奖励仍以原 rewards 为准，旧地图石的外观首次再次使用后点亮，不推测历史回执。

`SanctuaryRenderer` 用原生实体缓冲绘制静态 GLB 三角形：石材吃世界光，水面/铭文单独自发光，支持无 Flywheel 可视化时显示；资源加载期间回退旧方块模型。覆盖完整体量的 `getRenderBoundingBox` 防止只按核心一格裁剪。额外盆沿/拱/悬浮核心无碰撞。祭坛核心同层有至少三个坚固邻格时，视为旧式埋地核心，视觉基座上移一格并同步扩大渲染 AABB；不改核心坐标或地形。钥石门两面均有共享进度的钥石槽（旧门无朝向属性），按原连通门洞合成圆门，第二次插钥石仍当 tick 变空气；客户端从方块更新前快照播放 24 tick 收拢消散，最多 32 个，离线清空，不引入延迟碰撞或新协议。

原创源：`scripts/art/build_sanctuaries.py` → `art/source/sanctuaries/v002/`（12 份 .blend / GLB / PBR 贴图、manifest），打包到 `models/sanctuary` 和 `textures/sanctuary`。生成：`~/.local/opt/blender/blender -b --factory-startup --python scripts/art/build_sanctuaries.py -- --version v002`；校验：`python3 scripts/check-sanctuaries.py`（只读，检查源与包字节、SHA、单 UV、nearest 采样、512×64 纹理、纹理引用与节点变换）。与全部图形命令一样使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0`。

石材使用模型米制坐标投影到 8 个 64×64 材质区，约 16 texel/格；单位门模型预补偿常用 2×2 门洞缩放。林泉为带苔点的绿灰石，溪谷为蓝灰石，洞穴为深板岩色，含像素石粒、错缝和暗刻槽；水面/铭文/核心材质保持原颜色与发光。根拱基部半径 0.27（原 0.18），顶部升高 0.5，整体最高 2.79 格。v001 源保留为首轮外观参照，当前打包/校验使用 v002。

Devtools `inspect_block` 新增 `sanctuary` / `clientSanctuary`，各含 present、phase、activated，便于核对区块重同步。`scripts/capture-sanctuaries.py <新目录名> gallery|legacy|insitu` 连接客户端；gallery 在 (6400,150,6400) 建展示台，legacy 读取 R114 自然世界的副本，仅使用原神龛与驿站，不重新放置遗迹模板；insitu 拍摄原自然遗迹中的地图石/两座祭坛及洞穴门，门会被真实插钥石打开，需使用未开过的测试存档副本。所有实拍结束后撤销 forceload、leave_world、quit，再运行 Gradle。

本阶段小树显示名为“先祖之树 / Ancestral Tree”，ID、命令和奖励键未改。B 尚未实现，因此复苏仪式暂时仍可在原树心使用；不会在 A 提前移除唯一结局入口。


## R113 恢复与技能轮盘

G轻按松开开原能力/碎片面板；按住250ms开技能轮盘，鼠标或滚轮选择，再按Z/X/H绑定。松G直接关闭。三个槽继承原光爆/蓄力灵焰/灵闪的按键配置，Controls中改名“技能槽1/2/3”；移动能力与武器不进轮盘。打开菜单会取消未完成引导，绑定时按住的技能键要松开重按才会施法。

`WheelSkill`一处登记目录/触发方式/原服务端action/耗能/冷却；`SkillBindings`为三个持久槽、初始化标记与已见技能集合，死亡复制，仅同步本人。新学技能填首个空槽、不挤已有技能；重复绑定会从旧槽移走。`skill_bind`通道v1有显式线格式版本拒绝。施法仍走已有progress_action验证，没有新增可绕过校验的施法入口。

`spirit_state.client/server.skills`包含slots和readyAt；`client.skillWheel`包含open/hovered/skills/centre/radius；`server.recovery`包含regenerating/lastDamage/food。`ori energy set 20`、`damage @s 8 minecraft:generic`可准备恢复场景，保持naturalRegeneration=true、去除回血碎片/效果。精灵脱伤8秒后0.5HP/s至80%；按住复原每秒扣10能量回6HP，伤害中断需松手。冷却后回能只到20，精灵饥饿冻结；人形食物保持原版。

实拍脚本：`DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe python3 scripts/verify-r113-recovery-wheel.py <新证据目录名>`，连接现有验收客户端，在(5800,140,5800)建立测试平台，使用XTest真实G/Z/H和鼠标输入；不要在生产世界运行。脚本不启动Gradle、不退出客户端；交接时清forceload、leave_world、quit。技能PNG由Claude制作，本轮只读取，`icon-check.txt`记录全部6张逐字节复现。

## R113 羽毛与灵泉验证

人形使用原版鞘翅；精灵装备同一个 minecraft:elytra 得到羽毛滑翔升级，仍需GLIDE。仅客户端默认名称/物品模型切换，服务器保存和物品ID不变；其他模组鞘翅不接管。`ori awaken`、`ori unlock all`、`ori form on|off`、`ori energy set <n>`可准备场景。左下主HUD的回退设置为客户端 `vanillaHud`。灵泉用右键发现/补满并打开同维度选择列表；遗迹面板的灵泉按钮也打开该列表。

`spirit_state.client/server.feather`给出wearing/permitted/gliding/handoff/migrationRemaining/displayName；服务端displayName保持原物品名，客户端随本地形态切换。`container_state.slots[].name`便于比对名称；脚本用XTest在DISPLAY=:0移动真实鼠标，截图原版工具提示。验证脚本为 scripts/verify-r113.py。客户端启动遵守 llvmpipe + DISPLAY=:0；测试运行时关闭客户端。

运行方式：`DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe XAUTHORITY="$HOME/.Xauthority" python3 scripts/verify-r113.py <新证据目录名> <flight|beauty|inventory|hud|well|death|all>`。脚本只连接已启动的验收世界，会改建(5800,140,5800)附近测试场；beauty需单独运行，临时静音Aura看羽毛姿势。最终两形态证据与覆盖限制见TESTING的R113条目；第二批恢复循环已接入，数值依据见 `drafts/ORI_VANILLA_PARITY.md`。


代码按职责命名。`P1`、`P2` 等阶段编号只用于计划和验收记录，不进入包名、类名、资源 ID 或构建任务。样例使用明确的 `Preview`、`Sample` 名称；正式玩法类按实际行为命名。

## R107 驿站验收

- `/locate structure orimod:glades_outpost` 查自然结构；`/place structure orimod:glades_outpost ~ ~ ~` 摆放模板。后者不登记structure start，适合外观、摊位、泉水测试；`first_outpost`入场台词应在自然结构验收。
- 模板31×18×31，局部入口脚位(15,3,0)，苔语(7,3,13)，路路(23,3,13)，灵泉方块(15,3,19)。未旋转时世界坐标等于模板原点加局部坐标。自然生成四旋转由piece处理。右键商人交易；R113起右键灵泉回满并打开同维度已发现目的地列表，取代原来的潜行右键循环传送。
- 独立测试商人可 `/summon orimod:glades_merchant ~ ~ ~ {Job:0}` 或 `{Job:1}`；结构生成的商人自带泉水/站位绑定及持久化。`/ori light 500`准备余额（勿先unlock all，否则所有碎片已拥有而无商品）。`/time add 24000`检查隔日换货；同一天购买一件后不补抽，重新打开仍见原3行且已售行禁用。
- 成长面板提供商人价×1.5的保底购买（120–300），并提示去驿站可省钱、显示2048格内最近驿站的异步指引；商人仍按80–200出售。由服务端检查余额/所有权并只扣一次，已拥有碎片不变。经济调整保留商人探索价值，同时避免海洋出生或旧世界找不到驿站卡进度。
- 价格：充盈/回流/复苏100，聚光80，轻灵/生机汲取/灵能汲取120，荆棘/吸附140，极限猛击160，三段跳200；路路遗迹40/灵树60/腐地50。未找到不扣灵光。指引默认最近水平距离≤2048：遗迹未通关，灵树未领取，腐地未净化；后两者来自持久生成索引而非视野扫描，未加载目标不强载。旧版本已生成的树/地带不会补登记；手放方块不登记；用`/place feature`成功生成可建索引。原`/ori tree nearest`、`/ori blight nearest`仍仅查已加载方块。
- 地图石、地图碎片、普通遗迹指引额外包含未发现驿站；付费遗迹商品只搜索遗迹。泉水的32格庇护复用R101调色板/区段缓存，放置/移除即时失效；中心精灵玩家开启`/time set 18000`、`/ori tide start`可测刷怪环带。

`spirit_state`新增读取字段（不改变其他实体/既有字段）：

| 路径 | 字段 |
| --- | --- |
| `server.merchant` | `open`；打开时另有`entityId, job, day, stock, busy` |
| `client.merchant` | 关闭时`open:false`；打开时`open:true, entityId, job, day, balance, busy, message, offers:[{id,price,available}]` |
| `server.featureSites` | `trees, blights`，当前维度已登记数；查询会清除目标区块已加载且方块缺失的条目 |
| `client.guide` | `active`；有目标时`kind, dimension, entry:[x,y,z]` |
| `server.outpost` | `inside`；自然piece内另有世界`entry, well, merchants`坐标字符串 |
| `nearby_entities.entities[]` | 仅商人追加`job:0|1`与绑定的`well:[x,y,z]`；其他实体格式不变 |

`gui_state`/`gui_click`读取并点击现有Screen按钮；widget新增 `textWidth`（实际当前字体宽度），购买按钮必须 `width >= textWidth + 16`，中英文按钮分别“购买 · N 灵光”/“Buy · N”。碎片说明单行显示，过长时省略并悬停看面板同源全文，路路底部单独提示未找到不收费。`client.line`仍可验证`first_outpost`台词。商人消息id是`purchased/not_found/searching/stale/funds/refreshed/...`，余额以服务端为准。交易协议1，同伴协议8，指引协议2，客户端/服务器需同版；FX协议仍8。

资源复现（与客户端/Gradle互斥规则相同，纯Python不占Gradle）：`~/.local/opt/orimod-art-venv/bin/python -B scripts/art/build_glades_outpost.py --check`；`python3 -B scripts/art/build_merchant_textures.py --check`。生成器只写自己的驿站NBT/layout，两张商人纹理读取新增源文件`art/source/entities/glades_merchant.json`；不运行旧遗迹写入模式。客户端仍须 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority` 启动；首轮商人交易/指引/庇护已由Claude实测；修正后的中英双职业UI已通过（`evidence/r107-ui-fixes-v002`），密度数据见下，台地外观待Claude复验。

自然密度探针仅在gametest源码集，发行jar不包含：

```sh
JAVA_HOME="$HOME/.local/opt/jdk-21" PATH="$HOME/.local/opt/jdk-21/bin:$PATH" python3 scripts/verify-outpost-density.py r107-density-check --seed 107
```

创建全新普通噪声地形专服（不启动客户端），不能与客户端/其他Gradle同时运行。289个实际random_spread候选起点运行生产预检 `preflight()`，记录群系、地形和入口拒绝原因；25个坐标固定为实际出生点加 `(x*1024,z*1024)`、x/z∈[-2,2]，不排除海洋。查询走原版 `/locate` 的 `findNearestMapStructure`，验证真实structure start，另记入口距离；输出到 `evidence/<run_id>/seed-107.json`。入口阶梯使用保存的45个噪声地面样本，生成及读档不依赖邻区装饰先后。模板尺寸/坐标不变，台地下方最多填16格至坚实地面。

最终 spacing24/separation10，独立群系标签排除沙漠/恶地/冰刺/蘑菇岛；旧48/20起点仍纳入模组异步指引。seed107同组中位数684.83、最大1856.28；实际入口672.98/1826.60。不是所有种子的距离保证，面板加价购买是进度保底。原始结果：`r107-density-baseline-v001`、`r107-density-v001/v002/v003`（v002为被否决的所有主世界群系对照，不能当最终配置）。最终拒绝前三：群系126、地形119、入口3，另有41个通过；旧版289点仅4个通过。

## 模块边界

`src/main` 是交付给玩家的模组。`src/devtools` 是独立开发工具源码集，模组 ID 为 `orimod_devtools`，Java 包为 `cn.wwl.orimod.devtools`。普通 `runClient` 和发行 JAR 不加载或包含开发工具。

| 类 | 职责 |
| --- | --- |
| `DevelopmentBridge` | 经令牌认证的本地开发接口，执行游戏内操作并读取状态 |
| `CharacterPreview` | 注册角色预览实体，验证蒙皮和动画混合 |
| `SpiritMotePreview` | 生成、重开、导出并播放用于兼容检查的 Photon 特效工程 |
| `FrameRecorder` | 从游戏渲染目标录制帧和时间戳 |

接口仅绑定本机回环地址，令牌保存在开发实例内，不提交 Git。操作使用游戏 API，不操作桌面鼠标键盘。

## 启动

先执行 `python scripts/sync-dependencies.py`，下载并核对可选画面依赖。锁定的渲染库由 `git lfs pull` 恢复，常规开发无须运行 `build-renderer.ps1` 重建。Windows 随后执行：

```powershell
.\scripts\gradle.ps1 runDevtoolsClient --console=plain
```

本机 Linux 客户端统一使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY="$HOME/.Xauthority" ./gradlew runDevtoolsClient --console=plain`（先在当前命令设置 JDK 21 的 JAVA_HOME/PATH）。所有图形命令使用 `DISPLAY=:0`；启动后确认日志 `GL info` 为 llvmpipe / OpenGL 4.6，避免默认 virgl 的 GL 4.2 与 Flywheel 不兼容。云端无界面构建和交接步骤见 [CLOUD_HANDOFF](CLOUD_HANDOFF.md)。

开发实例位于 `run-devtools`。构建工具下载失败时保留日志和已有缓存，再定位或更换网络入口；不要因一次等待超时就启动重复客户端。

启动任务先按 `dependencies/lock.json` 校验依赖哈希。新实例自动安装 Mellow 及 `dependencies/shader-settings/mellow.properties`，已有配置不覆盖。兼容基线必须打开动态阴影；原始预设关闭了这一项。开发接口切换光影后调用 Photon 的缓存失效接口，避免继续向旧帧缓冲合成；正式设置界面及外部 Iris 设置变化仍须接入相同生命周期。

Spirit Glow 的 Ori 自发光材质使用显式标记：手臂顶点 alpha=254（hand 中恢复 255），身体 packed block-light 坐标=241（Colorwheel 中恢复 240）。仅实时选中并开启 `Spirit Glow.zip` 时写入，其他光影/无光影保持原顶点。Java `SpiritSelfLight` 与 GLSL `global/lighting/spirit_self_light.glsl` 的契约有 JUnit 校验。GemRender atlas 第三带是 emission RGB + paint coverage alpha，不可把 alpha 当发光不透明度；Colorwheel 的 `clrwl_computeFragment` 材质输出不可丢弃，也不可重复乘 vertex color/AO。固定暗室回归脚本为 `python3 -B scripts/capture-hand-shader.py <唯一证据名>`，需要已启动的专用 `orimod_dev_feedback_r108` 开发世界；旧包以独立的 `Ori Hand Before.zip` 安装，不能覆盖工作区资源。脚本完成只留客户端供检查，交接前另行 `forceload remove all`、`leave_world`、`quit`。实拍、颜色数据与根因见 TESTING 的反馈 C 节。

客户端就绪后，以下命令读取状态，不会输出访问令牌：

```powershell
.\scripts\devtools.ps1
.\scripts\devtools.ps1 -Action '{"op":"open_world","name":"orimod_dev_compatibility"}'
.\scripts\devtools.ps1 -Action '{"op":"screenshot","name":"character-preview"}'
```

开发世界名称使用 `orimod_dev_` 前缀。截图、录帧和连接信息位于 `run-devtools/devtools`；验收时将所需证据另存到 `evidence/<build-id>`，登记构建和环境。

录制使用 `record_video` 操作指定 `name`、`fps`、`seconds`，需要 PATH 中存在 FFmpeg。画面直接来自游戏渲染目标，以 RGBA 流交给编码器；无需桌面捕获。等待状态中的 `recording=false` 且 `pending=0` 后执行 `save_video`，再将返回的 `frames.json` 路径交给 `scripts/encode-capture.py`。缺失的采集时隙保持前一帧，不加速播放；采集时间戳和重复帧数量随清单保存。录屏与性能采样分开执行。

## R105 腐光甲虫验收

准备 `/difficulty normal`、`/ori awaken`、`/ori form on`、`/ori unlock all`、`/ori giveall`，然后 `/ori spawn beetle`（或原创配色的腐光甲虫刷怪蛋）。在开阔地拉到3–8格：预警15tick期间不移动，方向在预警开始锁定；侧闪可避开，随后沿锁定方向以0.6格/tick冲最多7格。墙碰撞眩晕30tick，撞任何可碰撞实体都停下、不自晕，只对玩家造成6伤害和击退；结束后冷却60tick。甲虫有18生命、移速0.24、检测16格，浅水中寻岸步行，没有游泳/浮力目标，陆地恢复高水域寻路代价。

正面以**身体朝向 yBodyRot**的水平±60°判断，不跟头部转向；近战用直接伤害源位置，投射物用速度反方向，零速度才回退位置，因此从侧后射入的箭不会因射手站在前方而减伤。上方45°锥内正常受伤；正面×0.2伴随叮声/火花，背面正常。下砸真正命中的目标、猛击真正弹出的目标进入60tick掀翻；撞墙眩晕期间只有正数实际伤害才触发掀翻。翻倒时无AI移动但保留外力、重力，伤害×2且不再吃正面护甲；重复触发不续期，恢复后40tick免翻。下砸先翻再伤，因此触发那一下6伤害成为12；眩晕受击先结算该次伤害，之后掀翻。灵闪对甲虫无特殊效果。状态、剩余时间、免翻/冷却、锁定方向与已冲距离保存在实体NBT里，卸载暂停、重载继续。

devtools `{"op":"nearby_entities"}` 仍返回顶层 `entities` 列表，**只给甲虫追加** `state`，取值 `idle / windup / charging / stunned / flipped`。原有id/uuid/type/alive/position/velocity/health/noGravity/noAi/leashHolder字段和其他实体格式保持不变；该读取在服务端线程，24格范围。比如可筛选 `type` 中含 `gloom_beetle` 的条目，再按id连续采样state、position和health。客户端从entityData同步阶段/剩余tick，翻身用6tick滚转过渡；预警背甲满亮度、腿部刨动与烟尘，裂紫背甲/暗腹纹理为原创64×64。`python3 -B scripts/art/build_beetle_texture.py --check`校验PNG。

| 新遗迹 | 甲虫点位（NBT模板局部脚位） |
| --- | --- |
| creek_ruins | (15,6,5)、(1,6,20)，已含4格垫层 |
| cave_altar | (15,2,18)、(6,2,30) |
| forest_spring | 无甲虫 |

点位随piece旋转/原点变换，世界脚位为目标方块的底面中心。`/place structure orimod:creek_ruins`、`/place structure orimod:cave_altar`可摆放验证（仍不登记自然结构引用）。布局JSON新增beetles契约，**三份NBT字节未改**，原路线/检查点/桥/秘室均不变。实际现有喷吐者/鸦走群系生成，没有可复用的遗迹固定点，因此本轮新增piece生成路径：通过WorldGenLevel/ServerLevelAccessor添加实体，STRUCTURE初始化并setPersistenceRequired；OriBeetles存点位、OriBeetlesSpawned逐点记录已处理位置，重新postProcess、死亡后再处理和读档均不补刷。旧piece缺字段即无甲虫；和平难度仍由原版清除。没有甲虫自然刷怪modifier。

潮夜验收用 `/time set 18000`、`/gamerule doMobSpawning true`、`/ori tide start`，结束用 `/ori tide stop`。波次改为1–2喷吐者、独立35%一只甲虫、独立30%一只鸦，上限/16–28环带/防御/同tick区块波次归属不变；固定种子105的200波实测73甲虫、61鸦、296喷吐者。击杀甲虫掉2颗R35灵光，潮汐标记时3颗。

首次12格内有视线的甲虫触发gloom_beetle提示（仍按原有提示顺序、200tick间隔、觉醒精灵门控与客户端同伴开关）；记hint/gloom_beetle。同伴通道与包内版本均升6，v5/v6明确互拒，FX保持7。客户端验收继续使用llvmpipe + DISPLAY=:0，且与Gradle互斥；当前未启动客户端，外观/声音/侧闪与翻身手感待Claude实拍。

## R104 光敏灵台验收

准备：`/ori awaken`、`/ori form on`、`/ori unlock all`、`/ori energy fill`。`/setblock ~ ~ ~3 orimod:spirit_platform`放虚影，`/setblock ~ ~ ~5 orimod:bash_anchor`放灵灯，用真实猛击、蓄力灵焰或光爆点灯；灵闪H按住，每10tick推送7格球，光爆爆点5格球。`/setblock ~ ~ ~5 orimod:spirit_tree_heart`可验证树心10格常亮，拆除后保留100tick。灵灯半径6，从实际熄灭后再留100tick；无光平台亮度2、无碰撞，点亮亮度12、完整碰撞。预警只换模型和粒子，12亮度保持到熄灭。

下表均为**NBT模板局部坐标，森林/溪谷已含4格垫层**；入口/出口是玩家脚位，平台/灯是方块位。三桥未旋转朝向均south（+Z），世界旋转随piece。模板尺寸仍为19×17×31、21×21×35、23×19×39。

| 遗迹 | 入口 → 出口 | 平台方块 | 灯 | 全灭退路 |
| --- | --- | --- | --- | --- |
| forest_spring | (4,8,13) → (4,8,22) | (4,7,z)，z=14..21，共8块 | (4,9,18) | 落入浅水，池底脚位(4,5,z)回到z14，经(2,5,14)低排水口、(1,5,14)落脚位上西岸，再回入口 |
| creek_ruins | (14,6,10) → (15,11,14) | (14,6,11)、(15,7,11)、(16,8,11)、(16,9,12)、(15,10,12) | 无；用灵闪或光爆 | 出口侧(17,5,13)落水口，落到底层后回入口；不设可反向爬升的返回梯 |
| cave_altar | (18,4,18) → (18,4,31) | (18,3,z)，z=19..30，共12块 | (18,5,20)、(18,5,28) | 下层脚位(18,2,z)，z=20..30，经近端(18,3,19)回入口；远端取消上行台阶，出口后侧封闭 |

森林改为模板内部水庭，桥两侧x=2/6与后侧z=24的围墙高出桥面4格，外围草地不能接通奖励端。原入口有干燥引路和逐级台阶通往 `(4.5,8,13.5)`；朝灯猛击后向south水平发射。灯到入口水平5格、到最远平台中心约4.47格，覆盖z=14..21全部8块；四种旋转×中心及四角（内缩0.3格）仍测试至少0.5格选取余量、实际释放/全桥先亮/碰撞落脚。出口小岛 `(4,8,23)` 放一只普通灵光容器，不加入秘室，也不计地图石勘察。池底低于岛面3格，排水口只供撤离，不能沿池底跳上奖励岛。

溪谷基础100能量足够五级上升；光爆可瞄准模板 `(15.5,8.5,11.5)`，一次爆炸覆盖5块。洞穴第一灯只亮前段；从入口 `(18,4,18)` 选第一灯、从已亮中点脚位 `(18,4,24)` 选第二灯，各测同样五个站位和四种旋转，均要求至少0.5格余量、实际选取和视线有效。两球有交叠，两端均能回到安全下层。

不可绕过的回归覆盖三遗迹×两场景：整个模板内搜索，以及模板外5格宽范围填土/草至最高平台高度并允许走到外围。按实际碰撞面（含半砖）、宽0.6格体型搜索；暗态用蹲下精灵的1格高度，亮态及撤离用1.8格高度。跳跃采用更宽松的包络（先升高至多1.25格、平移/转弯总长至多4格、再下降），防绕行搜索允许任何向下落差，安全撤离则要求落差最多3格或入水；不包含游泳、攀爬、搭方块或其他能力。暗态入口不可到出口，亮态双向可达；出口、每个平台失足落点和退路都可回入口。临时恢复森林干岸/洞穴远端台阶时必须找到绕路，防止搜索器漏检；原遗迹入口也必须能到桥入口。仅新生成模板采用此布局，已保存piece不会迁移。

`{"op":"ruin_state"}` 顶层新增 `bridges`：`[{"kind":"forest_spring","entry":[x,y,z],"exit":[x,y,z],"lanterns":[[x,y,z]],"platforms":8}]`，位置已经转为世界坐标。查询复用secrets的已加载结构引用路径，来源为piece存档OriBridges；旧piece或遗迹外为 `[]`，不从新版layout补造。`inspect_block`原有state字符串可见`lit`、`fading`、`blink`，同时有collisionEmpty/fullBlock/shape。不新增命令，原版 `/place structure orimod:forest_spring` 等可以摆桥，但不登记结构引用，接口定位需自然遗迹。

运行 `python3 -B scripts/art/build_platform_textures.py --check`校验资源；遗迹仍用锁定venv的 `scripts/check-ruins.py` 默认只读、显式 `--write`发布。原路线、R103秘室不改，仅新生成遗迹有光桥。同伴新增spirit_platform提示，协议5拒绝旧v4；FX仍7。

平台期限存于每维度 `orimod_platform_timers.dat`，通过原版scheduleTick驱动，不设方块实体。破坏/熄灭立即删除，区块加载按已有坐标索引清除失效记录；期限索引在访问/保存时淘汰已过期的未加载记录，不遍历或加载那些区块。加载事件只在含平台的区段逐格枚举，光源查询延迟到计划刻并用getChunkNow；无平台且无存档条目的区块仅有调色板/索引检查。永久光源在预警/到期刻复查，实体重叠则每5tick等候；没有光源且100tick耗尽后取消待实体化。

客户端必须使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority ./gradlew runDevtoolsClient`，与其他Gradle任务互斥。本轮未启动客户端，尤其需要验收Sodium下水面/虚影透明排序、夜间边框可见度、警告模型与真实返程手感。

## R103 裂壁、容器与秘室验收

调试准备：`/ori awaken`、`/ori form on`、`/ori unlock all`、`/ori giveall`、`/ori energy fill`。用 `/setblock ~ ~ ~3 orimod:cracked_ruin_wall`、`cracked_ruin_floor`、`spirit_light_vessel` 摆测试目标；`inspect_block` 可读 `collapsing`。裂壁允许 CHARGE_FLAME/SMASH/LIGHT_BURST/BASHED_IMPACT/SPEAR，裂地板仅STOMP且必须在落点下方1.5格内。普通手/工具、活塞、TNT/苦力怕、凋灵、末影龙不能打开裂壁/地板；创造可移除。连锁面相连、半径8、最多64块，按直线距离ceil延迟1tick/格，期间再命中不重排，新放块不加入旧波。

容器可徒手或用刀打碎、用箭射碎，也接受上述能力和普通灵焰；亮度10，慢脉动粒子。碎裂散开25–40颗R35灵光，不掉方块物品；先移除再发奖避免重复。自动灵焰优先既有敌人目标，无目标时只选6格内且射线直达的容器，隔裂壁不能打碎。验证无敌人时请留意自动灵焰可能先于手动操作打掉裸露容器。`first_vessel` 沿用R102门控、240tick最大等待及同伴开关。

新生成的每类遗迹各有一个秘室、室内1个及室外2个容器。下表坐标均为**NBT模板局部坐标（已含森林/溪谷的4格垫层）**，通过 `RuinPiece.placement()` 处理世界原点和旋转；朝向为封口朝外的法线。外壳包含封口，内部空间均为3×3×3。

| 遗迹 / 模板尺寸 | 外壳 from → to | 室内 from → to | 入口 / 朝向 / 封口 | 室内容器 |
| --- | --- | --- | --- | --- |
| forest_spring / 19×17×31 | (13,5,14) → (17,9,18) | (14,6,15) → (16,8,17) | (13,6,16) / west / wall；封口(13,6,15)→(13,8,17) | (15,6,16) |
| creek_ruins / 21×21×35 | (1,2,23) → (5,6,27) | (2,3,24) → (4,5,26) | (3,6,25) / up / floor；封口(2,6,24)→(4,6,26) | (3,3,25) |
| cave_altar / 23×19×39 | (1,1,22) → (7,5,26) | (2,2,23) → (4,4,25) | (7,2,24) / east / wall；封口(5,2,23)→(7,4,25)，厚3格 | (3,2,24) |

室外装饰容器：森林(2,6,6)/(16,6,29)，溪谷(2,6,6)/(18,6,32)，洞穴(5,2,16)/(18,2,35)。溪谷返回梯在(4,3..5,25)，攀回打开的附属平台即可，无须追加技能。检查点、挑战净空、原平台地板、钥石门、灵沙通道、祭坛和地图石字段均保持；地图石勘察仍只数灵格、钥石和祭坛。

进入自然生成遗迹后执行 `/ori ruin secrets`：找到返回result>0及入口/朝向/封口类型；不在遗迹返回0并提示“不在遗迹中”，旧版遗迹返回0并说明无秘室记录。`{"op":"ruin_state"}` 的顶层 `secrets` 与命令共用数据源，格式为 `[{"kind":"forest_spring","entrance":[x,y,z],"facing":"west","blocks":"wall"}]`；不在遗迹或旧piece无记录时为 `[]`。查询只用已加载starts/references，不扫描方块；入口被打碎后仍能报告原位置。原版 `/place structure` 只放方块、不登记结构引用，可用于外观/破坏验收，不能单独用于该命令的定位验收。

资源：`~/.local/opt/orimod-art-venv/bin/python -B scripts/check-ruins.py` 为只读校验；改生成器后显式 `--write` 才更新资产，随后运行 `scripts/test_check_ruins.py`（相同venv，nbtlib2.0.4）。`python3 -B scripts/art/build_cracked_ruin_textures.py --check` 核对4张原创纹理与模型/方块状态/空掉落表。存量已生成区块不迁移，旧piece不会从新layout推测秘室。坍塌计划刻及COLLAPSING随区块保存，卸载暂停、重载继续；服务器无独立坍塌队列。

同伴新增两个hint与first_vessel后协议为4（wire 0/4），两端版本需匹配；旧v3与新v4相互明确拒绝。客户端继续用llvmpipe + DISPLAY=:0启动，与其他Gradle任务互斥。R103等待Claude实机验证裂纹、晶罐脉动、坍塌观感及秘室路径。

## R102「萤」台词验收

`{"op":"spirit_state"}` 的 `client.line` 包含 `id`（不含line/前缀）、`text`（当前客户端语言的完整文案）、`remaining`（剩余客户端tick）。未显示时为 `{"id":"","text":"","remaining":0}`。台词显示60tick，末20tick淡出；底行位于屏幕底部以上84像素，原版actionbar在68像素处，长英文向上换行，保留功能性消息。沿用“萤的引导提示”开关，关闭时不显示/响铃/脉冲；已发送的一次性记录仍由服务端保存，与原hint行为相同。

台词与hint共享200tick间隔，hint优先，只有一个待发条目，新覆盖旧；最大等待按台词设置，默认240tick（含boss_start），low_health为60tick，超过才丢弃。默认等待覆盖200tick间隔及下一次20tick扫描：新玩家第一次接近灵树收到hint后立即领取赐福，或刚收到遗迹机关hint便入场，台词应在间隔结束后的扫描说出并写记录，不必先清hint或去第二棵树。`/ori hints reset`只清hint、不清line记录；多个未读hint连续抢先仍可能令台词超时，低血量过时则有意丢弃。没有新增调试命令修改台词记录；重测一次性事件请使用新玩家/测试存档。

遗迹验收请使用自然生成结构（例如 `/locate structure orimod:forest_spring` 后前往实际入口）。已核对 Minecraft 1.21.1 的 PlaceCommand.placeStructure：`/place structure` 调用 placeInChunk 放置方块，但不登记区块的 structure starts/references，因此不能作为入场检测夹具；它的坐标参数也不保证最终结构原点与高度。GameTest 则明确建立并还原真实结构起点和引用。

触发点：三类自然遗迹实际包围盒首次进入；首领首次锁定玩家；翼母胜利；心脏死亡后的逃亡（escape覆盖同时排队的boss_defeated）；潮夜开始/结束；树心领取成功；生命比例由≥25%落到<25%；觉醒后主世界可见天空（同步高度图也无头顶遮挡）且日内时间进入≥13000。低血量在持续低血量时不会因为1200tick冷却结束而重说，必须恢复后再下降。首领每实例一场战斗，持久开战标记防止重锁/重载误触发；另一个新首领仍受该玩家6000tick冷却。

一次性记录为 rewards `line/<id>`，实际发送后写入。排队下线不保存，登录不会回放旧队列；以后时机再次满足可以再排队。换维度/离开精灵形态会清过时队列，换维度保留会话内冷却。遗迹每20tick仅查当前区块及已加载结构引用，三类记录全齐后跳过。

R102引入CompanionHintPayload协议3（R103已升为4），必须两端匹配；在协议版本握手之外，新格式也带0/3标记和kind，旧v2包、新v3包的交叉解码均明确拒绝。FX与潮夜协议不变。客户端仍用llvmpipe + `DISPLAY=:0`，和全量Gradle互斥；台词布局与实际触发由 `scripts/verify-r102.py` 实拍验收。

## R101 腐光潮夜验收

服务器配置文件为世界 `serverconfig/orimod-server.toml`，仅 `[gloomTide]` 下三个项目：`enabled=true`、`waveInterval=200`（20..24000）、`maxNearby=8`（1..64）。改配置不会改变 R98 普通自然刷怪的 16 格灵闪压制；潮夜防御单独采用灵闪/树心 32 格、灵灯 10 格。

已觉醒的精灵生存/冒险玩家在地表且不在挑战/首领战中时生成。验收可按以下顺序执行（operator 权限 2）：

```text
/difficulty normal
/gamerule doMobSpawning true
/ori awaken
/ori form on
/time set 18000
/ori tide start
/ori tide status
/ori tide stop
```

`start` 只忽略月相，白天失败并返回 0（可先 `/time set 18000`）；仍要求 Overworld、非和平、doMobSpawning 和配置 enabled。强制持续到 stop 或离开本夜（包括 `/time set day`，或跳到另一天夜晚）。`stop` 同时抑制当夜自然重新开始，再执行 start 可解除。离开本夜照常清场结算。自然新月验收可设 `/time set 114000`（第 4 天夜晚）并不调用 start。

`{"op":"spirit_state"}` 返回：
- `server.tide`: `active`（世界潮夜状态）、`nearby`（本玩家 48 格球内活着的潮汐怪数）、`kills`（本天计数）、`nextWaveIn`（距该玩家下次尝试波次的服务端 tick 数，无当前参与时 0）。
- `client.tide`: `intensity`（当前 partialTick 插值值）、`target`（维度门控后的目标，0 或正常潮夜 0.35）。用它观测 100 tick 渐入/渐出，以及下界→主世界恢复。`active=true` 不保证此玩家合格；灵闪/树心/灯也可能让所有候选位置被防御。

客户端网络依据：锁定 NeoForge 21.1.248 的 PayloadRegistrar 默认 HandlerThread.MAIN，MainThreadPayloadHandler 通过 context.enqueueWork 执行；ServerPlayer.changeDimension 先发送 ClientboundRespawnPacket，之后触发 PlayerChangedDimensionEvent，客户端 Respawn 也经 PacketUtils 主线程处理。同一连接的正常包序先建立目标 ClientLevel 再处理 tide。为兼容不同交付时序，客户端额外保留最后 active/intensity，ClientLevel 更换只清渐变、按新维度重新应用；断开连接才丢弃最后状态。

防御查询只用已加载区块的调色板预筛；区段缓存灯/心位置 100 tick，方块 onPlace/onRemove 立即失效，卸载清理，缓存上限 4096 区段。潮汐怪以 persistentData 的 `orimod:tide=true` 识别，普通喷吐者不计入 nearby。普通战利品照旧，灵光 ×1.5 向最近整数取整（半数向上）。

启动继续使用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY="$HOME/.Xauthority" ./gradlew runDevtoolsClient --console=plain`，先在命令环境设置 JDK21。客户端和全量构建互斥。视觉仅由实拍确认；夜空向暗紫混色可能比原夜空更亮，当前按审查要求保留。

## 角色变形样例

运行 `scripts/art/build_deformation_sample.py` 时使用全新的后台 Blender 进程和 `--factory-startup`。脚本验证输入模型哈希，生成独立的 `.blend` 修订文件和 GLB，不修改下载原件。

源文件记录位于 `art/source/deformation-sample/manifest.json`。执行 `python scripts/check-assets.py` 检查导出文件的哈希、关节数量、渲染器矩阵槽上限、权重和动画名称，也检查主资源中的玩家 GLB 与贴图是否和已验证样例一致。这个检查通过只代表导出契约有效，视觉质量仍需 Blender 与游戏内验收。

在兼容世界中启动角色和循环微粒后，`python scripts/verify-rendering.py <唯一构建标识>` 执行五次资源重载、窗口尺寸切换及 60 秒性能采样。数据写入 `evidence/<构建标识>`，脚本不会自动判定截图的视觉质量。性能采样期间不录屏。

## 玩法操作与记录

每次重启后用 `python scripts/open-dev-world.py <世界名>` 等待首次资源加载结束，再打开世界。不要在 NeoForge 加载遮罩仍在时发出打开世界请求。

`spirit_state` 同时返回客户端和集成服务器形态、移动、战斗信息；`progress_state` 返回成长、能量和物品栏。`record_motion` / `save_motion` 记录两端每刻状态。`input_script` 的 `steps` 由相对 `at`、按键名 `key`、按下状态 `down` 组成；也可附 `action` 对象，限定为命令、换槽位、背包、暂停、关闭界面、切形态和转向。定时操作报错记入 `inputScriptError`，后续步骤停止。

`spirit_state.client.companion` 返回「萤」最近一个 tick 的世界坐标 `pos`、速度 `vel`（格/秒）、肩部锚点 `anchor`、`anchorDistance`、`returning`、`seekTicks`、`dartTicks`、`hintFlight`；没有跟踪状态时为 `{"tracked":false}`。`spirit_state.client.aura` 和 `render_diagnostics.aura` 返回灯缓存数 `cachedLanterns`、上一帧提交绘制的灯数 `renderedLanterns`、缓存位置与扫描时间；灯已熄灭而缓存尚未刷新时，两数可以不同。

同一个 `aura` 的 `companionDraws` 记录上一帧实际绘制的同伴中心：`source`、`owner` 实体 ID、插值后的世界 `pos`、到主人插值脚部的 `ownerDistance`、`billboards` 数和 `tail`。`screenPixels` 是该中心经当前渲染矩阵投影到窗口的像素坐标（左上为原点，非 GUI 缩放坐标）；`inFrontOfCamera` 指示是否在相机前方，投影 w 接近零时坐标为空。第一人称隐藏本人的光球，因此没有本人的该条记录。用这些字段核对截图中的亮点来源，避免把屏幕间距当成世界距离；状态查询与截图是不同帧，移动时应允许小幅像素差。

`ruin_state` 返回挑战、恢复标记、附近已加载的真实结构起点与有界搜索状态。`use_block` 走原版物品使用入口，`gui_click` / `gui_key` 操作当前游戏界面；`container_state` 只读合成与物品栏槽位。`focus_game` 仅请求本客户端窗口焦点，测试仍使用正式失焦保护。`server_hitch` 仅限集成服务器、1–600 毫秒，可作为定时脚本动作，复现服务器停顿下的输入行为。

R100 灵树验收：在开阔平地执行 `/place feature orimod:spirit_tree ~ ~ ~`，多棵中心至少隔 32 格。Feature 按指定 X/Z 的中心 5×5 地表最高处确定根部 Y；地表高差须 ≤3，脚下干燥实心，树冠/树干/入口不能碰石柱或建筑。树心在中心北侧 2 格处，前方是 3×3×3 空地。若需要固定坐标的测试平台，可在专用开发世界先执行 `/fill 1985 99 1985 2015 99 2015 minecraft:stone`，确保其上方至 Y=130 开阔，再 `/place feature orimod:spirit_tree 2000 100 2000`；树心为 `(2000,100,1998)`。不要在要保留的玩家建筑旁用填充命令。

`/ori awaken`、`/ori form on` 后空手右键树心；`/ori status` 查看能力，`progress_state` 查看灵光与 rewards。已全解锁时，可用另一棵树确认 100 灵光；`/ori hints reset` 可重测 24 格 `spirit_tree` 提示（树心记账不受 hints reset 影响）。`inspect_block` 返回树心状态中的 `bloomed=true/false`；测试用树心也可 `/give @s orimod:spirit_tree_heart`。自然分布的尝试率锁在 `worldgen/placed_feature/spirit_tree.json` 的 `chance:220`，仅合格群系计入，平均每 56,320 平方格一次尝试、约 237 格名义间距，实际成树因地形/障碍/遗迹保护更稀疏。`python3 scripts/art/build_spirit_tree_textures.py --check` 检查七张纹理；不带 `--check` 重生成。灵树是 Feature，当前不参与 `/locate` 或地图石指引。

外观返工后的树形为约 20–22 格高、冠半径最多 11 格的多层穹顶：5×5 根部逐级收到 3×3 / 2×2，3–4 根粗主枝从中段向外伸出，7–8 个有空腔和下部缺口的冠团高低错开。外围主枝冠中心按 3 枝 `[-2,0,+2]` / 4 枝 `[-3,+1,-1,+3]` 交错，包括首尾相邻都至少差 2 格；冠团下半径比上半径大 1，形成下垂圆弧。主干之外的板根最长 6 格；清理被壁龛切断的根尖和随机疏叶留下的孤块，再挂冷蓝白 `spirit_vine`。悬藤固定 2–5 格长，不生长、无浆果；藤尖按下方连接更新，失去支撑时整段脱落，剪刀或精准采集才掉自身。叶子采用 cutout_mipped，悬藤采用 cutout；两者和树心使用自发光渲染，树皮仍受环境光影响。已有世界里的旧树不会自动重塑，外观复测请重新放置。

自然生成调查用 `/ori tree nearest [radius]`，默认 256，允许 1–1024。只搜索当前维度的已加载区块，区段调色板预筛后比较到树心中心的三维距离；不会加载或生成新区块。找到时报告坐标、距离和搜索半径，点击坐标建议传送到北侧入口地面高度；未找到会明确提示。森林航拍时先飞过待调查区域并等待加载，再查询并按坐标去重。命令也会找到人工摆放的树心，因此自然密度记录应使用新的调查区域；不能将未加载区域的“未找到”计为无树。

`SpiritTreeShapeReportTest` 使用实际 Java 形状函数生成 `build/reports/spirit-tree-shapes.md`：五个固定位置种子、每层主干/全树面积、冠幅/冠高、方块数和沿 Z 轴压缩的 XY 侧视投影。运行完整 JUnit 即可重生成；不需要启动客户端。

R100 生成阶段为 `local_modifications`，先于当前区块的原版花草/树木；稀有度仍为 220、起伏仍为 3。迁移原因是原版原木在中心地基阶段就会拒绝，seed100100密网格29个合格点中13个首先被原木阻挡。不清除原版原木，相邻已装饰区块仍可能阻挡。遗迹 start 在 STRUCTURE_STARTS 就确定；`RuinVegetationGuard` 读取其模板+入口的联合包围盒并外扩 14 格，仍覆盖后来 `top_layer_modification` 放置/清冠的范围。

开发桥只读动作 `spirit_tree_probe` 接收 `minX/maxX/minZ/maxZ/step`（闭区间，跨度≤4096，步长 1–1024，总采样≤1024）。缺失完整 ±14 footprint 的点返回 UNLOADED，绝不补加载；返回原因直方图、合格群系直方图、按群系分组、每点基座/地形高差/首个阻挡方块、附近已有树心，以及整个矩形内按区段调色板扫描的精确树心列表 `hearts`。可用：

```sh
python3 scripts/probe-spirit-trees.py r100-probe-my-world --min-x=-224 --max-x=224 --min-z=-224 --max-z=224 --step=64
```

探针不放置树、不改区块票据；要加载区域，另在专用开发世界执行 forceload 或飞行。512×512 要分成四个 256×256 象限，避免单条命令的 256 区块上限。`command` 的 success 不能代替命令返回码；nearest/place/forceload add 要核对 `result > 0`。例外：原版 forceload remove all 固定返回0，应以票据或保存后的 Forced 列表确认清理。`hearts` 仅统计已加载区块，未加载部分不能作为“无树”；在全新世界未人工放树时才可用它计自然树。

`SpiritTreeFeature.preflight` 保留 place 的顺序和规则，失败不暴露部分写入计划；`preflightLoaded` 先用 getChunkNow 拒绝缺失区块。原因依次为 RUIN_GUARD、NOT_DRY、RELIEF、FOOT_BLOCKED/FOOT_UNGROUNDED、BUILD_HEIGHT/CLEARANCE_LOG/CLEARANCE_OTHER，全部通过为 OK。NOT_DRY 包含水、非坚固地基、原木/叶/方块实体；BUILD_HEIGHT 也包含生成区域写入边界。探针运行于装饰完成的 ServerLevel，与 `/place feature` 相同，不执行仅 WorldGenRegion 生效的遗迹保护；它的通过率**不是** local_modifications 时刻的天然尝试成功率。原因是遇到的第一个失败，不代表后续检查一定通过。

全冠最低叶片 y 的极差≥4、众数占比≤35%；另对水平半径≥6 的外圈约束极差≥4、众数占比≤30%，防止内冠空腔掩盖外围平底。测试含 64 个位置种子及截图基座 `(3000,120,3000)`；直方图输出 `build/reports/spirit-tree-undersides.csv`。

R100 最终实拍已通过：seed100101 新世界原点周围512×512精确查到3棵自然树，冠底错落、冠顶收圆、白桦林入口通畅和夜景均由Claude确认。客户端使用上文 llvmpipe + DISPLAY=:0 标准命令；收尾先 `forceload remove all`，再 `leave_world`、`quit`。证据 `evidence/r100-rework-natural-v001/`；前两轮 `r100-rework-accept-v001/v002`、旧密度 `r100-rework-density-v001/` / `r100-rework-acceptance-rate-v001/`、探针 `r100-preflight-loaded-v001/` / `r100-preflight-dense-v001/`、新世界 `r100-early-density-v002/`；完整索引见 TESTING。

单人服务器没有 `save-all` 命令。测试快照须先暂停，再调用 `save_world`，等待原版 `saveEverything` 完成强制写盘后复制，并检查 SHA-256 和关键区块内容。仅暂停触发的自动保存不保证所有异步区块写入已完成。`command` 对单人服务器没有注册的命令直接报错；只读查询的短暂超时最多重试两次，并记录在证据中，操作请求不自动重发。

遗迹模板的可编辑源文件是 `scripts/art/build_ruins.py`。使用独立虚拟环境安装 `scripts/requirements-art.txt`（锁定 `nbtlib==2.0.4`），已有环境可直接复用：

```sh
python3 -m venv ~/.local/opt/orimod-art-venv
~/.local/opt/orimod-art-venv/bin/python -m pip install -r scripts/requirements-art.txt
~/.local/opt/orimod-art-venv/bin/python -B scripts/check-ruins.py
```

`check-ruins.py` **默认只读**：先在系统临时目录生成，再检查三份 NBT/路线契约，并比较全部 17 个产物（NBT、布局、世界生成配置、神龛模型和方块状态）。NBT 比较解压后的原始内容，忽略 gzip 头；JSON 比较解析后的结构，忽略空白和对象键顺序。内容缺失、损坏或不一致时列出路径并以非零状态退出，成功或失败都不回写仓库。输出的 SHA-256 用于记录现有文件字节，不作为默认比较条件。

只有显式执行以下命令才更新仓库资源；先验证生成结果，再写入有字节差异的文件，未变化文件保留修改时间：

```sh
~/.local/opt/orimod-art-venv/bin/python -B scripts/check-ruins.py --write
git diff -- src/main/resources
~/.local/opt/orimod-art-venv/bin/python -B scripts/test_check_ruins.py -v
```

生成器不再有隐式仓库输出路径；独立预览可用 `python -B scripts/art/build_ruins.py --output-root /tmp/orimod-ruins-preview`，在指定目录下创建 `src/main/resources` 树。NBT 固定压缩级别 9、`mtime=0`、OS 字节 `0x0a`，保留初始提交 `c8b254e` 的 gzip 头；OS 字节是历史封装元数据，不代表当前操作系统。Python/zlib 的压缩输出若发生变化，默认内容校验仍可通过，逐字节复现需再检查 `--write` 的资源 diff。

布局中的后续机关由 `RuinKind` 读取、`RuinPiece.placeCells` 在模板放置后添加，因此生成器把它们写入布局契约，不烘焙进基础 NBT。坐标与模板共用地表遗迹的 4 格基础偏移；风口按矩形生成，宝库门由侧墙范围生成，灵砂奖励位于隧道末端外两格的洞室。2026-10-20 根据只读 `git log` / `git show` 补回以下来源，神龛模型按遗迹主题选择原创 top/side 并共用 bottom：

| 来源提交 | 原资源变更 |
| --- | --- |
| R44 `57bcc97` | 三座遗迹的生命/能量核心基座 `cells` |
| R46 `29e0d1b` | 森林与溪流滑翔间隙的 `updrafts` |
| R50 `ffd4841` | 洞穴竞技场的 `boss_altar` |
| R57 `eef6b65` | 洞穴 `keystones`、`vault` 和宝库内能量核心 |
| R59 `c7a6351` | 溪流滑翔路线下的 `gloom_water` 范围 |
| R62 `0fc04c1` | 三座遗迹入口旁的 `map_stone` |
| R64 `e845034` | 洞穴 `spirit_sand` 隧道范围 |
| R66 `882ed8d` | 灵砂隧道末端洞室的生命核心 |
| R70 `2dc5bb4` | 森林后部的 `storm_altar` |
| R76 `516bb93` | 三种神龛的原创纹理引用，替换原版重生锚/深板岩纹理 |

实际路线使用 `verify-ruin-routes.py`，边界使用 `verify-ruin-boundaries.py`，自然生成使用 `verify-natural-ruins.py <构建标识> <orimod_dev_世界名> <种子>`。这些游戏内验证脚本会修改专用测试世界；不能用于用户生存存档。上述纯 Python 校验与回归测试不启动 Minecraft 或 Gradle。

这些接口只在开发模组中存在。正式入口使用核心、V/C/R/G 按键、鼠标和原版物品栏，不依赖开发命令。具体已验证范围分别见移动、成长和战斗检查记录。

## R106 腐化地带与净化验收

客户端与Gradle互斥；启动仍用 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY="$HOME/.Xauthority" ./gradlew runDevtoolsClient`。本轮实现期间没有启动客户端。

- `/place feature orimod:blighted_grounds ~ ~ ~`：在开阔、干燥的泥土/草地/沙/黏土地面生成；整片预检失败则无写入。放置原点的Y取该XZ地表高度，不用于悬空放置；周围±14区块必须已加载。既有森林可能因原木拒绝，自然生成采用 `local_modifications` 在本区块原版植被之前。
- `/ori blight nearest [radius]`：默认256，允许1–1024，只查加载区块、区段调色板预筛，返回最近尚存核心坐标/距离；点击建议tp到核心北侧3格，地形是否安全需自行确认。没有核心时 result=0；devtools command 的 success 不能代替 result。
- `/ori unlock all`、`/give @s orimod:spirit_hammer`、`/ori awaken`、`/ori form on`及正常能力输入可用于战斗验收；核心准确血量40，蓄力灵焰12、灵锤10、光爆8、灵矛6、灵闪每20持续tick4。灵闪持续保活由现有按键实现，不能只发一次包就期待一直净化。
- 荆棘复验：开启精灵形态和同伴，在玩家3格内放 `gloom_thorns`，等待后仍应存在；走入荆棘30tick内掉血（2原始伤害/15tick，护甲可能减伤）。自动灵焰只在无敌人时选取可见的灵光容器，命中敌人也不清荆棘；主动灵刀、普通箭/灵弧箭、蓄力灵焰、光爆、灵锤、灵矛、下砸可清除，剪刀采集掉自身。徒手/普通挖掘、猛击和灵闪不能清除荆棘；核心依旧只吃上述五种有效能力。
- 核心摧毁后观察约30秒：土变草、枯木保留轴向变发光灵木、荆棘变短草/白花/空气、腐光水变清水。已有蓄力灵焰/光爆/灵闪也可能先净掉局部腐光水，这是原机制。

`spirit_state` 的 **server.blight** 查询玩家256格内最近的已登记地带，返回：

| 字段 | 含义 |
| --- | --- |
| `state` | `none` / `active` / `purifying` / `completed` |
| `center` | 核心世界坐标 `[x,y,z]`，none时不带 |
| `health` | 32格内最近尚存核心的精确血量；没有核心不带 |
| `elapsed` | 本波有效tick，0–600；全部待办区块都卸载时暂停 |
| `processed` / `total` | 已检查/总生成坐标；完成后旧坐标表已清理，两值为0 |
| `participants` | 本波固定参与者数，≤16；完成后清理为0 |
| `defenders` | 当前所属喷吐者UUID数，≤3，含未加载者 |
| `pendingRewards` | 当前维度尚待补发人数总计；领取/删除玩家后条目删除 |
| `lastReads` / `lastWrites` | 本维度最近tick的检查/写入数，分别≤128/32 |

完成中心独立按区块索引提供32格球形潮夜庇护，核心消失后仍有效；不依赖原灵灯/树心缓存。`/ori tide start` 仍要求夜间、非和平和doMobSpawning，站在已净化中心观察多波应为0；32格边界包含，超出不保护。核心注册已有noLootTable，能力摧毁使用removeBlock不掉物品，创造栏物品保留；截图里的旋转紫色立方体也可能是存活的喷吐者，可用nearby_entities区分实体类型后判断。参与者取触发时32格内存活、非旁观者，最多最近16人；奖励不要求继续留在范围内。离线登录补发，不在该世界playerdata中的离线UUID跳过。补发使用个人回执防重复，完成记录仅保留中心；创造模式重新放核心不能刷地带奖励。手放核心没有生成地带元数据，只做战斗测试。

纹理复现：`python3 -B scripts/art/build_blight_textures.py --check`；9张PNG，其中5种核心裂纹各由12个16×16帧及mcmeta组成，48tick循环插值，光照恒定不引发重算。`python3 -B scripts/export-spirit-fx.py --check`校验PURIFY源文件与发行定义。未新增结构资产，不需要重写遗迹。


## R108 意志之光与复苏验收

客户端与 Gradle 互斥；图形启动固定为 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY="$HOME/.Xauthority" ./gradlew runDevtoolsClient`。R108 基础功能已由 Claude 完成 18 项真实输入验收；视觉打磨使用同一 llvmpipe 环境补拍。

仅 OP（权限 2）调试命令：

```text
/ori wisp give heart [player]
/ori wisp give wing [player]
/ori wisp give earth [player]
/ori wisp give all [player]
/ori wisp clear [player]
/ori restoration status
/ori restoration reset
```

省略玩家时作用于自己；沿用 `/ori` 的玩家选择器支持。give 直接设置收集位，用于仪式验收；clear 取消该玩家当前会话并清空三道光/待领实体资格。reset 清世界复苏标志、取消会话、递增回执版本，在线玩家立即清个人完成/结局台词记录与成就，离线玩家登录后清理；保留已收集的三道光、已得灵光和原灵树赐福记录。已加载灵叶渐进恢复未 BLOOMED 与光照 9，未加载灵叶下次加载时恢复。树心原有 BLOOMED 同时代表 R100 赐福，reset 不撤销树心的赐福外观，避免把合法已领树恢复为未领观感；不影响仪式重试或个人回执。新生成和新放置灵叶也遵守当前世界标志。

普通流程：击败两位首领后，在祭坛上方各出现属于参与者自己的光实体；心之光仍留在祭坛，R54 逃离后可返回拾取。每次击败按原首领结算范围内存活、非旁观玩家发放，已收集不再出现，已有待领保留原祭坛而不转移。第三片净化按 R106 固定参与者快照逐人计数，离线补奖时同样计数，光留在第三片核心处。实体不掉物品、不自动消失、不受伤，个人待领账本可恢复缺失实体；只在实体已经加载的区块修复，绝不为待领光加载区块。面板第四页显示三道光的剪影/已收集图标、来源、个人净化计数与待领维度/坐标，不覆盖指引 HUD。

集齐后，精灵形态右键已向自己赐福过的树心（未领过先领一次）。开场由服务端在入口外 4–6 格选取合法站位（相对自然树心向北 7–9 格，左右/高度允许各偏移 1 格）。检查已加载区块、坚实安全地面、完整移动通道和目的地生物碰撞；找不到就留在原地，不强载区块、不穿过板根。服务端保留 yaw/pitch；客户端仅第一人称开场约20tick平滑引导至树冠下沿附近，任何原始鼠标位移立刻终止并在本会话不恢复。第三人称不引导，不切相机类型。由 MouseHandler.onMove/turnPlayer 在原版灵敏度/电影镜头平滑前处理，中途可自由转头。15 秒/300 tick 仪式保留鼠标视角，开始包携带服务端会话原点的 double 坐标，客户端立即采用同一原点锁位（不自行记录预测位置）；Esc 仅在会话内截获并立即走同一结算。结束包、断线及客户端会话租约失效均解除截获；普通状态 Esc 仍打开暂停菜单。断线、死亡、换维度、树心消失取消仪式，不消耗三道光，不发奖励，可回来重试。称号成就「守光者」、500 灵光及「萤：光回来了。森林会记得你。」每人只结算一次；之后其他玩家也可完成自己的结局。

分镜：0 秒树回应/锁位；1–2 秒三光显现并环绕；3/4/5 秒心、翼、地之光依次飞向树心；6 秒根部扩散光环；7–11 秒树冠从低到高实际 BLOOMED、「萤」绕树；12 秒收束；13–14 秒冲天光柱与轻微屏幕提亮；15 秒结算/淡出。Esc 不打开新 Screen；开场引导结束或被鼠标打断后完全交还视角。仪式途中局部灵叶变化取消时可回退，首位完成后全世界的已加载树逐批 BLOOMED，未加载树在加载时处理。

世界效果存在主世界 SavedData（覆盖整个存档的各维度）：潮夜每波数量 `ceil(原波数量 / 2)`，只出现喷吐者，`maxNearby` 配置不变。加载事件只记区块，服务端按区段 `maybeHas` 预筛；所有维度共享每 tick 最多 256 次方块写入、32768 格扫描和约 2ms 软时间片。叶片切换使用 UPDATE_CLIENTS，亮度 9→12，reset 反向 12→9；树心原本已是光照15，不进一步提高。所有待办只保存运行时加载区块坐标，存档里的世界标志保证重启恢复，不扫未加载世界。

`spirit_state`：

| 路径 | 字段 |
| --- | --- |
| `server.wisps` / `client.wisps` | `mask`（心1/翼2/地4）、`purifications`（个人0–3） |
| `server.restoration` | `active`、`remaining`（0–300）、`completed`（个人当前回执）、`restored`（全存档）、`epoch` |
| `server.restoration.pending` | `[{kind, dimension, position:[x,y,z]}]`；待领实体来源坐标，最多三条 |
| `server.restoration` 更新预算 | `bloomChunks`、`bloomWrites`、`bloomMs`（最近批次实耗时） |
| `client.restoration` | `active`、`remaining`、`restored`、`completed`、`session`、`second`（演出秒数）、`sparkPending`（队列）、`sparkLast`（本 tick 提交粒子数）、`sparkEmitted`（本客户端世界累计提交数）、`viewGuide:{active,cancelled,targetYaw,targetPitch}` |

复现资源：`python3 -B scripts/art/build_spirit_tree_textures.py --check`、`python3 -B scripts/art/build_wisp_textures.py --check`、`python3 -B scripts/export-spirit-fx.py --check`。新增源文件 `art/source/effects/spirit/wisp_collect.json`、`art/source/effects/spirit/restoration.json` 必须与导出定义一起提交。FX 通道9、同伴通道9、新复苏通道1；ProgressAction 仍1，Esc 的目标使用32字符会话令牌，不增加原字段长度。


R108 视觉打磨：盛开叶片保留原来的 cutout 孔隙，保留高饱和青色底，点缀四朵2×2近白小花与淡金花心；盛开模型shade=false/ambientocclusion=false，解除方向性压暗以显出满亮度；三光和图标统一心金、翼紫、地青绿，入树拖尾同色、吸收后逐个淡出。光柱用独立宽横向渐隐贴图，亮芯 1.8 格、外晕 3.6 格、高 48 格，12 段连续向上淡出，避免把原能量细线贴图横向拉宽后仍只有细线。

客户端仅在真实方块更新的灵叶 `BLOOMED: false → true` 时排队一次闪光；初次区块载入不重放。全客户端每 tick 按流畅/均衡/精细最多 8/16/24 个，受高级粒子开关及原版粒子设置约束；最多排队 2048 个、160 tick 过期，离线/换世界清空。叶片本身仍使用原共享写入预算与光照 9→12，无新网络包或通道升级。

固定机位补拍：打开已有 `orimod_dev_r108`（树心 4000,120,3998）后运行 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY="$HOME/.Xauthority" python3 -B scripts/capture-r108-polish.py <唯一证据名>`。脚本使用真实右键，原始锚点 (4000.5,120,3996.5)，`review_camera follow=false`，三个偏移 (-4,2.5,-5)/(-10,6,-18)/(-12,4,-24)，目标高度分别 1.3/8/10；固定夜晚、FOV70、关闭 shader，另拍不动鼠标时第1/5/9/13秒的第一人称，并通过XTest真实鼠标位移走GLFW/MouseHandler验证缓动中断。结果记录实际时刻、相机设置、玩家位置和粒子计数；清除 forceload、恢复界面和相机。它会 reset 该测试世界的复苏数据，只在验收存档使用。旧参照为 `evidence/r108-capture-v001`，不需要替换工作区资源；旧图未保存完整相机目标高度/帧时刻，因此只能比较指定偏移和同棵树，不能声称逐像素同帧。

新增确定性光柱贴图源脚本 `scripts/art/build_restoration_texture.py`，用 `--check` 校验 `textures/misc/restoration_column.png`。本次 polish 没有新增 `art/source` JSON；前述两份 R108 FX JSON 已在 fbb3c90 中，22 个 FX 导出不变。

实拍色彩回归见 `evidence/r108-polish-after-v003/canopy-metrics.json`：Sodium下盛开模型关闭方向压暗/AO，树冠ROI饱和度+12.2%、HSV亮度+49.2%，没有增加光照引擎写入或neoforge_data顶点光照字段。


### R115 B 内部生成与指引

`GreatTreeInterior` 是对已验收 `GreatTreeShape` 的确定性建筑覆盖层；世界生成逐区块读取同一个原点相对坐标，内部不另开写入队列、不跨clip写邻区块。`scripts/art/build_great_tree_interior.py --check` 和 `build_great_heart.py --check` 检查原创布局/根笼心部模型源与包装，外部树形/树皮的原检查脚本仍须通过。

`great_tree_heart` 用于复苏；`spirit_tree_heart` 仍是先祖赐福核心，注册ID与旧奖励键不变。旧复苏世界效果及个人回执保持兼容，未完成者带三光到新大厅即可，无先祖赐福前置。地图石普通使用仍找遗迹；潜行使用通过持久唯一树址指向大树根部，不生成目标区块。Companion协议10、Guide协议3拒绝旧版本。

Devtools `great_tree_state` 增加世界坐标 `entrance/heart/reward/ascent/descent` 和 `foliage.loadedLeaves/bloomedLeaves`；仅在手动查询时扫描已加载、调色板含灵叶的区段。`capture-great-tree-interior.py <新目录> [seed]` 从根部用真实按键走完盘梯与根洞，保留逐步位置和视频。客户端仍必须 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority`；与全量Gradle互斥。


R115 B 内部第二版：已通过的 `GreatTreeShape` 与外部树皮/光纹不变；`GreatTreeInterior` 独立覆盖短根拱、三圈台阶、九窗/三顶井、拱顶大厅和根洞。调色板12/13/14是内根木/暗细纹/亮度14根结，不能替换外部WOOD/VEIN。`GreatSpiritRootBlock.light` 控制真实光照，楼梯每六步放置根结；生成仍裁剪在当前区块。内根木同样禁止潮夜落点。新树心采用共享Sanctuary BE，以一个方块保持碰撞和交互，GLB根笼/晶体仅视觉，渲染AABB覆盖整体。新资产复现：`python3 scripts/art/build_great_tree_root_textures.py --check`、`build_great_tree_interior.py --check`、`build_great_heart.py --check`；源目录均为各自v002。客户端拍摄必须 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0 XAUTHORITY=$HOME/.Xauthority`，并与全量Gradle互斥。

R115 B 切片写入使用 `Block.UPDATE_CLIENTS`（2），不包含邻居更新。在正常 FEATURES 的 `WorldGenRegion/ProtoChunk` 中，树心BE先保存DUMMY占位，成品区块才实例化；GLB不在生成线程读取。ProtoChunk在INITIALIZE_LIGHT之后写入才进入逐格光照检查，正常新生结构的照明由后续阶段初始化。不能将该说明推广到OP在已完成区块中的强制放置：此时必须正常更新光照。第二版实测(40,23)10443写/22.42ms，日志未做BE/光照分项归因，保留此慢切片而不额外改不明标志。实拍与时序索引见 `evidence/r115-b-interior-v002/checkpoint.json`。
