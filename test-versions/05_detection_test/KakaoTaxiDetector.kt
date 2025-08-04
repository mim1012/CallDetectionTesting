package com.kakao.taxi.test.module

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log

/**
 * 카카오 택시 기사용 앱 전용 감지기
 * 실제 카카오 택시 화면의 특징을 감지
 */
class KakaoTaxiDetector {
    companion object {
        private const val TAG = "KakaoTaxiDetector"
        
        // 카카오 택시 노란색 버튼 색상 범위 (더 넓게 설정)
        private const val KAKAO_YELLOW_R_MIN = 200  // 더 넓게
        private const val KAKAO_YELLOW_R_MAX = 255
        private const val KAKAO_YELLOW_G_MIN = 150  // 더 넓게
        private const val KAKAO_YELLOW_G_MAX = 240
        private const val KAKAO_YELLOW_B_MIN = 0
        private const val KAKAO_YELLOW_B_MAX = 120  // 더 넓게
        
        // 완료콜 삭제 버튼 위치 (대략적인 화면 비율)
        private const val DELETE_BUTTON_BOTTOM_RATIO = 0.9f
        private const val DELETE_BUTTON_CENTER_X_RATIO = 0.25f
    }
    
    /**
     * 콜 목록 화면인지 감지
     */
    fun isCallListScreen(bitmap: Bitmap): Boolean {
        // 상단에 "콜 목차가" 텍스트가 있는지 확인 (OCR 필요)
        // 여러 개의 노란색 "콜 수락" 버튼이 있는지 확인
        
        val yellowButtons = detectAllYellowButtons(bitmap)
        
        // 노란색 버튼이 1개 이상 있으면 일단 카카오 화면으로 판단
        if (yellowButtons.isNotEmpty()) {
            Log.d(TAG, "Kakao screen detected with ${yellowButtons.size} yellow button(s)")
            return true
        }
        
        return false
    }
    
    /**
     * 개별 콜 상세 화면인지 감지
     */
    fun isCallDetailScreen(bitmap: Bitmap): Boolean {
        // 하단에 큰 노란색 "콜 수락" 버튼이 하나만 있는지 확인
        val yellowButtons = detectAllYellowButtons(bitmap)
        
        // 노란색 버튼이 있으면 카카오 화면으로 판단
        if (yellowButtons.isNotEmpty()) {
            Log.d(TAG, "Kakao screen detected in detail check with ${yellowButtons.size} button(s)")
            return true
        }
        
        return false
    }
    
    /**
     * 모든 노란색 "콜 수락" 버튼 감지
     */
    fun detectAllYellowButtons(bitmap: Bitmap): List<ButtonCandidate> {
        val candidates = mutableListOf<ButtonCandidate>()
        val visited = Array(bitmap.height) { BooleanArray(bitmap.width) }
        
        // 화면 스캔
        for (y in 0 until bitmap.height step 10) {
            for (x in 0 until bitmap.width step 10) {
                if (!visited[y][x] && isKakaoYellow(bitmap.getPixel(x, y))) {
                    // 연결된 노란색 영역 찾기
                    val bounds = findYellowRegion(bitmap, x, y, visited)
                    
                    // 버튼 크기 조건 확인
                    if (isValidButtonSize(bounds, bitmap)) {
                        candidates.add(
                            ButtonCandidate(
                                bounds = bounds,
                                centerX = bounds.centerX(),
                                centerY = bounds.centerY(),
                                confidence = 0.9f,
                                avgColor = Color.rgb(250, 210, 25) // 카카오 노란색
                            )
                        )
                    }
                }
            }
        }
        
        return candidates
    }
    
    /**
     * 완료콜 삭제 버튼 위치 찾기
     * 화면 하단 "담기" 또는 "완료콜 삭제" 버튼
     */
    fun findDeleteCompletedCallButton(bitmap: Bitmap): ButtonCandidate? {
        // 화면 하단 중앙 왼쪽 영역 확인
        val bottomY = (bitmap.height * DELETE_BUTTON_BOTTOM_RATIO).toInt()
        val centerX = (bitmap.width * DELETE_BUTTON_CENTER_X_RATIO).toInt()
        
        // 회색/어두운 버튼 찾기
        val searchRect = Rect(
            centerX - 100,
            bottomY - 50,
            centerX + 100,
            bitmap.height
        )
        
        // 이 영역에서 버튼 찾기
        Log.d(TAG, "Looking for delete button in area: $searchRect")
        
        return ButtonCandidate(
            bounds = searchRect,
            centerX = centerX,
            centerY = bottomY,
            confidence = 0.7f,
            avgColor = Color.GRAY
        )
    }
    
    private fun isKakaoYellow(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        return r in KAKAO_YELLOW_R_MIN..KAKAO_YELLOW_R_MAX &&
               g in KAKAO_YELLOW_G_MIN..KAKAO_YELLOW_G_MAX &&
               b in KAKAO_YELLOW_B_MIN..KAKAO_YELLOW_B_MAX
    }
    
    private fun findYellowRegion(bitmap: Bitmap, startX: Int, startY: Int, visited: Array<BooleanArray>): Rect {
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        
        val queue = mutableListOf(Pair(startX, startY))
        visited[startY][startX] = true
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0)
            
            // 상하좌우 확인
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val nx = x + dx * 5
                    val ny = y + dy * 5
                    
                    if (nx in 0 until bitmap.width && 
                        ny in 0 until bitmap.height && 
                        !visited[ny][nx] &&
                        isKakaoYellow(bitmap.getPixel(nx, ny))) {
                        
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        
                        minX = minOf(minX, nx)
                        maxX = maxOf(maxX, nx)
                        minY = minOf(minY, ny)
                        maxY = maxOf(maxY, ny)
                    }
                }
            }
        }
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    private fun isValidButtonSize(bounds: Rect, bitmap: Bitmap): Boolean {
        val width = bounds.width()
        val height = bounds.height()
        
        // 버튼 크기 조건
        return width > bitmap.width * 0.15 &&  // 화면 폭의 15% 이상
               height > 30 &&                   // 최소 높이
               height < bitmap.height * 0.2 &&  // 화면 높이의 20% 이하
               width > height * 1.5             // 가로가 세로보다 길어야 함
    }
}