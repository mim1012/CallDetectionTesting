package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

/**
 * 앱 실행 시 자동으로 모든 기능 시작
 * ADB 명령어 필요 없음!
 */
class AutoStartService : Service() {
    
    companion object {
        private const val TAG = "AutoStart"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 자동 시작 서비스 실행")
        
        // 자동으로 모든 기능 시작
        startAllFeatures()
        
        return START_STICKY
    }
    
    private fun startAllFeatures() {
        serviceScope.launch {
            // 1. 접근성 서비스 확인
            checkAccessibility()
            
            // 2. 3초 후 자동 클릭 시작
            delay(3000)
            startAutoClicking()
            
            // 3. 카카오 앱 감지 시작
            startKakaoDetection()
        }
    }
    
    private fun checkAccessibility() {
        // 접근성 서비스가 켜져있는지 확인
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        if (enabled?.contains(packageName) == true) {
            Log.d(TAG, "✅ 접근성 서비스 활성화됨")
        } else {
            Log.d(TAG, "⚠️ 접근성 서비스를 켜주세요")
            // 설정 화면 열기
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }
    
    private fun startAutoClicking() {
        serviceScope.launch {
            Log.d(TAG, "🎯 자동 클릭 시작")
            
            // 알려진 수락 버튼 위치들
            val positions = listOf(
                Pair(540, 1800),  // 중앙 하단
                Pair(540, 1600),  // 중간
                Pair(720, 1800),  // 우측 하단
                Pair(360, 1800),  // 좌측 하단
                Pair(540, 2000),  // 최하단 (S24 Ultra)
                Pair(540, 1400)   // 상단
            )
            
            while (isActive) {
                // 카카오 앱이 실행 중인지 확인
                if (isKakaoRunning()) {
                    positions.forEach { (x, y) ->
                        performClick(x, y)
                        delay(300)
                    }
                }
                delay(1000) // 1초 대기
            }
        }
    }
    
    private fun isKakaoRunning(): Boolean {
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = activityManager.getRunningTasks(1)
            
            if (tasks.isNotEmpty()) {
                val topActivity = tasks[0].topActivity
                return topActivity?.packageName?.contains("kakao") == true
            }
        } catch (e: Exception) {
            // 권한 없으면 무시
        }
        return true // 기본값: 항상 시도
    }
    
    private fun performClick(x: Int, y: Int) {
        try {
            // 방법 1: Runtime exec
            Runtime.getRuntime().exec("input tap $x $y")
            Log.d(TAG, "클릭: ($x, $y)")
        } catch (e: Exception) {
            // 실패 시 다음 방법 시도
            try {
                // 방법 2: ProcessBuilder
                ProcessBuilder("sh", "-c", "input tap $x $y").start()
            } catch (e2: Exception) {
                // 방법 3: 접근성 서비스에 요청
                val intent = Intent("com.kakao.taxi.test.CLICK")
                intent.putExtra("x", x)
                intent.putExtra("y", y)
                sendBroadcast(intent)
            }
        }
    }
    
    private fun startKakaoDetection() {
        serviceScope.launch {
            while (isActive) {
                // 카카오 앱 실행 감지
                val pm = packageManager
                val intent = pm.getLaunchIntentForPackage("com.kakao.driver")
                
                if (intent != null) {
                    // 카카오 설치됨
                    Log.d(TAG, "✅ 카카오 드라이버 앱 감지됨")
                }
                
                delay(5000) // 5초마다 확인
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}