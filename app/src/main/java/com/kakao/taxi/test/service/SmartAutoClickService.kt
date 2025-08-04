package com.kakao.taxi.test.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * 똑똑한 자동 클릭 - 카카오 앱이 화면에 있을 때만!
 */
class SmartAutoClickService : AccessibilityService() {
    
    companion object {
        private const val TAG = "SmartClick"
        private const val KAKAO_PACKAGE = "com.kakao.driver"
        private const val KAKAO_T_PACKAGE = "com.kakao.t"
        
        // 기기별 최적 위치 (학습된 데이터)
        private val DEVICE_POSITIONS = when {
            Build.MODEL.contains("SM-S92") -> { // S24 시리즈
                listOf(Pair(540, 2000), Pair(540, 1900))
            }
            Build.MODEL.contains("SM-G98") -> { // S20 시리즈
                listOf(Pair(540, 1800), Pair(540, 1700))
            }
            Build.MODEL.contains("SM-G97") -> { // S10 시리즈
                listOf(Pair(540, 1600), Pair(540, 1500))
            }
            else -> { // 기본값
                listOf(Pair(540, 1800), Pair(540, 1600))
            }
        }
    }
    
    private var isKakaoActive = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ 스마트 클릭 서비스 시작")
        
        // 접근성 이벤트 설정
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf(KAKAO_PACKAGE, KAKAO_T_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        
        startSmartClicking()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // 카카오 앱 화면 전환 감지
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                isKakaoActive = (packageName == KAKAO_PACKAGE || packageName == KAKAO_T_PACKAGE)
                
                if (isKakaoActive) {
                    Log.d(TAG, "📱 카카오 앱 활성화 감지!")
                    
                    // 콜 화면인지 확인
                    val className = event.className?.toString() ?: ""
                    if (className.contains("CallActivity") || 
                        className.contains("OrderActivity") ||
                        className.contains("MainActivity")) {
                        
                        Log.d(TAG, "🎯 콜 화면 감지! 즉시 클릭!")
                        performImmediateClick()
                    }
                }
            }
        }
    }
    
    /**
     * 똑똑한 클릭 - 카카오 앱이 포그라운드일 때만
     */
    private fun startSmartClicking() {
        serviceScope.launch {
            while (isActive) {
                if (isKakaoInForeground()) {
                    // 카카오 앱이 화면에 있을 때만 클릭
                    Log.d(TAG, "카카오 앱 포그라운드 확인 - 클릭 시도")
                    
                    // 기기별 최적 위치만 클릭 (2곳)
                    DEVICE_POSITIONS.forEach { (x, y) ->
                        performClick(x, y)
                        delay(500) // 0.5초 간격
                    }
                } else {
                    Log.d(TAG, "카카오 앱 백그라운드 - 대기 중")
                }
                
                delay(2000) // 2초마다 체크
            }
        }
    }
    
    /**
     * 카카오 앱이 포그라운드인지 확인 (3가지 방법)
     */
    private fun isKakaoInForeground(): Boolean {
        // 방법 1: AccessibilityService 상태
        if (isKakaoActive) {
            return true
        }
        
        // 방법 2: UsageStats (Android 5.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val currentTime = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    currentTime - 1000 * 10, // 최근 10초
                    currentTime
                )
                
                if (stats != null && stats.isNotEmpty()) {
                    val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                    val topPackage = sortedStats.firstOrNull()?.packageName
                    
                    if (topPackage == KAKAO_PACKAGE || topPackage == KAKAO_T_PACKAGE) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // 권한 없으면 무시
            }
        }
        
        // 방법 3: ActivityManager (deprecated but still works)
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses
            
            runningApps?.forEach { process ->
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (process.processName == KAKAO_PACKAGE || process.processName == KAKAO_T_PACKAGE) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // 실패 시 무시
        }
        
        return false
    }
    
    /**
     * 즉시 클릭 (콜 화면 감지 시)
     */
    private fun performImmediateClick() {
        serviceScope.launch {
            // 빠르게 3번 클릭
            repeat(3) {
                DEVICE_POSITIONS.forEach { (x, y) ->
                    performClick(x, y)
                    delay(100) // 0.1초 간격으로 빠르게
                }
            }
        }
    }
    
    /**
     * 실제 클릭 수행
     */
    private fun performClick(x: Int, y: Int) {
        try {
            // input tap 명령
            val process = Runtime.getRuntime().exec("input tap $x $y")
            process.waitFor()
            Log.d(TAG, "✅ 클릭: ($x, $y)")
        } catch (e: Exception) {
            Log.e(TAG, "클릭 실패: ${e.message}")
        }
    }
    
    override fun onInterrupt() {
        // 서비스 중단
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

/**
 * 일반 Service 버전 (AccessibilityService 아닌 경우)
 */
class SmartAutoClickNormalService : Service() {
    
    companion object {
        private const val TAG = "SmartClickNormal"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSmartClicking()
        return START_STICKY
    }
    
    private fun startSmartClicking() {
        serviceScope.launch {
            while (isActive) {
                // 카카오 앱 실행 여부 체크
                if (isKakaoRunning()) {
                    // 기기에 맞는 2곳만 클릭
                    val positions = when {
                        Build.MODEL.contains("SM-S92") -> { // S24
                            listOf(Pair(540, 2000))
                        }
                        Build.MODEL.contains("SM-G97") -> { // S10
                            listOf(Pair(540, 1600))
                        }
                        else -> {
                            listOf(Pair(540, 1800))
                        }
                    }
                    
                    positions.forEach { (x, y) ->
                        Runtime.getRuntime().exec("input tap $x $y")
                        delay(500)
                    }
                }
                
                delay(2000) // 2초마다
            }
        }
    }
    
    private fun isKakaoRunning(): Boolean {
        try {
            // ps 명령으로 프로세스 확인
            val process = Runtime.getRuntime().exec("ps -A")
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            
            return output.contains("com.kakao.driver") || output.contains("com.kakao.t")
        } catch (e: Exception) {
            return false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}