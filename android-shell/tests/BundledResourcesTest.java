package org.aminos.tgm3.privateport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class BundledResourcesTest {
 static int checks;
 static void check(boolean value,String text) { checks++; if(!value) throw new AssertionError(text); }
 interface Work { void run() throws Exception; }
 static void rejects(Work work,String text) throws Exception {
  boolean rejected=false; try { work.run(); } catch(Exception e) { rejected=true; }
  check(rejected,text);
 }
 public static void main(String[] args) throws Exception {
  Map<String,BundledResources.Entry> manifest=BundledResources.readManifest(new FileInputStream(args[1]));
  check(manifest.size()==226,"expected bundle entries");
  Path temp=Files.createTempDirectory("tgm-bundled-test"); File root=temp.resolve("new-app/game").toFile();
  try {
   Path old=temp.resolve("old-app/save.bin");Files.createDirectories(old.getParent());Files.write(old,new byte[]{9,8,7});
   try(InputStream in=new FileInputStream(args[0])) {GameImporter.importZip(in,root,stage->BundledResources.verify(stage,manifest));}
   check(GameImporter.ready(root),"ready after initial staged extraction");
   BundledResources.verify(new File(root,"imported"),manifest);checks++;
   for(int i=0;i<50;i++) Files.write(root.toPath().resolve("TGM3/save-"+i),new byte[]{(byte)i,1,2,3});
   try(InputStream in=new FileInputStream(args[0])) {GameImporter.importZip(in,root,stage->BundledResources.verify(stage,manifest));}
   for(int i=0;i<50;i++) check(Arrays.equals(Files.readAllBytes(root.toPath().resolve("TGM3/save-"+i)),new byte[]{(byte)i,1,2,3}),"save retained");
   check(Arrays.equals(Files.readAllBytes(old),new byte[]{9,8,7}),"separate old installation untouched");
   String track=manifest.keySet().stream().filter(x->x.endsWith(".ogg")).findFirst().get();
   Path audio=root.toPath().resolve("imported").resolve(track);byte[] original=Files.readAllBytes(audio);
   byte[] bad=original.clone();bad[bad.length-1]^=1;Files.write(audio,bad);
   rejects(()->BundledResources.verify(new File(root,"imported"),manifest),"same-size corrupted audio rejected");
   Files.write(audio,original);
   Path extra=audio.resolveSibling("unlisted.wav");Files.write(extra,new byte[]{1});
   rejects(()->BundledResources.verify(new File(root,"imported"),manifest),"extra WAV cannot shadow OGG");Files.delete(extra);
   Files.delete(audio);Files.createSymbolicLink(audio,old);
   rejects(()->BundledResources.verify(new File(root,"imported"),manifest),"resource symlink rejected");Files.delete(audio);Files.write(audio,original);
   rejects(()->{try(InputStream in=new FileInputStream(args[0])) {GameImporter.importZip(in,root,stage->{throw new IOException("Injected stage failure");});}},"failed staged verification rejected");
   check(GameImporter.ready(root),"previous valid import retained after verifier failure");
   BundledResources.verify(new File(root,"imported"),manifest);checks++;
   for(int i=0;i<50;i++) check(Files.size(root.toPath().resolve("TGM3/save-"+i))==4,"saves retained after failed import");
   String hash=GameImporter.EXE_HASH;
   for(String data:new String[]{"bad",hash+"\t1\t../escape\n",hash+"\t1\tTGM3/save\n",hash+"\t-1\tgame.exe\n",hash+"\t1\tgame.exe\n"+hash+"\t1\tgame.exe\n",hash+"\t1\tgame.exe\n"+hash+"\t1\tdata//bad\n"})
    rejects(()->BundledResources.readManifest(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))),"invalid manifest rejected");
   System.out.println("PASS "+checks+" bundle/import/save assertions");
  } finally {GameImporter.deleteTree(temp);}
 }
}
