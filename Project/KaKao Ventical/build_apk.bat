@echo off
echo Building Kakao Taxi Test APK...
cd /d "D:\Project\KaKao Ventical"
call gradlew.bat assembleDebug
echo.
echo Build complete! Check app\build\outputs\apk\debug\ for the APK file.
pause