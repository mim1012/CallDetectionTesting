package com.kakao.taxi.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kakao.taxi.test.service.RemoteControlService

class SimpleTestActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SimpleTest"
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }
    
    private lateinit var btnStartTest: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvServerInfo: TextView
    private lateinit var tvTestResult: TextView
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 간단한 레이아웃 프로그래밍 방식으로 생성
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }
        
        // 제목
        val title = TextView(this).apply {
            text = "🚕 카카오택시 콜 수락 테스트"
            textSize = 24f
            setTextColor(0xFFFFD700.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        layout.addView(title)
        
        // 서버 정보
        tvServerInfo = TextView(this).apply {
            text = """
                📡 테스트 서버 정보
                IP: 192.168.1.100 (변경 필요)
                Port: 8081
                
                ⚠️ RemoteControlService.kt에서
                SERVER_URL을 본인 PC IP로 변경하세요!
            """.trimIndent()
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2A2A2A.toInt())
            setPadding(30, 30, 30, 30)
        }
        layout.addView(tvServerInfo)
        
        // 상태 표시
        tvStatus = TextView(this).apply {
            text = "상태: 대기중"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 50, 0, 30)
        }
        layout.addView(tvStatus)
        
        // 테스트 시작 버튼
        btnStartTest = Button(this).apply {
            text = "테스트 시작"
            textSize = 20f
            setBackgroundColor(0xFFFFD700.toInt())
            setTextColor(0xFF000000.toInt())
            setPadding(0, 40, 0, 40)
        }
        layout.addView(btnStartTest)
        
        // 테스트 결과
        tvTestResult = TextView(this).apply {
            text = """
                📋 테스트 체크리스트:
                □ 서버 연결
                □ 화면 캡처
                □ 노란 버튼 감지
                □ 클릭 실행
            """.trimIndent()
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 50, 0, 0)
        }
        layout.addView(tvTestResult)
        
        setContentView(layout)
        
        // MediaProjection 매니저 초기화
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // 버튼 클릭 리스너
        btnStartTest.setOnClickListener {
            startTest()
        }
    }
    
    private fun startTest() {
        tvStatus.text = "상태: 화면 캡처 권한 요청중..."
        
        // MediaProjection 권한 요청
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                tvStatus.text = "상태: 서비스 시작중..."
                
                // MediaProjection 저장
                val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                RemoteControlService.mediaProjection = mediaProjection
                
                // 서비스 시작
                val serviceIntent = Intent(this, RemoteControlService::class.java)
                startService(serviceIntent)
                
                tvStatus.text = "상태: 실행중 (서버 연결 대기)"
                
                updateTestResult("✅ 화면 캡처 권한 획득")
                
                // 테스트 안내
                showTestInstructions()
            } else {
                tvStatus.text = "상태: 권한 거부됨"
                Toast.makeText(this, "화면 캡처 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateTestResult(message: String) {
        tvTestResult.text = tvTestResult.text.toString() + "\n" + message
    }
    
    private fun showTestInstructions() {
        val instructions = """
            
            📌 테스트 방법:
            
            1. PC에서 서버 실행:
               cd server-architecture
               node test-server.js
            
            2. 서버 콘솔에서 연결 확인
            
            3. 카카오택시 드라이버 앱 실행
            
            4. 콜이 오면 자동으로:
               - 노란 버튼 감지
               - 클릭 명령 실행
            
            5. 로그 확인:
               adb logcat -s RemoteControl
        """.trimIndent()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("테스트 시작됨")
            .setMessage(instructions)
            .setPositiveButton("확인", null)
            .show()
    }
}