package com.kakao.taxi.test.module

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 자연스러운 터치 패턴을 모방하는 스마트 클릭 시뮬레이터
 */
class SmartClickSimulator(private val service: AccessibilityService) {
    
    companion object {
        private const val TAG = "SmartClickSimulator"
    }
    
    /**
     * 자연스러운 터치 패턴으로 클릭
     */
    suspend fun performNaturalClick(x: Int, y: Int): Boolean {
        // 약간의 랜덤 오프셋 추가 (실제 손가락 터치처럼)
        val offsetX = Random.nextInt(-5, 6)
        val offsetY = Random.nextInt(-5, 6)
        val targetX = x + offsetX
        val targetY = y + offsetY
        
        // 터치 다운 → 약간의 움직임 → 터치 업
        val gesture = createNaturalGesture(targetX.toFloat(), targetY.toFloat())
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.dispatchGesture(gesture, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform natural click", e)
            false
        }
    }
    
    /**
     * 자연스러운 제스처 생성
     */
    private fun createNaturalGesture(x: Float, y: Float): GestureDescription {
        val path = Path()
        
        // 시작점
        path.moveTo(x, y)
        
        // 미세한 움직임 추가 (손가락 떨림 모방)
        val movements = 3
        for (i in 1..movements) {
            val microX = x + Random.nextFloat() * 2 - 1
            val microY = y + Random.nextFloat() * 2 - 1
            path.lineTo(microX, microY)
        }
        
        // 터치 지속 시간 (50-150ms 사이 랜덤)
        val duration = Random.nextLong(50, 150)
        
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }
    
    /**
     * 스와이프 제스처 (리스트 스크롤용)
     */
    suspend fun performSwipe(
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int, 
        duration: Long = 300
    ): Boolean {
        val path = Path()
        path.moveTo(startX.toFloat(), startY.toFloat())
        
        // 베지어 곡선으로 자연스러운 스와이프
        val midX = (startX + endX) / 2f
        val midY = (startY + endY) / 2f + Random.nextFloat() * 20 - 10
        
        path.quadTo(midX, midY, endX.toFloat(), endY.toFloat())
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.dispatchGesture(gesture, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform swipe", e)
            false
        }
    }
    
    /**
     * 더블 탭 제스처
     */
    suspend fun performDoubleTap(x: Int, y: Int): Boolean {
        val success1 = performNaturalClick(x, y)
        if (success1) {
            delay(Random.nextLong(50, 150)) // 탭 간격
            return performNaturalClick(x, y)
        }
        return false
    }
    
    /**
     * 롱 프레스 제스처
     */
    suspend fun performLongPress(x: Int, y: Int, duration: Long = 500): Boolean {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        // 롱프레스 중 미세한 움직임
        val microMovements = 5
        val timePerMovement = duration / microMovements
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.dispatchGesture(gesture, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform long press", e)
            false
        }
    }
}

/**
 * 클릭 패턴 학습 및 재현
 */
class ClickPatternLearner {
    
    private val clickHistory = mutableListOf<ClickPattern>()
    private val patternStats = mutableMapOf<String, PatternStatistics>()
    
    data class ClickPattern(
        val x: Int,
        val y: Int,
        val timestamp: Long,
        val duration: Long,
        val pressure: Float = 0.5f
    )
    
    data class PatternStatistics(
        var avgDuration: Long = 100,
        var avgInterval: Long = 1000,
        var avgPressure: Float = 0.5f,
        var count: Int = 0
    )
    
    /**
     * 사용자 클릭 패턴 학습
     */
    fun learnPattern(x: Int, y: Int, duration: Long) {
        val pattern = ClickPattern(x, y, System.currentTimeMillis(), duration)
        clickHistory.add(pattern)
        
        // 영역별 통계 업데이트
        val region = getRegion(x, y)
        val stats = patternStats.getOrPut(region) { PatternStatistics() }
        
        stats.avgDuration = (stats.avgDuration * stats.count + duration) / (stats.count + 1)
        stats.count++
        
        // 클릭 간격 계산
        if (clickHistory.size > 1) {
            val prevPattern = clickHistory[clickHistory.size - 2]
            val interval = pattern.timestamp - prevPattern.timestamp
            stats.avgInterval = (stats.avgInterval * (stats.count - 1) + interval) / stats.count
        }
    }
    
    /**
     * 학습된 패턴으로 클릭 시뮬레이션
     */
    fun getLearnedClickDuration(x: Int, y: Int): Long {
        val region = getRegion(x, y)
        val stats = patternStats[region]
        
        return if (stats != null && stats.count > 10) {
            // 학습된 패턴에 약간의 변화 추가
            val variation = Random.nextLong(-20, 21)
            (stats.avgDuration + variation).coerceIn(50, 200)
        } else {
            // 기본값
            Random.nextLong(80, 120)
        }
    }
    
    /**
     * 화면 영역 구분 (9분할)
     */
    private fun getRegion(x: Int, y: Int): String {
        // 화면을 3x3 그리드로 나눔
        val screenWidth = 1080 // 예시 값
        val screenHeight = 2400 // 예시 값
        
        val col = when {
            x < screenWidth / 3 -> 0
            x < screenWidth * 2 / 3 -> 1
            else -> 2
        }
        
        val row = when {
            y < screenHeight / 3 -> 0
            y < screenHeight * 2 / 3 -> 1
            else -> 2
        }
        
        return "$row,$col"
    }
    
    /**
     * 클릭 간격 예측
     */
    fun predictNextClickInterval(x: Int, y: Int): Long {
        val region = getRegion(x, y)
        val stats = patternStats[region]
        
        return if (stats != null && stats.count > 5) {
            // 학습된 간격에 변화 추가
            val variation = Random.nextLong(-200, 201)
            (stats.avgInterval + variation).coerceIn(500, 3000)
        } else {
            // 기본값
            Random.nextLong(1000, 2000)
        }
    }
}