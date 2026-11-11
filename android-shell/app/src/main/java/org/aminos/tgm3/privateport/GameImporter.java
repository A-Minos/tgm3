package org.aminos.tgm3.privateport;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Private, bounded import. Only imported resources are replaced; saves live outside it. */
final class GameImporter {
 static final long LIMIT = 500L * 1024 * 1024;
 static final int MAX_ENTRIES = 2000;
 static final String EXE_HASH = "0d10e863eaf4d1e431408d4aef860a29e1b8efbe5e68ee23a5f979bb6cb23cb2";
 static boolean ready(File root) {
  File dir = new File(root, "imported");
  Path marker = new File(dir, ".verified").toPath();
  Path saveLink = new File(dir, "TGM3").toPath();
  Path saveRoot = new File(root, "TGM3").toPath().toAbsolutePath().normalize();
  try {
   if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) != EXE_HASH.length()) return false;
   if (!EXE_HASH.equals(new String(Files.readAllBytes(marker), java.nio.charset.StandardCharsets.US_ASCII))) return false;
   File exe = new File(dir, "game.exe");
   if (exe.length() != 557056 || !EXE_HASH.equals(sha256(exe))) return false;
   if (!new File(dir, "data").isDirectory() || !Files.isSymbolicLink(saveLink)) return false;
   Path target = Files.readSymbolicLink(saveLink);
   if (!target.isAbsolute()) target = saveLink.getParent().resolve(target);
   return target.toAbsolutePath().normalize().equals(saveRoot)
       && Files.isDirectory(saveRoot, LinkOption.NOFOLLOW_LINKS);
  } catch (Exception e) { return false; }
 }
 static synchronized void importZip(InputStream source, File root) throws Exception {
  Files.createDirectories(root.toPath());
  File stage = new File(root, "import-stage"), archive = new File(root, "import.zip");
  File current = new File(root, "imported"), old = new File(root, "import-backup");
  // Recover an interrupted last rename before doing anything else.
  if (!current.exists() && old.exists()) Files.move(old.toPath(), current.toPath());
  deleteTree(stage.toPath()); deleteTree(old.toPath());
  Files.createDirectory(stage.toPath());
  try {
   try (OutputStream out = new FileOutputStream(archive)) { copyBounded(source, out, LIMIT); }
   checkCentralDirectory(archive);
   long total = 0; int dataFiles = 0;
   try (ZipFile zip = new ZipFile(archive, java.nio.charset.StandardCharsets.ISO_8859_1)) {
    // Permit one common enclosing folder, but never search for arbitrary nested assets.
    String prefix = null;
    Enumeration<? extends ZipEntry> scan = zip.entries();
    Set<String> names = new HashSet<>();
    while (scan.hasMoreElements()) {
     ZipEntry e = scan.nextElement(); String name = e.getName(); checkName(name);
     if (!names.add(name)) throw new IOException("Duplicate ZIP entry.");
     if (name.equals("game.exe") || (name.endsWith("/game.exe") && name.indexOf('/') == name.lastIndexOf('/'))) {
      if (prefix != null) throw new IOException("More than one game.exe found.");
      prefix = name.substring(0, name.length() - "game.exe".length());
     }
    }
    if (prefix == null) throw new IOException("game.exe is missing or nested too deeply.");
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
     ZipEntry e = entries.nextElement(); String n = e.getName();
     if (!n.startsWith(prefix)) continue;
     String relative = n.substring(prefix.length());
     if (!(relative.equals("game.exe") || relative.startsWith("data/"))) continue;
     File dest = new File(stage, relative);
     if (!dest.getCanonicalPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new IOException("Unsafe ZIP path.");
     if (e.isDirectory()) { Files.createDirectories(dest.toPath()); continue; }
     Files.createDirectories(dest.toPath().getParent());
     long written;
     try (InputStream in = zip.getInputStream(e); OutputStream out = new FileOutputStream(dest)) {
      written = copyBounded(in, out, LIMIT - total);
     }
     total += written;
     if (relative.startsWith("data/") && written > 0) dataFiles++;
    }
   }
   File exe = new File(stage, "game.exe");
   if (exe.length() != 557056 || !EXE_HASH.equals(sha256(exe))) throw new IOException("Unsupported game.exe version (SHA-256 mismatch).");
   if (dataFiles == 0) throw new IOException("The data/ folder has no nonempty files.");
   File saves = new File(root, "TGM3"); Files.createDirectories(saves.toPath());
   Files.createSymbolicLink(new File(stage, "TGM3").toPath(), saves.toPath().toAbsolutePath());
   Files.write(new File(stage, ".verified").toPath(), EXE_HASH.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
   if (current.exists()) Files.move(current.toPath(), old.toPath());
   try { Files.move(stage.toPath(), current.toPath()); }
   catch (IOException e) { if (old.exists()) Files.move(old.toPath(), current.toPath()); throw e; }
   deleteTree(old.toPath());
  } finally { deleteTree(stage.toPath()); Files.deleteIfExists(archive.toPath()); }
 }
 static long copyBounded(InputStream in, OutputStream out, long cap) throws IOException {
  byte[] buf = new byte[65536]; long sum = 0; int n;
  while ((n = in.read(buf)) != -1) { sum += n; if (sum > cap) throw new IOException("ZIP exceeds the 500 MiB limit."); out.write(buf, 0, n); }
  return sum;
 }
 static void checkName(String name) throws IOException {
  if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) throw new IOException("Unsafe ZIP entry path.");
  for (String part : name.split("/", -1)) if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe ZIP entry path.");
 }
 static String sha256(File f) throws Exception {
  MessageDigest md = MessageDigest.getInstance("SHA-256");
  try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[65536]; int n; while ((n=in.read(b)) != -1) md.update(b,0,n); }
  StringBuilder result = new StringBuilder(); for(byte b:md.digest()) result.append(String.format(Locale.ROOT,"%02x",b & 255)); return result.toString();
 }
 static void deleteTree(Path path) throws IOException {
  // Deliberately do not follow TGM3 symlink or any unexpected symlink.
  if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
   try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) { for(Path p:children) deleteTree(p); }
  }
  Files.deleteIfExists(path);
 }
 static int u16(RandomAccessFile f) throws IOException { return f.readUnsignedByte() | (f.readUnsignedByte()<<8); }
 static long u32(RandomAccessFile f) throws IOException { return Integer.toUnsignedLong(u16(f) | (u16(f)<<16)); }
 static void checkCentralDirectory(File archive) throws IOException {
  try (RandomAccessFile f = new RandomAccessFile(archive,"r")) {
   long end=-1, len=f.length();
   for(long at=len-22;at>=Math.max(0,len-65557);at--) {
    f.seek(at); if(u32(f)==0x06054b50L) { f.seek(at+20); if(at+22+u16(f)==len) { end=at; break; } }
   }
   if(end<0) throw new IOException("Invalid ZIP directory.");
   f.seek(end+4); int disk=u16(f), cdDisk=u16(f), local=u16(f), count=u16(f); long size=u32(f), offset=u32(f);
   if(disk!=0 || cdDisk!=0 || local!=count || count==65535 || count>MAX_ENTRIES || size==0xffffffffL || offset==0xffffffffL || offset+size!=end) throw new IOException("Unsupported ZIP or too many entries.");
   f.seek(offset); long expanded=0;
   for(int i=0;i<count;i++) {
    long start=f.getFilePointer(); if(u32(f)!=0x02014b50L) throw new IOException("Invalid ZIP directory entry.");
    u16(f); u16(f); int flags=u16(f); if((flags & 1)!=0) throw new IOException("Encrypted ZIP is unsupported.");
    f.seek(start+24); long uncompressed=u32(f); expanded+=uncompressed;
    if(uncompressed==0xffffffffL || expanded>LIMIT) throw new IOException("ZIP exceeds the 500 MiB limit.");
    int name=u16(f), extra=u16(f), comment=u16(f); int entryDisk=u16(f); u16(f); long attrs=u32(f);
    int unixType=(int)((attrs>>>16)&0170000);
    if(entryDisk!=0 || (unixType!=0 && unixType!=0100000 && unixType!=0040000)) throw new IOException("ZIP symlinks and special files are forbidden.");
    long next=start+46L+name+extra+comment; if(next>end) throw new IOException("Invalid ZIP bounds."); f.seek(next);
   }
   if(f.getFilePointer()!=end) throw new IOException("Invalid ZIP directory size.");
  }
 }
}
