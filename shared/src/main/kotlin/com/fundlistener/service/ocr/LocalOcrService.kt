package com.fundlistener.service.ocr

import com.fundlistener.client.TianTianFundClient
import io.github.mymonstercat.Model
import io.github.mymonstercat.ocr.InferenceEngine
import io.github.mymonstercat.ocr.config.ParamConfig
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.sync.withLock

/**
 * 本地 OCR 服务实现 — 使用 RapidOCR-Java (ONNX 引擎) 进行纯本地文字识别，
 * 并通过 Algorithm B (Y-Axis IoU 重叠度算法) 进行文本行物理聚类。
 */
class LocalOcrService(
    private val parser: OcrParser = OcrParser(),
    private val repository: com.fundlistener.repository.FundRepository? = null,
    private val tianTianFundClient: TianTianFundClient? = null
) : OcrService {

    private val logger = LoggerFactory.getLogger(LocalOcrService::class.java)

    // 延迟初始化 OCR 引擎，避免系统启动时出现不必要的 native 加载开销
    private val engine by lazy {
        InferenceEngine.getInstance(Model.ONNX_PPOCR_V4)
    }

    // 互斥锁，保证 native 引擎的并发安全
    private val ocrMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun recognize(imageBytes: ByteArray, hint: String?, tradeDate: String?): OcrResult {
        logger.info("LocalOcrService: processing image ({} bytes) using local RapidOCR", imageBytes.size)

        // Native 接口只接受文件路径，因此需要写到临时文件
        val tempFile = File.createTempFile("ocr_input_", ".png")
        try {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val byteStream = java.io.ByteArrayInputStream(imageBytes)
                    val bufferedImage = javax.imageio.ImageIO.read(byteStream)
                    if (bufferedImage != null) {
                        val rgbImage = java.awt.image.BufferedImage(
                            bufferedImage.width,
                            bufferedImage.height,
                            java.awt.image.BufferedImage.TYPE_3BYTE_BGR
                        )
                        val g2d = rgbImage.createGraphics()
                        g2d.drawImage(bufferedImage, 0, 0, null)
                        g2d.dispose()
                        javax.imageio.ImageIO.write(rgbImage, "png", tempFile)
                    } else {
                        throw IllegalStateException("bufferedImage is null")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to preprocess image to TYPE_3BYTE_BGR, writing raw bytes directly", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    tempFile.writeBytes(imageBytes)
                }
            }

            val paramConfig = ParamConfig.getDefaultConfig().apply {
                isDoAngle = true
                isMostAngle = true
            }

            val ocrResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ocrMutex.withLock {
                    engine.runOcr(tempFile.absolutePath, paramConfig)
                }
            }
            val textBlocks = ocrResult.textBlocks ?: emptyList()

            // 运行算法 B: Y轴 IoU 物理聚类
            val groupedRows = algoBIouOverlap(textBlocks, overlapThreshold = 0.3)

            // 按每一行进行 X 坐标排序，保证拼接出的文本行从左到右是有序的
            val rawText = groupedRows.joinToString("\n") { row ->
                row.sortedBy { it.boxPoint[0].x }.joinToString(" ") { it.text }
            }
            logger.info("LocalOcrService parsed text:\n{}", rawText)

            // 解析单只基金详情（向下兼容原有单只截图匹配逻辑）
            val fields = parser.parse(rawText)
            val confidence = if (fields.isNotEmpty()) fields.map { it.confidence }.average() else 0.0

            // 增量解析：批量持仓列表解析，支持过滤噪声与自动关联数据库
            val parsedFunds = parseMultipleFunds(groupedRows, tradeDate)

            return OcrResult(
                rawText = rawText,
                extractedFields = fields,
                confidence = confidence,
                parsedFunds = parsedFunds
            )
        } catch (e: Exception) {
            logger.error("LocalOcrService failed to process image", e)
            return OcrResult(rawText = "", extractedFields = emptyList(), confidence = 0.0)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun parseMultipleFunds(
        groupedRows: List<List<com.benjaminwan.ocrlibrary.TextBlock>>,
        tradeDate: String? = null
    ): List<ParsedFund> {
        val parsedList = mutableListOf<ParsedFund>()
        if (repository == null) return emptyList()

        val skipWords = setOf("我的持仓", "资产", "总金额", "持有收益", "昨日收益", "产品名称", "金额", "盈亏", "持仓", "明细", "收益率", "总资产")

        var i = 0
        while (i < groupedRows.size) {
            val row = groupedRows[i]
            val texts = row.sortedBy { it.boxPoint[0].x }.map { it.text.trim() }

            // 寻找基金名称候选：将同一行数字之前的、非噪音的文本块水平拼接起来
            val nameParts = mutableListOf<String>()
            for (text in texts) {
                if (parseNumber(text) != null || text.contains("%")) {
                    break
                }
                if (skipWords.none { text.contains(it) } && !text.contains(":") && !text.contains("：")) {
                    nameParts.add(text)
                }
            }
            val nameCandidate = if (nameParts.any { it.length >= 2 }) nameParts.joinToString("") else null

            val numbers = texts.mapNotNull { parseNumber(it) }

            if (nameCandidate != null && numbers.isNotEmpty()) {
                var fundName = nameCandidate
                val amount = numbers.maxOrNull() ?: 0.0
                var holdingReturn = if (numbers.size > 1) {
                    numbers.firstOrNull { it != amount } ?: 0.0
                } else 0.0

                // 提取收益率百分比候选 (例如: +1.23%, -0.50%, 0.00%)
                val pctCandidate = texts.firstOrNull { it.contains("%") }
                val parsedPct = if (pctCandidate != null) {
                    val cleaned = pctCandidate.replace("%", "").replace("+", "").replace(" ", "").trim()
                    cleaned.toDoubleOrNull()
                } else null

                // 智能恢复：如果有百分比，但没识别出收益金额（或者收益金额识别有误为0）
                if (parsedPct != null && holdingReturn == 0.0 && parsedPct != 0.0) {
                    holdingReturn = amount - amount / (1.0 + parsedPct / 100.0)
                }

                // 尝试合并下一行的类型后缀（如 "A", "C", "联接C" 等）
                if (i + 1 < groupedRows.size) {
                    val nextRow = groupedRows[i + 1]
                    val nextTexts = nextRow.sortedBy { it.boxPoint[0].x }.map { it.text.trim() }
                    
                    // 几何布局规则（降维打击）：有效后缀行必定伴随着财务数据（金额/收益/百分比）。若整行没有任何财务数据，则必然是下挂的Tag标签。
                    val hasFinancialData = nextTexts.any { 
                        parseNumber(it) != null || 
                        it.contains("%") || 
                        it.matches(Regex("^-+$")) // 匹配暂无数据的 "-" 或 "--"
                    }

                    val suffix = if (hasFinancialData) {
                        nextTexts.firstOrNull { text ->
                            text.length <= 25 && 
                            parseNumber(text) == null && 
                            !text.contains("%") && 
                            !text.contains(":") && 
                            !text.contains("：")
                        }
                    } else {
                        null
                    }

                    if (suffix != null) {
                        fundName = "$fundName$suffix".replace(" ", "")
                        i++ // 跳过下一行，因为已被作为后缀合并
                    } else {
                        fundName = fundName.replace(" ", "")
                    }
                }

                // 模糊匹配数据库，转换出6位代码和规范名称
                val searchResult = findBestMatchingFund(fundName, repository)
                var code = searchResult?.code ?: ""
                val officialName = searchResult?.name ?: fundName
                
                var candidates: List<com.fundlistener.service.ocr.FundCandidate> = emptyList()
                
                if (fundName.endsWith("...") || fundName.contains("...") || fundName.endsWith("…") || code.isEmpty()) {
                    val queryName = fundName.replace("...", "").replace("…", "").trim()
                    if (queryName.isNotEmpty() && tianTianFundClient != null) {
                        val rawCandidates = tianTianFundClient.searchLive(queryName)
                        
                        // 1. 尝试完全前缀匹配过滤 (移除支付宝等平台特有的展示字眼以增强鲁棒性)
                        val cleanQuery = queryName.replace(" ", "")
                            .replace("市值加权", "")
                            .replace("发起式", "")
                            .replace("...", "")
                            .replace("…", "")

                        val prefixMatches = rawCandidates.filter { 
                            val cleanCandName = it.fundName.replace(" ", "")
                                .replace("市值加权", "")
                                .replace("发起式", "")
                            cleanCandName.startsWith(cleanQuery, ignoreCase = true) 
                        }
                        
                        // 如果有前缀完全匹配的，只保留这些；否则保持原有的所有模糊匹配候选
                        candidates = if (prefixMatches.isNotEmpty()) prefixMatches else rawCandidates

                        // 如果刚好只有一个候选，直接补全
                        if (code.isEmpty() && candidates.size == 1) {
                            code = candidates.first().fundCode
                        }
                    }
                }

                // 如果有代码，或者是包含候选名单的截断名称，则认为是有效区块
                if (code.isNotEmpty() || candidates.isNotEmpty()) {
                    val costVal = amount - holdingReturn
                    
                    val holdingReturnPercentStr = if (parsedPct != null) {
                        val sign = if (parsedPct >= 0) "+" else ""
                        "$sign${String.format("%.2f", parsedPct)}%"
                    } else {
                        if (costVal > 0.0) {
                            val pct = (holdingReturn / costVal) * 100.0
                            val sign = if (pct >= 0) "+" else ""
                            "$sign${String.format("%.2f", pct)}%"
                        } else "0.00%"
                    }

                    // 决定使用哪个日期的净值来反推份额
                    var navVal: Double? = null
                    
                    // 如果传了 tradeDate (如 2026-05-20)，则我们认为截图上的金额是基于 T-1（即该日期之前最近的交易日）的净值算出来的。
                    if (tradeDate != null) {
                        try {
                            val history = repository.getNavHistory(code, 30) // 取最近一个月
                            // 寻找日期严格小于 tradeDate 的最新一条净值
                            val tMinusOneNav = history.firstOrNull { it.navDate < tradeDate }
                            if (tMinusOneNav != null) {
                                navVal = tMinusOneNav.nav.toDouble()
                                logger.info("Using T-1 NAV ({}) for fund {} on tradeDate {}", navVal, code, tradeDate)
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to query historical NAV for tradeDate fallback", e)
                        }
                    }

                    // 如果没传，或者查询失败，降级使用系统的绝对最新净值
                    if (navVal == null || navVal <= 0.0) {
                        navVal = repository.getLatestNav(code)?.nav?.toDouble()
                    }
                    
                    if (navVal == null || navVal <= 0.0) {
                        val snapshotVal = repository.getLatestSnapshot(code)?.estimatedNav?.toDouble()
                        if (snapshotVal != null && snapshotVal > 0.0) {
                            navVal = snapshotVal
                        }
                    }
                    if ((navVal == null || navVal <= 0.0) && tianTianFundClient != null) {
                        try {
                            val raw = tianTianFundClient.fetchEstimation(code)
                            val fetched = raw?.dwjz?.toDoubleOrNull() ?: raw?.gsz?.toDoubleOrNull()
                            if (fetched != null && fetched > 0.0) {
                                navVal = fetched
                            } else {
                                // For QDII or when estimation is empty, try fetching historical NAV trend
                                val trend = tianTianFundClient.fetchNavTrend(code)
                                val trendLast = trend?.lastOrNull()?.y
                                if (trendLast != null && trendLast > 0.0) {
                                    navVal = trendLast
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to fetch live NAV or Trend for $code during OCR", e)
                        }
                    }
                    val latestNav = if (navVal != null && navVal > 0.0) navVal else 1.0
                    val calculatedShares = if (latestNav > 0.0) amount / latestNav else 0.0

                    // 校验系统内是否已存在该持仓
                    val existingPosition = repository.getPosition(code)
                    
                    parsedList.add(
                        ParsedFund(
                            fundCode = code,
                            fundName = fundName, // 使用带有合并后缀的原图提取名称，不强制覆盖为官方名称
                            amount = String.format("%.2f", amount),
                            holdingReturn = String.format("%.2f", holdingReturn),
                            holdingReturnPercent = holdingReturnPercentStr,
                            cost = String.format("%.2f", costVal),
                            shares = String.format("%.4f", calculatedShares),
                            nav = String.format("%.4f", latestNav),
                            isDuplicate = existingPosition != null,
                            existingShares = existingPosition?.totalShares?.toPlainString() ?: "0.0",
                            existingCost = existingPosition?.totalCost?.toPlainString() ?: "0.0",
                            candidates = candidates
                        )
                    )
                }
            }
            i++
        }

        return parsedList
    }

    private suspend fun findBestMatchingFund(
        ocrName: String,
        repository: com.fundlistener.repository.FundRepository
    ): com.fundlistener.model.FundSearchResult? {
        val normalized = ocrName.replace("（", "(").replace("）", ")").replace(" ", "")
        
        // 提取类别字母 (例如 A, B, C, D, E)
        val classRegex = Regex("(?i)\\b([A-E])\\b|([A-E])人民币|([A-E])美元|[ (]([A-E])[)]?$|[ (]([A-E])[)]$")
        val classMatch = classRegex.find(normalized)
        val classLetter = classMatch?.groupValues?.firstOrNull { it.isNotEmpty() && it.length == 1 }?.uppercase()

        // 提取核心名字
        var coreName = normalized
            .replace("工银瑞信", "工银")
            .replace("交银施罗德", "交银")
            .replace("农银汇理", "农银")
            .replace("浦银安盛", "浦银")
            .replace(Regex("\\([^)]*\\)"), "") // 去掉括号内容
            .replace("ETF", "")
            .replace("联接", "")
            .replace("配置", "")
            .replace("发起式", "")
            .replace("市值加权", "")
            .replace("人民币", "")
            .replace("美元现汇", "")
            .replace("美元现钞", "")
            .replace("QDII", "")
            .replace("LOF", "")
            .replace("FOF", "")
            .replace(".", "")
            .replace("…", "")
        
        // 去掉末尾的分类字母
        if (classLetter != null) {
            coreName = coreName.removeSuffix(classLetter).removeSuffix(classLetter.lowercase())
        }
        coreName = coreName.trim()

        if (coreName.length < 3) {
            coreName = normalized
        }

        // 1. 尝试使用核心词搜索
        var results = repository.searchFunds(coreName, 20)
        if (results.isEmpty() && coreName.length > 5) {
            // 2. 尝试前缀搜索（取前5个字符）
            results = repository.searchFunds(coreName.take(5), 20)
        }
        if (results.isEmpty() && coreName.length > 4) {
            // 3. 尝试更短前缀搜索（取前4个字符）
            results = repository.searchFunds(coreName.take(4), 20)
        }

        if (results.isEmpty()) {
            return null
        }

        // 排序规则：优先匹配符合分类字母的，然后在候选集中按与原始 OCR 规范名称的编辑距离从小到大排序
        val sortedCandidates = results.map { res ->
            val resNormalized = res.name.replace("（", "(").replace("）", ")").replace(" ", "")
            val hasMatchingClass = if (classLetter != null) {
                resNormalized.contains(classLetter, ignoreCase = true) ||
                (classLetter == "A" && (resNormalized.contains("A/") || resNormalized.contains("/B")))
            } else true
            
            // 计算编辑距离时，先剔除括号及其内容（如 (QDII)、(LOF) 等），避免元数据格式差异导致偏离最佳匹配
            val cleanOcr = normalized.replace(Regex("\\([^)]*\\)"), "")
            val cleanCandidate = resNormalized.replace(Regex("\\([^)]*\\)"), "")
            val distance = levenshteinDistance(cleanOcr, cleanCandidate)
            Triple(res, hasMatchingClass, distance)
        }.sortedWith(compareBy<Triple<com.fundlistener.model.FundSearchResult, Boolean, Int>> { !it.second }.thenBy { it.third })

        return sortedCandidates.firstOrNull()?.first
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j - 1], dp[j], prev) + 1
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun parseNumber(text: String): Double? {
        val cleaned = text.replace(",", "").replace("¥", "").replace("￥", "").trim()
        if (cleaned.contains("%")) return null
        return cleaned.toDoubleOrNull()
    }

    /**
     * Algorithm B: Y-Axis IoU 重叠度物理聚类算法
     * 针对表格/并排多列数据结构，通过计算 Y 轴重叠比例对不同文本块进行行聚类。
     */
    private fun algoBIouOverlap(
        blocks: List<com.benjaminwan.ocrlibrary.TextBlock>,
        overlapThreshold: Double
    ): List<List<com.benjaminwan.ocrlibrary.TextBlock>> {
        if (blocks.isEmpty()) return emptyList()

        // 1. 按 bounding box 最小的 Y 坐标值（即顶端）进行排序
        val sortedBlocks = blocks.sortedBy { block ->
            block.boxPoint.minOf { it.y }
        }

        val rows = mutableListOf<MutableList<com.benjaminwan.ocrlibrary.TextBlock>>()

        for (block in sortedBlocks) {
            val yMin = block.boxPoint.minOf { it.y }
            val yMax = block.boxPoint.maxOf { it.y }
            val h = (yMax - yMin).toDouble()

            var placed = false
            for (row in rows) {
                // 计算当前行所有 block 所占的合并 Y 轴范围
                val rowYMin = row.minOf { r -> r.boxPoint.minOf { it.y } }
                val rowYMax = row.maxOf { r -> r.boxPoint.maxOf { it.y } }
                val rowH = (rowYMax - rowYMin).toDouble()

                // 计算相交的 Y 轴区间宽度
                val interMin = max(yMin, rowYMin)
                val interMax = min(yMax, rowYMax)
                val interH = max(0, interMax - interMin).toDouble()

                // 重叠比例定义为：相交高度 / (两个区间中较小的一个高度)
                val minH = min(h, rowH)
                if (minH > 0.0 && (interH / minH) >= overlapThreshold) {
                    row.add(block)
                    placed = true
                    break
                }
            }

            if (!placed) {
                rows.add(mutableListOf(block))
            }
        }

        return rows
    }
}
