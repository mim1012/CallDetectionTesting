# ⚡ Galaxy S24 Ultra 빠른 시작 가이드

## 🚀 5분 안에 시작하기

### 1️⃣ APK 설치 (30초)
```bash
# USB 연결 후
adb install -r app-debug.apk
```

### 2️⃣ 필수 권한 설정 (2분)

#### 터미널에서 한 번에 설정:
```bash
# 오버레이 권한
adb shell appops set com.kakao.taxi.test SYSTEM_ALERT_WINDOW allow

# 접근성 서비스 활성화
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 알림 접근 권한
adb shell cmd notification allow_listener com.kakao.taxi.test/com.kakao.taxi.test.service.HybridAutomationService

# 배터리 최적화 제외
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test
```

### 3️⃣ 앱 실행 및 테스트 (2분)

1. **앱 실행**
   ```bash
   adb shell am start -n com.kakao.taxi.test/.MainActivity
   ```

2. **빠른 진단 실행**
   - "🏥 빠른 진단" 버튼 탭
   - 모든 항목이 ✅인지 확인

3. **Ultimate Bypass 시작**
   - "⚡ 완전 자동화 (Ultimate)" 버튼 탭
   - 화면 녹화 권한 → "지금 시작"

4. **카카오 드라이버 앱 열기**
   - 로그인 상태 확인
   - 콜 대기

## 🔥 빠른 문제 해결

### ❌ "불법 프로그램" 메시지
```bash
# 앱으로 돌아가서
1. "테스트 모드" 체크박스 ON
2. Ultimate Bypass 재시작
```

### ❌ 접근성 서비스 꺼짐
```bash
# 강제 활성화
adb shell settings put secure accessibility_enabled 1
```

### ❌ 화면이 검게 캡처됨
```bash
# FLAG_SECURE 우회
adb shell settings put global hidden_api_policy 1
```

## 📱 원클릭 테스트 스크립트

`quick_test.bat` (Windows):
```batch
@echo off
echo === Galaxy S24 Ultra 자동 설정 ===

:: APK 설치
adb install -r app-debug.apk

:: 모든 권한 자동 설정
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test

:: 앱 실행
adb shell am start -n com.kakao.taxi.test/.MainActivity

echo === 설정 완료! ===
echo Ultimate Bypass 버튼을 눌러주세요!
pause
```

## 🎯 핵심 체크포인트

| 항목 | 확인 방법 | 예상 결과 |
|------|-----------|-----------|
| APK 설치 | `adb shell pm list packages | grep kakao.taxi.test` | 패키지명 출력 |
| 접근성 | 설정 → 접근성 → 설치된 서비스 | KakaoTaxi Test Service ON |
| 화면 캡처 | 앱에서 "화면 캡처 시작" | 플로팅 버튼 표시 |
| Ultimate Bypass | "⚡ 완전 자동화" 버튼 | 백그라운드 실행 |

## 💪 Pro Tips

### 1. 실시간 로그 보기
```bash
adb logcat -s "UltimateBypass:*" "DeepLevel:*" | grep -E "시작|성공|실패"
```

### 2. 성능 모니터링
```bash
# CPU/메모리 실시간 확인
watch -n 1 'adb shell top -n 1 | grep kakao'
```

### 3. 화면 녹화 (증거 수집)
```bash
adb shell screenrecord --time-limit 30 /sdcard/test_result.mp4
adb pull /sdcard/test_result.mp4
```

## 🚨 긴급 상황 대처

### 앱이 크래시하는 경우:
```bash
# 크래시 로그 수집
adb bugreport > crash_report.zip

# 앱 데이터 초기화
adb shell pm clear com.kakao.taxi.test

# 재설치
adb uninstall com.kakao.taxi.test
adb install -r app-debug.apk
```

### 디바이스가 느려진 경우:
```bash
# 앱 강제 종료
adb shell am force-stop com.kakao.taxi.test
adb shell am force-stop com.kakao.driver

# 메모리 정리
adb shell am kill-all
```

## 📞 연락처

테스트 중 문제 발생 시:
- 로그 파일: `adb logcat -d > issue_log.txt`
- 스크린샷: 전원 + 볼륨 다운
- 시스템 정보: `adb shell getprop | grep version`