# USB Camera AutoView 1.1
Android app for full-frame USB UVC preview, adjustable parking guide overlay, horizontal image flip, optional system-wide landscape mode, and boot auto-launch attempt.

Target: Samsung Galaxy Note 20 Ultra (USB OTG, UVC camera).
Stack: Kotlin, Jetpack Compose, DroidUVC 0.2.1.

## Automated APK build
GitHub Actions builds the debug APK on every push to `build/usb-camera-autoview-1.0`.
The workflow artifact is named `USB-Camera-AutoView-debug` and contains `app-debug.apk`.

## Local build
JDK 17, Android SDK 35, Gradle 8.9:
`gradle :app:assembleDebug`

## Install / first run
1. Install APK and open once.
2. Connect UVC camera through a USB-C OTG adapter.
3. Accept Android Camera and USB permission prompts.
4. For reliable opening after reboot on Samsung One UI, set USB Camera AutoView as the default Home app if the device offers that option.

## Limitations
Android may block background Activity launches from BOOT_COMPLETED. The receiver attempts launch, and the Home-app option is the fallback. USB permission may need confirmation again after reboot or reconnect. UVC compatibility and power depend on the actual camera and OTG adapter.
