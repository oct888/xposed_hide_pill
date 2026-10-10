# HideNavPill (`com.oct.hidepill`)

An Xposed/LSPosed module designed for modern Pixel devices running Android 14+ / 16 (Baklava). It cleanly hides the gesture navigation bar pill and the keyboard dismiss down arrow (IME back button) without utilizing filesystem overlay mounts (such as Magisk/KernelSU mount overlays or RRO modules).

## Features
- **Hide Navigation Pill & Eliminate Dead Space**:
  - Pixel Launcher / Taskbar (`StashedHandleView`, `StashedHandleViewController`, `TaskbarDragLayer`).
  - Zeroes out navigation bar window dimensions and reported insets (`TaskbarInsetsController`, `TaskbarStashController`, `TaskbarActivityContext`), removing the bottom dead space across all apps without breaking swipe-up or back gestures.
  - SystemUI (`NavigationHandle`).
  - IME navigation bar (`android.inputmethodservice.navigationbar.NavigationHandle`).
- **Hide Keyboard Dismiss Arrow**:
  - Suppresses down arrow and IME switcher button in Pixel Launcher Taskbar (`NavbarButtonsViewController.addButton`).
  - SystemUI gestural layout (`NavigationBarInflaterView.getDefaultLayout`).
  - InputMethodService Navigation Bar (`android.inputmethodservice.navigationbar.NavigationBarInflaterView` & `NavigationBarView.updateNavButtonIcons`).
- **Mountless**: Operates purely in-memory via hook interception, avoiding filesystem mount detections and mount namespace unmounting issues under KernelSU / APatch / SUSFS.

## Target Scopes
- `com.google.android.apps.nexuslauncher`
- `com.android.launcher3`
- `com.android.systemui`
- `com.google.android.inputmethod.latin` (Gboard)

## Building
To build and sign the APK:
```bash
./build.sh
```
Requires `aapt2`, `javac`, `d8`, `zipalign`, and `apksigner` in your `$PATH`.
