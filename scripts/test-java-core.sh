#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
mkdir -p build/core-test
find src/main/java src/coreTest/java -name '*.java' > build/core-test/sources.txt
"${JAVA_BIN}javac" --release 25 -d build/core-test @build/core-test/sources.txt
"${JAVA_BIN}java" -ea -cp build/core-test dev.apexcraft.core.CoreTests
