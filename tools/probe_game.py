#!/usr/bin/env python3
"""Original, read-only PE32 resource preflight. No download or execution."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct


class ProbeError(ValueError):
    pass


class PE:
    def __init__(self, data):
        self.data = data
        self.sections = []
        if self.take(0, 2) != b'MZ':
            raise ProbeError('Missing DOS signature')
        pe = self.u32(0x3c)
        if pe < 64 or self.take(pe, 4) != b'PE\0\0':
            raise ProbeError('Invalid PE signature or offset')
        coff = pe + 4
        if self.u16(coff) != 0x14c:
            raise ProbeError('Expected x86 machine (0x14c)')
        count = self.u16(coff + 2)
        if not 1 <= count <= 96:
            raise ProbeError('Invalid section count')
        optional_size = self.u16(coff + 16)
        optional = coff + 20
        self.take(optional, optional_size)
        if optional_size < 96 or self.u16(optional) != 0x10b:
            raise ProbeError('Expected complete PE32 optional header')
        self.entry = self.u32(optional + 16)
        self.image_base = self.u32(optional + 28)
        self.image_size = self.u32(optional + 56)
        self.header_size = self.u32(optional + 60)
        table = optional + optional_size
        self.take(table, count * 40)
        if not table + count * 40 <= self.header_size <= len(data):
            raise ProbeError('Invalid SizeOfHeaders')
        if self.image_size < self.header_size or self.image_base + self.image_size > 2**32:
            raise ProbeError('Invalid image size/base')
        directory_count = self.u32(optional + 92)
        if directory_count > 16 or 96 + 8 * directory_count > optional_size:
            raise ProbeError('Invalid data-directory bounds')
        for i in range(count):
            off = table + 40 * i
            virtual_size, rva, raw_size, raw = struct.unpack('<IIII', self.take(off + 8, 16))
            extent = max(virtual_size, raw_size)
            if extent and (rva < self.header_size or rva + extent > self.image_size):
                raise ProbeError('Section virtual bounds outside image')
            if raw_size:
                if raw < self.header_size:
                    raise ProbeError('Section overlaps headers')
                self.take(raw, raw_size)
            for old_rva, old_extent, old_raw, old_size in self.sections:
                if extent and old_extent and rva < old_rva + old_extent and old_rva < rva + extent:
                    raise ProbeError('Overlapping virtual sections')
                if raw_size and old_size and raw < old_raw + old_size and old_raw < raw + raw_size:
                    raise ProbeError('Overlapping raw sections')
            self.sections.append((rva, extent, raw, raw_size))
        if self.entry:
            self.at_rva(self.entry, 1)
        self.imports = []
        if directory_count > 1:
            rva, size = struct.unpack('<II', self.take(optional + 104, 8))
            if bool(rva) != bool(size):
                raise ProbeError('Incomplete import directory')
            if rva:
                self.read_imports(rva, size)

    def take(self, offset, size):
        if offset < 0 or size < 0 or offset > len(self.data) or size > len(self.data) - offset:
            raise ProbeError('File range outside bounds')
        return self.data[offset:offset + size]

    def u16(self, offset):
        return struct.unpack('<H', self.take(offset, 2))[0]

    def u32(self, offset):
        return struct.unpack('<I', self.take(offset, 4))[0]

    def at_rva(self, rva, size):
        if rva < 0 or size < 0 or rva + size > self.image_size:
            raise ProbeError('RVA outside image')
        if rva < self.header_size and size <= self.header_size - rva:
            return rva
        for start, extent, raw, raw_size in self.sections:
            delta = rva - start
            if 0 <= delta < extent and size <= raw_size - delta:
                self.take(raw + delta, size)
                return raw + delta
        raise ProbeError('RVA not backed by file bytes')

    def string(self, rva):
        result = bytearray()
        for i in range(512):
            byte = self.data[self.at_rva(rva + i, 1)]
            if byte == 0:
                if not result:
                    raise ProbeError('Empty import name')
                try:
                    name = result.decode('utf-8', errors='strict')
                except UnicodeDecodeError as exc:
                    raise ProbeError('Invalid UTF-8 import name') from exc
                if any(ord(c) < 32 or ord(c) == 127 for c in name):
                    raise ProbeError('Control character in import name')
                return name
            result.append(byte)
        raise ProbeError('Unterminated or oversized import name')

    def read_imports(self, rva, size):
        if not 20 <= size <= 65536:
            raise ProbeError('Invalid import directory size')
        self.at_rva(rva, size)
        total_symbols = 0
        for index in range(size // 20):
            off = self.at_rva(rva + index * 20, 20)
            lookup, timestamp, forwarder, name, address = struct.unpack('<IIIII', self.take(off, 20))
            if not any((lookup, timestamp, forwarder, name, address)):
                return
            if not name or not address:
                raise ProbeError('Incomplete import descriptor')
            if len(self.imports) >= 256:
                raise ProbeError('Import DLL count exceeds preflight limit')
            dll = self.string(name)
            thunk = lookup or address
            symbols = []
            for item in range(4096):
                value = self.u32(self.at_rva(thunk + item * 4, 4))
                self.at_rva(address + item * 4, 4)
                if value == 0:
                    break
                total_symbols += 1
                if total_symbols > 16384:
                    raise ProbeError('Import symbol count exceeds preflight limit')
                if value & 0x80000000:
                    if value & 0x7fff0000:
                        raise ProbeError('Invalid ordinal import')
                    symbols.append({'ordinal': value & 0xffff})
                else:
                    self.at_rva(value, 2)  # hint
                    symbols.append({'name': self.string(value + 2)})
            else:
                raise ProbeError('Unterminated or oversized import thunk list')
            self.imports.append({'dll': dll, 'symbols': symbols})
        raise ProbeError('Unterminated import descriptor list')


def probe(folder, expected_sha256=None):
    folder = Path(folder)
    exe = folder / 'game.exe'
    resources = folder / 'data'
    if not exe.is_file():
        raise ProbeError('Missing game.exe')
    if not resources.is_dir() or not any(resources.iterdir()):
        raise ProbeError('Missing or empty data directory')
    if expected_sha256 is not None and not re.fullmatch(r'[0-9a-fA-F]{64}', expected_sha256):
        raise ProbeError('Expected SHA-256 must contain 64 hexadecimal characters')
    # Bound memory use for untrusted input; this probe is not a general PE loader.
    with exe.open('rb') as stream:
        data = stream.read(128 * 1024 * 1024 + 1)
    if len(data) > 128 * 1024 * 1024:
        raise ProbeError('Executable exceeds preflight 128 MiB limit')
    digest = hashlib.sha256(data).hexdigest()
    if expected_sha256 is not None and digest != expected_sha256.lower():
        raise ProbeError('SHA-256 mismatch')
    pe = PE(data)
    return {'ok': True, 'sha256': digest, 'architecture': 'x86', 'format': 'PE32',
            'entry_rva': pe.entry, 'image_base': pe.image_base, 'image_size': pe.image_size,
            'sections': len(pe.sections), 'imports': pe.imports,
            'opengl_imports': [item for item in pe.imports if item['dll'].lower() == 'opengl32.dll'],
            'resources': 'data directory exists and is nonempty',
            'scope': 'Structural preflight only; does not prove game compatibility, resource completeness, or licensing'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--expected-sha256')
    args = parser.parse_args()
    try:
        result = probe(args.folder, args.expected_sha256)
    except (ProbeError, OSError) as exc:
        print(json.dumps({'ok': False, 'error': str(exc)}, ensure_ascii=True))
        return 2
    print(json.dumps(result, ensure_ascii=True, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
