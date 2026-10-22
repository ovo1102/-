# 本地文件完整性哈希校验工具（hash-check-tool）

[![GitHub release](https://img.shields.io/github/v/release/ovo1102/JavaHashChecker)](https://github.com/ovo1102/JavaHashChecker/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ovo1102/JavaHashChecker/total)](https://github.com/ovo1102/JavaHashChecker/releases)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://github.com/ovo1102/JavaHashChecker#%E7%8E%AF%E5%A2%83%E8%A6%81%E6%B1%82)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)](https://github.com/ovo1102/JavaHashChecker)

一个基于 Java Swing 的纯本地文件完整性校验桌面工具：对单个文件或整个文件夹递归计算 **MD5 / SHA-256** 哈希，多线程并发加速、结果保序展示，并支持一键导出 UTF-8 文本报告。运行时零第三方依赖，开箱即用。

## 功能特性

- **双算法校验**：支持 MD5（32 位）与 SHA-256（64 位）摘要算法
- **单文件 / 文件夹递归**：选择目录后自动递归收集全部普通文件，无权限子目录自动跳过
- **并发批量计算**：固定 4 线程池并发计算，4096 字节分块读取，内存占用恒定
- **结果保序**：界面结果行严格按输入顺序刷出，进度条单调推进
- **线程安全 UI**：计算在后台守护线程执行，全部界面更新经 EDT 调度，大目录校验时界面不卡顿
- **报告导出**：一键导出 UTF-8 编码 txt 报告，含文件明细与「成功 / 失败 / 总计」统计
- **失败不中断**：个别文件被占用或无权限时，该文件标记为失败并继续完成其余文件
- **全中文界面**：按钮、提示、状态栏均为中文，窗口自动居中并适配屏幕可用区域

## 快速开始

### 环境要求

| 项 | 要求 |
| --- | --- |
| Java 运行环境 | JRE / JDK **8 或更高**（构建实测使用 JDK 21.0.6） |
| 构建工具（仅从源码构建时） | Maven 3.9+ |
| 网络 | 运行不需要网络；仅首次 Maven 构建需联网下载依赖 |
| 操作系统 | Windows / Linux / macOS（纯 Java 实现） |

检查 Java 是否可用：

```bash
java -version
```

### 安装步骤

**方式一：下载 Release 成品（推荐）**

前往 [GitHub Releases](https://github.com/ovo1102/JavaHashChecker/releases/latest) 下载最新版 `hash-check-tool-x.y.z.jar`，双击或命令行运行即可，无需安装。

当前版本 **v1.0.0** 直链：

```text
https://github.com/ovo1102/JavaHashChecker/releases/download/v1.0.0/hash-check-tool-1.0.0.jar
```

下载后建议核对 SHA-256，确保文件完整未被篡改：

```text
bfb0d2b656e6966fba37fb3f12d86e36a96d3a949533d3aa01f810a7c77dcab0  hash-check-tool-1.0.0.jar
```

```powershell
# Windows
certutil -hashfile hash-check-tool-1.0.0.jar SHA256
```

```bash
# Linux / macOS
sha256sum hash-check-tool-1.0.0.jar
```

**方式二：从源码构建**

```bash
# 1. 克隆仓库
git clone https://github.com/ovo1102/JavaHashChecker.git
cd JavaHashChecker

# 2. 运行测试（可选，40 个用例）
mvn clean test

# 3. 打包，生成 target/hash-check-tool-1.0.0.jar
mvn clean package
```

### 配置方法

本项目为绿色软件，**无需任何配置文件**：

- 算法在界面下拉框中选择（MD5 / SHA-256）；
- 并发线程数默认固定为 4（源码中可通过 `HashCheckService` 构造参数注入调整）；
- 不写注册表、不产生系统服务，删除目录即完成卸载。

### 启动命令

| 平台 | 方式 |
| --- | --- |
| Windows（推荐） | 双击 `运行-哈希校验工具.bat`（自动以 javaw 启动，无黑色控制台） |
| Linux / macOS | `./run.sh` |
| 任意平台（命令行） | `java -jar target/hash-check-tool-1.0.0.jar` |
| Windows 无控制台 | `javaw -jar target\hash-check-tool-1.0.0.jar` |

## 使用示例

1. **单文件校验**：点击「选择文件...」→ 选择算法（如 SHA-256）→ 点击「开始校验」→ 结果区显示 `[1] 路径 | 算法 | 哈希值`；
2. **文件夹批量校验**：点击「选择文件夹...」→ 选中目录 → 「开始校验」→ 结果逐条追加、进度条显示 `n/总数`，完成后状态栏提示「批量校验完成」；
3. **导出报告**：点击「导出报告...」→ 选择保存位置 → 用记事本打开 txt，查看文件明细与统计行（`成功: N 失败: M 总计: N`）；
4. **清空结果**：点击「清空」恢复初始状态，可开始新一轮校验。

界面效果参见 [docs/images/gui-smoke.png](docs/images/gui-smoke.png)，完整操作指引见 [docs/05-部署手册.md](docs/05-部署手册.md)。

## 项目结构

```
文件哈希校验工具/
├── pom.xml                          # Maven 构建（release 8 / JUnit 5 / JaCoCo / 可执行 jar）
├── 运行-哈希校验工具.bat             # Windows 启动脚本
├── run.sh                           # Linux / macOS 启动脚本
├── docs/                            # 全套中文项目文档
│   ├── 01-需求规格说明书.md
│   ├── 02-概要设计说明书.md
│   ├── 03-详细设计说明书.md
│   ├── 04-测试计划与测试报告.md
│   ├── 05-部署手册.md
│   ├── 06-维护手册.md
│   └── images/gui-smoke.png         # 界面冒烟截图
├── src/main/java/com/hashcheck/     # 主代码（四层分包）
│   ├── model/
│   │   └── FileHashResult.java      # 不可变校验结果模型
│   ├── service/
│   │   ├── HashUtil.java            # 分块哈希计算工具
│   │   ├── FileScaner.java          # 文件/目录递归扫描
│   │   └── HashCheckService.java    # 固定线程池并发校验服务
│   ├── io/
│   │   └── ReportWriter.java        # UTF-8 报告导出
│   └── ui/
│       └── HashCheckGUI.java        # Swing 主界面（程序入口）
└── src/test/java/com/hashcheck/     # 单元 / 集成测试（40 个用例）
    ├── model/  service/  io/
```

分层依赖方向：`ui → service / io / model`，`service / io → model`，`model` 无依赖；`service` 层不引入 Swing。

## 贡献指南

欢迎提交 Issue 与 Pull Request。为保证代码库质量，请遵循 [docs/06-维护手册.md](docs/06-维护手册.md) 中的工程规范：

1. **分支模型**：从 `main` 切出 `feature/<名称>` 或 `fix/<名称>` 分支开发，完成后以 `--no-ff` 合并回 `main`；
2. **提交信息**：遵循 Conventional Commits 中文规范（`feat:` / `fix:` / `test:` / `docs:` / `refactor:` / `chore:`）；
3. **合并前检查**：`mvn clean test` 全绿（当前基线 40 个用例）、覆盖率不下降；
4. **代码要求**：公共方法补充中文 Javadoc；不引入任何运行时第三方依赖；保持 `--release 8` 字节码兼容；
5. **版本号**：遵循语义化版本（SemVer），发布时在 `main` 打 `vX.Y.Z` 注释标签。

## 常见问题（FAQ）

**Q1：双击 jar 没有反应 / 提示找不到 Java？**
系统未安装 JRE 8+ 或未加入 PATH。请安装后用 `java -version` 验证，或改用启动脚本（自带检测提示）。

**Q2：导出的报告打开是乱码？**
报告为 UTF-8 编码，请使用 Windows 10/11 记事本、Notepad++、VS Code 等支持 UTF-8 的编辑器打开；Windows 7 旧版记事本可能默认按 ANSI 解读。

**Q3：个别文件显示失败（FileNotFoundException / IO 错误）？**
文件在校验期间被移动、删除、独占占用或无读取权限。工具会跳过该文件继续完成其余文件，检查权限后可单独重新校验。

**Q4：超大文件夹校验会撑爆内存吗？**
不会。哈希按 4096 字节分块流式读取，内存占用恒定；报告写出亦为流式。

**Q5：窗口在高分屏下显示不全？**
v1.0.0 已对窗口位置做屏幕可用区域夹紧；如仍异常，可将显示缩放调整到 100%–150% 或最大化窗口使用。

更多问题请查阅 [docs/05-部署手册.md](docs/05-部署手册.md) 与 [docs/06-维护手册.md](docs/06-维护手册.md)。

## 许可证

Copyright (c) 2026 ovo1102

本项目为课程交付项目，**暂未附加正式开源许可证**。允许用于学习、教学与非商业用途；如需引用代码或二次分发，请保留出处并注明原作者。后续如需正式开源，建议按仓库 `docs/06-维护手册.md` 的版本规范补充 LICENSE 文件并升级次版本号。
