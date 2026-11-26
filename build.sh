#!/data/data/com.termux/files/usr/bin/bash
set -e
cd ~/hunt-apk
rm -rf obj classes.dex hunt.unsigned.apk hunt.apk
mkdir -p obj
javac -source 8 -target 8 -classpath android.jar -d obj \
  src/ai/venice/hunt/MainActivity.java
if command -v d8 >/dev/null 2>&1; then
  d8 --min-api 21 --lib android.jar --output . obj/ai/venice/hunt/MainActivity.class
else
  (cd obj && dx --dex --output=../classes.dex .)
fi
aapt package -f -M AndroidManifest.xml -I android.jar -F hunt.unsigned.apk
aapt add hunt.unsigned.apk classes.dex
[ -f hunt.ks ] || keytool -genkeypair -keystore hunt.ks -alias hunt \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass hunt123 -keypass hunt123 -dname "CN=hunt"
apksigner sign --ks hunt.ks --ks-pass pass:hunt123 --key-pass pass:hunt123 \
  --out hunt.apk hunt.unsigned.apk
echo "toiri: $(pwd)/hunt.apk"
