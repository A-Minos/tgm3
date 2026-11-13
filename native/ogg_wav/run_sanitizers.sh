#!/bin/sh
# Host-only, isolated build. LSan is unavailable under the validation sandbox's
# ptrace; fd lifetime is still exercised explicitly in the synthetic suite.
set -eu
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
build=${BUILD_DIR:-"$src/.test-build/sanitize"}
mkdir -p "$build"
build=$(CDPATH= cd -- "$build" && pwd)
cc=${CC:-cc}
flags="-std=c11 -O1 -g -fno-omit-frame-pointer -fno-pie -fsanitize=address,undefined"
for name in ogg_wav vorbis_decoder verify_loader test_fd_lifecycle; do
    # Word splitting flags is intentional; paths remain quoted.
    "$cc" $flags -I "$src" -c "$src/$name.c" -o "$build/$name.o"
done
for name in verify_loader test_fd_lifecycle; do
    "$cc" -no-pie -fsanitize=address,undefined "$build/$name.o" "$build/ogg_wav.o" "$build/vorbis_decoder.o" -lm -o "$build/$name"
done
export ASAN_OPTIONS=${ASAN_OPTIONS:-detect_leaks=0:halt_on_error=1}
export UBSAN_OPTIONS=${UBSAN_OPTIONS:-halt_on_error=1}
python3 "$src/test_loader.py" "$build/verify_loader"
