package com.kakao.taxi.test.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.*

/**
 * 진짜 지능형 감지 서비스
 * 막 누르지 않고 정확히 감지해서 클릭!
 */
class IntelligentDetectionService : AccessibilityService() {
    
    companion object {
        private const val TAG = "IntelligentDetection"
        
        // 카카오 노란색 정확한 범위
        private const val KAKAO_YELLOW_HUE = 47f // HSV Hue 값
        private const val HUE_TOLERANCE = 5f
        private const val MIN_BUTTON_WIDTH = 200
        private const val MIN_BUTTON_HEIGHT = 80
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    
    // 학습된 버튼 위치 캐시
    private var lastKnownButtonRect: Rect? = null
    private var lastDetectionTime = 0L
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ 지능형 감지 서비스 시작")
        
        // 접근성 이벤트 설정
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.kakao.driver", "com.kakao.t")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // 카카오 앱 이벤트만 처리
        if (!isKakaoApp(event.packageName?.toString())) return
        
        Log.d(TAG, "📱 카카오 이벤트 감지: ${event.eventType}")
        
        // 화면 변화 감지 시 분석 시작
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 콜 화면 패턴 감지
                if (isCallScreen(event)) {
                    Log.d(TAG, "🎯 콜 화면 감지! 정밀 분석 시작")
                    analyzeAndClick()
                }
            }
        }
    }
    
    /**
     * 카카오 앱인지 확인
     */
    private fun isKakaoApp(packageName: String?): Boolean {
        return packageName == "com.kakao.driver" || packageName == "com.kakao.t"
    }
    
    /**
     * 콜 화면인지 패턴으로 감지
     */
    private fun isCallScreen(event: AccessibilityEvent): Boolean {
        // 1. 클래스명으로 확인
        val className = event.className?.toString() ?: ""
        if (className.contains("Call") || className.contains("Order") || className.contains("Accept")) {
            return true
        }
        
        // 2. 텍스트 내용으로 확인
        val eventText = event.text?.joinToString(" ") ?: ""
        if (eventText.contains("수락") || eventText.contains("원") || eventText.contains("km")) {
            return true
        }
        
        // 3. 노드 정보로 확인
        event.source?.let { node ->
            if (findAcceptButton(node) != null) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 화면 분석 후 정확한 클릭
     */
    private fun analyzeAndClick() {
        serviceScope.launch {
            // 방법 1: 접근성 노드에서 버튼 찾기
            val buttonRect = findButtonByAccessibility()
            if (buttonRect != null) {
                Log.d(TAG, "✅ 접근성으로 버튼 발견: $buttonRect")
                clickAtRect(buttonRect)
                return@launch
            }
            
            // 방법 2: 화면 캡처 후 이미지 분석
            val capturedButton = captureAndAnalyze()
            if (capturedButton != null) {
                Log.d(TAG, "✅ 이미지 분석으로 버튼 발견: $capturedButton")
                clickAtRect(capturedButton)
                return@launch
            }
            
            // 방법 3: OCR로 텍스트 찾기
            val ocrButton = findButtonByOCR()
            if (ocrButton != null) {
                Log.d(TAG, "✅ OCR로 버튼 발견: $ocrButton")
                clickAtRect(ocrButton)
                return@launch
            }
            
            // 방법 4: 마지막으로 알려진 위치 사용
            lastKnownButtonRect?.let {
                if (System.currentTimeMillis() - lastDetectionTime < 5000) { // 5초 이내
                    Log.d(TAG, "⚠️ 캐시된 위치 사용: $it")
                    clickAtRect(it)
                }
            }
        }
    }
    
    /**
     * 접근성 노드에서 수락 버튼 찾기
     */
    private fun findButtonByAccessibility(): Rect? {
        rootInActiveWindow?.let { root ->
            // ID로 찾기
            val byId = root.findAccessibilityNodeInfosByViewId("com.kakao.driver:id/btn_accept")
            if (byId.isNotEmpty()) {
                val rect = Rect()
                byId[0].getBoundsInScreen(rect)
                return rect
            }
            
            // 텍스트로 찾기
            val byText = root.findAccessibilityNodeInfosByText("수락")
            if (byText.isNotEmpty()) {
                val rect = Rect()
                byText[0].getBoundsInScreen(rect)
                return rect
            }
            
            // 재귀 탐색
            return findAcceptButton(root)
        }
        return null
    }
    
    /**
     * 재귀적으로 수락 버튼 찾기
     */
    private fun findAcceptButton(node: AccessibilityNodeInfo): Rect? {
        // 클릭 가능하고 텍스트가 있는 노드 확인
        if (node.isClickable) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            
            if (text.contains("수락") || desc.contains("수락") ||
                text.contains("승인") || desc.contains("승인")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                
                // 버튼 크기 검증
                if (rect.width() >= MIN_BUTTON_WIDTH && rect.height() >= MIN_BUTTON_HEIGHT) {
                    return rect
                }
            }
        }
        
        // 자식 노드 탐색
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAcceptButton(child)?.let { return it }
            }
        }
        
        return null
    }
    
    /**
     * 화면 캡처 및 이미지 분석
     */
    private suspend fun captureAndAnalyze(): Rect? {
        // MediaRecorder로 FLAG_SECURE 우회 캡처
        return withContext(Dispatchers.IO) {
            try {
                setupMediaRecorder()
                delay(100) // 캡처 대기
                
                // 프레임 분석
                val bitmap = getLatestFrame()
                bitmap?.let {
                    findYellowButton(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "캡처 실패", e)
                null
            }
        }
    }
    
    /**
     * MediaRecorder 설정
     */
    private fun setupMediaRecorder() {
        if (mediaRecorder != null) return
        
        try {
            val metrics = resources.displayMetrics
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(metrics.widthPixels / 2, metrics.heightPixels / 2)
                setVideoFrameRate(10)
                setVideoEncodingBitRate(1024 * 1024)
                setOutputFile("/dev/null") // 임시
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder 설정 실패", e)
        }
    }
    
    /**
     * 노란 버튼 찾기 (정밀 분석)
     */
    private fun findYellowButton(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        
        // HSV 색상 공간으로 변환하여 정확한 노란색 찾기
        val hsv = FloatArray(3)
        
        // 가능한 버튼 영역들
        val candidates = mutableListOf<Rect>()
        
        // 스캔 (10픽셀 간격)
        for (y in height / 2 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                
                // 카카오 노란색인지 확인
                if (Math.abs(hsv[0] - KAKAO_YELLOW_HUE) < HUE_TOLERANCE &&
                    hsv[1] > 0.7f && // 채도 70% 이상
                    hsv[2] > 0.8f) { // 명도 80% 이상
                    
                    // 주변 영역 확장하여 버튼 크기 계산
                    val buttonRect = expandToButtonBounds(bitmap, x, y)
                    if (buttonRect != null && isValidButton(buttonRect)) {
                        candidates.add(buttonRect)
                    }
                }
            }
        }
        
        // 가장 큰 버튼 선택 (일반적으로 수락 버튼이 가장 큼)
        return candidates.maxByOrNull { it.width() * it.height() }
    }
    
    /**
     * 버튼 경계 확장
     */
    private fun expandToButtonBounds(bitmap: Bitmap, startX: Int, startY: Int): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val hsv = FloatArray(3)
        
        var left = startX
        var right = startX
        var top = startY
        var bottom = startY
        
        // 좌우로 확장
        while (left > 0) {
            Color.colorToHSV(bitmap.getPixel(left - 1, startY), hsv)
            if (Math.abs(hsv[0] - KAKAO_YELLOW_HUE) > HUE_TOLERANCE) break
            left--
        }
        while (right < width - 1) {
            Color.colorToHSV(bitmap.getPixel(right + 1, startY), hsv)
            if (Math.abs(hsv[0] - KAKAO_YELLOW_HUE) > HUE_TOLERANCE) break
            right++
        }
        
        // 상하로 확장
        while (top > 0) {
            Color.colorToHSV(bitmap.getPixel(startX, top - 1), hsv)
            if (Math.abs(hsv[0] - KAKAO_YELLOW_HUE) > HUE_TOLERANCE) break
            top--
        }
        while (bottom < height - 1) {
            Color.colorToHSV(bitmap.getPixel(startX, bottom + 1), hsv)
            if (Math.abs(hsv[0] - KAKAO_YELLOW_HUE) > HUE_TOLERANCE) break
            bottom++
        }
        
        // 실제 화면 좌표로 변환 (2배 스케일)
        return Rect(left * 2, top * 2, right * 2, bottom * 2)
    }
    
    /**
     * 유효한 버튼인지 확인
     */
    private fun isValidButton(rect: Rect): Boolean {
        return rect.width() >= MIN_BUTTON_WIDTH && 
               rect.height() >= MIN_BUTTON_HEIGHT &&
               rect.width() < 800 && // 너무 크면 배경일 가능성
               rect.height() < 300
    }
    
    /**
     * OCR로 버튼 찾기
     */
    private suspend fun findButtonByOCR(): Rect? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = getLatestFrame() ?: return@withContext null
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(
                    KoreanTextRecognizerOptions.Builder().build()
                )
                
                val result = suspendCoroutine<Rect?> { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            for (block in visionText.textBlocks) {
                                val text = block.text
                                if (text.contains("수락") || text.contains("승인") || 
                                    text.contains("받기") || text.contains("시작")) {
                                    val boundingBox = block.boundingBox
                                    if (boundingBox != null) {
                                        continuation.resume(boundingBox)
                                        return@addOnSuccessListener
                                    }
                                }
                            }
                            continuation.resume(null)
                        }
                        .addOnFailureListener {
                            continuation.resume(null)
                        }
                }
                
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 계산된 위치에 정확히 클릭
     */
    private fun clickAtRect(rect: Rect) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        
        Log.d(TAG, "🎯 정확한 클릭: ($centerX, $centerY) - 버튼 크기: ${rect.width()}x${rect.height()}")
        
        // 위치 캐싱
        lastKnownButtonRect = rect
        lastDetectionTime = System.currentTimeMillis()
        
        // 여러 방법으로 클릭 시도
        
        // 방법 1: 제스처
        performGesture(centerX, centerY)
        
        // 방법 2: input tap
        try {
            Runtime.getRuntime().exec("input tap $centerX $centerY")
        } catch (e: Exception) {
            Log.e(TAG, "input tap 실패", e)
        }
        
        // 방법 3: 접근성 클릭
        rootInActiveWindow?.let { root ->
            clickNodeAtPosition(root, centerX, centerY)
        }
    }
    
    /**
     * 제스처로 클릭
     */
    private fun performGesture(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ 제스처 클릭 성공")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "제스처 클릭 취소됨")
            }
        }, null)
    }
    
    /**
     * 특정 좌표의 노드 클릭
     */
    private fun clickNodeAtPosition(node: AccessibilityNodeInfo, x: Int, y: Int): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        if (rect.contains(x, y)) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (clickNodeAtPosition(child, x, y)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 최신 프레임 가져오기 (stub)
     */
    private fun getLatestFrame(): Bitmap? {
        // 실제 구현 필요
        return null
    }
    
    override fun onInterrupt() {
        // 서비스 중단
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        serviceScope.cancel()
    }
}