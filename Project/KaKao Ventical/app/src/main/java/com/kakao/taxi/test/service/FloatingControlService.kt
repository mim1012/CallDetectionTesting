package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.kakao.taxi.test.R
import com.kakao.taxi.test.module.FilterCriteria
import com.kakao.taxi.test.module.FilterSettings
import kotlin.math.abs
import android.content.BroadcastReceiver
import android.content.Context as AndroidContext
import android.content.IntentFilter
import com.kakao.taxi.test.MainActivity

class FloatingControlService : Service() {
    companion object {
        const val ACTION_SHOW_CONTROLS = "ACTION_SHOW_CONTROLS"
        const val ACTION_HIDE_CONTROLS = "ACTION_HIDE_CONTROLS"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var expandedView: LinearLayout? = null
    private var collapsedView: ImageButton? = null
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastAction = 0
    private var filterSettings: FilterSettings? = null
    
    // Detection state
    private var isDetecting = false
    
    // Callbacks for control actions
    var onStartTest: (() -> Unit)? = null
    var onStopTest: (() -> Unit)? = null
    var onTemplateTest: (() -> Unit)? = null
    var onOCRTest: (() -> Unit)? = null
    var onClickTest: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        filterSettings = FilterSettings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_CONTROLS -> showFloatingControls()
            ACTION_HIDE_CONTROLS -> hideFloatingControls()
        }
        return START_STICKY
    }

    private fun showFloatingControls() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        if (floatingView != null) return

        // Inflate floating layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_control, null)
        
        collapsedView = floatingView?.findViewById(R.id.collapsed_view)
        expandedView = floatingView?.findViewById(R.id.expanded_view)
        
        // Setup button listeners
        setupButtons()
        
        // Setup layout parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 300

        windowManager?.addView(floatingView, params)
        
        // 초기에는 축소된 상태로 표시
        expandedView?.visibility = View.GONE
        collapsedView?.visibility = View.VISIBLE
        
        // Setup touch listener for drag
        setupTouchListener(params)
    }

    private fun setupButtons() {
        floatingView?.apply {
            // Collapsed view click - expand/collapse
            findViewById<ImageButton>(R.id.collapsed_view)?.setOnClickListener {
                if (expandedView?.visibility == View.GONE) {
                    expandedView?.visibility = View.VISIBLE
                    collapsedView?.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                } else {
                    expandedView?.visibility = View.GONE
                    collapsedView?.setImageResource(android.R.drawable.ic_menu_more)
                }
            }
            
            // Control buttons - Play/Pause toggle
            val playButton = findViewById<ImageButton>(R.id.btn_start_detection)
            val stopButton = findViewById<ImageButton>(R.id.btn_stop_detection)
            
            playButton?.setOnClickListener {
                if (!isDetecting) {
                    // 자동 감지 시작
                    startAutoDetection()
                    isDetecting = true
                    playButton.setImageResource(android.R.drawable.ic_media_pause)
                    showToast("자동 감지 시작")
                } else {
                    // 자동 감지 일시정지
                    pauseAutoDetection()
                    isDetecting = false
                    playButton.setImageResource(android.R.drawable.ic_media_play)
                    showToast("자동 감지 일시정지")
                }
            }
            
            stopButton?.setOnClickListener {
                // 자동 감지 완전 중지
                stopAutoDetection()
                isDetecting = false
                playButton?.setImageResource(android.R.drawable.ic_media_play)
                showToast("자동 감지 중지")
            }
            
            findViewById<ImageButton>(R.id.btn_test_template)?.setOnClickListener {
                sendFloatingAction("template_test")
                showToast("템플릿 매칭 테스트")
            }
            
            findViewById<ImageButton>(R.id.btn_test_ocr)?.setOnClickListener {
                sendFloatingAction("ocr_test")
                showToast("OCR 테스트")
            }
            
            findViewById<ImageButton>(R.id.btn_test_click)?.setOnClickListener {
                sendFloatingAction("click_test")
                showToast("클릭 테스트")
            }
            
            findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
                hideFloatingControls()
            }
        }
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastAction = event.action
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (lastAction == MotionEvent.ACTION_DOWN) {
                        // Click detected - handled by button click listeners
                    }
                    lastAction = event.action
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate new position
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // Update layout
                    windowManager?.updateViewLayout(floatingView, params)
                    lastAction = event.action
                    true
                }
                else -> false
            }
        }
    }

    private fun hideFloatingControls() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun startAutoDetection() {
        // 화면 캐처 서비스가 실행 중인지 확인
        val captureActive = ScreenCaptureService.loadCaptureState(this)
        if (!captureActive) {
            showToast("⚠️ 화면 캐처 서비스를 먼저 시작하세요")
            // 화면 캐처 시작을 위해 MainActivity로 이동
            sendFloatingAction("start_detection")
            // 하지만 일단 AutoDetectionService는 시작
        }
        
        // 필터 설정 로드
        val filterCriteria = loadFilterCriteria()
        
        // 자동 감지 서비스 시작
        val intent = Intent(this, AutoDetectionService::class.java).apply {
            action = AutoDetectionService.ACTION_START_DETECTION
            putExtra("filter", filterCriteria)
        }
        startService(intent)
        
        showToast("✅ 자동 감지 시작됨")
        
        // 디버그 서비스가 실행중이 아니면 시작
        val debugIntent = Intent(this, FloatingDebugService::class.java)
        startService(debugIntent)
    }
    
    private fun pauseAutoDetection() {
        // 자동 감지 일시정지 (토글)
        val intent = Intent(this, AutoDetectionService::class.java).apply {
            action = AutoDetectionService.ACTION_TOGGLE_DETECTION
        }
        startService(intent)
    }
    
    private fun stopAutoDetection() {
        // 자동 감지 완전 중지
        val intent = Intent(this, AutoDetectionService::class.java).apply {
            action = AutoDetectionService.ACTION_STOP_DETECTION
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingControls()
    }
    
    private fun isAutoDetectionRunning(): Boolean {
        // Check if AutoDetectionService is running
        // This is a simplified check - in real app, use proper service binding
        return false // Default to false, will be updated via callbacks
    }
    
    private fun loadFilterCriteria(): FilterCriteria {
        return filterSettings?.loadFilterCriteria() ?: FilterCriteria()
    }
    
    private fun sendFloatingAction(action: String) {
        val intent = Intent("com.kakao.taxi.test.FLOATING_ACTION").apply {
            putExtra("action", action)
        }
        sendBroadcast(intent)
    }
    
    // 상태 업데이트 리시버
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: AndroidContext?, intent: Intent?) {
            when (intent?.action) {
                "com.kakao.taxi.test.DETECTION_STATUS" -> {
                    val isRunning = intent.getBooleanExtra("is_running", false)
                    updateDetectionStatus(isRunning)
                }
            }
        }
    }
    
    private fun updateDetectionStatus(isRunning: Boolean) {
        isDetecting = isRunning
        floatingView?.findViewById<ImageButton>(R.id.btn_start_detection)?.apply {
            setImageResource(if (isRunning) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            })
        }
    }
}