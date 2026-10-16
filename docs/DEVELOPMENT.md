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

先执行 `scripts/sync-dependencies.ps1` 和 `scripts/build-renderer.ps1`，然后执行：

```powershell
.\scripts\gradle.ps1 runDevtoolsClient --console=plain
```

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

这些接口只在开发模组中存在。正式入口使用核心、V/C/R/G 按键、鼠标和原版物品栏，不依赖开发命令。具体已验证范围分别见移动、成长和战斗检查记录。
