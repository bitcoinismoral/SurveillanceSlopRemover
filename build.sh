#!/bin/zsh
# Surveillance Slop Remover — open-source-only build (no proprietary Android SDK components).
#   javac  : any OpenJDK 17+ (Azul Zulu on macOS)               — GPLv2 + Classpath Exception
#   d8     : R8/D8 from r8.googlesource.com (Google Maven)       — BSD-3 / Apache-2.0
#   signing: apksig from AOSP (Google Maven)                     — Apache-2.0
#   android.jar / aapt2 are NOT used: compile-time stubs (checked against AOSP's API list by
#   verify_stubs.py) and buildtools/ResEncoder.java replace them.
set -euo pipefail
ROOT="$HOME/XLinkCleaner-build"
SRC="$(cd "$(dirname "$0")" && pwd)"   # this repository
OSS="$ROOT/oss"
OUT="$ROOT/oss-out"
APK="$ROOT/SurveillanceSlopRemover.apk"
export JAVA_HOME="$(echo $ROOT/tools/jdk/zulu*/zulu-17.jdk/Contents/Home)"
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf "$OUT"; mkdir -p "$OUT"/{stubs,classes,dex,bt,test,apk}

echo "== 1/8 compile Android API stubs (compile-time only, never packaged)"
javac --release 8 -nowarn -Xlint:-options -d "$OUT/stubs" $(find "$SRC/stubs" -name '*.java')

echo "== 2/8 compile app"
javac --release 8 -nowarn -Xlint:-options -cp "$OUT/stubs" -d "$OUT/classes" $(find "$SRC/app" -name '*.java')

echo "== 3/8 tests: link cleaner + photo metadata stripper"
javac -nowarn -cp "$OUT/classes" -d "$OUT/test" "$SRC/buildtools/LinkCleanerTest.java" "$SRC/buildtools/StripperTest.java"
java -cp "$OUT/classes:$OUT/test" LinkCleanerTest

echo "== 4/8 dex (D8)"
java -cp "$OSS/r8.jar" com.android.tools.r8.D8 --release --min-api 29 \
  --lib "$JAVA_HOME" --classpath "$OUT/stubs" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "== 5/8 resources: manifest, adaptive icon, resources.arsc"
javac -nowarn -d "$OUT/bt" "$SRC/buildtools/ResEncoder.java"
mkdir -p "$OUT/apk/res" && cp -R "$SRC/res/." "$OUT/apk/res/"
rm -f "$OUT/apk/res/drawable-anydpi-v26/ic_launcher.xml"   # text version is for Gradle; ResEncoder writes the binary one
java -cp "$OUT/bt" ResEncoder "$OUT/apk"
cp "$OUT/dex/classes.dex" "$OUT/apk/"

echo "== 6/8 package (resources.arsc + PNGs stored uncompressed, as Android 11+ requires)"
(cd "$OUT/apk" && zip -q -X -9 "$OUT/unsigned.apk" AndroidManifest.xml classes.dex res/drawable-anydpi-v26/ic_launcher.xml \
  && zip -q -X -0 "$OUT/unsigned.apk" resources.arsc $(find res -name '*.png' | sort))

echo "== 7/8 signing key"
KS="$ROOT/signing/release.p12"; PWF="$ROOT/signing/password.txt"
if [ ! -f "$KS" ]; then
  mkdir -p "$ROOT/signing"; chmod 700 "$ROOT/signing"
  openssl rand -hex 16 > "$PWF"; chmod 600 "$PWF"
  keytool -genkeypair -storetype PKCS12 -keystore "$KS" -alias xlinkcleaner -keyalg RSA -keysize 4096 \
    -validity 36500 -storepass:file "$PWF" -keypass:file "$PWF" -dname "CN=Link Cleaner" >/dev/null 2>&1
  chmod 600 "$KS"
fi

echo "== 8/8 sign + verify (apksig), check alignment"
javac -nowarn -cp "$OSS/apksig.jar" -d "$OUT/bt" "$SRC/buildtools/Sign.java"
java -cp "$OSS/apksig.jar:$OUT/bt" Sign "$OUT/unsigned.apk" "$APK" "$KS" "$PWF"
python3 - "$APK" <<'EOF'
import sys, zipfile, struct
z = zipfile.ZipFile(sys.argv[1]); f = open(sys.argv[1], 'rb'); bad = []
for i in z.infolist():
    if i.compress_type == zipfile.ZIP_STORED:
        f.seek(i.header_offset); h = f.read(30)
        n, m = struct.unpack('<HH', h[26:30]); data = i.header_offset + 30 + n + m
        if data % 4: bad.append(i.filename)
arsc = z.getinfo('resources.arsc')
print("resources.arsc stored:", arsc.compress_type == zipfile.ZIP_STORED, "| unaligned stored entries:", bad or "none")
sys.exit(1 if bad or arsc.compress_type != zipfile.ZIP_STORED else 0)
EOF
ls -la "$APK"
echo BUILD_OK
