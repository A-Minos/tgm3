#!/usr/bin/env python3
"""Copy game.exe/data, converting validated PCM16 BGM to Vorbis plus lossless metadata sidecars."""
import argparse, hashlib, json, os, pathlib, shutil, struct, subprocess, sys

class ConversionError(Exception): pass

def reject(message): raise ConversionError(message)
def within(a,b): return a==b or b in a.parents

def inspect_wav(path):
    size=path.stat().st_size
    with path.open('rb') as f:
        h=f.read(12)
        if len(h)!=12 or h[:4]!=b'RIFF' or h[8:]!=b'WAVE' or struct.unpack_from('<I',h,4)[0]+8!=size:
            reject(f'Invalid RIFF/WAVE size: {path.name}')
        fmt=None; data=None; off=12; tags=[]
        while off<size:
            f.seek(off); h=f.read(8)
            if len(h)!=8: reject(f'Truncated chunk: {path.name}')
            tag,n=struct.unpack('<4sI',h); end=off+8+n+n%2
            if end>size: reject(f'Chunk exceeds file: {path.name}')
            tags.append(tag.decode('ascii',errors='replace'))
            if tag==b'fmt ':
                if fmt is not None or n!=16: reject(f'Only single 16-byte PCM fmt supported: {path.name}')
                fmt=struct.unpack('<HHIIHH',f.read(16))
            if tag==b'data':
                if data is not None: reject(f'Multiple data chunks: {path.name}')
                data=(off+8,n)
            off=end
    if fmt is None or data is None: reject(f'Missing fmt/data: {path.name}')
    if data[1]==0: reject(f'Empty PCM payload: {path.name}')
    if data[1]>64*1024*1024: reject(f'PCM payload exceeds 64 MiB: {path.name}')
    if size-data[1]>1024*1024: reject(f'WAV metadata sidecar exceeds 1 MiB: {path.name}')
    codec,c,rate,byte_rate,align,bits=fmt
    if codec!=1 or bits!=16 or c not in (1,2) or rate not in (44100,) or align!=c*2 or byte_rate!=rate*align or data[1]%align:
        reject(f'Unsupported WAV; requires verified PCM16 mono/stereo 44100 Hz: {path.name}')
    return {'channels':c,'sample_rate':rate,'pcm_offset':data[0],'pcm_bytes':data[1],'frames':data[1]//align,'riff_chunks':tags}

def copy_n(src,dst,n,hasher=None):
    while n:
        b=src.read(min(n,1024*1024))
        if not b: reject('Unexpected end of file')
        dst.write(b)
        if hasher: hasher.update(b)
        n-=len(b)

def metadata(path,dest,info):
    h=hashlib.sha256()
    with path.open('rb') as src,dest.open('xb') as dst:
        copy_n(src,dst,info['pcm_offset'],h)
        src.seek(info['pcm_bytes'],1)
        copy_n(src,dst,path.stat().st_size-info['pcm_offset']-info['pcm_bytes'],h)
    return h.hexdigest()

def vorbis_format(path):
    with path.open('rb') as f:
        h=f.read(27)
        if len(h)!=27 or h[:4]!=b'OggS' or h[4]!=0: reject('Invalid Ogg header')
        lacing=f.read(h[26]); body=f.read(sum(lacing))
        if len(body)<30 or body[:7]!=b'\x01vorbis': reject('Missing Vorbis identification packet')
        return body[11],struct.unpack_from('<I',body,12)[0]

def decode_count(ffmpeg,path):
    # Consume actual output, never trust container duration; no PCM temporary file.
    p=subprocess.Popen([ffmpeg,'-v','error','-nostdin','-i',str(path),'-f','s16le','-acodec','pcm_s16le','-'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
    n=0
    try:
        while b:=p.stdout.read(1024*1024): n+=len(b)
    finally: p.stdout.close()
    if p.wait()!=0: reject(f'Decode failed: {path.name}')
    return n

def convert(source,output,ffmpeg='ffmpeg'):
    if any(".." in pathlib.Path(p).parts for p in (source,output)): reject("Parent traversal components are not allowed")
    # No source or destination path component may be a symlink.
    source=pathlib.Path(os.path.abspath(source)); output=pathlib.Path(os.path.abspath(output))
    for p in [source,*source.parents,output,*output.parents]:
        if p.is_symlink(): reject(f'Symlink path rejected: {p}')
    source=source.resolve(); output=output.resolve()
    if within(source,output) or within(output,source): reject('Source and output must not contain each other')
    if output.exists(): reject('Output already exists; refusing overwrite')
    if not source.is_dir(): reject('Source is not a directory')
    exe=source/'game.exe'; data=source/'data'; bgm=data/'bgm'
    if not exe.is_file() or not data.is_dir() or not bgm.is_dir(): reject('Expected game.exe and data/bgm')
    # Reject all symlinks/special files, including ignored root entries, without traversing links.
    for base,dirs,files in os.walk(source,followlinks=False):
        for name in dirs+files:
            p=pathlib.Path(base)/name
            if p.is_symlink() or not (p.is_dir() or p.is_file()): reject(f'Symlink/special file rejected: {p.relative_to(source)}')
    wavs=sorted(p for p in bgm.iterdir() if p.is_file() and p.suffix.lower()=='.wav')
    if not wavs: reject('No BGM WAV files')
    infos={p:inspect_wav(p) for p in wavs}
    for p in wavs:
        for target in [p.with_suffix('.ogg'),p.with_suffix('.ogg.wavmeta')]:
            if target.exists(): reject(f'Existing conversion target in input: {target.name}')
    binary=shutil.which(ffmpeg)
    if not binary: reject('ffmpeg not found; install it yourself before running')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.mkdir() # atomic no-overwrite creation; partial output deliberately retained on failure
    report={'status':'incomplete','codec':'libvorbis','quality':4,'files':[],'scope':'game.exe and data only; BGM converted, all other data copied unchanged'}
    report_path=output/'conversion_report.json'
    try:
        shutil.copy2(exe,output/'game.exe')
        for base,dirs,files in os.walk(data):
            rel=pathlib.Path(base).relative_to(source); (output/rel).mkdir(parents=True,exist_ok=True)
            for name in sorted(files):
                p=pathlib.Path(base)/name
                if p not in infos: shutil.copy2(p,output/p.relative_to(source)); continue
                info=infos[p]; dst=(output/p.relative_to(source)).with_suffix('.ogg'); meta=dst.with_suffix('.ogg.wavmeta')
                proc=subprocess.run([binary,'-n','-v','error','-nostdin','-i',str(p),'-map','0:a:0','-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(dst)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
                if proc.returncode: reject(f'Encoding failed for {p.name}: {proc.stderr.decode(errors="replace")[:1000]}')
                # Check Vorbis identification header, then count actual decoded PCM.
                if vorbis_format(dst)!=(info["channels"],info["sample_rate"]): reject(f"Rate/channels changed: {p.name}")
                decoded=decode_count(binary,dst)
                if decoded!=info['pcm_bytes']: reject(f'PCM frame mismatch for {p.name}: expected {info["pcm_bytes"]} decoded bytes, got {decoded}')
                mh=metadata(p,meta,info)
                report['files'].append({'source':str(p.relative_to(source)),'ogg':str(dst.relative_to(output)),'wavmeta':str(meta.relative_to(output)),'source_bytes':p.stat().st_size,'ogg_bytes':dst.stat().st_size,'wavmeta_bytes':meta.stat().st_size,'wavmeta_sha256':mh,'metadata_preserved':'All bytes except data PCM payload retained in original order; original length fields unchanged','decoded_pcm_bytes':decoded,'frame_count_verified':True,**info})
        report['status']='complete'; report['total_output_file_bytes_before_report']=sum(p.stat().st_size for p in output.rglob('*') if p.is_file())
    except Exception as e:
        report['error']=str(e); raise
    finally: report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    return report

def main():
    ap=argparse.ArgumentParser(description=__doc__); ap.add_argument('source'); ap.add_argument('output'); args=ap.parse_args()
    try:
        r=convert(args.source,args.output); print(json.dumps({'status':r['status'],'converted_bgm':len(r['files']),'report':str(pathlib.Path(args.output)/'conversion_report.json')},ensure_ascii=False))
    except Exception as e: print(f'ERROR: {e}',file=sys.stderr); return 1
    return 0
if __name__=='__main__': sys.exit(main())
