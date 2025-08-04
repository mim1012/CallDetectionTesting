# Shizuku 설정 가이드

## ⚠️ "Binder haven't been received" 오류 해결

이 오류는 Shizuku 서비스가 시작되지 않았을 때 발생합니다.

## 1. Shizuku 앱 설치
1. Google Play Store에서 "Shizuku" 검색하여 설치
2. 또는 [GitHub](https://github.com/RikkaApps/Shizuku/releases)에서 APK 다운로드

## 2. Shizuku 시작 방법

### 방법 1: 무선 디버깅 사용 (Android 11 이상)
1. **개발자 옵션 활성화**
   - 설정 > 휴대전화 정보 > 빌드 번호 7번 탭

2. **무선 디버깅 활성화**
   - 설정 > 개발자 옵션 > 무선 디버깅 ON
   - Wi-Fi 연결 필수

3. **Shizuku 앱에서 설정**
   - Shizuku 앱 실행
   - "무선 디버깅으로 시작" 선택
   - "페어링" 탭하여 페어링 코드 입력

### 방법 2: ADB (USB) 사용
1. **USB 디버깅 활성화**
   - 설정 > 개발자 옵션 > USB 디버깅 ON

2. **PC에서 ADB 명령 실행**
   ```bash
   # Shizuku 시작
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   만약 위 경로에 파일이 없다면:
   ```bash
   # Shizuku 서비스 직접 시작
   adb shell cmd -w package list packages | grep shizuku
   adb shell sh /data/local/tmp/shizuku_starter.sh
   ```

## 3. Shizuku 권한 부여
1. Shizuku가 실행 중인지 확인 (상단에 "Shizuku is running" 표시)
2. 카카오 택시 테스트 앱 실행
3. Shizuku 권한 요청 팝업이 나타나면 "허용"

## 4. 문제 해결

### Shizuku가 계속 시작되지 않을 때
```bash
# 1. Shizuku 프로세스 확인
adb shell ps | grep shizuku

# 2. 수동으로 Shizuku 서버 시작
adb shell am start-activity moe.shizuku.privileged.api/.RequestPermissionActivity

# 3. 직접 바이너리 실행
adb push shizuku_server /data/local/tmp/
adb shell chmod 755 /data/local/tmp/shizuku_server
adb shell /data/local/tmp/shizuku_server
```

### 재부팅 후 자동 시작 설정
1. Shizuku 앱 > 설정
2. "부팅 시 자동 시작" 활성화 (Root 필요)

## 5. 대안: Shizuku 없이 사용

Shizuku를 사용할 수 없는 경우, 접근성 서비스를 대신 사용할 수 있도록 코드를 수정할 수 있습니다.

### ClickEventHandler 수정
```kotlin
// Shizuku 사용 불가 시 대체 방법
private fun performClickFallback(x: Int, y: Int): Boolean {
    // 1. 접근성 서비스 사용
    // 2. 또는 수동 클릭 알림
    Log.w(TAG, "Shizuku unavailable. Manual click required at ($x, $y)")
    showClickNotification(x, y)
    return false
}
```

## 6. 권한 확인 코드
MainActivity에서 Shizuku 상태 확인:
```kotlin
private fun checkShizukuStatus() {
    try {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            addLog("Shizuku 권한 승인됨")
        } else if (Shizuku.isPreV11()) {
            addLog("Shizuku 버전이 너무 낮음")
        } else if (!Shizuku.pingBinder()) {
            addLog("Shizuku 서비스가 실행되지 않음")
        } else {
            Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
        }
    } catch (e: Exception) {
        addLog("Shizuku 오류: ${e.message}")
    }
}
```

## 7. 자주 묻는 질문

**Q: 매번 ADB로 시작해야 하나요?**
A: Android 11 이상에서는 무선 디버깅으로 시작 가능합니다. 재부팅 후에만 다시 시작하면 됩니다.

**Q: Root가 필요한가요?**
A: 아니요, Shizuku는 Root 없이 ADB 권한으로 동작합니다.

**Q: 보안상 안전한가요?**
A: Shizuku는 오픈소스이며, 앱별로 권한을 관리할 수 있어 안전합니다.