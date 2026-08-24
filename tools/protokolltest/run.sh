#!/usr/bin/env bash
# End-zu-End-Test des Fernbedienungs-Protokolls ohne echten Beamer.
#
# Startet einen Mock-Beamer in Python (mit den Original-Protobuf-Dateien und der
# offiziellen protobuf-Laufzeit) und laesst den echten Kotlin-Client dagegen
# koppeln und Tasten senden. So wird der handgeschriebene Protobuf-Codec gegen
# eine unabhaengige Implementierung geprueft.
#
# Aufruf:  tools/protokolltest/run.sh
# Voraussetzung: JDK 17+, python3, Internetzugang (laedt Compiler und Bibliotheken).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$HERE/.work}"
LIB="$WORK/lib"
M2=https://repo1.maven.org/maven2
mkdir -p "$LIB"

fetch() { [ -f "$LIB/$2" ] || curl -sSfL --retry 3 -o "$LIB/$2" "$1"; }

echo "== Bibliotheken laden"
fetch $M2/org/jetbrains/kotlin/kotlin-compiler/2.0.21/kotlin-compiler-2.0.21.jar kotlin-compiler.jar
fetch $M2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar kotlin-stdlib.jar
fetch $M2/org/jetbrains/annotations/13.0/annotations-13.0.jar annotations.jar
fetch $M2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar trove4j.jar
fetch $M2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar coroutines.jar
fetch $M2/org/bouncycastle/bcprov-jdk18on/1.78.1/bcprov-jdk18on-1.78.1.jar bcprov.jar
fetch $M2/org/bouncycastle/bcpkix-jdk18on/1.78.1/bcpkix-jdk18on-1.78.1.jar bcpkix.jar
fetch $M2/org/bouncycastle/bcutil-jdk18on/1.78.1/bcutil-jdk18on-1.78.1.jar bcutil.jar
# Android-Stubs (Context, Build, NsdManager) ohne Android SDK
fetch $M2/org/robolectric/android-all/15-robolectric-12650502/android-all-15-robolectric-12650502.jar android-all.jar

echo "== Python-Umgebung"
[ -d "$WORK/venv" ] || python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install -q protobuf grpcio-tools cryptography
mkdir -p "$WORK/protos"
touch "$WORK/protos/__init__.py"
cp "$HERE/protos/"*.proto "$WORK/protos/"
"$WORK/venv/bin/python" -m grpc_tools.protoc -I "$HERE/protos" --python_out="$WORK/protos" \
    "$HERE/protos/polo.proto" "$HERE/protos/remotemessage.proto"
cp "$HERE/mockbeamer.py" "$WORK/"

echo "== Kotlin uebersetzen"
SRC="$ROOT/app/src/main/java/de/lightweb/fernbedienung"
CP="$LIB/kotlin-stdlib.jar:$LIB/annotations.jar:$LIB/coroutines.jar:$LIB/bcprov.jar:$LIB/bcpkix.jar:$LIB/bcutil.jar:$LIB/android-all.jar"
rm -rf "$WORK/out"
java -cp "$LIB/kotlin-compiler.jar:$LIB/kotlin-stdlib.jar:$LIB/coroutines.jar:$LIB/annotations.jar:$LIB/trove4j.jar" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -classpath "$CP" -no-stdlib -nowarn -d "$WORK/out" \
    "$SRC/proto/Proto.kt" "$SRC"/net/*.kt "$SRC"/data/*.kt "$HERE/Harness.kt"

echo "== Test laeuft"
rm -f "$WORK/client.p12" "$WORK/client.pem" "$WORK/code.txt"
RCP="$WORK/out:$LIB/kotlin-stdlib.jar:$LIB/annotations.jar:$LIB/coroutines.jar:$LIB/bcprov.jar:$LIB/bcpkix.jar:$LIB/bcutil.jar"
java -cp "$RCP" HarnessKt gencert "$WORK/client.p12" "$WORK/client.pem"

( cd "$WORK" && "$WORK/venv/bin/python" mockbeamer.py "$WORK/client.pem" > "$WORK/server.log" 2>&1; echo "EXIT=$?" >> "$WORK/server.log" ) &
SERVER=$!
sleep 2
java -cp "$RCP" HarnessKt run "$WORK/client.p12" "$WORK/code.txt"
wait $SERVER || true
cat "$WORK/server.log"
grep -q "GESAMT: BESTANDEN" "$WORK/server.log"
