package com.kakao.taxi.test.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakao.taxi.test.MainActivity
import com.kakao.taxi.test.R
import com.kakao.taxi.test.module.*
import kotlinx.coroutines.*
import java.io.File
import android.graphics.Point
import android.app.PendingIntent

class AutoDetectionService : Service() {
    companion object {
        private const val TAG = "AutoDetectionService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "auto_detection_channel"
        
        const val ACTION_START_DETECTION = "ACTION_START_DETECTION"
        const val ACTION_STOP_DETECTION = "ACTION_STOP_DETECTION"
        const val ACTION_UPDATE_FILTER = "ACTION_UPDATE_FILTER"
        const val ACTION_TOGGLE_DETECTION = "ACTION_TOGGLE_DETECTION"
        
        const val DETECTION_INTERVAL_FAST = 100L // 0.1초마다 감지 (초고속)
        const val DETECTION_INTERVAL_NORMAL = 500L // 0.5초마다 감지 (카카오 앱 활성화시)
        const val DETECTION_INTERVAL_IDLE = 2000L // 2초마다 감지 (대기 모드)
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var detectionJob: Job? = null
    private var deleteCompletedCallJob: Job? = null
    
    private lateinit var openCVMatcher: OpenCVMatcher
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var clickHandler: ClickEventHandler
    private lateinit var yellowButtonDetector: YellowButtonDetector
    private lateinit var debugHelper: DebugHelper
    private lateinit var kakaoTaxiDetector: KakaoTaxiDetector
    private lateinit var enhancedRecognition: EnhancedImageRecognition
    private lateinit var smartClickSimulator: SmartClickSimulator
    
    // 디버그 모드 설정 (설정에서 변경 가능)
    private var debugMode = true
    
    private var filterCriteria: FilterCriteria? = null
    private var templateBitmap: Bitmap? = null
    private var isDetecting = false
    private var currentDetectionMode = DetectionMode.IDLE
    
    enum class DetectionMode {
        IDLE,     // 카카오 앱이 비활성화 (2초)
        NORMAL,   // 카카오 앱 활성화 (0.5초)
        FAST      // 콜 화면 감지됨 (0.1초)
    }
    
    // 화면 캡처 서비스와 통신
    private var screenCaptureCallback: ((Bitmap) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initModules()
        loadTemplateImage()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> {
                filterCriteria = intent.getSerializableExtra("filter") as? FilterCriteria
                startDetection()
            }
            ACTION_STOP_DETECTION -> {
                stopDetection()
            }
            ACTION_UPDATE_FILTER -> {
                filterCriteria = intent.getSerializableExtra("filter") as? FilterCriteria
            }
            ACTION_TOGGLE_DETECTION -> {
                if (isDetecting) {
                    pauseDetection()
                } else {
                    resumeDetection()
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "자동 감지 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "택시 콜 자동 감지 중"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AutoDetectionService::class.java).apply {
                action = ACTION_STOP_DETECTION
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause Intent
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutoDetectionService::class.java).apply {
                action = ACTION_TOGGLE_DETECTION
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Settings Intent
        val settingsIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionIcon = if (isDetecting) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val actionText = if (isDetecting) "일시정지" else "시작"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("카카오 택시 자동 감지")
            .setContentText(if (isDetecting) "감지 실행 중..." else "일시정지됨")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(actionIcon, actionText, toggleIntent)
            .addAction(android.R.drawable.ic_menu_preferences, "설정", settingsIntent)
            .addAction(android.R.drawable.ic_delete, "종료", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun initModules() {
        openCVMatcher = OpenCVMatcher()
        ocrProcessor = OCRProcessor(this)
        clickHandler = ClickEventHandler(this)
        yellowButtonDetector = YellowButtonDetector()
        debugHelper = DebugHelper(this)
        kakaoTaxiDetector = KakaoTaxiDetector()
        enhancedRecognition = EnhancedImageRecognition()
        
        // SmartClickSimulator는 접근성 서비스 인스턴스가 필요
        val accessibilityInstance = KakaoTaxiAccessibilityService.getInstance()
        if (accessibilityInstance != null) {
            smartClickSimulator = SmartClickSimulator(accessibilityInstance)
        }
        
        // Initialize OCR
        serviceScope.launch {
            ocrProcessor.initialize()
        }
    }

    private fun loadTemplateImage() {
        // Load template image for call accept button
        // In real implementation, load from assets or storage
        try {
            val templateFile = File(filesDir, "call_accept_button.png")
            if (templateFile.exists()) {
                templateBitmap = BitmapFactory.decodeFile(templateFile.absolutePath)
                Log.d(TAG, "Template image loaded")
            } else {
                Log.w(TAG, "Template image not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load template image", e)
        }
    }

    private fun startDetection() {
        if (isDetecting) {
            Log.d(TAG, "Already detecting, skipping start")
            return
        }
        
        Log.d(TAG, "Starting auto detection service")
        startForeground(NOTIFICATION_ID, createNotification())
        isDetecting = true
        
        // 초기 상태 전송
        sendDebugUpdate("capture", "🔄 자동 감지 시작됨")
        updateFloatingDebugStatus("capture", "시작됨")
        updateFloatingDebugStatus("button", "준비")
        updateFloatingDebugStatus("click", "준비")
        updateFloatingDebugStatus("app", "확인중")
        updateFloatingDebugStatus("performance", "2초")
        
        // Start detection loop
        detectionJob = serviceScope.launch {
            while (isActive && isDetecting) {
                performDetection()
                // 동적 간격 설정
                val interval = when (currentDetectionMode) {
                    DetectionMode.FAST -> DETECTION_INTERVAL_FAST
                    DetectionMode.NORMAL -> DETECTION_INTERVAL_NORMAL
                    DetectionMode.IDLE -> DETECTION_INTERVAL_IDLE
                }
                delay(interval)
            }
        }
        
        // Start delete completed call loop (5초마다)
        deleteCompletedCallJob = serviceScope.launch {
            while (isActive && isDetecting) {
                delay(5000L) // 5초 대기
                deleteCompletedCalls()
            }
        }
        
        Log.d(TAG, "Auto detection started")
    }

    private suspend fun performDetection() {
        try {
            Log.d(TAG, "Performing detection cycle...")
            
            // 카카오 택시 앱이 활성화되어 있는지 확인
            val kakaoStatus = checkKakaoAppStatus()
            Log.d(TAG, "Kakao app status: $kakaoStatus")
            
            when (kakaoStatus) {
                KakaoAppStatus.NOT_ACTIVE -> {
                    currentDetectionMode = DetectionMode.IDLE
                    sendDebugUpdate("capture", "⏸️ 카카오 택시 앱 대기 중... (2초 간격)")
                    updateFloatingDebugStatus("app", "⏸️ 대기")
                    updateFloatingDebugStatus("performance", "2초")
                    // 테스트 모드가 아니면 여기서 끝
                    val bypassKakaoCheck = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getBoolean("bypass_kakao_check", false)
                    if (!bypassKakaoCheck) {
                        updateFloatingDebugStatus("capture", "대기")
                        updateFloatingDebugStatus("button", "대기")
                        return
                    }
                    Log.d(TAG, "Test mode enabled, continuing detection...")
                }
                KakaoAppStatus.ACTIVE -> {
                    if (currentDetectionMode == DetectionMode.IDLE) {
                        currentDetectionMode = DetectionMode.NORMAL
                        Log.d(TAG, "카카오 앱 활성화 감지 - 0.5초 간격으로 전환")
                        updateFloatingDebugStatus("app", "✅ 활성화")
                        updateFloatingDebugStatus("performance", "0.5초")
                    }
                }
                KakaoAppStatus.CALL_SCREEN -> {
                    if (currentDetectionMode != DetectionMode.FAST) {
                        currentDetectionMode = DetectionMode.FAST
                        Log.d(TAG, "콜 화면 감지 - 0.1초 초고속 모드 전환")
                    }
                }
            }
            
            // 디버그: 상태 초기화
            sendDebugUpdate("reset", "")
            
            // ScreenCaptureService가 실행 중인지 확인
            val captureActive = ScreenCaptureService.loadCaptureState(this@AutoDetectionService)
            if (!captureActive) {
                Log.w(TAG, "ScreenCaptureService is not running")
                sendDebugUpdate("capture", "❌ 화면 캡처 서비스 비활성화")
                updateFloatingDebugStatus("capture", "❌ 비활성")
                updateFloatingDebugStatus("button", "대기")
                updateFloatingDebugStatus("click", "대기")
                return
            }
            
            // Request screen capture
            sendDebugUpdate("capture", "📸 캡처 요청 중...")
            updateFloatingDebugStatus("capture", "📸 캡처중")
            
            // 캡처 서비스가 활성화되어 있는지 다시 한번 확인
            val isServiceActive = ScreenCaptureService.loadCaptureState(this@AutoDetectionService)
            if (!isServiceActive) {
                // 접근성 서비스로 대체 시도
                Log.w(TAG, "ScreenCaptureService not active, trying accessibility service")
                sendDebugUpdate("capture", "🔄 접근성 모드")
                updateFloatingDebugStatus("capture", "🔄 접근성")
                
                // 접근성 서비스에서 화면 정보 읽기 시도
                tryAccessibilityDetection()
                return
            }
            
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_ONCE
            }
            startService(captureIntent)
            
            // Wait for capture result (캡처 완료 대기)
            delay(300) // 더 긴 대기 시간
            
            // Get captured bitmap from ScreenCaptureService
            val bitmap = ScreenCaptureService.capturedBitmap
            if (bitmap != null) {
                Log.d(TAG, "Got captured bitmap: ${bitmap.width}x${bitmap.height}")
                sendDebugUpdate("capture", "✅ 캡처 성공")
                analyzeCapturedScreen(bitmap)
                // Clear the bitmap after use
                ScreenCaptureService.capturedBitmap = null
            } else {
                Log.w(TAG, "No bitmap captured")
                sendDebugUpdate("capture", "❌ 캡처 실패", "비트맵이 없음")
                
                // 캡처 서비스 상태 확인 및 해결방법 제시
                val captureServiceActive = ScreenCaptureService.loadCaptureState(this@AutoDetectionService)
                if (!captureServiceActive) {
                    sendDebugUpdate("capture", "🔄 화면 캡처 서비스 재시작 필요")
                    Log.e(TAG, "⚠️ 해결방법:")
                    Log.e(TAG, "1. 메인 화면에서 '화면 캡처 시작' 클릭")
                    Log.e(TAG, "2. MediaProjection 권한 승인")
                    Log.e(TAG, "3. 플로팅 버튼에서 ▶️ 클릭")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            sendDebugUpdate("capture", "❌ 캡처 실패", e.message ?: "Unknown error")
        }
    }
    
    enum class KakaoAppStatus {
        NOT_ACTIVE,    // 카카오 앱 비활성화
        ACTIVE,        // 카카오 앱 활성화됨
        CALL_SCREEN    // 콜 대기/상세 화면
    }
    
    private fun checkKakaoAppStatus(): KakaoAppStatus {
        // 기존 isKakaoTaxiActive 로직 활용
        val accessibilityStatus = KakaoTaxiAccessibilityService.getStatus()
        
        if (!accessibilityStatus.isConnected) {
            return KakaoAppStatus.NOT_ACTIVE
        }
        
        // 테스트 모드 확인
        val bypassKakaoCheck = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("bypass_kakao_check", false)
        if (bypassKakaoCheck) {
            return KakaoAppStatus.CALL_SCREEN // 테스트 모드에서는 항상 빠른 모드
        }
        
        // 카카오 앱 활성화 여부
        val timeSinceLastDetection = System.currentTimeMillis() - accessibilityStatus.lastKakaoDetection
        val isRecent = timeSinceLastDetection < 10000
        
        if (!isRecent || !accessibilityStatus.isKakaoAccessible) {
            return KakaoAppStatus.NOT_ACTIVE
        }
        
        // 콜 화면인지 추가 확인 (추후 구현)
        // 현재는 카카오 앱 활성화시 ACTIVE 반환
        return KakaoAppStatus.ACTIVE
    }
    
    private fun isKakaoTaxiActive(): Boolean {
        // 접근성 서비스에서 현재 앱 정보 가져오기
        val accessibilityStatus = KakaoTaxiAccessibilityService.getStatus()
        
        // 디버그 로그 추가
        Log.d(TAG, "Checking Kakao app status:")
        Log.d(TAG, "- isConnected: ${accessibilityStatus.isConnected}")
        Log.d(TAG, "- isKakaoAccessible: ${accessibilityStatus.isKakaoAccessible}")
        Log.d(TAG, "- lastKakaoDetection: ${accessibilityStatus.lastKakaoDetection}")
        Log.d(TAG, "- blockReason: ${accessibilityStatus.blockReason}")
        
        // 접근성 서비스가 연결되지 않은 경우
        if (!accessibilityStatus.isConnected) {
            Log.w(TAG, "Accessibility service not connected")
            sendDebugUpdate("capture", "⚠️ 접근성 서비스 연결 안됨")
            return false
        }
        
        // 개발/테스트 모드: 카카오 앱 체크 우회 옵션
        val bypassKakaoCheck = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("bypass_kakao_check", false)
        if (bypassKakaoCheck) {
            Log.d(TAG, "Bypassing Kakao app check (test mode)")
            return true
        }
        
        // 프로그레시브 감지 모드: 현재 실행 중인 앱 확인
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.let { am ->
            val tasks = am.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                val topPackage = tasks[0].topActivity?.packageName
                if (topPackage == "com.kakao.taxi.driver") {
                    Log.d(TAG, "Kakao app detected via ActivityManager")
                    return true
                }
            }
        }
        
        // 최근 카카오 앱 감지 시간 확인 (30초로 늘림 - 접근성 차단 대응)
        val timeSinceLastDetection = System.currentTimeMillis() - accessibilityStatus.lastKakaoDetection
        val isRecent = timeSinceLastDetection < 30000 // 30초로 증가
        
        Log.d(TAG, "Time since last detection: ${timeSinceLastDetection}ms, isRecent: $isRecent")
        
        return isRecent && accessibilityStatus.isKakaoAccessible
    }

    fun analyzeCapturedScreen(bitmap: Bitmap) {
        serviceScope.launch {
            val startTime = System.currentTimeMillis()
            var error: String? = null
            var ocrText: String? = null
            var clickSuccess = false
            var callButtonFound = false
            var shouldAccept = false
            var buttonCandidate: ButtonCandidate? = null
            
            try {
                // 디버그: 화면 캡처 성공
                sendDebugUpdate("capture", "✅ 캡처 성공")
                
                // 디버그 모드: 원본 스크린샷 저장
                if (debugMode) {
                    debugHelper.saveOriginalScreenshot(bitmap)
                }
                
                // 1. 카카오 택시 화면 감지
                sendDebugUpdate("detection", "🔍 카카오 화면 확인 중...")
                val isCallListScreen = kakaoTaxiDetector.isCallListScreen(bitmap)
                val isCallDetailScreen = kakaoTaxiDetector.isCallDetailScreen(bitmap)
                val isKakaoScreen = isCallListScreen || isCallDetailScreen
                
                // 콜 화면 감지시 초고속 모드로 전환
                if (isKakaoScreen && currentDetectionMode != DetectionMode.FAST) {
                    currentDetectionMode = DetectionMode.FAST
                    Log.d(TAG, "콜 화면 확인 - 초고속 모드(0.1초) 활성화")
                    sendDebugUpdate("capture", "⚡ 초고속 모드 활성화 (0.1초)")
                    
                    // 성능 상태 업데이트
                    updateFloatingDebugStatus("performance", "⚡ 0.1초")
                }
                
                if (!isKakaoScreen) {
                    // 화면 분석을 위해 모든 노란색 영역 체크
                    val yellowAreas = kakaoTaxiDetector.detectAllYellowButtons(bitmap)
                    val reason = if (yellowAreas.isEmpty()) {
                        "노란색 버튼이 감지되지 않음"
                    } else {
                        "노란색 영역 ${yellowAreas.size}개 발견했으나 카카오 화면 패턴과 불일치"
                    }
                    
                    Log.d(TAG, "Not a Kakao Taxi screen: $reason")
                    sendDebugUpdate("detection", "❌ 카카오 택시 화면 아님", reason)
                    
                    if (debugMode) {
                        error = "화면 감지 실패: $reason"
                        debugHelper.saveDebugInfo(
                            bitmap,
                            "not_kakao_screen",
                            mapOf(
                                "reason" to reason,
                                "yellow_areas_count" to yellowAreas.size.toString(),
                                "screen_width" to bitmap.width.toString(),
                                "screen_height" to bitmap.height.toString(),
                                "timestamp" to System.currentTimeMillis().toString()
                            )
                        )
                    }
                    return@launch
                }
                
                // 2. Yellow button detection using KakaoTaxiDetector
                sendDebugUpdate("detection", "🔍 노란 버튼 찾는 중...")
                val yellowButtons = kakaoTaxiDetector.detectAllYellowButtons(bitmap)
                if (yellowButtons.isNotEmpty()) {
                    // 첫 번째 버튼을 선택 (또는 조건에 따라 선택)
                    buttonCandidate = yellowButtons[0]
                    callButtonFound = true
                    Log.d(TAG, "Kakao yellow button found at: (${buttonCandidate.centerX}, ${buttonCandidate.centerY})")
                    sendDebugUpdate("detection", "✅ 버튼 ${yellowButtons.size}개 발견", "(${buttonCandidate.centerX}, ${buttonCandidate.centerY})")
                    
                    // 디버그 모드: 감지 결과 저장
                    if (debugMode) {
                        debugHelper.saveDetectionResult(bitmap, buttonCandidate)
                    }
                    
                    // Convert to MatchResult for overlay
                    val matchResult = MatchResult(
                        location = android.graphics.Point(buttonCandidate.bounds.left, buttonCandidate.bounds.top),
                        confidence = buttonCandidate.confidence.toDouble(),
                        width = buttonCandidate.bounds.width(),
                        height = buttonCandidate.bounds.height()
                    )
                    updateOverlay(listOf(matchResult), emptyList())
                }
                
                // 2. Fallback to template matching if yellow detection fails
                if (!callButtonFound) {
                    templateBitmap?.let { template ->
                        val matchResult = openCVMatcher.findTemplate(bitmap, template)
                        matchResult?.let { match ->
                            callButtonFound = true
                            Log.d(TAG, "Call button found by template at: ${match.location}")
                            updateOverlay(listOf(match), emptyList())
                        }
                    }
                }
                
                // 3. OCR - 필터 없이 모든 텍스트 인식
                val ocrResults = ocrProcessor.extractTextWithRegions(bitmap)
                
                // Update overlay with OCR results
                updateOverlay(emptyList(), ocrResults)
                
                // 디버그: 인식된 모든 텍스트 로그
                if (ocrResults.isNotEmpty()) {
                    Log.d(TAG, "인식된 텍스트 ${ocrResults.size}개:")
                    ocrResults.forEach { result ->
                        Log.d(TAG, "- ${result.text} at (${result.boundingBox.left}, ${result.boundingBox.top})")
                    }
                    sendDebugUpdate("detection", "📝 텍스트 ${ocrResults.size}개 인식", ocrResults.joinToString(", ") { it.text })
                } else {
                    Log.d(TAG, "텍스트 인식 실패")
                    sendDebugUpdate("detection", "❌ 텍스트 인식 실패")
                }
                
                // 노란 버튼 발견시 바로 클릭 (필터 무시)
                if (callButtonFound) {
                    // 필터 체크 없이 바로 수락
                    shouldAccept = true
                    Log.d(TAG, "노란 버튼 발견 - 즉시 수락!")
                    
                    if (shouldAccept) {
                        Log.d(TAG, "노란 버튼 발견 - 필터 무시하고 즉시 수락!")
                        
                        // Extract amount and distance for notification
                        val (amount, distance) = ocrProcessor.extractAmountAndDistance(bitmap)
                        updateNotification("콜 감지! 금액: ${amount}원, 거리: ${distance}km")
                        
                        // 디버그 모드: OCR 결과 저장
                        if (debugMode) {
                            ocrText = ocrResults.joinToString(" ") { it.text }
                            debugHelper.saveOCRResult(bitmap, ocrText, amount, distance)
                        }
                        
                        // Perform click on call button
                        if (buttonCandidate != null) {
                            // Try automated click first
                            sendDebugUpdate("click", "👆 클릭 시도 중... (${buttonCandidate.centerX}, ${buttonCandidate.centerY})")
                            val clicked = clickHandler.performClick(buttonCandidate.centerX, buttonCandidate.centerY)
                            if (clicked) {
                                Log.d(TAG, "Call accepted successfully at yellow button")
                                sendDebugUpdate("click", "✅ 클릭 성공!")
                                updateNotification("콜 수락 완료!")
                                clickSuccess = true
                            } else {
                                // Fallback: Show notification with click location
                                Log.w(TAG, "Auto-click failed, showing manual notification")
                                sendDebugUpdate("click", "⚠️ 자동 클릭 실패 - 수동 알림")
                                showManualClickNotification(
                                    buttonCandidate.centerX, 
                                    buttonCandidate.centerY,
                                    amount,
                                    distance
                                )
                                error = "Auto-click failed, manual notification shown"
                            }
                        } else {
                            // Fallback to template matching
                            templateBitmap?.let { template ->
                                val matchResult = openCVMatcher.findTemplate(bitmap, template)
                                matchResult?.let { match ->
                                    val centerX = match.location.x + match.width / 2
                                    val centerY = match.location.y + match.height / 2
                                    
                                    val clicked = clickHandler.performClick(centerX, centerY)
                                    if (clicked) {
                                        Log.d(TAG, "Call accepted successfully at template match")
                                        updateNotification("콜 수락 완료!")
                                        clickSuccess = true
                                    } else {
                                        Log.e(TAG, "Failed to click call button")
                                        error = "Click failed at template location"
                                    }
                                }
                            }
                        }
                    }
                } else if (!callButtonFound) {
                    // 향상된 이미지 인식으로 재시도
                    Log.d(TAG, "Standard detection failed, trying enhanced recognition")
                    sendDebugUpdate("detection", "🔄 향상된 인식 시도 중...")
                    
                    val enhancedButtons = enhancedRecognition.detectButtonsParallel(bitmap)
                    if (enhancedButtons.isNotEmpty()) {
                        // 향상된 인식으로 버튼 발견
                        buttonCandidate = enhancedButtons.maxByOrNull { it.confidence }
                        callButtonFound = true
                        
                        Log.d(TAG, "Enhanced recognition found ${enhancedButtons.size} buttons")
                        sendDebugUpdate("detection", "✅ 향상된 인식 성공", "(${buttonCandidate?.centerX}, ${buttonCandidate?.centerY})")
                        
                        // 필터 무시하고 바로 클릭
                        serviceScope.launch {
                            performEnhancedClick(buttonCandidate!!)
                        }
                    } else {
                        // 그래도 못 찾은 경우
                        val reason = if (yellowButtons.isEmpty()) {
                            "노란색 버튼이 감지되지 않음"
                        } else {
                            "버튼 크기 조건 미충족 (너무 작거나 큼)"
                        }
                        sendDebugUpdate("detection", "❌ 버튼 없음 (${yellowButtons.size}개)", reason)
                    }
                    
                    if (debugMode) {
                        debugHelper.saveDetectionResult(bitmap, null)
                        debugHelper.saveDebugInfo(
                            bitmap,
                            "no_button_found",
                            mapOf(
                                "screen_type" to if (kakaoTaxiDetector.isCallListScreen(bitmap)) "call_list" else "call_detail",
                                "yellow_buttons_count" to yellowButtons.size.toString(),
                                "timestamp" to System.currentTimeMillis().toString(),
                                "filter_active" to (filterCriteria != null).toString()
                            )
                        )
                    }
                    error = "No yellow button found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Screen analysis failed", e)
                error = e.message
            } finally {
                // 디버그 모드: 전체 세션 로그 저장
                if (debugMode) {
                    val log = debugHelper.createDetectionLog(
                        timestamp = startTime,
                        screenCaptured = true,
                        buttonFound = callButtonFound,
                        candidate = buttonCandidate,
                        ocrResult = ocrText,
                        clickAttempted = buttonCandidate != null && shouldAccept,
                        clickSuccess = clickSuccess,
                        error = error
                    )
                    debugHelper.saveLogFile(log)
                    
                    // 디버그 폴더 경로 로그
                    Log.d(TAG, "Debug files saved to: ${debugHelper.getDebugFolderPath()}")
                }
            }
        }
    }

    private fun updateOverlay(matches: List<MatchResult>, ocrResults: List<OCRResult>) {
        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_MATCH
            putExtra("matches", ArrayList(matches))
        }
        startService(overlayIntent)
        
        val ocrIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_OCR
            putExtra("ocr_results", ArrayList(ocrResults))
        }
        startService(ocrIntent)
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("택시 자동 감지")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun pauseDetection() {
        isDetecting = false
        detectionJob?.cancel()
        deleteCompletedCallJob?.cancel()
        
        // Update notification to show paused state
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Auto detection paused")
    }
    
    private fun resumeDetection() {
        if (isDetecting) return
        
        isDetecting = true
        
        // Update notification to show running state
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        // Restart detection loop
        detectionJob = serviceScope.launch {
            while (isActive && isDetecting) {
                performDetection()
                // 동적 간격 설정
                val interval = when (currentDetectionMode) {
                    DetectionMode.FAST -> DETECTION_INTERVAL_FAST
                    DetectionMode.NORMAL -> DETECTION_INTERVAL_NORMAL
                    DetectionMode.IDLE -> DETECTION_INTERVAL_IDLE
                }
                delay(interval)
            }
        }
        
        // Restart delete completed call loop
        deleteCompletedCallJob = serviceScope.launch {
            while (isActive && isDetecting) {
                delay(5000L) // 5초 대기
                deleteCompletedCalls()
            }
        }
        
        Log.d(TAG, "Auto detection resumed")
    }

    private suspend fun deleteCompletedCalls() {
        try {
            // Request screen capture
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_ONCE
            }
            startService(captureIntent)
            
            // Wait for capture result (캡처 완료 대기)
            delay(200) // 더 긴 대기 시간
            
            // This would be triggered by callback from ScreenCaptureService
            // For now, log the action
            Log.d(TAG, "Checking for completed calls to delete")
            
        } catch (e: Exception) {
            Log.e(TAG, "Delete completed calls failed", e)
        }
    }
    
    fun processDeleteCompletedCalls(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                // 카카오 택시 콜 목록 화면인지 확인
                if (kakaoTaxiDetector.isCallListScreen(bitmap)) {
                    // 완료콜 삭제 버튼 찾기
                    val deleteButton = kakaoTaxiDetector.findDeleteCompletedCallButton(bitmap)
                    deleteButton?.let { button ->
                        Log.d(TAG, "Delete button found at: (${button.centerX}, ${button.centerY})")
                        
                        // 클릭 수행
                        val clicked = clickHandler.performClick(button.centerX, button.centerY)
                        if (clicked) {
                            Log.d(TAG, "Delete completed calls button clicked")
                            updateNotification("완료콜 삭제 완료")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process delete completed calls failed", e)
            }
        }
    }

    private fun stopDetection() {
        isDetecting = false
        detectionJob?.cancel()
        deleteCompletedCallJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Auto detection stopped")
    }

    /**
     * 수동 클릭 알림 표시
     */
    private fun showManualClickNotification(x: Int, y: Int, amount: Int?, distance: Float?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 알림음과 진동 포함
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚖 콜 감지! 수동 클릭 필요")
            .setContentText("금액: ${amount ?: "?"}원, 거리: ${distance ?: "?"}km")
            .setSubText("위치: ($x, $y)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 소리, 진동
            .setAutoCancel(true)
            .setTimeoutAfter(10000) // 10초 후 자동 제거
            .addAction(
                android.R.drawable.ic_menu_view,
                "화면으로 이동",
                PendingIntent.getActivity(
                    this, 
                    System.currentTimeMillis().toInt(),
                    packageManager.getLaunchIntentForPackage("com.kakao.taxi.driver"),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
            
        notificationManager.notify(
            System.currentTimeMillis().toInt(), // 고유 ID로 여러 알림 가능
            notification
        )
        
        // 플로팅 알림도 표시
        showFloatingAlert(x, y, amount, distance)
    }
    
    /**
     * 플로팅 알림 표시
     */
    private fun showFloatingAlert(x: Int, y: Int, amount: Int?, distance: Float?) {
        val floatingIntent = Intent(this, FloatingAlertService::class.java).apply {
            putExtra("x", x)
            putExtra("y", y)
            putExtra("amount", amount ?: 0)
            putExtra("distance", distance ?: 0f)
        }
        startService(floatingIntent)
    }

    /**
     * 디버그 상태 업데이트 브로드캐스트
     */
    private fun sendDebugUpdate(type: String, status: String, extra: String? = null) {
        val intent = Intent("com.kakao.taxi.test.DEBUG_UPDATE").apply {
            putExtra("type", type)
            putExtra("status", status)
            extra?.let { putExtra("extra", it) }
        }
        sendBroadcast(intent)
        
        // FloatingDebugService에도 상태 전송
        updateFloatingDebugStatus(type, status)
    }
    
    private fun updateFloatingDebugStatus(type: String, status: String) {
        val step = when (type) {
            "capture" -> "capture"
            "detection" -> "button"
            "click" -> "click"
            else -> return
        }
        
        val debugStatus = when {
            status.contains("✅") || status.contains("성공") -> "success"
            status.contains("❌") || status.contains("실패") -> "error"
            status.contains("⚡") || status.contains("초고속") -> "processing"
            status.contains("🔍") || status.contains("확인 중") -> "processing"
            status.contains("⏸️") || status.contains("대기") -> "idle"
            else -> "processing"
        }
        
        // FloatingDebugService로 상태 전송
        val debugIntent = Intent(this, FloatingDebugService::class.java).apply {
            action = "UPDATE_STATUS"
            putExtra("step", step)
            putExtra("status", debugStatus)
            putExtra("message", status)
        }
        startService(debugIntent)
    }
    
    /**
     * 향상된 클릭 수행 (SmartClickSimulator 사용)
     */
    private suspend fun performEnhancedClick(button: ButtonCandidate) {
        sendDebugUpdate("click", "🎯 스마트 클릭 시도 중...")
        
        try {
            // SmartClickSimulator가 초기화되었는지 확인
            if (!::smartClickSimulator.isInitialized) {
                val accessibilityInstance = KakaoTaxiAccessibilityService.getInstance()
                if (accessibilityInstance != null) {
                    smartClickSimulator = SmartClickSimulator(accessibilityInstance)
                } else {
                    // 폴백: 기존 클릭 핸들러 사용
                    Log.w(TAG, "SmartClickSimulator not available, using fallback")
                    val clicked = clickHandler.performClick(button.centerX, button.centerY)
                    if (clicked) {
                        sendDebugUpdate("click", "✅ 클릭 성공 (폴백)")
                    } else {
                        sendDebugUpdate("click", "❌ 클릭 실패")
                    }
                    return
                }
            }
            
            // 자연스러운 클릭 수행
            val success = smartClickSimulator.performNaturalClick(button.centerX, button.centerY)
            
            if (success) {
                Log.d(TAG, "Smart click successful at (${button.centerX}, ${button.centerY})")
                sendDebugUpdate("click", "✅ 스마트 클릭 성공!")
                updateNotification("콜 자동 수락 완료!")
                
                // 클릭 후 잠시 대기 (화면 전환 대기)
                delay(1000)
                
                // 연속 클릭이 필요한 경우 (예: 확인 팝업)
                if (needsConfirmation()) {
                    delay(500)
                    performConfirmationClick()
                }
            } else {
                Log.e(TAG, "Smart click failed")
                sendDebugUpdate("click", "⚠️ 스마트 클릭 실패", "수동 모드 전환")
                showManualClickNotification(button.centerX, button.centerY, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced click error", e)
            sendDebugUpdate("click", "❌ 클릭 오류", e.message)
        }
    }
    
    /**
     * 콜 수락 조건 확인
     */
    private fun shouldAcceptCall(ocrResults: List<OCRResult>, criteria: FilterCriteria): Boolean {
        // 필터가 없으면 모든 콜 수락
        if (!criteria.filterEnabled) return true
        
        // OCR 결과에서 금액 추출
        val amount = extractAmount(ocrResults)
        val distance = extractDistance(ocrResults)
        
        // 금액 기준 확인
        if (criteria.minAmount != null && criteria.minAmount > 0 && amount < criteria.minAmount) {
            Log.d(TAG, "Call rejected: amount $amount < ${criteria.minAmount}")
            return false
        }
        
        // 거리 기준 확인
        if (criteria.maxDistance != null && criteria.maxDistance > 0 && distance > criteria.maxDistance) {
            Log.d(TAG, "Call rejected: distance $distance > ${criteria.maxDistance}")
            return false
        }
        
        Log.d(TAG, "Call accepted: amount=$amount, distance=$distance")
        return true
    }
    
    /**
     * 확인 팝업 필요 여부 확인
     */
    private fun needsConfirmation(): Boolean {
        // 설정에서 확인 팝업 자동 처리 옵션 확인
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_confirm_popup", true)
    }
    
    /**
     * 확인 팝업 클릭
     */
    private suspend fun performConfirmationClick() {
        // 화면 중앙 하단 영역에서 확인 버튼 찾기
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2
        val bottomY = (metrics.heightPixels * 0.8).toInt()
        
        Log.d(TAG, "Attempting confirmation click at ($centerX, $bottomY)")
        smartClickSimulator.performNaturalClick(centerX, bottomY)
    }
    
    /**
     * OCR 결과에서 금액 추출
     */
    private fun extractAmount(ocrResults: List<OCRResult>): Int {
        for (result in ocrResults) {
            val amountPattern = Regex("(\\d{1,3}(,\\d{3})*|\\d+)\\s*원")
            val match = amountPattern.find(result.text)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toIntOrNull() ?: 0
            }
        }
        return 0
    }
    
    /**
     * OCR 결과에서 거리 추출
     */
    private fun extractDistance(ocrResults: List<OCRResult>): Float {
        for (result in ocrResults) {
            val distancePattern = Regex("(\\d+\\.?\\d*)\\s*(km|㎞)")
            val match = distancePattern.find(result.text)
            if (match != null) {
                return match.groupValues[1].toFloatOrNull() ?: 0f
            }
        }
        return 0f
    }

    private fun tryAccessibilityDetection() {
        // 접근성 서비스에 화면 읽기 요청
        sendBroadcast(Intent("com.kakao.taxi.test.REQUEST_SCREEN_READ"))
        
        // 잠시 대기 후 결과 확인
        serviceScope.launch {
            delay(100)
            
            // 노란색 텍스트나 "수락" 버튼 찾기
            val prefs = getSharedPreferences("accessibility_data", Context.MODE_PRIVATE)
            val foundYellowButton = prefs.getBoolean("found_yellow_button", false)
            val buttonText = prefs.getString("button_text", "")
            
            if (foundYellowButton && buttonText?.contains("수락") == true) {
                Log.d(TAG, "✅ 접근성으로 콜 감지: $buttonText")
                sendDebugUpdate("detection", "✅ 콜 감지됨", buttonText)
                
                // 자동 클릭 시도
                sendBroadcast(Intent("com.kakao.taxi.test.REQUEST_AUTO_CLICK"))
                
                // ADB 방식으로도 시도
                tryAdbClick()
            }
        }
    }

    private fun tryAdbClick() {
        // 방법 1: Runtime.exec로 직접 실행 (루트 필요)
        try {
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2
            val centerY = displayMetrics.heightPixels * 0.7f // 화면 하단 70% 위치
            
            Runtime.getRuntime().exec("input tap $centerX $centerY")
            Log.d(TAG, "ADB 클릭 시도: ($centerX, $centerY)")
        } catch (e: Exception) {
            Log.e(TAG, "ADB 클릭 실패", e)
        }
        
        // 방법 2: 알림으로 사용자에게 알리기
        showClickNotification()
    }
    
    private fun showClickNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 알림 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_alert",
                "콜 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "콜 수락 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 알림 생성
        val notification = NotificationCompat.Builder(this, "call_alert")
            .setContentTitle("🚕 콜 도착!")
            .setContentText("수락 버튼을 눌러주세요!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(9999, notification)
        
        // 진동
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ocrProcessor.release()
    }
}