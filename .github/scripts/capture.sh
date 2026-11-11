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
adb shell am start -W -n "$PKG/$PKG.MainActivity" --ez dotos.demo true --ez dotos.demoWidgets true
sleep 20
shot() { adb exec-out screencap -p > "shots/$1.png"; }
adb shell input keyevent KEYCODE_HOME; sleep 4
shot 01-home
for i in 02 03 04 05 06; do
  adb shell input swipe 900 1300 150 1300 250; sleep 3
  shot "$i-page"
done
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell input swipe 540 1900 540 500 280; sleep 3
shot 10-drawer
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1720 540 1720 1200; sleep 2
shot 11-edit-sheet
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -W -n "$PKG/$PKG.SettingsActivity"; sleep 4
shot 12-settings
adb shell am start -W --activity-clear-task -n "$PKG/$PKG.SettingsActivity" --es dotos.settings.screen customizer; sleep 4
shot 13-customizer
adb shell am start -W --activity-clear-task -n "$PKG/$PKG.SettingsActivity" --es dotos.settings.screen icons; sleep 4
shot 14-icons
adb shell am start -W --activity-clear-task -n "$PKG/$PKG.SettingsActivity" --es dotos.settings.screen layout; sleep 4
shot 15-layout
adb logcat -d -s AndroidRuntime:E > shots/crashes.txt || true
exit 0
