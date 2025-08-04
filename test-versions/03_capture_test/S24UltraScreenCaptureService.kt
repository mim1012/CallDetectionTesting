package com.kakao.taxi.test.service

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import android.hardware.display.DisplayManager
import android.view.SurfaceControl
import android.graphics.PixelFormat

/**
 * Galaxy S24 Ultra 전용 화면 캡처 서비스
 * FLAG_SECURE 우회를 위한 여러 방법 시도
 */
class S24UltraScreenCaptureService : Service() {
    companion object {
        private const val TAG = "S24UltraCapture"
        const val ACTION_CAPTURE = "ACTION_CAPTURE_S24"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> captureScreen()
        }
        return START_STICKY
    }
    
    private fun captureScreen() {
        serviceScope.launch {
            try {
                // 방법 1: SurfaceControl 사용 (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    captureBySurfaceControl()
                }
                
                // 방법 2: 접근성 서비스를 통한 스크린샷
                captureByAccessibility()
                
                // 방법 3: WindowManager FLAG 수정
                modifyWindowFlags()
                
                // 방법 4: 시스템 속성 변경 시도
                bypassSystemProperties()
                
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed: ${e.message}", e)
                
                // 실패 시 대체 방법: 접근성 노드 정보로 UI 재구성
                reconstructUIFromAccessibility()
            }
        }
    }
    
    /**
     * SurfaceControl API를 사용한 캡처 (Android 10+)
     * FLAG_SECURE를 무시하고 캡처 가능
     */
    private fun captureBySurfaceControl() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = windowManager.defaultDisplay
                
                // Reflection으로 숨겨진 API 접근
                val surfaceControlClass = Class.forName("android.view.SurfaceControl")
                val screenshotMethod = surfaceControlClass.getDeclaredMethod(
                    "screenshot",
                    Rect::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Boolean::class.java,
                    Int::class.java
                )
                screenshotMethod.isAccessible = true
                
                val displaySize = android.graphics.Point()
                display.getRealSize(displaySize)
                
                val screenshot = screenshotMethod.invoke(
                    null,
                    Rect(0, 0, displaySize.x, displaySize.y),
                    displaySize.x / 2, // 해상도 줄이기
                    displaySize.y / 2,
                    0,
                    0,
                    true,
                    Surface.ROTATION_0
                ) as? Bitmap
                
                screenshot?.let {
                    Log.d(TAG, "✅ SurfaceControl capture success: ${it.width}x${it.height}")
                    processCapturedBitmap(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SurfaceControl capture failed: ${e.message}")
        }
    }
    
    /**
     * 접근성 서비스를 통한 스크린샷 시도
     */
    private fun captureByAccessibility() {
        try {
            // KakaoTaxiAccessibilityService에 스크린샷 요청
            val intent = Intent("com.kakao.taxi.test.REQUEST_ACCESSIBILITY_SCREENSHOT")
            sendBroadcast(intent)
            Log.d(TAG, "Requested screenshot from accessibility service")
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility capture failed: ${e.message}")
        }
    }
    
    /**
     * WindowManager의 FLAG_SECURE 제거 시도
     */
    private fun modifyWindowFlags() {
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Reflection으로 mGlobal 접근
            val wmGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val getInstanceMethod = wmGlobalClass.getDeclaredMethod("getInstance")
            val wmGlobal = getInstanceMethod.invoke(null)
            
            // mViews 필드 접근
            val mViewsField = wmGlobalClass.getDeclaredField("mViews")
            mViewsField.isAccessible = true
            val views = mViewsField.get(wmGlobal) as? ArrayList<View>
            
            views?.forEach { view ->
                try {
                    val params = view.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        if (it.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                            it.flags = it.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                            windowManager.updateViewLayout(view, it)
                            Log.d(TAG, "Removed FLAG_SECURE from a view")
                        }
                    }
                } catch (e: Exception) {
                    // 개별 뷰 처리 실패 무시
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Window flags modification failed: ${e.message}")
        }
    }
    
    /**
     * 시스템 속성 변경으로 보안 우회 시도
     */
    private fun bypassSystemProperties() {
        try {
            val runtime = Runtime.getRuntime()
            
            // 스크린샷 보안 비활성화 시도
            val commands = listOf(
                "setprop persist.sys.debug.multi_window_capture 1",
                "setprop debug.screenshot.secure 0",
                "setprop persist.sys.ui.hw 1"
            )
            
            commands.forEach { cmd ->
                try {
                    runtime.exec(cmd)
                    Log.d(TAG, "Executed: $cmd")
                } catch (e: Exception) {
                    // 권한 없을 수 있음
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "System property bypass failed: ${e.message}")
        }
    }
    
    /**
     * 접근성 노드 정보로 UI 재구성 (최후의 수단)
     */
    private fun reconstructUIFromAccessibility() {
        try {
            val intent = Intent("com.kakao.taxi.test.REQUEST_UI_RECONSTRUCTION")
            sendBroadcast(intent)
            Log.d(TAG, "Requested UI reconstruction from accessibility data")
        } catch (e: Exception) {
            Log.e(TAG, "UI reconstruction failed: ${e.message}")
        }
    }
    
    private fun processCapturedBitmap(bitmap: Bitmap) {
        // 캡처된 비트맵을 메인 서비스로 전달
        val intent = Intent("com.kakao.taxi.test.S24_CAPTURE_SUCCESS").apply {
            putExtra("width", bitmap.width)
            putExtra("height", bitmap.height)
        }
        sendBroadcast(intent)
        
        // 임시로 파일로 저장
        try {
            val file = File(externalCacheDir, "s24_capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Log.d(TAG, "Bitmap saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}