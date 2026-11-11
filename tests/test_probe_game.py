"""Synthetic PE bytes only; no game material used."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import json

TOOL = Path(__file__).resolve().parents[1] / 'tools/probe_game.py'
spec = importlib.util.spec_from_file_location('probe_game', TOOL)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture():
    data = bytearray(0x600)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 0x3c, 0x80)
    data[0x80:0x84] = b'PE\0\0'
    struct.pack_into('<HHIIIHH', data, 0x84, 0x14c, 1, 0, 0, 0, 224, 0x102)
    opt = 0x98
    struct.pack_into('<H', data, opt, 0x10b)
    for offset, value in [(16, 0x1000), (28, 0x400000), (32, 0x1000),
                          (36, 0x200), (56, 0x2000), (60, 0x200), (92, 16),
                          (104, 0x1100), (108, 40)]:
        struct.pack_into('<I', data, opt + offset, value)
    sec = opt + 224
    data[sec:sec + 8] = b'.probe\0\0'
    struct.pack_into('<IIII', data, sec + 8, 0x400, 0x1000, 0x400, 0x200)
    struct.pack_into('<IIIII', data, 0x300, 0x1160, 0, 0, 0x1140, 0x1180)
    data[0x340:0x34d] = b'OPENGL32.dll\0'
    struct.pack_into('<II', data, 0x360, 0x11a0, 0)
    struct.pack_into('<II', data, 0x380, 0x11a0, 0)
    data[0x3a0:0x3aa] = b'\0\0glClear\0'
    return data


class ProbeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        (self.folder / 'data').mkdir()
        (self.folder / 'data' / 'synthetic.txt').write_text('not game data')
        self.write(fixture())

    def write(self, data):
        (self.folder / 'game.exe').write_bytes(data)

    def reject(self, data):
        self.write(data)
        with self.assertRaises(module.ProbeError):
            module.probe(self.folder)

    def test_valid_gl_import(self):
        result = module.probe(self.folder)
        self.assertEqual(result['entry_rva'], 0x1000)
        self.assertEqual(result['image_base'], 0x400000)
        self.assertEqual(result['image_size'], 0x2000)
        self.assertEqual(result['opengl_imports'][0]['symbols'], [{'name': 'glClear'}])
        digest = hashlib.sha256(fixture()).hexdigest()
        self.assertEqual(module.probe(self.folder, digest.upper())['sha256'], digest)

    def test_truncated_headers(self):
        for length in [0, 2, 63, 0x83, 0x97, 0x99, 0x177, 0x19f]:
            with self.subTest(length=length):
                self.reject(fixture()[:length])

    def test_wrong_architecture_and_pe64(self):
        for offset, value in [(0x84, 0x8664), (0x98, 0x20b)]:
            data = fixture()
            struct.pack_into('<H', data, offset, value)
            self.reject(data)

    def test_sections_out_of_bounds(self):
        for offset, value in [(0x184, 0x3000), (0x188, 0x5000), (0x18c, 0x5000)]:
            data = fixture()
            struct.pack_into('<I', data, offset, value)
            self.reject(data)

    def test_bad_import_directory(self):
        for offset, value in [(0x100, 0x3000), (0x104, 19), (0x104, 0x10001),
                              (0x30c, 0x5000), (0x300, 0x5000), (0x310, 0x5000),
                              (0x360, 0x5000)]:
            data = fixture()
            struct.pack_into('<I', data, offset, value)
            self.reject(data)

    def test_unterminated_descriptors(self):
        data = fixture()
        struct.pack_into('<I', data, 0x104, 20)
        self.reject(data)

    def test_bad_names(self):
        for value in [b'\xff', b'\n', b'\0']:
            data = fixture()
            data[0x340:0x341] = value
            self.reject(data)
        data = fixture()
        struct.pack_into('<I', data, 0x30c, 0x1200)
        data[0x400:0x600] = b'x' * 512
        self.reject(data)

    def test_ordinal_import(self):
        data = fixture()
        struct.pack_into('<I', data, 0x360, 0x80000007)
        self.write(data)
        self.assertEqual(module.probe(self.folder)['imports'][0]['symbols'], [{'ordinal': 7}])
        struct.pack_into('<I', data, 0x360, 0x80010007)
        self.reject(data)

    def test_overlapping_sections_and_directory_count(self):
        data = fixture()
        struct.pack_into('<H', data, 0x86, 2)
        data[0x1a0:0x1c8] = data[0x178:0x1a0]
        self.reject(data)
        data = fixture()
        struct.pack_into('<I', data, 0xf4, 17)
        self.reject(data)

    def test_missing_resources(self):
        (self.folder / 'data' / 'synthetic.txt').unlink()
        with self.assertRaises(module.ProbeError):
            module.probe(self.folder)
        (self.folder / 'data').rmdir()
        with self.assertRaises(module.ProbeError):
            module.probe(self.folder)

    def test_missing_exe_and_hash(self):
        for digest in ['0' * 64, 'not-a-hash']:
            with self.assertRaises(module.ProbeError):
                module.probe(self.folder, digest)
        (self.folder / 'game.exe').unlink()
        with self.assertRaises(module.ProbeError):
            module.probe(self.folder)

    def test_cli_json_success_and_failure(self):
        for expected, code in [(hashlib.sha256(fixture()).hexdigest(), 0), ('0' * 64, 2)]:
            run = subprocess.run([sys.executable, str(TOOL), str(self.folder),
                                  '--expected-sha256', expected], capture_output=True, text=True)
            self.assertEqual(run.returncode, code, run.stderr)
            self.assertEqual(json.loads(run.stdout)['ok'], code == 0)
            self.assertEqual(run.stderr, '')


if __name__ == '__main__':
    unittest.main()
