package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.DataOutputStream

/**
 * 윈도우 레벨에서 작동하는 우회 서비스
 * FLAG_SECURE는 화면 캡처만 막고 터치는 막지 않음!
 */
class WindowLevelBypassService : Service() {
    
    companion object {
        private const val TAG = "WindowBypass"
        
        // 카카오 수락 버튼 좌표 (학습된 위치)
        private val ACCEPT_POSITIONS = listOf(
            Pair(540, 1800),  // 중앙 하단
            Pair(540, 1600),  // 중하단
            Pair(720, 1800),  // 우측 하단
            Pair(360, 1800),  // 좌측 하단
            Pair(540, 2000),  // 최하단
            Pair(540, 1400)   // 중앙
        )
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 윈도우 레벨 우회 시작")
        
        if (!isRunning) {
            isRunning = true
            startBypassMethods()
        }
        
        return START_STICKY
    }
    
    private fun startBypassMethods() {
        // 여러 방법 동시 실행
        serviceScope.launch {
            val methods = listOf(
                async { method1_AdbInput() },
                async { method2_Sendevent() },
                async { method3_Getevent() },
                async { method4_MonkeyRunner() },
                async { method5_UiAutomator() }
            )
            
            // 모든 방법 시도
            methods.awaitAll()
        }
    }
    
    /**
     * 방법 1: ADB input 명령 (윈도우 레벨에서 작동!)
     * FLAG_SECURE 영향 없음
     */
    private suspend fun method1_AdbInput() {
        while (isRunning) {
            try {
                ACCEPT_POSITIONS.forEach { (x, y) ->
                    // input tap 명령 - FLAG_SECURE 무시하고 작동
                    val process = Runtime.getRuntime().exec("input tap $x $y")
                    process.waitFor()
                    
                    Log.d(TAG, "✅ ADB tap 실행: ($x, $y)")
                    delay(100)
                    
                    // swipe로 더블탭 효과
                    Runtime.getRuntime().exec("input swipe $x $y $x $y 50")
                    delay(100)
                }
                
                // 키 이벤트도 시도
                Runtime.getRuntime().exec("input keyevent KEYCODE_ENTER")
                
            } catch (e: Exception) {
                Log.e(TAG, "ADB input 실패", e)
            }
            
            delay(1000) // 1초마다 반복
        }
    }
    
    /**
     * 방법 2: sendevent 직접 사용 (커널 레벨)
     * 가장 낮은 레벨 - 모든 보안 우회
     */
    private suspend fun method2_Sendevent() {
        try {
            // 터치 디바이스 찾기
            val devices = findTouchDevices()
            
            devices.forEach { device ->
                ACCEPT_POSITIONS.forEach { (x, y) ->
                    sendTouchEvent(device, x, y)
                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sendevent 실패", e)
        }
    }
    
    /**
     * 터치 디바이스 찾기
     */
    private fun findTouchDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec("getevent -il")
            val reader = process.inputStream.bufferedReader()
            var currentDevice = ""
            
            reader.forEachLine { line ->
                when {
                    line.contains("/dev/input/event") -> {
                        currentDevice = line.substringAfter("add device").trim()
                    }
                    line.contains("ABS_MT_POSITION") && currentDevice.isNotEmpty() -> {
                        devices.add(currentDevice)
                        Log.d(TAG, "터치 디바이스 발견: $currentDevice")
                    }
                }
            }
        } catch (e: Exception) {
            // 기본값 사용
            devices.addAll(listOf(
                "/dev/input/event0",
                "/dev/input/event1",
                "/dev/input/event2",
                "/dev/input/event3",
                "/dev/input/event4"
            ))
        }
        return devices
    }
    
    /**
     * 터치 이벤트 전송
     */
    private fun sendTouchEvent(device: String, x: Int, y: Int) {
        try {
            val commands = listOf(
                // Touch down
                "sendevent $device 3 57 1",        // ABS_MT_TRACKING_ID
                "sendevent $device 3 53 $x",       // ABS_MT_POSITION_X
                "sendevent $device 3 54 $y",       // ABS_MT_POSITION_Y
                "sendevent $device 3 48 5",        // ABS_MT_TOUCH_MAJOR
                "sendevent $device 3 58 50",       // ABS_MT_PRESSURE
                "sendevent $device 0 0 0",         // SYN_REPORT
                
                // Hold
                "sleep 0.05",
                
                // Touch up
                "sendevent $device 3 57 -1",       // Release
                "sendevent $device 0 0 0"          // SYN_REPORT
            )
            
            // 실행
            val shell = Runtime.getRuntime().exec("sh")
            val os = DataOutputStream(shell.outputStream)
            
            commands.forEach { cmd ->
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            
            Log.d(TAG, "✅ 터치 이벤트 전송: $device ($x, $y)")
            
        } catch (e: Exception) {
            Log.w(TAG, "터치 이벤트 실패: $device", e)
        }
    }
    
    /**
     * 방법 3: getevent 모니터링 + 재전송
     * 사용자 터치 패턴 학습 후 재현
     */
    private suspend fun method3_Getevent() {
        try {
            // 사용자 터치 기록
            val process = Runtime.getRuntime().exec("getevent -lt")
            val reader = process.inputStream.bufferedReader()
            
            serviceScope.launch {
                reader.forEachLine { line ->
                    if (line.contains("ABS_MT_POSITION")) {
                        // 터치 위치 학습
                        Log.d(TAG, "터치 감지: $line")
                        // 같은 위치에 자동 클릭
                        replayTouch(line)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Getevent 실패", e)
        }
    }
    
    /**
     * 터치 재현
     */
    private fun replayTouch(eventLine: String) {
        try {
            // 좌표 추출 후 동일 위치 클릭
            val coords = extractCoordinates(eventLine)
            coords?.let { (x, y) ->
                Runtime.getRuntime().exec("input tap $x $y")
                Log.d(TAG, "✅ 터치 재현: ($x, $y)")
            }
        } catch (e: Exception) {
            // 무시
        }
    }
    
    /**
     * 방법 4: MonkeyRunner 스타일
     * 랜덤 + 패턴 클릭
     */
    private suspend fun method4_MonkeyRunner() {
        while (isRunning) {
            try {
                // Monkey 명령으로 앱 내 랜덤 클릭
                val commands = listOf(
                    "monkey -p com.kakao.driver --pct-touch 100 1",
                    "monkey -p com.kakao.driver --pct-tap 100 1",
                    "monkey -p com.kakao.driver -c android.intent.category.LAUNCHER 1"
                )
                
                commands.forEach { cmd ->
                    Runtime.getRuntime().exec(cmd)
                    delay(500)
                }
                
                // 특정 좌표도 시도
                ACCEPT_POSITIONS.forEach { (x, y) ->
                    Runtime.getRuntime().exec(
                        "monkey --pct-touch 100 --pct-motion 0 " +
                        "--throttle 50 -s 1234 --ignore-crashes " +
                        "--kill-process-after-error -v 1"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Monkey 실패", e)
            }
            
            delay(2000)
        }
    }
    
    /**
     * 방법 5: UI Automator (시스템 레벨)
     * 접근성 서비스보다 낮은 레벨
     */
    private suspend fun method5_UiAutomator() {
        try {
            // uiautomator dump로 화면 구조 파악
            Runtime.getRuntime().exec("uiautomator dump /sdcard/window_dump.xml")
            delay(100)
            
            // XML 파싱해서 버튼 위치 찾기
            val dumpFile = java.io.File("/sdcard/window_dump.xml")
            if (dumpFile.exists()) {
                val content = dumpFile.readText()
                
                // 좌표 추출
                val pattern = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex()
                pattern.findAll(content).forEach { match ->
                    val x1 = match.groupValues[1].toInt()
                    val y1 = match.groupValues[2].toInt()
                    val x2 = match.groupValues[3].toInt()
                    val y2 = match.groupValues[4].toInt()
                    
                    val centerX = (x1 + x2) / 2
                    val centerY = (y1 + y2) / 2
                    
                    // 하단 영역이면 클릭
                    if (centerY > 1400) {
                        Runtime.getRuntime().exec("input tap $centerX $centerY")
                        Log.d(TAG, "✅ UIAutomator 클릭: ($centerX, $centerY)")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "UIAutomator 실패", e)
        }
    }
    
    /**
     * 좌표 추출 헬퍼
     */
    private fun extractCoordinates(line: String): Pair<Int, Int>? {
        return try {
            val parts = line.split(" ")
            val x = parts.find { it.contains("003e") }?.substringAfter("003e")?.toInt(16) ?: 0
            val y = parts.find { it.contains("003f") }?.substringAfter("003f")?.toInt(16) ?: 0
            
            if (x > 0 && y > 0) Pair(x, y) else null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}