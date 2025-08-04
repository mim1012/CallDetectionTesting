package com.kakao.taxi.test.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.R
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class RemoteControlService : Service() {
    companion object {
        private const val TAG = "RemoteControl"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "remote_control_channel"
        
        // 서버 설정
        private const val SERVER_URL = "ws://192.168.1.100:8081"  // 서버 IP 변경 필요
        private const val SCREENSHOT_INTERVAL = 100L  // 100ms마다 스크린샷
        
        var mediaProjection: MediaProjection? = null
    }
    
    private var webSocket: WebSocket? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var windowManager: WindowManager
    private lateinit var displayMetrics: DisplayMetrics
    
    private var isCapturing = false
    private var lastCaptureTime = 0L
    private var driverId: String? = null
    
    // 통계
    private var totalClicks = 0
    private var successfulClicks = 0
    private var failedClicks = 0
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 필터 설정 브로드캐스트 리시버 등록
        registerFilterUpdateReceiver()
        
        Log.d(TAG, "RemoteControlService 생성됨")
    }
    
    private fun registerFilterUpdateReceiver() {
        val filter = android.content.IntentFilter("com.kakao.taxi.FILTER_UPDATE")
        registerReceiver(filterUpdateReceiver, filter)
    }
    
    private val filterUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val minAmount = it.getIntExtra("min_amount", 5000)
                val maxDistance = it.getFloatExtra("max_distance", 3.0f)
                val preferredAreas = it.getStringArrayListExtra("preferred_areas") ?: arrayListOf()
                val autoAccept = it.getBooleanExtra("auto_accept", true)
                val priorityHigh = it.getBooleanExtra("priority_high", false)
                val avoidTraffic = it.getBooleanExtra("avoid_traffic", false)
                
                // 서버로 개별 필터 설정 전송
                sendFilterSettings(minAmount, maxDistance, preferredAreas, autoAccept, priorityHigh, avoidTraffic)
            }
        }
    }
    
    private fun sendFilterSettings(
        minAmount: Int,
        maxDistance: Float,
        preferredAreas: ArrayList<String>,
        autoAccept: Boolean,
        priorityHigh: Boolean,
        avoidTraffic: Boolean
    ) {
        val json = JSONObject().apply {
            put("type", "filter_settings")
            put("driverId", driverId)
            put("settings", JSONObject().apply {
                put("minAmount", minAmount)
                put("maxDistance", maxDistance)
                put("preferredAreas", org.json.JSONArray(preferredAreas))
                put("autoAccept", autoAccept)
                put("priorityHigh", priorityHigh)
                put("avoidTraffic", avoidTraffic)
            })
            put("timestamp", System.currentTimeMillis())
        }
        
        webSocket?.send(json.toString())
        sendLog("필터 설정 업데이트: 최소 ${minAmount}원, 최대 ${maxDistance}km")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RemoteControlService 시작")
        
        connectToServer()
        setupScreenCapture()
        startScreenStreaming()
        
        return START_STICKY
    }
    
    private fun connectToServer() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url(SERVER_URL)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 연결됨")
                sendStatus("connected")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerCommand(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 종료중: $reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 종료됨: $reason")
                // 재연결 시도
                Handler(Looper.getMainLooper()).postDelayed({
                    connectToServer()
                }, 5000)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 연결 실패", t)
                // 재연결 시도
                Handler(Looper.getMainLooper()).postDelayed({
                    connectToServer()
                }, 5000)
            }
        })
    }
    
    private fun handleServerCommand(message: String) {
        try {
            val json = JSONObject(message)
            val action = json.getString("action")
            
            when (action) {
                "init" -> {
                    driverId = json.getString("driverId")
                    Log.d(TAG, "드라이버 ID 할당: $driverId")
                }
                
                "click" -> {
                    val x = json.getInt("x")
                    val y = json.getInt("y")
                    executeClick(x, y)
                }
                
                "swipe" -> {
                    val x1 = json.getInt("x1")
                    val y1 = json.getInt("y1")
                    val x2 = json.getInt("x2")
                    val y2 = json.getInt("y2")
                    executeSwipe(x1, y1, x2, y2)
                }
                
                "config" -> {
                    // 필터 설정 업데이트
                    val config = json.getJSONObject("config")
                    updateFilterConfig(config)
                }
                
                "stop" -> {
                    stopScreenStreaming()
                }
                
                "start" -> {
                    startScreenStreaming()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "명령 처리 실패", e)
        }
    }
    
    private fun setupScreenCapture() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection이 없습니다")
            return
        }
        
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi
        
        imageReader = ImageReader.newInstance(
            width, height, 
            PixelFormat.RGBA_8888, 
            2  // 더블 버퍼링
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            if (System.currentTimeMillis() - lastCaptureTime >= SCREENSHOT_INTERVAL) {
                captureAndSendScreen(reader)
                lastCaptureTime = System.currentTimeMillis()
            }
        }, null)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RemoteControlCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        
        Log.d(TAG, "화면 캡처 설정 완료: ${width}x${height}")
    }
    
    private fun captureAndSendScreen(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        
        try {
            val buffer = image.planes[0].buffer
            val pixelStride = image.planes[0].pixelStride
            val rowStride = image.planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // 비트맵을 Base64로 인코딩
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)  // 품질 75%
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            
            // 서버로 전송
            sendScreenshot(base64Image)
            
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 실패", e)
        } finally {
            image.close()
        }
    }
    
    private fun sendScreenshot(imageBase64: String) {
        val json = JSONObject().apply {
            put("type", "screenshot")
            put("image", imageBase64)
            put("timestamp", System.currentTimeMillis())
            put("driverId", driverId)
        }
        
        webSocket?.send(json.toString())
    }
    
    private fun sendStatus(status: String) {
        val json = JSONObject().apply {
            put("type", "status")
            put("status", status)
            put("driverId", driverId)
            put("stats", JSONObject().apply {
                put("totalClicks", totalClicks)
                put("successfulClicks", successfulClicks)
                put("failedClicks", failedClicks)
            })
        }
        
        webSocket?.send(json.toString())
    }
    
    private fun sendLog(message: String) {
        val json = JSONObject().apply {
            put("type", "log")
            put("message", message)
            put("driverId", driverId)
            put("timestamp", System.currentTimeMillis())
        }
        
        webSocket?.send(json.toString())
    }
    
    private fun executeClick(x: Int, y: Int) {
        totalClicks++
        
        coroutineScope.launch {
            try {
                // 방법 1: Runtime.exec (가장 기본)
                val process = Runtime.getRuntime().exec("input tap $x $y")
                val success = process.waitFor(500, TimeUnit.MILLISECONDS)
                
                if (success && process.exitValue() == 0) {
                    successfulClicks++
                    sendLog("클릭 성공: ($x, $y)")
                    Log.d(TAG, "클릭 성공: ($x, $y)")
                } else {
                    // 방법 2: sendevent 시도 (디바이스별 다름)
                    executeSendEvent(x, y)
                }
                
            } catch (e: Exception) {
                failedClicks++
                sendLog("클릭 실패: ($x, $y) - ${e.message}")
                Log.e(TAG, "클릭 실행 실패", e)
                
                // 방법 3: AccessibilityService 이용 (별도 구현 필요)
                tryAccessibilityClick(x, y)
            }
        }
    }
    
    private fun executeSendEvent(x: Int, y: Int) {
        try {
            // Galaxy S24 Ultra 기준 (디바이스마다 다름)
            val commands = arrayOf(
                "sendevent /dev/input/event2 3 57 0",     // ABS_MT_TRACKING_ID
                "sendevent /dev/input/event2 3 53 $x",    // ABS_MT_POSITION_X
                "sendevent /dev/input/event2 3 54 $y",    // ABS_MT_POSITION_Y
                "sendevent /dev/input/event2 3 58 50",    // ABS_MT_PRESSURE
                "sendevent /dev/input/event2 3 48 5",     // ABS_MT_TOUCH_MAJOR
                "sendevent /dev/input/event2 0 0 0",      // SYN_REPORT
                "sendevent /dev/input/event2 3 57 -1",    // Release
                "sendevent /dev/input/event2 0 0 0"       // SYN_REPORT
            )
            
            for (cmd in commands) {
                Runtime.getRuntime().exec(cmd)
                Thread.sleep(10)
            }
            
            successfulClicks++
            sendLog("SendEvent 클릭 성공: ($x, $y)")
            
        } catch (e: Exception) {
            failedClicks++
            Log.e(TAG, "SendEvent 실패", e)
        }
    }
    
    private fun tryAccessibilityClick(x: Int, y: Int) {
        // AccessibilityService가 활성화되어 있다면 브로드캐스트로 전달
        val intent = Intent("com.kakao.taxi.ACCESSIBILITY_CLICK").apply {
            putExtra("x", x)
            putExtra("y", y)
        }
        sendBroadcast(intent)
    }
    
    private fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int) {
        coroutineScope.launch {
            try {
                val process = Runtime.getRuntime().exec("input swipe $x1 $y1 $x2 $y2 300")
                process.waitFor(1, TimeUnit.SECONDS)
                sendLog("스와이프 실행: ($x1,$y1) -> ($x2,$y2)")
            } catch (e: Exception) {
                Log.e(TAG, "스와이프 실행 실패", e)
            }
        }
    }
    
    private fun updateFilterConfig(config: JSONObject) {
        // 필터 설정 저장
        val prefs = getSharedPreferences("filter_config", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("minAmount", config.optInt("minAmount", 5000))
            putFloat("maxDistance", config.optDouble("maxDistance", 3.0).toFloat())
            apply()
        }
        
        sendLog("필터 설정 업데이트됨")
    }
    
    private fun startScreenStreaming() {
        isCapturing = true
        sendStatus("streaming")
        Log.d(TAG, "화면 스트리밍 시작")
    }
    
    private fun stopScreenStreaming() {
        isCapturing = false
        sendStatus("paused")
        Log.d(TAG, "화면 스트리밍 중지")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "원격 제어 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "카카오택시 원격 제어 서비스"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("원격 제어 활성화")
            .setContentText("서버: $SERVER_URL")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        isCapturing = false
        webSocket?.close(1000, "Service destroyed")
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        coroutineScope.cancel()
        
        // BroadcastReceiver 해제
        try {
            unregisterReceiver(filterUpdateReceiver)
        } catch (e: Exception) {
            // 이미 해제된 경우 무시
        }
        
        Log.d(TAG, "RemoteControlService 종료")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}