#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <cstring>
#include <cstdlib>

#define LOG_TAG "NativeOverlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum Backend {
    VRAPI_GLES = 0,
    OPENXR_VULKAN = 1
};

static EGLDisplay sEglDisplay = EGL_NO_DISPLAY;
static EGLContext sEglContext = EGL_NO_CONTEXT;
static EGLSurface sEglSurface = EGL_NO_SURFACE;
static GLuint sTextureId = 0;
static GLuint sProgram = 0;
static GLuint sVao = 0;
static GLuint sVbo = 0;
static int sTextureWidth = 256;
static int sTextureHeight = 136;
static Backend sBackend = OPENXR_VULKAN;
static bool sInitialized = false;

static float sPitch = 0.0f;
static float sYaw = 0.0f;
static float sScale = 1.0f;
static float sDistance = 1.0f;
static bool sHeadLocked = false;
static bool sCaptureAllowed = false;

static JavaVM* sJvm = nullptr;
static jclass sJavaRendererClass = nullptr;

static const char* kVertShader = R"(
#version 300 es
in vec2 aPos;
in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* kFragShader = R"(
#version 300 es
precision highp float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
out vec4 fragColor;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
)";

static bool initEgl() {
    sEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (sEglDisplay == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    if (!eglInitialize(sEglDisplay, nullptr, nullptr)) {
        LOGE("eglInitialize failed");
        return false;
    }

    EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(sEglDisplay, configAttribs, &config, 1, &numConfigs)) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };

    sEglContext = eglCreateContext(sEglDisplay, config, EGL_NO_CONTEXT, contextAttribs);
    if (sEglContext == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return false;
    }

    EGLint surfaceAttribs[] = {
        EGL_WIDTH, sTextureWidth,
        EGL_HEIGHT, sTextureHeight,
        EGL_NONE
    };

    sEglSurface = eglCreatePbufferSurface(sEglDisplay, config, surfaceAttribs);
    if (sEglSurface == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed");
        eglDestroyContext(sEglDisplay, sEglContext);
        sEglContext = EGL_NO_CONTEXT;
        return false;
    }

    if (!eglMakeCurrent(sEglDisplay, sEglSurface, sEglSurface, sEglContext)) {
        LOGE("eglMakeCurrent failed");
        eglDestroySurface(sEglDisplay, sEglSurface);
        eglDestroyContext(sEglDisplay, sEglContext);
        sEglSurface = EGL_NO_SURFACE;
        sEglContext = EGL_NO_CONTEXT;
        return false;
    }

    return true;
}

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        GLchar infoLog[512];
        glGetShaderInfoLog(shader, sizeof(infoLog), nullptr, infoLog);
        LOGE("Shader compile error: %s", infoLog);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static bool initGl() {
    GLuint vertShader = compileShader(GL_VERTEX_SHADER, kVertShader);
    GLuint fragShader = compileShader(GL_FRAGMENT_SHADER, kFragShader);
    if (!vertShader || !fragShader) {
        LOGE("Failed to compile shaders");
        if (vertShader) glDeleteShader(vertShader);
        if (fragShader) glDeleteShader(fragShader);
        return false;
    }

    sProgram = glCreateProgram();
    glAttachShader(sProgram, vertShader);
    glAttachShader(sProgram, fragShader);
    glLinkProgram(sProgram);

    GLint success;
    glGetProgramiv(sProgram, GL_LINK_STATUS, &success);
    if (!success) {
        GLchar infoLog[512];
        glGetProgramInfoLog(sProgram, sizeof(infoLog), nullptr, infoLog);
        LOGE("Program link error: %s", infoLog);
        glDeleteProgram(sProgram);
        sProgram = 0;
        glDeleteShader(vertShader);
        glDeleteShader(fragShader);
        return false;
    }

    glDeleteShader(vertShader);
    glDeleteShader(fragShader);

    float vertices[] = {
        -1.0f,  1.0f,   0.0f, 1.0f,
        -1.0f, -1.0f,   0.0f, 0.0f,
         1.0f, -1.0f,   1.0f, 0.0f,
        -1.0f,  1.0f,   0.0f, 1.0f,
         1.0f, -1.0f,   1.0f, 0.0f,
         1.0f,  1.0f,   1.0f, 1.0f,
    };

    glGenVertexArrays(1, &sVao);
    glBindVertexArray(sVao);

    glGenBuffers(1, &sVbo);
    glBindBuffer(GL_ARRAY_BUFFER, sVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    GLint aPos = glGetAttribLocation(sProgram, "aPos");
    glEnableVertexAttribArray(aPos);
    glVertexAttribPointer(aPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);

    GLint aTexCoord = glGetAttribLocation(sProgram, "aTexCoord");
    glEnableVertexAttribArray(aTexCoord);
    glVertexAttribPointer(aTexCoord, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));

    glBindVertexArray(0);

    glGenTextures(1, &sTextureId);
    glBindTexture(GL_TEXTURE_2D, sTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, sTextureWidth, sTextureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    return true;
}

static void destroyGl() {
    if (sTextureId) {
        glDeleteTextures(1, &sTextureId);
        sTextureId = 0;
    }
    if (sVao) {
        glDeleteVertexArrays(1, &sVao);
        sVao = 0;
    }
    if (sVbo) {
        glDeleteBuffers(1, &sVbo);
        sVbo = 0;
    }
    if (sProgram) {
        glDeleteProgram(sProgram);
        sProgram = 0;
    }
}

static void destroyEgl() {
    if (sEglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(sEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (sEglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(sEglDisplay, sEglSurface);
            sEglSurface = EGL_NO_SURFACE;
        }
        if (sEglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(sEglDisplay, sEglContext);
            sEglContext = EGL_NO_CONTEXT;
        }
        eglTerminate(sEglDisplay);
        sEglDisplay = EGL_NO_DISPLAY;
    }
}

static void nativeDestroy() {
    if (!sInitialized) return;
    LOGI("Destroying native renderer");
    destroyGl();
    destroyEgl();

    if (sJavaRendererClass != nullptr && sJvm != nullptr) {
        JNIEnv* env = nullptr;
        if (sJvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(sJavaRendererClass);
            sJavaRendererClass = nullptr;
        }
    }

    sInitialized = false;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_init(
        JNIEnv* env, jclass clazz, jobject context, jint textureWidth, jint textureHeight, jint backend) {
    (void)context;

    if (sJvm == nullptr) {
        env->GetJavaVM(&sJvm);
        jclass localClass = env->GetObjectClass(clazz);
        sJavaRendererClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        env->DeleteLocalRef(localClass);
    }

    if (sInitialized) {
        LOGW("NativeRenderer already initialized, destroying first");
        nativeDestroy();
    }

    sTextureWidth = textureWidth;
    sTextureHeight = textureHeight;
    sBackend = static_cast<Backend>(backend);

    LOGI("Initializing native renderer: %dx%d backend=%d", sTextureWidth, sTextureHeight, (int)sBackend);

    if (!initEgl()) {
        LOGE("Failed to initialize EGL");
        return;
    }

    if (!initGl()) {
        LOGE("Failed to initialize OpenGL");
        destroyEgl();
        return;
    }

    sInitialized = true;
    LOGI("Native renderer initialized successfully");
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_destroy(
        JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    nativeDestroy();
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_updateTexture(
        JNIEnv* env, jclass clazz, jintArray textureArray, jint textureWidth, jint textureHeight,
        jint displayedWidth, jint displayedHeight, jint textureUpdateCount) {
    (void)clazz;
    (void)displayedWidth;
    (void)displayedHeight;
    (void)textureUpdateCount;

    if (!sInitialized || !sTextureId) return;

    if (textureArray == nullptr) return;

    jint* pixels = env->GetIntArrayElements(textureArray, nullptr);
    if (pixels == nullptr) return;

    glBindTexture(GL_TEXTURE_2D, sTextureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, textureWidth, textureHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glBindTexture(GL_TEXTURE_2D, 0);

    env->ReleaseIntArrayElements(textureArray, pixels, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_update(
        JNIEnv* env, jclass clazz, jfloat pitch, jfloat yaw, jfloat scale, jfloat distance,
        jboolean headLocked, jboolean captureAllowed) {
    (void)env;
    (void)clazz;

    if (!sInitialized) return;

    sPitch = pitch;
    sYaw = yaw;
    sScale = scale;
    sDistance = distance;
    sHeadLocked = headLocked != JNI_FALSE;
    sCaptureAllowed = captureAllowed != JNI_FALSE;

    if (!eglMakeCurrent(sEglDisplay, sEglSurface, sEglSurface, sEglContext)) {
        LOGE("eglMakeCurrent failed in update");
        return;
    }

    glViewport(0, 0, sTextureWidth, sTextureHeight);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(sProgram);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sTextureId);
    GLint texLoc = glGetUniformLocation(sProgram, "uTexture");
    glUniform1i(texLoc, 0);

    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glBindVertexArray(0);

    glUseProgram(0);

    eglSwapBuffers(sEglDisplay, sEglSurface);
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_nativeSetOverlayFrameRateHint(
        JNIEnv* env, jclass clazz, jshort frameRate) {
    (void)env;
    (void)clazz;
    LOGI("Overlay frame rate hint: %d", (int)frameRate);
}

}
