package com.kakao.taxi.test.module

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.widget.Toast
import com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
import com.kakao.taxi.test.service.ScreenCaptureService

/**
 * 빠른 진단 도구
 * 문제가 어디서 발생하는지 즉시 파악
 */
class QuickDiagnostic(private val context: Context) {
    
    fun runFullDiagnostic(): DiagnosticReport {
        val report = DiagnosticReport()
        
        // 1. 접근성 서비스 체크
        report.accessibilityEnabled = isAccessibilityServiceEnabled()
        report.accessibilityConnected = KakaoTaxiAccessibilityService.getInstance() != null
        
        // 2. 화면 캡처 권한 체크
        report.screenCapturePermission = Settings.canDrawOverlays(context)
        report.screenCaptureActive = ScreenCaptureService.isCapturing
        report.lastCapturedBitmap = ScreenCaptureService.capturedBitmap != null
        
        // 3. 카카오 앱 상태
        val accessibilityStatus = KakaoTaxiAccessibilityService.getStatus()
        report.kakaoAppDetected = accessibilityStatus.isKakaoAccessible
        report.lastKakaoDetectionTime = System.currentTimeMillis() - accessibilityStatus.lastKakaoDetection
        
        // 4. 메모리/성능
        report.availableMemoryMB = getAvailableMemory()
        
        return report
    }
    
    fun showQuickFix(activity: Activity) {
        val report = runFullDiagnostic()
        
        // 가장 심각한 문제부터 해결
        when {
            !report.accessibilityEnabled -> {
                Toast.makeText(context, "❌ 접근성 서비스가 꺼져있습니다!", Toast.LENGTH_LONG).show()
                // 접근성 설정으로 이동
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                activity.startActivity(intent)
            }
            
            !report.screenCapturePermission -> {
                Toast.makeText(context, "❌ 화면 표시 권한이 없습니다!", Toast.LENGTH_LONG).show()
                // 권한 설정으로 이동
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                activity.startActivity(intent)
            }
            
            !report.screenCaptureActive -> {
                Toast.makeText(context, "❌ 화면 캡처가 시작되지 않았습니다!", Toast.LENGTH_LONG).show()
                // MediaProjection 재요청
                requestScreenCapture(activity)
            }
            
            !report.lastCapturedBitmap -> {
                Toast.makeText(context, "⚠️ 캡처는 되는데 비트맵이 null입니다!", Toast.LENGTH_LONG).show()
                // 서비스 재시작
                restartServices(activity)
            }
            
            else -> {
                Toast.makeText(context, "✅ 시스템 정상. 카카오 앱을 실행하세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(context.packageName) == true
    }
    
    private fun getAvailableMemory(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        return (maxMemory - usedMemory) / 1048576L // MB
    }
    
    private fun requestScreenCapture(activity: Activity) {
        val mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            1000
        )
    }
    
    private fun restartServices(activity: Activity) {
        // 모든 서비스 재시작
        val services = listOf(
            ScreenCaptureService::class.java,
            com.kakao.taxi.test.service.AutoDetectionService::class.java
        )
        
        services.forEach { service ->
            activity.stopService(Intent(activity, service))
            activity.startService(Intent(activity, service))
        }
    }
    
    data class DiagnosticReport(
        var accessibilityEnabled: Boolean = false,
        var accessibilityConnected: Boolean = false,
        var screenCapturePermission: Boolean = false,
        var screenCaptureActive: Boolean = false,
        var lastCapturedBitmap: Boolean = false,
        var kakaoAppDetected: Boolean = false,
        var lastKakaoDetectionTime: Long = 0,
        var availableMemoryMB: Long = 0
    ) {
        fun getErrorSummary(): String {
            val errors = mutableListOf<String>()
            
            if (!accessibilityEnabled) errors.add("접근성 OFF")
            if (!accessibilityConnected) errors.add("접근성 미연결")
            if (!screenCapturePermission) errors.add("화면표시 권한없음")
            if (!screenCaptureActive) errors.add("캡처 비활성")
            if (!lastCapturedBitmap) errors.add("비트맵 null")
            if (!kakaoAppDetected) errors.add("카카오앱 미감지")
            
            return if (errors.isEmpty()) {
                "✅ 모든 시스템 정상"
            } else {
                "❌ 문제: ${errors.joinToString(", ")}"
            }
        }
    }
}