import importlib.util
import pathlib
import struct
import unittest

spec = importlib.util.spec_from_file_location("elfcheck", pathlib.Path(__file__).parents[1] / "tools/check_elf_alignment.py")
elfcheck = importlib.util.module_from_spec(spec)
spec.loader.exec_module(elfcheck)

def fixture(align=16384, vaddr=0):
    data = bytearray(128)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, 183)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 4, 0, vaddr, 0, 128, 128, align)
    return data

class ElfAlignmentTests(unittest.TestCase):
    def test_good(self):
        self.assertEqual(elfcheck.check(fixture()), 1)
    def test_reject_4k(self):
        with self.assertRaises(ValueError): elfcheck.check(fixture(4096))
    def test_reject_bad_congruence(self):
        with self.assertRaises(ValueError): elfcheck.check(fixture(vaddr=4096))
    def test_reject_truncated(self):
        with self.assertRaises(ValueError): elfcheck.check(fixture()[:70])
    def test_reject_wrong_machine(self):
        data = fixture(); struct.pack_into("<H", data, 18, 62)
        with self.assertRaises(ValueError): elfcheck.check(data)
