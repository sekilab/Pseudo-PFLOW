#!/bin/bash
# Build C KD-tree native library for macOS
# Produces: lib/libpflownn.dylib
set -e

cd "$(dirname "$0")"

JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "$JAVA_HOME")
if [ -z "$JAVA_HOME" ]; then
    echo "ERROR: JAVA_HOME not found. Install JDK 21: brew install openjdk@21"
    exit 1
fi

echo "[BUILD] JAVA_HOME: $JAVA_HOME"
echo "[BUILD] Compiling C KD-tree..."

mkdir -p lib

clang -shared -fPIC -O2 -Wall \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/darwin" \
    -o lib/libpflownn.dylib \
    src/kdtree.c src/jni_bridge.c

echo "[BUILD] Success: lib/libpflownn.dylib ($(wc -c < lib/libpflownn.dylib | tr -d ' ') bytes)"
