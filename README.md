# ViPER4Android

Material Design 3 UI for ViPER4Android FX. This app provides the full feature set of ViPER4Android FX with a modern interface

## Features

### Audio Effects

- [x] Playback Gain Control
- [x] FET Compressor
- [x] ViPER-DDC
- [x] Spectrum Extension
- [x] FIR Equalizer
- [x] Convolver
- [x] Field Surround
- [x] Differential Surround
- [x] Headphone Surround+
- [x] Reverberation
- [x] Dynamic System
- [x] Tube Simulator
- [x] ViPER Bass
- [x] ViPER Bass Mono (original v0.5.0 ViPERBass algorithm)
- [x] ViPER Clarity
- [x] Auditory System Protection
- [x] AnalogX
- [x] Speaker Optimization

---

### App Features

- [x] Material Design 3 UI
- [x] Support for both AIDL and non-AIDL devices
- [x] Device auto-detection (Speaker & Headphone)
- [x] In app log debugging (tap the `Driver Version` 7 times in the `Settings`)
- [x] Global mode and per-app mode

## Installation

1. Download the latest APK from the [releases](https://github.com/likelikeslike/ViPER4Android/releases)
2. Install the APK on your Android device
3. Flash the module from this [repo](https://github.com/likelikeslike/ViPERFX_RE)
4. Reboot your device
5. Open the app and enable `AIDL mode` in the settings if your device use AIDL for HALs
6. Enjoy

## Q&A

- **What is Global Mode?**
  > Global Mode (previously known as "Legacy Mode") creates a single AudioEffect on session ID 0 (the global mix). The Android audio framework routes all audio through session 0, so one effect instance processes everything.

- **What is Per-App Mode?**
  > Per-App Mode creates a separate AudioEffect on each audio player's real session ID. The DSP processes each app's audio independently.

- **Why Per-App Mode requires root access?**
  > 1. The `android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast was historically sent by the Android framework (AudioFlinger) when apps opened audio sessions. On modern Android (API 34+/Android 14+), this broadcast is no longer sent by the framework.
  > 2. `AudioManager.getActivePlaybackConfigurations()`, which provides the session IDs of active audio players, however the configs are anonymized: `sessionId:0, u/pid:-1/-1` in the callback data
  > 3. Current approach: Use `AudioPlaybackCallback ` to detect when playback state changes. On change, get the session ID via `su -c "dumpsys audio | grep -E 'state:started|new player'"`, which requires root access.

## Important Notes

- This app may requires root access if:
  - You are on AIDL mode and the driver is not properly installed or configured during the module installation (to create shm for AIDL driver)
  - In in-app log debugging (to `logcat` driver's log)
  - In Per-App Mode (to get correct session id via piid)
- Please make sure the source of any modified APK is trustworthy to avoid any security risks

## Contributing

Contributions are welcome! Please open an issue or submit a pull request if you have any ideas or improvements for the app.

### Localization

If you want to help with localization, please follow [this guide](app/res-template/values-template/strings.xml)
