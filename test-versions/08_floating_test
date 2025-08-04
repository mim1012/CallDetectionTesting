package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 플로팅 알림 서비스
 * 콜 감지 시 화면에 표시
 */
class FloatingAlertService : Service() {
    private var windowManager: WindowManager? = null
    private var alertView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val x = it.getIntExtra("x", 0)
            val y = it.getIntExtra("y", 0)
            val amount = it.getIntExtra("amount", 0)
            val distance = it.getFloatExtra("distance", 0f)
            
            showFloatingAlert(x, y, amount, distance)
        }
        
        return START_NOT_STICKY
    }
    
    private fun showFloatingAlert(x: Int, y: Int, amount: Int, distance: Float) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 알림 뷰 생성
        alertView = createAlertView(x, y, amount, distance)
        
        // 레이아웃 파라미터
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.y = 100 // 상단에서 100px
        }
        
        windowManager?.addView(alertView, params)
        
        // 5초 후 자동 제거
        handler.postDelayed({
            removeAlert()
        }, 5000)
    }
    
    private fun createAlertView(x: Int, y: Int, amount: Int, distance: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            
            // 배경 그라데이션
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFDE700")) // 카카오 노란색
                cornerRadius = 20f
                setStroke(3, Color.parseColor("#FFE5D000"))
            }
        }
        
        // 제목
        TextView(this).apply {
            text = "🚖 콜 감지!"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            container.addView(this)
        }
        
        // 정보
        TextView(this).apply {
            text = "💰 ${amount}원 | 📍 ${distance}km"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
            container.addView(this)
        }
        
        // 위치
        TextView(this).apply {
            text = "👆 클릭 위치: ($x, $y)"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            container.addView(this)
        }
        
        // 클릭 리스너
        container.setOnClickListener {
            removeAlert()
        }
        
        return container
    }
    
    private fun removeAlert() {
        alertView?.let {
            windowManager?.removeView(it)
            alertView = null
        }
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        removeAlert()
    }
}