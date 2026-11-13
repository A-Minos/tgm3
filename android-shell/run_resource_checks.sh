#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 35}"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
jar="$ANDROID_HOME/platforms/android-35/android.jar"
out=build/resource-tests
src=app/src/main/java/org/aminos/tgm3/privateport
mkdir -p "$out"
"$javac_bin" -cp "$jar" -d "$out" "$src/GameImporter.java" "$src/BundledResources.java" tests/ResourceManifestTest.java tests/BundledResourcesTest.java
"$java_bin" -cp "$out:$jar" org.aminos.tgm3.privateport.ResourceManifestTest
# Optional integration test, using locally supplied authorized files only.
if [[ $# -eq 2 ]]; then
 "$java_bin" -cp "$out:$jar" org.aminos.tgm3.privateport.BundledResourcesTest "$1" "$2"
elif [[ $# -ne 0 ]]; then
 echo 'Usage: run_resource_checks.sh [private-bundle.zip private-manifest]' >&2
 exit 2
fi
