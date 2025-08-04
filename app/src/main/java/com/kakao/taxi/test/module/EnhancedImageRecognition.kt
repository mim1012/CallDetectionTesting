package com.kakao.taxi.test.module

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 향상된 이미지 인식 모듈
 * 다양한 상황에서도 버튼을 정확히 감지
 */
class EnhancedImageRecognition {
    
    companion object {
        private const val TAG = "EnhancedRecognition"
    }
    
    // 카카오 노란색의 다양한 변형 감지
    private val yellowVariations = listOf(
        ColorRange(240..255, 200..240, 0..50),    // 표준 노란색
        ColorRange(220..255, 180..220, 0..80),    // 밝은 노란색
        ColorRange(200..240, 160..200, 0..100),   // 어두운 노란색
        ColorRange(255..255, 220..255, 0..60)     // 매우 밝은 노란색
    )
    
    data class ColorRange(val r: IntRange, val g: IntRange, val b: IntRange)
    
    /**
     * 병렬 처리로 빠른 버튼 감지
     */
    suspend fun detectButtonsParallel(bitmap: Bitmap): List<ButtonCandidate> = coroutineScope {
        val width = bitmap.width
        val height = bitmap.height
        val segments = 4 // 화면을 4개 구역으로 나눔
        
        val segmentHeight = height / segments
        val deferredResults = mutableListOf<Deferred<List<ButtonCandidate>>>()
        
        // 각 구역을 병렬로 분석
        for (i in 0 until segments) {
            val startY = i * segmentHeight
            val endY = if (i == segments - 1) height else (i + 1) * segmentHeight
            
            val deferred = async(Dispatchers.Default) {
                analyzeSegment(bitmap, 0, startY, width, endY)
            }
            deferredResults.add(deferred)
        }
        
        // 결과 수집
        val allCandidates = mutableListOf<ButtonCandidate>()
        deferredResults.forEach { deferred ->
            allCandidates.addAll(deferred.await())
        }
        
        // 중복 제거 및 병합
        return@coroutineScope mergeNearbyButtons(allCandidates)
    }
    
    /**
     * 화면 구역 분석
     */
    private fun analyzeSegment(
        bitmap: Bitmap, 
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int
    ): List<ButtonCandidate> {
        val candidates = mutableListOf<ButtonCandidate>()
        val visited = Array(endY - startY) { BooleanArray(endX - startX) }
        
        // 격자 스캔 (성능 최적화)
        val step = 5
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                if (!visited[y - startY][x - startX]) {
                    val pixel = bitmap.getPixel(x, y)
                    
                    // 다양한 노란색 변형 체크
                    if (isKakaoYellowVariation(pixel)) {
                        val bounds = findYellowRegion(
                            bitmap, x, y, startX, startY, endX, endY, visited
                        )
                        
                        if (isValidButton(bounds, bitmap)) {
                            candidates.add(createButtonCandidate(bounds, bitmap))
                        }
                    }
                }
            }
        }
        
        return candidates
    }
    
    /**
     * 다양한 노란색 변형 감지
     */
    private fun isKakaoYellowVariation(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        return yellowVariations.any { range ->
            r in range.r && g in range.g && b in range.b
        }
    }
    
    /**
     * 연결된 노란색 영역 찾기 (개선된 알고리즘)
     */
    private fun findYellowRegion(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        regionStartX: Int,
        regionStartY: Int,
        regionEndX: Int,
        regionEndY: Int,
        visited: Array<BooleanArray>
    ): android.graphics.Rect {
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        visited[startY - regionStartY][startX - regionStartX] = true
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            
            // 8방향 탐색
            for (dx in -2..2 step 2) {
                for (dy in -2..2 step 2) {
                    val nx = x + dx
                    val ny = y + dy
                    
                    if (nx in regionStartX until regionEndX && 
                        ny in regionStartY until regionEndY &&
                        !visited[ny - regionStartY][nx - regionStartX]) {
                        
                        val pixel = bitmap.getPixel(nx, ny)
                        if (isKakaoYellowVariation(pixel)) {
                            visited[ny - regionStartY][nx - regionStartX] = true
                            queue.add(Pair(nx, ny))
                            
                            minX = minOf(minX, nx)
                            maxX = maxOf(maxX, nx)
                            minY = minOf(minY, ny)
                            maxY = maxOf(maxY, ny)
                        }
                    }
                }
            }
        }
        
        return android.graphics.Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * 버튼 유효성 검증 (개선된 조건)
     */
    private fun isValidButton(bounds: android.graphics.Rect, bitmap: Bitmap): Boolean {
        val width = bounds.width()
        val height = bounds.height()
        
        // 다양한 해상도에 대응하는 조건
        val minWidth = bitmap.width * 0.1  // 화면 너비의 10% 이상
        val maxWidth = bitmap.width * 0.9  // 화면 너비의 90% 이하
        val minHeight = 30                 // 최소 높이 30px
        val maxHeight = bitmap.height * 0.3 // 화면 높이의 30% 이하
        
        // 가로세로 비율 체크 (버튼은 대체로 가로가 긴 형태)
        val aspectRatio = width.toFloat() / height
        val validAspectRatio = aspectRatio in 1.5f..8.0f
        
        return width >= minWidth && width <= maxWidth &&
               height >= minHeight && height <= maxHeight &&
               validAspectRatio
    }
    
    /**
     * 버튼 후보 생성
     */
    private fun createButtonCandidate(
        bounds: android.graphics.Rect, 
        bitmap: Bitmap
    ): ButtonCandidate {
        // 버튼 내부의 평균 색상 계산
        var totalR = 0
        var totalG = 0
        var totalB = 0
        var pixelCount = 0
        
        val step = 5
        for (y in bounds.top until bounds.bottom step step) {
            for (x in bounds.left until bounds.right step step) {
                val pixel = bitmap.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                pixelCount++
            }
        }
        
        val avgColor = if (pixelCount > 0) {
            Color.rgb(
                totalR / pixelCount,
                totalG / pixelCount,
                totalB / pixelCount
            )
        } else {
            Color.YELLOW
        }
        
        return ButtonCandidate(
            bounds = bounds,
            centerX = bounds.centerX(),
            centerY = bounds.centerY(),
            confidence = calculateConfidence(bounds, avgColor),
            avgColor = avgColor
        )
    }
    
    /**
     * 버튼 신뢰도 계산
     */
    private fun calculateConfidence(bounds: android.graphics.Rect, avgColor: Int): Float {
        var confidence = 0.5f
        
        // 색상이 표준 카카오 노란색에 가까울수록 높은 점수
        val r = Color.red(avgColor)
        val g = Color.green(avgColor)
        val b = Color.blue(avgColor)
        
        if (r in 240..255 && g in 200..240 && b in 0..50) {
            confidence += 0.3f
        }
        
        // 버튼 크기가 적절할수록 높은 점수
        val area = bounds.width() * bounds.height()
        if (area in 5000..50000) {
            confidence += 0.2f
        }
        
        return minOf(confidence, 1.0f)
    }
    
    /**
     * 인접한 버튼 병합
     */
    private fun mergeNearbyButtons(candidates: List<ButtonCandidate>): List<ButtonCandidate> {
        if (candidates.isEmpty()) return emptyList()
        
        val merged = mutableListOf<ButtonCandidate>()
        val used = BooleanArray(candidates.size)
        
        for (i in candidates.indices) {
            if (used[i]) continue
            
            var current = candidates[i]
            used[i] = true
            
            // 인접한 버튼 찾기
            for (j in i + 1 until candidates.size) {
                if (used[j]) continue
                
                if (isNearby(current.bounds, candidates[j].bounds)) {
                    // 병합
                    current = mergeButtons(current, candidates[j])
                    used[j] = true
                }
            }
            
            merged.add(current)
        }
        
        return merged
    }
    
    /**
     * 두 영역이 인접한지 확인
     */
    private fun isNearby(rect1: android.graphics.Rect, rect2: android.graphics.Rect): Boolean {
        val threshold = 20 // 20픽셀 이내면 인접
        
        return abs(rect1.left - rect2.right) <= threshold ||
               abs(rect2.left - rect1.right) <= threshold ||
               abs(rect1.top - rect2.bottom) <= threshold ||
               abs(rect2.top - rect1.bottom) <= threshold
    }
    
    /**
     * 두 버튼 병합
     */
    private fun mergeButtons(btn1: ButtonCandidate, btn2: ButtonCandidate): ButtonCandidate {
        val mergedBounds = android.graphics.Rect(
            minOf(btn1.bounds.left, btn2.bounds.left),
            minOf(btn1.bounds.top, btn2.bounds.top),
            maxOf(btn1.bounds.right, btn2.bounds.right),
            maxOf(btn1.bounds.bottom, btn2.bounds.bottom)
        )
        
        return ButtonCandidate(
            bounds = mergedBounds,
            centerX = mergedBounds.centerX(),
            centerY = mergedBounds.centerY(),
            confidence = maxOf(btn1.confidence, btn2.confidence),
            avgColor = btn1.avgColor // 첫 번째 버튼의 색상 사용
        )
    }
}