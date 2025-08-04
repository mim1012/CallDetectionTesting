@echo off
echo ============================================
echo   Galaxy S24 Ultra - KakaoTaxi Test Setup
echo ============================================
echo.

:: APK 파일 확인
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [ERROR] APK 파일을 찾을 수 없습니다!
    echo 먼저 gradlew.bat assembleDebug 를 실행하세요.
    pause
    exit /b 1
)

:: ADB 연결 확인
echo [1/7] ADB 디바이스 확인 중...
adb devices | findstr /r "device$" >nul
if errorlevel 1 (
    echo [ERROR] 연결된 디바이스가 없습니다!
    echo USB 디버깅을 활성화하고 디바이스를 연결하세요.
    pause
    exit /b 1
)

:: 기존 앱 제거
echo [2/7] 기존 앱 제거 중...
adb uninstall com.kakao.taxi.test >nul 2>&1

:: APK 설치
echo [3/7] APK 설치 중...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo [ERROR] APK 설치 실패!
    pause
    exit /b 1
)

:: 권한 부여
echo [4/7] 권한 설정 중...
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.kakao.taxi.test android.permission.POST_NOTIFICATIONS
adb shell pm grant com.kakao.taxi.test android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.kakao.taxi.test android.permission.WRITE_EXTERNAL_STORAGE

:: 접근성 서비스 활성화
echo [5/7] 접근성 서비스 활성화 중...
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
adb shell settings put secure accessibility_enabled 1

:: 알림 리스너 활성화
echo [6/7] 알림 리스너 활성화 중...
adb shell cmd notification allow_listener com.kakao.taxi.test/com.kakao.taxi.test.service.HybridAutomationService

:: 배터리 최적화 제외
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test >nul 2>&1

:: 앱 실행
echo [7/7] 앱 실행 중...
adb shell am start -n com.kakao.taxi.test/.MainActivity

echo.
echo ============================================
echo   설정 완료!
echo ============================================
echo.
echo 다음 단계:
echo 1. 앱에서 "빠른 진단" 버튼을 눌러 상태 확인
echo 2. "완전 자동화 (Ultimate)" 버튼을 눌러 시작
echo 3. 카카오 드라이버 앱에서 테스트
echo.
echo 실시간 로그 보기: adb logcat -s "MainActivity:*"
echo.
pause