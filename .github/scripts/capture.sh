#!/usr/bin/env bash
# Drives the launcher through its main screens and saves PNGs in shots/.
set -x
mkdir -p shots
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
PKG=com.dotos.launcher
adb install -r -g "$APK"
adb shell cmd package set-home-activity "$PKG/$PKG.MainActivity"
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell am start -W -n "$PKG/$PKG.MainActivity" --ez dotos.demo true
sleep 12
shot() { adb exec-out screencap -p > "shots/$1.png"; }
adb shell input keyevent KEYCODE_HOME; sleep 4
shot 01-home
adb shell input swipe 900 1300 150 1300 250; sleep 3
shot 02-page2
adb shell input swipe 900 1300 150 1300 250; sleep 3
shot 03-app-library
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell input swipe 540 1900 540 500 280; sleep 3
shot 04-drawer
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input swipe 540 1400 540 1400 1200; sleep 2
shot 05-edit-sheet
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -W -n "$PKG/$PKG.SettingsActivity"; sleep 4
shot 06-settings
adb shell am start -W -n "$PKG/$PKG.SettingsActivity" --es dotos.settings.screen customizer; sleep 4
shot 07-customizer
adb logcat -d -s AndroidRuntime:E > shots/crashes.txt || true
exit 0
