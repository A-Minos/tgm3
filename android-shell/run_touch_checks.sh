#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/touch-tests
src=app/src/main/java/org/aminos/tgm3/privateport
java_bin=java
javac_bin=javac
if [[ -n "${JAVA_HOME:-}" ]]; then
    java_bin="$JAVA_HOME/bin/java"
    javac_bin="$JAVA_HOME/bin/javac"
fi
"$javac_bin" -Xlint:all -Werror -d build/touch-tests \
    "$src/TouchLayout.java" "$src/TouchInputState.java" "$src/ViewportTransform.java" \
    tests/TouchControlsTest.java tests/ViewportTransformTest.java
"$java_bin" -cp build/touch-tests org.aminos.tgm3.privateport.TouchControlsTest
"$java_bin" -cp build/touch-tests org.aminos.tgm3.privateport.ViewportTransformTest
