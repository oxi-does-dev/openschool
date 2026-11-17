#!/usr/bin/env bash
# Сборка sch.apk из исходников openschool в Termux.
set -euo pipefail
cd "$(dirname "$0")"

OUT="$PWD/build-local"
PLATFORM="$HOME/android-sdk/platforms/android-34/android.jar"
D8JAR="$HOME/android-sdk/tools/r8-9.5.32.jar"
# Ключ живёт вне build-local: иначе каждый пересбор стирал бы его вместе
# с каталогом сборки и APK нельзя было бы обновить поверх прошлого.
KS="$PWD/.keys/sch.jks"
KSP="$PWD/.keys/password.txt"

for f in "$PLATFORM" "$D8JAR"; do
  [ -e "$f" ] || { echo "нет $f" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile"
aapt2 compile --dir res -o "$OUT/res/compiled.zip"

echo "== aapt2 link"
aapt2 link \
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
    @"$OUT/sources.txt" 2>&1 | grep -v "^Note:" || true

echo "== d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$D8JAR" com.android.tools.r8.D8 --release --min-api 26 \
    --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "== package dex"
cp "$OUT/unsigned.apk" "$OUT/withdex.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/withdex.apk" classes.dex)

echo "== zipalign"
zipalign -f -p 4 "$OUT/withdex.apk" "$OUT/aligned.apk"

echo "== keystore"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  PASS="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
  printf '%s\n' "$PASS" > "$KSP"
  chmod 600 "$KSP"
  keytool -genkeypair \
      -keystore "$KS" -storepass "$PASS" -keypass "$PASS" \
      -alias sch -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=OpenSchool, OU=OpenSchool, O=OpenSchool, L=Moscow, C=RU" >/dev/null 2>&1
fi
PASS="$(cat "$KSP")"

echo "== apksigner"
apksigner sign \
    --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
    --ks-key-alias sch --min-sdk-version 26 \
    --out "$OUT/sch-local.apk" "$OUT/aligned.apk"

apksigner verify "$OUT/sch-local.apk" && echo "подпись ok"
echo
ls -lh "$OUT/sch-local.apk"