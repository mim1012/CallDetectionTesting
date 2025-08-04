package com.kakao.taxi.test.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.R
import kotlinx.coroutines.*

/**
 * 하이브리드 접근법: 알림 모니터링 + 부분 자동화
 */
class HybridAutomationService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "HybridAutomation"
        private const val KAKAO_PACKAGE = "com.kakao.taxi.driver"
        private const val CHANNEL_ID = "hybrid_automation_channel"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isMonitoring = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startMonitoring()
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // 카카오 택시 알림 감지
        if (sbn?.packageName == KAKAO_PACKAGE) {
            analyzeKakaoNotification(sbn)
        }
    }
    
    private fun analyzeKakaoNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        
        // 알림 텍스트 추출
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        
        // 콜 관련 키워드 검색
        if (isCallNotification(title, text, bigText)) {
            // 고액 콜 패턴 분석
            val fare = extractFareAmount(text + bigText)
            if (fare >= 50000) { // 5만원 이상
                notifyHighValueCall(fare, text)
            }
        }
    }
    
    private fun isCallNotification(title: String, text: String, bigText: String): Boolean {
        val keywords = listOf("콜", "배차", "요청", "호출", "Call")
        val content = "$title $text $bigText".lowercase()
        return keywords.any { content.contains(it.lowercase()) }
    }
    
    private fun extractFareAmount(text: String): Int {
        // 금액 패턴 추출 (예: "50,000원", "5만원")
        val patterns = listOf(
            Regex("(\\\\d{1,3}(,\\\\d{3})*)\\\\s*원"),
            Regex("(\\\\d+)만\\\\s*원")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amount = match.groupValues[1].replace(",", "")
                return if (text.contains("만원")) {
                    amount.toIntOrNull()?.times(10000) ?: 0
                } else {
                    amount.toIntOrNull() ?: 0
                }
            }
        }
        return 0
    }
    
    private fun notifyHighValueCall(fare: Int, details: String) {
        // 사용자에게 고액 콜 알림
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚖 고액 콜 감지!")
            .setContentText("예상 요금: ${String.format("%,d", fare)}원")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        // 화면 켜기 (선택사항)
        wakeUpScreen()
    }
    
    private fun wakeUpScreen() {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "$TAG:WakeLock"
        )
        wakeLock.acquire(3000) // 3초간 화면 켜기
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "고액 콜 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "카카오 택시 고액 콜 알림"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startMonitoring() {
        isMonitoring = true
        // 주기적으로 상태 체크
        serviceScope.launch {
            while (isMonitoring) {
                checkActiveNotifications()
                delay(5000) // 5초마다 체크
            }
        }
    }
    
    private fun checkActiveNotifications() {
        val activeNotifications = activeNotifications
        activeNotifications?.forEach { sbn ->
            if (sbn.packageName == KAKAO_PACKAGE) {
                analyzeKakaoNotification(sbn)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        serviceScope.cancel()
    }
}