import json,pathlib,struct,tempfile,unittest,wave
import convert_bgm as c
class Tests(unittest.TestCase):
 def setUp(self):
  self.t=tempfile.TemporaryDirectory(); self.root=pathlib.Path(self.t.name); self.src=self.root/'game'; (self.src/'data/bgm').mkdir(parents=True); (self.src/'game.exe').write_bytes(b'never executed')
  self.wav=self.src/'data/bgm/a.wav'
  with wave.open(str(self.wav),'wb') as w: w.setparams((2,2,44100,0,'NONE','')); w.writeframes(b'\x00'*4096)
 def tearDown(self): self.t.cleanup()
 def make_sparse(self,pcm_bytes,extra=0):
  fmt=struct.pack('<HHIIHH',1,2,44100,176400,4,16)
  size=44+pcm_bytes+(8+extra if extra else 0)
  with self.wav.open('wb') as f:
   f.write(b'RIFF'+struct.pack('<I',size-8)+b'WAVEfmt '+struct.pack('<I',16)+fmt+b'data'+struct.pack('<I',pcm_bytes))
   f.seek(pcm_bytes,1)
   if extra:
    f.write(b'JUNK'+struct.pack('<I',extra)); f.seek(extra,1)
   f.truncate()
 def test_empty_pcm(self):
  self.make_sparse(0)
  with self.assertRaisesRegex(c.ConversionError,'Empty PCM'): c.inspect_wav(self.wav)
 def test_pcm_above_limit(self):
  self.make_sparse(64*1024*1024+4)
  with self.assertRaisesRegex(c.ConversionError,'64 MiB'): c.inspect_wav(self.wav)
 def test_pcm_at_limit(self):
  self.make_sparse(64*1024*1024)
  self.assertEqual(c.inspect_wav(self.wav)['pcm_bytes'],64*1024*1024)
 def test_metadata_above_limit(self):
  self.make_sparse(4,1024*1024-50)
  with self.assertRaisesRegex(c.ConversionError,'1 MiB'): c.inspect_wav(self.wav)
 def test_metadata_at_limit(self):
  self.make_sparse(4,1024*1024-52)
  self.assertEqual(c.inspect_wav(self.wav)['pcm_bytes'],4)
 def test_parent_traversal(self):
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/"x"/".."/"out")
 def test_contained_output(self):
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.src/'out')
 def test_containing_output(self):
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root)
 def test_existing_output(self):
  out=self.root/'out'; out.mkdir()
  with self.assertRaises(c.ConversionError): c.convert(self.src,out)
 def test_symlink(self):
  (self.src/'link').symlink_to(self.wav)
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/'out')
 def test_symlink_source(self):
  link=self.root/'link'; link.symlink_to(self.src,target_is_directory=True)
  with self.assertRaises(c.ConversionError): c.convert(link,self.root/'out')
 def test_symlink_output_parent(self):
  real=self.root/'real'; real.mkdir(); link=self.root/'link'; link.symlink_to(real,target_is_directory=True)
  with self.assertRaises(c.ConversionError): c.convert(self.src,link/'out')
 def test_non_pcm16(self):
  b=bytearray(self.wav.read_bytes()); struct.pack_into('<H',b,34,8); self.wav.write_bytes(b)
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/'out')
 def test_multiple_data(self):
  b=bytearray(self.wav.read_bytes()+b'data\x00\x00\x00\x00'); struct.pack_into('<I',b,4,len(b)-8); self.wav.write_bytes(b)
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/'out')
 def test_truncated(self):
  self.wav.write_bytes(self.wav.read_bytes()[:-1])
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/'out')
 def test_wrong_rate(self):
  b=bytearray(self.wav.read_bytes()); struct.pack_into('<I',b,24,48000); self.wav.write_bytes(b)
  with self.assertRaises(c.ConversionError): c.convert(self.src,self.root/'out')
if __name__=='__main__': unittest.main(verbosity=2)
