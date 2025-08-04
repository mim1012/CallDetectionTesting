# 단계별 디버깅 가이드

## 🔍 실시간 상태 확인

앱 메인 화면에서 실시간으로 확인 가능:
- 📷 **화면캡처**: 스크린샷 촬영 여부
- 🔍 **버튼감지**: 노란 버튼 발견 여부  
- 👆 **클릭시도**: 클릭 실행 여부
- 📍 **좌표**: 감지된 버튼 위치

## 📋 테스트 순서

### 1단계: Mock 화면 테스트
1. **"Mock 콜 화면 테스트"** 버튼 클릭
2. 노란색 수락 버튼이 있는 화면 표시
3. 메인 화면의 디버그 상태 확인:
   - ✅ 화면캡처: 캡처 성공
   - ✅ 버튼감지: 버튼 1개 발견 (x, y)
   - ✅ 클릭시도: 클릭 성공

### 2단계: 실제 카카오 택시 앱 테스트
1. **"화면 캡처 시작"** 클릭
2. 카카오 T 기사용 앱으로 이동
3. 메인 앱의 디버그 상태 실시간 확인

## 🚨 문제별 해결 방법

### 📷 "화면캡처: 대기" 상태 유지
**원인**: MediaProjection 권한 문제
```bash
# 로그 확인
adb logcat -s ScreenCaptureService
```
**해결**: 
- 화면 녹화 권한 재승인
- 앱 재시작

### 🔍 "버튼감지: ❌ 카카오 택시 화면 아님"
**원인**: 화면 인식 실패
- 디버그 폴더에서 `not_kakao_screen_*.png` 확인
- 실제 카카오 택시 화면인지 확인

### 🔍 "버튼감지: ❌ 버튼 없음 (0개)"
**원인**: 색상 감지 범위 문제
```bash
# 저장된 스크린샷 확인
adb pull /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/no_button_found_*.png
```
**해결**:
- 노란색 버튼이 화면에 있는지 확인
- KakaoTaxiDetector.kt에서 색상 범위 조정

### 👆 "클릭시도: ⚠️ 자동 클릭 실패"
**원인**: Shizuku 또는 접근성 권한
```bash
# Shizuku 상태 확인
adb shell pm list packages | grep shizuku
```
**해결**:
1. Shizuku 실행 확인
2. 접근성 서비스 활성화
3. 수동 알림 모드 사용

## 📊 디버그 로그 분석

### ADB 실시간 로그
```bash
# 모든 디버그 메시지
adb logcat | grep -E "AutoDetection|KakaoTaxi|Debug"

# 특정 태그만
adb logcat -s AutoDetectionService:* KakaoTaxiDetector:*
```

### 파일로 저장
```bash
# 디버그 세션 저장
adb logcat -d > debug_session_$(date +%Y%m%d_%H%M%S).txt
```

## 🔄 빠른 테스트 사이클

1. **Mock 테스트로 시작**
   - 기본 기능 정상 작동 확인
   - 좌표 클릭 테스트

2. **실제 앱에서 확인**
   - 카카오 T 기사용 앱 열기
   - 콜 목록 화면에서 대기

3. **디버그 정보 수집**
   ```bash
   # 최근 디버그 파일 모두 가져오기
   adb shell ls -lt /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/ | head -10
   adb pull /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/
   ```

## 💡 Pro Tips

1. **화면 밝기 최대로**: 색상 감지 정확도 향상
2. **다크모드 OFF**: 노란색 버튼 감지 개선
3. **배터리 최적화 제외**: 백그라운드 실행 안정화
4. **Wi-Fi 연결**: 무선 디버깅 사용 시

## 🎯 정확한 문제 파악 순서

1. 메인 화면 디버그 상태 확인
2. 어느 단계에서 멈췄는지 파악
3. 해당 단계의 디버그 파일 확인
4. ADB 로그로 상세 오류 확인

이제 각 단계별로 정확히 어디서 문제가 발생하는지 알 수 있습니다!