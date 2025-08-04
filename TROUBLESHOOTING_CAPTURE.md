# 화면 캡처 및 감지 문제 해결 가이드

## 🔍 현재 사용 중인 기술

### 화면 캡처
- **MediaProjection API**: Android 5.0+ 화면 녹화 API
- 루트 권한 불필요
- 사용자 승인 필요

### 버튼 감지
- **색상 기반**: 카카오 노란색 (RGB) 감지
- **영역 찾기**: 연결된 노란색 픽셀 그룹화
- **크기 검증**: 버튼 크기 조건 확인

### 텍스트 인식
- **Google ML Kit**: 온디바이스 OCR
- 한국어/영어 지원
- 실시간 텍스트 추출

### 클릭 수행
- **AccessibilityService**: 좌표 기반 제스처
- **대체 방법**: Shizuku (ADB 권한)

## 🚨 로그 분석

### "📸 캡쳐 요청 중..." 에서 멈춤
**원인**:
1. MediaProjection 권한 미승인
2. ScreenCaptureService 초기화 실패
3. 화면 녹화 권한 만료

**해결**:
```bash
# 권한 상태 확인
adb shell dumpsys media_projection

# 서비스 상태 확인
adb shell dumpsys activity services | grep ScreenCapture
```

### "⏸️ 카카오 택시 앱 대기 중..." 계속 표시
**원인**:
1. 접근성 서비스가 카카오 앱을 감지 못함
2. 패키지명 불일치
3. 접근성 이벤트 수신 안됨

**해결**:
1. 접근성 서비스 재시작
2. 카카오 T 기사용 앱 실행 확인
3. 패키지명 확인: `com.kakao.taxi.driver`

## 🛠️ 단계별 문제 해결

### 1단계: 권한 확인
```bash
# 접근성 서비스 활성화 확인
adb shell settings get secure enabled_accessibility_services

# 오버레이 권한 확인
adb shell appops get com.kakao.taxi.test SYSTEM_ALERT_WINDOW
```

### 2단계: 서비스 재시작
1. 설정 > 접근성 > 카카오 택시 테스트
2. 서비스 OFF → ON
3. 앱 완전 종료 후 재실행

### 3단계: 디버그 모드 테스트
1. Mock 콜 화면으로 테스트
2. 정상 작동 확인
3. 실제 카카오 앱에서 테스트

## 💡 임시 해결책

### 카카오 앱 감지 우회
`AutoDetectionService.kt` 수정:
```kotlin
private fun isKakaoTaxiActive(): Boolean {
    // 임시로 항상 true 반환 (테스트용)
    return true
}
```

### 수동 화면 캡처
1. "화면 캡처 시작" 버튼 클릭
2. 권한 승인
3. 플로팅 버튼으로 제어

## 📊 추가 디버그 정보

### ADB 로그 확인
```bash
# 접근성 이벤트 로그
adb logcat -s KakaoTaxiAccessibility:*

# 화면 캡처 로그
adb logcat -s ScreenCaptureService:*

# 전체 앱 로그
adb logcat | grep "com.kakao.taxi.test"
```

### 상태 덤프
```bash
# 현재 포커스된 앱
adb shell dumpsys window | grep mCurrentFocus

# 접근성 서비스 정보
adb shell dumpsys accessibility
```

## ✅ 체크리스트

- [ ] 화면 녹화 권한 승인됨
- [ ] 접근성 서비스 활성화됨
- [ ] 오버레이 권한 승인됨
- [ ] 카카오 T 기사용 앱 설치됨
- [ ] 배터리 최적화 제외됨
- [ ] 알림 권한 승인됨

## 🔄 완전 초기화 방법

1. 앱 삭제
2. 재설치
3. 모든 권한 재승인
4. Mock 테스트로 확인
5. 실제 앱에서 테스트