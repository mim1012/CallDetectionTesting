package com.kakao.taxi.test.module

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 디버깅을 위한 헬퍼 클래스
 * - 스크린샷 저장
 * - 감지 결과 시각화
 * - 로그 파일 생성
 */
class DebugHelper(private val context: Context) {
    companion object {
        private const val TAG = "DebugHelper"
        private const val DEBUG_FOLDER = "KakaoTaxiDebug"
        private const val MAX_SAVED_IMAGES = 50 // 저장 공간 관리를 위해 최대 50개만 유지
    }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val debugDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), DEBUG_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    /**
     * 원본 스크린샷 저장
     */
    fun saveOriginalScreenshot(bitmap: Bitmap, prefix: String = "original"): String? {
        return try {
            val filename = "${prefix}_${dateFormat.format(Date())}.png"
            val file = File(debugDir, filename)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "Original screenshot saved: ${file.absolutePath}")
            cleanupOldFiles()
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save original screenshot", e)
            null
        }
    }

    /**
     * 감지 결과가 표시된 스크린샷 저장
     */
    fun saveDetectionResult(
        bitmap: Bitmap,
        candidate: ButtonCandidate?,
        prefix: String = "detection"
    ): String? {
        return try {
            val resultBitmap = drawDetectionResult(bitmap, candidate)
            val filename = "${prefix}_${dateFormat.format(Date())}_${if (candidate != null) "found" else "notfound"}.png"
            val file = File(debugDir, filename)
            
            FileOutputStream(file).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "Detection result saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save detection result", e)
            null
        }
    }

    /**
     * 감지 결과를 비트맵에 그리기
     */
    private fun drawDetectionResult(bitmap: Bitmap, candidate: ButtonCandidate?): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // 타임스탬프 추가
        val timestampPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(
            dateFormat.format(Date()),
            10f,
            30f,
            timestampPaint
        )
        
        if (candidate != null) {
            // 감지된 영역 표시
            val boxPaint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(
                candidate.bounds.left.toFloat(),
                candidate.bounds.top.toFloat(),
                candidate.bounds.right.toFloat(),
                candidate.bounds.bottom.toFloat(),
                boxPaint
            )
            
            // 중심점 표시
            val centerPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(
                candidate.centerX.toFloat(),
                candidate.centerY.toFloat(),
                10f,
                centerPaint
            )
            
            // 정보 표시
            val infoPaint = Paint().apply {
                color = Color.GREEN
                textSize = 28f
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            canvas.drawText(
                "Confidence: %.1f%%".format(candidate.confidence * 100),
                candidate.bounds.left.toFloat(),
                candidate.bounds.top.toFloat() - 10,
                infoPaint
            )
            canvas.drawText(
                "Click: (${candidate.centerX}, ${candidate.centerY})",
                candidate.bounds.left.toFloat(),
                candidate.bounds.bottom.toFloat() + 30,
                infoPaint
            )
        } else {
            // 감지 실패 표시
            val failPaint = Paint().apply {
                color = Color.RED
                textSize = 36f
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            canvas.drawText(
                "No Yellow Button Found",
                50f,
                bitmap.height / 2f,
                failPaint
            )
        }
        
        return mutableBitmap
    }

    /**
     * OCR 결과 저장
     */
    fun saveOCRResult(
        bitmap: Bitmap,
        ocrText: String,
        amount: Int?,
        distance: Float?,
        prefix: String = "ocr"
    ): String? {
        return try {
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            
            // OCR 정보 표시
            val paint = Paint().apply {
                color = Color.YELLOW
                textSize = 28f
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            
            var yOffset = 100f
            canvas.drawText("OCR Result:", 10f, yOffset, paint)
            yOffset += 40f
            
            if (amount != null) {
                canvas.drawText("Amount: ${amount}원", 10f, yOffset, paint)
                yOffset += 40f
            }
            
            if (distance != null) {
                canvas.drawText("Distance: ${distance}km", 10f, yOffset, paint)
                yOffset += 40f
            }
            
            // OCR 텍스트 일부 표시
            val displayText = if (ocrText.length > 100) {
                ocrText.substring(0, 100) + "..."
            } else {
                ocrText
            }
            canvas.drawText("Text: $displayText", 10f, yOffset, paint)
            
            val filename = "${prefix}_${dateFormat.format(Date())}.png"
            val file = File(debugDir, filename)
            
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "OCR result saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save OCR result", e)
            null
        }
    }

    /**
     * 텍스트 로그 파일 저장
     */
    fun saveLogFile(content: String, prefix: String = "log"): String? {
        return try {
            val filename = "${prefix}_${dateFormat.format(Date())}.txt"
            val file = File(debugDir, filename)
            
            file.writeText(content)
            
            Log.d(TAG, "Log file saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save log file", e)
            null
        }
    }

    /**
     * 감지 세션 로그 생성
     */
    fun createDetectionLog(
        timestamp: Long,
        screenCaptured: Boolean,
        buttonFound: Boolean,
        candidate: ButtonCandidate?,
        ocrResult: String?,
        clickAttempted: Boolean,
        clickSuccess: Boolean,
        error: String? = null
    ): String {
        return buildString {
            appendLine("=== Detection Log ===")
            appendLine("Timestamp: ${dateFormat.format(Date(timestamp))}")
            appendLine("Screen Captured: $screenCaptured")
            appendLine("Button Found: $buttonFound")
            
            if (candidate != null) {
                appendLine("Button Location: (${candidate.centerX}, ${candidate.centerY})")
                appendLine("Button Bounds: ${candidate.bounds}")
                appendLine("Confidence: ${candidate.confidence}")
            }
            
            if (ocrResult != null) {
                appendLine("OCR Result: $ocrResult")
            }
            
            appendLine("Click Attempted: $clickAttempted")
            appendLine("Click Success: $clickSuccess")
            
            if (error != null) {
                appendLine("Error: $error")
            }
            
            appendLine("===================")
        }
    }

    /**
     * 오래된 파일 정리
     */
    private fun cleanupOldFiles() {
        try {
            val files = debugDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            
            if (files.size > MAX_SAVED_IMAGES) {
                // 오래된 파일 삭제
                files.drop(MAX_SAVED_IMAGES).forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old files", e)
        }
    }

    /**
     * 디버그 폴더 경로 가져오기
     */
    fun getDebugFolderPath(): String = debugDir.absolutePath
    
    /**
     * 디버그 정보를 맵으로 저장
     */
    fun saveDebugInfo(bitmap: Bitmap, prefix: String, info: Map<String, String>) {
        val timestamp = dateFormat.format(Date())
        
        // 스크린샷 저장
        val imageFile = File(debugDir, "${prefix}_${timestamp}.png")
        try {
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Debug screenshot saved: ${imageFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug screenshot", e)
            return
        }
        
        // 정보를 JSON으로 저장
        val infoFile = File(debugDir, "${prefix}_${timestamp}_info.json")
        try {
            val json = buildString {
                append("{\n")
                info.entries.forEachIndexed { index, entry ->
                    append("  \"${entry.key}\": \"${entry.value}\"")
                    if (index < info.size - 1) append(",")
                    append("\n")
                }
                append("}")
            }
            infoFile.writeText(json)
            Log.d(TAG, "Debug info saved: ${infoFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug info", e)
        }
        
        // 자동 정리
        cleanupOldFiles()
    }

    /**
     * 모든 디버그 파일 삭제
     */
    fun clearAllDebugFiles() {
        try {
            debugDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All debug files cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear debug files", e)
        }
    }
}