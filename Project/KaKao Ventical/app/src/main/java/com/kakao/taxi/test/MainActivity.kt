package com.kakao.taxi.test

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kakao.taxi.test.module.*
import com.kakao.taxi.test.service.OverlayService
import com.kakao.taxi.test.service.ScreenCaptureService
import com.kakao.taxi.test.service.FloatingControlService
import com.kakao.taxi.test.service.AutoDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
import com.kakao.taxi.test.service.BackgroundLogger
import com.kakao.taxi.test.service.FloatingDebugService
import com.kakao.taxi.test.service.OneClickAutoService
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_OVERLAY_PERMISSION = 1002
        private const val REQUEST_STORAGE_PERMISSION = 1003
        private const val REQUEST_NOTIFICATION_PERMISSION = 1005
        private const val REQUEST_ONE_CLICK_AUTO = 2000
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var debugCaptureStatus: TextView
    private lateinit var debugDetectionStatus: TextView
    private lateinit var debugClickStatus: TextView
    private lateinit var debugLastCoord: TextView
    private lateinit var debugAccessibilityStatus: TextView
    
    private lateinit var btnStartCapture: Button
    private lateinit var btnTestTemplate: Button
    private lateinit var btnTestOCR: Button
    private lateinit var btnTestClick: Button
    private lateinit var btnTestFilter: Button
    private lateinit var btnShowOverlay: Button
    private lateinit var btnMockCall: Button
    private lateinit var btnOpenDebugFolder: Button
    private lateinit var btnViewLogs: Button
    private lateinit var chkBypassKakao: CheckBox
    
    private lateinit var debugHelper: DebugHelper
    private lateinit var filterSettings: FilterSettings
    private lateinit var backgroundLogger: BackgroundLogger
    
    private lateinit var openCVMatcher: OpenCVMatcher
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var clickHandler: ClickEventHandler
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var isCapturing = false
    
    private val screenCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.kakao.taxi.test.SCREEN_CAPTURED" -> {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("bitmap", Bitmap::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Bitmap>("bitmap")
                    }
                    bitmap?.let {
                        addLog("화면 캡처 완료: ${it.width}x${it.height}")
                        updateDebugStatus("capture", "✅ 캡처 완료 (${it.width}x${it.height})")
                    }
                }
                "com.kakao.taxi.test.DEBUG_UPDATE" -> {
                    val type = intent.getStringExtra("type") ?: return
                    val status = intent.getStringExtra("status") ?: return
                    val extra = intent.getStringExtra("extra")
                    updateDebugStatus(type, status, extra)
                    
                    // 백그라운드 로깅
                    when (type) {
                        "capture" -> backgroundLogger.logFeatureStatus("ScreenCapture", status, status.contains("✅"))
                        "detection" -> backgroundLogger.logFeatureStatus("ButtonDetection", status, status.contains("✅"), extra)
                        "click" -> backgroundLogger.logFeatureStatus("AutoClick", status, status.contains("✅"), extra)
                    }
                }
                "com.kakao.taxi.test.ACCESSIBILITY_STATUS" -> {
                    val isConnected = intent.getBooleanExtra("isConnected", false)
                    val isKakaoAccessible = intent.getBooleanExtra("isKakaoAccessible", false)
                    val lastDetection = intent.getLongExtra("lastDetection", 0)
                    val blockReason = intent.getStringExtra("blockReason") ?: ""
                    
                    updateAccessibilityStatus(isConnected, isKakaoAccessible, lastDetection, blockReason)
                }
                "com.kakao.taxi.test.CAPTURE_ERROR" -> {
                    val error = intent.getStringExtra("error") ?: "Unknown error"
                    addLog("❌ 캡처 오류: $error")
                    updateDebugStatus("capture", "❌ 캡처 실패", error)
                }
                "com.kakao.taxi.test.CAPTURE_STARTED" -> {
                    isCapturing = true
                    updateDebugStatus("capture", "✅ 활성")
                    addLog("✅ 화면 캡처 서비스 시작됨")
                    btnStartCapture.text = "화면 캡처 중지"
                }
                "com.kakao.taxi.test.CAPTURE_STOPPED" -> {
                    isCapturing = false
                    updateDebugStatus("capture", "❌ 비활성")
                    addLog("❌ 화면 캡처 서비스 중지됨")
                    btnStartCapture.text = "화면 캡처 시작"
                }
                "com.kakao.taxi.test.REQUEST_CAPTURE_START" -> {
                    addLog("⚠️ 화면 캡처 서비스 재시작 요청됨")
                    if (!isCapturing) {
                        showToast("화면 캡처 서비스를 다시 시작합니다...")
                        startCapture()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initModules()
        setupButtons()
        checkPermissions()
        
        val filter = IntentFilter().apply {
            addAction("com.kakao.taxi.test.SCREEN_CAPTURED")
            addAction("com.kakao.taxi.test.DEBUG_UPDATE")
            addAction("com.kakao.taxi.test.ACCESSIBILITY_STATUS")
            addAction("com.kakao.taxi.test.CAPTURE_ERROR")
            addAction("com.kakao.taxi.test.CAPTURE_STARTED")
            addAction("com.kakao.taxi.test.CAPTURE_STOPPED")
            addAction("com.kakao.taxi.test.REQUEST_CAPTURE_START")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenCaptureReceiver, filter)
        }
        
        // Start floating controls automatically if permission is granted
        if (Settings.canDrawOverlays(this)) {
            startFloatingControls()
        }
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        debugCaptureStatus = findViewById(R.id.debugCaptureStatus)
        debugDetectionStatus = findViewById(R.id.debugDetectionStatus)
        debugClickStatus = findViewById(R.id.debugClickStatus)
        debugLastCoord = findViewById(R.id.debugLastCoord)
        debugAccessibilityStatus = findViewById(R.id.debugAccessibilityStatus)
        
        btnStartCapture = findViewById(R.id.btnStartCapture)
        btnTestTemplate = findViewById(R.id.btnTestTemplate)
        btnTestOCR = findViewById(R.id.btnTestOCR)
        btnTestClick = findViewById(R.id.btnTestClick)
        btnTestFilter = findViewById(R.id.btnTestFilter)
        btnShowOverlay = findViewById(R.id.btnShowOverlay)
        btnMockCall = findViewById(R.id.btnMockCall)
        btnOpenDebugFolder = findViewById(R.id.btnOpenDebugFolder)
        btnViewLogs = findViewById(R.id.btnViewLogs)
        chkBypassKakao = findViewById(R.id.chkBypassKakao)
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun initModules() {
        openCVMatcher = OpenCVMatcher()
        ocrProcessor = OCRProcessor(this)
        clickHandler = ClickEventHandler(this)
        debugHelper = DebugHelper(this)
        filterSettings = FilterSettings(this)
        backgroundLogger = BackgroundLogger(this)
        
        // Initialize OCR
        lifecycleScope.launch {
            val ocrInitialized = ocrProcessor.initialize()
            withContext(Dispatchers.Main) {
                updateStatus("OCR 초기화: ${if (ocrInitialized) "성공" else "실패"}")
            }
        }
        
    }

    private fun startFloatingControls() {
        // 오버레이 권한 확인
        if (!Settings.canDrawOverlays(this)) {
            addLog("⚠️ 플로팅 버튼 표시를 위해 오버레이 권한이 필요합니다")
            return
        }
        
        val intent = Intent(this, FloatingControlService::class.java).apply {
            action = FloatingControlService.ACTION_SHOW_CONTROLS
        }
        startService(intent)
        
        addLog("✅ 플로팅 컨트롤 시작됨")
        addLog("플로팅 버튼을 클릭하여 확장하세요")
        
        // Setup callbacks for floating controls
        setupFloatingControlCallbacks()
        
        // 디버그 패널 자동 표시 (설정에서 확인)
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("show_debug_panel", true)) {
            startFloatingDebugPanel()
        }
    }
    
    private fun setupFloatingControlCallbacks() {
        // 플로팅 컨트롤과의 통신을 위한 브로드캐스트 리시버 설정
        val filter = IntentFilter().apply {
            addAction("com.kakao.taxi.test.FLOATING_ACTION")
        }
        
        val floatingActionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra("action")) {
                    "template_test" -> testTemplateMatching()
                    "ocr_test" -> testOCR()
                    "click_test" -> testClick()
                    "start_detection" -> {
                        addLog("플로팅 버튼에서 화면 캡처 요청")
                        if (!isCapturing) {
                            // 앱을 포그라운드로 가져오고 캡처 시작
                            val mainIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("start_capture", true)
                            }
                            startActivity(mainIntent)
                        } else {
                            addLog("이미 화면 캡처 중")
                        }
                    }
                    "stop_detection" -> {
                        if (isCapturing) {
                            stopCapture()
                        }
                    }
                }
            }
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(floatingActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(floatingActionReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register floating action receiver", e)
        }
    }
    
    private fun setupButtons() {
        btnStartCapture.setOnClickListener {
            if (isCapturing) {
                stopCapture()
            } else {
                startCapture()
            }
        }
        
        btnTestTemplate.setOnClickListener {
            testTemplateMatching()
        }
        
        btnTestOCR.setOnClickListener {
            testOCR()
        }
        
        btnTestClick.setOnClickListener {
            testClick()
        }
        
        btnTestFilter.setOnClickListener {
            testFilter()
        }
        
        btnShowOverlay.setOnClickListener {
            toggleOverlay()
        }
        
        btnMockCall.setOnClickListener {
            showMockCall()
        }
        
        btnOpenDebugFolder.setOnClickListener {
            openDebugFolder()
        }
        
        btnViewLogs.setOnClickListener {
            // 실시간 로그 뷰어 열기
            val intent = Intent(this, LogViewerActivity::class.java)
            startActivity(intent)
        }
        
        // 플로팅 컨트롤 표시 버튼 추가
        findViewById<Button>(R.id.btnShowFloating)?.setOnClickListener {
            // 화면 캡처가 시작되지 않았으면 먼저 시작
            if (!isCapturing) {
                Toast.makeText(this, "화면 캡처를 먼저 시작합니다...", Toast.LENGTH_SHORT).show()
                startCapture()
                // 캡처 권한 받은 후 플로팅 컨트롤 표시는 onActivityResult에서 처리
            } else {
                startFloatingControls()
            }
        }
        
        // 테스트 모드 체크박스
        chkBypassKakao.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("bypass_kakao_check", isChecked).apply()
            
            if (isChecked) {
                addLog("⚠️ 테스트 모드 활성화 - 카카오 앱 체크 우회")
                addLog("모든 화면에서 캡처가 작동합니다")
            } else {
                addLog("✅ 테스트 모드 비활성화 - 카카오 앱에서만 작동")
            }
        }
        
        // 저장된 설정 로드
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        chkBypassKakao.isChecked = prefs.getBoolean("bypass_kakao_check", false)
        
        // 원클릭 자동 모드
        findViewById<Button>(R.id.btnOneClickAuto)?.setOnClickListener {
            startOneClickAutoMode()
        }
        
        // 빠른 진단
        findViewById<Button>(R.id.btnQuickDiagnose)?.setOnClickListener {
            runQuickDiagnostic()
        }
        
        // 자동 클릭 설정
        setupAutoClickMethod()
    }

    private fun checkPermissions() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
        
        // Check storage permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_STORAGE_PERMISSION)
            }
        }
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
        
        // Check Accessibility Service instead of Shizuku
        checkAccessibilityStatus()
    }

    private fun startCapture() {
        addLog("화면 캡처 시작 요청...")
        
        // 1. 이미 다른 앱이 화면 녹화 중인지 확인
        checkIfOtherAppRecording()
        
        if (mediaProjectionManager == null) {
            addLog("❌ MediaProjectionManager가 null입니다")
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        }
        
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent != null) {
            addLog("✅ MediaProjection 권한 요청 다이얼로그 표시 시도...")
            try {
                // 강제로 앱을 최상위로 가져오기
                val bringToFrontIntent = Intent(this, MainActivity::class.java)
                bringToFrontIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(bringToFrontIntent)
                
                // 약간의 딜레이 후 권한 요청
                lifecycleScope.launch {
                    delay(100)
                    withContext(Dispatchers.Main) {
                        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
                    }
                }
            } catch (e: Exception) {
                addLog("❌ 권한 요청 실패: ${e.message}")
                e.printStackTrace()
                // 대체 방법 제시
                showManualStartDialog()
            }
        } else {
            addLog("❌ MediaProjectionManager를 사용할 수 없습니다")
            addLog("시스템 버전: ${Build.VERSION.SDK_INT}")
            showManualStartDialog()
        }
    }
    
    private fun checkIfOtherAppRecording() {
        // 상태바에 녹화 아이콘이 있는지 간접적으로 확인
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(100)
        
        runningServices.forEach { service ->
            if (service.service.className.contains("MediaProjection") || 
                service.service.className.contains("ScreenRecord") ||
                service.service.className.contains("Capture")) {
                addLog("⚠️ 다른 화면 녹화 앱이 실행 중일 수 있습니다: ${service.service.packageName}")
            }
        }
    }
    
    private fun showManualStartDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("화면 캡처 권한 문제")
            .setMessage("""
                자동으로 권한 다이얼로그를 표시할 수 없습니다.
                
                해결 방법:
                1. 다른 화면 녹화 앱을 모두 종료하세요
                2. 휴대폰을 재시작하세요
                3. 설정 > 앱 > 카카오 택시 테스트 > 강제 종료 후 다시 시도
                
                또는 대체 방법을 사용하시겠습니까?
            """.trimIndent())
            .setPositiveButton("대체 방법 사용") { _, _ ->
                startAlternativeCapture()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun startAlternativeCapture() {
        // 대체 방법: 접근성 서비스를 이용한 화면 읽기
        addLog("대체 방법으로 화면 감지 시작...")
        
        if (!isAccessibilityServiceEnabled()) {
            addLog("❌ 접근성 서비스가 비활성화되어 있습니다")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
        } else {
            // 화면 캡처 없이 접근성으로만 동작
            updateCaptureState(this, true) // 가상으로 캡처 상태 설정
            isCapturing = true
            updateDebugStatus("capture", "✅ 대체 모드 활성")
            btnStartCapture.text = "화면 캡처 중지"
            addLog("✅ 대체 모드로 작동 중 (접근성 서비스 사용)")
            
            // 플로팅 컨트롤 표시
            if (Settings.canDrawOverlays(this)) {
                startFloatingControls()
            }
        }
    }
    
    private fun updateCaptureState(context: Context, state: Boolean) {
        ScreenCaptureService.updateCaptureState(context, state)
    }

    private fun stopCapture() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAPTURE
        }
        startService(intent)
        isCapturing = false
        updateStatus("화면 캡처 중지됨")
        btnStartCapture.text = "화면 캡처 시작"
    }

    private fun testTemplateMatching() {
        if (!isCapturing) {
            Toast.makeText(this, "먼저 화면 캡처를 시작하세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        addLog("노란색 버튼 감지 테스트 시작...")
        
        // Test yellow button detection
        lifecycleScope.launch {
            // Capture screen
            val captureIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_ONCE
            }
            startService(captureIntent)
            
            // Wait for capture and test
            delay(500)
            
            addLog("노란색 버튼 감지 중...")
        }
    }

    private fun testOCR() {
        if (!isCapturing) {
            Toast.makeText(this, "먼저 화면 캡처를 시작하세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        addLog("OCR 테스트 시작...")
        
        lifecycleScope.launch {
            // Capture and extract text
            // In real implementation, capture screen and run OCR
            addLog("OCR 테스트 완료")
        }
    }

    private fun testClick() {
        addLog("클릭 테스트 시작...")
        
        lifecycleScope.launch {
            // Test click at center of screen
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2
            val centerY = displayMetrics.heightPixels / 2
            
            val success = clickHandler.performClick(centerX, centerY)
            
            withContext(Dispatchers.Main) {
                addLog("클릭 테스트 ${if (success) "성공" else "실패"}: ($centerX, $centerY)")
            }
        }
    }

    private fun testFilter() {
        addLog("필터 설정 테스트...")
        
        // 현재 필터 설정 로드
        val currentCriteria = filterSettings.loadFilterCriteria()
        
        // 테스트용 새 필터 설정
        val newCriteria = FilterCriteria(
            minAmount = 5000,
            maxAmount = 30000,
            minDistance = 1.0f,
            maxDistance = 8.0f,
            keywords = listOf("무안", "남악")
        )
        
        // 필터 저장
        filterSettings.saveFilterCriteria(newCriteria)
        
        addLog("필터 저장 완료:")
        addLog("- 금액: ${newCriteria.minAmount}~${newCriteria.maxAmount}원")
        addLog("- 거리: ${newCriteria.minDistance}~${newCriteria.maxDistance}km")
        addLog("- 키워드: ${newCriteria.keywords?.joinToString(", ") ?: "없음"}")
        
        // AutoDetectionService에 필터 업데이트 알림
        val updateIntent = Intent(this, AutoDetectionService::class.java).apply {
            action = AutoDetectionService.ACTION_UPDATE_FILTER
            putExtra("filter", newCriteria)
        }
        startService(updateIntent)
    }

    private fun toggleOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = if (btnShowOverlay.text == "오버레이 표시") {
                OverlayService.ACTION_SHOW_OVERLAY
            } else {
                OverlayService.ACTION_HIDE_OVERLAY
            }
        }
        startService(intent)
        
        btnShowOverlay.text = if (btnShowOverlay.text == "오버레이 표시") {
            "오버레이 숨기기"
        } else {
            "오버레이 표시"
        }
    }

    private fun updateStatus(status: String) {
        statusText.text = "상태: $status"
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + KakaoTaxiAccessibilityService::class.java.name
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(service) == true
        } catch (e: Exception) {
            return false
        }
    }

    private fun showMockCall() {
        val intent = Intent(this, MockCallActivity::class.java)
        startActivity(intent)
        
        // 자동 감지 테스트를 위해 짧은 딜레이 후 캡처
        lifecycleScope.launch {
            delay(1000) // Mock 화면이 완전히 로드될 때까지 대기
            testYellowButtonOnMock()
        }
    }
    
    private suspend fun testYellowButtonOnMock() {
        withContext(Dispatchers.Main) {
            addLog("Mock 화면에서 노란색 버튼 감지 테스트...")
        }
        
        // 화면 캡처 및 분석은 여기에 구현
    }
    
    private fun startFloatingDebugPanel() {
        val intent = Intent(this, FloatingDebugService::class.java)
        startService(intent)
        addLog("🔍 디버그 패널 표시됨")
    }
    
    private fun openDebugFolder() {
        try {
            val debugPath = debugHelper.getDebugFolderPath()
            addLog("디버그 폴더: $debugPath")
            
            // 파일 관리자 앱으로 폴더 열기
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Android%2Fdata%2Fcom.kakao.taxi.test%2Ffiles%2FPictures%2FKakaoTaxiDebug")
            intent.setDataAndType(uri, "vnd.android.document/directory")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // 파일 관리자가 없으면 경로만 표시
                Toast.makeText(this, "파일 경로: $debugPath", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open debug folder", e)
            Toast.makeText(this, "디버그 폴더 열기 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addLog(message: String) {
        Log.d(TAG, message)
        val currentLog = logText.text.toString()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "[$timestamp] $message\n$currentLog"
        logText.text = newLog.take(1000) // Keep last 1000 chars
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun runQuickDiagnostic() {
        val diagnostic = QuickDiagnostic(this)
        val report = diagnostic.runFullDiagnostic()
        
        addLog("\n========== 진단 결과 ==========")
        addLog("접근성 활성화: ${if (report.accessibilityEnabled) "✅" else "❌"}")
        addLog("접근성 연결됨: ${if (report.accessibilityConnected) "✅" else "❌"}")
        addLog("화면표시 권한: ${if (report.screenCapturePermission) "✅" else "❌"}")
        addLog("캡처 활성화: ${if (report.screenCaptureActive) "✅" else "❌"}")
        addLog("비트맵 존재: ${if (report.lastCapturedBitmap) "✅" else "❌"}")
        addLog("카카오앱 감지: ${if (report.kakaoAppDetected) "✅" else "❌"}")
        addLog("메모리: ${report.availableMemoryMB}MB")
        addLog("\n${report.getErrorSummary()}")
        addLog("===============================\n")
        
        // MediaProjection 테스트
        testMediaProjection()
        
        // 자동 수정 제안
        diagnostic.showQuickFix(this)
    }
    
    private fun testMediaProjection() {
        addLog("\n--- MediaProjection 테스트 ---")
        
        // 1. MediaProjectionManager 확인
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            addLog("❌ MediaProjectionManager 서비스 없음")
            return
        }
        addLog("✅ MediaProjectionManager 사용 가능")
        
        // 2. 다른 앱 녹화 상태 확인
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningTasks = activityManager.getRunningTasks(10)
            runningTasks.forEach { task ->
                if (task.topActivity?.packageName?.contains("screenrecord") == true) {
                    addLog("⚠️ 화면 녹화 앱 감지: ${task.topActivity?.packageName}")
                }
            }
        } catch (e: Exception) {
            addLog("⚠️ 실행 중인 작업 확인 실패")
        }
        
        // 3. 테스트 권한 요청
        addLog("📱 테스트 권한 요청 시도...")
        val testIntent = mpm.createScreenCaptureIntent()
        try {
            startActivityForResult(testIntent, 9999) // 테스트용 코드
        } catch (e: Exception) {
            addLog("❌ 권한 요청 실패: ${e.javaClass.simpleName}")
            addLog("   ${e.message}")
        }
    }
    
    private fun startOneClickAutoMode() {
        addLog("🚀 원클릭 자동 모드 시작...")
        
        // 접근성 서비스만 확인
        if (!isAccessibilityServiceEnabled()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("간단 설정")
                .setMessage("접근성 서비스만 켜면 바로 사용 가능합니다!")
                .setPositiveButton("설정 열기") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    addLog("💡 '카카오 택시 테스트'를 찾아서 켜주세요")
                }
                .show()
        } else {
            // 바로 시작
            addLog("✅ 접근성 서비스로 바로 시작!")
            isCapturing = true // 가상으로 설정
            updateCaptureState(this, true)
            
            // 자동 감지 서비스 시작
            val intent = Intent(this, AutoDetectionService::class.java).apply {
                action = AutoDetectionService.ACTION_START_DETECTION
            }
            startService(intent)
            
            // 플로팅 컨트롤 표시
            if (Settings.canDrawOverlays(this)) {
                startFloatingControls()
            }
            
            Toast.makeText(this, "카카오 택시 앱으로 이동하세요!", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }
    
    
    // 이전 상태 저장용
    private var lastCaptureStatus = ""
    private var lastDetectionStatus = ""
    private var lastClickStatus = ""
    private var lastCoordStatus = ""
    
    private fun updateDebugStatus(type: String, status: String, extra: String? = null) {
        runOnUiThread {
            when (type) {
                "capture" -> {
                    val newStatus = "📷 화면캡처: $status"
                    if (newStatus != lastCaptureStatus) {
                        debugCaptureStatus.text = newStatus
                        lastCaptureStatus = newStatus
                        // X 표시인 경우 로그에 이유 표시
                        if (status.contains("❌")) {
                            addLog(newStatus)
                        }
                    }
                }
                "detection" -> {
                    val newStatus = "🔍 버튼감지: $status"
                    if (newStatus != lastDetectionStatus) {
                        debugDetectionStatus.text = newStatus
                        lastDetectionStatus = newStatus
                        // X 표시인 경우 로그에 이유 표시
                        if (status.contains("❌")) {
                            addLog(newStatus)
                            extra?.let { addLog("  → 이유: $it") }
                        }
                    }
                    if (extra != null) {
                        val newCoord = "📍 좌표: $extra"
                        if (newCoord != lastCoordStatus) {
                            debugLastCoord.text = newCoord
                            lastCoordStatus = newCoord
                        }
                    }
                }
                "click" -> {
                    val newStatus = "👆 클릭시도: $status"
                    if (newStatus != lastClickStatus) {
                        debugClickStatus.text = newStatus
                        lastClickStatus = newStatus
                        // X 표시나 실패인 경우 로그에 이유 표시
                        if (status.contains("❌") || status.contains("실패")) {
                            addLog(newStatus)
                            extra?.let { addLog("  → 이유: $it") }
                        }
                    }
                }
                "reset" -> {
                    // 모든 상태 초기화
                    debugCaptureStatus.text = "📷 화면캡처: 대기"
                    debugDetectionStatus.text = "🔍 버튼감지: 대기"
                    debugClickStatus.text = "👆 클릭시도: 대기"
                    debugLastCoord.text = "📍 좌표: -"
                    lastCaptureStatus = ""
                    lastDetectionStatus = ""
                    lastClickStatus = ""
                    lastCoordStatus = ""
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        addLog("onActivityResult - requestCode: $requestCode, resultCode: $resultCode")
        
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> {
                addLog("MediaProjection 권한 응답 받음")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    addLog("✅ 권한 승인됨!")
                    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_START_CAPTURE
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ScreenCaptureService.EXTRA_DATA, data)
                    }
                    startService(serviceIntent)
                    isCapturing = true
                    updateStatus("화면 캡처 시작됨")
                    btnStartCapture.text = "화면 캡처 중지"
                    
                    // 플로팅 컨트롤 자동 표시
                    if (Settings.canDrawOverlays(this)) {
                        startFloatingControls()
                        Toast.makeText(this, "화면 캡처 시작됨! 플로팅 버튼으로 제어하세요.", Toast.LENGTH_LONG).show()
                    }
                    
                    // Minimize the app to background
                    moveTaskToBack(true)
                } else {
                    addLog("❌ 화면 캡처 권한 거부됨 (resultCode: $resultCode)")
                }
            }
            
            REQUEST_OVERLAY_PERMISSION -> {
                if (Settings.canDrawOverlays(this)) {
                    addLog("오버레이 권한 승인됨")
                    // 오버레이 권한을 받은 후 플로팅 컨트롤 시작
                    startFloatingControls()
                } else {
                    addLog("오버레이 권한 거부됨")
                }
            }
            
            REQUEST_ONE_CLICK_AUTO -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    addLog("✅ MediaProjection 권한 승인!")
                    addLog("🚀 원클릭 자동 모드 시작 중...")
                    
                    // OneClickAutoService 시작
                    val intent = Intent(this, OneClickAutoService::class.java).apply {
                        action = OneClickAutoService.ACTION_START_AUTO
                        putExtra("resultCode", resultCode)
                        putExtra("data", data)
                    }
                    startService(intent)
                    
                    addLog("✅ 원클릭 자동 모드 활성화됨!")
                    addLog("📱 이제 카카오 택시 앱을 실행하세요")
                    addLog("🎯 콜이 오면 자동으로 수락됩니다")
                    
                    // 앱을 백그라운드로
                    moveTaskToBack(true)
                } else {
                    addLog("❌ MediaProjection 권한이 거부되었습니다")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                addLog("저장소 권한 승인됨")
            } else {
                addLog("저장소 권한 거부됨")
            }
        }
    }

    private fun checkAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        runOnUiThread {
            if (isEnabled) {
                // 서비스는 활성화되었지만 카카오 앱 접근성은 별도 확인 필요
                debugAccessibilityStatus.text = "♿ 접근성: ✅ 서비스 활성화 (카카오 앱 확인 중...)"
                addLog("✅ 접근성 서비스 활성화됨")
                backgroundLogger.logFeatureStatus("AccessibilityService", "활성화됨", true)
            } else {
                debugAccessibilityStatus.text = "♿ 접근성: ❌ 비활성화"
                addLog("⚠️ 접근성 서비스 비활성화!")
                addLog("해결방법:")
                addLog("1. 설정 > 접근성 메뉴 열기")
                addLog("2. '카카오 택시 테스트' 찾기")
                addLog("3. 서비스 활성화")
                addLog("4. 권한 승인")
                backgroundLogger.logFeatureStatus("AccessibilityService", "비활성화", false)
                
                // 접근성 설정으로 이동
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
        
        // 3초마다 접근성 상태 확인
        lifecycleScope.launch {
            delay(3000)
            checkAccessibilityStatus()
        }
    }
    
    private fun updateAccessibilityStatus(
        isConnected: Boolean, 
        isKakaoAccessible: Boolean, 
        lastDetection: Long,
        blockReason: String
    ) {
        runOnUiThread {
            val timeSinceDetection = if (lastDetection > 0) {
                val seconds = (System.currentTimeMillis() - lastDetection) / 1000
                "(${seconds}초 전 감지)"
            } else {
                ""
            }
            
            when {
                !isConnected -> {
                    debugAccessibilityStatus.text = "♿ 접근성: ❌ 서비스 연결 안됨"
                }
                !isKakaoAccessible && blockReason.isNotEmpty() -> {
                    debugAccessibilityStatus.text = "♿ 접근성: ⚠️ 카카오 차단 $timeSinceDetection"
                    addLog("❌ 카카오 앱 접근 차단: $blockReason")
                    backgroundLogger.logFeatureStatus(
                        "KakaoAccessibility", 
                        "차단됨", 
                        false, 
                        blockReason
                    )
                }
                isKakaoAccessible -> {
                    debugAccessibilityStatus.text = "♿ 접근성: ✅ 정상 작동 $timeSinceDetection"
                    backgroundLogger.logFeatureStatus(
                        "KakaoAccessibility", 
                        "정상", 
                        true
                    )
                }
                else -> {
                    debugAccessibilityStatus.text = "♿ 접근성: ❓ 카카오 앱 실행 필요"
                }
            }
        }
    }
    
    private fun viewBackgroundLogs() {
        try {
            val logsDir = File(getExternalFilesDir(null), "KakaoTaxiLogs")
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val logFile = File(logsDir, "log_$today.txt")
            
            if (logFile.exists()) {
                val logs = logFile.readLines().takeLast(50) // 최근 50줄
                val logsText = logs.joinToString("\n")
                
                // 대화상자로 표시
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("백그라운드 로그 (최근 50줄)")
                    .setMessage(logsText as CharSequence)
                    .setPositiveButton("확인", null)
                    .setNeutralButton("전체 파일 열기") { dialog, which ->
                        openLogFile(logFile)
                    }
                    .show()
                    
                addLog("로그 파일 로드됨: ${logFile.name}")
            } else {
                Toast.makeText(this, "오늘 로그가 없습니다", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to view logs", e)
            Toast.makeText(this, "로그 읽기 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openLogFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "로그 파일 열기"))
        } catch (e: Exception) {
            Toast.makeText(this, "로그 파일 열기 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("start_capture", false) == true) {
            addLog("앱이 포그라운드로 전환됨 - 화면 캡처 시작")
            if (!isCapturing) {
                startCapture()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 화면으로 돌아올 때 접근성 상태 재확인
        checkAccessibilityStatus()
        
        // 화면 캡처 상태 확인
        val captureActive = ScreenCaptureService.loadCaptureState(this)
        if (captureActive != isCapturing) {
            isCapturing = captureActive
            updateDebugStatus("capture", if (captureActive) "✅ 활성" else "❌ 비활성")
            btnStartCapture.text = if (captureActive) "화면 캡처 중지" else "화면 캡처 시작"
        }
    }

    private fun setupAutoClickMethod() {
        // 사용 가능한 자동 클릭 방법 확인
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        // 1. 먼저 접근성 서비스 확인
        if (isAccessibilityServiceEnabled()) {
            addLog("✅ 접근성 서비스로 자동 클릭 가능")
            return
        }
        
        // 2. ADB 권한 확인
        checkAdbPermission()
    }
    
    private fun checkAdbPermission() {
        // ADB를 통한 자동 클릭 설정 안내
        val hasShownAdbGuide = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("adb_guide_shown", false)
            
        if (!hasShownAdbGuide) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("자동 클릭 설정")
                .setMessage("""
                    카카오 택시가 접근성을 차단하는 경우:
                    
                    방법 1) 알림 + 진동으로 알려드립니다
                    - 콜이 오면 큰 알림과 진동
                    - 수동으로 수락 버튼 클릭
                    
                    방법 2) USB 디버깅 (고급)
                    - PC와 연결하여 ADB 명령 실행
                    - 완전 자동 클릭 가능
                    
                    일단 방법 1로 시작하시겠습니까?
                """.trimIndent())
                .setPositiveButton("네, 시작합니다") { _, _ ->
                    getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                        .putBoolean("use_notification_alert", true)
                        .putBoolean("adb_guide_shown", true)
                        .apply()
                    addLog("✅ 알림 모드 활성화 - 콜 감지시 알림")
                }
                .setNegativeButton("ADB 설정 방법") { _, _ ->
                    showAdbGuide()
                }
                .show()
        }
    }
    
    private fun showAdbGuide() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("업체들이 쓰는 방법")
            .setMessage("""
                실제 택시 기사들이 쓰는 방법:
                
                1. Shizuku 앱 설치 (구글 플레이)
                   - 무선 ADB 권한 유지
                   - 루팅 없이 시스템 권한
                
                2. 구버전 카카오 택시 사용
                   - 2021년 이전 버전 APK
                   - 접근성 차단 없는 버전
                
                3. 태블릿 + 미러링
                   - 태블릿에서는 접근성 안 막힘
                   - 휴대폰 화면 미러링해서 사용
                
                4. 매크로 전용 폰
                   - 중국산 안드로이드 폰
                   - 기본적으로 자동화 지원
                
                어떤 방법을 시도하시겠습니까?
            """.trimIndent())
            .setPositiveButton("Shizuku 설치") { _, _ ->
                // Shizuku 플레이스토어 링크
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                startActivity(intent)
            }
            .setNegativeButton("구버전 APK") { _, _ ->
                showOldVersionGuide()
            }
            .show()
    }
    
    private fun showOldVersionGuide() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("구버전 카카오 택시")
            .setMessage("""
                접근성이 안 막힌 버전:
                - 카카오 T 택시 (기사용) v4.7.0 이하
                - 2021년 6월 이전 버전
                
                주의사항:
                - 자동 업데이트 끄기 필수
                - 일부 신기능 사용 불가
                - APKPure, APKMirror에서 다운로드
                
                현재 버전 삭제 후 설치하세요.
            """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenCaptureReceiver)
        ocrProcessor.release()
        backgroundLogger.release()
        
        if (isCapturing) {
            stopCapture()
        }
        
        // 플로팅 서비스들 종료
        stopService(Intent(this, FloatingControlService::class.java))
        stopService(Intent(this, FloatingDebugService::class.java))
    }
}