#!/usr/bin/env bash
# Установка sch.apk на подключённый по adb телефон + импорт cookies с ПК.
set -euo pipefail
cd "$(dirname "$0")"

PKG="ru.sch.diary"
APK="sch.apk"
COOKIES="${SCH_COOKIES:-$HOME/.cache/sch/cookies.json}"

[ -f "$APK" ] || { echo "нет $APK — сначала ./build.sh" >&2; exit 1; }

adb wait-for-device
echo "== установка"
adb install -r "$APK"

if [ -f "$COOKIES" ]; then
    echo "== cookies"
    adb shell mkdir -p "/sdcard/Android/data/$PKG/files"
    adb push "$COOKIES" "/sdcard/Android/data/$PKG/files/cookies.json"
fi

echo "== запуск"
adb shell am start -n "$PKG/.MainActivity"
