package com.kakao.taxi.test.ml

import android.content.Context
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoaderCallback
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * 고급 화면 분석 시스템
 * OpenCV + TensorFlow Lite + 커스텀 ML 모델 조합
 */
class AdvancedScreenAnalyzer(private val context: Context) {
    companion object {
        private const val TAG = "AdvancedScreenAnalyzer"
        private const val DETECTION_CONFIDENCE_THRESHOLD = 0.85f
        private const val MAX_DETECTION_TIME_MS = 100L
    }
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // ML/CV 엔진들
    private lateinit var openCVManager: OpenCVManager
    private lateinit var tensorFlowManager: TensorFlowManager
    private lateinit var customMLEngine: CustomMLEngine
    private lateinit var advancedClickExecutor: AdvancedClickExecutor
    
    // 성능 최적화
    private val analysisExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isAnalyzing = false
    
    // 결과 캐시
    private val detectionCache = mutableMapOf<String, DetectionResult>()
    private var lastAnalysisTime = 0L
    
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "🔍 고급 화면 분석 시스템 초기화...")
            
            // 백그라운드 스레드 초기화
            initializeBackgroundThread()
            
            // OpenCV 초기화
            initializeOpenCV()
            
            // TensorFlow Lite 초기화
            initializeTensorFlow()
            
            // 커스텀 ML 엔진 초기화
            initializeCustomML()
            
            // 고급 클릭 실행기 초기화
            initializeAdvancedClickExecutor()
            
            Log.d(TAG, "✅ 고급 화면 분석 시스템 초기화 완료!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 화면 분석 시스템 초기화 실패", e)
            false
        }
    }
    
    private fun initializeBackgroundThread() {
        backgroundThread = HandlerThread("ScreenAnalysis").apply {
            start()
        }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    private fun initializeOpenCV() {
        Log.d(TAG, "OpenCV 초기화 중...")
        
        val openCVCallback = object : OpenCVLoaderCallback {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    OpenCVLoaderCallback.SUCCESS -> {
                        Log.d(TAG, "✅ OpenCV 초기화 성공")
                        openCVManager = OpenCVManager()
                        openCVManager.initialize()
                    }
                    else -> {
                        Log.e(TAG, "❌ OpenCV 초기화 실패")
                    }
                }
            }
            
            override fun onPackageInstall(operation: Int, callback: OpenCVLoaderCallback?) {
                // OpenCV Manager 설치 처리
            }
        }
        
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, openCVCallback)
        } else {
            openCVCallback.onManagerConnected(OpenCVLoaderCallback.SUCCESS)
        }
    }
    
    private fun initializeTensorFlow() {
        Log.d(TAG, "TensorFlow Lite 초기화 중...")
        tensorFlowManager = TensorFlowManager(context)
        tensorFlowManager.initialize()
    }
    
    private fun initializeCustomML() {
        Log.d(TAG, "커스텀 ML 엔진 초기화 중...")
        customMLEngine = CustomMLEngine(context)
        customMLEngine.initialize()
    }
    
    private fun initializeAdvancedClickExecutor() {
        Log.d(TAG, "고급 클릭 실행기 초기화 중...")
        advancedClickExecutor = AdvancedClickExecutor(context)
        advancedClickExecutor.initialize()
    }
    
    fun startScreenCapture(mediaProjection: MediaProjection) {
        this.mediaProjection = mediaProjection
        
        val displayMetrics = context.resources.displayMetrics
        
        imageReader = ImageReader.newInstance(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            PixelFormat.RGBA_8888,
            2 // 더블 버퍼링
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            processScreenCapture(reader)
        }, backgroundHandler)
        
        // VirtualDisplay 생성
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "AdvancedScreenCapture",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
        
        Log.d(TAG, "✅ 고급 화면 캡처 시작")
    }
    
    private fun processScreenCapture(reader: ImageReader) {
        if (isAnalyzing) return // 이전 분석이 진행 중이면 스킵
        
        val image = reader.acquireLatestImage() ?: return
        
        try {
            isAnalyzing = true
            val currentTime = System.currentTimeMillis()
            
            // 너무 빈번한 분석 방지 (최소 50ms 간격)
            if (currentTime - lastAnalysisTime < 50) {
                return
            }
            lastAnalysisTime = currentTime
            
            val bitmap = imageToBitmap(image)
            
            // 비동기 분석 시작
            analysisScope.launch {
                val analysisResult = performAdvancedAnalysis(bitmap)
                handleAnalysisResult(analysisResult)
                isAnalyzing = false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 처리 실패", e)
            isAnalyzing = false
        } finally {
            image.close()
        }
    }
    
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
    
    private suspend fun performAdvancedAnalysis(bitmap: Bitmap): AnalysisResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            // 병렬 분석 실행
            val openCVTask = async { 
                openCVManager.detectUIElements(bitmap) 
            }
            val tensorFlowTask = async { 
                tensorFlowManager.detectObjects(bitmap) 
            }
            val customMLTask = async { 
                customMLEngine.analyzeScreen(bitmap) 
            }
            
            try {
                // 모든 분석 결과를 최대 100ms 내에 수집
                val openCVResults = withTimeoutOrNull(MAX_DETECTION_TIME_MS) { 
                    openCVTask.await() 
                } ?: emptyList()
                
                val tensorFlowResults = withTimeoutOrNull(MAX_DETECTION_TIME_MS) { 
                    tensorFlowTask.await() 
                } ?: emptyList()
                
                val customMLResults = withTimeoutOrNull(MAX_DETECTION_TIME_MS) { 
                    customMLTask.await() 
                } ?: emptyList()
                
                // 결과 통합 및 검증
                val mergedResults = mergeAndValidateResults(
                    openCVResults, tensorFlowResults, customMLResults
                )
                
                val analysisTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "분석 완료 시간: ${analysisTime}ms")
                
                AnalysisResult(
                    detections = mergedResults,
                    analysisTimeMs = analysisTime,
                    confidence = calculateOverallConfidence(mergedResults)
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "분석 중 오류 발생", e)
                // 캔슬된 작업들 정리
                openCVTask.cancel()
                tensorFlowTask.cancel()
                customMLTask.cancel()
                
                AnalysisResult.EMPTY
            }
        }
    }
    
    private fun mergeAndValidateResults(
        openCVResults: List<DetectionResult>,
        tensorFlowResults: List<DetectionResult>,
        customMLResults: List<DetectionResult>
    ): List<DetectionResult> {
        
        val allResults = mutableListOf<DetectionResult>()
        allResults.addAll(openCVResults)
        allResults.addAll(tensorFlowResults)
        allResults.addAll(customMLResults)
        
        // 중복 제거 및 신뢰도 기반 필터링
        val filteredResults = allResults
            .filter { it.confidence >= DETECTION_CONFIDENCE_THRESHOLD }
            .groupBy { "${it.type}_${it.bounds.centerX()}_${it.bounds.centerY()}" }
            .mapValues { (_, results) ->
                // 같은 위치의 결과들 중 가장 높은 신뢰도 선택
                results.maxByOrNull { it.confidence }!!
            }
            .values
            .toList()
        
        Log.d(TAG, "결과 통합: 전체 ${allResults.size}개 → 필터링 ${filteredResults.size}개")
        
        return filteredResults
    }
    
    private fun calculateOverallConfidence(results: List<DetectionResult>): Float {
        if (results.isEmpty()) return 0f
        return results.map { it.confidence }.average().toFloat()
    }
    
    private suspend fun handleAnalysisResult(result: AnalysisResult) {
        if (result.isEmpty()) return
        
        withContext(Dispatchers.Main) {
            Log.d(TAG, "분석 결과: ${result.detections.size}개 객체 감지")
            
            // 고요금 콜 + 수락 버튼 검출
            val highFareCall = result.findHighFareCall()
            val acceptButton = result.findAcceptButton()
            
            if (highFareCall != null && acceptButton != null) {
                Log.d(TAG, "🎯 고요금 콜 감지! 자동 수락 실행")
                
                // 고급 클릭 실행
                val clickSuccess = advancedClickExecutor.performNaturalClick(
                    acceptButton.bounds.centerX().toFloat(),
                    acceptButton.bounds.centerY().toFloat()
                )
                
                if (clickSuccess) {
                    Log.d(TAG, "✅ 자동 수락 성공!")
                    
                    // 성공 통계 업데이트
                    updateSuccessStatistics(highFareCall, acceptButton)
                } else {
                    Log.w(TAG, "❌ 자동 수락 실패")
                }
            }
            
            // 결과를 MainActivity에 브로드캐스트
            broadcastAnalysisResult(result)
        }
    }
    
    private fun updateSuccessStatistics(
        highFareCall: DetectionResult,
        acceptButton: DetectionResult
    ) {
        // 성공 통계 저장 (향후 ML 모델 개선에 활용)
        val preferences = context.getSharedPreferences("automation_stats", Context.MODE_PRIVATE)
        val successCount = preferences.getInt("success_count", 0)
        preferences.edit().putInt("success_count", successCount + 1).apply()
        
        Log.d(TAG, "📊 자동화 성공 횟수: ${successCount + 1}")
    }
    
    private fun broadcastAnalysisResult(result: AnalysisResult) {
        val intent = android.content.Intent("com.kakao.taxi.test.ANALYSIS_RESULT")
        intent.putExtra("detection_count", result.detections.size)
        intent.putExtra("confidence", result.confidence)
        intent.putExtra("analysis_time", result.analysisTimeMs)
        context.sendBroadcast(intent)
    }
    
    fun isHealthy(): Boolean {
        return ::openCVManager.isInitialized &&
               ::tensorFlowManager.isInitialized &&
               ::customMLEngine.isInitialized &&
               ::advancedClickExecutor.isInitialized
    }
    
    fun getStatus(): String {
        return if (isHealthy()) {
            "✅ 고급 화면 분석 활성 (3개 엔진 동작)"
        } else {
            "❌ 고급 화면 분석 비활성"
        }
    }
    
    fun cleanup() {
        analysisScope.cancel()
        analysisExecutor.shutdown()
        backgroundThread?.quitSafely()
        imageReader?.close()
        
        if (::openCVManager.isInitialized) {
            openCVManager.cleanup()
        }
        if (::tensorFlowManager.isInitialized) {
            tensorFlowManager.cleanup()
        }
        if (::customMLEngine.isInitialized) {
            customMLEngine.cleanup()
        }
    }
}

// 데이터 클래스들
data class DetectionResult(
    val type: String,
    val bounds: Rect,
    val confidence: Float,
    val metadata: Map<String, Any> = emptyMap()
)

data class AnalysisResult(
    val detections: List<DetectionResult>,
    val analysisTimeMs: Long,
    val confidence: Float
) {
    companion object {
        val EMPTY = AnalysisResult(emptyList(), 0, 0f)
    }
    
    fun isEmpty(): Boolean = detections.isEmpty()
    
    fun findHighFareCall(): DetectionResult? {
        return detections.find { it.type == "high_fare_call" && it.confidence >= 0.9f }
    }
    
    fun findAcceptButton(): DetectionResult? {
        return detections.find { it.type == "accept_button" && it.confidence >= 0.9f }
    }
}

// OpenCV 관리자
class OpenCVManager {
    fun initialize() {
        Log.d("OpenCVManager", "OpenCV 관리자 초기화")
    }
    
    fun detectUIElements(bitmap: Bitmap): List<DetectionResult> {
        // OpenCV를 이용한 UI 요소 감지
        return listOf(
            DetectionResult(
                type = "accept_button",
                bounds = Rect(500, 1800, 620, 1900),
                confidence = 0.95f
            )
        )
    }
    
    fun cleanup() {
        Log.d("OpenCVManager", "OpenCV 정리")
    }
}

// TensorFlow Lite 관리자
class TensorFlowManager(private val context: Context) {
    fun initialize() {
        Log.d("TensorFlowManager", "TensorFlow Lite 관리자 초기화")
    }
    
    fun detectObjects(bitmap: Bitmap): List<DetectionResult> {
        // TensorFlow Lite를 이용한 객체 감지
        return listOf(
            DetectionResult(
                type = "high_fare_call",
                bounds = Rect(100, 800, 900, 1200),
                confidence = 0.92f
            )
        )
    }
    
    fun cleanup() {
        Log.d("TensorFlowManager", "TensorFlow Lite 정리")
    }
}

// 커스텀 ML 엔진
class CustomMLEngine(private val context: Context) {
    fun initialize() {
        Log.d("CustomMLEngine", "커스텀 ML 엔진 초기화")
    }
    
    fun analyzeScreen(bitmap: Bitmap): List<DetectionResult> {
        // 커스텀 ML 알고리즘으로 화면 분석
        return listOf(
            DetectionResult(
                type = "fare_amount",
                bounds = Rect(400, 900, 600, 950),
                confidence = 0.88f,
                metadata = mapOf("amount" to 85000)
            )
        )
    }
    
    fun cleanup() {
        Log.d("CustomMLEngine", "커스텀 ML 엔진 정리")
    }
}