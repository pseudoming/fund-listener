package com.fundlistener.db

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite 连接工厂（JVM 开发期）
 *
 * 职责：
 * 1. 管理 SQLite 连接（单连接，SQLite 本身是串行写入）
 * 2. 启动时执行 schema.sql 初始化表结构
 *
 * Android 期由 Room 的 Database.Builder 替代，此类不迁移。
 */
class DatabaseFactory(private val dbPath: String = "fund-listener.db") {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var connection: Connection

    /**
     * 初始化数据库连接并执行建表 DDL
     * 应在应用启动时调用一次
     */
    fun init() {
        logger.info("Initializing SQLite database: {}", dbPath)
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        connection.autoCommit = true

        // 启用 WAL 模式，提升并发读性能
        connection.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }

        // 执行建表 SQL
        val schema = this::class.java.classLoader
            .getResourceAsStream("schema.sql")
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("schema.sql not found in classpath")

        // 逐行解析 SQL：跳过注释行，按分号分句
        val statements = mutableListOf<String>()
        var current = StringBuilder()
        for (line in schema.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue
            current.append(trimmed).append(' ')
            if (trimmed.endsWith(";")) {
                statements.add(current.toString().trim().removeSuffix(";").trim())
                current = StringBuilder()
            }
        }
        if (current.isNotBlank()) {
            statements.add(current.toString().trim())
        }

        for (sql in statements) {
            try {
                connection.createStatement().use { it.execute(sql) }
            } catch (e: Exception) {
                logger.error("Failed to execute DDL: {}", sql.take(80), e)
                throw e
            }
        }

        logger.info("Database schema initialized successfully")
    }

    /**
     * 获取数据库连接
     * SQLite 单连接模型，所有读写共用
     */
    fun getConnection(): Connection {
        if (!::connection.isInitialized || connection.isClosed) {
            throw IllegalStateException("Database not initialized. Call init() first.")
        }
        return connection
    }

    /**
     * 关闭连接（应用关闭时调用）
     */
    fun close() {
        if (::connection.isInitialized && !connection.isClosed) {
            connection.close()
            logger.info("Database connection closed")
        }
    }
}
