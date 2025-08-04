# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android automation testing application specifically designed for testing the Kakao Taxi Driver app. The project implements multiple sophisticated techniques to bypass security measures and automate call acceptance.

## Build Commands

### Build APK
```bash
# Windows
gradlew.bat clean assembleDebug

# Quick build (without clean)
gradlew.bat assembleDebug
```

### Install APK
```bash
# Direct USB installation
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Full setup with permissions
quick_test.bat
```

### Running Tests
```bash
# Currently no unit tests implemented
# Testing is done through manual APK installation on device
```

## Architecture Overview

### Core Components

1. **MainActivity** (`MainActivity.kt`)
   - Entry point for the application
   - Handles permission requests (MediaProjection, Accessibility, Overlay)
   - Manages service lifecycle
   - Key constants: `REQUEST_MEDIA_PROJECTION = 1001`, `REQUEST_ONE_CLICK_AUTO = 2000`

2. **Service Layer** (`service/`)
   - **ScreenCaptureService**: MediaProjection-based screen capture
   - **KakaoTaxiAccessibilityService**: Main accessibility service for UI interaction
   - **UltimateBypassService**: Advanced bypass techniques combining multiple approaches
   - **FloatingControlService**: Floating UI controls for user interaction
   - **HybridAutomationService**: NotificationListenerService for notification-based automation

3. **Module Layer** (`module/`)
   - **YellowButtonDetector**: Detects Kakao's yellow accept button using color analysis
   - **AdvancedBypassModule**: Implements memory reading, process spoofing, DEX injection
   - **DeepLevelAutomation**: Combines MediaProjection + image processing + multiple click methods
   - **SmartClickSimulator**: Handles click injection through multiple methods

### Security Bypass Architecture

The app implements a multi-layered approach to bypass Kakao Taxi's security:

1. **Level 1 - Standard Approach**
   - AccessibilityService for UI reading
   - MediaProjection for screen capture
   - Standard touch injection

2. **Level 2 - Enhanced Detection**
   - Color-based button detection (yellow pixel scanning)
   - OCR using ML Kit for text extraction
   - Pattern matching for UI elements

3. **Level 3 - Advanced Bypass (UltimateBypassService)**
   - Memory reading from `/proc/{pid}/mem`
   - Runtime method hooking
   - Process disguise techniques
   - Native touch injection via sendevent
   - Dynamic DEX loading

### Key Technical Details

- **Target Package**: `com.kakao.driver`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 33
- **Compile SDK**: 34

### Performance Optimization

The app achieves ~100-150ms response time through:
- Double buffering in ImageReader
- Parallel image processing with coroutines
- Grid-based pixel scanning (5px intervals)
- 50ms touch duration (vs normal 100-200ms)

### Critical Security Considerations

The codebase includes aggressive bypass techniques that may violate terms of service. Key areas:
- `/proc` filesystem manipulation
- SELinux policy modification attempts
- System property spoofing
- Kernel-level bypass attempts

### Device-Specific Notes

**Galaxy S24 Ultra**: 
- Enhanced Knox security may limit some features
- High resolution (3120x1440) requires coordinate adjustments
- One UI 6.1 has stricter permission management

### Important Files for Context

- `kakao_security_analysis.md`: Detailed analysis of Kakao's security measures
- `non_root_bypass_techniques.md`: Technical approaches for non-rooted devices
- `TECHNICAL_DEEP_DIVE.md`: Performance optimization details
- `GALAXY_S24_ULTRA_TEST_GUIDE.md`: Device-specific testing procedures

### Development Workflow

1. Make code changes
2. Build APK: `gradlew.bat assembleDebug`
3. Install and test: `quick_test.bat`
4. Monitor logs: `adb logcat -s "MainActivity:*" "UltimateBypass:*"`

### Key APIs and Libraries

- **ML Kit**: Text recognition (Korean)
- **Shizuku**: Advanced permission management
- **Coroutines**: Async operations
- **MediaProjection**: Screen capture
- **AccessibilityService**: UI automation

### Common Issues and Solutions

1. **"Illegal program" message**: Enable test mode checkbox in app
2. **Black screen capture**: FLAG_SECURE bypass may be needed
3. **Accessibility service disabled**: Force enable via ADB
4. **Touch not working**: Check sendevent permissions

### Testing Approach

The app is tested on real devices, primarily Galaxy S24 Ultra. Key test scenarios:
1. Basic call acceptance
2. High-value call filtering  
3. Long-term stability
4. Security bypass effectiveness