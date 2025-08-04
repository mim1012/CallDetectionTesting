# APK 설치 오류 해결 가이드

## "패키지 파싱 중 문제 발생" 오류 원인 및 해결방법

### 1. 휴대폰 Android 버전 확인
- 이 앱은 최소 Android 7.0 (API 24) 이상 필요
- 설정 > 휴대전화 정보 > Android 버전 확인

### 2. 보안 설정 확인
1. **출처를 알 수 없는 앱 설치 허용**
   - 설정 > 보안 > 출처를 알 수 없는 앱 설치
   - 파일 관리자 또는 설치에 사용하는 앱에 권한 부여

2. **Google Play Protect 일시 비활성화**
   - Play 스토어 > 메뉴 > Play Protect
   - "기기의 보안 위협 검색" 일시 비활성화

### 3. APK 파일 문제 확인
1. **파일 크기 확인**
   - APK 파일이 완전히 전송되었는지 확인
   - 파일 크기가 0KB가 아닌지 확인

2. **다시 빌드**
   ```
   gradlew.bat clean
   gradlew.bat assembleDebug
   ```

3. **APK 위치**
   - 빌드된 APK: `app\build\outputs\apk\debug\app-debug.apk`

### 4. ADB로 설치 시도
```bash
adb install app-debug.apk
```

에러 메시지가 나오면 더 자세한 정보 제공:
- `INSTALL_FAILED_OLDER_SDK`: 휴대폰 Android 버전이 낮음
- `INSTALL_FAILED_DUPLICATE_PERMISSION`: 권한 충돌
- `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`: AndroidManifest 오류

### 5. 서명되지 않은 APK 문제
Debug APK는 자동으로 서명되지만, 일부 기기에서 문제가 될 수 있음

### 6. 대안: Android Studio에서 직접 설치
1. Android Studio 열기
2. 휴대폰을 USB로 연결 (개발자 모드 활성화)
3. Run 버튼 클릭

### 7. 빌드 변형 확인
release 빌드가 필요한 경우:
```
gradlew.bat assembleRelease
```
(단, 서명 키가 필요함)