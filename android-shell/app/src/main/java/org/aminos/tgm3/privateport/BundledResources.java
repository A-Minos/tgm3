package org.aminos.tgm3.privateport;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Verified resource bootstrap. Game saves are outside the replaceable tree. */
final class BundledResources {
 private static final int MAX_FILES=1000;
 private static final long MAX_BYTES=500L*1024*1024;
 static final class Entry {
  final String hash; final long bytes;
  Entry(String h,long b) { hash=h; bytes=b; }
 }
 static Map<String,Entry> readManifest(InputStream input) throws Exception {
  Map<String,Entry> entries=new LinkedHashMap<>(); long total=0;
  try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
   String line; int chars=0;
   while((line=reader.readLine())!=null) {
    chars+=line.length(); if(chars>256*1024) throw new IOException("Manifest too large.");
    String[] parts=line.split("\t",-1);
    if(parts.length!=3 || !parts[0].matches("[0-9a-f]{64}")) throw new IOException("Invalid resource manifest.");
    String path=parts[2]; GameImporter.checkName(path);
    if(path.endsWith("/") || path.contains("//") || !(path.equals("game.exe")||path.startsWith("data/")))
     throw new IOException("Unexpected resource path.");
    long bytes=Long.parseLong(parts[1]);
    if(bytes<0||bytes>MAX_BYTES||total>MAX_BYTES-bytes) throw new IOException("Resource limit exceeded.");
    total+=bytes;
    if(entries.put(path,new Entry(parts[0],bytes))!=null||entries.size()>MAX_FILES) throw new IOException("Duplicate or excess resource.");
   }
  }
  if(!entries.containsKey("game.exe")||entries.size()<2) throw new IOException("Incomplete manifest.");
  return entries;
 }
 static void verify(File directory,Map<String,Entry> entries) throws Exception {
  Path root=directory.toPath().toAbsolutePath().normalize();
  if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing resource directory.");
  for(Map.Entry<String,Entry> item:entries.entrySet()) {
   Path file=root.resolve(item.getKey()).normalize();
   if(!file.startsWith(root)) throw new IOException("Unsafe resource path.");
   for(Path p=file;p!=null&&!p.equals(root);p=p.getParent())
    if(Files.isSymbolicLink(p)) throw new IOException("Resource symlink rejected.");
   Entry expected=item.getValue();
   if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=expected.bytes||
       !expected.hash.equals(GameImporter.sha256(file.toFile()))) throw new IOException("Resource needs repair: "+item.getKey());
  }
  try(java.util.stream.Stream<Path> all=Files.walk(root)) {
   Iterator<Path> iterator=all.iterator();
   while(iterator.hasNext()) {
    Path file=iterator.next(); if(file.equals(root)) continue;
    String relative=root.relativize(file).toString();
    if(relative.equals("TGM3")||relative.equals(".verified")) continue;
    if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)) {
     if(!(relative.equals("data")||relative.startsWith("data/"))) throw new IOException("Unexpected resource directory.");
    } else if(!entries.containsKey(relative)) throw new IOException("Unexpected resource file: "+relative);
   }
  }
 }
 static void ensure(Context context,File root) throws Exception {
  Map<String,Entry> entries=readManifest(context.getAssets().open("bundled-game.manifest"));
  if(GameImporter.ready(root)) {
   try { verify(new File(root,"imported"),entries); return; } catch(IOException e) { /* rebuild from verified stage */ }
  }
  try(InputStream input=context.getAssets().open("bundled-game.zip")) {
   GameImporter.importZip(input,root,stage->verify(stage,entries));
  }
  if(!GameImporter.ready(root)) throw new IOException("Prepared game did not pass verification.");
  verify(new File(root,"imported"),entries);
 }
}
