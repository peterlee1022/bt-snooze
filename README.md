# BT Snooze

Temporarily stop paired Bluetooth devices from auto-connecting, without unpairing them.

## Use

1. Install `app/build/outputs/apk/release/app-release.apk` and grant the Nearby devices permission.
2. Tap the devices you want to snooze.
3. Turn on **Blocking on** (or use the BT Snooze Quick Settings tile).

Turning blocking off restores auto-connect for those devices and asks them to reconnect.

## How it works

For each checked device the app sets the profile connection policy to FORBIDDEN for
headset, media audio, HID, hearing aid and LE audio. That is the same setting as unticking
"Phone calls" / "Media audio" in the device's page in system Bluetooth settings, so the bond is kept.

It also listens for the device connecting (works even when the app is closed) and disconnects it.

## Limitations

Both calls are hidden Android APIs. On some Android versions/ROMs the Bluetooth service only
allows them for system apps. Each device row shows what happened on the last attempt, so you can
tell if your phone allows it. If it shows "couldn't change auto-connect", the manual fallback is
to untick the profiles in system Bluetooth settings (button at the bottom of the app).

## Build

Needs JDK 17 and the Android SDK (platform 35).

    ./gradlew assembleRelease
