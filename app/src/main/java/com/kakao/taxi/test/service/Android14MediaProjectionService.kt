package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * Android 14 전용 MediaProjection 서비스
 * FLAG_SECURE 우회 시도 포함
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14
class Android14MediaProjectionService : Service() {
    
    companion object {
        private const val TAG = "Android14Projection"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "media_projection_channel"
        
        const val ACTION_START_PROJECTION = "START_PROJECTION"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 노란색 감지 범위 (카카오 시그니처)
    private val YELLOW_MIN = Color.rgb(250, 225, 0)
    private val YELLOW_MAX = Color.rgb(255, 235, 59)
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Android 14 필수: ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                
                if (resultCode != -1 && data != null) {
                    startProjection(resultCode, data)
                }
            }
        }
        return START_STICKY
    }
    
    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            // Android 14 FLAG_SECURE 우회 시도
            bypassFlagSecure()
            
            setupVirtualDisplay()
            startContinuousCapture()
            
            Log.d(TAG, "✅ MediaProjection 시작 (Android 14)")
        } catch (e: Exception) {
            Log.e(TAG, "Projection 시작 실패", e)
            // FLAG_SECURE 때문에 실패하면 대체 방법 시도
            startAlternativeCapture()
        }
    }
    
    /**
     * FLAG_SECURE 우회 시도 (Android 14)
     */
    private fun bypassFlagSecure() {
        try {
            // 방법 1: MediaProjection 플래그 수정
            mediaProjection?.let { projection ->
                val clazz = projection.javaClass
                
                // mContext 필드 접근
                val contextField = clazz.getDeclaredField("mContext")
                contextField.isAccessible = true
                val context = contextField.get(projection) as? Context
                
                // Display 플래그 수정
                val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val display = windowManager?.defaultDisplay
                
                display?.let {
                    val displayClass = it.javaClass
                    val flagsField = displayClass.getDeclaredField("mFlags")
                    flagsField.isAccessible = true
                    var flags = flagsField.getInt(it)
                    
                    // FLAG_SECURE 비트 제거 (0x2000)
                    flags = flags and 0x2000.inv()
                    flagsField.setInt(it, flags)
                    
                    Log.d(TAG, "FLAG_SECURE 비트 제거 시도")
                }
            }
            
            // 방법 2: Surface 플래그 수정
            imageReader?.surface?.let { surface ->
                val surfaceClass = surface.javaClass
                try {
                    // setSecure 메서드 호출
                    val setSecureMethod = surfaceClass.getDeclaredMethod("setSecure", Boolean::class.java)
                    setSecureMethod.isAccessible = true
                    setSecureMethod.invoke(surface, false)
                    Log.d(TAG, "Surface secure 플래그 해제")
                } catch (e: NoSuchMethodException) {
                    // native 메서드 직접 호출
                    val nativeSetSecure = surfaceClass.getDeclaredMethod("nativeSetSecure", Long::class.java, Boolean::class.java)
                    nativeSetSecure.isAccessible = true
                    
                    val mNativeObjectField = surfaceClass.getDeclaredField("mNativeObject")
                    mNativeObjectField.isAccessible = true
                    val nativeObject = mNativeObjectField.getLong(surface)
                    
                    nativeSetSecure.invoke(null, nativeObject, false)
                    Log.d(TAG, "Native secure 플래그 해제")
                }
            }
            
            // 방법 3: VirtualDisplay 플래그 수정
            virtualDisplay?.let { vd ->
                val vdClass = vd.javaClass
                val flagsField = vdClass.getDeclaredField("mFlags")
                flagsField.isAccessible = true
                var flags = flagsField.getInt(vd)
                
                // VIRTUAL_DISPLAY_FLAG_SECURE 제거
                flags = flags and 0x4.inv()
                flagsField.setInt(vd, flags)
                
                Log.d(TAG, "VirtualDisplay secure 플래그 해제")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "FLAG_SECURE 우회 실패: ${e.message}")
        }
    }
    
    private fun setupVirtualDisplay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        
        // Android 14에서는 해상도를 낮춰서 성능 향상
        val width = size.x / 2  // 절반 해상도
        val height = size.y / 2
        val density = resources.displayMetrics.densityDpi
        
        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 
            2  // 더블 버퍼링
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            processImage(reader.acquireLatestImage())
        }, null)
        
        // Android 14용 플래그 설정
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                   DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                   DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "KakaoCapture",
            width, height, density,
            flags,
            imageReader?.surface,
            null, null
        )
    }
    
    /**
     * 연속 캡처 및 분석
     */
    private fun startContinuousCapture() {
        serviceScope.launch {
            while (isActive) {
                delay(100) // 100ms마다 캡처
                // 이미지는 OnImageAvailableListener에서 처리됨
            }
        }
    }
    
    /**
     * 캡처된 이미지 처리
     */
    private fun processImage(image: Image?) {
        image ?: return
        
        try {
            val bitmap = imageToBitmap(image)
            
            // FLAG_SECURE로 검은 화면인지 체크
            if (isBlackScreen(bitmap)) {
                Log.w(TAG, "⚠️ FLAG_SECURE 감지 - 검은 화면")
                // 대체 방법 시도
                startAlternativeCapture()
            } else {
                // 노란 버튼 찾기
                findYellowButton(bitmap)
            }
            
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "이미지 처리 실패", e)
        } finally {
            image.close()
        }
    }
    
    /**
     * Image를 Bitmap으로 변환
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, 
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }
    
    /**
     * 검은 화면 감지
     */
    private fun isBlackScreen(bitmap: Bitmap): Boolean {
        var blackPixels = 0
        val totalPixels = 100 // 샘플링
        
        for (i in 0 until totalPixels) {
            val x = (Math.random() * bitmap.width).toInt()
            val y = (Math.random() * bitmap.height).toInt()
            val pixel = bitmap.getPixel(x, y)
            
            if (Color.red(pixel) < 10 && Color.green(pixel) < 10 && Color.blue(pixel) < 10) {
                blackPixels++
            }
        }
        
        return blackPixels > totalPixels * 0.9 // 90% 이상 검은색
    }
    
    /**
     * 노란 버튼 찾기
     */
    private fun findYellowButton(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        
        // 그리드 스캔 (5픽셀 간격)
        for (y in height / 2 until height step 5) { // 하단 절반만 스캔
            for (x in 0 until width step 5) {
                val pixel = bitmap.getPixel(x, y)
                
                if (isYellow(pixel)) {
                    // 노란색 영역 발견
                    val centerX = x * 2 // 원래 해상도로 변환
                    val centerY = y * 2
                    
                    Log.d(TAG, "✅ 노란 버튼 발견: ($centerX, $centerY)")
                    
                    // 클릭 이벤트 전송
                    sendClickEvent(centerX, centerY)
                    return
                }
            }
        }
    }
    
    /**
     * 노란색 판별
     */
    private fun isYellow(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // 카카오 노란색 범위
        return r in 250..255 && g in 225..235 && b in 0..59
    }
    
    /**
     * 클릭 이벤트 전송
     */
    private fun sendClickEvent(x: Int, y: Int) {
        val intent = Intent("com.kakao.taxi.test.YELLOW_BUTTON_FOUND").apply {
            putExtra("x", x)
            putExtra("y", y)
        }
        sendBroadcast(intent)
        
        // 접근성 서비스에 클릭 요청
        val clickIntent = Intent("com.kakao.taxi.test.REQUEST_CLICK").apply {
            putExtra("x", x)
            putExtra("y", y)
        }
        sendBroadcast(clickIntent)
    }
    
    /**
     * 대체 캡처 방법 (FLAG_SECURE 우회 실패 시)
     */
    private fun startAlternativeCapture() {
        serviceScope.launch {
            Log.d(TAG, "🔄 대체 방법 시작: 접근성 + 패턴 분석")
            
            // 1. 접근성 서비스로 전환
            val intent = Intent("com.kakao.taxi.test.START_ACCESSIBILITY_MODE")
            sendBroadcast(intent)
            
            // 2. 화면 가장자리만 캡처 (FLAG_SECURE 영향 없는 영역)
            captureEdgeOnly()
            
            // 3. 시스템 UI 변화 감지
            monitorSystemUI()
        }
    }
    
    /**
     * 화면 가장자리만 캡처
     */
    private fun captureEdgeOnly() {
        // 상태바, 네비게이션 바 영역은 FLAG_SECURE 영향 없음
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            
            // 상단 100픽셀, 하단 100픽셀만 캡처
            val edgeReader = ImageReader.newInstance(
                size.x, 200,  // 상하단 합쳐서 200픽셀
                PixelFormat.RGBA_8888, 1
            )
            
            // 새로운 VirtualDisplay 생성 (가장자리만)
            val edgeDisplay = mediaProjection?.createVirtualDisplay(
                "EdgeCapture",
                size.x, 200, 
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                edgeReader.surface,
                null, null
            )
            
            Log.d(TAG, "가장자리 캡처 모드 활성화")
        } catch (e: Exception) {
            Log.e(TAG, "가장자리 캡처 실패", e)
        }
    }
    
    /**
     * 시스템 UI 변화 모니터링
     */
    private fun monitorSystemUI() {
        // 알림, 토스트, 다이얼로그 등 감지
        val intent = Intent("com.kakao.taxi.test.MONITOR_SYSTEM_UI")
        sendBroadcast(intent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "화면 캡처 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "카카오 콜 감지를 위한 화면 캡처"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("콜 감지 중")
            .setContentText("Android 14 MediaProjection 실행 중")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        serviceScope.cancel()
    }
}