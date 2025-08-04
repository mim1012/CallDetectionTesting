# 🚀 Galaxy S24 Ultra 테스트 가이드

## 📱 디바이스 정보
- **모델**: Samsung Galaxy S24 Ultra
- **Android 버전**: 14 (One UI 6.1)
- **특징**: 
  - 강화된 보안 (Knox)
  - 높은 해상도 (3120x1440)
  - 강력한 성능 (Snapdragon 8 Gen 3)

## 🔧 개발자 설정

### 1. 개발자 옵션 활성화
```bash
설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7번 탭
```

### 2. 필수 개발자 설정
- ✅ **USB 디버깅**: 활성화
- ✅ **USB 디버깅(보안 설정)**: 활성화
- ✅ **앱 확인 사용 안함**: 활성화
- ✅ **GPU 렌더링 프로파일**: 비활성화 (성능 향상)
- ✅ **애니메이션 배율**: 모두 0.5x 또는 사용 안함

### 3. 보안 설정 조정
```bash
설정 → 보안 및 개인정보 보호 → 기타 보안 설정
```
- **알 수 없는 앱 설치**: 허용
- **Google Play 프로텍트**: 비활성화 권장

## 📦 APK 설치 방법

### 방법 1: ADB 설치 (권장)
```bash
# 1. PC에서 ADB 연결
adb devices

# 2. APK 설치
adb install -r app-debug.apk

# 3. 권한 자동 부여
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.kakao.taxi.test android.permission.WRITE_SECURE_SETTINGS
```

### 방법 2: 직접 설치
1. APK 파일을 디바이스로 복사
2. 파일 관리자에서 APK 실행
3. "알 수 없는 앱 설치" 허용

## 🔑 필수 권한 설정

### 1. 접근성 서비스
```
설정 → 접근성 → 설치된 서비스 → KakaoTaxi Test Service → 사용
```

### 2. 알림 접근
```
설정 → 알림 → 고급 설정 → 알림 접근 → KakaoTaxi Test → 허용
```

### 3. 화면 표시 권한
```
설정 → 앱 → KakaoTaxi Test → 권한 → 다른 앱 위에 표시 → 허용
```

### 4. 미디어 프로젝션
- 앱 실행 시 자동으로 요청됨
- "화면 녹화를 시작하시겠습니까?" → "지금 시작"

## 🧪 테스트 절차

### 1단계: 기본 기능 테스트
```
1. 앱 실행
2. "빠른 진단" 버튼 클릭
3. 모든 항목이 ✅ 표시되는지 확인
```

### 2단계: 화면 캡처 테스트
```
1. "화면 캡처 시작" 클릭
2. 권한 허용
3. 플로팅 버튼 표시 확인
4. 디버그 패널에서 "📷 화면캡처: 활성화" 확인
```

### 3단계: 카카오 드라이버 앱 테스트
```
1. 카카오 드라이버 앱 실행
2. 로그인 상태 확인
3. 콜 대기 화면으로 이동
```

### 4단계: Ultimate Bypass 테스트
```
1. 테스트 앱으로 돌아가기
2. "⚡ 완전 자동화 (Ultimate)" 클릭
3. 화면 캡처 권한 허용
4. 앱이 백그라운드로 이동
5. 카카오 드라이버 앱 열기
6. 콜 수신 대기
```

## ⚠️ 주의사항

### Knox 관련
- Galaxy S24 Ultra는 Knox 보안이 강화되어 있음
- 루팅 시 Knox 보증이 영구적으로 무효화됨
- 일부 기능은 Knox 때문에 제한될 수 있음

### 성능 최적화
```bash
# 배터리 최적화 제외
adb shell dumpsys deviceidle whitelist +com.kakao.taxi.test

# 백그라운드 제한 해제
adb shell cmd appops set com.kakao.taxi.test RUN_IN_BACKGROUND allow
```

## 🐛 문제 해결

### 1. "불법 프로그램" 메시지가 뜨는 경우
```
1. 테스트 앱에서 "테스트 모드" 체크박스 활성화
2. Ultimate Bypass 재시작
```

### 2. 화면이 검은색으로 캡처되는 경우
```
1. 개발자 옵션 → 하드웨어 가속 렌더링 → "HW 오버레이 사용 안함" 활성화
2. 카카오 드라이버 앱 강제 종료 후 재시작
```

### 3. 터치가 작동하지 않는 경우
```bash
# ADB로 터치 테스트
adb shell input tap 500 1000

# sendevent 권한 확인
adb shell ls -la /dev/input/event*
```

### 4. 접근성 서비스가 자동으로 꺼지는 경우
```bash
# 접근성 서비스 강제 활성화
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService

adb shell settings put secure accessibility_enabled 1
```

## 📊 로그 수집

### ADB 로그
```bash
# 전체 로그
adb logcat > test_log.txt

# 필터링된 로그
adb logcat -s "MainActivity:*" "UltimateBypass:*" "DeepLevelAuto:*"
```

### 앱 내부 로그
```
1. "실시간 로그 보기" 버튼 클릭
2. 화면 캡처하여 저장
```

## 🔄 자동화 테스트 스크립트

### test_automation.sh
```bash
#!/bin/bash

# APK 설치
adb install -r app-debug.apk

# 권한 부여
adb shell pm grant com.kakao.taxi.test android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.kakao.taxi.test android.permission.WRITE_SECURE_SETTINGS

# 접근성 서비스 활성화
adb shell settings put secure enabled_accessibility_services com.kakao.taxi.test/com.kakao.taxi.test.service.KakaoTaxiAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 앱 실행
adb shell am start -n com.kakao.taxi.test/.MainActivity

# Ultimate Bypass 시작 (5초 대기 후)
sleep 5
adb shell input tap 540 2200  # Ultimate Bypass 버튼 좌표 (조정 필요)

echo "테스트 준비 완료!"
```

## 📱 실제 테스트 시나리오

### 시나리오 1: 일반 콜 수락
```
1. Ultimate Bypass 활성화
2. 카카오 드라이버 앱에서 대기
3. 테스트 콜 수신
4. 자동 수락 확인
```

### 시나리오 2: 고액 콜 필터링
```
1. 앱 설정에서 최소 금액 설정 (예: 30,000원)
2. Ultimate Bypass 활성화
3. 다양한 금액의 콜 테스트
4. 조건에 맞는 콜만 수락되는지 확인
```

### 시나리오 3: 장시간 안정성
```
1. Ultimate Bypass 활성화
2. 1시간 이상 대기
3. 메모리 사용량 모니터링
4. 크래시 여부 확인
```

## 💡 개발 팁

### 1. 빠른 디버깅
```bash
# 실시간 로그 모니터링
adb logcat | grep -E "Ultimate|Bypass|Deep"

# 화면 녹화 (문제 발생 시)
adb shell screenrecord /sdcard/test.mp4
```

### 2. 성능 프로파일링
```bash
# CPU 사용률 확인
adb shell top -n 1 | grep com.kakao

# 메모리 사용량
adb shell dumpsys meminfo com.kakao.taxi.test
```

### 3. 네트워크 디버깅
```bash
# 네트워크 요청 모니터링 (Charles Proxy 사용)
adb shell settings put global http_proxy 192.168.1.100:8888
```

## 🎯 체크리스트

- [ ] 개발자 옵션 활성화
- [ ] USB 디버깅 활성화
- [ ] APK 설치 완료
- [ ] 모든 권한 부여
- [ ] 접근성 서비스 활성화
- [ ] 화면 캡처 테스트 통과
- [ ] 플로팅 버튼 정상 작동
- [ ] Ultimate Bypass 실행
- [ ] 카카오 드라이버 앱에서 테스트
- [ ] 자동 콜 수락 확인

## 📞 지원

문제 발생 시:
1. 로그 수집 (adb logcat)
2. 스크린샷 또는 화면 녹화
3. 디바이스 정보 (Android 버전, One UI 버전)
4. 재현 단계 상세 기록