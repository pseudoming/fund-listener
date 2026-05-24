package com.fundlistener.service.ocr

import io.github.mymonstercat.Model
import io.github.mymonstercat.ocr.InferenceEngine
import java.io.File
import kotlin.math.abs

import kotlin.test.Test

class OcrAlgorithmTest {
    @Test
    fun testOcrClustering() {
        val engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4)

        val dir = File("test-data")
        if (!dir.exists()) {
            println("test-data not found")
            return
        }

        dir.listFiles { f -> f.extension == "jpg" || f.extension == "png" }?.forEach { file ->
            println("======================")
            println("Testing File: ${file.name}")
            
            val ocrResult = engine.runOcr(file.absolutePath)
            val textBlocks = ocrResult.textBlocks
            
            println("--- Algorithm A: Raw Output (VLM-like linear) ---")
            textBlocks.forEach { block ->
                println("${block.text} [y: ${block.boxPoint[0].y}]")
            }
            
            println("\n--- Algorithm B: Geometric Y-Axis Clustering ---")
            // Group by Y center
            val rows = textBlocks.groupBy { block ->
                // Use an approximate Y-coordinate bucket (e.g. within 15 pixels)
                val yCenter = (block.boxPoint[0].y + block.boxPoint[2].y) / 2
                yCenter / 15 
            }.toSortedMap()
            
            rows.forEach { (yBucket, blocks) ->
                val sortedBlocks = blocks.sortedBy { it.boxPoint[0].x }
                val rowText = sortedBlocks.joinToString(" | ") { it.text }
                println("Row $yBucket: $rowText")
            }
        }
    }
}
