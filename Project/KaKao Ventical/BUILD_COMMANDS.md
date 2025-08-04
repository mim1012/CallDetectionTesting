# 🔨 빌드 명령어 모음

## Windows에서 APK 빌드하기

### 1. PowerShell 사용
```powershell
cd "D:\Project\KaKao Ventical"
.\gradlew.bat assembleDebug
```

### 2. Command Prompt 사용
```cmd
cd D:\Project\KaKao Ventical
gradlew.bat assembleDebug
```

### 3. 빌드 후 APK 위치
```
app\build\outputs\apk\debug\app-debug.apk
```

## 자주 발생하는 빌드 오류 해결

### 1. Kotlin 타입 오류
- `Type mismatch` 오류: 반환 타입 확인
- `Unresolved reference` 오류: import 문 확인

### 2. 빌드 캐시 문제
```cmd
gradlew.bat clean
gradlew.bat assembleDebug
```

### 3. Gradle 동기화
```cmd
gradlew.bat --refresh-dependencies assembleDebug
```

## 빠른 설치 명령어

### ADB로 설치 (USB 연결 시)
```cmd
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 무선 ADB 설치
```cmd
adb connect [기기IP]:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 로그 확인

### 실시간 로그 보기
```cmd
adb logcat | findstr "KakaoTaxi"
```

### 로그 파일로 저장
```cmd
adb logcat > kakao_log.txt
```