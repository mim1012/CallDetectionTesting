# 갤럭시 S24 울트라 설치 가이드

## 설치 전 확인사항

### 1. 개발자 옵션 활성화
1. 설정 > 휴대전화 정보 > 소프트웨어 정보
2. 빌드 번호를 7번 탭
3. 설정 > 개발자 옵션으로 이동
4. 다음 항목 활성화:
   - 개발자 옵션 ON
   - USB 디버깅 ON

### 2. 보안 설정
1. 설정 > 보안 및 개인 정보 보호
2. 앱 보안 > 외부 소스 설치 허용
3. 파일 관리자 앱에 대해 허용 설정

### 3. Google Play Protect 설정
1. Play 스토어 열기
2. 프로필 아이콘 > Play Protect
3. 설정 아이콘 > "앱 검사" 일시적으로 비활성화

## APK 다시 빌드

targetSdk를 33으로 낮췄으니 다시 빌드:
```bash
gradlew.bat clean
gradlew.bat assembleDebug
```

## ADB로 설치 (권장)

1. USB 케이블로 PC와 연결
2. 휴대폰에서 USB 디버깅 허용
3. CMD에서 실행:
```bash
cd "D:\Project\KaKao Ventical"
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 무선 ADB 설치 (대안)

1. 개발자 옵션 > 무선 디버깅 활성화
2. QR 코드로 페어링 또는 페어링 코드 사용
3. 연결 후:
```bash
adb connect [IP주소:포트]
adb install -r app-debug.apk
```

## 설치 실패 시 진단

```bash
# 상세 오류 확인
adb install -r -d app-debug.apk

# 로그 확인
adb logcat | grep PackageManager
```

## 일반적인 오류 해결

- **INSTALL_FAILED_UPDATE_INCOMPATIBLE**: 기존 앱 삭제 후 재설치
- **INSTALL_PARSE_FAILED_NO_CERTIFICATES**: 서명 문제
- **INSTALL_FAILED_VERIFICATION_FAILURE**: Play Protect 비활성화 필요

## Samsung 특별 설정

1. 설정 > 디바이스 케어 > 보안
2. "앱 보안 검사" 일시 비활성화
3. Samsung Knox 관련 설정 확인