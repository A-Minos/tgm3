#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build-host
flags=(-std=c11 -Wall -Wextra -Werror -Iinclude)
src=(native/guest_pages.c native/guest_pages_selftest.c)
for name in guest_pages randomized host_failures; do
    link=()
    if [[ "$name" == host_failures ]]; then link=(-Wl,--wrap=mprotect -Wl,--wrap=madvise); fi
    gcc -O2 "${flags[@]}" "${src[@]}" "tests/test_${name}.c" "${link[@]}" -o "build-host/test_${name}"
    "build-host/test_${name}" | tee "build-host/${name}.log"
    gcc -O1 -g "${flags[@]}" -fsanitize=address,undefined -fno-omit-frame-pointer "${src[@]}" "tests/test_${name}.c" "${link[@]}" -o "build-host/test_${name}_san"
    ASAN_OPTIONS=detect_leaks=0 "build-host/test_${name}_san" | tee "build-host/${name}-san.log"
done
gcc -O2 "${flags[@]}" "${src[@]}" tests/test_host_protection.c -o build-host/test_host_protection
./build-host/test_host_protection | tee build-host/host_protection.log
if [[ -f tests/test_probe_game.py ]]; then python3 -m unittest discover -s tests -p 'test_*.py' -v; fi
