# 모바일 데이터 직접 연결 방법

## 방법 1: Termux로 폰에서 서버 실행

### 1. Termux 설치
```bash
# F-Droid에서 Termux 설치 (Play Store 버전 X)
```

### 2. Node.js 설치
```bash
pkg update && pkg upgrade
pkg install nodejs
pkg install git
```

### 3. 서버 코드 다운로드
```bash
git clone [your-repo]
cd server-architecture
npm install
```

### 4. 서버 실행
```bash
node test-server.js
```

### 5. 같은 폰에서 접속
```kotlin
// localhost 사용
private const val SERVER_URL = "ws://127.0.0.1:8081"
```

---

## 방법 2: 역방향 프록시 (Cloudflare Tunnel)

### 1. Cloudflare 계정 생성 (무료)

### 2. cloudflared 설치
```bash
# Windows
winget install --id Cloudflare.cloudflared

# 또는 직접 다운로드
https://github.com/cloudflare/cloudflared/releases
```

### 3. 터널 생성
```bash
# 로그인
cloudflared tunnel login

# 터널 생성
cloudflared tunnel create kakao-taxi

# 설정 파일 생성 (config.yml)
tunnel: kakao-taxi
credentials-file: C:\Users\[username]\.cloudflared\[tunnel-id].json

ingress:
  - hostname: kakao-taxi.yourdomain.com
    service: ws://localhost:8081
  - service: http_status:404
```

### 4. 터널 실행
```bash
cloudflared tunnel run kakao-taxi
```

### 5. 안드로이드 설정
```kotlin
// Cloudflare 도메인 사용
private const val SERVER_URL = "wss://kakao-taxi.yourdomain.com"
```

---

## 방법 3: 로컬 서버 + VPN

### 1. WireGuard VPN 설정
```bash
# 서버측 (PC)
# WireGuard 설치 후 설정
```

### 2. 폰에서 VPN 연결
```bash
# WireGuard 앱 설치
# QR 코드로 설정 가져오기
```

### 3. VPN IP로 접속
```kotlin
// VPN 내부 IP 사용
private const val SERVER_URL = "ws://10.0.0.1:8081"
```