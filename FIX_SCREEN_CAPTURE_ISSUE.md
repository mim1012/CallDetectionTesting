# 🚨 화면 캡처 비활성화 문제 해결 방법

## 문제 원인

1. **MediaProjection 권한 만료**
   - Android는 보안상 MediaProjection 권한을 일정 시간 후 자동 해제
   - 다른 앱이 화면 녹화 시작 시 기존 권한 무효화

2. **서비스 종료**
   - 메모리 부족 시 시스템이 서비스 강제 종료
   - 배터리 최적화로 인한 백그라운드 제한

3. **권한 충돌**
   - 다른 화면 녹화 앱과 충돌
   - 시스템 화면 녹화 기능과 충돌

## 즉시 해결 방법

### 1. 강제 권한 유지 (ADB)
```bash
# 개발자 옵션에서 "권한 모니터링 사용 안함" 활성화
adb shell settings put global hidden_api_policy 1

# 백그라운드 제한 해제
adb shell cmd appops set com.kakao.taxi.test RUN_IN_BACKGROUND allow
adb shell cmd appops set com.kakao.taxi.test RUN_ANY_IN_BACKGROUND allow

# 배터리 최적화 제외
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test
```

### 2. 자동 재시작 코드 추가
MainActivity.kt에 추가:
```kotlin
private fun ensureScreenCaptureActive() {
    if (!isCapturing) {
        addLog("⚠️ 화면 캡처가 중지됨. 자동 재시작...")
        lifecycleScope.launch {
            delay(500)
            startCapture()
        }
    }
}

// onResume에 추가
override fun onResume() {
    super.onResume()
    ensureScreenCaptureActive()
}
```

### 3. 영구적 해결책 - Foreground Service 강화
ScreenCaptureService.kt 수정:
```kotlin
private fun startForegroundServiceWithHighPriority() {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("화면 캡처 활성화")
        .setContentText("카카오택시 자동화 실행 중")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
    
    startForeground(NOTIFICATION_ID, notification)
}
```

## 설정 체크리스트

### 시스템 설정
- [ ] 개발자 옵션 → "활동 유지" ON
- [ ] 배터리 → 앱 최적화 → KakaoTaxi Test 제외
- [ ] 앱 정보 → 배터리 → "제한 없음" 선택
- [ ] 특별한 앱 액세스 → 기기 관리자 앱 → 활성화

### Galaxy S24 Ultra 전용
- [ ] 설정 → 기기 관리 → 배터리 → 백그라운드 사용량 제한 → 절전 예외 앱 추가
- [ ] Game Booster에서 제외 (있는 경우)
- [ ] Samsung Secure Folder에서 실행 X

## 자동 복구 스크립트

`auto_fix_capture.bat`:
```batch
@echo off
echo 화면 캡처 문제 자동 해결 중...

:: 앱 강제 종료
adb shell am force-stop com.kakao.taxi.test

:: 권한 재설정
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService

:: 백그라운드 제한 해제
adb shell cmd appops set com.kakao.taxi.test RUN_IN_BACKGROUND allow
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test

:: 앱 재시작
adb shell am start -n com.kakao.taxi.test/.MainActivity

echo 완료! 앱에서 화면 캡처를 다시 시작하세요.
pause
```

## 코드 레벨 개선

### 1. 자동 재연결 로직
```kotlin
class ScreenCaptureService : Service() {
    private var retryCount = 0
    private val maxRetries = 3
    
    private fun handleCaptureError() {
        if (retryCount < maxRetries) {
            retryCount++
            Handler(Looper.getMainLooper()).postDelayed({
                restartCapture()
            }, 2000)
        }
    }
    
    private fun restartCapture() {
        // MainActivity에 재시작 요청
        val intent = Intent("com.kakao.taxi.test.REQUEST_CAPTURE_RESTART")
        sendBroadcast(intent)
    }
}
```

### 2. Keep-Alive 메커니즘
```kotlin
private fun startKeepAlive() {
    timer = Timer()
    timer.scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection lost! Requesting restart...")
                requestCaptureRestart()
            }
        }
    }, 0, 5000) // 5초마다 체크
}
```

## 가장 확실한 해결책

**AccessibilityService만 사용하기**:
```kotlin
// MediaProjection 대신 AccessibilityService로 전환
private fun switchToAccessibilityOnly() {
    addLog("⚡ 접근성 서비스 전용 모드로 전환")
    
    // 화면 캡처 중지
    stopCapture()
    
    // 접근성 서비스 강제 활성화
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    startActivity(intent)
    
    Toast.makeText(this, 
        "KakaoTaxi Test Service를 활성화하세요", 
        Toast.LENGTH_LONG).show()
}
```

## 긴급 대응

화면 캡처가 계속 실패하면:

1. **앱 데이터 초기화**:
   ```bash
   adb shell pm clear com.kakao.taxi.test
   ```

2. **재설치**:
   ```bash
   adb uninstall com.kakao.taxi.test
   adb install -r app-debug.apk
   ```

3. **디바이스 재부팅**

이 방법들을 순서대로 시도해보세요!