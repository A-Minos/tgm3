#!/usr/bin/env bash
# Build only the original Ogg/WAV module and its diagnostic for Android.
# Usage: ./build_android.sh /path/to/android-ndk-r28 /path/to/new-or-owned-output
# No game, upstream compatibility code, credentials, or assets are used.
set -euo pipefail
if [[ $# != 2 ]]; then
  echo "Usage: $0 NDK_ROOT OUTPUT_DIRECTORY" >&2
  exit 2
fi
SRC=$(cd -- "$(dirname -- "$0")" && pwd)
NDK=$(cd -- "$1" && pwd)
OUT=$2
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || {
  echo 'This script currently supports a Linux x86_64 build host only.' >&2; exit 2;
}
[[ -f "$NDK/source.properties" ]] || { echo 'Missing NDK source.properties' >&2; exit 2; }
grep -Eq '^Pkg.Revision = 28\.' "$NDK/source.properties" || {
  echo 'Use an official Android NDK r28 installation for this reproducible recipe.' >&2; exit 2;
}
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p -- "$OUT"
OUT=$(cd -- "$OUT" && pwd)
for target in aarch64 x86_64; do
  if [[ $target == aarch64 ]]; then ABI=arm64-v8a; else ABI=x86_64; fi
  CC="$TC/${target}-linux-android26-clang"
  [[ -x "$CC" ]] || { echo "Missing compiler: $CC" >&2; exit 2; }
  DIR="$OUT/$ABI"; mkdir -p -- "$DIR"
  COMMON=(-O2 -g0 -std=c11 -fPIC "-ffile-prefix-map=$SRC=." "-fdebug-prefix-map=$SRC=." -I "$SRC")
  STRICT=(-Wall -Wextra -Wpedantic -Werror)
  LINK=(-Wl,--no-undefined -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--build-id=sha1)
  "$CC" "${COMMON[@]}" "${STRICT[@]}" -c "$SRC/ogg_wav.c" -o "$DIR/ogg_wav.o"
  # The pinned third-party decoder is compiled separately from strict project diagnostics.
  "$CC" "${COMMON[@]}" -c "$SRC/vorbis_decoder.c" -o "$DIR/vorbis_decoder.o"
  "$CC" -shared "${LINK[@]}" -Wl,-soname,libtgmogg.so "$DIR/ogg_wav.o" "$DIR/vorbis_decoder.o" -lm -o "$DIR/libtgmogg.so"
  "$CC" "${COMMON[@]}" "${STRICT[@]}" "$SRC/verify_loader.c" "$DIR/ogg_wav.o" "$DIR/vorbis_decoder.o" "${LINK[@]}" -lm -o "$DIR/verify_loader"
done
python3 - "$SRC" "$OUT" <<'PY'
import hashlib, json, pathlib, struct, sys
src,out=map(pathlib.Path,sys.argv[1:]); report={'android_api':26,'ndk_major':28,'executed_on_android':False,'files':[]}
for abi,machine in [('arm64-v8a',183),('x86_64',62)]:
    for name in ['libtgmogg.so','verify_loader']:
        p=out/abi/name; b=p.read_bytes()
        if len(b)<64 or b[:6]!=b'\x7fELF\x02\x01' or struct.unpack_from('<H',b,18)[0]!=machine:
            raise SystemExit('Unexpected ELF format or machine: '+str(p))
        phoff=struct.unpack_from('<Q',b,32)[0]; entsz,count=struct.unpack_from('<HH',b,54)
        if entsz!=56 or phoff+entsz*count>len(b): raise SystemExit('Invalid ELF program headers: '+str(p))
        loads=[]
        for i in range(count):
            typ,flags,off,addr,_,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',b,phoff+i*entsz)
            if typ!=1: continue
            if align<16384 or align&(align-1) or off%16384!=addr%16384 or filesz>memsz or off+filesz>len(b):
                raise SystemExit('Invalid or less-than-16-KiB LOAD alignment: '+str(p))
            loads.append({'offset':off,'virtual_address':addr,'alignment':align})
        if not loads: raise SystemExit('No LOAD segments: '+str(p))
        report['files'].append({'path':f'{abi}/{name}','bytes':len(b),'sha256':hashlib.sha256(b).hexdigest(),'load_segments':loads})
        print(f'PASS {abi}/{name}: ELF machine and {len(loads)} LOAD segments, 16 KiB alignment')
report['source_sha256']={n:hashlib.sha256((src/n).read_bytes()).hexdigest() for n in ['ogg_wav.c','ogg_wav.h','vorbis_decoder.c','stb_vorbis.c','verify_loader.c','build_android.sh']}
(out/'android-build-report.json').write_text(json.dumps(report,indent=2)+'\n')
PY
