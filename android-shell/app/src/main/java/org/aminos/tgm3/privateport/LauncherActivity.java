package org.aminos.tgm3.privateport;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherActivity extends Activity {
 private static final int PICK_ZIP = 10;
 // The job belongs to the process, not an Activity; recreation cannot orphan its state.
 private static final class ImportJob {
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static final Handler main = new Handler(Looper.getMainLooper());
  static WeakReference<LauncherActivity> observer = new WeakReference<>(null);
  static boolean busy;
  static String message;
  static void notifyObserver() {
   LauncherActivity activity = observer.get();
   if (activity != null && !activity.isDestroyed()) activity.refresh();
  }
  static void start(Context context, Uri uri, File root) {
   if (busy) return;
   busy = true; message = "Importing and checking resources…"; notifyObserver();
   Context app = context.getApplicationContext();
   worker.execute(() -> {
    String result;
    try (InputStream in = app.getContentResolver().openInputStream(uri)) {
     if (in == null) throw new java.io.IOException("The selected file could not be opened.");
     GameImporter.importZip(in, root);
     result = "Import verified. Ready to start.";
    } catch (Exception e) { result = "Import failed: " + e.getMessage(); }
    final String completed = result;
    main.post(() -> { busy = false; message = completed; notifyObserver(); });
   });
  }
 }
 private TextView status;
 private Button choose, start;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout layout = new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  int pad = (int)(24 * getResources().getDisplayMetrics().density);
  layout.setPadding(pad, pad, pad, pad);
  TextView title = new TextView(this);
  title.setText("TGM3 · Private Android Test"); title.setTextSize(25);
  layout.addView(title);
  TextView help = new TextView(this);
  help.setText("Import the game ZIP you supplied for this private test. Only its supported game.exe (557056 bytes, SHA-256 0d10e863…cb2) and data/ are accepted; other TGM3 builds may not work. Existing saves are kept. No game assets are bundled. Prototype touch keys, keyboard, and controller input are available.");
  layout.addView(help);
  status = new TextView(this); layout.addView(status);
  choose = new Button(this); choose.setText("Import game ZIP"); layout.addView(choose);
  start = new Button(this); start.setText("Start game"); layout.addView(start);
  choose.setOnClickListener(v -> {
   if (ImportJob.busy) return;
   Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   pick.addCategory(Intent.CATEGORY_OPENABLE);
   pick.setType("*/*");
   startActivityForResult(pick, PICK_ZIP);
  });
  start.setOnClickListener(v -> { if (!ImportJob.busy && GameImporter.ready(gameRoot())) startActivity(new Intent(this, GameActivity.class)); });
  setContentView(layout); refresh();
 }
 private File gameRoot() { return new File(getFilesDir(), "game"); }
 private void refresh() {
  boolean ready = !ImportJob.busy && GameImporter.ready(gameRoot());
  String fallback = ready ? "Resources ready. Saves remain in private app storage." : "Import the supported game ZIP to begin.";
  status.setText(ImportJob.message != null ? ImportJob.message : fallback);
  start.setEnabled(ready); choose.setEnabled(!ImportJob.busy);
 }
 @Override protected void onStart() {
  super.onStart();
  ImportJob.observer = new WeakReference<>(this);
  refresh();
 }
 @Override protected void onStop() {
  if (ImportJob.observer.get() == this) ImportJob.observer.clear();
  super.onStop();
 }
 @Override protected void onActivityResult(int request, int result, Intent data) {
  super.onActivityResult(request, result, data);
  if (request != PICK_ZIP || result != RESULT_OK || data == null || data.getData() == null || ImportJob.busy) return;
  ImportJob.start(getApplicationContext(), data.getData(), gameRoot());
 }
}
