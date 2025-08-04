# 카카오택시 원격 제어 시스템 아키텍처

## 문제점 정리
- **로컬 차단**: 카카오가 접근성 서비스 UI 읽기 차단
- **터치 차단**: 일반적인 터치 주입 모두 차단
- **FLAG_SECURE**: 화면 캡처 차단 (VirtualXposed로 우회 가능)

## 해결 방안: 서버 기반 원격 제어

### 시스템 구조

```
[갤럭시 S24 Ultra] <--WebSocket--> [제어 서버] <---> [모니터링 대시보드]
     |                                  |
     ├─ 화면 스트리밍 ──────────────> OCR/분석
     |                                  |
     └─ 클릭 명령 수신 <───────────── 클릭 좌표
```

### 1단계: 클라이언트 앱 (안드로이드)

```kotlin
// 필수 기능
1. MediaProjection으로 화면 캡처
2. WebSocket으로 서버에 실시간 전송
3. 서버에서 클릭 명령 수신
4. Runtime.exec("input tap x y") 실행
```

### 2단계: 제어 서버 (Node.js/Python)

```javascript
// 서버 기능
1. WebSocket 서버 (실시간 통신)
2. 이미지 수신 및 저장
3. OCR 처리 (Tesseract/Google Vision API)
4. 노란 버튼 감지 (색상 분석)
5. 필터링 로직 (금액, 거리)
6. 클릭 명령 전송
```

### 3단계: 실행 흐름

1. **앱 시작**
   - VirtualXposed 내에서 카카오택시 실행
   - 우리 앱도 함께 실행 (화면 캡처 권한)

2. **화면 스트리밍**
   ```kotlin
   // 100ms마다 화면 캡처
   imageReader.setOnImageAvailableListener({
       val bitmap = captureScreen()
       val base64 = bitmapToBase64(bitmap)
       webSocket.send(base64)
   }, backgroundHandler)
   ```

3. **서버 분석**
   ```javascript
   ws.on('message', async (imageData) => {
       // 1. 이미지 디코딩
       const image = Buffer.from(imageData, 'base64')
       
       // 2. 노란 버튼 찾기
       const yellowButton = findYellowButton(image)
       
       // 3. OCR로 금액/거리 확인
       const text = await performOCR(image)
       
       // 4. 필터링
       if (shouldAccept(text)) {
           ws.send(JSON.stringify({
               action: 'click',
               x: yellowButton.x,
               y: yellowButton.y
           }))
       }
   })
   ```

4. **클릭 실행**
   ```kotlin
   webSocket.onMessage { message ->
       val data = JSONObject(message)
       if (data.getString("action") == "click") {
           val x = data.getInt("x")
           val y = data.getInt("y")
           
           // 여러 방법 시도
           tryClick(x, y)
       }
   }
   
   fun tryClick(x: Int, y: Int) {
       // 방법 1: Runtime.exec
       Runtime.getRuntime().exec("input tap $x $y")
       
       // 방법 2: AccessibilityService (좌표 기반)
       performGlobalAction(x, y)
       
       // 방법 3: sendevent (디바이스별)
       sendTouchEvent(x, y)
   }
   ```

## 장점

1. **우회 가능**: 서버에서 분석하므로 로컬 차단 무의미
2. **다중 제어**: 여러 폰 동시 관리 가능
3. **로깅**: 모든 활동 서버에 기록
4. **실시간 모니터링**: 웹 대시보드로 확인

## 필요한 것들

### 서버 요구사항
- Node.js 또는 Python FastAPI
- WebSocket 라이브러리
- OpenCV (이미지 처리)
- Tesseract OCR 또는 Google Vision API
- PostgreSQL (로깅)

### 클라이언트 요구사항
- Android 7.0+ (MediaProjection API)
- VirtualXposed + DisableFlagSecure
- WebSocket 클라이언트
- 백그라운드 서비스 유지

## 테스트 순서

1. **로컬 테스트**
   - 화면 캡처 성공 확인
   - WebSocket 연결 테스트
   - 클릭 명령 실행 테스트

2. **서버 구축**
   - WebSocket 서버 설정
   - 이미지 수신/처리
   - OCR 설정

3. **통합 테스트**
   - 실제 카카오택시 앱에서 테스트
   - 응답 시간 측정 (목표: 200ms 이내)
   - 성공률 측정