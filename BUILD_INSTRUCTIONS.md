# 🔨 APK 빌드 가이드

## 📋 사전 요구사항

1. **JDK 11 이상** 설치
   - 확인: `java -version`
   - 다운로드: https://adoptopenjdk.net/

2. **Android SDK** 설치
   - Android Studio 설치 시 자동 포함
   - 또는 Command Line Tools 설치

3. **환경 변수** 설정
   - `JAVA_HOME`: JDK 설치 경로
   - `ANDROID_HOME`: Android SDK 경로

## 🚀 빌드 방법

### 방법 1: 자동 빌드 스크립트 (권장)

1. **Windows 명령 프롬프트** 또는 **PowerShell** 열기

2. 프로젝트 디렉토리로 이동:
   ```cmd
   cd "D:\Project\KaKao Ventical"
   ```

3. 빌드 스크립트 실행:
   ```cmd
   build_and_install.bat
   ```

### 방법 2: 수동 빌드

1. **명령 프롬프트에서**:
   ```cmd
   cd "D:\Project\KaKao Ventical"
   gradlew.bat clean assembleDebug
   ```

2. **빌드 성공 시 APK 위치**:
   ```
   app\build\outputs\apk\debug\app-debug.apk
   ```

## 📱 APK 설치

### USB로 직접 설치:
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### APK 파일 전송 후 설치:
1. APK를 휴대폰으로 복사
2. 파일 관리자에서 APK 실행
3. "알 수 없는 출처" 허용

## ⚠️ 빌드 오류 해결

### "gradlew.bat을 찾을 수 없음"
```cmd
# Gradle Wrapper 재생성
gradle wrapper
```

### "JAVA_HOME이 설정되지 않음"
```cmd
# JDK 경로 설정 (예시)
set JAVA_HOME=C:\Program Files\Java\jdk-11.0.2
```

### "SDK 라이선스 동의 필요"
```cmd
# 라이선스 동의
%ANDROID_HOME%\tools\bin\sdkmanager --licenses
```

## 🎯 빌드 완료 후

1. **APK 정보 확인**:
   - 파일명: `app-debug.apk`
   - 패키지명: `com.kakao.taxi.test`
   - 버전: 1.0

2. **빠른 테스트**:
   ```cmd
   quick_test.bat
   ```

3. **Galaxy S24 Ultra 테스트**:
   - `GALAXY_S24_ULTRA_TEST_GUIDE.md` 참조

## 📊 예상 빌드 시간

- 첫 빌드: 3-5분 (의존성 다운로드)
- 이후 빌드: 1-2분
- Clean 빌드: 2-3분

## 💡 팁

- **빠른 빌드**: `gradlew.bat assembleDebug` (clean 제외)
- **릴리즈 빌드**: `gradlew.bat assembleRelease` (서명 필요)
- **모든 변형 빌드**: `gradlew.bat assemble`