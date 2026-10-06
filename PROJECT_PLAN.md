# Daymark: Master Architecture & Product Evolution Roadmap

> **Document Status:** Authoritative Master Plan & Technical Specification  
> **Repository:** `AbrarulHaque8541/ai-personal-task-assistant`  
> **Baseline Commit:** `40108bfb` (API 35 Debug, v1.0.0)  
> **Philosophy:** *Simple on the surface. Powerful underneath. Nothing unnecessary installed by default.*

---

## 1. Vision

Daymark is an **AI-native personal task system** engineered as an evolutionary ladder across three user tiers:
1. **Level 1 — Simple User:** A minimalist, lightweight, distraction-free task & reminder app. Installs in seconds (<5 MB base APK), starts immediately, stores everything encrypted on-device, and requires zero technical knowledge.
2. **Level 2 — Power User:** Tasks expand into dynamic workspaces featuring deep scheduling, subtasks, notes, timers, research tools, and local document attachments.
3. **Level 3 — Developer / AI Agent User:** Tasks become isolated, reproducible agent execution environments where AI models operate tools, navigate web information, inspect code, run terminal scripts, and automate workflows inside strict Android security sandboxes.

The application never forces complexity onto Level 1 users. It grows in **capability on demand**, not in base APK bloat.

---

## 2. Current State Audit

### 2.1 Repository Architecture & Codebase Inspection
- **Android App (`android-app/`):**
  - **Framework / Language:** Native Java 21, Android Gradle Plugin 8.8.2, Target SDK 35, Min SDK 26 (Android 8.0 Oreo).
  - **Single-Activity Architecture:** `MainActivity.java` orchestrates layout inflation, task list views, filters, and modals.
  - **Current Task Model (`TaskLogic.java`):**
    - Fields: `id` (UUID), `title` (max 160 chars), `priority` (`low` | `medium` | `high`), `dueDate` (`YYYY-MM-DD` ISO-8601), `completed` (boolean), `createdAt` (ISO-8601 Instant), `updatedAt` (ISO-8601 Instant), `attachments` (List of `AttachmentRef`).
    - Validation: Strict boundary checks; duplicate IDs rejected; snapshot serialization validated before/after decoding.
  - **Storage & Security Engine (`EncryptedTaskStore.java` & `EncryptedBlobStore.java`):**
    - Hardware-backed Android Keystore with AES-256-GCM authenticated encryption (`daymark.task-store.aes-gcm.v1`).
    - Fail-safe `AtomicFile` semantics: Staged writing with automatic `.bak` fallback. Stale/corrupt ciphertext fails closed without wiping existing records.
    - Zero plaintext storage on flash (`tasks.enc`).
    - Device transfer exclusion: `tasks.enc`, `.bak`, `.new`, and `.tmp` excluded from Android Auto Backup / Cloud transfer via `full_backup_content.xml` and `data_extraction_rules.xml`.
  - **Attachments (`AttachmentBlobStore.java`, `AttachmentContentProvider.java`):**
    - Task-associated blobs encrypted with Keystore keys and served only via private `ContentProvider`.
  - **Portability (`PortableBackupManager.java`, `PortableBackupCodec.java`):**
    - Exportable `.dmbackup` files encrypted with a one-time generated 32-byte recovery key (HKDF-SHA-256 per-record keys + AES-256-GCM); passphrase-based recovery is deferred.
  - **Update System (`android-app/app/src/main/java/com/cue/daymark/updater/` - PR #12 branch):**
    - GitHub Release client, SHA-256 hash verifier, and Storage Access Framework (`ACTION_CREATE_DOCUMENT`) staging.
  - **Verification Suite (`android-app/tools/`):**
    - 100% offline smoke tests: `check-v1-source.sh`, `run-core-tests.sh`, `run-storage-recovery-tests.sh`, `run-attachment-tests.sh`, `run-portable-backup-tests.sh`.

### 2.2 Web Companion (repository root):
  - Pure vanilla JavaScript prototype with localStorage and basic web UI. Serves as a reference implementation for desktop/browser environments.

---

## 3. Architecture Overview

Daymark's system architecture enforces clean separation between the ultra-light core, the progressive UI layer, the dynamic capability manager, and the isolated agent sandbox:

```text
+-----------------------------------------------------------------------------------+
|                                  DAYMARK CORE                                     |
|  +-------------------+  +-------------------------+  +-------------------------+  |
|  | Task System (V1)  |  | Keystore Security Model |  | Minimal Material UI     |  |
|  +-------------------+  +-------------------------+  +-------------------------+  |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                        DYNAMIC CAPABILITY MANAGER (DCM)                           |
|       [Module Downloader]   [Integrity Verifier]   [Resource Profiler]            |
|       [Dynamic Feature Loader / DEX ClassLoader / Assets On-Demand]               |
+-----------------------------------------------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +------------------+            +--------------------+
| OPTIONAL MODULES |           |  PERMISSION      |            |  AGENT RUNTIME     |
| - Web Browser    |           |  BROKER          |            |  - Context Engine  |
| - Terminal/Shell | <-------  |  - Task Scoping  |  <-------  |  - Tool Dispatch   |
| - Model Runner   |           |  - User Consent  |            |  - Virtual FS      |
| - Dev Tools      |           |  - Revocation    |            |  - Visualizer      |
+------------------+           +------------------+            +--------------------+
```

---

## 4. Capability Map & Feature Classification

Every task feature is categorized to prevent core bloat:

| Feature | Category | Rationale / Placement |
| :--- | :--- | :--- |
| **Title, Priority, Date, Status** | **CORE** | Basic task essentials; always in base APK. |
| **Time & Timezone Support** | **CORE** | Exact alarm scheduling; essential for reminders. |
| **Reminders & Notifications** | **CORE** | NotificationManager + AlarmManager integration. |
| **Undo Action & Deletion Guard** | **CORE** | Already implemented with snackbar & TalkBack accessibility. |
| **Encrypted Task Store** | **CORE** | Keystore AES-256-GCM; non-negotiable security foundation. |
| **Encrypted Attachments** | **CORE** | Already integrated (`AttachmentBlobStore`). |
| **Portable Recovery-Key Backup** | **CORE** | Already integrated (`PortableBackupManager`). |
| **Subtasks & Checklists** | **POWER USER** | Expandable within task detail view; JSON structure. |
| **Notes & Markdown Description** | **POWER USER** | Collapsible textarea inside task detail view. |
| **Recurring Tasks (Cron/Interval)**| **POWER USER** | AlarmManager trigger calculating next occurrence. |
| **Tags & Category Hierarchy** | **POWER USER** | Tag pill filters on main list. |
| **Countdowns & Work Timers** | **POWER USER** | Built-in stopwatch/Pomodoro linked to task. |
| **Task Search & Advanced Filters**| **POWER USER** | Fast on-device in-memory index over cached tasks. |
| **Task Templates & Duplication** | **POWER USER** | Stored template records for repetitive workflows. |
| **Task Archive & History** | **POWER USER** | Cold storage partition to keep active list instant. |
| **Search Engine Abstraction** | **OPTIONAL MODULE** | Pluggable DuckDuckGo, Brave, Google, Bing providers. |
| **In-App HTTPS Browser** | **OPTIONAL MODULE** | Isolated WebView environment with DOM extraction. |
| **On-Device LLM (GGUF / NPU)** | **OPTIONAL MODULE** | llama.cpp / ExecuTorch runtime; downloaded on demand. |
| **Terminal & Scripting (Python)** | **OPTIONAL MODULE** | PRoot / Userspace sandbox; developer mode only. |
| **Action Recording & Timeline** | **DEVELOPER** | Frame-buffered UI recorder and structured action logs. |
| **Autonomous Multi-Agent Planner**| **DEVELOPER** | Tool-calling state machine orchestrating subtasks. |
| **Unrestricted Android App Cloning**| **NOT FEASIBLE**| Violates Android security model; replaced with Sandboxed Web Apps. |

---

## 5. Modular / Download-On-Demand Architecture

### 5.1 Base APK Footprint Target
- **Target Size:** `< 5 MB`.
- **Zero Native Binaries in Base:** No `.so` libraries for LLMs, Python, or Chromium bundled by default.

### 5.2 Dynamic Delivery Mechanisms
1. **Play Feature Delivery (For Google Play builds):** Dynamic feature modules (`.aar` split APKs) requested via `SplitInstallManager`.
2. **GitHub Sideload Delivery (For Direct APK builds):**
   - Pure-asset & DEX package loader: Downloads cryptographic module archives (`.dpm` - Daymark Package Module).
   - Verification: Each module package is signed by Daymark's release key; SHA-256 checksum verified before mounting.
   - Dynamic Code Loading: Optional code loaded via Android `DexClassLoader` into app-private code cache directory (`code_cache/`).
   - Clean Uninstall: One-tap deletion of module directory (`/data/user/0/com.cue.daymark/modules/<module_id>`) instantly frees storage with zero residual state.

---

## 6. Model Manager Architecture

### 6.1 Hardware Capability Profiling
Before recommending any local model, Daymark profiles the host hardware:
- **Available RAM:** Evaluated via `ActivityManager.MemoryInfo`.
- **Chipset / ISA:** Detected via `android.os.Build.SUPPORTED_ABIS` (arm64-v8a required for local inference).
- **SoC & Accelerator Support:** Checks for NNAPI / Qualcomm QNN / MediaTek NeuroPilot availability.
- **Battery & Thermal State:** Throttles inference if device temperature exceeds 42°C or battery is < 20% without AC power.

### 6.2 Model Tiers
- **Tier 1 (Low-End: 3 GB - 4 GB RAM):**
  - Recommendation: Remote API / Cloud Assistant (OpenAI, Anthropic, OpenRouter, local Ollama via Wi-Fi) OR micro quantized models (e.g. Qwen2.5-0.5B-Instruct Q4_K_M ~350 MB).
- **Tier 2 (Mid-Range: 6 GB - 8 GB RAM):**
  - Recommendation: 1.5B to 3B models (e.g. Llama-3.2-1B / 3B Q4_K_M ~800 MB - 1.8 GB RAM footprint).
- **Tier 3 (High-End: 12 GB+ RAM, Snapdragon 8 Gen 2/3):**
  - Recommendation: 7B to 8B models (e.g. Qwen2.5-7B-Instruct Q4_K_M or Gemma-2-9B).

---

## 7. Small Model to Agent Abstraction

A 1B-3B model cannot handle complex Android system APIs. Daymark bridges this via a **Universal Agent Tool Schema**:

```json
{
  "name": "agent.search",
  "description": "Search the web for real-time information",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "Search keywords"}
    },
    "required": ["query"]
  },
  "risk_level": "low"
}
```

The model interacts strictly through simple, predictable JSON tool calls:
- `agent.search(query)`
- `agent.browser.open(url)`
- `agent.browser.extract_text()`
- `agent.files.read(path)`
- `agent.files.write(path, content)`
- `agent.tasks.create(title, due_date)`
- `agent.terminal.exec(command)`

---

## 8. Agent Computer Environment & Virtual Filesystem

The agent never interacts with raw Android filesystem paths (`/sdcard/`, `/data/data/...`). Instead, it operates inside a virtual root:

```text
/workspace/              <-- Root of virtual environment
  ├── tasks/<task_id>/   <-- Isolated task workspace
  │   ├── notes.md
  │   ├── downloads/
  │   └── outputs/
  ├── scratch/           <-- Ephemeral workspace discarded on reset
  └── tools/             <-- Read-only scripts and binaries
```

All virtual paths map to app-private internal storage (`context.getFilesDir() + "/agent_workspace/"`). Access to real external user storage requires explicit Android Storage Access Framework (SAF) URI authorization.

---

## 9. Security Boundary & Permission Broker

### 9.1 Three-Layer Security Boundary
1. **Android OS Boundary:** Linux UID isolation, SELinux policies, Android permissions. Daymark never seeks root or privilege escalation.
2. **Daymark Security Boundary:** Keystore hardware encryption, AtomicFile consistency, private ContentProvider.
3. **Agent Workspace Boundary:** Sandboxed process, restricted virtual filesystem, network policy filter (blocking localhost/private subnets to prevent SSRF).

### 9.2 Permission Broker Schema
Before any tool executes, the **Permission Broker** verifies:
- Requesting Task ID and Agent ID.
- Tool Risk Level:
  - **LOW (Automatic if permitted):** Search, read internal workspace files, timer.
  - **MEDIUM (User prompt on first use):** Network requests, download module, create new tasks.
  - **HIGH (Explicit confirmation required every time):** Delete files, export external data, execute shell commands, install modules.

---

## 10. Technical Feasibility Matrix

| Feature | Android Feasibility | Technical Approach | Security Risk | APK Impact | Low-End Viable? | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Core Task Management** | 100% Native | Java 21, RecyclerView, AtomicFile | None | 0 MB (Base) | Yes (100%) | **READY** |
| **Encrypted Storage & Backup**| 100% Native | Keystore AES-256-GCM + HKDF-SHA-256 | Low | 0 MB (Base) | Yes (100%) | **READY** |
| **In-App HTTPS Browser** | 100% Native | Android WebView + NetworkSecurityConfig | Medium (SSRF/XSS) | +150 KB | Yes | **PROTOTYPE (PR #13)**|
| **Release-Gated Updater** | 100% Native | GitHub API + SAF + SHA-256 Verifier | Low | +80 KB | Yes | **PROTOTYPE (PR #12)**|
| **Search Engine Provider** | 100% Native | REST API over HTTPS (DuckDuckGo, Brave) | Low | +20 KB | Yes | **READY** |
| **On-Device LLM Inference** | Fully Feasible | llama.cpp via JNI (arm64-v8a) as on-demand module | Low (Local) | 0 MB (Base) / ~30 MB module | High-end only (RAM bound)| **RESEARCH REQUIRED** |
| **Remote LLM Connector** | 100% Native | OkHttp client to OpenAI/Anthropic/Ollama | Low | +15 KB | Yes | **READY** |
| **Terminal / Shell Sandbox** | Feasible via PRoot | Termux-style userspace PRoot engine | High | 0 MB (Base) / ~40 MB module | 4 GB+ RAM only | **RESEARCH REQUIRED** |
| **Visual Agent Workspace** | 100% Native | Split-screen View / Floating Overlay | Low | +100 KB | Yes | **READY** |
| **Arbitrary Android App Cloning**| **IMPOSSIBLE** | Violates Android UID & signature boundary | Critical | N/A | No | **NOT FEASIBLE** |
| **Sandboxed Web Apps (PWA)** | 100% Native | Isolated WebView with custom ServiceWorker | Medium | +80 KB | Yes | **FEASIBLE ALTERNATIVE**|

---

## 11. Multi-Phase Implementation Roadmap

### Phase 1: Core Consolidation & Safe Update (Current Milestone)
- Stabilize base application at commit `40108bfb`.
- Merge PR #12 (In-App Updater with Wi-Fi/Mobile data toggle, release notes, and SHA-256 integrity check) and resolve Issue #14 (SAF incomplete file cleanup).
- Merge PR #13 (Isolated HTTPS In-App Browser).

### Phase 2: Power-User Task Expansion (Non-Destructive)
- Introduce collapsible task workspace cards: Subtasks, markdown notes, tags, countdown timer.
- Add `AlarmManager` exact reminder triggers with snooze and reschedule options.
- Maintain 100% backward compatibility with existing `tasks.enc` schema through additive versioning.

### Phase 3: Modular Architecture & Dynamic Package Loader
- Build the `DynamicCapabilityManager` (DCM) infrastructure.
- Implement Search Engine Abstraction (DuckDuckGo, Brave, Google).
- Create on-demand downloadable language packs (Urdu, Hindi, Arabic, etc.).

### Phase 4: Local Model Manager & Agent Runtime
- Implement Hardware Profiler (RAM/SoC benchmark).
- Integrate Remote Agent Gateway (Ollama, OpenRouter, Anthropic) as lightweight default.
- Build JNI wrapper for on-demand `llama.cpp` module for qualifying high-RAM devices.
- Deliver the Visual Agent Workspace (live operation monitor).

### Phase 5: Developer Tools & Userspace Terminal
- Provide optional downloadable PRoot/terminal module for power users.
- Add session recording, action replay timeline, and workspace snapshot/rollback.

---

## 12. AI Handoff & Governance Guidelines

Any AI agent or human contributor working on Daymark must strictly abide by these rules:
1. **Zero Data Loss Rule:** Never alter `EncryptedTaskStore` or `AttachmentBlobStore` without running regression tests against legacy ciphertext.
2. **Never Bypass Android Security:** Do not attempt private API reflection, hidden permission bypasses, or root exploits.
3. **Minimal Base Rule:** The base APK must never exceed 5 MB. All heavy runtimes must remain downloadable modules.
4. **Offline Test Verification:** Every PR must pass all offline test scripts under `android-app/tools/` before submission.
5. **No Fake Claims:** Never claim physical device testing unless executed on a real target device.
