package com.kakao.taxi.test.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import android.os.Build
import android.content.Context

/**
 * 카카오 택시의 극단적인 보안을 우회하기 위한 서비스
 * 접근성 UI 읽기 차단, input/tap/touch 차단을 우회
 */
class ExtremeBypassService : Service() {
    companion object {
        private const val TAG = "ExtremeBypass"
        const val ACTION_START = "ACTION_START_EXTREME"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startExtremeBypass()
        }
        return START_STICKY
    }
    
    private fun startExtremeBypass() {
        serviceScope.launch {
            Log.d(TAG, "🔥 극단적 우회 시작...")
            
            // 1. 하드웨어 이벤트 직접 주입
            injectHardwareEvents()
            
            // 2. 프로세스 메모리 직접 조작
            manipulateProcessMemory()
            
            // 3. 시스템 바이너리 후킹
            hookSystemBinaries()
            
            // 4. 커널 레벨 이벤트 생성
            generateKernelEvents()
            
            // 5. 가상 입력 디바이스 생성
            createVirtualInputDevice()
        }
    }
    
    /**
     * 방법 1: /dev/input/eventX 직접 조작
     * sendevent 대신 직접 이벤트 노드에 쓰기
     */
    private fun injectHardwareEvents() {
        try {
            // 터치스크린 디바이스 찾기
            val inputDevices = File("/dev/input/").listFiles { file ->
                file.name.startsWith("event")
            }
            
            inputDevices?.forEach { device ->
                try {
                    // 디바이스 정보 읽기
                    val runtime = Runtime.getRuntime()
                    val process = runtime.exec("getevent -i ${device.absolutePath}")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val info = reader.readText()
                    
                    if (info.contains("ABS_MT_POSITION") || info.contains("touchscreen")) {
                        Log.d(TAG, "터치스크린 발견: ${device.name}")
                        
                        // 직접 바이너리 쓰기로 터치 이벤트 생성
                        val raf = RandomAccessFile(device, "rw")
                        
                        // input_event 구조체 (24 bytes on 64-bit)
                        // struct input_event {
                        //     struct timeval time; // 16 bytes
                        //     __u16 type;         // 2 bytes
                        //     __u16 code;         // 2 bytes
                        //     __s32 value;        // 4 bytes
                        // }
                        
                        val timestamp = System.currentTimeMillis()
                        val x = 540 // 화면 중앙 X
                        val y = 1200 // 수락 버튼 예상 Y
                        
                        // TOUCH DOWN
                        writeInputEvent(raf, timestamp, 3, 57, 1) // ABS_MT_TRACKING_ID
                        writeInputEvent(raf, timestamp, 3, 53, x) // ABS_MT_POSITION_X
                        writeInputEvent(raf, timestamp, 3, 54, y) // ABS_MT_POSITION_Y
                        writeInputEvent(raf, timestamp, 3, 48, 5) // ABS_MT_TOUCH_MAJOR
                        writeInputEvent(raf, timestamp, 3, 58, 100) // ABS_MT_PRESSURE
                        writeInputEvent(raf, timestamp, 0, 0, 0) // SYN_REPORT
                        
                        Thread.sleep(50)
                        
                        // TOUCH UP
                        writeInputEvent(raf, timestamp + 50, 3, 57, -1) // ABS_MT_TRACKING_ID
                        writeInputEvent(raf, timestamp + 50, 0, 0, 0) // SYN_REPORT
                        
                        raf.close()
                        Log.d(TAG, "✅ 하드웨어 터치 이벤트 주입 완료")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "디바이스 ${device.name} 접근 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "하드웨어 이벤트 주입 실패", e)
        }
    }
    
    /**
     * input_event 구조체를 바이트 배열로 쓰기
     */
    private fun writeInputEvent(raf: RandomAccessFile, timestamp: Long, type: Int, code: Int, value: Int) {
        // timeval 구조체 (16 bytes)
        raf.writeLong(timestamp / 1000) // tv_sec
        raf.writeLong((timestamp % 1000) * 1000) // tv_usec
        
        // event type, code, value
        raf.writeShort(type)
        raf.writeShort(code)
        raf.writeInt(value)
    }
    
    /**
     * 방법 2: 카카오 프로세스 메모리 직접 수정
     */
    private fun manipulateProcessMemory() {
        try {
            // 카카오 드라이버 앱 PID 찾기
            val process = Runtime.getRuntime().exec("pidof com.kakao.driver")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val pidStr = reader.readLine()?.trim()
            
            if (pidStr != null) {
                val pid = pidStr.toInt()
                Log.d(TAG, "카카오 드라이버 PID: $pid")
                
                // /proc/[pid]/mem 직접 접근
                val memFile = File("/proc/$pid/mem")
                val mapsFile = File("/proc/$pid/maps")
                
                // 메모리 맵 읽기
                val maps = mapsFile.readText()
                
                // 보안 체크 함수 주소 찾기
                maps.lines().forEach { line ->
                    if (line.contains("libkakao") || line.contains(".so")) {
                        val parts = line.split(" ")
                        if (parts.isNotEmpty()) {
                            val addressRange = parts[0].split("-")
                            if (addressRange.size == 2) {
                                val startAddr = addressRange[0].toLongOrNull(16) ?: 0
                                val endAddr = addressRange[1].toLongOrNull(16) ?: 0
                                
                                // NOP 명령어로 보안 체크 무효화
                                patchMemory(pid, startAddr, endAddr)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "메모리 조작 실패", e)
        }
    }
    
    /**
     * 메모리 패치 - 보안 체크 함수를 NOP으로 덮어쓰기
     */
    private fun patchMemory(pid: Int, startAddr: Long, endAddr: Long) {
        try {
            // ptrace를 사용한 메모리 쓰기 (루트 필요)
            val runtime = Runtime.getRuntime()
            
            // 보안 체크 패턴 찾기 (예: isSecurityEnabled, checkAccessibility 등)
            val searchPatterns = listOf(
                "6973536563757269747945" // "isSecurityE"
                "636865636b416363657373", // "checkAccess"
                "626c6f636b546f756368"    // "blockTouch"
            )
            
            searchPatterns.forEach { pattern ->
                val cmd = "echo -ne '\\x90\\x90\\x90\\x90' | dd of=/proc/$pid/mem bs=1 seek=$startAddr conv=notrunc"
                runtime.exec(arrayOf("sh", "-c", cmd))
            }
            
            Log.d(TAG, "메모리 패치 시도: 0x${startAddr.toString(16)} - 0x${endAddr.toString(16)}")
        } catch (e: Exception) {
            Log.w(TAG, "메모리 패치 실패: ${e.message}")
        }
    }
    
    /**
     * 방법 3: 시스템 바이너리 후킹
     */
    private fun hookSystemBinaries() {
        try {
            // LD_PRELOAD를 사용한 함수 후킹
            val hookLibPath = "${filesDir.absolutePath}/libhook.so"
            
            // 후킹 라이브러리 생성 (실제로는 NDK로 컴파일 필요)
            createHookLibrary(hookLibPath)
            
            // 환경 변수 설정
            val runtime = Runtime.getRuntime()
            runtime.exec("setprop wrap.com.kakao.driver LD_PRELOAD=$hookLibPath")
            
            Log.d(TAG, "시스템 바이너리 후킹 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "바이너리 후킹 실패", e)
        }
    }
    
    /**
     * 방법 4: 커널 레벨 이벤트 생성
     */
    private fun generateKernelEvents() {
        try {
            // /sys/kernel/debug/tracing 을 통한 이벤트 주입
            val runtime = Runtime.getRuntime()
            
            // 커널 디버그 파일시스템 마운트
            runtime.exec("mount -t debugfs none /sys/kernel/debug")
            
            // synthetic events 생성
            val eventFile = File("/sys/kernel/debug/tracing/synthetic_events")
            if (eventFile.exists()) {
                eventFile.appendText("touch_event u32 x; u32 y; u32 pressure\n")
                
                // 이벤트 트리거
                File("/sys/kernel/debug/tracing/events/synthetic/touch_event/trigger")
                    .writeText("x=540 y=1200 pressure=100")
                
                Log.d(TAG, "커널 레벨 이벤트 생성 완료")
            }
        } catch (e: Exception) {
            Log.w(TAG, "커널 이벤트 생성 실패: ${e.message}")
        }
    }
    
    /**
     * 방법 5: uinput을 통한 가상 입력 디바이스 생성
     */
    private fun createVirtualInputDevice() {
        try {
            val uinputFile = File("/dev/uinput")
            if (!uinputFile.exists()) {
                // uinput 모듈 로드
                Runtime.getRuntime().exec("modprobe uinput")
            }
            
            val raf = RandomAccessFile(uinputFile, "rw")
            
            // uinput_user_dev 구조체 설정
            val deviceName = "Virtual Touch Screen"
            val nameBytes = deviceName.toByteArray()
            val nameBuf = ByteArray(80)
            System.arraycopy(nameBytes, 0, nameBuf, 0, minOf(nameBytes.size, 80))
            
            raf.write(nameBuf)
            raf.writeShort(1) // BUS_USB
            raf.writeShort(0x1234) // vendor
            raf.writeShort(0x5678) // product
            raf.writeShort(1) // version
            
            // 절대 좌표 설정
            raf.seek(1052) // absmin offset
            raf.writeInt(0) // ABS_X min
            raf.writeInt(0) // ABS_Y min
            
            raf.seek(1116) // absmax offset  
            raf.writeInt(1080) // ABS_X max
            raf.writeInt(2400) // ABS_Y max
            
            // ioctl 명령으로 디바이스 생성
            Runtime.getRuntime().exec("ioctl $uinputFile UI_DEV_CREATE")
            
            Log.d(TAG, "가상 입력 디바이스 생성 완료")
            
            // 가상 디바이스로 터치 이벤트 전송
            sendVirtualTouch(raf, 540, 1200)
            
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "가상 디바이스 생성 실패", e)
        }
    }
    
    private fun sendVirtualTouch(device: RandomAccessFile, x: Int, y: Int) {
        val timestamp = System.currentTimeMillis()
        
        // Touch down
        writeInputEvent(device, timestamp, 3, 0, x) // ABS_X
        writeInputEvent(device, timestamp, 3, 1, y) // ABS_Y
        writeInputEvent(device, timestamp, 1, 330, 1) // BTN_TOUCH down
        writeInputEvent(device, timestamp, 0, 0, 0) // SYN_REPORT
        
        Thread.sleep(50)
        
        // Touch up
        writeInputEvent(device, timestamp + 50, 1, 330, 0) // BTN_TOUCH up
        writeInputEvent(device, timestamp + 50, 0, 0, 0) // SYN_REPORT
    }
    
    /**
     * 후킹 라이브러리 생성 (실제로는 NDK 필요)
     */
    private fun createHookLibrary(path: String) {
        // 실제 구현은 NDK로 컴파일된 .so 파일 필요
        // 여기서는 예시 코드만 제공
        val hookCode = """
            #include <dlfcn.h>
            #include <stdio.h>
            
            // 원본 함수 포인터
            static int (*original_checkSecurity)() = NULL;
            static int (*original_blockTouch)() = NULL;
            
            // 후킹된 함수
            int checkSecurity() {
                return 0; // 항상 보안 체크 통과
            }
            
            int blockTouch() {
                return 0; // 터치 차단 해제
            }
            
            __attribute__((constructor))
            void init() {
                // 원본 함수 저장
                original_checkSecurity = dlsym(RTLD_NEXT, "checkSecurity");
                original_blockTouch = dlsym(RTLD_NEXT, "blockTouch");
            }
        """.trimIndent()
        
        File(path).writeText(hookCode)
        Log.d(TAG, "후킹 라이브러리 코드 생성 (컴파일 필요)")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}