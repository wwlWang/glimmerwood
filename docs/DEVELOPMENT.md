# 开发与验收工具

代码按职责命名。`P1`、`P2` 等阶段编号只用于计划和验收记录，不进入包名、类名、资源 ID 或构建任务。样例使用明确的 `Preview`、`Sample` 名称；正式玩法类按实际行为命名。

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

客户端就绪后，以下命令读取状态，不会输出访问令牌：

```powershell
.\scripts\devtools.ps1
.\scripts\devtools.ps1 -Action '{"op":"open_world","name":"orimod_dev_compatibility"}'
.\scripts\devtools.ps1 -Action '{"op":"screenshot","name":"character-preview"}'
```

开发世界名称使用 `orimod_dev_` 前缀。截图、录帧和连接信息位于 `run-devtools/devtools`；验收时将所需证据另存到 `evidence/<build-id>`，登记构建和环境。

录制使用 `record_video` 操作指定 `name`、`fps`、`seconds`，需要 PATH 中存在 FFmpeg。画面直接来自游戏渲染目标，以 RGBA 流交给编码器；无需桌面捕获。等待状态中的 `recording=false` 且 `pending=0` 后执行 `save_video`，再将返回的 `frames.json` 路径交给 `scripts/encode-capture.py`。缺失的采集时隙保持前一帧，不加速播放；采集时间戳和重复帧数量随清单保存。录屏与性能采样分开执行。

## R102「萤」台词验收

`{"op":"spirit_state"}` 的 `client.line` 包含 `id`（不含line/前缀）、`text`（当前客户端语言的完整文案）、`remaining`（剩余客户端tick）。未显示时为 `{"id":"","text":"","remaining":0}`。台词显示60tick，末20tick淡出；底行位于屏幕底部以上84像素，原版actionbar在68像素处，长英文向上换行，保留功能性消息。沿用“萤的引导提示”开关，关闭时不显示/响铃/脉冲；已发送的一次性记录仍由服务端保存，与原hint行为相同。

台词与hint共享200tick间隔，hint优先，只有一个待发条目，新覆盖旧；最大等待按台词设置，默认240tick（含boss_start），low_health为60tick，超过才丢弃。默认等待覆盖200tick间隔及下一次20tick扫描：新玩家第一次接近灵树收到hint后立即领取赐福，或刚收到遗迹机关hint便入场，台词应在间隔结束后的扫描说出并写记录，不必先清hint或去第二棵树。`/ori hints reset`只清hint、不清line记录；多个未读hint连续抢先仍可能令台词超时，低血量过时则有意丢弃。没有新增调试命令修改台词记录；重测一次性事件请使用新玩家/测试存档。

遗迹验收请使用自然生成结构（例如 `/locate structure orimod:forest_spring` 后前往实际入口）。已核对 Minecraft 1.21.1 的 PlaceCommand.placeStructure：`/place structure` 调用 placeInChunk 放置方块，但不登记区块的 structure starts/references，因此不能作为入场检测夹具；它的坐标参数也不保证最终结构原点与高度。GameTest 则明确建立并还原真实结构起点和引用。

触发点：三类自然遗迹实际包围盒首次进入；首领首次锁定玩家；翼母胜利；心脏死亡后的逃亡（escape覆盖同时排队的boss_defeated）；潮夜开始/结束；树心领取成功；生命比例由≥25%落到<25%；觉醒后主世界可见天空（同步高度图也无头顶遮挡）且日内时间进入≥13000。低血量在持续低血量时不会因为1200tick冷却结束而重说，必须恢复后再下降。首领每实例一场战斗，持久开战标记防止重锁/重载误触发；另一个新首领仍受该玩家6000tick冷却。

一次性记录为 rewards `line/<id>`，实际发送后写入。排队下线不保存，登录不会回放旧队列；以后时机再次满足可以再排队。换维度/离开精灵形态会清过时队列，换维度保留会话内冷却。遗迹每20tick仅查当前区块及已加载结构引用，三类记录全齐后跳过。

CompanionHintPayload协议3必须两端匹配；在协议版本握手之外，新格式也带0/3标记和kind，旧v2包、新v3包的交叉解码均明确拒绝。FX与潮夜协议不变。客户端仍用llvmpipe + `DISPLAY=:0`，和全量Gradle互斥；台词布局与实际触发由 `scripts/verify-r102.py` 实拍验收。

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
