# 클라우드 서버 배포 가이드

## AWS EC2 무료 티어 사용

### 1. EC2 인스턴스 생성
```bash
# Ubuntu 22.04 LTS 선택
# t2.micro (무료 티어)
# 보안 그룹: 8080, 8081 포트 개방
```

### 2. 서버 설정
```bash
# SSH 접속
ssh -i your-key.pem ubuntu@ec2-xx-xx-xx-xx.compute.amazonaws.com

# Node.js 설치
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# 프로젝트 업로드
scp -r server-architecture ubuntu@ec2-xx:/home/ubuntu/

# 의존성 설치
cd server-architecture
npm install

# PM2로 백그라운드 실행
sudo npm install -g pm2
pm2 start test-server.js
pm2 save
pm2 startup
```

### 3. 안드로이드 설정
```kotlin
// EC2 퍼블릭 IP 사용
private const val SERVER_URL = "ws://13.125.xxx.xxx:8081"
```

---

## Google Cloud Platform 무료 티어

### 1. Compute Engine 인스턴스
```bash
# e2-micro 인스턴스 (무료)
# 리전: asia-northeast3 (서울)
# 방화벽: tcp:8080,8081 허용
```

### 2. 배포 스크립트
```bash
#!/bin/bash
gcloud compute instances create kakao-server \
    --zone=asia-northeast3-a \
    --machine-type=e2-micro \
    --image-family=ubuntu-2204-lts \
    --image-project=ubuntu-os-cloud \
    --tags=websocket-server

gcloud compute firewall-rules create allow-websocket \
    --allow tcp:8080,tcp:8081 \
    --source-ranges 0.0.0.0/0 \
    --target-tags websocket-server
```

---

## Oracle Cloud 무료 티어 (평생 무료)

### 1. Always Free 인스턴스
- VM.Standard.E2.1.Micro
- 1 OCPU, 1GB RAM
- 평생 무료

### 2. 네트워크 설정
```bash
# 인그레스 규칙 추가
# 포트: 8080, 8081
# 소스: 0.0.0.0/0
```

---

## 비용 비교

| 서비스 | 무료 기간 | 스펙 | 추천도 |
|--------|----------|------|--------|
| ngrok | 무제한 | - | ⭐⭐⭐ (테스트용) |
| AWS EC2 | 12개월 | t2.micro | ⭐⭐⭐⭐ |
| GCP | 12개월 | e2-micro | ⭐⭐⭐⭐ |
| Oracle | 평생 | 1 OCPU | ⭐⭐⭐⭐⭐ |