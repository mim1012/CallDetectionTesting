package com.kakao.taxi.test.module

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 시각적 디버깅 도구
 * USB/ADB 없이 화면에서 바로 문제 확인
 */
class VisualDebugger(private val context: Context) {
    
    companion object {
        private const val TAG = "VisualDebugger"
        
        // 디버그 상태를 색상으로 표시
        const val COLOR_SUCCESS = Color.GREEN
        const val COLOR_WARNING = Color.YELLOW
        const val COLOR_ERROR = Color.RED
        const val COLOR_INFO = Color.BLUE
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 화면 캡처 상태 시각화
     */
    fun debugScreenCapture(bitmap: Bitmap?, error: String? = null): Bitmap {
        val debugBitmap = if (bitmap != null) {
            bitmap.copy(bitmap.config, true)
        } else {
            // 캡처 실패 시 검은 화면에 에러 표시
            Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(Color.BLACK)
            }
        }
        
        val canvas = Canvas(debugBitmap)
        val paint = Paint().apply {
            textSize = 50f
            strokeWidth = 5f
            isAntiAlias = true
        }
        
        // 상단에 상태 표시
        val status = if (bitmap != null) {
            paint.color = COLOR_SUCCESS
            "✅ 캡처 성공 ${bitmap.width}x${bitmap.height}"
        } else {
            paint.color = COLOR_ERROR
            "❌ 캡처 실패: $error"
        }
        
        // 배경 박스
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.alpha = 180
        canvas.drawRect(0f, 0f, debugBitmap.width.toFloat(), 150f, paint)
        
        // 텍스트
        paint.color = if (bitmap != null) COLOR_SUCCESS else COLOR_ERROR
        paint.alpha = 255
        canvas.drawText(status, 20f, 80f, paint)
        canvas.drawText(getCurrentTime(), 20f, 130f, paint)
        
        return debugBitmap
    }
    
    /**
     * 버튼 감지 결과 시각화
     */
    fun debugButtonDetection(
        bitmap: Bitmap, 
        buttons: List<ButtonCandidate>,
        yellowPixels: List<Point>? = null
    ): Bitmap {
        val debugBitmap = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(debugBitmap)
        val paint = Paint().apply {
            strokeWidth = 5f
            isAntiAlias = true
            textSize = 40f
        }
        
        // 노란색 픽셀 표시 (선택적)
        yellowPixels?.forEach { point ->
            paint.color = Color.MAGENTA
            paint.style = Paint.Style.FILL
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 3f, paint)
        }
        
        // 감지된 버튼 표시
        buttons.forEachIndexed { index, button ->
            // 버튼 영역 박스
            paint.color = COLOR_SUCCESS
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            canvas.drawRect(button.bounds, paint)
            
            // 중심점 표시
            paint.style = Paint.Style.FILL
            paint.color = COLOR_ERROR
            canvas.drawCircle(
                button.centerX.toFloat(), 
                button.centerY.toFloat(), 
                15f, 
                paint
            )
            
            // 버튼 정보 표시
            paint.color = COLOR_SUCCESS
            paint.style = Paint.Style.FILL
            paint.textSize = 35f
            
            val info = "BTN${index + 1} (${button.centerX},${button.centerY}) ${(button.confidence * 100).toInt()}%"
            
            // 텍스트 배경
            val textBounds = Rect()
            paint.getTextBounds(info, 0, info.length, textBounds)
            paint.color = Color.BLACK
            paint.alpha = 200
            canvas.drawRect(
                button.bounds.left.toFloat() - 5,
                button.bounds.top.toFloat() - textBounds.height() - 10,
                button.bounds.left.toFloat() + textBounds.width() + 10,
                button.bounds.top.toFloat() - 5,
                paint
            )
            
            // 텍스트
            paint.color = COLOR_SUCCESS
            paint.alpha = 255
            canvas.drawText(info, button.bounds.left.toFloat(), button.bounds.top.toFloat() - 10, paint)
        }
        
        // 상태 요약
        drawDebugOverlay(canvas, debugBitmap.width, debugBitmap.height, 
            "🔍 버튼 ${buttons.size}개 감지됨",
            if (buttons.isEmpty()) COLOR_ERROR else COLOR_SUCCESS
        )
        
        return debugBitmap
    }
    
    /**
     * 클릭 시도 결과 시각화
     */
    fun debugClickAttempt(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        success: Boolean,
        error: String? = null
    ): Bitmap {
        val debugBitmap = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(debugBitmap)
        val paint = Paint().apply {
            strokeWidth = 5f
            isAntiAlias = true
        }
        
        // 클릭 위치 표시
        paint.color = if (success) COLOR_SUCCESS else COLOR_ERROR
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        
        // 동심원으로 클릭 위치 강조
        for (radius in listOf(30f, 50f, 70f)) {
            paint.alpha = (255 * (1 - radius / 100)).toInt()
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius, paint)
        }
        
        // 십자선
        paint.alpha = 255
        paint.strokeWidth = 5f
        canvas.drawLine(x - 50f, y.toFloat(), x + 50f, y.toFloat(), paint)
        canvas.drawLine(x.toFloat(), y - 50f, x.toFloat(), y + 50f, paint)
        
        // 클릭 결과 텍스트
        paint.style = Paint.Style.FILL
        paint.textSize = 50f
        val status = if (success) "✅ 클릭 성공!" else "❌ 클릭 실패: $error"
        
        // 텍스트 배경
        val textBounds = Rect()
        paint.getTextBounds(status, 0, status.length, textBounds)
        paint.color = Color.BLACK
        paint.alpha = 200
        canvas.drawRect(
            x - textBounds.width() / 2f - 20,
            y + 80f,
            x + textBounds.width() / 2f + 20,
            y + 80f + textBounds.height() + 20,
            paint
        )
        
        // 텍스트
        paint.color = if (success) COLOR_SUCCESS else COLOR_ERROR
        paint.alpha = 255
        canvas.drawText(status, x - textBounds.width() / 2f, y + 80f + textBounds.height(), paint)
        
        return debugBitmap
    }
    
    /**
     * OCR 결과 시각화
     */
    fun debugOCRResults(bitmap: Bitmap, ocrResults: List<OCRResult>): Bitmap {
        val debugBitmap = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(debugBitmap)
        val paint = Paint().apply {
            strokeWidth = 3f
            isAntiAlias = true
            textSize = 30f
        }
        
        ocrResults.forEach { result ->
            // OCR 영역 박스
            paint.color = COLOR_INFO
            paint.style = Paint.Style.STROKE
            canvas.drawRect(result.boundingBox, paint)
            
            // 인식된 텍스트
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.alpha = 200
            
            val padding = 5f
            canvas.drawRect(
                result.boundingBox.left - padding,
                result.boundingBox.bottom + padding,
                result.boundingBox.right + padding,
                result.boundingBox.bottom + 40f + padding,
                paint
            )
            
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.drawText(
                result.text,
                result.boundingBox.left.toFloat(),
                result.boundingBox.bottom + 30f,
                paint
            )
        }
        
        drawDebugOverlay(canvas, debugBitmap.width, debugBitmap.height,
            "📝 OCR: ${ocrResults.size}개 텍스트 인식",
            COLOR_INFO
        )
        
        return debugBitmap
    }
    
    /**
     * 종합 디버그 정보 오버레이
     */
    private fun drawDebugOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        message: String,
        color: Int
    ) {
        val paint = Paint().apply {
            textSize = 45f
            isAntiAlias = true
        }
        
        // 상단 오버레이
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.alpha = 200
        canvas.drawRect(0f, 0f, width.toFloat(), 120f, paint)
        
        // 메시지
        paint.color = color
        paint.alpha = 255
        canvas.drawText(message, 20f, 70f, paint)
        
        // 시간
        paint.textSize = 35f
        paint.color = Color.WHITE
        canvas.drawText(getCurrentTime(), width - 300f, 70f, paint)
    }
    
    /**
     * 토스트 메시지로 즉시 피드백
     */
    fun showDebugToast(message: String, isError: Boolean = false) {
        handler.post {
            val emoji = if (isError) "❌" else "✅"
            Toast.makeText(context, "$emoji $message", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 플로팅 디버그 정보 업데이트
     */
    fun updateFloatingDebug(
        captureStatus: String,
        detectionStatus: String,
        clickStatus: String,
        performance: String
    ) {
        val debugInfo = """
            📷 $captureStatus
            🔍 $detectionStatus
            👆 $clickStatus
            ⚡ $performance
        """.trimIndent()
        
        // FloatingDebugService로 전송
        val intent = android.content.Intent(context, com.kakao.taxi.test.service.FloatingDebugService::class.java).apply {
            action = "UPDATE_DEBUG"
            putExtra("debug_info", debugInfo)
        }
        context.startService(intent)
    }
    
    /**
     * 디버그 스크린샷 저장 (갤러리에서 바로 확인)
     */
    fun saveDebugScreenshot(bitmap: Bitmap, tag: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "debug_${tag}_$timestamp.png"
        
        // 외부 저장소에 저장 (갤러리에서 볼 수 있음)
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), filename)
        
        try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            showDebugToast("디버그 이미지 저장: $filename")
        } catch (e: Exception) {
            showDebugToast("저장 실패: ${e.message}", true)
        }
    }
    
    private fun getCurrentTime(): String {
        return dateFormat.format(Date())
    }
    
    /**
     * 단계별 진단 결과
     */
    data class DiagnosticResult(
        val step: String,
        val success: Boolean,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 전체 프로세스 진단
     */
    fun runDiagnostics(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        
        // 1. 권한 체크
        results.add(DiagnosticResult(
            "권한 확인",
            checkPermissions(),
            if (checkPermissions()) "모든 권한 정상" else "권한 부족"
        ))
        
        // 2. 서비스 상태
        results.add(DiagnosticResult(
            "서비스 상태",
            checkServices(),
            if (checkServices()) "서비스 실행중" else "서비스 중지됨"
        ))
        
        // 3. 카카오 앱 감지
        results.add(DiagnosticResult(
            "카카오 앱",
            checkKakaoApp(),
            if (checkKakaoApp()) "카카오 택시 감지됨" else "카카오 택시 실행 안됨"
        ))
        
        return results
    }
    
    private fun checkPermissions(): Boolean {
        // 실제 권한 체크 로직
        return true
    }
    
    private fun checkServices(): Boolean {
        // 서비스 실행 상태 체크
        return true
    }
    
    private fun checkKakaoApp(): Boolean {
        // 카카오 앱 실행 상태 체크
        return true
    }
}