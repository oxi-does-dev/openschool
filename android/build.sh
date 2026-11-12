#!/usr/bin/env bash
# Сборка sch.apk без Gradle: aapt2 + javac + d8 + zipalign + apksigner.
set -euo pipefail

cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
OUT="$PWD/build"
KS="$PWD/keystore/sch.jks"
KS_PASS="${SCH_KS_PASS:-}"
KS_ALIAS="${SCH_KS_ALIAS:-sch}"
KS_PASS_FILE="$(dirname "$KS")/password.txt"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$PLATFORM"; do
    [ -e "$f" ] || { echo "нет $f — установите Android SDK" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile"
"$BT/aapt2" compile --dir res -o "$OUT/res/compiled.zip"

echo "== aapt2 link"
"$BT/aapt2" link \
    -o "$OUT/unsigned.apk" \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    -A "$PWD/assets" \
    --java "$OUT/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    --auto-add-overlay \
    "$OUT/res/compiled.zip"

echo "== javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -encoding UTF-8 \
    -classpath "$PLATFORM" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

echo "== d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 26 --lib "$PLATFORM" \
    --output "$OUT/dex" @"$OUT/classes.txt"

echo "== package dex"
cp "$OUT/unsigned.apk" "$OUT/withdex.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/withdex.apk" classes.dex)

echo "== zipalign"
"$BT/zipalign" -f -p 4 "$OUT/withdex.apk" "$OUT/aligned.apk"

echo "== keystore"
if [ -z "$KS_PASS" ] && [ -f "$KS_PASS_FILE" ]; then
    KS_PASS="$(cat "$KS_PASS_FILE")"
fi
if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    if [ -z "$KS_PASS" ]; then
        KS_PASS="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
        printf '%s\n' "$KS_PASS" > "$KS_PASS_FILE"
        chmod 600 "$KS_PASS_FILE"
    fi
    keytool -genkeypair -v \
        -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=OpenSchool, OU=OpenSchool, O=OpenSchool, L=Moscow, C=RU" >/dev/null 2>&1
    echo "создан $KS; пароль в $KS_PASS_FILE"
fi
if [ -z "$KS_PASS" ]; then
    echo "нет пароля keystore: задайте SCH_KS_PASS или создайте $KS_PASS_FILE" >&2
    exit 1
fi

echo "== apksigner"
"$BT/apksigner" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" \
    --min-sdk-version 26 \
    --out "$PWD/sch.apk" \
    "$OUT/aligned.apk"

"$BT/apksigner" verify --verbose "$PWD/sch.apk" | head -5

echo
ls -lh "$PWD/sch.apk"
echo "APK: $PWD/sch.apk"
