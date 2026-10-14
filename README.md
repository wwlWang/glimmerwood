# OriMod

Minecraft 方块世界中的 Ori 形态、跑酷、战斗与风格化画面模组。首版范围与验收标准见 [项目计划](docs/PROJECT_PLAN.md) 和 [验收计划](docs/ACCEPTANCE.md)。

目前已建立开发工程，尚未实现变身、技能、动画或光影。现有模型正在检查，未打入模组。

## 开发环境

| 项目 | 固定版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java | 21 |
| Gradle Wrapper | 9.2.1 |
| ModDevGradle | 2.0.148 |
| Parchment | 1.21.1 / 2024.11.17 |
| Mod ID / Java 包 | `orimod` / `cn.wwl.orimod` |

安装 JDK 21，确认 `java -version`。在项目根目录的 PowerShell 中执行：

```powershell
.\scripts\gradle.ps1 build --console=plain
.\scripts\gradle.ps1 runClient --console=plain
```

产物为 `build/libs/orimod-0.1.0-SNAPSHOT.jar`，开发客户端目录为 `run/`，日志在 `run/logs/latest.log`。客户端中的 `OriMod common setup ready` 与 `OriMod client setup ready` 日志表示两个入口已加载。

也可以在普通终端使用 `gradlew.bat build` / `gradlew.bat runClient`。`scripts/gradle.ps1` 仅为本次进程设置 Windows Java socket 临时目录，解决此机器受控终端出现的 `Unable to establish loopback connection`；执行后恢复原来的环境变量，不改系统、代理或全局 Gradle 设置。

IDEA 中打开根目录，按 Gradle 工程导入，Gradle JVM 选择 JDK 21。首次导入和构建会下载依赖。当前没有 GameTest 用例，`gameTestServer` 只是预留运行配置，不能将 `test NO-SOURCE` 当作游戏功能通过。

## 文件组织

- `src/main/java/cn/wwl/orimod/`：公共入口；`client/` 为客户端入口。
- `src/main/templates/`：构建时展开版本号的模组元信息。
- `docs/`：项目计划、验收标准和初始化记录。
- `models/`：用户提供的原始模型与资产检查记录。
- `scripts/`：本地开发辅助脚本。

原始模型和压缩包保留在本地，暂不纳入 Git；来源记录可入库。确定生产资产后再选择大文件存储方案。`.gitignore` 同时排除构建缓存、开发存档和大体积验收证据。

本仓库使用本地 `main` 分支，未配置远程仓库。MDK 来源和版本见 [初始化记录](docs/BOOTSTRAP.md)。模板许可保留在 `TEMPLATE_LICENSE.txt`；项目源码许可暂用 MDK 的默认 `All Rights Reserved`，第三方资产各自记录许可。
