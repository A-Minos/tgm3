#!/usr/bin/env python3
"""Generated audio only. Requires Python 3, ffmpeg/libvorbis and verify_loader."""
import hashlib
import json
import math
import os
import pathlib
import random
import struct
import subprocess
import sys
import tempfile
import unittest

BINARY = pathlib.Path(sys.argv.pop(1) if len(sys.argv) > 1 and not sys.argv[1].startswith('-') else './verify_loader').resolve()

def chunk(tag, data):
    return tag + struct.pack('<I', len(data)) + data + (b'\x7f' if len(data) % 2 else b'')

def wav(channels=2, frames=88200, order='normal'):
    fmt = chunk(b'fmt ', struct.pack('<HHIIHH', 1, channels, 44100, 44100*channels*2, channels*2, 16))
    pcm = b''.join(struct.pack('<h', round(10000*math.sin(i*.08+c*.25))) for i in range(frames) for c in range(channels))
    data = chunk(b'data', pcm)
    # A real nonzero loop record plus odd-length unknown chunks before/after PCM.
    smpl = chunk(b'smpl', struct.pack('<9I', 0,0,22675,60,0,0,0,1,0) + struct.pack('<6I', 1,0,400,frames-1,0,0))
    parts = [fmt, chunk(b'JUNK', b'abc'), data, smpl, chunk(b'zzzz', b'tail!')]
    if order == 'datafirst': parts = [data, chunk(b'JUNK', b'odd'), smpl, fmt]
    body = b'WAVE' + b''.join(parts)
    return b'RIFF' + struct.pack('<I', len(body)) + body

def sidecar(b):
    p=12
    while p < len(b):
        n=struct.unpack_from('<I',b,p+4)[0]
        if b[p:p+4] == b'data': return b[:p+8] + b[p+8+n:], p+8, n
        p += 8+n+n%2
    raise ValueError('no data')

def pages(b):
    p=0
    while p < len(b):
        n=b[p+26]; size=27+n+sum(b[p+27:p+27+n]); yield p,size; p+=size

def repair_crc(b, p, size):
    b=bytearray(b); b[p+22:p+26]=b'\0'*4; crc=0
    for x in b[p:p+size]:
        crc ^= x << 24
        for _ in range(8): crc=((crc<<1) ^ (0x04c11db7 if crc & 0x80000000 else 0)) & 0xffffffff
    struct.pack_into('<I',b,p+22,crc); return bytes(b)

class LoaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(prefix='ogg-wav-test-')
        cls.root=pathlib.Path(cls.tmp.name)
        cls.original=wav();cls.meta,cls.offset,cls.pcm=sidecar(cls.original)
        src=cls.root/'source.wav';src.write_bytes(cls.original)
        out=cls.root/'source.ogg'
        subprocess.run(['ffmpeg','-v','error','-nostdin','-y','-i',str(src),'-map','0:a:0','-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(out)],check=True)
        cls.ogg=out.read_bytes();cls.serial=0
    @classmethod
    def tearDownClass(cls): cls.tmp.cleanup()
    def fixture(self, meta=None, ogg=None, name='sound'):
        type(self).serial += 1
        d=self.root/str(type(self).serial);d.mkdir()
        p=d/(name+'.wav');p.with_suffix('.ogg').write_bytes(self.ogg if ogg is None else ogg)
        p.with_suffix('.ogg.wavmeta').write_bytes(self.meta if meta is None else meta)
        return p
    def run_loader(self,p,ok=True,probe=False):
        args=[str(BINARY),str(p)]+(['--probe'] if probe else [])
        r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)
        self.assertNotIn(b'Sanitizer',r.stderr, r.stderr.decode(errors='replace'))
        self.assertEqual(r.returncode,0 if ok else 1,r.stderr.decode(errors='replace'))
        if not ok: self.assertEqual(r.stdout,b'')
        return r.stdout
    def reject_meta(self,m): self.run_loader(self.fixture(meta=m),False)
    def test_reconstruct_all_metadata(self):
        p=self.fixture(); before=[hashlib.sha256(x.read_bytes()).hexdigest() for x in p.parent.iterdir()]
        b=self.run_loader(p);m,o,n=sidecar(b)
        self.assertEqual(m,self.meta);self.assertEqual(n,self.pcm);self.assertEqual(len(b),len(self.original))
        self.assertEqual(before,[hashlib.sha256(x.read_bytes()).hexdigest() for x in p.parent.iterdir()])
        self.assertNotEqual(b[o:o+n],b'\0'*n)
    def test_sealed_seekable_cloexec(self):
        r=json.loads(self.run_loader(self.fixture(),probe=True));self.assertTrue(r['fallback']);self.assertEqual(r['seals'],15)
    def test_existing_wav_wins(self):
        p=self.fixture(ogg=b'broken');p.write_bytes(self.original);self.assertEqual(self.run_loader(p),self.original)
        self.assertFalse(json.loads(self.run_loader(p,probe=True))['fallback'])
    def test_unicode_filename(self): self.run_loader(self.fixture(name='音声テスト'))
    def test_uppercase_wav_extension(self): self.run_loader(self.fixture().with_suffix('.WAV'))
    def test_invalid_extension(self): self.run_loader(self.fixture().with_suffix('.mp3'),False)
    def test_missing_sidecar(self):
        p=self.fixture();p.with_suffix('.ogg.wavmeta').unlink();self.run_loader(p,False)
    def test_missing_ogg(self):
        p=self.fixture();p.with_suffix('.ogg').unlink();self.run_loader(p,False)
    def test_empty_sidecar(self): self.reject_meta(b'')
    def test_truncated_riff(self): self.reject_meta(self.meta[:10])
    def test_wrong_riff_magic(self): self.reject_meta(b'RIFX'+self.meta[4:])
    def test_wrong_wave_magic(self): self.reject_meta(self.meta[:8]+b'nope'+self.meta[12:])
    def test_riff_size_mismatch(self): self.reject_meta(self.meta[:4]+struct.pack('<I',1)+self.meta[8:])
    def test_duplicate_fmt(self):
        m=self.meta+chunk(b'fmt ',self.meta[20:36]);m=m[:4]+struct.pack('<I',len(m)+self.pcm-8)+m[8:];self.reject_meta(m)
    def test_duplicate_data(self):
        m=self.meta+b'data'+struct.pack('<I',4);m=m[:4]+struct.pack('<I',len(m)+self.pcm+4-8)+m[8:];self.reject_meta(m)
    def test_missing_fmt(self): self.reject_meta(self.meta.replace(b'fmt ',b'abcd',1))
    def test_missing_data(self): self.reject_meta(self.meta.replace(b'data',b'abcd',1))
    def test_short_fmt(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,16,14);self.reject_meta(m)
    def test_non_pcm_fmt(self):
        m=bytearray(self.meta);struct.pack_into('<H',m,20,3);self.reject_meta(m)
    def test_wrong_bits(self):
        m=bytearray(self.meta);struct.pack_into('<H',m,34,8);self.reject_meta(m)
    def test_zero_channels(self):
        m=bytearray(self.meta);struct.pack_into('<H',m,22,0);self.reject_meta(m)
    def test_wrong_rate(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,24,48000);self.reject_meta(m)
    def test_wrong_byte_rate(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,28,1);self.reject_meta(m)
    def test_wrong_alignment(self):
        m=bytearray(self.meta);struct.pack_into('<H',m,32,1);self.reject_meta(m)
    def test_zero_pcm(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,self.offset-4,0);self.reject_meta(m)
    def test_pcm_over_cap(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,self.offset-4,64*1024*1024+4);self.reject_meta(m)
    def test_sidecar_over_cap(self): self.reject_meta(self.meta+b'\0'*(1024*1024))
    def test_misaligned_pcm(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,self.offset-4,self.pcm-2);self.reject_meta(m)
    def test_chunk_overrun(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,40,0xffffffff);self.reject_meta(m)
    def test_trailing_partial_chunk(self): self.reject_meta(self.meta+b'x')
    def test_decode_count_mismatch(self):
        m=bytearray(self.meta);struct.pack_into('<I',m,self.offset-4,self.pcm+4);struct.pack_into('<I',m,4,len(m)+self.pcm+4-8);self.reject_meta(m)
    def test_ogg_channel_mismatch(self):
        m=bytearray(self.meta);struct.pack_into('<H',m,22,1);struct.pack_into('<H',m,32,2);struct.pack_into('<I',m,28,88200);struct.pack_into('<I',m,self.offset-4,self.pcm//2);struct.pack_into('<I',m,4,len(m)+self.pcm//2-8);self.reject_meta(m)
    def test_ogg_sample_rate_mismatch(self):
        p=self.fixture();source=bytearray(self.original);struct.pack_into('<I',source,24,48000);struct.pack_into('<I',source,28,48000*4)
        tmp=p.parent/'source.wav';tmp.write_bytes(source)
        subprocess.run(['ffmpeg','-v','error','-nostdin','-y','-i',str(tmp),'-c:a','libvorbis','-q:a','4',str(p.with_suffix('.ogg'))],check=True)
        self.run_loader(p,False)
    def test_empty_ogg(self): self.run_loader(self.fixture(ogg=b''),False)
    def test_ogg_crc_corruption(self):
        b=bytearray(self.ogg);b[-5]^=1;self.run_loader(self.fixture(ogg=b),False)
    def test_ogg_truncation(self): self.run_loader(self.fixture(ogg=self.ogg[:-1]),False)
    def test_ogg_trailing_data(self): self.run_loader(self.fixture(ogg=self.ogg+b'junk'),False)
    def test_ogg_chained_stream(self): self.run_loader(self.fixture(ogg=self.ogg+self.ogg),False)
    def test_ogg_missing_eos(self):
        p,n=list(pages(self.ogg))[-1];b=bytearray(self.ogg);b[p+5]&=~4;self.run_loader(self.fixture(ogg=repair_crc(b,p,n)),False)
    def test_ogg_wrong_sequence(self):
        p,n=list(pages(self.ogg))[1];b=bytearray(self.ogg);struct.pack_into('<I',b,p+18,999);self.run_loader(self.fixture(ogg=repair_crc(b,p,n)),False)
    def test_ogg_wrong_serial(self):
        p,n=list(pages(self.ogg))[1];b=bytearray(self.ogg);b[p+14]^=1;self.run_loader(self.fixture(ogg=repair_crc(b,p,n)),False)
    def test_ogg_version(self):
        p,n=list(pages(self.ogg))[0];b=bytearray(self.ogg);b[p+4]=1;self.run_loader(self.fixture(ogg=repair_crc(b,p,n)),False)
    def test_fifo_sidecar_rejected(self):
        p=self.fixture();m=p.with_suffix('.ogg.wavmeta');m.unlink();os.mkfifo(m);self.run_loader(p,False)
    def test_directory_ogg_rejected(self):
        p=self.fixture();m=p.with_suffix('.ogg');m.unlink();m.mkdir();self.run_loader(p,False)
    def test_data_before_fmt(self):
        source=wav(order='datafirst');m,o,n=sidecar(source);p=self.fixture(meta=m);out=self.run_loader(p);self.assertEqual(sidecar(out)[0],m)
    def test_mono(self):
        source=wav(channels=1);m,o,n=sidecar(source);p=self.fixture(meta=m)
        tmp=p.parent/'source.wav';tmp.write_bytes(source)
        subprocess.run(['ffmpeg','-v','error','-nostdin','-y','-i',str(tmp),'-c:a','libvorbis','-q:a','4',str(p.with_suffix('.ogg'))],check=True)
        out=self.run_loader(p);self.assertEqual(sidecar(out)[0],m);self.assertEqual(len(out),len(source))
    def test_explicit_resolved_paths(self):
        p=self.fixture();ogg=p.with_suffix('.ogg');meta=p.with_suffix('.ogg.wavmeta')
        other_ogg=p.parent/'Different.OgG';other_meta=p.parent/'Original.META'
        ogg.rename(other_ogg);meta.rename(other_meta)
        r=subprocess.run([str(BINARY.parent/'test_fd_lifecycle'),'--paths',str(other_ogg),str(other_meta)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)
        self.assertEqual(r.returncode,0,r.stderr.decode(errors='replace'))
        self.assertNotIn(b'Sanitizer',r.stderr)
    def test_fd_lifecycle(self):
        binary=BINARY.parent/'test_fd_lifecycle'
        self.assertTrue(binary.is_file(), 'Build test_fd_lifecycle alongside verify_loader')
        good=self.fixture();bad=self.fixture(ogg=self.ogg[:-100])
        r=subprocess.run([str(binary),str(good),str(bad)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
        self.assertEqual(r.returncode,0,r.stderr.decode(errors='replace'))
        self.assertNotIn(b'Sanitizer',r.stderr)
        self.assertIn(b'no fd growth',r.stdout)
    def test_crc_valid_payload_mutations(self):
        rng=random.Random(31991);page_list=list(pages(self.ogg))
        for i in range(48):
            start,size=page_list[1+i%(len(page_list)-1)]
            body=start+27+self.ogg[start+26]
            b=bytearray(self.ogg);b[rng.randrange(body,start+size)]^=rng.randrange(1,256)
            b=repair_crc(b,start,size)
            p=self.fixture(ogg=b)
            r=subprocess.run([str(BINARY),str(p),'--probe'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)
            self.assertIn(r.returncode,(0,1));self.assertNotIn(b'Sanitizer',r.stderr)
    def test_bounded_mutations(self):
        rng=random.Random(7741)
        for i in range(120):
            m=bytearray(self.meta);b=bytearray(self.ogg)
            target=m if i%2 else b
            for _ in range(1+rng.randrange(4)): target[rng.randrange(len(target))]^=rng.randrange(1,256)
            p=self.fixture(meta=m,ogg=b)
            r=subprocess.run([str(BINARY),str(p),'--probe'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)
            self.assertIn(r.returncode,(0,1));self.assertNotIn(b'Sanitizer',r.stderr)

if __name__=='__main__': unittest.main(verbosity=2)
