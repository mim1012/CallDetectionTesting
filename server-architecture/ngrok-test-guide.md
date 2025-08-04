# 🚀 ngrok 테스트 가이드 (5분 완성)

## 1️⃣ 서버 실행 (1분)

### 터미널 1 - 테스트 서버 실행:
```bash
cd D:\Project\KaKao Ventical\server-architecture
node test-server.js
```

**확인할 것:**
```
=====================================
🚀 카카오택시 테스트 서버 시작
=====================================
📡 HTTP: http://localhost:8080/status
🔌 WebSocket: ws://localhost:8081
=====================================
⏳ 드라이버 연결 대기중...
```

---

## 2️⃣ ngrok 터널 생성 (1분)

### 터미널 2 - ngrok 실행:
```bash
# WebSocket 포트(8081)를 인터넷에 공개
ngrok tcp 8081
```

**출력 예시:**
```
Session Status                online
Account                       your-email@gmail.com (Plan: Free)
Version                       3.5.0
Region                        Asia Pacific (ap)
Forwarding                    tcp://4.tcp.ap.ngrok.io:19283 -> localhost:8081
                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                              이 주소를 복사하세요!

Connections                   ttl     opn     rt1     rt5     p50     p90
                              0       0       0.00    0.00    0.00    0.00
```

**중요한 부분:**
- `tcp://4.tcp.ap.ngrok.io:19283` ← 이게 공인 주소입니다
- 숫자(4)와 포트(19283)는 매번 바뀝니다

---

## 3️⃣ 안드로이드 앱 수정 (2분)

### RemoteControlService.kt 수정:
```kotlin
companion object {
    // 기존 (로컬)
    // private const val SERVER_URL = "ws://192.168.1.100:8081"
    
    // ngrok 주소로 변경 (tcp:// 를 ws:// 로 바꿔주세요)
    private const val SERVER_URL = "ws://4.tcp.ap.ngrok.io:19283"
}
```

### 앱 다시 빌드:
```bash
# PowerShell에서
cd "D:\Project\KaKao Ventical"
.\gradlew.bat assembleDebug

# 설치
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 4️⃣ 테스트 실행 (1분)

### 폰에서:
1. **VirtualXposed 실행**
2. **우리 테스트 앱 실행**
3. **"테스트 시작" 버튼 클릭**
4. **화면 캡처 권한 허용**

### PC 콘솔 확인:

**ngrok 터미널:**
```
Connections                   ttl     opn     rt1     rt5     p50     p90
                              1       1       0.00    0.00    0.00    0.00
                              ↑       ↑
                              연결됨!
```

**서버 터미널:**
```
✅ 드라이버 연결: driver_1234567890
현재 연결: 1명
📸 스크린샷 수신 (driver_1234567890)
   노란 픽셀 수: 0
📸 스크린샷 수신 (driver_1234567890)
   노란 픽셀 수: 0
```

---

## 5️⃣ 카카오택시 테스트

### 폰에서:
1. **카카오택시 드라이버 앱 실행** (VirtualXposed 내에서)
2. **콜 대기**

### 콜이 왔을 때 서버 콘솔:
```
📸 스크린샷 수신 (driver_1234567890)
   노란 픽셀 수: 287
🎯 노란 버튼 발견!
   위치: (540, 1850)
   크기: 287 픽셀
👆 클릭 명령 전송: (540, 1850)
📝 [driver_1234567890] 클릭 성공: (540, 1850)
```

---

## ❓ 자주 묻는 질문

### Q: 왜 ngrok을 쓰나요?
**A:** PC와 폰이 다른 네트워크에 있어도 연결 가능
```
집 PC (KT 인터넷) ←→ ngrok ←→ 폰 (SKT LTE)
```

### Q: tcp:// 를 ws:// 로 바꾸는 이유?
**A:** 
- ngrok은 TCP 터널 제공 → `tcp://`
- 우리 앱은 WebSocket 사용 → `ws://`
- 프로토콜만 바꿔주면 됨

### Q: 매번 주소가 바뀌나요?
**A:** 무료 버전은 재시작할 때마다 바뀜
- 해결: 유료 버전 ($10/월) 고정 도메인
- 또는: 테스트 중에는 ngrok 끄지 말기

### Q: 속도가 느린가요?
**A:** 
- 국내 ↔ ngrok 서버(싱가포르) ↔ 국내
- 지연시간: 약 50-100ms 추가
- 테스트는 충분, 실제 운영은 국내 서버 추천

---

## 🔍 문제 해결

### "드라이버 연결" 안 뜸
```bash
# 1. ngrok 주소 확인
# tcp://4.tcp.ap.ngrok.io:19283

# 2. 앱 코드 확인
# ws://4.tcp.ap.ngrok.io:19283 (tcp를 ws로 변경했는지)

# 3. 앱 재빌드 확인
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### "스크린샷 수신" 안 뜸
```bash
# 화면 캡처 권한 확인
adb shell dumpsys media_projection

# VirtualXposed 확인
# DisableFlagSecure 모듈 활성화 필수
```

### "노란 버튼" 감지 안 됨
```javascript
// test-server.js 수정
// 노란색 RGB 범위 조정
if (color.r > 180 && color.g > 150 && color.b < 120) {
    // 더 넓은 범위로 수정
}
```

---

## 📊 테스트 체크리스트

```
✅ 서버 실행 (node test-server.js)
✅ ngrok 터널 생성 (ngrok tcp 8081)
✅ 앱 코드 수정 (ws://주소 변경)
✅ 앱 재빌드 (gradlew assembleDebug)
✅ 앱 설치 (adb install)
✅ VirtualXposed 실행
✅ 테스트 앱 실행
✅ 화면 캡처 권한 허용
✅ 서버 연결 확인 ("드라이버 연결")
✅ 카카오택시 앱 실행
✅ 콜 수락 테스트
```

---

## 🎯 최종 목표

**성공 기준:**
서버 콘솔에 다음 메시지가 순서대로 출력:
1. `✅ 드라이버 연결`
2. `📸 스크린샷 수신` (반복)
3. `🎯 노란 버튼 발견!`
4. `👆 클릭 명령 전송`

**이것만 확인되면 테스트 성공!**