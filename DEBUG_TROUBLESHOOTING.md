# 디버그 및 문제 해결 가이드

## 🔍 디버그 폴더 확인 방법

### 방법 1: 앱에서 직접 열기
1. 메인 화면에서 **"디버그 폴더 열기"** 버튼 클릭
2. 파일 관리자가 자동으로 열림

### 방법 2: 파일 관리자에서 찾기
```
내장 메모리 > Android > data > com.kakao.taxi.test > files > Pictures > KakaoTaxiDebug
```

### 방법 3: ADB 명령어
```bash
# 디버그 파일 목록 확인
adb shell ls -la /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/

# 최신 파일만 보기
adb shell ls -lt /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/ | head -20

# PC로 모든 디버그 파일 복사
adb pull /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/ ./debug_files/

# 특정 파일만 복사
adb pull /sdcard/Android/data/com.kakao.taxi.test/files/Pictures/KakaoTaxiDebug/not_kakao_screen_*.png
```

## 📁 저장되는 디버그 파일 종류

### 1. 화면 감지 실패 시
- **not_kakao_screen_[시간].png** - 카카오 택시 화면으로 인식 못한 스크린샷
- **not_kakao_screen_[시간]_info.json** - 감지 실패 원인 정보

### 2. 버튼 감지 실패 시
- **no_button_found_[시간].png** - 노란 버튼을 찾지 못한 스크린샷
- **no_button_found_[시간]_info.json** - 감지 상세 정보

### 3. 감지 성공 시
- **detection_[시간].png** - 감지된 버튼 위치가 표시된 이미지
- **original_[시간].png** - 원본 스크린샷

### 4. OCR 결과
- **ocr_result_[시간].png** - OCR 영역이 표시된 이미지
- **detection_log_[시간].json** - 전체 감지 세션 로그

## 🔧 일반적인 문제와 해결 방법

### 1. "Not a Kakao Taxi screen" 오류
**원인**: 카카오 택시 화면으로 인식하지 못함

**해결 방법**:
- 카카오 택시 기사용 앱이 전면에 있는지 확인
- 콜 목록 또는 콜 상세 화면인지 확인
- `not_kakao_screen_*.png` 파일을 확인하여 실제 화면 상태 점검

### 2. "No yellow button found" 오류
**원인**: 노란색 버튼을 찾지 못함

**확인 사항**:
- `no_button_found_*.json` 파일에서 `yellow_buttons_count` 확인
- 실제 버튼 색상이 감지 범위에 있는지 확인
- 화면 밝기나 색상 필터가 적용되어 있는지 확인

### 3. 클릭이 실행되지 않음
**원인**: Shizuku 권한 문제

**해결 방법**:
1. Shizuku 앱이 실행 중인지 확인
2. ADB로 Shizuku 재시작:
   ```bash
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. 앱에서 Shizuku 권한 재요청

### 4. OCR 텍스트 추출 실패
**원인**: ML Kit 초기화 문제 또는 텍스트가 불명확

**해결 방법**:
- 앱 재시작 후 OCR 초기화 대기 (약 5초)
- `ocr_result_*.png` 파일에서 텍스트 영역 확인
- 화면 해상도가 너무 낮지 않은지 확인

## 📊 디버그 정보 분석

### JSON 파일 내용 예시
```json
{
  "screen_type": "call_list",
  "yellow_buttons_count": "0",
  "timestamp": "1234567890123",
  "filter_active": "true"
}
```

**필드 설명**:
- `screen_type`: 감지된 화면 종류 (call_list/call_detail)
- `yellow_buttons_count`: 감지된 노란 버튼 개수
- `timestamp`: 감지 시각
- `filter_active`: 필터 활성화 여부

## 🛠️ 고급 디버깅

### 실시간 로그 확인
```bash
# 앱의 모든 로그 보기
adb logcat | grep "com.kakao.taxi.test"

# 특정 태그만 보기
adb logcat -s "AutoDetectionService"
adb logcat -s "KakaoTaxiDetector"
adb logcat -s "DebugHelper"

# 로그를 파일로 저장
adb logcat -d > debug_log.txt
```

### 색상 범위 조정 필요 시
KakaoTaxiDetector.kt 파일에서 색상 범위 수정:
```kotlin
// 현재 설정 (넓은 범위)
KAKAO_YELLOW_R_MIN = 220
KAKAO_YELLOW_R_MAX = 255
KAKAO_YELLOW_G_MIN = 180
KAKAO_YELLOW_G_MAX = 240
KAKAO_YELLOW_B_MIN = 0
KAKAO_YELLOW_B_MAX = 100
```

더 정확한 감지를 위해 실제 버튼의 RGB 값을 확인하여 조정하세요.

## 💡 팁

1. **디버그 모드 항상 켜기**: 문제 발생 시 즉시 원인 파악 가능
2. **주기적으로 디버그 폴더 정리**: 50개 이상 파일은 자동 삭제됨
3. **스크린샷 확인**: 실제 화면과 감지 결과 비교
4. **필터 설정 확인**: 너무 엄격한 조건은 감지 실패 원인