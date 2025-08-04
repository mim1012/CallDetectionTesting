# 카카오 택시 자동 수락 테스트 가이드

## 개요
이 문서는 카카오 택시 앱의 노란색 콜 수락 버튼을 자동으로 감지하고 클릭하는 테스트 앱의 개발자 테스트 가이드입니다.

## 주요 기능
1. **노란색 버튼 감지**: HSV 색상 공간에서 노란색 영역 탐지
2. **OCR 텍스트 추출**: 금액과 거리 정보 추출
3. **자동 클릭**: 조건 충족 시 버튼 자동 클릭
4. **시각화**: 감지된 영역을 오버레이로 표시

## 테스트 환경 설정

### 1. 필수 권한 설정
```bash
# ADB로 권한 부여
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.kakao.taxi.test android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.kakao.taxi.test android.permission.WRITE_EXTERNAL_STORAGE
```

### 2. Shizuku 설정 (선택사항)
```bash
# Shizuku 설치 및 실행
# https://shizuku.rikka.app/ 에서 다운로드
adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
```

### 3. Tesseract OCR 데이터 설정
```bash
# 한글 학습 데이터 다운로드
wget https://github.com/tesseract-ocr/tessdata/raw/main/kor.traineddata
wget https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata

# 앱 데이터 폴더로 복사
adb push kor.traineddata /sdcard/Android/data/com.kakao.taxi.test/files/tessdata/
adb push eng.traineddata /sdcard/Android/data/com.kakao.taxi.test/files/tessdata/
```

## 테스트 시나리오

### 1. Mock 화면 테스트 (개발자용)
```kotlin
// 테스트 순서
1. 앱 실행
2. "화면 캡처 시작" 클릭
3. "Mock 콜 화면 테스트" 클릭
4. 로그에서 감지 결과 확인

// 예상 결과
- 노란색 버튼 감지: 성공 (confidence > 60%)
- 버튼 위치: 화면 하단
- 클릭 좌표: 버튼 중앙
```

### 2. 실제 카카오 택시 앱 테스트
```kotlin
// 테스트 순서
1. 카카오 택시 기사 앱 실행
2. 테스트 앱 실행
3. "화면 캡처 시작" 클릭
4. 플로팅 버튼 표시 확인
5. 카카오 택시 앱으로 전환
6. 플로팅 버튼 → "▶️" 클릭 (자동 감지 시작)

// 실시간 모니터링
- 플로팅 버튼의 컨트롤 패널에서 테스트
- 알림바에서 상태 확인
- 오버레이로 감지 영역 시각화
```

### 3. 노란색 버튼 감지 로직

#### HSV 색상 범위
```kotlin
// 표준 노란색 범위
Hue: 20-40 (60도 기준)
Saturation: 100-255 (39-100%)
Value: 100-255 (39-100%)

// 확장 노란색 범위 (밝기 변화 대응)
Hue: 15-45
Saturation: 80-255
Value: 80-255
```

#### 버튼 크기 제약
```kotlin
// 화면 대비 비율
최소 너비: 화면의 20%
최대 너비: 화면의 90%
최소 높이: 화면의 5%
최대 높이: 화면의 15%

// 가로세로 비율
최소: 2.0 (가로가 세로의 2배)
최대: 8.0 (가로가 세로의 8배)
```

### 4. 디버깅 방법

#### 로그 확인
```bash
# Logcat 필터링
adb logcat -s "YellowButtonDetector:*" "AutoDetectionService:*" "ClickEventHandler:*"

# 주요 로그 태그
- YellowButtonDetector: 색상 감지 결과
- OCRProcessor: 텍스트 추출 결과
- ClickEventHandler: 클릭 이벤트 결과
```

#### 캡처 이미지 저장
```kotlin
// ScreenCaptureService에 추가
fun saveBitmapForDebug(bitmap: Bitmap, filename: String) {
    val file = File(getExternalFilesDir(null), "$filename.png")
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
    Log.d(TAG, "Debug image saved: ${file.absolutePath}")
}
```

#### 감지 결과 시각화
```kotlin
// 디버그 모드에서 감지 결과 그리기
val debugBitmap = yellowButtonDetector.debugDrawDetection(
    capturedBitmap, 
    detectedCandidates
)
// 화면에 표시 또는 저장
```

## 문제 해결

### 1. 노란색 버튼이 감지되지 않을 때
- 조명 조건 확인 (너무 밝거나 어두운 환경)
- HSV 범위 조정 (useAltRange = true)
- 화면 해상도 확인
- 버튼 크기 제약 조건 확인

### 2. OCR이 작동하지 않을 때
- Tesseract 데이터 파일 확인
- 텍스트 영역의 대비 확인
- 폰트 크기가 너무 작지 않은지 확인

### 3. 클릭이 실패할 때
- Shizuku 권한 상태 확인
- 좌표 변환 정확도 확인
- 다른 앱의 오버레이 간섭 확인

## 성능 최적화

### 1. 프레임 레이트
```kotlin
// 감지 주기 조정
const val DETECTION_INTERVAL = 2000L // 2초
// 배터리와 성능의 균형점
```

### 2. 이미지 전처리
```kotlin
// 이미지 크기 축소로 처리 속도 향상
val scaledBitmap = Bitmap.createScaledBitmap(
    original, 
    original.width / 2, 
    original.height / 2, 
    true
)
```

### 3. 메모리 관리
```kotlin
// OpenCV Mat 객체 해제
mat.release()
// Bitmap 재활용
bitmap.recycle()
```

## 보안 고려사항

1. **권한 최소화**: 필요한 권한만 요청
2. **데이터 보호**: 캡처된 화면 정보 로컬 처리
3. **사용자 동의**: 자동화 기능 사용 전 명시적 동의

## 추가 개발 아이디어

1. **머신러닝 모델**: 버튼 감지 정확도 향상
2. **사용자 설정**: HSV 범위, 필터 조건 커스터마이징
3. **통계 기능**: 수락률, 평균 금액 등 분석
4. **다중 해상도 지원**: 다양한 기기 대응

## 참고 자료

- [OpenCV Android Documentation](https://docs.opencv.org/4.x/d5/df8/tutorial_dev_with_OCV_on_Android.html)
- [Tesseract Android Integration](https://github.com/tesseract-ocr/tesseract/wiki/APIExample)
- [Shizuku Documentation](https://shizuku.rikka.app/guide/setup/)
- [Android MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection)