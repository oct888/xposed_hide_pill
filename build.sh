#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="${SCRIPT_DIR}"
BUILD_DIR="${WORK_DIR}/build"
ANDROID_JAR="${WORK_DIR}/../android-all.jar"
API_JAR="${WORK_DIR}/../api-82.jar"
KEYSTORE="${WORK_DIR}/../debug.keystore"
OUT_APK="${WORK_DIR}/HideNavPill.apk"

# Check dependencies
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android-all.jar not found at $ANDROID_JAR"
    exit 1
fi

if [ ! -f "$API_JAR" ]; then
    echo "Error: api-82.jar not found at $API_JAR"
    exit 1
fi

echo "==> Cleaning build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex"

echo "==> Compiling resources..."
aapt2 compile --dir "$WORK_DIR/res" -o "$BUILD_DIR/resources.zip"

echo "==> Linking APK..."
aapt2 link -o "$BUILD_DIR/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$WORK_DIR/AndroidManifest.xml" \
    "$BUILD_DIR/resources.zip" \
    --auto-add-overlay

echo "==> Compiling Java sources..."
javac -cp "$ANDROID_JAR:$API_JAR" \
    -d "$BUILD_DIR/classes" \
    "$WORK_DIR/src/com/oct/hidepill/MainHook.java"

echo "==> Dexing classes..."
d8 --lib "$ANDROID_JAR" --classpath "$API_JAR" \
    --output "$BUILD_DIR/dex" \
    "$BUILD_DIR/classes/com/oct/hidepill/"*.class

echo "==> Packaging APK..."
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unaligned.apk"
(cd "$BUILD_DIR/dex" && zip -u "$BUILD_DIR/unaligned.apk" classes.dex)
(cd "$WORK_DIR" && zip -u "$BUILD_DIR/unaligned.apk" assets/xposed_init)

echo "==> Zipaligning (16KB page alignment)..."
zipalign -f -p 16 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

echo "==> Signing APK..."
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign --ks "$KEYSTORE" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out "$OUT_APK" "$BUILD_DIR/aligned.apk"

apksigner verify -v "$OUT_APK"
echo "==> Built successfully: $OUT_APK"
