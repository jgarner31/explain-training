# Grace Dialer

Small Android tablet dialer for a single Lenovo Idea Tab Plus device.

## What this app does

- Loads up to 6 contacts from the tablet's Contacts app.
- Shows a large 2x3 full-screen grid with photo or initials.
- Opens Zoiper with `zoiper:` first and falls back to `tel:` if needed.
- Keeps the screen on while the app is visible.
- Includes a boot receiver as groundwork for relaunching after restart.

## Before install

1. Put only the 6 desired contacts on the tablet.
2. Make sure Zoiper is installed and already works as the default dialer.
3. Keep the tablet plugged in.

## Build and install

1. Open this project in Android Studio on a Mac or PC with the Android SDK.
2. Let Android Studio sync Gradle dependencies.
3. Build a signed release APK.
4. Copy the APK to the tablet or host it on `https://explain.training/grace/`.
5. On the tablet, allow installs from that browser or file app once, then install the APK.

## Lock-down steps on the tablet

1. Launch the app once and allow Contacts permission.
2. In Android Developer Options, enable `Stay awake` so the screen stays on while charging.
3. Use your device-management or kiosk setup to lock the tablet into this app.

## Notes

- A true "can't ever exit" setup requires Android kiosk / dedicated-device configuration, not just the app itself.
- If you later want remote updates, the app can be extended to read a small JSON file from your server instead of the device contacts.
- The app intentionally does not try to answer or hang up calls. Zoiper handles the live call screen.
