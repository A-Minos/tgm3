package org.aminos.tgm3.privateport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Synthetic inputs only: this test requires no game files. */
public final class ResourceManifestTest {
 static int checks;
 static void check(boolean v,String message) { checks++; if(!v) throw new AssertionError(message); }
 interface Action { void run() throws Exception; }
 static void rejects(Action action) throws Exception {
  boolean rejected=false;try {action.run();}catch(Exception e){rejected=true;}
  check(rejected,"unsafe input accepted");
 }
 static Map<String,BundledResources.Entry> parse(String text) throws Exception {
  return BundledResources.readManifest(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
 }
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("synthetic-resources");
  try {
   Files.createDirectories(root.resolve("data/bgm"));
   Files.write(root.resolve("game.exe"),new byte[]{1,2,3});
   Path audio=root.resolve("data/bgm/test.ogg");Files.write(audio,new byte[]{4,5,6});
   String hash=GameImporter.sha256(root.resolve("game.exe").toFile());
   String manifest=hash+"\t3\tgame.exe\n"+GameImporter.sha256(audio.toFile())+"\t3\tdata/bgm/test.ogg\n";
   Map<String,BundledResources.Entry> entries=parse(manifest);
   check(entries.size()==2,"two synthetic files");
   BundledResources.verify(root.toFile(),entries);checks++;
   Files.write(audio,new byte[]{4,5,7});rejects(()->BundledResources.verify(root.toFile(),entries));
   Files.write(audio,new byte[]{4,5,6});
   Path extra=audio.resolveSibling("test.wav");Files.write(extra,new byte[]{1});
   rejects(()->BundledResources.verify(root.toFile(),entries));Files.delete(extra);
   Files.delete(audio);Files.createSymbolicLink(audio,root.resolve("game.exe"));
   rejects(()->BundledResources.verify(root.toFile(),entries));Files.delete(audio);
   for(String bad:new String[]{"bad",hash+"\t1\t../escape\n",hash+"\t-1\tgame.exe\n",manifest+hash+"\t3\tgame.exe\n",manifest+hash+"\t1\tdata//bad\n",manifest+hash+"\t1\tTGM3/save\n",manifest+hash+"\t524288001\tdata/oversize\n"}) rejects(()->parse(bad));
   System.out.println("PASS "+checks+" synthetic resource manifest assertions (no game resources)");
  } finally {GameImporter.deleteTree(root);}
 }
}
