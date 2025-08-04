package com.kakao.taxi.test.module

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log

data class MatchResult(
    val location: Point,
    val confidence: Double,
    val width: Int,
    val height: Int
)

/**
 * Stub implementation for OpenCVMatcher without OpenCV dependency
 * In production, you would implement proper template matching
 */
class OpenCVMatcher {
    companion object {
        private const val TAG = "OpenCVMatcher"
        private const val DEFAULT_THRESHOLD = 0.8
    }

    fun findTemplate(
        sourceBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Double = DEFAULT_THRESHOLD
    ): MatchResult? {
        // Stub implementation - always returns null
        // In production, implement template matching without OpenCV
        Log.w(TAG, "Template matching not implemented - using yellow button detection instead")
        return null
    }

    fun findMultipleTemplates(
        sourceBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Double = DEFAULT_THRESHOLD,
        maxMatches: Int = 10
    ): List<MatchResult> {
        // Stub implementation - always returns empty list
        Log.w(TAG, "Multiple template matching not implemented")
        return emptyList()
    }

    fun findButton(
        screenBitmap: Bitmap,
        buttonText: String,
        expectedRegion: android.graphics.Rect? = null
    ): MatchResult? {
        // Stub implementation
        Log.w(TAG, "Button text matching not implemented")
        return null
    }
}