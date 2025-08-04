package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * 플로팅 디버그 창
 * 실시간으로 각 단계별 상태를 화면에 표시
 */
class FloatingDebugService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var debugTextView: TextView
    private lateinit var statusIndicators: Map<String, View>
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 상태별 색상
    private val statusColors = mapOf(
        "success" to Color.GREEN,
        "warning" to Color.YELLOW,
        "error" to Color.RED,
        "processing" to Color.BLUE,
        "idle" to Color.GRAY
    )
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingDebugView()
    }
    
    private fun createFloatingDebugView() {
        // 플로팅 뷰 레이아웃
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000")) // 반투명 검정
            setPadding(20, 20, 20, 20)
            
            // 헤더
            addView(TextView(context).apply {
                text = "🔍 실시간 디버그"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 10)
            })
            
            // 상태 표시기들
            val indicators = mutableMapOf<String, View>()
            
            // 1. 화면 캡처 상태
            addView(createStatusRow("화면캡처", "capture").also {
                indicators["capture"] = it.second
            }.first)
            
            // 2. 카카오 앱 감지
            addView(createStatusRow("앱 감지", "app").also {
                indicators["app"] = it.second
            }.first)
            
            // 3. 버튼 감지
            addView(createStatusRow("버튼감지", "button").also {
                indicators["button"] = it.second
            }.first)
            
            // 4. OCR 인식
            addView(createStatusRow("텍스트", "ocr").also {
                indicators["ocr"] = it.second
            }.first)
            
            // 5. 클릭 수행
            addView(createStatusRow("클릭", "click").also {
                indicators["click"] = it.second
            }.first)
            
            // 6. 성능
            addView(createStatusRow("속도", "performance").also {
                indicators["performance"] = it.second
            }.first)
            
            statusIndicators = indicators
            
            // 상세 정보 텍스트
            debugTextView = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(0, 10, 0, 0)
                maxLines = 5
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(debugTextView)
            
            // 닫기 버튼
            addView(TextView(context).apply {
                text = "❌ 닫기"
                textSize = 14f
                setTextColor(Color.RED)
                setPadding(0, 10, 0, 0)
                setOnClickListener {
                    stopSelf()
                }
            })
        }
        
        // WindowManager 파라미터
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.END
            x = 10
            y = 200
        }
        
        // 드래그 가능하게 만들기
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
        
        windowManager.addView(floatingView, params)
    }
    
    private fun createStatusRow(label: String, tag: String): Pair<LinearLayout, View> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 5, 0, 5)
        }
        
        // 라벨
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        
        // 상태 인디케이터 (색상 원)
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(30, 30).apply {
                setMargins(10, 0, 10, 0)
            }
            background = ContextCompat.getDrawable(context, android.R.drawable.presence_offline)
            backgroundTintList = android.content.res.ColorStateList.valueOf(statusColors["idle"]!!)
        }
        row.addView(indicator)
        
        // 상태 텍스트
        val statusText = TextView(this).apply {
            text = "대기"
            textSize = 12f
            setTextColor(Color.GRAY)
        }
        row.addView(statusText)
        
        row.tag = tag
        return Pair(row, indicator)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "UPDATE_DEBUG" -> {
                val debugInfo = intent.getStringExtra("debug_info") ?: ""
                updateDebugInfo(debugInfo)
            }
            "UPDATE_STATUS" -> {
                val step = intent.getStringExtra("step") ?: ""
                val status = intent.getStringExtra("status") ?: "idle"
                val message = intent.getStringExtra("message") ?: ""
                updateStepStatus(step, status, message)
            }
        }
        return START_STICKY
    }
    
    private fun updateDebugInfo(info: String) {
        handler.post {
            debugTextView.text = info
        }
    }
    
    fun updateStepStatus(step: String, status: String, message: String) {
        handler.post {
            val indicator = statusIndicators[step]
            val row = floatingView.findViewWithTag<LinearLayout>(step)
            
            if (indicator != null && row != null) {
                // 색상 업데이트
                val color = statusColors[status] ?: statusColors["idle"]!!
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                
                // 텍스트 업데이트
                val statusText = row.getChildAt(2) as? TextView
                statusText?.apply {
                    text = message
                    setTextColor(color)
                }
                
                // 애니메이션 효과
                if (status == "processing") {
                    indicator.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(300)
                        .withEndAction {
                            indicator.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

// 디버그 상태 전송을 위한 헬퍼 함수
fun android.content.Context.updateDebugStatus(step: String, status: String, message: String) {
    val intent = Intent(this, FloatingDebugService::class.java).apply {
        action = "UPDATE_STATUS"
        putExtra("step", step)
        putExtra("status", status)
        putExtra("message", message)
    }
    startService(intent)
}