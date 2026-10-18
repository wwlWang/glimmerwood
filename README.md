# OriMod

Minecraft 1.21.1 / NeoForge 的单人开发项目，包含 Ori 形态、移动能力、刀弓、成长面板与三类遗迹挑战。当前首版尚未完成：正式角色已有可编辑源文件和部分实机验证，特效声音、完整无命令流程、安装与总验收仍待完成。

按用户决定，仅供自己玩，使用 [私有仓库](https://github.com/wwlWang/oriMod) 备份。模型署名与依赖许可见 [THIRD_PARTY.md](THIRD_PARTY.md)。当前进展见 [WORK_STATUS](docs/WORK_STATUS.md)，79 项验收结果见 [ACCEPTANCE_STATUS](docs/ACCEPTANCE_STATUS.md)，审查修复见 [AUDIT_FIXES](docs/AUDIT_FIXES.md)。

云端 Agent 接手请先读 [CLOUD_HANDOFF](docs/CLOUD_HANDOFF.md)，其中包含构建步骤、建议任务和 R14 未完成的测试边界。

## 从干净检出构建

需要 Git、Git LFS、JDK 21。不要使用 GitHub 的源码 ZIP；它可能只有大文件指针。

```sh
git lfs install
git clone https://github.com/wwlWang/oriMod.git
cd oriMod
git lfs pull
./gradlew build --console=plain
```

Windows 使用 `gradlew.bat build --console=plain`。本机若遇到 Java loopback socket 错误，可运行 `./scripts/gradle.ps1 build --console=plain`；此包装只为当前进程设置临时目录。首次构建仍需要下载 Gradle、Minecraft 和 NeoForge 工具链依赖。

锁定的 GemRender 和 Flywheel 编译接口保存在 `libs/`，由 LFS 管理。构建前校验 `dependencies/prepared.json`，缺失或损坏时明确失败。其余开发画面依赖按 `dependencies/lock.json` 从上游下载，不上传到本仓库。

产物为 `build/libs/orimod-0.1.0-SNAPSHOT.jar`。当前客户端至少需要 GemRender 0.1.17 和其内置的 Flywheel 1.0.6；仅复制 OriMod JAR 还不是完整安装包。干净游戏实例安装仍待验收。

## 开发与检查

| 项目 | 固定版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java | 21 |
| Gradle Wrapper | 9.2.1 |
| ModDevGradle | 2.0.148 |
| Parchment | 1.21.1 / 2024.11.17 |
| 当前角色制作 | Blender 5.1.2；4.5.14 LTS 已完成兼容重导出数据对比，游戏内复查待完成 |

`runDevtoolsClient` 启动包含可选画面库与本地测试接口的实例；接口仅在显式开发启动中开启，发行 JAR 不含开发工具。操作和录制方法见 [DEVELOPMENT](docs/DEVELOPMENT.md)。

运行开发客户端前先用 Python 3 执行 `python scripts/sync-dependencies.py`，下载并核验可选依赖。该脚本适用于 Windows、Linux 和 macOS；原 PowerShell 脚本保留供本机重建渲染库使用。

`./gradlew test runGameTestServer` 运行规则与存档单元测试，以及独立的无界面服务端回归。当前 262 项 JUnit、146 项 GameTest 已在 Windows 和 Linux 通过，覆盖范围与报告路径见 [TESTING](docs/TESTING.md)。真实客户端 Python 脚本继续用于输入同步和视觉验收；无界面测试不能替代这些检查。

遗迹支持四向旋转，三种子共 225 次自然世界定位的 1500 格覆盖率均达到本轮 90% 目标。路线布局保存在 `src/main/resources/orimod/ruin_layout/`，属于随 JAR 发布的内部契约，不支持数据包覆盖；世界生成配置和群系标签仍使用原版数据包机制。

## 文件与恢复

- `src/`：模组与独立开发源码集。
- `art/source/`：当前身体与手部 v010、技术样例 v008 的 Blender 工程、导出物与清单；大文件使用 LFS。
- `models/`：当前角色的下载原件和来源记录；未采用的原件留在本地。
- `libs/`、`dependencies/`：可恢复的依赖包与校验记录。
- `docs/`：计划、当前状态、审查修复和验收记录。

运行存档、原始录像、证据、历史资产候选、外部工具与反编译源码不上传 GitHub。本地完整备份保存到仓库外，并附逐文件 SHA-256 清单。阶段提交和标签标记可恢复位置；标签本身不等于质量验收通过。
