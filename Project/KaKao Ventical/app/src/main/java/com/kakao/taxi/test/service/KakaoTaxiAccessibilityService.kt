package com.kakao.taxi.test.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.kakao.taxi.test.module.ClickEventHandler
import kotlinx.coroutines.*

/**
 * 접근성 서비스를 이용한 자동화
 * 일반 사용자 배포용
 */
class KakaoTaxiAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "KakaoTaxiAccessibility"
        private const val KAKAO_TAXI_PACKAGE = "com.kakao.taxi.driver" // 카카오 T 기사용 앱
        
        @Volatile
        private var instance: KakaoTaxiAccessibilityService? = null
        
        @Volatile
        var isConnected = false
            private set
            
        @Volatile
        var lastKakaoDetection = 0L
            private set
            
        @Volatile
        var isKakaoAccessible = false
            private set
            
        @Volatile
        var kakaoBlockReason = ""
            private set
        
        fun getInstance() = instance
        
        fun getStatus() = ServiceStatus(
            isConnected = isConnected,
            isKakaoAccessible = isKakaoAccessible,
            lastKakaoDetection = lastKakaoDetection,
            blockReason = kakaoBlockReason
        )
    }
    
    data class ServiceStatus(
        val isConnected: Boolean,
        val isKakaoAccessible: Boolean,
        val lastKakaoDetection: Long,
        val blockReason: String
    )
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isAutoClickEnabled = false
    
    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ClickEventHandler.ACTION_PERFORM_CLICK -> {
                    val x = intent.getIntExtra("x", 0)
                    val y = intent.getIntExtra("y", 0)
                    performClickAtCoordinate(x, y)
                }
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        
        // 접근성 서비스 설정
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            packageNames = arrayOf(KAKAO_TAXI_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }
        
        Log.d(TAG, "Accessibility Service Connected")
        showToast("카카오 택시 자동화 서비스 시작")
        
        // Register click receiver
        val filter = IntentFilter(ClickEventHandler.ACTION_PERFORM_CLICK)
        registerReceiver(clickReceiver, filter)
        
        // 카카오 앱 접근성 테스트
        testKakaoAccessibility()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 카카오 택시 앱 이벤트 감지
        if (event.packageName == KAKAO_TAXI_PACKAGE) {
            lastKakaoDetection = System.currentTimeMillis()
            
            // 접근성 차단 여부 체크
            checkKakaoAccessibility(event)
            
            if (!isAutoClickEnabled) return
            
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    checkAndClickAcceptButton()
                }
            }
        }
    }
    
    /**
     * 콜 수락 버튼 찾기 및 클릭
     */
    private fun checkAndClickAcceptButton() {
        serviceScope.launch {
            delay(500) // 화면 로딩 대기
            
            rootInActiveWindow?.let { root ->
                // 1. 텍스트로 버튼 찾기
                val acceptButtons = findNodesByText(root, "수락", "콜 받기", "배차 수락")
                
                for (button in acceptButtons) {
                    if (shouldAcceptCall(button)) {
                        performClickOnNode(button)
                        Log.d(TAG, "콜 수락 버튼 클릭됨")
                        break
                    }
                }
                
                // 2. 노란색 버튼 찾기 (ID나 설명으로)
                if (acceptButtons.isEmpty()) {
                    findYellowButton(root)?.let { yellowButton ->
                        if (shouldAcceptCall(yellowButton)) {
                            performClickOnNode(yellowButton)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 텍스트로 노드 찾기
     */
    private fun findNodesByText(node: AccessibilityNodeInfo, vararg texts: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        for (text in texts) {
            node.findAccessibilityNodeInfosByText(text).forEach { 
                if (it.isClickable) {
                    results.add(it)
                }
            }
        }
        
        return results
    }
    
    /**
     * 노란색 버튼 찾기 (휴리스틱)
     */
    private fun findYellowButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = mutableListOf(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            
            // 버튼이고, 화면 하단에 있으며, 클릭 가능한 경우
            if (node.className == "android.widget.Button" && 
                node.isClickable && 
                isAtBottomOfScreen(node)) {
                
                // 추가 조건: 텍스트가 비어있지 않음
                if (!node.text.isNullOrEmpty()) {
                    return node
                }
            }
            
            // 자식 노드 탐색
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return null
    }
    
    /**
     * 화면 하단에 있는지 확인
     */
    private fun isAtBottomOfScreen(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        val screenHeight = resources.displayMetrics.heightPixels
        return rect.bottom > screenHeight * 0.7
    }
    
    /**
     * 콜 수락 조건 확인
     */
    private fun shouldAcceptCall(node: AccessibilityNodeInfo): Boolean {
        // 주변 텍스트에서 거리/금액 정보 추출
        val parentNode = node.parent ?: return true
        
        var distanceOk = true
        var amountOk = true
        
        // 형제 노드들에서 정보 찾기
        for (i in 0 until parentNode.childCount) {
            val sibling = parentNode.getChild(i) ?: continue
            val text = sibling.text?.toString() ?: continue
            
            // 거리 확인
            if (text.contains("km")) {
                val distance = extractDistance(text)
                distanceOk = distance in 1.0f..8.0f
            }
            
            // 금액 확인
            if (text.contains("원")) {
                val amount = extractAmount(text)
                amountOk = amount in 5000..30000
            }
        }
        
        return distanceOk && amountOk
    }
    
    /**
     * 노드 클릭
     */
    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 제스처로 클릭
            val rect = Rect()
            node.getBoundsInScreen(rect)
            performGestureClick(rect.centerX(), rect.centerY())
        } else {
            // 직접 클릭
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    
    /**
     * 제스처로 클릭
     */
    private fun performGestureClick(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
            
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * 거리 추출
     */
    private fun extractDistance(text: String): Float {
        val regex = """(\d+\.?\d*)\s*km""".toRegex()
        return regex.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 999f
    }
    
    /**
     * 금액 추출
     */
    private fun extractAmount(text: String): Int {
        val regex = """(\d{1,3}(,\d{3})*|\d+)\s*원""".toRegex()
        val match = regex.find(text)?.groupValues?.get(1)?.replace(",", "")
        return match?.toIntOrNull() ?: 0
    }
    
    /**
     * 자동 클릭 활성화/비활성화
     */
    fun setAutoClickEnabled(enabled: Boolean) {
        isAutoClickEnabled = enabled
        showToast(if (enabled) "자동 클릭 시작" else "자동 클릭 중지")
    }
    
    /**
     * 완료콜 삭제 수행
     */
    fun deleteCompletedCalls() {
        rootInActiveWindow?.let { root ->
            // "담기", "완료콜 삭제" 버튼 찾기
            val deleteButtons = findNodesByText(root, "담기", "완료콜 삭제", "삭제")
            deleteButtons.firstOrNull()?.let {
                performClickOnNode(it)
                Log.d(TAG, "완료콜 삭제 버튼 클릭")
            }
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
    
    /**
     * 좌표로 클릭 수행
     */
    fun performClickAtCoordinate(x: Int, y: Int) {
        Log.d(TAG, "Performing click at ($x, $y)")
        performGestureClick(x, y)
    }
    
    /**
     * 카카오 앱 접근성 테스트
     */
    private fun testKakaoAccessibility() {
        serviceScope.launch {
            delay(1000) // 서비스 완전 초기화 대기
            
            try {
                // 현재 활성 윈도우 확인
                val windows = windows
                val kakaoWindow = windows.find { it.root?.packageName == KAKAO_TAXI_PACKAGE }
                
                if (kakaoWindow != null) {
                    isKakaoAccessible = kakaoWindow.root != null
                    if (!isKakaoAccessible) {
                        kakaoBlockReason = "카카오 앱이 접근성 노드를 제공하지 않음"
                    }
                } else {
                    // 카카오 앱이 실행 중이 아님
                    isKakaoAccessible = false
                    kakaoBlockReason = "카카오 택시 앱이 실행되지 않음"
                }
            } catch (e: Exception) {
                isKakaoAccessible = false
                kakaoBlockReason = "접근성 테스트 실패: ${e.message}"
            }
            
            // 상태 브로드캐스트
            sendAccessibilityStatus()
        }
    }
    
    /**
     * 카카오 앱 접근성 체크
     */
    private fun checkKakaoAccessibility(event: AccessibilityEvent) {
        try {
            // 이벤트 소스 확인
            val source = event.source
            if (source == null) {
                isKakaoAccessible = false
                kakaoBlockReason = "이벤트 소스 없음 (앱이 접근성을 차단했을 가능성)"
                sendAccessibilityStatus()
                return
            }
            
            // 루트 노드 접근 가능 여부
            val root = rootInActiveWindow
            if (root == null || root.packageName != KAKAO_TAXI_PACKAGE) {
                isKakaoAccessible = false
                kakaoBlockReason = "루트 노드 접근 불가 (보안 설정으로 차단됨)"
                sendAccessibilityStatus()
                return
            }
            
            // 자식 노드 확인
            if (root.childCount == 0) {
                isKakaoAccessible = false
                kakaoBlockReason = "UI 요소 접근 불가 (앱이 접근성을 제한함)"
            } else {
                isKakaoAccessible = true
                kakaoBlockReason = ""
            }
            
            sendAccessibilityStatus()
            
        } catch (e: SecurityException) {
            isKakaoAccessible = false
            kakaoBlockReason = "보안 예외: ${e.message}"
            sendAccessibilityStatus()
        } catch (e: Exception) {
            isKakaoAccessible = false
            kakaoBlockReason = "접근성 확인 실패: ${e.message}"
            sendAccessibilityStatus()
        }
    }
    
    /**
     * 접근성 상태 브로드캐스트
     */
    private fun sendAccessibilityStatus() {
        val intent = Intent("com.kakao.taxi.test.ACCESSIBILITY_STATUS").apply {
            putExtra("isConnected", isConnected)
            putExtra("isKakaoAccessible", isKakaoAccessible)
            putExtra("lastDetection", lastKakaoDetection)
            putExtra("blockReason", kakaoBlockReason)
        }
        sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isConnected = false
        serviceScope.cancel()
        try {
            unregisterReceiver(clickReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}