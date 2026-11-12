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
  android.widget.ScrollView scroll = new android.widget.ScrollView(this);
  scroll.setFillViewport(true); scroll.setBackgroundColor(ModernUi.BACKGROUND);
  LinearLayout layout = ModernUi.column(this,24);
  layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
  LinearLayout body = new LinearLayout(this) {
   @Override protected void onMeasure(int widthSpec,int heightSpec) {
    int bounded=Math.min(View.MeasureSpec.getSize(widthSpec),ModernUi.dp(getContext(),600));
    super.onMeasure(View.MeasureSpec.makeMeasureSpec(bounded,View.MeasureSpec.EXACTLY),heightSpec);
   }
  };
  body.setOrientation(LinearLayout.VERTICAL);
  layout.addView(body,new LinearLayout.LayoutParams(-1,-2));
  body.addView(ModernUi.text(this,"A-MINOS  /  ANDROID LAB",12,ModernUi.ACCENT));
  ModernUi.space(body,12);
  body.addView(ModernUi.heading(this,"TGM3",36));
  body.addView(ModernUi.text(this,"Private Android test",15,ModernUi.MUTED));
  ModernUi.space(body,24);
  LinearLayout game = ModernUi.card(this);
  game.addView(ModernUi.heading(this,"Ready to play?",20));
  ModernUi.space(game,8);
  status = ModernUi.text(this,"",14,ModernUi.MUTED); game.addView(status);
  start = ModernUi.button(this,"Start game",true,()->{}); ModernUi.fullButton(game,start);
  body.addView(game,new LinearLayout.LayoutParams(-1,-2));
  ModernUi.space(body,16);
  LinearLayout controls = ModernUi.card(this);
  controls.addView(ModernUi.heading(this,"Make it yours",20));
  ModernUi.space(controls,8);
  controls.addView(ModernUi.text(this,"Move, resize and fade the touch controls. Keyboard and controller input remain available.",14,ModernUi.MUTED));
  Button touch = ModernUi.button(this,"Touch layout",false,()->{}); ModernUi.fullButton(controls,touch);
  body.addView(controls,new LinearLayout.LayoutParams(-1,-2));
  ModernUi.space(body,24);
  body.addView(ModernUi.heading(this,"Game resources",16));
  ModernUi.space(body,8);
  body.addView(ModernUi.text(this,"Import your compatible game ZIP. Existing saves are kept; no game assets are bundled.",14,ModernUi.MUTED));
  choose = ModernUi.button(this,"Import game ZIP",false,()->{}); ModernUi.fullButton(body,choose);
  ModernUi.space(body,16);
  body.addView(ModernUi.text(this,"Supported build: game.exe · 557056 bytes · SHA-256 0d10e863…cb2, plus data/. Other builds may not work.",12,ModernUi.MUTED));
  scroll.addView(layout);
  // Keep content clear of status/navigation bars under Android 15 edge-to-edge.
  final int base=ModernUi.dp(this,24);
  layout.setOnApplyWindowInsetsListener((v,insets)->{
   v.setPadding(base+insets.getSystemWindowInsetLeft(),base+insets.getSystemWindowInsetTop(),
     base+insets.getSystemWindowInsetRight(),base+insets.getSystemWindowInsetBottom());
   return insets;
  });
  touch.setOnClickListener(v -> startActivity(new Intent(this, TouchSettingsActivity.class)));
  choose.setOnClickListener(v -> {
   if (ImportJob.busy) return;
   Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   pick.addCategory(Intent.CATEGORY_OPENABLE);
   pick.setType("*/*");
   startActivityForResult(pick, PICK_ZIP);
  });
  start.setOnClickListener(v -> { if (!ImportJob.busy && GameImporter.ready(gameRoot())) startActivity(new Intent(this, GameActivity.class)); });
  setContentView(scroll); refresh();
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
