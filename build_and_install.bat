@echo off
echo ============================================
echo   KakaoTaxi Test - Build and Install
echo ============================================
echo.

:: 빌드
echo [1/3] APK 빌드 중... (약 2-3분 소요)
call gradlew.bat clean assembleDebug

if errorlevel 1 (
    echo.
    echo [ERROR] 빌드 실패!
    echo.
    echo 가능한 해결 방법:
    echo 1. JDK 11 이상 설치 확인
    echo 2. JAVA_HOME 환경변수 설정
    echo 3. Android SDK 설치 확인
    echo.
    pause
    exit /b 1
)

:: APK 확인
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [ERROR] APK 파일을 찾을 수 없습니다!
    pause
    exit /b 1
)

echo.
echo [2/3] 빌드 성공!
for %%I in ("app\build\outputs\apk\debug\app-debug.apk") do (
    echo APK 크기: %%~zI bytes
    echo APK 위치: %%~fI
)

:: 디바이스 연결 확인
echo.
echo [3/3] 디바이스 연결 확인 중...
adb devices | findstr /r "device$" >nul
if errorlevel 1 (
    echo.
    echo [INFO] 연결된 디바이스가 없습니다.
    echo APK 파일 위치: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 수동으로 설치하려면:
    echo 1. USB 디버깅 활성화
    echo 2. adb install -r app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo 디바이스가 연결되었습니다!
    echo.
    choice /C YN /M "APK를 설치하시겠습니까?"
    if errorlevel 2 goto :skip_install
    
    echo APK 설치 중...
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    
    if errorlevel 1 (
        echo [ERROR] 설치 실패!
    ) else (
        echo [SUCCESS] 설치 완료!
        echo.
        echo 앱 실행: adb shell am start -n com.kakao.taxi.test/.MainActivity
    )
)

:skip_install
:: 프로젝트 루트에 APK 복사
copy "app\build\outputs\apk\debug\app-debug.apk" "KakaoTaxiTest-v1.0-debug.apk" >nul 2>&1
echo.
echo APK가 KakaoTaxiTest-v1.0-debug.apk로 복사되었습니다.

echo.
echo ============================================
echo   완료!
echo ============================================
pause