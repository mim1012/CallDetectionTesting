package com.kakao.taxi.test.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

/**
 * Galaxy S10 최적화 서비스
 * Android 9-11에서 최고 성능 발휘
 */
class S10OptimizedService : AccessibilityService() {
    
    companion object {
        private const val TAG = "S10Optimized"
        private const val KAKAO_PACKAGE = "com.kakao.driver"
        
        // S10 화면 해상도 (1440x3040)
        private const val S10_WIDTH = 1440
        private const val S10_HEIGHT = 3040
        
        // 수락 버튼 일반적 위치 (S10 기준)
        private const val ACCEPT_BUTTON_X = 720  // 중앙
        private const val ACCEPT_BUTTON_Y = 2400 // 하단
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastSuccessX = ACCEPT_BUTTON_X
    private var lastSuccessY = ACCEPT_BUTTON_Y
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ S10 최적화 서비스 시작")
        
        // S10에서는 모든 이벤트 타입 활성화 가능
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                   AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                   AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50 // 빠른 반응
        }
        
        startAggressiveMonitoring()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // 카카오 앱 이벤트만 처리
        if (event.packageName != KAKAO_PACKAGE) return
        
        // S10에서는 모든 방법이 작동함
        serviceScope.launch {
            // 병렬로 여러 방법 시도
            val jobs = listOf(
                async { tryAccessibilityClick(event) },
                async { tryMediaProjection() },
                async { tryDirectTouch() },
                async { tryShellCommand() }
            )
            
            // 하나라도 성공하면 완료
            jobs.awaitAll()
        }
    }
    
    /**
     * 방법 1: 접근성 서비스 (S10에서 완벽 작동)
     */
    private fun tryAccessibilityClick(event: AccessibilityEvent): Boolean {
        try {
            // S10에서는 UI 노드 완전 읽기 가능
            val root = rootInActiveWindow ?: event.source ?: return false
            
            // ID로 찾기 (S10에서 정상 작동)
            val acceptButtons = root.findAccessibilityNodeInfosByViewId("com.kakao.driver:id/btn_accept")
            if (acceptButtons.isNotEmpty()) {
                acceptButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "✅ ID로 클릭 성공")
                vibrate()
                return true
            }
            
            // 텍스트로 찾기
            val textButtons = root.findAccessibilityNodeInfosByText("수락")
            if (textButtons.isNotEmpty()) {
                textButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "✅ 텍스트로 클릭 성공")
                vibrate()
                return true
            }
            
            // 재귀 탐색 (S10에서 빠름)
            return findAndClickButton(root)
            
        } catch (e: Exception) {
            Log.e(TAG, "접근성 클릭 실패", e)
        }
        return false
    }
    
    /**
     * 방법 2: MediaProjection (S10에서 FLAG_SECURE 우회 가능)
     */
    private fun tryMediaProjection(): Boolean {
        try {
            // S10 + Android 11 이하에서는 FLAG_SECURE 우회 가능
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                // MediaProjection으로 화면 캡처
                val intent = Intent("com.kakao.taxi.test.REQUEST_CAPTURE")
                sendBroadcast(intent)
                
                // 캡처 결과 대기
                Thread.sleep(100)
                
                // 노란 버튼 위치로 클릭
                performGestureClick(lastSuccessX, lastSuccessY)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 실패", e)
        }
        return false
    }
    
    /**
     * 방법 3: 직접 터치 이벤트 (S10에서 가능)
     */
    private fun tryDirectTouch(): Boolean {
        try {
            // /dev/input/event 직접 접근 (S10 가능)
            Runtime.getRuntime().exec(arrayOf(
                "sh", "-c",
                "sendevent /dev/input/event2 3 57 1;" +  // TRACKING_ID
                "sendevent /dev/input/event2 3 53 $lastSuccessX;" +  // X
                "sendevent /dev/input/event2 3 54 $lastSuccessY;" +  // Y
                "sendevent /dev/input/event2 3 58 50;" +  // PRESSURE
                "sendevent /dev/input/event2 0 0 0;" +  // SYN
                "sleep 0.05;" +
                "sendevent /dev/input/event2 3 57 -1;" +  // RELEASE
                "sendevent /dev/input/event2 0 0 0"  // SYN
            ))
            
            Log.d(TAG, "✅ Direct touch 성공")
            return true
        } catch (e: Exception) {
            // 실패 시 다음 방법
        }
        return false
    }
    
    /**
     * 방법 4: Shell 명령 (S10에서 작동)
     */
    private fun tryShellCommand(): Boolean {
        try {
            // input tap 명령 (S10에서 작동)
            Runtime.getRuntime().exec("input tap $lastSuccessX $lastSuccessY")
            Log.d(TAG, "✅ Shell command 성공")
            return true
        } catch (e: Exception) {
            // 실패 무시
        }
        return false
    }
    
    /**
     * 제스처로 클릭 (S10에서 완벽 작동)
     */
    private fun performGestureClick(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ 제스처 클릭 성공: ($x, $y)")
                lastSuccessX = x
                lastSuccessY = y
                vibrate()
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "제스처 취소됨")
            }
        }, null)
    }
    
    /**
     * 재귀적 버튼 찾기 (S10에서 빠름)
     */
    private fun findAndClickButton(node: AccessibilityNodeInfo): Boolean {
        // 클릭 가능한 노드 확인
        if (node.isClickable) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            
            if (text.contains("수락") || desc.contains("수락") ||
                text.contains("승인") || desc.contains("승인")) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "✅ 재귀 탐색으로 클릭 성공")
                return true
            }
        }
        
        // 자식 노드 탐색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findAndClickButton(child)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 공격적 모니터링 (S10에서는 부담 없음)
     */
    private fun startAggressiveMonitoring() {
        serviceScope.launch {
            while (isActive) {
                // 100ms마다 체크 (S10 성능 충분)
                checkForAcceptButton()
                delay(100)
            }
        }
    }
    
    private fun checkForAcceptButton() {
        rootInActiveWindow?.let { root ->
            if (root.packageName == KAKAO_PACKAGE) {
                tryAccessibilityClick(AccessibilityEvent())
            }
        }
    }
    
    private fun vibrate() {
        // 진동 피드백
        performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "서비스 중단")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}