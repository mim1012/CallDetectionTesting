package com.kakao.taxi.test.module

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
// OpenCV imports removed - using SimpleYellowButtonDetector instead
import kotlin.math.abs

data class ButtonCandidate(
    val bounds: Rect,
    val centerX: Int,
    val centerY: Int,
    val confidence: Float,
    val avgColor: Int
)

class YellowButtonDetector {
    companion object {
        private const val TAG = "YellowButtonDetector"
        
        // SimpleYellowButtonDetector will handle color detection
        
        // 버튼 크기 제약 (화면 대비 비율)
        private const val MIN_BUTTON_WIDTH_RATIO = 0.2f  // 화면 폭의 20% 이상
        private const val MAX_BUTTON_WIDTH_RATIO = 0.9f  // 화면 폭의 90% 이하
        private const val MIN_BUTTON_HEIGHT_RATIO = 0.05f // 화면 높이의 5% 이상
        private const val MAX_BUTTON_HEIGHT_RATIO = 0.15f // 화면 높이의 15% 이하
        
        // 형태 제약
        private const val MIN_ASPECT_RATIO = 2.0f  // 가로가 세로의 2배 이상
        private const val MAX_ASPECT_RATIO = 8.0f  // 가로가 세로의 8배 이하
    }

    fun detectYellowButton(bitmap: Bitmap, useAltRange: Boolean = false): List<ButtonCandidate> {
        // Delegate to SimpleYellowButtonDetector
        val detector = SimpleYellowButtonDetector()
        val candidate = detector.detectYellowButton(bitmap)
        return candidate?.let { listOf(it) } ?: emptyList()
    }

    fun detectAllYellowButtons(bitmap: Bitmap): List<ButtonCandidate> {
        // Try both standard and alternative ranges
        val standardCandidates = detectYellowButton(bitmap, useAltRange = false)
        val altCandidates = detectYellowButton(bitmap, useAltRange = true)
        
        // Combine and remove duplicates
        val allCandidates = (standardCandidates + altCandidates).distinctBy {
            "${it.centerX}_${it.centerY}"
        }
        
        return allCandidates
    }

    fun detectCallAcceptButton(bitmap: Bitmap): ButtonCandidate? {
        // First try with standard yellow range
        var candidates = detectYellowButton(bitmap, useAltRange = false)
        
        // If no candidates found, try with alternative range
        if (candidates.isEmpty()) {
            candidates = detectYellowButton(bitmap, useAltRange = true)
        }
        
        // Filter candidates based on position (usually in lower half of screen)
        val screenHeight = bitmap.height
        val lowerHalfCandidates = candidates.filter { 
            it.centerY > screenHeight * 0.4f  // Button typically in lower 60% of screen
        }
        
        // Return the best candidate (highest confidence)
        return lowerHalfCandidates.firstOrNull() ?: candidates.firstOrNull()
    }

    private fun calculateAverageColor(bitmap: Bitmap, rect: Rect): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }
            }
        }
        
        return if (count > 0) {
            Color.rgb(
                (r / count).toInt(),
                (g / count).toInt(),
                (b / count).toInt()
            )
        } else {
            Color.BLACK
        }
    }

    fun isYellowColor(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        
        val hue = hsv[0]
        val saturation = hsv[1] * 255
        val value = hsv[2] * 255
        
        // Check if color is in yellow range
        return hue in 20f..40f && saturation > 100 && value > 100
    }

    fun debugDrawDetection(bitmap: Bitmap, candidates: List<ButtonCandidate>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        
        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        
        val textPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            textSize = 30f
            style = android.graphics.Paint.Style.FILL
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        
        candidates.forEachIndexed { index, candidate ->
            // Draw bounding box
            paint.color = if (index == 0) Color.GREEN else Color.YELLOW
            canvas.drawRect(
                candidate.bounds.left.toFloat(),
                candidate.bounds.top.toFloat(),
                candidate.bounds.right.toFloat(),
                candidate.bounds.bottom.toFloat(),
                paint
            )
            
            // Draw confidence
            val text = "%.1f%%".format(candidate.confidence * 100)
            canvas.drawText(
                text,
                candidate.bounds.left.toFloat() + 10,
                candidate.bounds.top.toFloat() + 30,
                textPaint
            )
            
            // Draw center point
            val centerPaint = android.graphics.Paint().apply {
                color = Color.RED
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(
                candidate.centerX.toFloat(),
                candidate.centerY.toFloat(),
                10f,
                centerPaint
            )
        }
        
        return mutableBitmap
    }
}