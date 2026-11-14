#!/usr/bin/env bash
# Builds a signed APK without Gradle: javac -> d8 -> aapt2 -> zipalign -> apksigner.
# Needs JDK 17 and an Android SDK with platforms;android-28 and build-tools;34.0.0:
#   TOOLS=/c/Users/Aman/android-build-tmp ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

TOOLS=${TOOLS:?set TOOLS to the folder holding the JDK and sdk/}
JAVA_HOME=$(ls -d "$TOOLS"/jdk-17*)
BT="$TOOLS/sdk/build-tools/34.0.0"
ANDROID_JAR="$TOOLS/sdk/platforms/android-28/android.jar"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
OUT=out
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex"

# Signing key: created once and kept out of git; every update must be signed with it.
if [ ! -f release.keystore ]; then
  PASS=$(python -c "import secrets; print(secrets.token_urlsafe(18))")
  keytool -genkeypair -keystore release.keystore -alias jvcremote -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=JVC Remote" >/dev/null
  echo "$PASS" > release.keystore.pass
fi
PASS=$(cat release.keystore.pass)

"$BT/aapt2.exe" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -o "$OUT/base.apk"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find src -name '*.java')
"$BT/d8.bat" --release --min-api 21 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1],'a').write(sys.argv[2],'classes.dex')" \
  "$OUT/base.apk" "$OUT/dex/classes.dex"
"$BT/zipalign.exe" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" sign --ks release.keystore --ks-pass "pass:$PASS" \
  --out "JVC-Remote-$VERSION.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" verify "JVC-Remote-$VERSION.apk"
echo "Built JVC-Remote-$VERSION.apk ($(stat -c %s "JVC-Remote-$VERSION.apk") bytes)"
