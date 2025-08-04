package com.kakao.taxi.test.module

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 실시간 콜 모니터링 모듈
 * - 콜 상태 변화 즉시 감지
 * - 새로운 콜 알림 0.1초 이내 감지
 */
class RealTimeCallMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "RealTimeCallMonitor"
        
        // 카카오 택시 UI 특징
        private const val NEW_CALL_COLOR = -256 // 새 콜 표시 색상 (빨간색)
        private const val CALL_LIST_TOP_Y = 200 // 콜 목록 시작 위치
        private const val CALL_ITEM_HEIGHT = 150 // 콜 아이템 높이
    }
    
    // 실시간 상태
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState
    
    // 콜 변화 감지용 캐시
    private val callHashCache = ConcurrentHashMap<Int, Long>()
    private var lastScreenHash = 0
    
    sealed class CallState {
        object Idle : CallState()
        data class NewCallDetected(
            val position: CallPosition,
            val timestamp: Long = System.currentTimeMillis()
        ) : CallState()
        data class CallAccepted(val callId: Int) : CallState()
    }
    
    data class CallPosition(
        val x: Int,
        val y: Int,
        val confidence: Float
    )
    
    /**
     * 화면에서 새 콜 감지 (초고속)
     */
    suspend fun detectNewCall(bitmap: Bitmap): CallPosition? = withContext(Dispatchers.Default) {
        val startTime = System.nanoTime()
        
        // 화면 해시로 빠른 변화 감지
        val currentHash = calculateScreenHash(bitmap)
        if (currentHash == lastScreenHash) {
            return@withContext null // 변화 없음
        }
        lastScreenHash = currentHash
        
        // 새 콜 영역만 스캔 (상단 영역)
        val newCallIndicator = findNewCallIndicator(bitmap)
        
        if (newCallIndicator != null) {
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            Log.d(TAG, "New call detected in ${elapsedMs}ms")
            
            _callState.value = CallState.NewCallDetected(newCallIndicator)
            return@withContext newCallIndicator
        }
        
        null
    }
    
    /**
     * 빠른 화면 해시 계산 (변화 감지용)
     */
    private fun calculateScreenHash(bitmap: Bitmap): Int {
        var hash = 17
        
        // 주요 위치만 샘플링하여 해시 계산
        val samplePoints = listOf(
            100 to 100,   // 상단 좌측
            bitmap.width - 100 to 100,  // 상단 우측
            bitmap.width / 2 to 200,     // 콜 목록 영역
            bitmap.width / 2 to 400,
            bitmap.width / 2 to 600
        )
        
        for ((x, y) in samplePoints) {
            if (x < bitmap.width && y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                hash = 31 * hash + pixel
            }
        }
        
        return hash
    }
    
    /**
     * 새 콜 표시 찾기 (빨간 점, NEW 배지 등)
     */
    private fun findNewCallIndicator(bitmap: Bitmap): CallPosition? {
        // 콜 목록 상단부터 스캔
        for (y in CALL_LIST_TOP_Y until minOf(CALL_LIST_TOP_Y + CALL_ITEM_HEIGHT * 3, bitmap.height) step 10) {
            for (x in 0 until bitmap.width step 20) {
                val pixel = bitmap.getPixel(x, y)
                
                // 새 콜 표시 색상 감지 (빨간색 계열)
                if (isNewCallColor(pixel)) {
                    // 주변 픽셀도 확인하여 정확도 향상
                    if (confirmNewCallArea(bitmap, x, y)) {
                        // 콜 수락 버튼 위치 계산
                        val buttonX = bitmap.width - 100 // 우측 버튼
                        val buttonY = y + CALL_ITEM_HEIGHT / 2
                        
                        return CallPosition(
                            x = buttonX,
                            y = buttonY,
                            confidence = 0.95f
                        )
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * 새 콜 색상 판별
     */
    private fun isNewCallColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // 빨간색 계열 (NEW 표시, 긴급 콜 등)
        return r > 200 && g < 100 && b < 100
    }
    
    /**
     * 새 콜 영역 확인
     */
    private fun confirmNewCallArea(bitmap: Bitmap, centerX: Int, centerY: Int): Boolean {
        var redPixelCount = 0
        val checkRadius = 20
        
        for (dy in -checkRadius..checkRadius step 5) {
            for (dx in -checkRadius..checkRadius step 5) {
                val x = centerX + dx
                val y = centerY + dy
                
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    if (isNewCallColor(bitmap.getPixel(x, y))) {
                        redPixelCount++
                    }
                }
            }
        }
        
        // 일정 이상의 빨간 픽셀이 있으면 새 콜로 판단
        return redPixelCount >= 5
    }
    
    /**
     * 노란 버튼 빠른 감지 (최적화 버전)
     */
    fun findYellowButtonFast(bitmap: Bitmap, nearY: Int): CallPosition? {
        // 특정 Y 좌표 근처만 스캔
        val scanStartY = maxOf(0, nearY - 50)
        val scanEndY = minOf(bitmap.height, nearY + 50)
        
        // 버튼이 주로 나타나는 우측 영역 집중 스캔
        val scanStartX = bitmap.width / 2
        
        for (y in scanStartY until scanEndY step 5) {
            for (x in scanStartX until bitmap.width step 10) {
                val pixel = bitmap.getPixel(x, y)
                
                if (isKakaoYellow(pixel)) {
                    // 버튼 중앙 찾기
                    val buttonBounds = findButtonBounds(bitmap, x, y)
                    if (buttonBounds != null) {
                        return CallPosition(
                            x = buttonBounds.centerX(),
                            y = buttonBounds.centerY(),
                            confidence = 0.98f
                        )
                    }
                }
            }
        }
        
        return null
    }
    
    private fun isKakaoYellow(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        return r in 240..255 && g in 200..240 && b < 50
    }
    
    private fun findButtonBounds(bitmap: Bitmap, startX: Int, startY: Int): android.graphics.Rect? {
        // 간단한 플러드필 알고리즘으로 버튼 영역 찾기
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        
        // 우측으로 확장
        for (x in startX until minOf(startX + 200, bitmap.width)) {
            if (!isKakaoYellow(bitmap.getPixel(x, startY))) break
            maxX = x
        }
        
        // 좌측으로 확장
        for (x in startX downTo maxOf(startX - 200, 0)) {
            if (!isKakaoYellow(bitmap.getPixel(x, startY))) break
            minX = x
        }
        
        // 아래로 확장
        for (y in startY until minOf(startY + 100, bitmap.height)) {
            if (!isKakaoYellow(bitmap.getPixel((minX + maxX) / 2, y))) break
            maxY = y
        }
        
        // 위로 확장
        for (y in startY downTo maxOf(startY - 100, 0)) {
            if (!isKakaoYellow(bitmap.getPixel((minX + maxX) / 2, y))) break
            minY = y
        }
        
        val width = maxX - minX
        val height = maxY - minY
        
        // 유효한 버튼 크기인지 확인
        return if (width in 50..400 && height in 30..150) {
            android.graphics.Rect(minX, minY, maxX, maxY)
        } else {
            null
        }
    }
    
    /**
     * 콜 상태 초기화
     */
    fun reset() {
        _callState.value = CallState.Idle
        callHashCache.clear()
        lastScreenHash = 0
    }
}