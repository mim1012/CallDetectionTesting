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
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.MainActivity
import com.kakao.taxi.test.R
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val PREFS_NAME = "screen_capture_prefs"
        private const val KEY_IS_CAPTURING = "is_capturing"
        
        const val ACTION_START_CAPTURE = "ACTION_START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "ACTION_STOP_CAPTURE"
        const val ACTION_CAPTURE_ONCE = "ACTION_CAPTURE_ONCE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        
        @Volatile
        var capturedBitmap: Bitmap? = null
        
        @Volatile
        var isCapturing: Boolean = false
        
        fun updateCaptureState(context: Context, capturing: Boolean) {
            isCapturing = capturing
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_CAPTURING, capturing)
                .apply()
        }
        
        fun loadCaptureState(context: Context): Boolean {
            val capturing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_CAPTURING, false)
            isCapturing = capturing
            return capturing
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureCallback: ((Bitmap) -> Unit)? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 서비스 시작시 저장된 상태 로드
        loadCaptureState(this)
        Log.d(TAG, "ScreenCaptureService onCreate - isCapturing: $isCapturing")
        
        // 서비스가 생성될 때 항상 foreground로 시작하여 시스템에 의한 종료 방지
        if (isCapturing) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode != -1 && data != null) {
                    startCapture(resultCode, data)
                } else {
                    Log.e(TAG, "Invalid capture request: resultCode=$resultCode, data=${data != null}")
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopCapture()
            }
            ACTION_CAPTURE_ONCE -> {
                // 서비스가 실행 중이 아니면 foreground로 시작
                if (mediaProjection == null) {
                    Log.w(TAG, "MediaProjection is null, service may need to be restarted")
                    // 캡처 상태가 true인데 mediaProjection이 null이면 서비스가 재시작된 것
                    if (loadCaptureState(this)) {
                        Log.e(TAG, "Service was restarted but capture state is true. Need to restart capture.")
                        updateCaptureState(this, false)
                        sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_ERROR").apply {
                            putExtra("error", "화면 캡처 서비스가 재시작되었습니다. 다시 시작해주세요.")
                        })
                        return START_STICKY
                    }
                }
                captureOnce()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "화면 캡처 서비스 실행 중"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("화면 캡처 서비스")
            .setContentText("화면 캡처가 실행 중입니다")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        Log.d(TAG, "Starting screen capture service...")
        startForeground(NOTIFICATION_ID, createNotification())
        
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        // 상태 업데이트 및 저장
        updateCaptureState(this, true)
        Log.d(TAG, "Screen capture started! isCapturing = $isCapturing")
        
        setupVirtualDisplay()
        
        // 상태 브로드캐스트
        sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_STARTED"))
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val bitmap = imageToBitmap(it, width, height)
                captureCallback?.invoke(bitmap)
                it.close()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }

    private fun captureOnce() {
        Log.d(TAG, "captureOnce() called - isCapturing: $isCapturing, mediaProjection: ${mediaProjection != null}")
        
        // SharedPreferences에서 상태 다시 확인
        val currentCaptureState = loadCaptureState(this)
        Log.d(TAG, "Current capture state from SharedPrefs: $currentCaptureState")
        
        if (mediaProjection == null || !currentCaptureState) {
            Log.e(TAG, "Cannot capture: MediaProjection=${mediaProjection != null}, isCapturing=$currentCaptureState")
            sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_ERROR").apply {
                putExtra("error", "화면 캡처 서비스가 실행되지 않았습니다. 먼저 '화면 캡처 시작'을 클릭하세요.")
            })
            return
        }
        
        serviceScope.launch {
            try {
                delay(100) // 짧은 딜레이 후 캡처
                val metrics = resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                
                imageReader?.acquireLatestImage()?.use { image ->
                    val bitmap = imageToBitmap(image, width, height)
                    Log.d(TAG, "Screen captured successfully: ${bitmap.width}x${bitmap.height}")
                    
                    // 캡처된 비트맵을 static 변수에 저장 (임시)
                    capturedBitmap = bitmap
                    
                    withContext(Dispatchers.Main) {
                        // Broadcast captured bitmap
                        sendBroadcast(Intent("com.kakao.taxi.test.SCREEN_CAPTURED").apply {
                            putExtra("bitmap", bitmap)
                        })
                        
                        // AutoDetectionService에서 직접 분석
                        val autoDetection = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        autoDetection?.let {
                            // Find AutoDetectionService and call analyzeCapturedScreen
                            Log.d(TAG, "Sending bitmap to AutoDetectionService for analysis")
                        }
                    }
                } ?: run {
                    Log.w(TAG, "No image available for capture")
                    sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_ERROR").apply {
                        putExtra("error", "No image available")
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
                sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_ERROR").apply {
                    putExtra("error", e.message ?: "Unknown error")
                })
            }
        }
    }

    private fun stopCapture() {
        Log.d(TAG, "Stopping screen capture...")
        
        // 상태 업데이트 및 저장
        updateCaptureState(this, false)
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        // 상태 브로드캐스트
        sendBroadcast(Intent("com.kakao.taxi.test.CAPTURE_STOPPED"))
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Screen capture stopped! isCapturing = $isCapturing")
    }

    fun setOnCaptureListener(callback: (Bitmap) -> Unit) {
        captureCallback = callback
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopCapture()
    }
}