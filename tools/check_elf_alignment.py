#!/usr/bin/env python3
"""Check ELF64 little-endian arm64 PT_LOAD layout for 16 KiB compatibility."""
import pathlib
import struct
import sys

def check(data):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("expected little-endian ELF64")
    if struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError("expected AArch64 machine")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", data, 54)
    if phsize < 56 or not phnum or phoff + phsize * phnum > len(data):
        raise ValueError("invalid program-header table")
    loads = 0
    for index in range(phnum):
        p = phoff + index * phsize
        kind, flags, offset, vaddr, paddr, filesz, memsz, align = struct.unpack_from("<IIQQQQQQ", data, p)
        if kind != 1:
            continue
        loads += 1
        if align < 16384 or align & (align - 1) or (offset - vaddr) % 16384:
            raise ValueError(f"PT_LOAD {index} is not 16 KiB compatible")
        if filesz > memsz or offset + filesz > len(data):
            raise ValueError(f"PT_LOAD {index} extends beyond file/memory")
    if not loads:
        raise ValueError("no PT_LOAD segments")
    return loads

if __name__ == "__main__":
    try:
        path = pathlib.Path(sys.argv[1])
        count = check(path.read_bytes())
    except (IndexError, OSError, ValueError, struct.error) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
    print(f"PASS: {path.name}: {count} ARM64 LOAD segments, 16 KiB compatible")
