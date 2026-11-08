# Implementation Plan — Authentic OS Permissions & Non-Intrusive Engine Lifecycle

Eliminate false-positive permission indicators by binding standard Android permissions directly to real-time OS APIs, while maintaining persistent OEM autostart (`BOOT`) memory, non-intrusive fallbacks, and preventing unsolicited Settings redirects.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Strict Separation: Authentic OS Checks vs. Persistent OEM Autostart**
> - Standard Android permissions (`Overlay`, `Accessibility / Lock`, `Write Settings`, `DND`, and standard `Battery Optimizations`) will **never** be faked via `SharedPreferences`. Their status badges and checkmarks (`✓`) will strictly reflect actual OS permission state refreshed on `ON_RESUME`.
> - The OEM **"BOOT"** button remains permanently persisted via `PuckPreferences.isBatteryOptPermanentlyGranted` once tapped, because Android provides no public API to query OEM-specific AutoStart permission tables (MIUI, ColorOS, EMUI, etc.).
> - Unsolicited automatic popups and jarring Settings redirects on service launch, reboot, or puck interaction are permanently suppressed; missing permissions trigger subtle, non-intrusive Toasts rather than forceful screen switches.

---

### 1. Overview & Core Concept

* **The Problem**: Storing standard permissions as permanently granted in `PuckPreferences` causes the UI to show `✓ Active` even if the user backs out of the system settings screen without enabling the permission or if the system revokes it. Conversely, relying purely on an in-memory accessibility service pointer (`instance != null`) causes the app to erroneously prompt for permissions after phone reboots.
* **The Solution**: 
  1. Inspect the Android OS directly via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and official system APIs for true status.
  2. Maintain `PuckPreferences.isBatteryOptPermanentlyGranted` strictly for the OEM BOOT/AutoStart button.
  3. Ensure `PuckAccessibilityService` is resilient across reboots with `android:stopWithTask="false"` and `android:directBootAware="true"`.
  4. Ensure manual taps on permission cards always open system settings without being blocked.

---

### 2. User Experience & Visual Design

* **Permission Cards (`SystemPermissionsCard.kt` & `HeroStatusCard.kt`)**:
  - **Authentic Statuses**: Badges and `✓` indicators show true system state. If a permission is genuinely missing in Android settings, the row accurately displays the action button (`Grant`, `Accessibility`, `Admin`), preventing false-positive "Active" states.
  - **BOOT Button**: Displays `BOOT` on fresh install. Once clicked, it immediately triggers the OEM autostart dialog, permanently stores the action in `PuckPreferences`, and displays `✓` (same compact 12.sp badge size).
  - **Manual Settings Access**: Clicking on any permission row or button always allows the user to navigate to the system settings page whenever they choose.
* **Non-Intrusive Floating Puck Actions**:
  - When locking the screen or capturing a screenshot from the floating puck:
    - If `isAccessibilityServiceEnabled(context)` is true in `Settings.Secure`, dispatch the action immediately. If the in-memory service reference is reconnecting right after boot, retry silently without throwing the user into Settings.
    - If the permission is genuinely disabled in system settings, show a brief, polite Toast (`⚠️ Accessibility service is disabled in settings`) without launching full-screen intents uninvited.

---

### 3. Key Technical Decisions & Architecture

#### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE & CARDS                          │
│                                                                        │
│   HeroStatusCard.kt                 SystemPermissionsCard.kt           │
│   ┌───────────────────────────┐     ┌────────────────────────────────┐ │
│   │ Real-time OS Badges       │     │ Overlay: Settings.canDraw...   │ │
│   │ (Refreshed on ON_RESUME)  │     │ DND: nm.isPolicyGranted        │ │
│   │ Overlay | DND | Settings  │     │ Settings: Settings.canWrite    │ │
│   │ Battery: OS isIgnoringOpt │     │ Lock: Secure.ENABLED_ACC...    │ │
│   └─────────────┬─────────────┘     │ BOOT: PuckPrefs.isBootGranted  │ │
│                 │                   └───────────────┬────────────────┘ │
└─────────────────┼───────────────────────────────────┼──────────────────┘
                  │                                   │
                  ▼                                   ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     AUTHENTIC OS PERMISSION       │ │      PERSISTENT OEM STORAGE       │
│           INSPECTOR               │ │        (PuckPreferences)          │
│                                   │ │                                   │
│  • Settings.Secure (Accessibility)│ │  • isBatteryOptPermanentlyGranted │
│  • Settings.canDrawOverlays()     │ │    (OEM AutoStart / BOOT button)  │
│  • Settings.System.canWrite()     │ │  • hasShownInitialPrompt          │
│  • NotificationManager (DND)      │ │    (Suppresses auto-popups)       │
│  • PowerManager (Battery Opt)     │ │                                   │
└─────────────────┬─────────────────┘ └───────────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      BACKGROUND ENGINE & ACTIONS                       │
│                                                                        │
│   LockScreenHelper.kt               FloatingPuckService.kt             │
│   • Checks Settings.Secure string   • Executes lock/screenshot direct  │
│   • Silent re-bind handling         • Non-intrusive Toast if disabled  │
│   • No automatic Settings redirects • Zero forced activity launch     │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed Changes by Component:

1. **`AndroidManifest.xml`**:
   - Verify `PuckAccessibilityService` declaration has `android:stopWithTask="false"` and `android:directBootAware="true"` to ensure persistent accessibility availability across process lifecycle and boot.

2. **`PuckPreferences.kt`**:
   - Retain `isBatteryOptPermanentlyGranted` exclusively for the OEM "BOOT" button.
   - Retain `hasShownInitialPrompt` to prevent automated first-launch spam.
   - Remove faked flags (`isOverlayPermanentlyGranted`, `isWriteSettingsPermanentlyGranted`, `isDndPermanentlyGranted`, `isLockPermanentlyGranted`, `isAccessibilityPermanentlyGranted`) to prevent false-positive UI badges.

3. **`LockScreenHelper.kt`**:
   - Implement authentic OS verification using `Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)` parsed with `ComponentName`.
   - In `isLockPermissionGranted()`: returns `true` if accessibility service component is listed in `Settings.Secure` OR `DevicePolicyManager.isAdminActive()`.
   - In `performLock()`:
     - If accessibility service is enabled in settings, invoke `PuckAccessibilityService.lockScreen()`.
     - If service binding is briefly null after reboot, wait/retry silently.
     - Fallback to active Device Admin if configured.
     - Never launch system settings automatically.
   - In `performScreenshot()`: dispatch via accessibility service without popping settings dialogs.

4. **`SystemPermissionsCard.kt` & `HeroStatusCard.kt`**:
   - Bind all standard permission rows (`Overlay`, `Settings`, `Lock & Screenshot`, `DND`) strictly to real-time OS checks.
   - Bind `BackgroundRunRow` BOOT button state to `PuckPreferences.isBatteryOptPermanentlyGranted`. Once clicked, it calls battery opt & autostart intents, sets the preference flag to `true`, and permanently renders the green `✓`.
   - Keep manual click listeners active so users can deliberately click any row to re-open settings whenever they wish.

5. **`FloatingPuckService.kt`**:
   - Update `lockScreen()` and `takeInstantScreenshot()`: if genuine system permission is off, show a brief Toast rather than opening Settings automatically.
   - Keep DND and Write Settings execution clean and non-intrusive.

---

### 4. Verification Plan

1. **Static Analysis & Compilation**:
   - Run `compile_applet` to ensure zero compilation or type errors.
2. **Unit & Logic Tests**:
   - Run `gradle :app:testDebugUnitTest` to verify test suite and Robolectric tests pass.
3. **Behavioral Checks**:
   - Verify standard permissions show `Grant` / `Accessibility` if disabled in system, and switch to `✓` when enabled in system.
   - Verify `BOOT` button turns into `✓` after being tapped and retains `✓` across app restarts.
   - Verify all checkmarks in `SystemPermissionsCard` are visually aligned at the identical position with unified `GrantedCheckBadge` (36x30 dp) and 8dp spacing.
   - Verify floating puck actions do not force-launch Settings intents unprompted.
