package org.aminos.tgm3.privateport;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Atomic app-private file, freshly read across launcher and :game processes. */
public final class TouchSettingsStore {
 private TouchSettingsStore() {}
 private static AtomicFile file(Context c) { return new AtomicFile(new File(c.getFilesDir(),"touch-layout-v1.properties")); }
 public static TouchLayout read(Context c) {
  try { return TouchLayout.decode(new String(file(c).readFully(),StandardCharsets.UTF_8)); }
  catch(IOException ignored) { return new TouchLayout(); }
 }
 public static void write(Context c,TouchLayout layout) throws IOException {
  AtomicFile file=file(c); FileOutputStream output=null;
  try { output=file.startWrite(); output.write(layout.encode().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
  catch(IOException e) { if(output!=null) file.failWrite(output); throw e; }
 }
}
