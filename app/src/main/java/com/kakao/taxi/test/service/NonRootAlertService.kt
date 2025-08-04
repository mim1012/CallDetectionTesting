package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.R
import kotlinx.coroutines.*

/**
 * 비루팅 환경에서 배포 가능한 알림 서비스
 * 자동 클릭은 불가능하지만 최대한 빠른 수동 클릭을 돕는 서비스
 */
class NonRootAlertService : Service() {
    companion object {
        private const val TAG = "NonRootAlert"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "urgent_call_alert"
    }
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CALL_DETECTED" -> showUrgentAlert()
            "START_MONITORING" -> startCallMonitoring()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * 콜 감지 시 긴급 알림 표시
     */
    private fun showUrgentAlert() {
        // 1. 강력한 진동
        vibrateUrgent()
        
        // 2. 전체 화면 플래시
        flashScreen()
        
        // 3. 플로팅 버튼 표시
        showFloatingButton()
        
        // 4. 헤드업 알림
        showHeadsUpNotification()
        
        // 5. 소리 재생
        playAlertSound()
    }
    
    /**
     * 강력한 진동 패턴
     */
    private fun vibrateUrgent() {
        val pattern = longArrayOf(
            0, 500, 100, 500, 100, 500,  // SOS 패턴
            200, 1000  // 긴 진동
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
    
    /**
     * 화면 전체 플래시 효과
     */
    private fun flashScreen() {
        val flashView = View(this).apply {
            setBackgroundColor(0x80FFFF00.toInt()) // 반투명 노란색
        }
        
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
        }
        
        windowManager.addView(flashView, params)
        
        // 0.5초 후 제거
        serviceScope.launch {
            delay(500)
            windowManager.removeView(flashView)
        }
    }
    
    /**
     * 플로팅 클릭 버튼 표시
     */
    private fun showFloatingButton() {
        if (floatingView != null) return
        
        floatingView = LayoutInflater.from(this).inflate(
            android.R.layout.simple_list_item_1, null
        )
        
        val button = Button(this).apply {
            text = "🚕 콜 도착!\n여기를 눌러\n카카오 열기"
            textSize = 20f
            setBackgroundColor(0xFFFFE500.toInt()) // 카카오 노란색
            setTextColor(0xFF000000.toInt())
            setPadding(40, 40, 40, 40)
            
            setOnClickListener {
                // 카카오 드라이버 앱 열기
                val intent = packageManager.getLaunchIntentForPackage("com.kakao.driver")
                intent?.let {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                              Intent.FLAG_ACTIVITY_SINGLE_TOP or
                              Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(it)
                }
                
                // 버튼 제거
                removeFloatingButton()
                
                // 클릭 위치 안내
                showClickGuide()
            }
        }
        
        val container = FrameLayout(this).apply {
            addView(button)
        }
        
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            y = -200 // 화면 중앙 약간 위
        }
        
        floatingView = container
        windowManager.addView(container, params)
        
        // 애니메이션 효과
        animateFloatingButton(container)
    }
    
    /**
     * 플로팅 버튼 애니메이션
     */
    private fun animateFloatingButton(view: View) {
        serviceScope.launch {
            while (floatingView != null) {
                view.scaleX = 1.2f
                view.scaleY = 1.2f
                delay(300)
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                delay(300)
            }
        }
    }
    
    /**
     * 클릭 위치 가이드 표시
     */
    private fun showClickGuide() {
        val guideView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
            scaleX = 3f
            scaleY = 3f
        }
        
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            y = 200 // 수락 버튼 예상 위치
        }
        
        windowManager.addView(guideView, params)
        
        // 깜빡임 효과
        serviceScope.launch {
            repeat(10) {
                guideView.visibility = View.VISIBLE
                delay(200)
                guideView.visibility = View.INVISIBLE
                delay(200)
            }
            windowManager.removeView(guideView)
        }
    }
    
    /**
     * 헤드업 알림 표시
     */
    private fun showHeadsUpNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 긴급! 카카오 콜 도착!")
            .setContentText("지금 바로 수락하세요!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(createFullScreenIntent(), true)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    /**
     * 전체 화면 인텐트 생성
     */
    private fun createFullScreenIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage("com.kakao.driver")
            ?: Intent(this, NonRootAlertService::class.java)
        
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * 알림음 재생
     */
    private fun playAlertSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
            
            // 3초 후 중지
            serviceScope.launch {
                delay(3000)
                ringtone.stop()
            }
        } catch (e: Exception) {
            // 무시
        }
    }
    
    /**
     * 콜 모니터링 시작 (노티피케이션 리스너 대체)
     */
    private fun startCallMonitoring() {
        serviceScope.launch {
            while (true) {
                // 1초마다 카카오 앱 상태 체크
                checkKakaoAppState()
                delay(1000)
            }
        }
    }
    
    /**
     * 카카오 앱 실행 상태 확인
     */
    private fun checkKakaoAppState() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningApps = activityManager.runningAppProcesses
        
        runningApps?.forEach { process ->
            if (process.processName == "com.kakao.driver") {
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    // 카카오 드라이버가 포그라운드에 있음
                    // 추가 로직...
                }
            }
        }
    }
    
    private fun removeFloatingButton() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "긴급 콜 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "카카오 콜 도착 시 긴급 알림"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("콜 감지 서비스 실행 중")
            .setContentText("카카오 콜을 기다리고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        removeFloatingButton()
        serviceScope.cancel()
    }
}