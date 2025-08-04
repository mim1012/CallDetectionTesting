# 📱 완전 자동화 시스템 테스트 절차서

## 🧪 1단계: 기본 환경 테스트

### APK 빌드 및 설치 테스트
```cmd
# 1. 프로젝트 빌드
cd "D:\Project\KaKao Ventical"
gradlew.bat clean assembleDebug

# 2. 디바이스 연결 확인
adb devices

# 3. APK 설치
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4. 앱 실행 확인
adb shell am start -n com.kakao.taxi.test/.MainActivity
```

### 권한 상태 테스트
```cmd
# 접근성 서비스 상태 확인
adb shell settings get secure enabled_accessibility_services

# 오버레이 권한 확인  
adb shell appops get com.kakao.taxi.test SYSTEM_ALERT_WINDOW

# 배터리 최적화 상태 확인
adb shell dumpsys deviceidle whitelist
```

## 🔍 2단계: 개별 모듈 테스트

### A. VirtualEnvironmentBypass 테스트
```kotlin
// 테스트 시나리오 1: 보안 검사 우회 확인
fun testVirtualBypass() {
    // 1. VirtualApp 초기화 테스트
    val bypass = VirtualEnvironmentBypass(context)
    val initResult = bypass.initialize()
    
    // 예상 결과: true 반환, 로그에 "✅ VirtualApp 환경 초기화 완료!" 출력
    assert(initResult == true)
    
    // 2. 보안 검사 후킹 테스트
    val beforeHook = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    bypass.executeInVirtualEnvironment {
        val afterHook = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        // 예상 결과: 빈 문자열 반환 (후킹된 상태)
        assert(afterHook == "")
    }
    
    // 3. 상태 확인 테스트
    val status = bypass.getStatus()
    // 예상 결과: "✅ VirtualApp 우회 활성 (X개 메서드 후킹됨)"
    assert(status.contains("VirtualApp 우회 활성"))
}
```

### B. AdvancedScreenAnalyzer 테스트
```kotlin
// 테스트 시나리오 2: ML 화면 분석 성능 테스트
fun testScreenAnalyzer() {
    val analyzer = AdvancedScreenAnalyzer(context)
    
    // 1. 초기화 테스트
    val initResult = analyzer.initialize()
    assert(initResult == true)
    
    // 2. 가짜 MediaProjection으로 분석 테스트
    val mockMediaProjection = createMockMediaProjection()
    analyzer.startScreenCapture(mockMediaProjection)
    
    // 3. 테스트 이미지로 분석 성능 측정
    val testBitmap = loadTestBitmap("mock_kakao_call_screen.png")
    val startTime = System.currentTimeMillis()
    
    // 분석 실행
    runBlocking {
        val result = analyzer.performAdvancedAnalysis(testBitmap)
        val analysisTime = System.currentTimeMillis() - startTime
        
        // 예상 결과: 100ms 이내 완료, 85% 이상 신뢰도
        assert(analysisTime <= 100)
        assert(result.confidence >= 0.85f)
        assert(result.findHighFareCall() != null)
        assert(result.findAcceptButton() != null)
    }
    
    // 4. 상태 확인
    assert(analyzer.isHealthy() == true)
}
```

### C. AdvancedClickExecutor 테스트
```kotlin
// 테스트 시나리오 3: 다중 클릭 방법 테스트
fun testClickExecutor() {
    val executor = AdvancedClickExecutor(context)
    executor.initialize()
    
    // 1. 각 클릭 방법별 가용성 테스트
    val instrumentation = InstrumentationClicker()
    val root = RootClicker()
    val accessibility = AccessibilityClicker(context)
    
    Log.d("TEST", "Instrumentation 사용 가능: ${instrumentation.isAvailable()}")
    Log.d("TEST", "Root 사용 가능: ${root.isAvailable()}")
    Log.d("TEST", "Accessibility 사용 가능: ${accessibility.isAvailable()}")
    
    // 2. 실제 클릭 테스트 (화면 중앙)
    val displayMetrics = context.resources.displayMetrics
    val centerX = displayMetrics.widthPixels / 2f
    val centerY = displayMetrics.heightPixels / 2f
    
    runBlocking {
        val success = executor.performNaturalClick(centerX, centerY)
        // 예상 결과: 최소 1개 방법으로 성공
        assert(success == true)
    }
    
    // 3. 성공률 통계 확인
    val statistics = executor.getStatistics()
    Log.d("TEST", "클릭 통계: $statistics")
}
```

### D. NetworkTrafficAnalyzer 테스트
```kotlin
// 테스트 시나리오 4: 네트워크 트래픽 분석 테스트
fun testNetworkAnalyzer() {
    val analyzer = NetworkTrafficAnalyzer(context)
    analyzer.initialize()
    
    // 1. 가짜 패킷 분석 테스트
    val fakePacket = createFakeKakaoApiPacket()
    analyzer.analyzePacket(fakePacket)
    
    // 2. 콜 예측 생성 확인  
    Thread.sleep(1000) // 분석 완료 대기
    val predictions = analyzer.getCallPredictions()
    
    // 예상 결과: 최소 1개 예측 생성
    assert(predictions.isNotEmpty())
    
    val highFarePrediction = predictions.find { it.isHighFare }
    assert(highFarePrediction != null)
    assert(highFarePrediction!!.confidence >= 0.7f)
    
    // 3. 네트워크 통계 확인
    val stats = analyzer.getNetworkStatistics()
    Log.d("TEST", "네트워크 통계: $stats")
}

fun createFakeKakaoApiPacket(): ByteArray {
    val fakeApiResponse = """
        {
            "calls": [
                {
                    "id": "test_call_123",
                    "fare": 95000,
                    "distance": 15.5,
                    "departure": "강남역",
                    "destination": "인천공항",
                    "timestamp": ${System.currentTimeMillis()}
                }
            ]
        }
    """.trimIndent()
    
    return fakeApiResponse.toByteArray(StandardCharsets.UTF_8)
}
```

## 🎯 3단계: 통합 시스템 테스트

### 전체 자동화 프로세스 테스트
```cmd
# 1. 로그 모니터링 시작
adb logcat -s "UltimateOrchestrator:*" "VirtualBypass:*" "ScreenAnalyzer:*" "ClickExecutor:*" "NetworkAnalyzer:*"

# 2. 완전 자동화 시작
adb shell am start -n com.kakao.taxi.test/.MainActivity
# 앱에서 "🚀 완전 자동화" 버튼 클릭

# 3. 카카오 택시 앱 실행
adb shell am start -n com.kakao.driver/.MainActivity

# 4. Mock 콜 생성 (테스트용)
adb shell am broadcast -a com.kakao.taxi.test.MOCK_HIGH_FARE_CALL \
    --es fare "120000" \
    --es departure "강남역" \
    --es destination "인천공항"
```

## 🔍 4단계: 실시간 모니터링 및 디버깅

### 시스템 상태 실시간 확인
```cmd
# 1. 메모리 사용량 모니터링
adb shell dumpsys meminfo com.kakao.taxi.test

# 2. CPU 사용률 확인
adb shell top -p `adb shell pidof com.kakao.taxi.test`

# 3. 네트워크 트래픽 모니터링
adb shell netstat -i

# 4. 서비스 상태 확인
adb shell dumpsys activity services com.kakao.taxi.test
```

### 로그 분석 패턴
```
성공적인 자동화 로그 예시:
🚀 궁극의 자동화 오케스트레이터 시작...
🥷 VirtualApp 환경 초기화 시작...
✅ VirtualApp 환경 초기화 완료!
🔍 고급 화면 분석 시스템 초기화...
✅ 고급 화면 분석 시스템 초기화 완료!
🎯 고급 클릭 실행기 초기화...
✅ 고급 클릭 실행기 초기화 완료!
🌐 네트워크 트래픽 분석기 초기화...
✅ 네트워크 트래픽 분석기 초기화 완료!
🎯 메인 오케스트레이션 루프 시작
🎯 고요금 콜 예측: 강남역→인천공항 (95000원, 신뢰도 92%)
🎯 콜 수락 시도: 강남역→인천공항 (95000원, 신뢰도 92%)
✅ Instrumentation 클릭 성공
✅ 콜 수락 성공!
```

## ⚠️ 5단계: 문제 해결 및 트러블슈팅

### 일반적인 문제와 해결책
```cmd
# 문제 1: VirtualApp 초기화 실패
로그: "❌ VirtualApp 초기화 실패"
해결: adb shell pm clear com.kakao.taxi.test && 앱 재설치

# 문제 2: 화면 분석 시스템 불안정
로그: "화면 분석 시스템 불안정, 전략 변경"
해결: MediaProjection 권한 재부여 또는 디바이스 재부팅

# 문제 3: 클릭 모든 방법 실패
로그: "모든 클릭 시도 실패"
해결: 접근성 서비스 재활성화 또는 Root 권한 확인

# 문제 4: 네트워크 분석 불가
로그: "네트워크 분석 시스템 불안정"
해결: VPN 권한 부여 또는 WiFi 재연결
```