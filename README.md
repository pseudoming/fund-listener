# Fund Listener

**Fund Listener** 是一款专注于「隐私安全」与「盘中实时估值」的个人基金追踪全栈应用。

✨ **核心特性**：
- 📸 **截图解析**：集成本地离线 OCR，无缝读取支付宝持仓图片。
- 📈 **盘中估值**：直连金融公开数据 API，交易时间内实时测算持仓收益。
- 🛡️ **隐私至上**：所有数据处理均在设备本地完成，彻底杜绝资产信息外泄。
- 🛠️ **跨平台架构**：基于 Kotlin 构建核心，提供现代 Web 界面与轻量 JVM 后端，并为后续 Android 客户端预留了完整基础。

## 💡 开发初衷 (Motivation)

项目的诞生源于一次“抱怨”：支付宝下架基金实时估值后，家人们盘中查看收益极不方便。恰逢想体验 **Vibe Coding**，于是便动手做了这个工具。

🤖 **纯血 Vibe Coding**
- 从零到一，全程由 AI 自动编写与组织。
- **0** 手动代码调整，沉浸式体验 AI 编程的魅力。

🔄 **核心工作流**
1. **上传截图**：读取支付宝持仓图片。
2. **提取明细**：离线 OCR 提取数据。
3. **盘中估值**：结合天天基金 API，实时测算交易日收益。

🔒 **跨平台与绝对本地化**
为了打包成 Android APP 安装给家人，项目选用了 Kotlin 跨平台架构。对于敏感资产数据，我们做到：
- **绝对数据安全**：本地（JVM/Android）完成所有 OCR 解析、计算与存储。彻底告别云端 OCR，数据 **100% 离线**。
- **极度克制网络**：仅有两个对外的 GET 请求通道：
  1. 🌐 **天天基金 API**：查基金盘中估值与历史净值。
  2. 🌐 **腾讯财经 API**：看大盘指数与股票行情。
  绝不上传任何截图、金额或份额信息。

💌 **欢迎交流**
项目完全开源，欢迎随意下载体验。有问题或建议？随时邮件交流：[roamingchou@gmail.com](mailto:roamingchou@gmail.com)

## 🏗 架构说明

本项目采用模块化工程结构：

- **`shared/`**：核心业务逻辑与 Ktor Server 引擎层。包含：
  - **本地 OCR**：集成 `RapidOCR` 实现完全离线的图片文字识别。
  - **网页解析**：集成 `Jsoup` 解析抓取到的网页内容。
  - **持久化存储**：使用 SQLite 进行本地数据落盘（Android 端由 Room 承接）。
  - **网络与依赖注入**：Ktor (Server/Client) + Koin。
- **`jvm-app/`**：Ktor 后端的 JVM 启动入口。负责在本地开发阶段或服务端部署时拉起 `shared` 中的服务逻辑。
- **`android-app/`**：Android 移动端应用。
- **`frontend/`**：Web 端可视化界面，提供完善的用户交互，并配套了 Playwright 端到端（E2E）自动化测试。

## 🚀 快速启动

### ⚠️ 测试数据准备
本项目部分模块包含针对 OCR 的测试脚本与 Playwright 自动化测试。出于隐私保护原因，所有真实的测试截图均已被排除在代码库之外（`test-data/` 目录被 Git 忽略）。
**如果你打算进行本地开发并跑通所有的测试，请务必先行准备：**
1. 在项目根目录手工创建 `test-data/` 文件夹。
2. 在里面放入你个人的基金截图图片（例如用于测试解析的 `.png`、`.jpg` 文件）。
> *说明：如果你不准备该目录，所有依赖本地截图的自动化测试脚本将会检测并自动跳过，以免运行报错。*

### 🛠️ 2. 依赖环境
- **JDK 23**：编译 Kotlin/JVM 及 Android。
- **Node.js**：构建和运行 `frontend` 模块。
- **Android SDK**：构建 Android 客户端。

### 🖥️ 3. 启动后端 (JVM)
```bash
./gradlew jvm-app:run
```
*(注：首次启动会在 `jvm-app/` 生成本地测试数据库 `fund-listener.db`)*

### 🌐 4. 启动前端 (Web)
```bash
cd frontend
npm install
npm run dev
```

### 📱 5. 编译 Android
```bash
./gradlew android-app:assembleDebug
```
*如需打 Release 包，请参考根目录的 `keystore.properties.template` 配置本地签名。*

## 🛡️ 质量保障与测试
项目根目录提供了前置物理卡口脚本 `delivery.sh`。在进行重要的功能修改后，必须运行该脚本：
```bash
./delivery.sh
```
该脚本会自动调用 `frontend` 的 Playwright E2E 回归测试，确保核心路由与拦截器逻辑未被破坏。
