package com.kakao.taxi.test.module

import android.graphics.*
import android.util.Log
import kotlin.math.abs

/**
 * OpenCV 없이 Android 기본 API만으로 노란색 버튼을 감지하는 클래스
 */
class SimpleYellowButtonDetector {
    companion object {
        private const val TAG = "SimpleYellowDetector"
        
        // 카카오 노란색 RGB 범위
        private const val YELLOW_R_MIN = 200
        private const val YELLOW_R_MAX = 255
        private const val YELLOW_G_MIN = 180
        private const val YELLOW_G_MAX = 240
        private const val YELLOW_B_MIN = 0
        private const val YELLOW_B_MAX = 100
        
        // 버튼 크기 제약
        private const val MIN_BUTTON_WIDTH_RATIO = 0.3f
        private const val MAX_BUTTON_WIDTH_RATIO = 0.9f
        private const val MIN_BUTTON_HEIGHT_RATIO = 0.05f
        private const val MAX_BUTTON_HEIGHT_RATIO = 0.15f
    }

    fun detectYellowButton(bitmap: Bitmap): ButtonCandidate? {
        val width = bitmap.width
        val height = bitmap.height
        
        // 하단 영역만 스캔 (버튼은 보통 하단에 위치)
        val scanStartY = (height * 0.4).toInt()
        val scanEndY = height
        
        var bestCandidate: ButtonCandidate? = null
        var maxYellowPixels = 0
        
        // 여러 영역을 스캔하여 노란색이 가장 많은 영역 찾기
        val regionSize = 50 // 스캔할 영역 크기
        
        for (y in scanStartY until scanEndY step regionSize) {
            for (x in 0 until width step regionSize) {
                val region = scanRegion(bitmap, x, y, regionSize, regionSize)
                
                if (region.yellowRatio > 0.6f) { // 60% 이상이 노란색
                    // 주변 영역 확장하여 전체 버튼 영역 찾기
                    val expandedRegion = expandRegion(bitmap, x, y, width, height)
                    
                    if (expandedRegion != null && isValidButton(expandedRegion, width, height)) {
                        val yellowPixelCount = (expandedRegion.width * expandedRegion.height * expandedRegion.yellowRatio).toInt()
                        
                        if (yellowPixelCount > maxYellowPixels) {
                            maxYellowPixels = yellowPixelCount
                            bestCandidate = ButtonCandidate(
                                bounds = expandedRegion.bounds,
                                centerX = expandedRegion.bounds.centerX(),
                                centerY = expandedRegion.bounds.centerY(),
                                confidence = expandedRegion.yellowRatio,
                                avgColor = Color.rgb(255, 231, 0) // 카카오 노란색
                            )
                        }
                    }
                }
            }
        }
        
        bestCandidate?.let {
            Log.d(TAG, "Yellow button found at (${it.centerX}, ${it.centerY}) with confidence ${it.confidence}")
        }
        
        return bestCandidate
    }

    private fun scanRegion(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int): RegionInfo {
        val endX = minOf(startX + width, bitmap.width)
        val endY = minOf(startY + height, bitmap.height)
        
        var yellowPixels = 0
        var totalPixels = 0
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = bitmap.getPixel(x, y)
                if (isYellowPixel(pixel)) {
                    yellowPixels++
                }
                totalPixels++
            }
        }
        
        return RegionInfo(
            bounds = Rect(startX, startY, endX, endY),
            yellowRatio = if (totalPixels > 0) yellowPixels.toFloat() / totalPixels else 0f,
            width = endX - startX,
            height = endY - startY
        )
    }

    private fun expandRegion(bitmap: Bitmap, centerX: Int, centerY: Int, maxWidth: Int, maxHeight: Int): RegionInfo? {
        // 중심점에서 시작하여 노란색 영역 확장
        var left = centerX
        var right = centerX
        var top = centerY
        var bottom = centerY
        
        // 좌우로 확장
        while (left > 0 && isYellowColumn(bitmap, left - 1, top, bottom)) left--
        while (right < maxWidth - 1 && isYellowColumn(bitmap, right + 1, top, bottom)) right++
        
        // 상하로 확장
        while (top > 0 && isYellowRow(bitmap, top - 1, left, right)) top--
        while (bottom < maxHeight - 1 && isYellowRow(bitmap, bottom + 1, left, right)) bottom++
        
        val bounds = Rect(left, top, right, bottom)
        val region = scanRegion(bitmap, left, top, right - left, bottom - top)
        
        return if (region.yellowRatio > 0.5f) region else null
    }

    private fun isYellowColumn(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        var yellowCount = 0
        val total = bottom - top
        
        for (y in top..bottom) {
            if (isYellowPixel(bitmap.getPixel(x, y))) {
                yellowCount++
            }
        }
        
        return yellowCount.toFloat() / total > 0.5f
    }

    private fun isYellowRow(bitmap: Bitmap, y: Int, left: Int, right: Int): Boolean {
        var yellowCount = 0
        val total = right - left
        
        for (x in left..right) {
            if (isYellowPixel(bitmap.getPixel(x, y))) {
                yellowCount++
            }
        }
        
        return yellowCount.toFloat() / total > 0.5f
    }

    private fun isYellowPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        return r in YELLOW_R_MIN..YELLOW_R_MAX &&
               g in YELLOW_G_MIN..YELLOW_G_MAX &&
               b in YELLOW_B_MIN..YELLOW_B_MAX
    }

    private fun isValidButton(region: RegionInfo, screenWidth: Int, screenHeight: Int): Boolean {
        val widthRatio = region.width.toFloat() / screenWidth
        val heightRatio = region.height.toFloat() / screenHeight
        val aspectRatio = region.width.toFloat() / region.height
        
        return widthRatio in MIN_BUTTON_WIDTH_RATIO..MAX_BUTTON_WIDTH_RATIO &&
               heightRatio in MIN_BUTTON_HEIGHT_RATIO..MAX_BUTTON_HEIGHT_RATIO &&
               aspectRatio > 2.0f // 가로가 세로보다 2배 이상 길어야 함
    }

    fun visualizeDetection(bitmap: Bitmap, candidate: ButtonCandidate?): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        candidate?.let {
            // 감지된 영역 표시
            val paint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(it.bounds.toRectF(), paint)
            
            // 중심점 표시
            val centerPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(it.centerX.toFloat(), it.centerY.toFloat(), 10f, centerPaint)
            
            // 신뢰도 표시
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 30f
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            canvas.drawText(
                "%.1f%%".format(it.confidence * 100),
                it.bounds.left.toFloat(),
                it.bounds.top.toFloat() - 10,
                textPaint
            )
        }
        
        return mutableBitmap
    }

    private data class RegionInfo(
        val bounds: Rect,
        val yellowRatio: Float,
        val width: Int,
        val height: Int
    )
}

// Rect 확장 함수
private fun Rect.toRectF(): RectF = RectF(
    left.toFloat(),
    top.toFloat(),
    right.toFloat(),
    bottom.toFloat()
)