package com.kakao.taxi.test.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.kakao.taxi.test.module.MatchResult
import com.kakao.taxi.test.module.OCRResult

class OverlayService : Service() {
    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SHOW_OVERLAY = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "ACTION_HIDE_OVERLAY"
        const val ACTION_UPDATE_MATCH = "ACTION_UPDATE_MATCH"
        const val ACTION_UPDATE_OCR = "ACTION_UPDATE_OCR"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var imageView: ImageView? = null
    
    private val matchResults = mutableListOf<MatchResult>()
    private val ocrResults = mutableListOf<OCRResult>()
    
    // 드래그를 위한 변수들
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 저장된 위치 불러오기
        val prefs = getSharedPreferences("overlay_position", Context.MODE_PRIVATE)
        savedX = prefs.getInt("x", -1)
        savedY = prefs.getInt("y", -1)
    }
    
    private var savedX = -1
    private var savedY = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_UPDATE_MATCH -> {
                val results = intent.getSerializableExtra("matches") as? List<MatchResult>
                results?.let { updateMatchResults(it) }
            }
            ACTION_UPDATE_OCR -> {
                val results = intent.getSerializableExtra("ocr_results") as? List<OCRResult>
                results?.let { updateOCRResults(it) }
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            return
        }

        if (overlayView != null) {
            return // Already showing
        }

        // Create overlay view
        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(Color.TRANSPARENT)
        }
        overlayView = imageView

        // Setup layout parameters - 작은 디버그 창으로 변경
        params = WindowManager.LayoutParams(
            450, // 너비 450px
            300, // 높이 300px (더 많은 텍스트 표시)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        // 저장된 위치가 있으면 사용, 없으면 화면 중앙
        params?.gravity = Gravity.TOP or Gravity.START
        if (savedX != -1 && savedY != -1) {
            params?.x = savedX
            params?.y = savedY
        } else {
            params?.x = resources.displayMetrics.widthPixels / 2 - 225
            params?.y = resources.displayMetrics.heightPixels / 2 - 150
        }

        // 터치 이벤트 리스너 추가 (드래그 가능)
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 위치 저장
                    val prefs = getSharedPreferences("overlay_position", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putInt("x", params?.x ?: 0)
                        putInt("y", params?.y ?: 0)
                        apply()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(overlayView, params)
        updateOverlay()
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
            imageView = null
        }
    }

    private fun updateMatchResults(results: List<MatchResult>) {
        matchResults.clear()
        matchResults.addAll(results)
        updateOverlay()
    }

    private fun updateOCRResults(results: List<OCRResult>) {
        ocrResults.clear()
        ocrResults.addAll(results)
        updateOverlay()
    }

    private fun updateOverlay() {
        // 오버레이 창 크기에 맞게 조정
        val width = 450
        val height = 300

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 반투명 검은색 배경
        canvas.drawColor(Color.argb(180, 0, 0, 0))

        // Draw match results
        val matchPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val matchTextPaint = Paint().apply {
            color = Color.GREEN
            textSize = 30f
            style = Paint.Style.FILL
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }

        // 디버그 정보만 표시 (사각형은 그리지 않음)
        drawDebugInfo(canvas, width, height)

        imageView?.setImageBitmap(bitmap)
    }

    private fun drawDebugInfo(canvas: Canvas, width: Int, height: Int) {
        val titlePaint = Paint().apply {
            color = Color.YELLOW
            textSize = 20f
            style = Paint.Style.FILL
            isFakeBoldText = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            style = Paint.Style.FILL
        }

        // 드래그 핸들 표시
        val handlePaint = Paint().apply {
            color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 40f, handlePaint)
        
        // 제목 (드래그 가능 표시)
        canvas.drawText("🔍 카카오 택시 디버그 [드래그 가능]", 10f, 25f, titlePaint)
        
        // 구분선
        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
        }
        canvas.drawLine(10f, 40f, width - 10f, 40f, linePaint)

        // 디버그 정보
        var yOffset = 60f
        
        // 버튼 감지 상태
        val buttonStatus = if (matchResults.isNotEmpty()) {
            "✅ 버튼 ${matchResults.size}개 감지됨"
        } else {
            "❌ 버튼 감지 안됨"
        }
        canvas.drawText(buttonStatus, 10f, yOffset, textPaint)
        yOffset += 25f
        
        // OCR 상태
        val ocrStatus = if (ocrResults.isNotEmpty()) {
            "✅ 텍스트 ${ocrResults.size}개 인식됨"
        } else {
            "❌ 텍스트 인식 안됨"
        }
        canvas.drawText(ocrStatus, 10f, yOffset, textPaint)
        yOffset += 25f
        
        // 금액/거리 정보 추출
        var amount: Int? = null
        var distance: Float? = null
        
        ocrResults.forEach { result ->
            val amountPattern = """(\d{1,3}(,\d{3})*|\d+)\s*원""".toRegex()
            amountPattern.find(result.text)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                amount = amountStr.toIntOrNull()
            }
            
            val distancePattern = """(\d+\.?\d*)\s*(km|㎞)""".toRegex()
            distancePattern.find(result.text)?.let { match ->
                distance = match.groupValues[1].toFloatOrNull()
            }
        }
        
        // 인식된 모든 텍스트 표시 (스크롤 가능하도록 최대 3개만)
        if (ocrResults.isNotEmpty()) {
            val textPaint2 = Paint().apply {
                color = Color.CYAN
                textSize = 14f
                style = Paint.Style.FILL
            }
            
            canvas.drawText("[ 인식된 텍스트 ]", 10f, yOffset, textPaint)
            yOffset += 20f
            
            ocrResults.take(3).forEach { result ->
                // 긴 텍스트는 잘라서 표시
                val displayText = if (result.text.length > 30) {
                    result.text.substring(0, 27) + "..."
                } else {
                    result.text
                }
                canvas.drawText("• $displayText", 15f, yOffset, textPaint2)
                yOffset += 18f
            }
            
            if (ocrResults.size > 3) {
                canvas.drawText("... 외 ${ocrResults.size - 3}개", 15f, yOffset, textPaint2)
            }
        } else {
            canvas.drawText("인식된 텍스트 없음", 10f, yOffset, textPaint)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
}