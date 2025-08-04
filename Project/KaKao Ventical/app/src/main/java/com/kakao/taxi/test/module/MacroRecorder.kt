package com.kakao.taxi.test.module

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 사용자의 클릭 동작을 녹화하고 재생
 * 일반 사용자 배포용 대안
 */
class MacroRecorder(private val context: Context) {
    companion object {
        private const val TAG = "MacroRecorder"
        private const val MACRO_FILE = "kakao_taxi_macro.json"
    }
    
    data class ClickAction(
        val x: Int,
        val y: Int,
        val screenHash: String, // 화면 식별용
        val delay: Long = 100L,
        val description: String = ""
    )
    
    data class Macro(
        val name: String,
        val actions: List<ClickAction>,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    private val gson = Gson()
    private val recordedActions = mutableListOf<ClickAction>()
    private var isRecording = false
    
    /**
     * 녹화 시작
     */
    fun startRecording() {
        isRecording = true
        recordedActions.clear()
        Log.d(TAG, "매크로 녹화 시작")
    }
    
    /**
     * 클릭 동작 기록
     */
    fun recordClick(x: Int, y: Int, screenshot: Bitmap?, description: String = "") {
        if (!isRecording) return
        
        val screenHash = screenshot?.let { 
            // 화면의 특징을 해시로 저장 (간단한 체크섬)
            calculateScreenHash(it)
        } ?: ""
        
        val action = ClickAction(
            x = x,
            y = y,
            screenHash = screenHash,
            delay = if (recordedActions.isEmpty()) 0 else 100L,
            description = description
        )
        
        recordedActions.add(action)
        Log.d(TAG, "클릭 기록: ($x, $y) - $description")
    }
    
    /**
     * 녹화 중지 및 저장
     */
    fun stopRecording(macroName: String): Boolean {
        if (!isRecording || recordedActions.isEmpty()) return false
        
        isRecording = false
        
        val macro = Macro(
            name = macroName,
            actions = recordedActions.toList()
        )
        
        return saveMacro(macro)
    }
    
    /**
     * 매크로 저장
     */
    private fun saveMacro(macro: Macro): Boolean {
        return try {
            val file = File(context.filesDir, MACRO_FILE)
            val existingMacros = loadAllMacros().toMutableList()
            
            // 같은 이름의 매크로가 있으면 교체
            existingMacros.removeAll { it.name == macro.name }
            existingMacros.add(macro)
            
            file.writeText(gson.toJson(existingMacros))
            Log.d(TAG, "매크로 저장 완료: ${macro.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "매크로 저장 실패", e)
            false
        }
    }
    
    /**
     * 모든 매크로 로드
     */
    fun loadAllMacros(): List<Macro> {
        return try {
            val file = File(context.filesDir, MACRO_FILE)
            if (!file.exists()) return emptyList()
            
            val type = object : TypeToken<List<Macro>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (e: Exception) {
            Log.e(TAG, "매크로 로드 실패", e)
            emptyList()
        }
    }
    
    /**
     * 특정 매크로 로드
     */
    fun loadMacro(name: String): Macro? {
        return loadAllMacros().find { it.name == name }
    }
    
    /**
     * 화면 해시 계산 (간단한 버전)
     */
    private fun calculateScreenHash(bitmap: Bitmap): String {
        // 화면의 몇 개 픽셀만 샘플링하여 간단한 해시 생성
        val sample = StringBuilder()
        val step = 50
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                sample.append(bitmap.getPixel(x, y))
            }
        }
        
        return sample.toString().hashCode().toString()
    }
    
    /**
     * 사용자 교육용 가이드 매크로 생성
     */
    fun createGuideMacro(): Macro {
        // 카카오 택시 콜 수락 위치 (예시)
        val guideActions = listOf(
            ClickAction(
                x = 540, // 화면 중앙
                y = 1800, // 하단 버튼 위치
                screenHash = "",
                description = "콜 수락 버튼"
            ),
            ClickAction(
                x = 270, // 왼쪽
                y = 1900, // 하단
                screenHash = "",
                delay = 5000L,
                description = "완료콜 삭제"
            )
        )
        
        return Macro(
            name = "카카오택시_기본_매크로",
            actions = guideActions
        )
    }
}