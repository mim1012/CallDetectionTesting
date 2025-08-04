package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 부분 캡처 서비스
 * 전체 화면 대신 하단 30% 영역만 캡처하여 성능 극대화
 */
class PartialCaptureService : Service() {
    
    companion object {
        private const val TAG = "PartialCaptureService"
        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "partial_capture_channel"
        
        const val ACTION_START_PARTIAL = "ACTION_START_PARTIAL"
        const val ACTION_STOP_PARTIAL = "ACTION_STOP_PARTIAL"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        
        @Volatile
        var capturedBitmap: Bitmap? = null
        
        @Volatile
        var isCapturing: Boolean = false
        
        // 캡처 영역 설정 (카카오 택시 버튼은 주로 하단에 위치)
        const val CAPTURE_REGION_TOP_RATIO = 0.7f    // 상단 70%부터
        const val CAPTURE_REGION_BOTTOM_RATIO = 1.0f // 하단 100%까지
        const val CAPTURE_REGION_LEFT_RATIO = 0.0f   // 좌측 0%부터  
        const val CAPTURE_REGION_RIGHT_RATIO = 1.0f  // 우측 100%까지
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureCallback: ((Bitmap) -> Unit)? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PARTIAL -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode != -1 && data != null) {
                    startPartialCapture(resultCode, data)
                }
            }
            ACTION_STOP_PARTIAL -> {
                stopPartialCapture()
            }
        }
        return START_STICKY
    }
    
    private fun startPartialCapture(resultCode: Int, data: Intent) {
        startForeground(NOTIFICATION_ID, createNotification())
        
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        isCapturing = true
        setupPartialVirtualDisplay()
        
        Log.d(TAG, "부분 캡처 시작 - 하단 30% 영역")
    }
    
    private fun setupPartialVirtualDisplay() {
        val metrics = resources.displayMetrics
        val fullWidth = metrics.widthPixels
        val fullHeight = metrics.heightPixels
        val density = metrics.densityDpi
        
        // 캡처할 영역 계산 (하단 30%)
        val captureTop = (fullHeight * CAPTURE_REGION_TOP_RATIO).toInt()
        val captureHeight = fullHeight - captureTop
        val captureWidth = fullWidth
        
        Log.d(TAG, "캡처 영역: ${captureWidth}x${captureHeight} (전체: ${fullWidth}x${fullHeight})")
        
        // 부분 영역만 캡처하도록 ImageReader 설정
        imageReader = ImageReader.newInstance(
            captureWidth, 
            captureHeight, 
            PixelFormat.RGBA_8888, 
            2
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                try {
                    // 부분 비트맵 생성
                    val partialBitmap = imageToPartialBitmap(it, captureWidth, captureHeight, captureTop)
                    capturedBitmap = partialBitmap
                    captureCallback?.invoke(partialBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "부분 비트맵 생성 실패", e)
                } finally {
                    it.close()
                }
            }
        }, null)
        
        // VirtualDisplay는 전체 화면으로 생성 (MediaProjection 제약)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PartialCapture",
            fullWidth, fullHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }
    
    private fun imageToPartialBitmap(image: Image, width: Int, height: Int, topOffset: Int): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        // 전체 비트맵 생성
        val fullBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(buffer)
        
        // 필요한 부분만 잘라내기 (하단 30%)
        val partialBitmap = Bitmap.createBitmap(
            fullBitmap,
            0, topOffset,           // 시작점: (0, 상단 70% 지점)
            image.width, height     // 크기: 전체 너비 x 하단 30% 높이
        )
        
        // 전체 비트맵 해제 (메모리 절약)
        fullBitmap.recycle()
        
        return partialBitmap
    }
    
    private fun stopPartialCapture() {
        isCapturing = false
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        capturedBitmap?.recycle()
        capturedBitmap = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "부분 캡처 중지")
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📷 부분 캡처 모드")
            .setContentText("하단 30% 영역만 캡처 중...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "부분 캡처",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "화면 하단 부분만 캡처"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun setOnCaptureListener(callback: (Bitmap) -> Unit) {
        this.captureCallback = callback
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopPartialCapture()
    }
}

/**
 * 스마트 영역 캡처 서비스
 * 동적으로 관심 영역을 조정하여 최적 성능 달성
 */
class SmartRegionCaptureService : Service() {
    
    companion object {
        private const val TAG = "SmartRegionCapture"
        
        // 동적 영역 조정
        data class CaptureRegion(
            val x: Int,
            val y: Int, 
            val width: Int,
            val height: Int,
            val priority: Float = 1.0f
        )
        
        // 카카오 택시 UI 영역들
        val KAKAO_UI_REGIONS = listOf(
            CaptureRegion(0, 1600, 1080, 800, 2.0f),      // 하단 버튼 영역 (최우선)
            CaptureRegion(0, 800, 1080, 400, 1.5f),       // 중앙 콜 정보 영역
            CaptureRegion(0, 0, 1080, 300, 1.0f)          // 상단 상태 영역
        )
    }
    
    private var currentRegion: CaptureRegion? = null
    private var lastButtonFound: Long = 0
    
    fun adaptCaptureRegion(foundButton: Boolean, buttonY: Int?) {
        if (foundButton && buttonY != null) {
            // 버튼 발견 시 해당 영역 집중 캡처
            val focusHeight = 200
            currentRegion = CaptureRegion(
                x = 0,
                y = maxOf(0, buttonY - focusHeight/2),
                width = 1080,
                height = focusHeight,
                priority = 3.0f
            )
            lastButtonFound = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - lastButtonFound > 5000) {
            // 5초간 버튼 없으면 전체 영역으로 복귀
            currentRegion = null
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}