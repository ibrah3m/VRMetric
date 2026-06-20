#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "PerfOverlayNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct OverlayState {
    float positionX;
    float positionY;
    float positionZ;
    float scale;
    bool headLocked;
    float pitch;
    float yaw;
    float distance;
} g_state = {0};

extern "C" JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_PerfDebugOverlay_nativeSetPosition(
    JNIEnv* env, jobject thiz,
    jfloat pitch, jfloat yaw, jfloat distance, jfloat scale) {

    g_state.pitch = pitch;
    g_state.yaw = yaw;
    g_state.distance = distance;
    g_state.scale = scale;

    g_state.positionY = (float)(pitch * M_PI / 180.0);
    g_state.positionX = (float)(yaw * M_PI / 180.0);
    g_state.positionZ = -distance;

    LOGI("Overlay position: pitch=%.1f yaw=%.1f dist=%.1f scale=%.1f",
         pitch, yaw, distance, scale);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_PerfDebugOverlay_nativeSetHeadLocked(
    JNIEnv* env, jobject thiz, jboolean locked) {
    g_state.headLocked = locked;
    LOGI("Head locked: %d", locked);
}
