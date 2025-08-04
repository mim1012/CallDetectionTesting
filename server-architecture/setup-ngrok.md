# ngrok을 이용한 외부 접속 설정

## 1. ngrok 설치

### Windows:
```bash
# 1. ngrok 다운로드
https://ngrok.com/download

# 2. 압축 해제 후 PATH에 추가
# 또는 현재 폴더에서 실행
```

## 2. 서버 실행

```bash
# 터미널 1: 테스트 서버 실행
cd server-architecture
node test-server.js
```

## 3. ngrok 터널 생성

```bash
# 터미널 2: WebSocket 포트 터널링
ngrok tcp 8081

# 출력 예시:
# Forwarding tcp://2.tcp.ngrok.io:12345 -> localhost:8081
```

## 4. 안드로이드 앱 설정

```kotlin
// RemoteControlService.kt 수정
// ngrok이 제공한 URL 사용
private const val SERVER_URL = "ws://2.tcp.ngrok.io:12345"
```

## 장점
- 무료 사용 가능
- 즉시 외부 접속 가능
- 방화벽 설정 불필요

## 단점
- 재시작시 URL 변경됨
- 무료 버전은 속도 제한