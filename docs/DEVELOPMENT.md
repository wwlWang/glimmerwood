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

Linux 使用 `./gradlew runDevtoolsClient --console=plain`，需要可用图形环境。云端无界面构建和交接步骤见 [CLOUD_HANDOFF](CLOUD_HANDOFF.md)。

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

## 角色变形样例

运行 `scripts/art/build_deformation_sample.py` 时使用全新的后台 Blender 进程和 `--factory-startup`。脚本验证输入模型哈希，生成独立的 `.blend` 修订文件和 GLB，不修改下载原件。

源文件记录位于 `art/source/deformation-sample/manifest.json`。执行 `python scripts/check-assets.py` 检查导出文件的哈希、关节数量、渲染器矩阵槽上限、权重和动画名称，也检查主资源中的玩家 GLB 与贴图是否和已验证样例一致。这个检查通过只代表导出契约有效，视觉质量仍需 Blender 与游戏内验收。

在兼容世界中启动角色和循环微粒后，`python scripts/verify-rendering.py <唯一构建标识>` 执行五次资源重载、窗口尺寸切换及 60 秒性能采样。数据写入 `evidence/<构建标识>`，脚本不会自动判定截图的视觉质量。性能采样期间不录屏。

## 玩法操作与记录

每次重启后用 `python scripts/open-dev-world.py <世界名>` 等待首次资源加载结束，再打开世界。不要在 NeoForge 加载遮罩仍在时发出打开世界请求。

`spirit_state` 同时返回客户端和集成服务器形态、移动、战斗信息；`progress_state` 返回成长、能量和物品栏。`record_motion` / `save_motion` 记录两端每刻状态。`input_script` 的 `steps` 由相对 `at`、按键名 `key`、按下状态 `down` 组成；也可附 `action` 对象，限定为命令、换槽位、背包、暂停、关闭界面、切形态和转向。定时操作报错记入 `inputScriptError`，后续步骤停止。

`ruin_state` 返回挑战、恢复标记、附近已加载的真实结构起点与有界搜索状态。`use_block` 走原版物品使用入口，`gui_click` / `gui_key` 操作当前游戏界面；`container_state` 只读合成与物品栏槽位。`focus_game` 仅请求本客户端窗口焦点，测试仍使用正式失焦保护。`server_hitch` 仅限集成服务器、1–600 毫秒，可作为定时脚本动作，复现服务器停顿下的输入行为。

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
