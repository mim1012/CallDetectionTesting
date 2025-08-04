package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.MainActivity
import kotlinx.coroutines.*

/**
 * 원클릭 자동 서비스
 * 권한만 한번 받으면 모든 것이 자동으로 실행됨
 */
class OneClickAutoService : Service() {
    
    companion object {
        private const val TAG = "OneClickAutoService"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "one_click_auto_channel"
        
        const val ACTION_START_AUTO = "ACTION_START_AUTO"
        const val ACTION_STOP_AUTO = "ACTION_STOP_AUTO"
        
        // MediaProjection 데이터 저장
        var mediaProjectionResultCode: Int = -1
        var mediaProjectionData: Intent? = null
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUTO -> {
                // MediaProjection 데이터 저장
                mediaProjectionResultCode = intent.getIntExtra("resultCode", -1)
                mediaProjectionData = intent.getParcelableExtra("data")
                
                startAutoMode()
            }
            ACTION_STOP_AUTO -> {
                stopAutoMode()
            }
        }
        return START_STICKY
    }
    
    private fun startAutoMode() {
        startForeground(NOTIFICATION_ID, createNotification())
        
        Log.d(TAG, "🚀 원클릭 자동 모드 시작!")
        
        monitoringJob = serviceScope.launch {
            // 1단계: 모든 서비스 시작
            startAllServices()
            
            // 2단계: 카카오 앱 대기
            waitForKakaoApp()
            
            // 3단계: 무한 감지 루프
            startInfiniteDetectionLoop()
        }
    }
    
    private suspend fun startAllServices() {
        Log.d(TAG, "📱 모든 서비스 시작 중...")
        
        // 1. 화면 캡처 서비스 시작
        if (mediaProjectionResultCode != -1 && mediaProjectionData != null) {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, mediaProjectionResultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, mediaProjectionData)
            }
            startService(captureIntent)
        }
        
        // 2. 플로팅 컨트롤 시작
        val floatingIntent = Intent(this, FloatingControlService::class.java).apply {
            action = FloatingControlService.ACTION_SHOW_CONTROLS
        }
        startService(floatingIntent)
        
        // 3. 자동 감지 서비스 시작
        val autoIntent = Intent(this, AutoDetectionService::class.java).apply {
            action = AutoDetectionService.ACTION_START_DETECTION
        }
        startService(autoIntent)
        
        delay(2000) // 서비스들이 시작될 시간 대기
        Log.d(TAG, "✅ 모든 서비스 시작 완료")
    }
    
    private suspend fun waitForKakaoApp() {
        Log.d(TAG, "⏳ 카카오 택시 앱 실행 대기 중...")
        
        while (monitoringJob?.isActive == true) {
            val accessibilityStatus = KakaoTaxiAccessibilityService.getStatus()
            
            if (accessibilityStatus.isKakaoAccessible) {
                Log.d(TAG, "✅ 카카오 택시 앱 감지됨!")
                break
            }
            
            delay(1000) // 1초마다 체크
        }
    }
    
    private suspend fun startInfiniteDetectionLoop() {
        Log.d(TAG, "🔄 무한 감지 루프 시작")
        
        var consecutiveFailures = 0
        val maxFailures = 10
        
        while (monitoringJob?.isActive == true) {
            try {
                // 화면 캡처 요청
                val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_CAPTURE_ONCE
                }
                startService(captureIntent)
                
                // 캡처 결과 대기
                delay(100)
                
                // 비트맵 확인
                val bitmap = ScreenCaptureService.capturedBitmap
                if (bitmap != null) {
                    consecutiveFailures = 0
                    
                    // 노란 버튼 감지
                    val detector = com.kakao.taxi.test.module.YellowButtonDetector()
                    val buttons = detector.detectAllYellowButtons(bitmap)
                    
                    if (buttons.isNotEmpty()) {
                        val bestButton = buttons.maxByOrNull { it.confidence }
                        bestButton?.let { button ->
                            Log.d(TAG, "🎯 콜 버튼 발견! 위치: (${button.centerX}, ${button.centerY})")
                            
                            // 즉시 클릭 시도
                            val clicked = performAutoClick(button.centerX, button.centerY)
                            if (clicked) {
                                Log.d(TAG, "✅ 자동 클릭 성공!")
                                sendSuccessNotification(button.centerX, button.centerY)
                                
                                // 성공 후 잠시 대기 (중복 클릭 방지)
                                delay(3000)
                            }
                        }
                    }
                    
                    // 비트맵 정리
                    ScreenCaptureService.capturedBitmap = null
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= maxFailures) {
                        Log.e(TAG, "❌ 연속 캡처 실패 $maxFailures 회, 서비스 재시작")
                        restartServices()
                        consecutiveFailures = 0
                    }
                }
                
                delay(50) // 0.05초 간격으로 초고속 감지
                
            } catch (e: Exception) {
                Log.e(TAG, "감지 루프 오류", e)
                delay(1000)
            }
        }
    }
    
    private suspend fun performAutoClick(x: Int, y: Int): Boolean {
        return try {
            val accessibilityService = KakaoTaxiAccessibilityService.getInstance()
            if (accessibilityService != null) {
                // 접근성 서비스로 클릭
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val path = android.graphics.Path()
                    path.moveTo(x.toFloat(), y.toFloat())
                    
                    val gesture = android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(
                            android.accessibilityservice.GestureDescription.StrokeDescription(
                                path, 0, 50 // 50ms 클릭
                            )
                        )
                        .build()
                    
                    accessibilityService.dispatchGesture(gesture, null, null)
                    true
                } else {
                    false
                }
            } else {
                Log.w(TAG, "접근성 서비스 없음")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "클릭 실패", e)
            false
        }
    }
    
    private suspend fun restartServices() {
        Log.d(TAG, "🔄 서비스 재시작 중...")
        
        // 모든 서비스 중지
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, AutoDetectionService::class.java))
        
        delay(1000)
        
        // 다시 시작
        startAllServices()
    }
    
    private fun sendSuccessNotification(x: Int, y: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎯 콜 자동 수락 성공!")
            .setContentText("위치: ($x, $y)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
    
    private fun stopAutoMode() {
        monitoringJob?.cancel()
        
        // 모든 서비스 중지
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, AutoDetectionService::class.java))
        stopService(Intent(this, FloatingControlService::class.java))
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "🛑 원클릭 자동 모드 중지")
    }
    
    private fun createNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OneClickAutoService::class.java).apply {
                action = ACTION_STOP_AUTO
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚀 원클릭 자동 모드")
            .setContentText("카카오 택시 콜 자동 감지/수락 중...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_delete, "중지", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "원클릭 자동 모드",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "카카오 택시 자동 감지 및 수락"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}