-- ============================================================
-- 基金监控系统 · 数据库 Schema
-- 存储引擎: SQLite (JVM 开发期) / Room (Android 生产期)
-- 精度方案: TEXT + BigDecimal (Kotlin 层运算)
-- ============================================================

-- 持仓表：当前持有的基金汇总，每个 fund_code 最多一行
CREATE TABLE IF NOT EXISTS fund_position (
    fund_code    TEXT PRIMARY KEY,
    fund_name    TEXT NOT NULL,
    total_shares TEXT NOT NULL DEFAULT '0',
    total_cost   TEXT NOT NULL DEFAULT '0',
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
);

-- 交易记录表：每次买入/卖出的明细
CREATE TABLE IF NOT EXISTS fund_transaction (
    id          TEXT PRIMARY KEY,
    fund_code   TEXT NOT NULL,
    type        TEXT NOT NULL CHECK(type IN ('BUY','SELL')),
    shares      TEXT NOT NULL,
    nav         TEXT NOT NULL,
    amount      TEXT NOT NULL,
    fee         TEXT NOT NULL DEFAULT '0',
    trade_date  TEXT NOT NULL,
    note        TEXT NOT NULL DEFAULT '',
    created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_txn_fund_code ON fund_transaction(fund_code);
CREATE INDEX IF NOT EXISTS idx_txn_trade_date ON fund_transaction(trade_date);

-- 净值历史表：基金每日收盘净值
CREATE TABLE IF NOT EXISTS fund_nav_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    fund_code   TEXT NOT NULL,
    nav_date    TEXT NOT NULL,
    nav         TEXT NOT NULL,
    acc_nav     TEXT,
    created_at  INTEGER NOT NULL,
    UNIQUE(fund_code, nav_date)
);
CREATE INDEX IF NOT EXISTS idx_nav_fund_code ON fund_nav_history(fund_code);

-- 估值快照表：盘中估值 + PE/PB 计算结果
CREATE TABLE IF NOT EXISTS fund_valuation_snapshot (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    fund_code             TEXT NOT NULL,
    snapshot_time         INTEGER NOT NULL,
    estimated_nav         TEXT NOT NULL,
    estimated_growth_rate TEXT NOT NULL,
    weighted_pe           TEXT,
    weighted_pb           TEXT,
    pe_percentile         TEXT,
    pb_percentile         TEXT,
    coverage_rate         TEXT,
    created_at            INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vs_fund_code ON fund_valuation_snapshot(fund_code);
CREATE INDEX IF NOT EXISTS idx_vs_snapshot_time ON fund_valuation_snapshot(snapshot_time);

-- KV 配置表：存储用户偏好设置
CREATE TABLE IF NOT EXISTS app_config (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 自定义估值引擎规则表
CREATE TABLE IF NOT EXISTS fund_valuation_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fund_code TEXT NOT NULL,
    component_type TEXT NOT NULL,
    target_code TEXT NOT NULL,
    weight_percent REAL NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_fvr_fund_code ON fund_valuation_rule(fund_code);

-- 全量基金基础信息缓存表
CREATE TABLE IF NOT EXISTS all_funds (
    code            TEXT PRIMARY KEY,
    pinyin_initials TEXT NOT NULL,
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,
    pinyin_full     TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_af_keyword ON all_funds(code, name, pinyin_initials);

-- 自选基金表
CREATE TABLE IF NOT EXISTS watchlist (
    fund_code  TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL
);

-- 基金完整元信息与指标缓存表
DROP TABLE IF EXISTS fund_metadata;
CREATE TABLE IF NOT EXISTS fund_metadata (
    fund_code        TEXT PRIMARY KEY,
    fund_name        TEXT NOT NULL,
    fund_type        TEXT,
    fund_manager     TEXT,
    top_holdings     TEXT,
    asset_type       TEXT,
    linked_etf_code  TEXT,
    linked_etf_name  TEXT,
    last_updated     INTEGER NOT NULL
);

-- 个股/ETF 基础元数据与最新实时价格
CREATE TABLE IF NOT EXISTS stock_metadata (
    stock_code            TEXT PRIMARY KEY,
    stock_name            TEXT NOT NULL,
    market_type           TEXT NOT NULL,
    current_price         TEXT,
    growth_rate           TEXT,
    updated_at            INTEGER NOT NULL
);

-- 基金重仓股/ETF 映射关系
CREATE TABLE IF NOT EXISTS fund_holding_mapping (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    fund_code             TEXT NOT NULL,
    stock_code            TEXT NOT NULL,
    weight_percent        TEXT NOT NULL,
    report_date           TEXT NOT NULL,
    created_at            INTEGER NOT NULL,
    UNIQUE(fund_code, stock_code)
);
CREATE INDEX IF NOT EXISTS idx_fhm_fund_code ON fund_holding_mapping(fund_code);

-- 个股/ETF 历史价格流水表（预留）
CREATE TABLE IF NOT EXISTS stock_price_history (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code            TEXT NOT NULL,
    trade_date            TEXT NOT NULL,
    close_price           TEXT NOT NULL,
    created_at            INTEGER NOT NULL,
    UNIQUE(stock_code, trade_date)
);
CREATE INDEX IF NOT EXISTS idx_sph_stock_code ON stock_price_history(stock_code);
