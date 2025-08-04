package com.kakao.taxi.test

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.kakao.taxi.test.service.BackgroundLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogViewerActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView
    private lateinit var autoScrollCheckBox: CheckBox
    private lateinit var filterSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var clearButton: Button
    private lateinit var pauseButton: Button
    
    private var isPaused = false
    private var currentFilter = "ALL"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var refreshRunnable: Runnable
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        
        initViews()
        setupControls()
        startAutoRefresh()
    }
    
    private fun initViews() {
        logTextView = findViewById(R.id.logTextView)
        autoScrollCheckBox = findViewById(R.id.autoScrollCheckBox)
        filterSpinner = findViewById(R.id.filterSpinner)
        refreshButton = findViewById(R.id.refreshButton)
        clearButton = findViewById(R.id.clearButton)
        pauseButton = findViewById(R.id.pauseButton)
        
        logTextView.movementMethod = ScrollingMovementMethod()
        
        // 필터 옵션 설정
        val filters = arrayOf(
            "ALL - 모든 로그",
            "ERROR - 오류만",
            "SUCCESS - 성공만", 
            "CAPTURE - 화면캡처",
            "DETECTION - 버튼감지",
            "CLICK - 클릭",
            "ACCESSIBILITY - 접근성"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter
    }
    
    private fun setupControls() {
        refreshButton.setOnClickListener {
            loadLogs()
        }
        
        clearButton.setOnClickListener {
            logTextView.text = ""
        }
        
        pauseButton.setOnClickListener {
            isPaused = !isPaused
            pauseButton.text = if (isPaused) "▶ 재개" else "⏸ 일시정지"
            if (!isPaused) {
                loadLogs()
            }
        }
        
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = when (position) {
                    0 -> "ALL"
                    1 -> "ERROR"
                    2 -> "SUCCESS"
                    3 -> "ScreenCapture"
                    4 -> "ButtonDetection"
                    5 -> "AutoClick"
                    6 -> "Accessibility"
                    else -> "ALL"
                }
                loadLogs()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isPaused) {
                    loadLogs()
                }
                handler.postDelayed(this, 1000) // 1초마다 새로고침
            }
        }
        handler.post(refreshRunnable)
    }
    
    private fun loadLogs() {
        try {
            val logsDir = File(getExternalFilesDir(null), "KakaoTaxiLogs")
            val today = dateFormat.format(Date())
            val logFile = File(logsDir, "log_$today.txt")
            
            if (!logFile.exists()) {
                logTextView.text = "로그 파일이 없습니다.\n경로: ${logFile.absolutePath}"
                return
            }
            
            // 파일 읽기 (최근 200줄)
            val allLines = logFile.readLines()
            val filteredLines = if (currentFilter == "ALL") {
                allLines
            } else {
                allLines.filter { line ->
                    when (currentFilter) {
                        "ERROR" -> line.contains("[ERROR]") || line.contains("실패") || line.contains("❌")
                        "SUCCESS" -> line.contains("성공") || line.contains("✅")
                        else -> line.contains(currentFilter)
                    }
                }
            }
            
            val recentLines = filteredLines.takeLast(200)
            val logText = buildString {
                appendLine("=== 실시간 로그 (${recentLines.size}개) ===")
                appendLine("필터: $currentFilter")
                appendLine("시간: ${timeFormat.format(Date())}")
                appendLine("=====================================")
                appendLine()
                
                // 색상 코딩을 위한 처리
                recentLines.forEach { line ->
                    when {
                        line.contains("[ERROR]") || line.contains("❌") -> {
                            appendLine("❌ $line")
                        }
                        line.contains("[WARN]") || line.contains("⚠️") -> {
                            appendLine("⚠️ $line")
                        }
                        line.contains("성공") || line.contains("✅") -> {
                            appendLine("✅ $line")
                        }
                        line.contains("[INFO]") -> {
                            appendLine("ℹ️ $line")
                        }
                        else -> {
                            appendLine(line)
                        }
                    }
                }
            }
            
            logTextView.text = logText
            
            // 자동 스크롤
            if (autoScrollCheckBox.isChecked) {
                logTextView.post {
                    val scrollAmount = logTextView.layout?.getLineTop(logTextView.lineCount) ?: 0
                    val scrollY = scrollAmount - logTextView.height
                    if (scrollY > 0) {
                        logTextView.scrollTo(0, scrollY)
                    }
                }
            }
            
        } catch (e: Exception) {
            logTextView.text = "로그 읽기 오류: ${e.message}"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
}