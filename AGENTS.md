# 基金监控系统 (fund-listen)

## 0. 沟通约束（对所有Agent生效）
- 所有回复使用中文，结论优先，不加废话
- 有多个方案时直接给推荐+理由，不让用户选择
- 承认判断失误时直接说，不辩解
- 每当在对话中承认"之前忽略了某个问题"或"下次应该..."，
  立即评估是否需要写进rule/skill/workflow，
  若是则在当轮对话结束前主动提示用户
- 每次对话结束前，主动检查Bug队列是否需要更新，
  有新发现或已修复的条目必须当轮同步写入AGENTS.md

## 1. 项目架构概览
- **后端**：JVM/Kotlin，端口 8080，SQLite 存储。
  - *存储路径*：默认在 `jvm-app/fund-listener.db`。
- **前端**：Vue 3 / Vite，端口 5173，WSL2 内运行。
- **外部依赖**：天天基金API（eastmoney/1234567）、腾讯财经API（qt.gtimg.cn），仅 GET 请求。
- **OCR**：本地离线 RapidOCR，无网络依赖。

### 1.1 模块职责（Gradle 多模块结构）

| 模块 | 路径 | 职责 | 依赖 |
|------|------|------|------|
| `shared` | `shared/` | 核心业务逻辑（client/service/repository/model/routes） | Ktor、SQLite、RapidOCR 等 |
| `jvm-app` | `jvm-app/` | JVM 桌面端入口（Application.kt + RefreshScheduler 启动） | `shared` |
| `android-app` | `android-app/` | Android 端入口（Foreground Service + Room 适配） | `shared` |
| 根模块 | `build.gradle.kts` | 仅声明 allprojects 配置，不含业务代码 | — |

> ⚠️ **禁止在根模块或 `jvm-app/src/` 中放置业务逻辑代码。**
> 所有跨平台共享的 client/service/repository/model/routes 代码必须放在 `shared/` 模块中。
> `jvm-app/` 和 `android-app/` 仅包含各自平台特有的启动入口和 DI 配置。

## 2. 核心数据模型

当前数据库主要包含以下核心表（数据类型主要依赖 TEXT 与 BigDecimal 在 Kotlin 层的转换）：

- **`fund_position`**（持仓表）：`fund_code` (TEXT), `fund_name` (TEXT), `total_shares` (TEXT), `total_cost` (TEXT), `created_at` (INTEGER), `updated_at` (INTEGER)
- **`fund_transaction`**（交易记录表）：`id` (TEXT), `fund_code` (TEXT), `type` (TEXT BUY/SELL), `shares` (TEXT), `nav` (TEXT), `amount` (TEXT), `fee` (TEXT), `trade_date` (TEXT), `note` (TEXT)
- **`fund_nav_history`**（净值历史表）：`id` (INTEGER), `fund_code` (TEXT), `nav_date` (TEXT), `nav` (TEXT), `acc_nav` (TEXT)
- **`fund_valuation_snapshot`**（估值快照表）：`id` (INTEGER), `fund_code` (TEXT), `snapshot_time` (INTEGER), `estimated_nav` (TEXT), `estimated_growth_rate` (TEXT), `weighted_pe` (TEXT), `weighted_pb` (TEXT)
- **`app_config`**（KV 配置表）：`key` (TEXT), `value` (TEXT)
- **`fund_valuation_rule`**（自定义估值引擎规则表）：`id` (INTEGER), `fund_code` (TEXT), `component_type` (TEXT), `target_code` (TEXT), `weight_percent` (REAL)
- **`all_funds`**（全量基金基础信息缓存表）：`code` (TEXT), `pinyin_initials` (TEXT), `name` (TEXT), `type` (TEXT), `pinyin_full` (TEXT)
- **`watchlist`**（自选基金表）：`fund_code` (TEXT), `created_at` (INTEGER)
- **`fund_metadata`**（基金完整元信息与指标缓存表）：`fund_code` (TEXT), `fund_name` (TEXT), `fund_type` (TEXT), `fund_manager` (TEXT), `top_holdings` (TEXT), `asset_type` (TEXT), `linked_etf_code` (TEXT), `linked_etf_name` (TEXT)
- **`stock_metadata`**（个股/ETF 基础元数据与最新实时价格）：`stock_code` (TEXT), `stock_name` (TEXT), `market_type` (TEXT), `current_price` (TEXT), `growth_rate` (TEXT)
- **`fund_holding_mapping`**（基金重仓股/ETF 映射关系）：`id` (INTEGER), `fund_code` (TEXT), `stock_code` (TEXT), `weight_percent` (TEXT), `report_date` (TEXT)
- **`stock_price_history`**（个股/ETF 历史价格流水表）：`stock_code` (TEXT), `trade_date` (TEXT), `close_price` (TEXT)

## 3. 关键业务规则
- **净值计算**：默认使用 T-1 净值。对于海外 QDII 基金，系统设置默认开启“T-2 净值对齐支付宝”，但也允许用户在设置页关闭此对齐，直接使用 T-1 的天天基金 API 数据。
- **基金分类**：纯股票型 / 指数 ETF 联接型（锚定场内 ETF）/ QDII 型（通过重仓股判断）。
- **估值状态机与文案渲染 (最新架构)**：
  - 后端已通过 `ValuationDisplayNormalizer` 收拢所有 QDII 的时差与支付宝模式平移逻辑，以及通用的 `isSettled` 判定。
  - 所有 API（包括 Dashboard、自选列表和详情）均统一返回标准化后的展示字段与 `isSettled` 标志位。
  - 前端严禁包含任何特化逻辑（如硬编码对比 `navDate` 和 `estimationTime` 来推断状态），必须完全信任并消费后端下发的 `isSettled` 字段进行渲染。
  - 单只基金：根据 `isSettled` 标志与日期天数差异，动态渲染“今日/昨日/估算/绝对日期”文案。
  - 大盘看板：底层**所有**基金均结算后大盘才算已结算，并取最晚的 `navDate` 进行冒泡渲染。
  - 后端数据模型使用客观的 `latestPnl`（而非 `todayPnl`）来承载各维度的最新收益。
- **数据更新**：工作日 15:00-23:00 触发净值拉取。非交易日需检查是否已拉取成功最近交易日的净值（该补偿拉取策略同样依赖于设置页中“QDII T-2 策略”的开启状态），若未拉取成功则仍需执行。

## 4. 开发约束
- 项目开发相关的所有终端命令，必须在WSL2内部环境执行
  （即路径以 /home/agent 或 /mnt/... 开头，而非 C:\ 开头）。
- 若确实需要调用Windows宿主机工具（如排查Hyper-V/WSL2网络问题），
  调用前必须先说明为什么WSL2侧无法完成，再执行。
- 项目的Node/JVM/Python运行时均在WSL2内，
  禁止调用Windows侧的同名工具（如Windows的gradle.bat、npm.cmd）。
- 修改代码后必须执行 `./delivery.sh`。
- npm/node 通过 nvm 管理，非交互式 shell 执行前需先加载：
  `export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"`
  （delivery.sh 已内置此逻辑，其他临时命令需手动添加）
- 实现任何新的图表或UI组件之前，必须先用浏览器自动化截图同类型的现有组件作为视觉参考，确保风格一致后再动手，不等用户指出不一致问题。
- **[UI 强制自检卡口]**：任何涉及 HTML/CSS 的布局变化或重构，完成后必须主动调用 Chrome DevTools 的 `take_screenshot` 对页面（或特定元素）进行截图，由 Agent 自行完成“视觉走查”。重点检查：1px 边框像素折叠、文字异常折行、flex/grid 未对齐等低级视觉瑕疵。未经过截图走查的代码严禁提交交付。
- **[视觉回归快照机制 (Golden Baseline)]**：在本地修改完 UI 并自我确认后，必须更新并运行 `visual_regression.spec.js` 生成新版金标（Golden Baseline），确保后续流水线能拦截任何破坏现有布局的样式污染。
- 禁止内联 CSS 样式。
- 要求响应式布局，移动端优先。
- **前端禁止越权推导业务状态**：UI 仅应消费后端下发的 `isSettled` 等业务字段，严禁前端自行根据日期或其他字段拼凑出新的状态逻辑，避免多页面状态不一致。
- **结论必须基于证据（去“应该”化）**：回答问题或陈述结果时，禁止使用“应该”、“可能已经好”等词。必须提供日志截图、数据快照或成功执行的测试通过记录作为客观证据。
- **颜色语义隔离原则**：红绿色有强烈的盈亏涨跌属性，必须专属于涨跌幅显示。其他状态标签（如结算、估值中）必须使用蓝、橙或灰色等中性或隔离色系，绝对避免视觉语义交叉。
- **环境安全兜底**：执行临时诊断脚本或有潜在死锁风险的单行命令（如直连 DB、发起网络请求的 Python/Bash）时，必须在最外层使用 `timeout 300s` 等系统级命令进行包裹，防止其进入后台成为长期驻留的僵尸进程。

## 5. Bug队列（P0优先修，未经用户确认不得关闭）

### P0 - 阻断功能
<!-- 在此追加P0级bug -->

### P1 - 影响体验
<!-- 在此追加P1级bug -->

### P2 - 待观察
<!-- 在此追加P2级bug -->

---
**[回写规则] 每次修复bug后必须同步本队列：**
- 已修复的条目：打✅，注明对应回归测试文件路径
- 新发现的条目：追加到对应优先级分组
- 此操作是delivery流程的一部分，不是可选项