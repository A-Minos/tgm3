package org.aminos.tgm3.privateport;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
public final class ImporterTest {
 static void check(boolean v,String s) { if(!v) throw new AssertionError(s); }
 public static void main(String[] args) throws Exception {
  Path temp=Files.createTempDirectory("import-test"); File root=temp.resolve("game").toFile();
  try {
   try(InputStream in=new FileInputStream(args[0])) {GameImporter.importZip(in,root);}
   check(GameImporter.ready(root),"real ZIP accepted");
   Files.write(root.toPath().resolve("TGM3/save.bin"),new byte[]{1,2,3});
   try(InputStream in=new FileInputStream(args[0])) {GameImporter.importZip(in,root);}
   check(Files.size(root.toPath().resolve("TGM3/save.bin"))==3,"save retained after reimport");
   check(Files.size(root.toPath().resolve("imported/TGM3/save.bin"))==3,"save symlink resolves");
   Path marker=root.toPath().resolve("imported/.verified");
   Files.write(marker,"invalid".getBytes()); check(!GameImporter.ready(root),"invalid persisted marker disables Start");
   Files.write(marker,GameImporter.EXE_HASH.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
   Path link=root.toPath().resolve("imported/TGM3"); Files.delete(link);
   check(!GameImporter.ready(root),"missing save link disables Start");
   Files.createSymbolicLink(link,root.toPath().resolve("TGM3").toAbsolutePath());
   check(GameImporter.ready(root),"valid marker and link reenable Start");
   for(String name:new String[]{"../escape","/absolute","data/../escape","data\\escape","C:/escape"}) {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    try(ZipOutputStream z=new ZipOutputStream(bytes)) {z.putNextEntry(new ZipEntry(name));z.write(1);z.closeEntry();}
    boolean rejected=false; try {GameImporter.importZip(new ByteArrayInputStream(bytes.toByteArray()),root);} catch(IOException e){rejected=true;}
    check(rejected,"unsafe entry rejected: "+name); check(GameImporter.ready(root),"old resources kept");
   }
   for(String zip:java.util.Arrays.copyOfRange(args,1,args.length)) {
    boolean rejected=false; try(InputStream in=new FileInputStream(zip)) {GameImporter.importZip(in,root);}catch(IOException e){rejected=true;}
    check(rejected,"bad archive rejected: "+zip);
   }
   System.out.println("PASS: real ZIP, repeated import, save preservation, symlink, unsafe paths, malformed fixtures");
  } finally {GameImporter.deleteTree(temp);}
 }
}
