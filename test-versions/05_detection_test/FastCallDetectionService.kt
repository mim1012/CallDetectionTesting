package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.MainActivity
import com.kakao.taxi.test.module.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel

/**
 * 초고속 콜 감지 서비스
 * - 연속 캡처로 지연 최소화
 * - 메모리 캐싱으로 성능 최적화
 * - 병렬 처리로 분석 속도 향상
 */
class FastCallDetectionService : Service() {
    
    companion object {
        private const val TAG = "FastCallDetection"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "fast_detection_channel"
        
        const val ACTION_START = "ACTION_START_FAST_DETECTION"
        const val ACTION_STOP = "ACTION_STOP_FAST_DETECTION"
        
        // 초고속 설정
        const val CAPTURE_INTERVAL = 50L // 50ms = 초당 20프레임
        const val ANALYSIS_THREADS = 4 // 병렬 분석 스레드 수
        
        private var captureCount = 0
        private var startTime = 0L
        
        fun getCaptureCount(): Int = captureCount
        
        fun getFPS(): Float {
            val elapsed = System.currentTimeMillis() - startTime
            return if (elapsed > 0) {
                (captureCount * 1000f) / elapsed
            } else {
                0f
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureJob: Job? = null
    
    // 연속 캡처를 위한 변수들
    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader
    private lateinit var virtualDisplay: VirtualDisplay
    private val imageReaderThread = HandlerThread("ImageReader")
    private lateinit var imageReaderHandler: Handler
    
    // 성능 최적화를 위한 캐시
    private val yellowButtonCache = mutableMapOf<Int, ButtonCandidate>()
    private val lastDetectionTime = AtomicLong(0)
    private val isProcessing = AtomicBoolean(false)
    
    // 감지 모듈들
    private lateinit var enhancedRecognition: EnhancedImageRecognition
    private lateinit var smartClickSimulator: SmartClickSimulator
    private lateinit var yellowDetector: YellowButtonDetector
    
    // 클릭 대기열 (초고속 처리)
    private val clickQueue = Channel<ClickTask>(Channel.UNLIMITED)
    
    data class ClickTask(
        val x: Int,
        val y: Int,
        val timestamp: Long,
        val priority: Int = 0
    )
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initModules()
        startImageReaderThread()
        startClickProcessor()
    }
    
    private fun initModules() {
        enhancedRecognition = EnhancedImageRecognition()
        yellowDetector = YellowButtonDetector()
        
        // SmartClickSimulator 초기화
        KakaoTaxiAccessibilityService.getInstance()?.let { accessibilityInstance ->
            smartClickSimulator = SmartClickSimulator(accessibilityInstance)
        }
    }
    
    private fun startImageReaderThread() {
        imageReaderThread.start()
        imageReaderHandler = Handler(imageReaderThread.looper)
    }
    
    private fun startClickProcessor() {
        // 클릭 처리 전용 코루틴
        serviceScope.launch {
            for (task in clickQueue) {
                if (task.timestamp + 1000 > System.currentTimeMillis()) {
                    // 1초 이내의 클릭만 처리
                    performInstantClick(task.x, task.y)
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra("resultCode", -1)
                val data = intent.getParcelableExtra<Intent>("data")
                if (resultCode != -1 && data != null) {
                    startFastDetection(resultCode, data)
                }
            }
            ACTION_STOP -> stopFastDetection()
        }
        return START_STICKY
    }
    
    private fun startFastDetection(resultCode: Int, data: Intent) {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 통계 초기화
        startTime = System.currentTimeMillis()
        captureCount = 0
        
        // MediaProjection 설정
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        // 연속 캡처 설정
        setupContinuousCapture()
        
        // 초고속 감지 시작
        startUltraFastDetection()
    }
    
    private fun setupContinuousCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        // ImageReader 설정 (최적화된 포맷)
        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, // 더 빠른 포맷
            2 // 버퍼 2개로 지연 최소화
        )
        
        // 이미지 가용 리스너
        imageReader.setOnImageAvailableListener({ reader ->
            if (!isProcessing.get()) {
                isProcessing.set(true)
                processLatestImage(reader)
            }
        }, imageReaderHandler)
        
        // VirtualDisplay 생성
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FastCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )
    }
    
    private fun processLatestImage(reader: ImageReader) {
        serviceScope.launch {
            try {
                reader.acquireLatestImage()?.use { image ->
                    captureCount++
                    val bitmap = imageToBitmap(image)
                    analyzeScreenUltraFast(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image processing error", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
    
    private suspend fun analyzeScreenUltraFast(bitmap: Bitmap) = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        // 병렬로 여러 감지 방법 동시 실행
        val yellowButtonsDeferred = async {
            yellowDetector.detectAllYellowButtons(bitmap)
        }
        
        val enhancedButtonsDeferred = async {
            enhancedRecognition.detectButtonsParallel(bitmap)
        }
        
        // 결과 수집
        val yellowButtons = yellowButtonsDeferred.await()
        val enhancedButtons = enhancedButtonsDeferred.await()
        
        // 가장 신뢰도 높은 버튼 선택
        val allButtons = yellowButtons + enhancedButtons
        val sortedButtons = allButtons.sortedByDescending { it.confidence }
        
        if (sortedButtons.isNotEmpty()) {
            val bestButton = sortedButtons.first()
            
            // 캐시 확인 (중복 클릭 방지)
            val cacheKey = "${bestButton.centerX}_${bestButton.centerY}".hashCode()
            val lastDetection = yellowButtonCache[cacheKey]
            
            if (lastDetection == null || 
                System.currentTimeMillis() - lastDetectionTime.get() > 500) {
                
                // 즉시 클릭 큐에 추가 (필터 없이 바로 클릭)
                clickQueue.send(ClickTask(
                    x = bestButton.centerX,
                    y = bestButton.centerY,
                    timestamp = System.currentTimeMillis(),
                    priority = (bestButton.confidence * 100).toInt()
                ))
                
                Log.d(TAG, "⚡ 노란 버튼 발견 - 즉시 클릭! (${bestButton.centerX}, ${bestButton.centerY})")
                
                // 캐시 업데이트
                yellowButtonCache[cacheKey] = bestButton
                lastDetectionTime.set(System.currentTimeMillis())
                
                Log.d(TAG, "Button detected in ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }
    
    private suspend fun performInstantClick(x: Int, y: Int) {
        // 초고속 클릭 (지연 최소화)
        if (::smartClickSimulator.isInitialized) {
            val clicked = smartClickSimulator.performNaturalClick(x, y)
            if (clicked) {
                Log.d(TAG, "Ultra-fast click performed at ($x, $y)")
                sendClickNotification(x, y)
            }
        } else {
            // Fallback: 접근성 서비스 직접 사용
            val accessibilityService = KakaoTaxiAccessibilityService.getInstance()
            accessibilityService?.performGlobalClick(x, y)
        }
    }
    
    private fun startUltraFastDetection() {
        // 연속 감지 (중단 없이)
        captureJob = serviceScope.launch {
            while (isActive) {
                // ImageReader가 자동으로 처리하므로 대기만
                delay(CAPTURE_INTERVAL)
                
                // 주기적으로 캐시 정리
                if (System.currentTimeMillis() % 10000 < CAPTURE_INTERVAL) {
                    cleanupCache()
                }
            }
        }
    }
    
    private fun cleanupCache() {
        val currentTime = System.currentTimeMillis()
        yellowButtonCache.entries.removeIf { 
            currentTime - lastDetectionTime.get() > 5000 
        }
    }
    
    private fun sendClickNotification(x: Int, y: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ 초고속 콜 수락!")
            .setContentText("위치: ($x, $y)")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚀 초고속 감지 실행중")
            .setContentText("0.05초 간격으로 감지중...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "초고속 감지",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun stopFastDetection() {
        captureJob?.cancel()
        clickQueue.close()
        
        if (::virtualDisplay.isInitialized) virtualDisplay.release()
        if (::imageReader.isInitialized) imageReader.close()
        if (::mediaProjection.isInitialized) mediaProjection.stop()
        
        imageReaderThread.quitSafely()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopFastDetection()
    }
}

// 확장 함수: 접근성 서비스에 글로벌 클릭 추가
fun KakaoTaxiAccessibilityService.performGlobalClick(x: Int, y: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val path = android.graphics.Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 10 // 10ms 초고속 탭
                )
            )
            .build()
            
        dispatchGesture(gesture, null, null)
    }
}