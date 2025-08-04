#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <linux/input.h>
#include <string.h>
#include <stdio.h>

#define LOG_TAG "NativeHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * 네이티브 레벨에서 직접 터치 이벤트 주입
 */
JNIEXPORT jboolean JNICALL
Java_com_kakao_taxi_test_module_NativeHook_injectTouchNative(
    JNIEnv *env, jobject thiz, jint x, jint y) {
    
    // 모든 input event 디바이스 시도
    for (int i = 0; i < 20; i++) {
        char device_path[32];
        snprintf(device_path, sizeof(device_path), "/dev/input/event%d", i);
        
        int fd = open(device_path, O_RDWR);
        if (fd < 0) continue;
        
        // 디바이스 이름 확인
        char name[256] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0) {
            LOGI("Device %d: %s", i, name);
            
            // 터치스크린 찾기
            if (strstr(name, "touch") || strstr(name, "Touch") || 
                strstr(name, "screen") || strstr(name, "Screen")) {
                
                LOGI("Found touchscreen at %s", device_path);
                
                struct input_event ev[7];
                memset(ev, 0, sizeof(ev));
                
                // 터치 다운 이벤트
                ev[0].type = EV_ABS;
                ev[0].code = ABS_MT_TRACKING_ID;
                ev[0].value = 1;
                
                ev[1].type = EV_ABS;
                ev[1].code = ABS_MT_POSITION_X;
                ev[1].value = x;
                
                ev[2].type = EV_ABS;
                ev[2].code = ABS_MT_POSITION_Y;
                ev[2].value = y;
                
                ev[3].type = EV_ABS;
                ev[3].code = ABS_MT_PRESSURE;
                ev[3].value = 50;
                
                ev[4].type = EV_ABS;
                ev[4].code = ABS_MT_TOUCH_MAJOR;
                ev[4].value = 5;
                
                ev[5].type = EV_SYN;
                ev[5].code = SYN_MT_REPORT;
                ev[5].value = 0;
                
                ev[6].type = EV_SYN;
                ev[6].code = SYN_REPORT;
                ev[6].value = 0;
                
                // 이벤트 쓰기
                if (write(fd, ev, sizeof(ev)) == sizeof(ev)) {
                    LOGI("Touch down injected at (%d, %d)", x, y);
                    
                    usleep(50000); // 50ms 대기
                    
                    // 터치 업 이벤트
                    ev[0].type = EV_ABS;
                    ev[0].code = ABS_MT_TRACKING_ID;
                    ev[0].value = -1;
                    
                    ev[1].type = EV_SYN;
                    ev[1].code = SYN_MT_REPORT;
                    ev[1].value = 0;
                    
                    ev[2].type = EV_SYN;
                    ev[2].code = SYN_REPORT;
                    ev[2].value = 0;
                    
                    if (write(fd, ev, sizeof(struct input_event) * 3) > 0) {
                        LOGI("Touch up injected");
                        close(fd);
                        return JNI_TRUE;
                    }
                }
            }
        }
        close(fd);
    }
    
    LOGE("Failed to inject touch event");
    return JNI_FALSE;
}

/**
 * 메모리 패치를 통한 보안 우회
 */
JNIEXPORT jboolean JNICALL
Java_com_kakao_taxi_test_module_NativeHook_patchSecurityCheck(
    JNIEnv *env, jobject thiz) {
    
    // dlopen으로 카카오 라이브러리 로드
    void* handle = dlopen("libkakao.so", RTLD_NOW);
    if (!handle) {
        handle = dlopen("/data/app/com.kakao.driver*/lib/*/libkakao.so", RTLD_NOW);
    }
    
    if (handle) {
        // 보안 체크 함수들 찾기
        void* checkSecurity = dlsym(handle, "_Z13checkSecurityv");
        void* blockTouch = dlsym(handle, "_Z10blockTouchv");
        void* verifyApp = dlsym(handle, "_Z9verifyAppv");
        
        // 함수 주소를 NOP으로 패치
        if (checkSecurity) {
            // 페이지 권한 변경
            size_t page_size = getpagesize();
            void* page_start = (void*)((uintptr_t)checkSecurity & ~(page_size - 1));
            
            if (mprotect(page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
                // ARM64 NOP instruction: 0xD503201F
                // ARM32 NOP instruction: 0xE320F000
                #ifdef __aarch64__
                    *(uint32_t*)checkSecurity = 0xD503201F;
                    *(uint32_t*)((char*)checkSecurity + 4) = 0xD65F03C0; // RET
                #else
                    *(uint32_t*)checkSecurity = 0xE320F000;
                    *(uint32_t*)((char*)checkSecurity + 4) = 0xE12FFF1E; // BX LR
                #endif
                
                LOGI("Security check patched");
                return JNI_TRUE;
            }
        }
        
        dlclose(handle);
    }
    
    LOGE("Failed to patch security check");
    return JNI_FALSE;
}

/**
 * 시스템 속성 우회
 */
JNIEXPORT void JNICALL
Java_com_kakao_taxi_test_module_NativeHook_bypassSystemProperties(
    JNIEnv *env, jobject thiz) {
    
    // property_set 함수 후킹
    void* handle = dlopen("libc.so", RTLD_NOW);
    if (handle) {
        int (*property_set)(const char*, const char*) = 
            (int (*)(const char*, const char*))dlsym(handle, "property_set");
        
        if (property_set) {
            property_set("debug.layout", "false");
            property_set("debug.screenshot.secure", "0");
            property_set("persist.sys.ui.hw", "1");
            property_set("ro.debuggable", "0");
            LOGI("System properties bypassed");
        }
        
        dlclose(handle);
    }
}

/**
 * 프로세스 메모리 스캔 및 패치
 */
JNIEXPORT void JNICALL
Java_com_kakao_taxi_test_module_NativeHook_scanAndPatchMemory(
    JNIEnv *env, jobject thiz, jint pid) {
    
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
    
    FILE* maps = fopen(maps_path, "r");
    if (!maps) return;
    
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, end;
        char perms[5];
        
        if (sscanf(line, "%lx-%lx %s", &start, &end, perms) == 3) {
            // 실행 가능한 영역만 검사
            if (perms[2] == 'x') {
                // 보안 체크 패턴 검색
                unsigned char* mem = (unsigned char*)start;
                size_t size = end - start;
                
                // "isSecurityEnabled" 문자열 찾기
                const char* pattern = "isSecurityEnabled";
                for (size_t i = 0; i < size - strlen(pattern); i++) {
                    if (memcmp(mem + i, pattern, strlen(pattern)) == 0) {
                        // 찾으면 "isSecurityDisabled"로 변경
                        if (mprotect((void*)start, size, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
                            memcpy(mem + i, "isSecurityDisabled", strlen(pattern));
                            LOGI("Patched security string at %lx", start + i);
                        }
                    }
                }
            }
        }
    }
    
    fclose(maps);
}

/**
 * 커널 모듈 로드 (루트 필요)
 */
JNIEXPORT jboolean JNICALL
Java_com_kakao_taxi_test_module_NativeHook_loadKernelModule(
    JNIEnv *env, jobject thiz, jstring modulePath) {
    
    const char* path = env->GetStringUTFChars(modulePath, nullptr);
    
    // insmod 명령 실행
    char command[256];
    snprintf(command, sizeof(command), "insmod %s", path);
    
    int result = system(command);
    
    env->ReleaseStringUTFChars(modulePath, path);
    
    if (result == 0) {
        LOGI("Kernel module loaded successfully");
        return JNI_TRUE;
    } else {
        LOGE("Failed to load kernel module");
        return JNI_FALSE;
    }
}

} // extern "C"