package com.kakao.taxi.test.module

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClickEventHandler(private val context: Context) {
    companion object {
        private const val TAG = "ClickEventHandler"
        const val ACTION_PERFORM_CLICK = "com.kakao.taxi.test.PERFORM_CLICK"
    }

    suspend fun performClick(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Attempting to click at ($x, $y)")
        
        // Send broadcast to accessibility service
        val intent = Intent(ACTION_PERFORM_CLICK).apply {
            putExtra("x", x)
            putExtra("y", y)
        }
        context.sendBroadcast(intent)
        
        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled()) {
            Log.e(TAG, "Accessibility service is not enabled")
            return@withContext false
        }
        
        Log.d(TAG, "Click request sent to accessibility service")
        return@withContext true
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${context.packageName}/${context.packageName}.service.KakaoTaxiAccessibilityService"
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(service) == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = 300
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Attempting to swipe from ($startX, $startY) to ($endX, $endY)")
        
        // Send swipe request to accessibility service
        val intent = Intent("${context.packageName}.PERFORM_SWIPE").apply {
            putExtra("startX", startX)
            putExtra("startY", startY)
            putExtra("endX", endX)
            putExtra("endY", endY)
            putExtra("duration", duration)
        }
        context.sendBroadcast(intent)
        
        return@withContext isAccessibilityServiceEnabled()
    }
}