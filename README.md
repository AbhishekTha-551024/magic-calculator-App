# 🪄 Magic Calculator Pro (V3)

> **An advanced, covert mentalism & magic performance suite disguised as an everyday Android calculator, dialer, and gallery application.**
> Built with clean architecture, strategy plugin patterns, dependency injection, and hardware-grade sensor coordination.

---

## 📑 Table of Contents
1. [Overview](#-overview)
2. [Key Highlights & Performance Modes](#-key-highlights--performance-modes)
3. [Technology Stack & Architecture](#-technology-stack--architecture)
4. [Complete Project & File Structure](#-complete-project--file-structure)
5. [In-Depth Feature & Illusion Breakdown](#-in-depth-feature--illusion-breakdown)
   - [A. Plugin Strategy Magic Engine (All 12 Illusions)](#a-plugin-strategy-magic-engine-all-12-illusions)
   - [B. Customizable 3-Digit Secret Backdoor Passcode](#b-customizable-3-digit-secret-backdoor-passcode)
   - [C. Hardware Sensor & Signal Processing Subsystem](#c-hardware-sensor--signal-processing-subsystem)
   - [D. Physics Button Explosion Engine](#d-physics-button-explosion-engine)
   - [E. Online P2P Magic Link](#e-online-p2p-magic-link)
   - [F. Dynamic Photo Prediction Overlays](#f-dynamic-photo-prediction-overlays)
   - [G. Fake Lock Screen Simulation (MoiLock)](#g-fake-lock-screen-simulation-moilock)
   - [H. Dynamic Launcher App Camouflage](#h-dynamic-launcher-app-camouflage)
6. [Monetization & Payment Infrastructure](#-monetization--payment-infrastructure)
7. [Cloud & Backend Architecture](#-cloud--backend-architecture)
8. [Security & Cryptography](#-security--cryptography)
9. [Automated Testing & Build Verification](#-automated-testing--build-verification)
10. [🤖 Future Developer & AI Continuation Guide](#-future-developer--ai-continuation-guide)

---

## 🌟 Overview

**Magic Calculator Pro** is an Android application engineered specifically for magicians and mentalists. To any spectator or casual observer, the app appears and behaves identically to a clean, native Calculator, a System Phone Dialer, or a standard Photo Gallery. 

Behind this disguise lies a set of magic performance tools, including **hardware sensor triggers, multi-device P2P cloud synchronization, dynamic photo manipulation overlays, real-time physics gravity simulations, customizable backdoor passcodes, and simulated lock-screen illusions**.

---

## 🎭 Key Highlights & Performance Modes

The app provides **5 operational modes** configured in `SettingsManager`:

| Mode ID | Mode Name | Visual Disguise | Performance Capability | Access |
| :---: | :--- | :--- | :--- | :---: |
| **`0`** | **Magic Calculator** | Modern Material / iOS Dark Calculator | Full magic engine armed, customizable backdoor (`741=`), hardware sensor triggers, P2P sync. | **Free** |
| **`1`** | **System Dialer** | Native Android Phone Dialer & Call Log | Accesses contacts/logs, allows forced spectator calling, contact predictions, and dialer magic. | **🔒 PRO** |
| **`2`** | **Real Calculator** | Pure Standard Calculator | Disables all magic logic for innocent spectator inspection. | **Free** |
| **`3`** | **Stealth Gallery** | Native Photo Gallery Suite | Photo/Album viewer with real-time dynamic clock and friend prediction badge overlays. | **🔒 PRO** |
| **`4`** | **MoiLock Screen** | System Lock Screen Simulation | Forces spectator PIN entry, secretly records PIN, and executes mathematical revelation. | **🔒 PRO** |

---

## 🛠 Technology Stack & Architecture

### Core Platform
- **Language**: Kotlin 1.9.24 (JVM Target 17)
- **Target SDK**: Android 15 (API level 35) / **Min SDK**: Android 6.0 (API level 23)
- **Build Tools**: Gradle 8.7 + Android Gradle Plugin 8.5.2

### Architecture Layers
- **Dependency Injection**: Centralized Service Locator via [`AppContainer`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/di/AppContainer.kt) (singleton management for `SettingsManager`, `SoundManager`, `HapticManager`, `MagicEngine`, `MagicRegistry`, `AuthManager`, `SubscriptionManager`).
- **Magic Plugin Engine**: Open-Closed Strategy Pattern via [`MagicTrickStrategy`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/logic/strategies/MagicTrickStrategy.kt) & [`MagicRegistry`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/logic/strategies/MagicRegistry.kt).
- **Mode Controllers**: Clean separation of concerns with [`DialerController`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/dialer/DialerController.kt) and [`CalculatorKeypadController`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/calculator/CalculatorKeypadController.kt).
- **Asynchronous**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`).
- **Lifecycle & MVVM**: `lifecycle-viewmodel-ktx:2.7.0`, `lifecycle-livedata-ktx:2.7.0`.

### Cloud & Payments
- **Firebase BoM (`33.5.1`)**: Firebase Auth (Email/Google), Cloud Firestore (P2P display sync & order verification).
- **Razorpay Checkout SDK (`1.6.41`)**: In-app purchases for Pro entitlements.
- **PhotoView (`2.3.0`)**: Multi-touch zoom & pan for microscopic photo prediction reveals.

---

## 📂 Complete Project & File Structure

```
MagicCalculatorV3/
├── app/
│   ├── build.gradle                               # App dependencies, SDK levels, BuildConfig
│   ├── google-services.json                       # Firebase configuration
│   └── src/
│       ├── test/java/com/magic/calculator/
│       │   └── MagicEngineTest.kt                 # Automated unit tests for all strategies & PINs
│       └── main/
│           ├── AndroidManifest.xml                # Permissions, Activity Aliases, Services
│           ├── assets/gallery/                    # Decoy stock images & prediction templates
│           ├── res/
│           │   ├── layout/
│           │   │   ├── activity_main.xml          # Core calculator & embedded dialer layout
│           │   │   ├── activity_secret.xml        # Secret switchboard & sensor testing lab
│           │   │   ├── dialog_profile_settings.xml# My Profile & 3-Digit Backdoor PIN modal
│           │   │   ├── dialog_toxic_time_settings.xml # Toxic Time Force modal
│           │   │   ├── dialog_ghost_typing_settings.xml # Ghost Typing Reveal modal
│           │   │   ├── dialog_ghost_recovery_settings.xml # Ghost Recovery modal
│           │   │   └── ...
│           └── java/com/magic/calculator/
│               ├── di/
│               │   └── AppContainer.kt            # Central Dependency Injection Container
│               ├── logic/
│               │   ├── strategies/
│               │   │   ├── MagicTrickStrategy.kt  # Strategy interface & execution context
│               │   │   ├── MagicTrickStrategies.kt# 12 concrete trick strategy implementations
│               │   │   └── MagicRegistry.kt       # Central trick registry & strategy lookup
│               │   ├── AuthManager.kt             # User authentication state
│               │   ├── MagicEngine.kt             # Calculation & strategy execution delegator
│               │   ├── PinManager.kt              # MoiLock PIN hashing & storage
│               │   ├── PremiumManager.kt          # Feature gating & entitlement verification
│               │   ├── SettingsManager.kt         # Per-account persistent preferences
│               │   └── SubscriptionManager.kt     # Razorpay payment & sync engine
│               ├── ui/
│               │   ├── calculator/
│               │   │   └── CalculatorKeypadController.kt # Calculator buttons & feedback controller
│               │   │   └── MagicViewModel.kt             # Arithmetic state & secret unlock
│               │   ├── dialer/
│               │   │   └── DialerController.kt    # Phone dialer, call logs & contacts controller
│               │   ├── dialogs/
│               │   │   └── SecretDialogHelper.kt  # Modal dialogs for secret configurations
│               │   ├── moilock/
│               │   │   ├── MoiLockActivity.kt     # Lock screen simulation UI
│               │   │   └── MoiLockService.kt      # Foreground service for screen-off detection
│               │   ├── physics/
│               │   │   └── PhysicsExplosionEngine.kt # Button scatter & accelerometer physics
│               │   ├── GalleryActivity.kt         # Stealth photo album browser
│               │   ├── PhotoViewerActivity.kt     # Dynamic image canvas prediction viewer
│               │   ├── PremiumActivity.kt         # Paywall subscription screen
│               │   └── SecretActivity.kt          # Backstage switchboard & hardware sensor lab
│               ├── audio/SoundManager.kt          # Sound FX synthesis & playback
│               ├── haptic/HapticManager.kt        # Haptic vibration waveforms
│               ├── p2p/P2PLinkManager.kt          # Multi-device Firestore display mirroring
│               ├── sensor/SensorCoordinator.kt    # Hardware sensor polling & trigger dispatcher
│               ├── MagicApplication.kt            # Application class & DI provider
│               └── MainActivity.kt                # Primary screen & coordinator
```

---

## 🔮 In-Depth Feature & Illusion Breakdown

### A. Plugin Strategy Magic Engine (All 12 Illusions)

All magic effects are modular strategies implementing `MagicTrickStrategy` and registered in `MagicRegistry`:

1. **Digital Flip (`DIGIT_FLIP`)**: Typing trigger number (e.g. `66`) flips instantly into forced result (e.g. `99`) on trigger or `=`.
2. **Lucky Number Force (`LUCKY`)**: Quick-select menu typing `777` forces any number 1–6 using discrete gestures.
3. **Phone Number Force (`PHONE_NUMBER_FORCE`)**: Forces any saved phone number sequence on `=` to immediately dial.
4. **Toxic Time Force (`TOXIC_TIME`)** *(Advanced / Premium)*: Forces calculated result to match live system **Time** (`HHmm`), **Date** (`ddMM`), or **Battery %**.
5. **Ghost Typing Reveal (`GHOST_TYPING`)** *(Advanced / Premium)*: Digits type themselves automatically onto the screen after a custom delay.
6. **Ghost Recovery (`REAPPEARING_RESULT`)** *(Advanced / Premium)*: Vanished arithmetic results reappear out of thin air on sensor wave.
7. **Step Reveal (`STEP_REVEAL`)**: Mathematical solution is revealed one digit at a time per touch or tilt.
8. **Contact Force (`CONTACT_FORCE`)**: Selected contact from phone directory is secretly forced as the target prediction.
9. **Toxic Gallery (`TOXIC_GALLERY`)**: Time or digits calculated in the calculator magically appear stamped on a photo inside the spectator's gallery.
10. **Inversion (`INVERSION`)**: Inverts digit patterns (e.g. `6666` $\rightarrow$ `9999`).
11. **Birthday Force (`BIRTHDAY`)**: Forces date calculations to match spectator birth dates.
12. **Mirror Force (`MIRROR`)**: Reverses digits into mirrored patterns.

---

### B. Customizable 3-Digit Secret Backdoor Passcode

* **Default PIN**: `741`
* **Customization**: Magicians can change their secret unlock code to any **3-digit number** (e.g. `987`, `123`, `555`) in the **"My Profile"** dialog panel.
* **Calculator Unlock**: Type your 3-digit code and press `=` to instantly launch `SecretActivity`.
* **Dialer Unlock**: Type your 3-digit code and press the green **Call** button to launch `SecretActivity`.
* **Persistence**: Stored per-user in `SettingsManager.secretUnlockCode`.

---

### C. Hardware Sensor & Signal Processing Subsystem

Managed by [`SensorCoordinator.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/sensor/SensorCoordinator.kt):
- **Proximity Sensor**: Wave hand over the top edge to trigger secret updates.
- **Light Sensor**: Secretly shadow or cover the ambient light sensor.
- **Magnetometer**: Detects magnetic rings or concealed magnets.
- **Accelerometer**: Detects 3 vertical shakes or directional phone tilts.
- **Orientation (Face Down)**: Laying the phone screen-down triggers revelations.
- **Gyroscope**: Spinning the phone flat on a table triggers events.
- **Hardware Volume Buttons**: Volume UP/DOWN clicks trigger covert forces in pockets.
- **Acoustic Snapping / Clapping**: Detects audio peak spikes via microphone.
- **Fingerprint Touch**: Secret long-press triggers.

---

### D. Physics Button Explosion Engine

Managed by [`PhysicsExplosionEngine.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/physics/PhysicsExplosionEngine.kt):
- Swiping on the calculator display or triggering sensors causes all buttons to physically shatter, scatter across the screen, and respond to real-time physical device gravity/tilting.
- A subsequent trigger smoothly restores all buttons or transitions into the secret gallery.

---

### E. Online P2P Magic Link

Managed by [`P2PLinkManager.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/p2p/P2PLinkManager.kt):
- Connect two devices wirelessly over Firestore without Bluetooth pairing.
- **Dual Display Mirroring**: Live keystroke updates on an accomplice's device.
- **Remote Puppet Commands**: Remotely trigger explosions, digit flips, and force values.

---

### F. Dynamic Photo Prediction Overlays

Managed by [`PhotoViewerActivity.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/PhotoViewerActivity.kt):
- Dynamically renders live calculations onto decoy photo assets using Android Canvas.
- Microscopic time clock rendering matches calculated spectator numbers.

---

### G. Fake Lock Screen Simulation (MoiLock)

Managed by [`MoiLockActivity.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/moilock/MoiLockActivity.kt) & [`MoiLockService.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/moilock/MoiLockService.kt):
- Displays a realistic Android lock screen requiring a PIN.
- Secretly captures whatever PIN the spectator attempts, displays a fake error, and opens into the calculator where the captured PIN is revealed in mathematical equations.

---

### H. Dynamic Launcher App Camouflage

Managed by [`AppIconManager.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/logic/AppIconManager.kt):
- Dynamically swaps the home screen launcher icon and name via `activity-alias` between:
  - **Calculator** (Default)
  - **Calendar 🔒 PRO**
  - **Notes 🔒 PRO**
  - **Settings 🔒 PRO**
  - **Messages 🔒 PRO**

---

### I. Interactive Multi-Stage Onboarding System

Managed by [`OnboardingManager.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/logic/OnboardingManager.kt) & [`InteractiveOnboardingDialog.kt`](file:///d:/ThertyDayProjectforCR@/GITHUBUPLOADAPP/VersionOFAllApp/MagicCalculatoreV.3/app/src/main/java/com/magic/calculator/ui/dialogs/InteractiveOnboardingDialog.kt):
- **Stage 1: Free Magician Tour (4 Steps)**:
  - 1. The Invisible Calculator Disguise
  - 2. Secret Master Backdoor PIN (`741=`)
  - 3. Core Number Forces (Digital Flip, Lucky Number, Phone Force)
  - 4. Invisible Sensor Diagnostics (Wave, Tilt, Shadow)
- **Real-Time Interactive Magician Coach (Live Practice Mode)**:
  - Guided step-by-step physical training with pulsing neon button spotlighting and floating pointer arrows.
  - **Backdoor Unlock**: Physical guidance on tapping `7` -> `4` -> `1` -> `=` with strict keypad locking (all other buttons disabled & dimmed).
  - **Secret Switchboard Setup**: Physical guidance to toggle `Digital Flip` -> Toggle `Proximity Wave` sensor trigger -> Tap top-bar `Edit` icon (pencil) -> Tap `Save Flip` (confirming `66` -> `99`) -> Tap `Back`.
  - **Live Live Performance**: User types `66` on calculator -> taps `=` -> waves hand over proximity sensor -> `66` flips to `99` live on screen!
  - **Firestore Onboarding Sync**: Saves and syncs `hasCompletedFreeOnboarding` to cloud `users/{userId}`. Replayable anytime from My Profile.
- **Stage 2: Pro VIP Masterclass (5 Advanced Steps)**:
  - 1. VIP Entitlements & Advanced Switchboard
  - 2. Toxic Time Force (Time / Date / Battery calculation forces)
  - 3. Ghost Typing & Vanishing Ghost Recovery
  - 4. Real-Time Wireless P2P Link & Accomplice Mirroring
  - 5. Stealth Gallery & MoiLock Camouflage
- **Firestore Cloud Sync**: Automatically persists `hasCompletedFreeOnboarding` and `hasCompletedProOnboarding` to user cloud profiles (`users/{userId}`).
- **On-Demand Practice Replay**: Magicians can review both tours anytime via **"Replay Basics Tour"** and **"Replay Pro Masterclass"** in **"My Profile"**.

---

## 💳 Monetization & Payment Infrastructure

- **3-Tier Subscription Plans**: 7-Day Pass, 30-Day Pro, Lifetime VIP.
- **Razorpay Integration**: Native checkout sheet with Firestore webhook confirmation.
- **Offline License Caching**: Encrypted timestamp verification in `SubscriptionManager`.
- **Sandbox Testing Tools**: Magician sandbox buttons in the Profile panel for instant testing.

---

## 🧪 Automated Testing & Build Verification

The project includes an automated JUnit test suite for all magic strategies and security validations.

### Running Unit Tests:
```powershell
.\gradlew.bat testDebugUnitTest
```
*Current Status: 100% Passed (All 12 strategies & PIN validation tests).*

### Compiling Kotlin:
```powershell
.\gradlew.bat compileDebugKotlin
```

### Installing & Restarting on Device:
```powershell
.\gradlew.bat installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.magic.calculator
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.magic.calculator/com.magic.calculator.SplashActivity
```

---

## 🤖 Future Developer & AI Continuation Guide

When starting a new conversation or working on this codebase in future sessions, follow these mandatory development rules:

### 1. Architectural Principles
1. **Single Service Locator (`AppContainer`)**:
   - Never manually instantiate `SettingsManager(context)` or `MagicEngine()` in activities.
   - Always use `context.appContainer.settingsManager` or `activity.appContainer.magicEngine`.
2. **Strategy Pattern for Magic Tricks**:
   - To add a new magic trick, **create 1 new file** implementing `MagicTrickStrategy` in `com.magic.calculator.logic.strategies` and register it in `MagicRegistry.kt`.
   - **DO NOT** add trick-specific calculation branches to `MagicEngine.kt` or `MainActivity.kt`.
3. **Decoupled Controllers**:
   - Dialer logic belongs in `DialerController.kt`.
   - Calculator keypad clicks belong in `CalculatorKeypadController.kt`.
   - `MainActivity.kt` acts solely as a top-level coordinator.
4. **ViewBinding ID Safety**:
   - When editing XML layouts, never remove or rename IDs referenced by ViewBinding without updating all controllers.
5. **Always Verify Compilation**:
   - Run `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat compileDebugKotlin` after any refactor to maintain 100% clean builds.

### 2. Quick Prompt Template for Future AI Chats:
If starting a new chat with an AI assistant, copy-paste this prompt:
```text
I am working on Magic Calculator Pro (V3).
Please read README.md first. 
Follow the existing architecture:
- Use AppContainer for dependency injection.
- Use MagicTrickStrategy & MagicRegistry for adding any magic tricks.
- Use DialerController & CalculatorKeypadController for UI logic.
- Ensure all builds pass `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat compileDebugKotlin`.
```
