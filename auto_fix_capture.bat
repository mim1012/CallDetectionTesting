@echo off
echo ========================================
echo   화면 캡처 문제 자동 해결
echo ========================================
echo.

echo [1/5] 앱 강제 종료 중...
adb shell am force-stop com.kakao.taxi.test

echo [2/5] 권한 재설정 중...
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.kakao.taxi.test android.permission.POST_NOTIFICATIONS

echo [3/5] 접근성 서비스 활성화 중...
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
adb shell settings put secure accessibility_enabled 1

echo [4/5] 백그라운드 제한 해제 중...
adb shell cmd appops set com.kakao.taxi.test RUN_IN_BACKGROUND allow
adb shell cmd appops set com.kakao.taxi.test RUN_ANY_IN_BACKGROUND allow
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test

echo [5/5] 앱 재시작 중...
timeout /t 2 >nul
adb shell am start -n com.kakao.taxi.test/.MainActivity

echo.
echo ========================================
echo   완료!
echo ========================================
echo.
echo 다음 작업을 수행하세요:
echo 1. 앱에서 "화면 캡처 시작" 버튼 클릭
echo 2. 권한 허용
echo 3. "빠른 진단"으로 상태 확인
echo.
echo 여전히 문제가 있다면 디바이스를 재부팅하세요.
echo.
pause