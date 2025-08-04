# 🎨 추가 가능한 UI 요소들

## 현재 UI (변경 없음)
- 화면 캡처 시작
- 템플릿 매칭 테스트  
- OCR 텍스트 추출 테스트
- 클릭 이벤트 테스트
- 필터링 테스트
- 오버레이 표시
- Mock 콜 화면 테스트
- 디버그 폴더 열기
- 실시간 로그 보기
- 플로팅 컨트롤 표시
- 테스트 모드 체크박스

## 추가하면 좋을 UI 요소들

### 1. 초고속 모드 버튼
```xml
<Button
    android:id="@+id/btnFastMode"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="⚡ 초고속 모드 (0.1초)"
    android:textAllCaps="false"
    android:backgroundTint="#FF0000"
    android:layout_marginBottom="8dp" />
```

### 2. 자가 진단 버튼
```xml
<Button
    android:id="@+id/btnSelfDiagnose"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="🏥 자가 진단"
    android:textAllCaps="false"
    android:backgroundTint="#2196F3"
    android:layout_marginBottom="8dp" />
```

### 3. 필터 설정 섹션
```xml
<LinearLayout
    android:id="@+id/filterSection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#F5F5F5"
    android:padding="8dp"
    android:layout_marginBottom="8dp">
    
    <TextView
        android:text="필터 설정"
        android:textStyle="bold"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
        
    <EditText
        android:id="@+id/etMinAmount"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="최소 금액 (원)"
        android:inputType="number" />
        
    <EditText
        android:id="@+id/etMaxDistance"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="최대 거리 (km)"
        android:inputType="numberDecimal" />
</LinearLayout>
```

### 4. 성능 모니터링 뷰
```xml
<TextView
    android:id="@+id/tvPerformance"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="⚡ 성능: -- FPS | 지연: -- ms"
    android:textSize="12sp"
    android:background="#E8F5E9"
    android:padding="4dp" />
```

### 5. 플로팅 디버그 토글
```xml
<CheckBox
    android:id="@+id/chkFloatingDebug"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="플로팅 디버그 창 표시"
    android:textColor="#9C27B0" />
```

## MainActivity.kt에 추가할 코드

```kotlin
// 초고속 모드
findViewById<Button>(R.id.btnFastMode)?.setOnClickListener {
    val intent = Intent(this, FastCallDetectionService::class.java)
    startService(intent)
    addLog("⚡ 초고속 모드 시작 (0.1초 간격)")
}

// 자가 진단
findViewById<Button>(R.id.btnSelfDiagnose)?.setOnClickListener {
    runSelfDiagnosis()
}

// 필터 설정 저장
findViewById<EditText>(R.id.etMinAmount)?.addTextChangedListener {
    saveFilterSettings()
}

// 성능 모니터링
private fun updatePerformanceMonitor() {
    findViewById<TextView>(R.id.tvPerformance)?.text = 
        "⚡ 성능: ${FastCallDetectionService.getFPS()} FPS | 지연: ${getAverageLatency()} ms"
}
```

현재 UI는 기본 기능만 있고, 새로 추가한 초고속 모드나 시각적 디버깅 기능의 UI는 없습니다.