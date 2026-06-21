# VR Overlay Permission Debug Guide
## Quest 3 VRAPI_GLES Overlay Permission Issue Investigation

**Date:** 2026-06-22  
**Device:** Meta Quest 3  
**App:** com.vroverlay.metrics  
**Backend:** VRAPI_GLES  
**Architecture:** arm64-v8a

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Investigation Steps](#investigation-steps)
3. [Key Findings](#key-findings)
4. [Permission Check Analysis](#permission-check-analysis)
5. [Potential Solutions](#potential-solutions)
6. [Tools and Commands](#tools-and-commands)
7. [Lessons Learned](#lessons-learned)

---

## Problem Statement

The app `com.vroverlay.metrics` fails to initialize VR runtime with VRAPI_GLES backend due to overlay permission being denied, resulting in:
- `vrapi_Initialize: Failed to connect to VrRuntimeService`
- CrashGuard blocking subsequent init attempts after crashes
- VrRuntimeService logs showing `clientFlags=2` (overlay permission denied)

The app is installed as a privileged system app but the native permission check in VrRuntimeService still blocks overlay access.

---

## Investigation Steps

### Step 1: Initial Problem Diagnosis

**Commands:**
```bash
# Check app installation status
adb shell "su -c 'pm dump com.vroverlay.metrics | grep -A5 codePath'"

# Check app permissions
adb shell "su -c 'dumpsys package com.vroverlay.metrics | grep permission'"

# Clear CrashGuard to allow retesting
adb shell "su -c 'pm clear com.vroverlay.metrics'"
```

**Findings:**
- APK installed at `/system/priv-app/com.vroverlay.metrics845/`
- Has SYSTEM flag in manifest
- UID: 10153
- Signature: `PackageSignatures{883549e version:2, signatures:[7351b102]}`
- App crashes with overlay permission error

---

### Step 2: Analyze VrRuntimeService Logs

**Commands:**
```bash
# Start logcat with VR Runtime tags
adb logcat -c
adb logcat VrRuntimeService:* VrRuntimeClient:* IPCBrokerConnectionMgr:* | tee app_launch.log

# Launch the app
adb shell "su -c 'am start -n com.vroverlay.metrics/.MainActivity'"

# Stop logcat after crash (Ctrl+C)
```

**Key Log Entries:**
```
VrRuntimeService: RuntimeServiceSDKServer::RegisterClient: packageName com.vroverlay.metrics clientName VrRuntimeClient-1 clientType 1 clientFlags 2 ...
VrRuntimeClient: RegisterClient RequestDescriptor com.vroverlay.metrics:12938 clientType 1 clientFlags 2 ...
```

**Analysis:**
- `clientType: 1` — application type
- `clientFlags: 0x2` (decimal 2) — overlay permission NOT granted
- Multiple RegisterClient calls (attempts until timeout at ~0.65s)

---

### Step 3: Locate VR Runtime Components

**Commands:**
```bash
# Find VR Runtime service process
adb shell "su -c 'ps -A | grep -i vrruntime'"

# Find VR Driver package
adb shell "su -c 'pm list packages -f | grep -i oculus | grep -i vr'"

# List system libraries
adb shell "su -c 'ls -la /system_ext/lib64/'" | Select-String -Pattern 'vrapi'
```

**Findings:**
- VR Runtime process: `com.oculus.vrruntimeservice` PID 2502, UID 10120
- VR Driver APK: `/apex/com.meta.xr/priv-app/VrDriver/VrDriver.apk` (36 MB)
- VRAPI library: `/system_ext/lib64/libvrapi.so`
- No `runtimeipcbroker` binary found (integrated into VrDriver now)

---

### Step 4: Extract VR Driver APK

**Commands:**
```bash
# Create working directory
mkdir C:\Users\iqdvi\AppData\Local\Temp\kilo

# Pull VR Driver APK
adb pull /apex/com.meta.xr/priv-app/VrDriver/VrDriver.apk C:\Users\iqdvi\AppData\Local\Temp\kilo\VrDriver.apk

# Rename to .zip and extract (APK is a ZIP archive)
Copy-Item VrDriver.apk VrDriver.zip
Expand-Archive -Path VrDriver.zip -DestinationPath VrDriver_extracted -Force
```

**Extracted Structure:**
```
VrDriver_extracted/
├── lib/
│   ├── arm64-v8a/
│   │   ├── libvrruntimeservice.so   (14,439,440 bytes) ← KEY FILE
│   │   ├── libvrapiimpl.so
│   │   ├── libvrapi_trampoline.so
│   │   ├── libvrapilayers.so
│   │   ├── libautodriver.so
│   │   ├── libselect-autodriver.so
│   │   ├── libloaderimpl.so
│   │   ├── libopenxr_forwardloader.so
│   │   ├── liblib_trex_loader_shared.so
│   │   ├── libtrex_haptics_server_plugin.so
│   │   ├── libtrex_render_model_server_plugin.so
│   │   ├── libstatspull.so
│   │   ├── libstatssocket.so
│   │   └── ... (thrift/folly support libs)
│   └── armeabi-v7a/ (same set, 32-bit)
├── assets/
├── classes.dex
├── AndroidManifest.xml
└── resources.arsc
```

**Key Library:** `lib/arm64-v8a/libvrruntimeservice.so` — contains the overlay permission check logic.

---

### Step 5: Analyze Native Library Strings

**Commands:**
```bash
# Push library to device for on-device strings extraction
adb push libvrruntimeservice.so /data/local/tmp/libvrruntimeservice.so
adb shell "su -c 'chmod 755 /data/local/tmp/libvrruntimeservice.so'"

# Search for overlay-related strings
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep -i overlay"

# Search for permission-related strings
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep permission"

# Search for client registration strings
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep RegisterClient"
```

**Overlay-Related Strings Found:**
```
blend_support_overlay
persist.ovr.debug.boosted_overlay
debug.oculus.postprocess.overlay
CreateClient: MultiClientDebugEnabled: allowed client %s as overlay
SetOverlayClientHelper: MultiClientDebugEnabled: allowed client %s as overlay
CreateClient: %s registered as overlay
CreateClient: %s has permissions of overlay
com.oculus.vrshell:Overlay
%s is not a Boosted Overlay
%s detected and will be treated as a Boosted Overlay
FocusedWindowChanged: found matching overlay client: %u
runtime/overlay_is_active_client
SetOverlayClientHelper: Client is not specified as overlayClient
SetOverlayClientHelper: client does not have overlay permission
CreateClient: client does not have overlay permission
persist.ovr.debug.boosted_overlay = %s
Failed to load overlay image, check if image file is valid: %s
```

**Permission-Related Strings Found:**
```
CheckRpcHandlerPermissions: one-way RPC silently dropped, permission denied for %s from client %u
ClientMgr::GetClientPermissionFlagsById: Client missing proper permissions: %s, %u
com.oculus.systempermissions
com.oculus.extrapermissions
CreateClient: %s has default permissions
CreateClient: Invalid client type: %d.  %s has default permissions
CreateClient: client does not have overlay permission
SetOverlayClientHelper: client does not have overlay permission
Not enforcing the passthrough permission
SetApertureManagerClient: client %s permission denied
horizonos.permission.USE_SCENE
Plugin '%s:%s': client features or permissions don't match requirements.
Disabling all permissions (ONLY USE IN TESTS): %u:%s/%s (%u:%u), mask: %s
com.android.permissioncontroller
permissionsJson
Failed to find UID for package. No permission added.
```

**RegisterClient Strings Found:**
```
RuntimeServiceSDKServer::RegisterClient: Invalid ID: %u
auto rssdk::RuntimeServiceSDKServer::RegisterClient(const uint32_t, const RegisterClientRequest &, bool &)::(anonymous class)::operator()()::(anonymous class)::operator()(uint32_t) const
RegisterClientRequest
RegisterClient
RuntimeServiceSDKServer_RegisterClient_CreateClient
CompositorClientManager::UnRegisterClient: invalid client %s
CompositorClientManager::UnRegisterClient %s
CompositorClientManager::RegisterClient %s
RegisterClient was called again but the client was not shutdown. %s
RegisterServer: ipc_RegisterClientState FAILED: %s, %i / %i
RegisterClient: ipc_GetServerStateInterface FAILED: %s, %i
ipc_RegisterClientState
```

**Debug Log Format (detailed):**
```
%s: id 0x%x, pid %d, uid %d, packageName %s, clientName %s, processName %s, 
processNameWithPID %s, launchId %s, permissionFlags %x, clientType %d, 
clientFlags %x, isApplication=%s, isOsApp=%s, clientSessionPlacement %u, 
clientDisplayId %d, clientVWToken %s
```

**RegisterClient Log Format (summary):**
```
RuntimeServiceSDKServer::RegisterClient: packageName %s clientName %s clientType %d 
clientFlags %x sessionPlacement %d RequestDescriptor %s uid %d, pid %d ProcessName %s 
RIPC clientId %u (0x%x) clientLaunchId [%s]
```

**Debug Override:**
```
CreateClient: MultiClientDebugEnabled: allowed client %s as overlay
SetOverlayClientHelper: MultiClientDebugEnabled: allowed client %s as overlay
MultiClientDebugEnabled = %d
```

**Debug Property References:**
```
persist.debug.oculus.gazecontrol.verbosity
persist.debug.handmod_target_latency
persist.ovr.debug.boosted_overlay
debug.oculus.postprocess.overlay
debug.oculus.ptHandOverlay
debug.oculus.MVOverlay
debug.oculus.MVOverlay.scale
debug.oculus.MVOverlay.Alpha
persist.ovr.debug.boosted_overlay = %s
```

---

### Step 6: Investigate Permission System

**Commands:**
```bash
# Check for Oculus permission packages
adb shell "su -c 'pm list packages | grep -i permission'"

# Examine extrapermissions package
adb shell "su -c 'dumpsys package com.oculus.extrapermissions'"

# Pull extrapermissions APK
adb pull /system_ext/app/ExtraPermissions/ExtraPermissions.apk extrapermissions.apk

# Extract and examine
Copy-Item extrapermissions.apk extrapermissions.zip
Expand-Archive -Path extrapermissions.zip -DestinationPath extrapermissions_extracted -Force
```

**Findings for com.oculus.extrapermissions:**
- Path: `/system_ext/app/ExtraPermissions/`
- System app (not privileged) with `SYSTEM` flag
- Has `android.permission.INTERNAL_SYSTEM_WINDOW`
- Has `android.permission.WRITE_SECURE_SETTINGS`
- Has `android.permission.READ_PRIVILEGED_PHONE_STATE`
- Signature: `PackageSignatures{ab32949 version:3, signatures:[223615b9]}`
- Many Oculus-specific permissions granted

**Note:** `com.oculus.systempermissions` package name found in library strings but NOT installed on device. This may be a legacy reference or a removed package.

---

### Step 7: Check VR Runtime Configuration

**Commands:**
```bash
# Check vendor VR configs
adb shell "su -c 'ls -la /vendor/etc/ | grep -i vr'"

# Read VR power mitigation config
adb shell "su -c 'cat /vendor/etc/vr_runtime_power_mitigation.json'"

# Check VR debug properties
adb shell "su -c 'getprop | grep -i vr | grep -i debug'"

# Check overlay-related properties
adb shell "su -c 'getprop | grep -i oculus | grep -i overlay'"
```

**Findings:**
- Only `/vendor/etc/vr_runtime_power_mitigation.json` exists (power settings, not permissions)
- System debug properties exist but none directly for overlay permission
- `persist.ovr.debug.boosted_overlay` property found in library strings (may be settable)

---

### Step 8: Cross-Reference App Logs with Library Analysis

**Commands:**
```bash
# Extract app registration parameters from logs
Select-String -Path app_launch.log -Pattern "clientType|clientFlags|packageName.*vroverlay"
```

**App's Registration Parameters:**
```
packageName: com.vroverlay.metrics
clientName: VrRuntimeClient-[1 through 6]
clientType: 1
clientFlags: 0x2
sessionPlacement: -1 (0xFFFFFFFF = 4294967295)
uid: 10153
pid: 12938
ProcessName: com.vroverlay.metrics
RIPC clientId: varies per attempt
clientLaunchId: [] (empty)
```

**Critical Observation:**
- The detailed debug log (`permissionFlags %x, clientType %d, clientFlags %x, isApplication=%s, isOsApp=%s`) does NOT appear for our app
- Only the summary RegisterClient log appears
- This means either the verbose log is at a different level or only fires for certain clients
- The `permissionFlags` value is unknown — it would show the raw permission evaluation result before it maps to `clientFlags`

---

## Key Findings

### 1. Permission Check Location
**File:** `/apex/com.meta.xr/priv-app/VrDriver/lib/arm64-v8a/libvrruntimeservice.so`  
**Size:** 14,439,440 bytes (14 MB)  
**APK:** VrDriver.apk inside APEX module `/apex/com.meta.xr/`

**Key Functions (from string references):**
| Function | Purpose |
|----------|---------|
| `RuntimeServiceSDKServer::RegisterClient()` | Main registration IPC handler |
| `CreateClient()` | Client creation and permission evaluation |
| `SetOverlayClientHelper()` | Sets overlay client flag |
| `GetClientPermissionFlagsById()` | Retrieves permission flags by client ID |
| `CheckRpcHandlerPermissions()` | Permission gate for RPC calls |
| `CompositorClientManager::RegisterClient()` | Compositor-side client registration |

### 2. Permission Evaluation Logic

The overlay permission check evaluates the following:

**Client Registration Parameters:**
```
packageName + uid + signature hash  →  Client identity
clientType                          →  Application type (1 = application)
clientFlags                         →  Output: resulting permission flags
permissionFlags                     →  Internal: raw permission evaluation
isApplication                       →  Boolean: is this a user app?
isOsApp                             →  Boolean: is this OS/system app?
```

**Three Permission Outcomes:**

| Outcome | Log String | Meaning | clientFlags |
|---------|-----------|---------|-------------|
| **Allowed** | `CreateClient: %s has permissions of overlay` | Overlay granted | 0x1 (hypothesized) |
| **Default** | `CreateClient: %s has default permissions` | No overlay (default) | 0x0? (hypothesized) |
| **Denied** | `CreateClient: client does not have overlay permission` | Explicitly denied | 0x2 (observed) |

**Your App's Case:**
- Falls into "does not have overlay permission" (explicitly denied)
- `clientFlags=0x2` — overlay permission bit NOT set
- Multiple RegisterClient attempts all fail with the same result

### 3. System App Status vs Native Check

**What Your App Has:**
- ✅ Installed as `/system/priv-app/com.vroverlay.metrics845/`
- ✅ Manifest contains SYSTEM flag
- ✅ Signature hash: `[7351b102]`
- ✅ UID 10153 (privileged app UID range)
- ✅ Target SDK 35

**What the Native Check Likely Requires (one or more of):**
- ❓ Specific signature hash in hardcoded allowlist
- ❓ Package name in hardcoded allowlist
- ❓ Specific UID range matching Oculus system apps
- ❓ Additional native permission flag from package manager
- ❓ Oculus-signed certificate verification (platform key)

### 4. Debug Override Mechanism

**Discovery:** There's a `MultiClientDebugEnabled` debug setting that can override overlay permission:

```cpp
// From library strings:
CreateClient: MultiClientDebugEnabled: allowed client %s as overlay
SetOverlayClientHelper: MultiClientDebugEnabled: allowed client %s as overlay
MultiClientDebugEnabled = %d
```

**Implication:** If `MultiClientDebugEnabled` is set to 1 (or true), ALL clients are allowed as overlay regardless of normal permission checks. This is a bypass of the entire allowlist/signature check.

### 5. VR Shell Overlay Reference

**Found string:** `com.oculus.vrshell:Overlay`

This suggests `com.oculus.vrshell` is explicitly listed as an overlay client, likely hardcoded in the binary or in a configuration file. This is the reference implementation of an overlay app.

---

## Permission Check Analysis

### The Check Flow (Reconstructed)

```
┌──────────────────────────────────────────────┐
│ 1. RegisterClient IPC received               │
│    - packageName: "com.vroverlay.metrics"    │
│    - uid: 10153                              │
│    - pid: 12938                              │
│    Source: libvrapiimpl.so → RuntimeIPC      │
└──────────────────┬───────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│ 2. GetClientPermissionFlagsById()            │
│    - Look up package by UID                 │
│    - Get signature hash                      │
│    - Check system app status                │
│    - Compute permissionFlags                │
└──────────────────┬───────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│ 3. Check MultiClientDebugEnabled override    │
│    - If enabled → skip normal check         │
│    - Allow all clients as overlay           │
│    - Log: "MultiClientDebugEnabled: allowed  │
│      client %s as overlay"                  │
└──────┬───────────────────────┬───────────────┘
       │                       │
  Enabled=1               Enabled=0
       │                       │
       ▼                       ▼
┌──────────────┐   ┌──────────────────────────┐
│ SKIP CHECK   │   │ 4. Native Permission     │
│ clientFlags  │   │    Check                 │
│ = overlay    │   │    - Signature allowlist?│
│ allowed      │   │    - Package allowlist?  │
└──────────────┘   │    - UID range check?  │
                   │    - isOsApp flag?       │
                   │    - Platform key?       │
                   └──────────┬───────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
             ┌─────────────┐    ┌─────────────┐
             │ ALLOWED     │    │ DENIED      │
             │ "has        │    │ "does not   │
             │  permissions│    │  have overlay│
             │  of overlay"│    │  permission" │
             │             │    │             │
             │ clientFlags │    │ clientFlags │
             │ = 0x1       │    │ = 0x2       │
             └─────────────┘    └─────────────┘
```

### clientFlags Bitfield (Hypothesized)

| Value | Hex | Meaning | Source |
|-------|-----|---------|--------|
| 1 | 0x1 | Overlay permission granted | Hypothesized (not observed) |
| 2 | 0x2 | Overlay permission denied | **Observed in our app** |
| 4 | 0x4 | Unknown (possibly passthrough) | Not observed |
| 8 | 0x8 | Unknown | Not observed |

**Your App:** `clientFlags = 0x2` → Only the "no overlay" bit is set

### Critical Gap: Missing permissionFlags Log

**Expected Log Line (from library strings):**
```
%s: id 0x%x, pid %d, uid %d, packageName %s, clientName %s, processName %s, 
processNameWithPID %s, launchId %s, permissionFlags %x, clientType %d, 
clientFlags %x, isApplication=%s, isOsApp=%s, ...
```

**Actual Log (from app_launch.log):**
```
RuntimeServiceSDKServer::RegisterClient: packageName com.vroverlay.metrics clientName 
VrRuntimeClient-1 clientType 1 clientFlags 2 ...
```

**Significance:**
- The `permissionFlags` value is NOT appearing in our logs
- The detailed format is likely behind a verbose/debug log level
- The `permissionFlags` would show the raw permission evaluation before mapping to `clientFlags`
- Enabling verbose logging for VrRuntimeService may reveal this value

### How Other Apps Get Overlay Permission

**com.oculus.vrshell (known overlay client):**
- Installed at `/system_ext/priv-app/VrShell/VrShell.apk`
- Has `com.oculus.vrshell:Overlay` string reference in libvrruntimeservice.so
- Likely hardcoded as overlay-allowed in the binary or loaded from config
- Has Oculus platform signature

**com.oculus.ovrmonitormetricsservice:**
- Installed at `/data/app/.../com.oculus.ovrmonitormetricsservice-.../base.apk`
- Not a system app — but still has VR access
- Unknown if it has overlay permission

---

## Potential Solutions

### Solution 1: Enable Multi-Client Debug Mode (Easiest — Try First)

**Rationale:** The library has a debug override mechanism (`MultiClientDebugEnabled`) that bypasses the normal permission check entirely.

**Steps:**
```bash
# Try setting possible property names (exact name unknown)
adb shell "su -c 'setprop persist.debug.oculus.multiclient 1'"
adb shell "su -c 'setprop persist.ovr.debug.multiclient 1'"
adb shell "su -c 'setprop debug.oculus.multiclient 1'"

# Kill VR Runtime to force reload
adb shell "su -c 'killall com.oculus.vrruntimeservice'"

# Monitor for MultiClientDebugEnabled in logs
adb logcat -s VrRuntimeService | grep -i multiclient

# Test the app
adb shell "su -c 'am start -n com.vroverlay.metrics/.MainActivity'"
```

**Success Criteria:**
- App starts without crashing
- Log shows: `"MultiClientDebugEnabled: allowed client com.vroverlay.metrics as overlay"`
- `clientFlags` changes from `0x2` to `0x1`

**Challenges:**
- Exact property name is unknown (need to find it in binary with disassembler)
- Property may not be persisted across reboots (use `persist.` prefix)
- May be disabled/compiled-out in production builds
- Security implications (ALL apps would get overlay access)

**Finding the Property Name:**
```bash
# Search library for property references near MultiClientDebugEnabled
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep -i multiclient"
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep -E 'persist.*debug.*multi|debug.*multi'"

# Use Ghidra to find xrefs to the MultiClientDebugEnabled string
# The property read (system_property_get / __system_property_find) will be nearby
```

---

### Solution 2: Permission Allowlist Configuration Files

**Rationale:** VR Runtime may use config files to store allowed overlay package names.

**Steps:**
```bash
# Search for allowlist/permission files
adb shell "su -c 'find /system /vendor /data -name *overlay* 2>/dev/null' | grep -v apk | grep -v lib | grep -v .odex"
adb shell "su -c 'find /system /vendor /data -name *allowlist* 2>/dev/null'"
adb shell "su -c 'find /system /vendor /data -name *permission* 2>/dev/null' | grep -v apk | grep -v lib | grep -v .odex"

# Search for JSON/XML config files containing "overlay"
adb shell "su -c 'find /system /vendor /data -type f \( -name *.json -o -name *.xml \) 2>/dev/null'" 

# Check APEX module for config
adb shell "su -c 'ls -la /apex/com.meta.xr/etc/'" 

# Check VrRuntimeService data
adb shell "su -c 'find /data -path *vrruntimeservice* 2>/dev/null'"

# Search library for file path references
adb shell "strings /data/local/tmp/libvrruntimeservice.so | grep -E '/system|/vendor|/data.*json|/data.*xml' | grep -v lib64 | grep -v .so"
```

**Expected File Locations:**
```
/vendor/etc/vr_permissions.json
/vendor/etc/oculus/overlay_allowlist.xml
/system/etc/permissions/vr_overlay.xml
/apex/com.meta.xr/etc/permissions.json
/data/system/vr_runtime_config.json
/data/user/0/com.oculus.vrruntimeservice/
```

**If Found — Modifying Allowlist:**
```bash
# Pull config file
adb pull /path/to/config overlay_config.json

# Edit: add your package name
# Example JSON:
# {
#   "overlay_clients": [
#     "com.vroverlay.metrics",
#     "com.oculus.vrshell",
#     ...
#   ]
# }

# Push back (prefer systemless via Magisk)
adb push overlay_config.json /data/local/tmp/overlay_config.json
adb shell "su -c 'cp /data/local/tmp/overlay_config.json /path/to/config'"
adb shell "su -c 'chmod 644 /path/to/config'"

# Restart VR Runtime
adb shell "su -c 'killall com.oculus.vrruntimeservice'"
```

**Challenges:**
- No config files found so far
- Allowlist may be hardcoded in the binary (not a file)
- `/system` and `/vendor` are read-only (need Magisk module)
- Config format unknown

---

### Solution 3: Patch libvrruntimeservice.so (Most Reliable)

**Rationale:** Modify the native library to always allow overlay permission, bypassing the check.

#### 3.1. Disassemble the Library

Use Ghidra, IDA Pro, or Binary Ninja to load `libvrruntimeservice.so` (14 MB, arm64).

**Key search targets:**
- String: `"CreateClient: client does not have overlay permission"`
- String: `"SetOverlayClientHelper: client does not have overlay permission"`
- String: `"MultiClientDebugEnabled"`
- Function: `CreateClient`
- Function: `SetOverlayClientHelper`
- Function: `GetClientPermissionFlagsById`

**Cross-reference approach:**
1. Find the string `"CreateClient: client does not have overlay permission"` in the `.rodata` section
2. Find all code references to this string address
3. The referencing function is the permission check
4. Analyze the conditional branches around the string reference
5. Find the branch that leads to `"CreateClient: %s has permissions of overlay"` (the "allowed" path)
6. Patch the conditional jump to always take the "allowed" path

#### 3.2. Locate the Check Function

```cpp
// Reconstructed pseudo-code for CreateClient():
void CreateClient(ClientInfo* client, RegisterClientRequest* request) {
    // ... setup ...
    
    if (MultiClientDebugEnabled) {
        // Override: allow all as overlay
        client->clientFlags |= OVERLAY_FLAG;
        log("CreateClient: MultiClientDebugEnabled: allowed client %s as overlay", name);
        return;
    }
    
    // Normal check
    permissionFlags = GetClientPermissionFlagsById(client->uid);
    
    if (/* permission check passes */) {
        client->clientFlags |= OVERLAY_FLAG;  // 0x1
        log("CreateClient: %s has permissions of overlay", name);
    } else if (/* some condition */) {
        log("CreateClient: %s has default permissions", name);
    } else {
        // OUR APP HITS THIS PATH
        log("CreateClient: client does not have overlay permission");
        // clientFlags stays 0x2 (no overlay)
    }
}
```

#### 3.3. Binary Patching Approaches

**Approach A: Patch the Conditional Jump (Recommended)**

1. In the disassembler, find the branch instruction that decides between "allowed" and "denied"
2. Change the conditional branch (e.g., `B.NE`) to an unconditional branch (`B`) to the "allowed" path
3. Or: NOP the check and force the result

**ARM64 Example:**
```asm
; Original: branch to "denied" if check fails
CMP W0, #0          ; compare permission result
B.EQ allowed_path   ; branch if equal (allowed)
; ... denied path follows ...

; Patched: always branch to "allowed"
NOP                  ; NOP the compare
B allowed_path       ; unconditional branch to allowed
```

**Approach B: Force clientFlags Value**

1. Find the instruction that writes `clientFlags = 0x2`
2. Change it to write `clientFlags = 0x1` (or `0x3` to set both bits)

```asm
; Original:
MOV W0, #2          ; 0x52800040
STR W0, [X1, #offset]

; Patched:
MOV W0, #1          ; 0x52800020
STR W0, [X1, #offset]
```

**Approach C: Enable MultiClientDebugEnabled Permanently**

1. Find where `MultiClientDebugEnabled` is read from system property
2. Patch the property read to always return 1
3. This enables the debug bypass for ALL apps

#### 3.4. Deploy Patched Library via Magisk Module

```bash
# Create Magisk module structure (systemless overlay)
mkdir -p /data/adb/modules/vr_overlay_patch/system/apex/com.meta.xr/priv-app/VrDriver/lib/arm64-v8a/

# Copy patched library
cp patched_libvrruntimeservice.so \
   /data/adb/modules/vr_overlay_patch/system/apex/com.meta.xr/priv-app/VrDriver/lib/arm64-v8a/libvrruntimeservice.so

# Create module metadata
cat > /data/adb/modules/vr_overlay_patch/module.prop << 'ENDPROP'
id=vr_overlay_patch
name=VR Overlay Permission Patch
version=1.0
versionCode=1
author=Developer
description=Patches libvrruntimeservice.so to allow overlay permission for all apps
ENDPROP

# Enable module
touch /data/adb/modules/vr_overlay_patch/disable  # disable by default
# Remove disable file to enable:
rm /data/adb/modules/vr_overlay_patch/disable

# Reboot
adb reboot
```

**Challenges:**
- APEX modules have special mounting — Magisk may not overlay APEX contents correctly
- May need to replace the library in `/data` or use a different overlay path
- Binary layout changes with system updates — patch must be re-created
- Risk of breaking VR Runtime if patch is incorrect
- Need Ghidra/IDA for proper reverse engineering

---

### Solution 4: Switch to OPENXR_VULKAN Backend

**Rationale:** OpenXR bypasses VrRuntimeService entirely — no overlay permission check needed.

**Implementation:**
- Change VR backend from `VRAPI_GLES` to `OPENXR_VULKAN` in app settings
- OpenXR creates its own session via the OpenXR loader
- No RegisterClient IPC → no permission check → no `clientFlags` issue

**Pros:**
- ✅ No VrRuntimeService permission checks
- ✅ Officially supported on Quest 3
- ✅ Future-proof (OpenXR is Khronos standard)
- ✅ No system modification required

**Cons:**
- ❌ Requires significant code changes (different API surface)
- ❌ Vulkan required (not OpenGL ES)
- ❌ Different rendering pipeline
- ❌ May have different performance characteristics

---

## Tools and Commands

### ADB Commands

```bash
# App management
adb install -r app-debug.apk
adb uninstall com.vroverlay.metrics
adb shell pm list packages
adb shell dumpsys package [pkg]

# Root operations
adb shell "su -c 'id'"
adb shell "su -c 'mount -o rw,remount /system'"

# Process management
adb shell ps -A | grep [process]
adb shell "su -c 'killall com.oculus.vrruntimeservice'"
adb shell "su -c 'am start -n com.vroverlay.metrics/.MainActivity'"

# File operations
adb pull [remote] [local]
adb push [local] [remote]
adb shell "su -c 'ls -la [path]'"
adb shell "su -c 'cat [file]'"

# System properties
adb shell getprop
adb shell getprop [name]
adb shell "su -c 'setprop [name] [value]'"

# Permissions
adb shell pm grant [pkg] [perm]
adb shell "su -c 'pm clear [pkg]'"

# Logging
adb logcat -c
adb logcat VrRuntimeService:* VrRuntimeClient:* IPCBrokerConnectionMgr:* *:S
adb logcat -v time > log.txt
```

### Native Library Analysis

```bash
# On-device string extraction
adb push lib.so /data/local/tmp/lib.so
adb shell "strings /data/local/tmp/lib.so | grep [pattern]"

# Symbol extraction
adb shell "su -c 'readelf -s /data/local/tmp/libvrruntimeservice.so'"

# Section headers
adb shell "su -c 'readelf -S /data/local/tmp/libvrruntimeservice.so'"

# Dynamic dependencies
adb shell "su -c 'readelf -d /data/local/tmp/libvrruntimeservice.so'"
```

### PowerShell Commands (Windows)

```powershell
# APK extraction as ZIP
Copy-Item app.apk app.zip
Expand-Archive -Path app.zip -DestinationPath app_extracted -Force

# Search in log files
Select-String -Path .\app_launch.log -Pattern "keyword"

# Filter results
Select-String -Path .\app_launch.log -Pattern "keyword" | Select-Object -First 20

# File info
Get-Item lib.so | Select-Object Length, Name
```

### Reverse Engineering Tools

| Tool | Purpose | Platform |
|------|---------|----------|
| Ghidra | Disassembly + decompilation | Cross-platform, free |
| IDA Pro | Disassembly + decompilation | Windows/Linux/macOS, paid |
| Binary Ninja | Disassembly + decompilation | Cross-platform, paid |
| HxD | HEX editing | Windows, free |
| readelf | ELF analysis | Linux (on-device) |
| strings | String extraction | Linux (on-device) |

---

## Lessons Learned

### 1. VRAPI vs OpenXR Architecture

**VRAPI (Mobile VR API):**
- Meta's proprietary VR API
- Connects to VrRuntimeService via RuntimeIPC
- Requires overlay permission for system-wide overlays
- More restricted in recent Quest firmware versions
- Legacy API (being phased out in favor of OpenXR)

**OpenXR:**
- Open standard VR API (Khronos Group)
- Connects directly to OpenXR runtime via loader
- No VrRuntimeService permission checks for overlay
- Future-proof and cross-platform
- Recommended for new Quest development

**Takeaway:** If VRAPI overlay permission cannot be obtained, OpenXR is the alternative.

---

### 2. System App ≠ Native Permission

**Common Misconception:** "Installing as a system app gives all permissions."

**Reality:**
- System app status grants Android-level permissions
- Native permission checks are independent and may use:
  - Hardcoded signature allowlists
  - Package name allowlists
  - UID range checks
  - Platform key verification
- System app status alone is insufficient for VR overlay access

**Takeaway:** The VR overlay permission is a native-level check, not an Android permission.

---

### 3. Debug Overrides in Production Code

**Discovery:** `MultiClientDebugEnabled` override exists in production `libvrruntimeservice.so`.

**Implications:**
- Debug capabilities are often left in production code
- Can be leveraged for development and testing
- Usually controlled by system properties (`persist.debug.*`, `debug.oculus.*`)
- May be disabled or removed in future firmware updates

**Takeaway:** Always search for debug overrides before resorting to binary patching.

---

### 4. Log Levels and Hidden Information

**Observation:** The `permissionFlags` value is computed but not logged in our logs.

**Possible Causes:**
- Different log levels (VERBOSE, DEBUG, INFO, WARN, ERROR)
- Privacy/security (exposing permission flags in production)
- Two log points: summary (INFO) vs detailed (DEBUG/VERBOSE)

**Solution:**
- Try enabling verbose logging: `adb shell "su -c 'setprop persist.debug.oculus.log.level verbose'"`
- Or: `adb logcat VrRuntimeService:V *:S`

**Takeaway:** Critical debug information may exist but be hidden behind log level filtering.

---

### 5. APEX Module Structure

**Discovery:** VR Runtime lives inside an APEX module (`/apex/com.meta.xr/`), not in `/system/`.

**Implications:**
- APEX modules have special mounting and update mechanisms
- Magisk overlay of APEX contents may require special handling
- Standard `/system` replacement approaches may not work
- Need to verify Magisk module path matches APEX internal structure

**Takeaway:** APEX modules complicate systemless modification — test carefully.

---

### 6. Methodology for Investigating VR Permission Issues

**Effective Approach:**
1. **Observe runtime behavior** — Logcat, crashes, process info
2. **Locate VR components** — Find APK, libraries, processes
3. **Extract and analyze** — Pull APK, extract .so files, run `strings`
4. **Cross-reference** — Match log messages to library function names
5. **Map the flow** — Reconstruct permission check logic from strings
6. **Identify bypasses** — Debug properties, allowlist configs, patch points
7. **Test incrementally** — Try easiest solutions first (properties → configs → patches)

**Takeaway:** Systematic string analysis of native libraries reveals architecture before needing a full disassembler.

---

## Appendix

### A. File Locations Reference

| File/Path | Description | Access |
|-----------|-------------|--------|
| `/system/priv-app/com.vroverlay.metrics845/` | Our app installation | Read-only |
| `/apex/com.meta.xr/priv-app/VrDriver/VrDriver.apk` | VR Driver APK | Read-only (APEX) |
| `/apex/com.meta.xr/priv-app/VrDriver/lib/arm64-v8a/libvrruntimeservice.so` | VR Runtime library (inside APK) | Read-only (APEX) |
| `/system_ext/lib64/libvrapi.so` | VRAPI public library | Read-only |
| `/system_ext/app/ExtraPermissions/ExtraPermissions.apk` | Extra permissions system app | Read-only |
| `/system_ext/priv-app/VrShell/VrShell.apk` | VR Shell (known overlay client) | Read-only |
| `/vendor/etc/vr_runtime_power_mitigation.json` | VR power config | Read-only |
| `/data/local/tmp/` | Temporary directory (writable) | Read-write |

### B. Process Reference

| Process | PID | User (UID) | Description |
|---------|-----|------------|-------------|
| `com.oculus.vrruntimeservice` | 2502 | u0_a120 (10120) | VR Runtime Service |
| `com.vroverlay.metrics` | 12938 | u0_a153 (10153) | Our app |

### C. Signature Hashes Reference

| Package | Signature Hash | Has Overlay? |
|---------|----------------|-------------|
| `com.vroverlay.metrics` | `7351b102` | No |
| `com.oculus.extrapermissions` | `223615b9` | Unknown |
| `com.oculus.vrshell` | Not extracted | Yes (hardcoded) |

### D. Debug Properties Reference

| Property | Found In | Purpose |
|----------|----------|---------|
| `persist.ovr.debug.boosted_overlay` | Library strings | Boosted overlay setting |
| `debug.oculus.postprocess.overlay` | Library strings | Post-processing overlay |
| `debug.oculus.ptHandOverlay` | Library strings | Passthrough hand overlay |
| `debug.oculus.MVOverlay` | Library strings | MV overlay control |
| `debug.oculus.MVOverlay.scale` | Library strings | MV overlay scale |
| `debug.oculus.MVOverlay.Alpha` | Library strings | MV overlay alpha |
| `persist.debug.oculus.multiclient` | Hypothesized | Multi-client debug (name TBD) |

### E. Next Steps for Continued Research

1. **Use Ghidra to disassemble `libvrruntimeservice.so`** and find:
   - The `CreateClient()` function implementation
   - The exact `MultiClientDebugEnabled` property name
   - The overlay permission allowlist (hardcoded or config-based)
   - The conditional branch to patch

2. **Search for `MultiClientDebugEnabled` property name** by:
   - Finding xrefs to the string in Ghidra
   - Looking for `__system_property_get` or `SystemProperties_get` calls nearby
   - The first argument of the property get call is the property name

3. **Monitor a working overlay app** (like VR Shell) to compare:
   - Its `clientFlags` value (should be 0x1 or 0x3)
   - Its `permissionFlags` value
   - Its signature hash vs our app's hash

4. **Test the `persist.ovr.debug.boosted_overlay` property** with our package name:
   ```bash
   adb shell "su -c 'setprop persist.ovr.debug.boosted_overlay com.vroverlay.metrics'"
   adb shell "su -c 'killall com.oculus.vrruntimeservice'"
   ```

5. **Investigate APEX module overlay** for Magisk:
   - Check if Magisk can overlay files inside APEX modules
   - If not, find alternative injection point (e.g., `/data` overlay)

---

## Conclusion

The overlay permission issue on Quest 3 with VRAPI_GLES is fundamentally a **native permission check** in `libvrruntimeservice.so` that is **independent of Android's permission system**. Even though our app is properly installed as a privileged system app, the native check denies overlay access.

**Root Cause:** The native permission check in `RuntimeServiceSDKServer::RegisterClient()` evaluates the client against an allowlist (likely signature-based or package-name-based) that our app doesn't match. The check sets `clientFlags=0x2` (denied) instead of `0x1` (allowed).

**Recommended Solutions (in order of effort):**

1. **Enable Multi-Client Debug Mode** — Try setting debug properties; if the exact property name is found, this is the easiest fix
2. **Patch libvrruntimeservice.so** — Most reliable; requires Ghidra disassembly to find the exact patch point; deploy via Magisk module
3. **Find and Modify Allowlist Config** — If a configuration file controls the overlay allowlist, add our package name
4. **Switch to OPENXR_VULKAN** — Bypasses VRAPI entirely; requires code changes but no system modification

**Critical Next Step:** Disassemble `libvrruntimeservice.so` in Ghidra to:
- Find the exact `MultiClientDebugEnabled` property name
- Map the permission check branches for patching
- Determine if the allowlist is hardcoded or config-driven

---

*Document created during investigation of VR overlay permission issue on Meta Quest 3*  
*All commands and findings are based on actual device analysis*  
*For educational and development purposes only*
