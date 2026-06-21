#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>

#include <cstring>
#include <cstdlib>
#include <thread>
#include <chrono>
#include <vector>
#include <mutex>
#include <atomic>

#define LOG_TAG "NativeOverlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ---- Minimal OpenXR type definitions (no header dependency) ----
typedef uint64_t XrInstance;
typedef uint64_t XrSession;
typedef uint64_t XrSystemId;
typedef uint64_t XrSpace;
typedef uint64_t XrSwapchain;
typedef int32_t XrResult;
typedef uint32_t XrBool32;
typedef int64_t XrStructureType;
typedef uint64_t XrFlags64;

#define XR_SUCCESS 0
#define XR_ERROR_INITIALIZATION_FAILED (-6)
#define XR_ERROR_RUNTIME_FAILURE (-2)
#define XR_SESSION_STATE_READY 4
#define XR_SESSION_STATE_STOPPED 6
#define XR_SESSION_STATE_EXITING 8
#define XR_SESSION_STATE_LOSS_PENDING 7
#define XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY 1
#define XR_REFERENCE_SPACE_TYPE_LOCAL 1
#define XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND 3
#define XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT 0x4
#define XR_SWAPCHAIN_USAGE_SAMPLED_BIT 0x10
#define XR_GL_SRGB8_ALPHA8_INTERNAL_FORMAT 0x8C43
#define XR_TYPE_COMPOSITION_LAYER_QUAD 17
#define XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT 2

// OpenXR function pointer types
typedef XrResult (*PFN_xrCreateInstance)(const void*, XrInstance*);
typedef XrResult (*PFN_xrDestroyInstance)(XrInstance);
typedef XrResult (*PFN_xrGetInstanceProcAddr)(XrInstance, const char*, void**);
typedef XrResult (*PFN_xrEnumerateInstanceExtensionProperties)(const char*, uint32_t*, void*);
typedef XrResult (*PFN_xrGetSystem)(XrInstance, const void*, XrSystemId*);
typedef XrResult (*PFN_xrCreateSession)(XrInstance, const void*, XrSession*);
typedef XrResult (*PFN_xrDestroySession)(XrSession);
typedef XrResult (*PFN_xrCreateReferenceSpace)(XrSession, const void*, XrSpace*);
typedef XrResult (*PFN_xrDestroySpace)(XrSpace);
typedef XrResult (*PFN_xrCreateSwapchain)(XrSession, const void*, XrSwapchain*);
typedef XrResult (*PFN_xrDestroySwapchain)(XrSwapchain);
typedef XrResult (*PFN_xrEnumerateSwapchainImages)(XrSwapchain, uint32_t*, void*);
typedef XrResult (*PFN_xrAcquireSwapchainImage)(XrSwapchain, const void*, uint32_t*);
typedef XrResult (*PFN_xrWaitSwapchainImage)(XrSwapchain, const void*);
typedef XrResult (*PFN_xrReleaseSwapchainImage)(XrSwapchain, const void*);
typedef XrResult (*PFN_xrPollEvent)(XrInstance, void*);
typedef XrResult (*PFN_xrWaitFrame)(XrSession, const void*, void*);
typedef XrResult (*PFN_xrBeginFrame)(XrSession, const void*);
typedef XrResult (*PFN_xrEndFrame)(XrSession, const void*);
typedef XrResult (*PFN_xrBeginSession)(XrSession, const void*);
typedef XrResult (*PFN_xrEndSession)(XrSession);

// ---- Minimal struct layouts matching OpenXR ABI on AArch64 ----
// OpenXR uses natural alignment on 64-bit platforms

struct XrInstanceCreateInfo {
    int64_t type;
    void* next;
    uint64_t createFlags;
    char applicationName[128];
    uint64_t applicationApiVersion;
    char engineName[128];
    uint64_t engineVersion;
    uint32_t enabledApiLayerCount;
    uint32_t _pad1;
    const char* const* enabledApiLayerNames;
    uint32_t enabledExtensionCount;
    uint32_t _pad2;
    const char* const* enabledExtensionNames;
};

struct XrSystemGetInfo {
    int64_t type;
    void* next;
    int32_t formFactor;
    int32_t _pad1;
};

struct XrSessionCreateInfo {
    int64_t type;
    void* next;
    uint64_t createFlags;
    uint64_t systemId;
};

struct XrReferenceSpaceCreateInfo {
    int64_t type;
    void* next;
    int32_t referenceSpaceType;
    int32_t _pad1;
    struct { float x, y, z, w; } orientation;
    struct { float x, y, z; } position;
};

struct XrSwapchainCreateInfo {
    int64_t type;
    void* next;
    uint32_t createFlags;
    uint32_t usageFlags;
    int32_t format;
    uint32_t sampleCount;
    uint32_t width;
    uint32_t height;
    uint32_t faceCount;
    uint32_t arraySize;
    uint32_t mipCount;
};

struct XrSwapchainImageGLES {
    int64_t type;
    void* next;
    uint32_t image;
    uint32_t _pad1;
};

struct XrEventDataBuffer {
    int64_t type;
    void* next;
    uint8_t varying[4000];
};

struct XrFrameState {
    int64_t type;
    void* next;
    int64_t predictedDisplayTime;
    int64_t predictedDisplayPeriod;
    uint32_t shouldRender;
    uint32_t _pad1;
};

struct XrFrameBeginInfo {
    int64_t type;
    void* next;
};

struct XrCompositionLayerBaseHeader {
    int64_t type;
    void* next;
    uint32_t layerFlags;
    uint32_t _pad1;
    uint64_t space;
};

struct XrCompositionLayerQuad {
    XrCompositionLayerBaseHeader base;
    uint32_t eyeVisibility;
    uint32_t _pad1;
    struct { float x, y, z, w; } orientation;
    struct { float x, y, z; } position;
    struct { float width, height; } size;
    uint64_t swapchain;
    uint32_t swapchainImageIndex;
    uint32_t _pad2;
};

struct XrFrameEndInfo {
    int64_t type;
    void* next;
    int64_t displayTime;
    uint32_t environmentBlendMode;
    uint32_t layerCount;
    const void* const* layers;
};

// ---- Global state ----
static void* sOpenXrLib = nullptr;
static XrInstance sInstance = 0;
static XrSession sSession = 0;
static XrSystemId sSystemId = 0;
static XrSpace sSpace = 0;
static XrSwapchain sSwapchain = 0;
static std::vector<XrSwapchainImageGLES> sSwapchainImages;
static uint32_t sSwapchainImageCount = 0;

static EGLDisplay sEglDisplay = EGL_NO_DISPLAY;
static EGLContext sEglContext = EGL_NO_CONTEXT;
static EGLSurface sEglSurface = EGL_NO_SURFACE;

static GLuint sTextureId = 0;
static GLuint sProgram = 0;
static GLuint sVao = 0;
static GLuint sVbo = 0;

static int sTextureWidth = 256;
static int sTextureHeight = 136;

static std::mutex sMutex;
static std::atomic<bool> sInitialized{false};
static std::atomic<bool> sShutdownRequested{false};
static std::atomic<uint32_t> sSessionState{0};
static std::atomic<int> sTextureUpdateCount{0};

static float sPitch = 0.0f;
static float sYaw = 0.0f;
static float sScale = 1.0f;
static float sDistance = 1.0f;
static bool sHeadLocked = false;

// OpenXR function pointers
static PFN_xrCreateInstance p_xrCreateInstance = nullptr;
static PFN_xrDestroyInstance p_xrDestroyInstance = nullptr;
static PFN_xrGetInstanceProcAddr p_xrGetInstanceProcAddr = nullptr;
static PFN_xrEnumerateInstanceExtensionProperties p_xrEnumerateInstanceExtensionProperties = nullptr;
static PFN_xrGetSystem p_xrGetSystem = nullptr;
static PFN_xrCreateSession p_xrCreateSession = nullptr;
static PFN_xrDestroySession p_xrDestroySession = nullptr;
static PFN_xrCreateReferenceSpace p_xrCreateReferenceSpace = nullptr;
static PFN_xrDestroySpace p_xrDestroySpace = nullptr;
static PFN_xrCreateSwapchain p_xrCreateSwapchain = nullptr;
static PFN_xrDestroySwapchain p_xrDestroySwapchain = nullptr;
static PFN_xrEnumerateSwapchainImages p_xrEnumerateSwapchainImages = nullptr;
static PFN_xrAcquireSwapchainImage p_xrAcquireSwapchainImage = nullptr;
static PFN_xrWaitSwapchainImage p_xrWaitSwapchainImage = nullptr;
static PFN_xrReleaseSwapchainImage p_xrReleaseSwapchainImage = nullptr;
static PFN_xrPollEvent p_xrPollEvent = nullptr;
static PFN_xrWaitFrame p_xrWaitFrame = nullptr;
static PFN_xrBeginFrame p_xrBeginFrame = nullptr;
static PFN_xrEndFrame p_xrEndFrame = nullptr;
static PFN_xrBeginSession p_xrBeginSession = nullptr;
static PFN_xrEndSession p_xrEndSession = nullptr;

// ---- Shader sources ----
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

// ---- EGL initialization ----
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

    LOGI("EGL initialized successfully");
    return true;
}

// ---- GL shader/program setup ----
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

    LOGI("OpenGL initialized successfully");
    return true;
}

// ---- Load OpenXR dynamically ----
static bool loadOpenXr() {
    sOpenXrLib = dlopen("libopenxr_loader.so", RTLD_NOW);
    if (!sOpenXrLib) {
        LOGE("Failed to load libopenxr_loader.so: %s", dlerror());
        return false;
    }

    p_xrGetInstanceProcAddr = (PFN_xrGetInstanceProcAddr)dlsym(sOpenXrLib, "xrGetInstanceProcAddr");
    if (!p_xrGetInstanceProcAddr) {
        LOGE("Failed to find xrGetInstanceProcAddr: %s", dlerror());
        return false;
    }

    p_xrCreateInstance = (PFN_xrCreateInstance)dlsym(sOpenXrLib, "xrCreateInstance");
    if (!p_xrCreateInstance) {
        LOGE("Failed to find xrCreateInstance: %s", dlerror());
        return false;
    }

    p_xrEnumerateInstanceExtensionProperties = (PFN_xrEnumerateInstanceExtensionProperties)dlsym(sOpenXrLib, "xrEnumerateInstanceExtensionProperties");
    if (!p_xrEnumerateInstanceExtensionProperties) {
        LOGE("Failed to find xrEnumerateInstanceExtensionProperties: %s", dlerror());
        return false;
    }

    LOGI("OpenXR loader loaded successfully");
    return true;
}

static bool loadXrFunctions() {
    p_xrDestroyInstance = (PFN_xrDestroyInstance)p_xrGetInstanceProcAddr(sInstance, "xrDestroyInstance", (void**)&p_xrDestroyInstance);
    p_xrGetSystem = (PFN_xrGetSystem)p_xrGetInstanceProcAddr(sInstance, "xrGetSystem", (void**)&p_xrGetSystem);
    p_xrCreateSession = (PFN_xrCreateSession)p_xrGetInstanceProcAddr(sInstance, "xrCreateSession", (void**)&p_xrCreateSession);
    p_xrDestroySession = (PFN_xrDestroySession)p_xrGetInstanceProcAddr(sInstance, "xrDestroySession", (void**)&p_xrDestroySession);
    p_xrCreateReferenceSpace = (PFN_xrCreateReferenceSpace)p_xrGetInstanceProcAddr(sInstance, "xrCreateReferenceSpace", (void**)&p_xrCreateReferenceSpace);
    p_xrDestroySpace = (PFN_xrDestroySpace)p_xrGetInstanceProcAddr(sInstance, "xrDestroySpace", (void**)&p_xrDestroySpace);
    p_xrCreateSwapchain = (PFN_xrCreateSwapchain)p_xrGetInstanceProcAddr(sInstance, "xrCreateSwapchain", (void**)&p_xrCreateSwapchain);
    p_xrDestroySwapchain = (PFN_xrDestroySwapchain)p_xrGetInstanceProcAddr(sInstance, "xrDestroySwapchain", (void**)&p_xrDestroySwapchain);
    p_xrEnumerateSwapchainImages = (PFN_xrEnumerateSwapchainImages)p_xrGetInstanceProcAddr(sInstance, "xrEnumerateSwapchainImages", (void**)&p_xrEnumerateSwapchainImages);
    p_xrAcquireSwapchainImage = (PFN_xrAcquireSwapchainImage)p_xrGetInstanceProcAddr(sInstance, "xrAcquireSwapchainImage", (void**)&p_xrAcquireSwapchainImage);
    p_xrWaitSwapchainImage = (PFN_xrWaitSwapchainImage)p_xrGetInstanceProcAddr(sInstance, "xrWaitSwapchainImage", (void**)&p_xrWaitSwapchainImage);
    p_xrReleaseSwapchainImage = (PFN_xrReleaseSwapchainImage)p_xrGetInstanceProcAddr(sInstance, "xrReleaseSwapchainImage", (void**)&p_xrReleaseSwapchainImage);
    p_xrPollEvent = (PFN_xrPollEvent)p_xrGetInstanceProcAddr(sInstance, "xrPollEvent", (void**)&p_xrPollEvent);
    p_xrWaitFrame = (PFN_xrWaitFrame)p_xrGetInstanceProcAddr(sInstance, "xrWaitFrame", (void**)&p_xrWaitFrame);
    p_xrBeginFrame = (PFN_xrBeginFrame)p_xrGetInstanceProcAddr(sInstance, "xrBeginFrame", (void**)&p_xrBeginFrame);
    p_xrEndFrame = (PFN_xrEndFrame)p_xrGetInstanceProcAddr(sInstance, "xrEndFrame", (void**)&p_xrEndFrame);
    p_xrBeginSession = (PFN_xrBeginSession)p_xrGetInstanceProcAddr(sInstance, "xrBeginSession", (void**)&p_xrBeginSession);
    p_xrEndSession = (PFN_xrEndSession)p_xrGetInstanceProcAddr(sInstance, "xrEndSession", (void**)&p_xrEndSession);

    LOGI("OpenXR function pointers loaded");
    return true;
}

// ---- Check extension availability ----
static bool hasExtension(const char* extensionName) {
    if (!p_xrEnumerateInstanceExtensionProperties) return false;

    uint32_t count = 0;
    XrResult res = p_xrEnumerateInstanceExtensionProperties(nullptr, &count, nullptr);
    if (res != XR_SUCCESS) {
        LOGE("xrEnumerateInstanceExtensionProperties count failed: %d", res);
        return false;
    }

    struct ExtProp {
        int64_t type;
        void* next;
        char name[128];
        uint64_t version;
    };
    std::vector<ExtProp> props(count);
    for (auto& p : props) {
        p.type = 7; // XR_TYPE_EXTENSION_PROPERTIES
        p.next = nullptr;
    }

    res = p_xrEnumerateInstanceExtensionProperties(nullptr, &count, (void*)props.data());
    if (res != XR_SUCCESS) {
        LOGE("xrEnumerateInstanceExtensionProperties enum failed: %d", res);
        return false;
    }

    for (auto& p : props) {
        if (strcmp(p.name, extensionName) == 0) {
            LOGI("Found extension: %s", extensionName);
            return true;
        }
    }

    LOGW("Extension not found: %s", extensionName);
    LOGI("Available extensions (%u):", count);
    for (auto& p : props) {
        LOGI("  %s", p.name);
    }
    return false;
}

// ---- OpenXR initialization ----
static bool initOpenXr() {
    if (!loadOpenXr()) {
        LOGE("Failed to load OpenXR loader");
        return false;
    }

    // Check for required extensions
    const char* extensions[] = {
        "XR_KHR_opengl_es_enable",
        "XR_META_overlay",
    };

    bool hasGles = hasExtension(extensions[0]);
    bool hasOverlay = hasExtension(extensions[1]);

    if (!hasGles) {
        LOGE("XR_KHR_opengl_es_enable extension not available - cannot create OpenXR overlay");
        return false;
    }

    if (!hasOverlay) {
        LOGW("XR_META_overlay extension not available - overlay may not be visible in VR");
    }

    // Create instance
    XrInstanceCreateInfo ci = {};
    ci.type = 1; // XR_TYPE_INSTANCE_CREATE_INFO
    ci.next = nullptr;
    ci.createFlags = 0;
    strncpy(ci.applicationName, "VR Metrics Overlay", sizeof(ci.applicationName) - 1);
    ci.applicationApiVersion = ((1ULL & 0xffff) << 48) | ((0ULL & 0xffff) << 32) | (34ULL & 0xffffffff); // XR_MAKE_VERSION(1,0,34)
    strncpy(ci.engineName, "Custom", sizeof(ci.engineName) - 1);
    ci.engineVersion = 1;
    ci.enabledApiLayerCount = 0;
    ci.enabledApiLayerNames = nullptr;

    std::vector<const char*> enabledExts;
    enabledExts.push_back("XR_KHR_opengl_es_enable");
    if (hasOverlay) {
        enabledExts.push_back("XR_META_overlay");
    }
    ci.enabledExtensionCount = (uint32_t)enabledExts.size();
    ci.enabledExtensionNames = enabledExts.data();

    XrResult res = p_xrCreateInstance(&ci, &sInstance);
    if (res != XR_SUCCESS) {
        LOGE("xrCreateInstance failed: %d", res);
        return false;
    }
    LOGI("OpenXR instance created: %llu", (unsigned long long)sInstance);

    loadXrFunctions();

    // Get system
    XrSystemGetInfo sysInfo = {};
    sysInfo.type = 3; // XR_TYPE_SYSTEM_GET_INFO
    sysInfo.next = nullptr;
    sysInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;

    res = p_xrGetSystem(sInstance, &sysInfo, &sSystemId);
    if (res != XR_SUCCESS) {
        LOGE("xrGetSystem failed: %d", res);
        return false;
    }
    LOGI("OpenXR system: %llu", (unsigned long long)sSystemId);

    // Create session
    XrSessionCreateInfo sessionCi = {};
    sessionCi.type = 2; // XR_TYPE_SESSION_CREATE_INFO
    sessionCi.next = nullptr;
    sessionCi.createFlags = 0;
    sessionCi.systemId = sSystemId;

    // For overlay, we'd add XR_META_overlay session creation info here
    // But we need the overlay extension function pointers
    // For now, create a normal session first

    res = p_xrCreateSession(sInstance, &sessionCi, &sSession);
    if (res != XR_SUCCESS) {
        LOGE("xrCreateSession failed: %d", res);
        // This is expected for a background overlay service without XR_META_overlay
        LOGW("Normal session creation failed (expected for background overlay). "
             "XR_META_overlay extension is required for overlay sessions.");
        sSession = 0;
        return false;
    }
    LOGI("OpenXR session created: %llu", (unsigned long long)sSession);

    // Create reference space
    XrReferenceSpaceCreateInfo spaceCi = {};
    spaceCi.type = 11; // XR_TYPE_REFERENCE_SPACE_CREATE_INFO
    spaceCi.next = nullptr;
    spaceCi.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    spaceCi.orientation = {0.0f, 0.0f, 0.0f, 1.0f};
    spaceCi.position = {0.0f, 0.0f, -1.0f};

    res = p_xrCreateReferenceSpace(sSession, &spaceCi, &sSpace);
    if (res != XR_SUCCESS) {
        LOGE("xrCreateReferenceSpace failed: %d", res);
        return false;
    }
    LOGI("OpenXR reference space created");

    // Create swapchain
    XrSwapchainCreateInfo swapCi = {};
    swapCi.type = 8; // XR_TYPE_SWAPCHAIN_CREATE_INFO
    swapCi.next = nullptr;
    swapCi.createFlags = 0;
    swapCi.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    swapCi.format = 0x8C43; // GL_SRGB8_ALPHA8
    swapCi.sampleCount = 1;
    swapCi.width = sTextureWidth;
    swapCi.height = sTextureHeight;
    swapCi.faceCount = 1;
    swapCi.arraySize = 1;
    swapCi.mipCount = 1;

    res = p_xrCreateSwapchain(sSession, &swapCi, &sSwapchain);
    if (res != XR_SUCCESS) {
        LOGE("xrCreateSwapchain failed: %d", res);
        return false;
    }
    LOGI("OpenXR swapchain created: %dx%d", sTextureWidth, sTextureHeight);

    // Enumerate swapchain images
    uint32_t imgCount = 0;
    res = p_xrEnumerateSwapchainImages(sSwapchain, &imgCount, nullptr);
    if (res != XR_SUCCESS) {
        LOGE("xrEnumerateSwapchainImages count failed: %d", res);
        return false;
    }

    sSwapchainImages.resize(imgCount);
    for (auto& img : sSwapchainImages) {
        img.type = 1000144000; // XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR
        img.next = nullptr;
        img.image = 0;
    }

    res = p_xrEnumerateSwapchainImages(sSwapchain, &imgCount, (void*)sSwapchainImages.data());
    if (res != XR_SUCCESS) {
        LOGE("xrEnumerateSwapchainImages enum failed: %d", res);
        return false;
    }

    sSwapchainImageCount = imgCount;
    LOGI("Swapchain images: %u", imgCount);
    for (uint32_t i = 0; i < imgCount; i++) {
        LOGI("  Image %u: GL texture %u", i, sSwapchainImages[i].image);
    }

    // Begin session
    struct { int64_t type; void* next; int32_t primaryView; } beginInfo = {};
    beginInfo.type = 0; // XR_TYPE_SESSION_BEGIN_INFO
    beginInfo.next = nullptr;
    beginInfo.primaryView = 1; // XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO

    res = p_xrBeginSession(sSession, &beginInfo);
    if (res != XR_SUCCESS) {
        LOGE("xrBeginSession failed: %d", res);
        return false;
    }
    sSessionState = XR_SESSION_STATE_READY;
    LOGI("OpenXR session started");

    return true;
}

// ---- Poll events ----
static bool pollEvents() {
    if (!p_xrPollEvent) return false;

    XrEventDataBuffer event = {};
    event.type = 9; // XR_TYPE_EVENT_DATA_BUFFER
    event.next = nullptr;

    XrResult res = p_xrPollEvent(sInstance, &event);
    if (res == XR_SUCCESS) {
        // Check event type
        int64_t eventType = *(int64_t*)event.varying;
        LOGD("OpenXR event type: %lld", (long long)eventType);

        if (eventType == 14) { // XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED
            struct { int64_t type; void* next; uint32_t session; uint32_t fromState; uint32_t toState; } *stateChanged = (decltype(stateChanged))&event;
            sSessionState = stateChanged->toState;
            LOGI("Session state changed: %u -> %u", stateChanged->fromState, stateChanged->toState);

            if (sSessionState == XR_SESSION_STATE_EXITING || sSessionState == XR_SESSION_STATE_LOSS_PENDING) {
                LOGI("Session exiting or lost");
                return false;
            }
        }
    }

    if (sSessionState == XR_SESSION_STATE_EXITING || sSessionState == XR_SESSION_STATE_LOSS_PENDING) {
        return false;
    }

    return true;
}

// ---- Render frame with OpenXR ----
static bool renderFrame() {
    if (!sSession || !sSwapchain) {
        return false;
    }

    if (!eglMakeCurrent(sEglDisplay, sEglSurface, sEglSurface, sEglContext)) {
        LOGE("eglMakeCurrent failed in renderFrame");
        return false;
    }

    // Wait for frame
    XrFrameState frameState = {};
    frameState.type = 17; // XR_TYPE_FRAME_STATE
    frameState.next = nullptr;

    XrResult res = p_xrWaitFrame(sSession, nullptr, &frameState);
    if (res != XR_SUCCESS) {
        LOGE("xrWaitFrame failed: %d", res);
        return false;
    }

    if (!frameState.shouldRender) {
        return true;
    }

    // Begin frame
    XrFrameBeginInfo beginInfo = {};
    beginInfo.type = 15; // XR_TYPE_FRAME_BEGIN_INFO
    beginInfo.next = nullptr;

    res = p_xrBeginFrame(sSession, &beginInfo);
    if (res != XR_SUCCESS) {
        LOGE("xrBeginFrame failed: %d", res);
        return false;
    }

    // Acquire swapchain image
    uint32_t imgIndex = 0;
    res = p_xrAcquireSwapchainImage(sSwapchain, nullptr, &imgIndex);
    if (res != XR_SUCCESS) {
        LOGE("xrAcquireSwapchainImage failed: %d", res);
        p_xrEndFrame(sSession, nullptr);
        return false;
    }

    // Wait for image
    struct { int64_t type; void* next; int64_t timeout; } waitInfo = {};
    waitInfo.type = 0;
    waitInfo.next = nullptr;
    waitInfo.timeout = 1000000000LL; // 1 second

    res = p_xrWaitSwapchainImage(sSwapchain, &waitInfo);
    if (res != XR_SUCCESS) {
        LOGD("xrWaitSwapchainImage: %d", res);
    }

    // Render to the swapchain image
    GLuint targetTex = sSwapchainImages[imgIndex].image;
    if (targetTex != 0) {
        // Use FBO to render to the swapchain texture
        GLuint fbo;
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targetTex, 0);

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
        glFlush();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &fbo);
    } else {
        LOGW("Swapchain image has no GL texture, rendering to pbuffer");
        glViewport(0, 0, sTextureWidth, sTextureHeight);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(sProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sTextureId);
        glUniform1i(glGetUniformLocation(sProgram, "uTexture"), 0);
        glBindVertexArray(sVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);
        eglSwapBuffers(sEglDisplay, sEglSurface);
    }

    // Release swapchain image
    res = p_xrReleaseSwapchainImage(sSwapchain, nullptr);
    if (res != XR_SUCCESS) {
        LOGD("xrReleaseSwapchainImage: %d", res);
    }

    // End frame with quad overlay layer
    XrCompositionLayerQuad quad = {};
    quad.base.type = XR_TYPE_COMPOSITION_LAYER_QUAD;
    quad.base.next = nullptr;
    quad.base.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
    quad.base.space = sSpace;
    quad.eyeVisibility = 0; // both eyes

    // Position the overlay in front of the user
    float dist = sDistance;
    float pitchRad = sPitch * 3.14159265f / 180.0f;
    float yawRad = sYaw * 3.14159265f / 180.0f;

    // Combined pitch+yaw quaternion
    // For small angles: approximate with pitch around X, yaw around Y
    float cp = cosf(pitchRad * 0.5f);
    float sp = sinf(pitchRad * 0.5f);
    float cy = cosf(yawRad * 0.5f);
    float sy = sinf(yawRad * 0.5f);

    quad.orientation = {sp * cy, sy * cp, sp * sy, cp * cy};
    quad.position = {0.0f, 0.0f, -dist};
    quad.size = {0.5f * sScale, 0.265625f * sScale};
    quad.swapchain = sSwapchain;
    quad.swapchainImageIndex = imgIndex;

    const void* layers[] = {&quad.base};

    XrFrameEndInfo endInfo = {};
    endInfo.type = 16; // XR_TYPE_FRAME_END_INFO
    endInfo.next = nullptr;
    endInfo.displayTime = frameState.predictedDisplayTime;
    endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND;
    endInfo.layerCount = 1;
    endInfo.layers = layers;

    res = p_xrEndFrame(sSession, &endInfo);
    if (res != XR_SUCCESS) {
        LOGE("xrEndFrame failed: %d", res);
        return false;
    }

    return true;
}

// ---- Cleanup ----
static void cleanupOpenXr() {
    if (sSwapchain && p_xrDestroySwapchain) {
        p_xrDestroySwapchain(sSwapchain);
        sSwapchain = 0;
    }
    if (sSpace && p_xrDestroySpace) {
        p_xrDestroySpace(sSpace);
        sSpace = 0;
    }
    if (sSession) {
        if (p_xrEndSession) p_xrEndSession(sSession);
        if (p_xrDestroySession) p_xrDestroySession(sSession);
        sSession = 0;
    }
    if (sInstance && p_xrDestroyInstance) {
        p_xrDestroyInstance(sInstance);
        sInstance = 0;
    }
    if (sOpenXrLib) {
        dlclose(sOpenXrLib);
        sOpenXrLib = nullptr;
    }
}

static void destroyGl() {
    if (sTextureId) { glDeleteTextures(1, &sTextureId); sTextureId = 0; }
    if (sVao) { glDeleteVertexArrays(1, &sVao); sVao = 0; }
    if (sVbo) { glDeleteBuffers(1, &sVbo); sVbo = 0; }
    if (sProgram) { glDeleteProgram(sProgram); sProgram = 0; }
}

static void destroyEgl() {
    if (sEglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(sEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (sEglSurface != EGL_NO_SURFACE) { eglDestroySurface(sEglDisplay, sEglSurface); sEglSurface = EGL_NO_SURFACE; }
        if (sEglContext != EGL_NO_CONTEXT) { eglDestroyContext(sEglDisplay, sEglContext); sEglContext = EGL_NO_CONTEXT; }
        eglTerminate(sEglDisplay);
        sEglDisplay = EGL_NO_DISPLAY;
    }
}

static void nativeDestroy() {
    if (!sInitialized) return;
    LOGI("Destroying native renderer");
    destroyGl();
    cleanupOpenXr();
    destroyEgl();
    sInitialized = false;
}

// ---- Render loop thread (unused - Java manages the loop) ----

// ---- JNI ----
extern "C" {

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_init(
        JNIEnv* env, jclass clazz, jobject context, jint textureWidth, jint textureHeight, jint backend) {
    (void)context;
    (void)clazz;

    if (sInitialized) {
        LOGW("NativeRenderer already initialized, destroying first");
        nativeDestroy();
    }

    sTextureWidth = textureWidth;
    sTextureHeight = textureHeight;

    LOGI("Initializing native renderer: %dx%d backend=%d", sTextureWidth, sTextureHeight, (int)backend);

    if (!initEgl()) {
        LOGE("Failed to initialize EGL");
        return;
    }

    if (!initGl()) {
        LOGE("Failed to initialize OpenGL");
        destroyEgl();
        return;
    }

    // Try to initialize OpenXR for VR overlay
    bool xrOk = initOpenXr();
    if (!xrOk) {
        LOGW("OpenXR overlay init failed - overlay will not be visible in VR. "
             "Ensure XR_META_overlay extension is available and app has overlay permission.");
        // Continue running - texture updates still work, just no VR display
    }

    sInitialized = true;
    LOGI("Native renderer initialized (OpenXR: %s)", xrOk ? "YES" : "NO - NO VR DISPLAY");
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_destroy(
        JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    sShutdownRequested = true;
    nativeDestroy();
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_updateTexture(
        JNIEnv* env, jclass clazz, jintArray textureArray, jint textureWidth, jint textureHeight,
        jint displayedWidth, jint displayedHeight, jint textureUpdateCount) {
    (void)clazz;
    (void)displayedWidth;
    (void)displayedHeight;

    if (!sInitialized || !sTextureId) return;

    if (textureArray == nullptr) return;

    jint* pixels = env->GetIntArrayElements(textureArray, nullptr);
    if (pixels == nullptr) return;

    sTextureUpdateCount = textureUpdateCount;

    if (!eglMakeCurrent(sEglDisplay, sEglSurface, sEglSurface, sEglContext)) {
        env->ReleaseIntArrayElements(textureArray, pixels, JNI_ABORT);
        return;
    }

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
    (void)captureAllowed;

    if (!sInitialized) return;

    sPitch = pitch;
    sYaw = yaw;
    sScale = scale;
    sDistance = distance;
    sHeadLocked = headLocked != JNI_FALSE;

    if (sSession != 0 && sSwapchain != 0) {
        pollEvents();
        renderFrame();
    }
}

JNIEXPORT void JNICALL
Java_com_vroverlay_metrics_rendering_NativeRenderer_nativeSetOverlayFrameRateHint(
        JNIEnv* env, jclass clazz, jshort frameRate) {
    (void)env;
    (void)clazz;
    LOGI("Overlay frame rate hint: %d", (int)frameRate);
}

}
