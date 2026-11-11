package org.aminos.tgm3.privateport;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import org.libsdl.app.SDLActivity;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class GameActivity extends SDLActivity {
 private final Set<Integer> heldKeys = new HashSet<>();
 @Override protected String[] getLibraries() { return new String[]{"SDL2", "tgm3recomp"}; }
 @Override protected String getMainFunction() { return "SDL_main"; }
 @Override protected String[] getArguments() {
  File root = new File(getFilesDir(), "game");
  return new String[]{new File(root, "imported").getAbsolutePath()};
 }
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  if (mLayout == null || mSurface == null) return; // SDL may show its own load error.
  LinearLayout controls = new LinearLayout(this);
  controls.setOrientation(LinearLayout.VERTICAL);
  controls.setId(View.generateViewId());
  controls.setBackgroundColor(0xff202020);
  addRow(controls, new String[]{"←", "↓", "↑", "→", "Coin (5)", "Start (1)"},
      new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
          KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_1});
  addRow(controls, new String[]{"Z", "X", "C", "V"},
      new int[]{KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V});
  RelativeLayout.LayoutParams bar = new RelativeLayout.LayoutParams(-1, dp(104));
  bar.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
  mLayout.addView(controls, bar);
  RelativeLayout.LayoutParams surface = new RelativeLayout.LayoutParams(-1, -1);
  surface.addRule(RelativeLayout.ABOVE, controls.getId());
  surface.addRule(RelativeLayout.ALIGN_PARENT_TOP);
  mSurface.setLayoutParams(surface);
  mSurface.requestFocus();
 }
 private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
 @SuppressLint("ClickableViewAccessibility") // Momentary game keys must receive release/cancel, not click toggles.
 private void addRow(LinearLayout parent, String[] labels, int[] keys) {
  LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
  parent.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
  for(int i=0;i<labels.length;i++) {
   final int key = keys[i]; Button button = new Button(this);
   button.setText(labels[i]); button.setContentDescription("Game key " + labels[i]);
   button.setTextSize(14); button.setPadding(0,0,0,0); button.setMinWidth(0);
   button.setFocusable(false); button.setSoundEffectsEnabled(false);
   button.setOnTouchListener((v,event) -> {
    int action = event.getActionMasked();
    if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
        && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
     String label = action == MotionEvent.ACTION_DOWN ? "down" : action == MotionEvent.ACTION_UP ? "up" : "cancel";
     Log.d("TGM3Touch", label + " key=" + key + " t=" + SystemClock.uptimeMillis());
    }
    switch(action) {
     case MotionEvent.ACTION_DOWN:
      if(heldKeys.add(key)) onNativeKeyDown(key);
      v.setPressed(true); return true;
     case MotionEvent.ACTION_UP:
     case MotionEvent.ACTION_CANCEL:
      if(heldKeys.remove(key)) onNativeKeyUp(key);
      v.setPressed(false); return true;
     default: return true;
    }
   });
   row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
  }
 }
 private void releaseKeys() {
  for(int key:heldKeys) onNativeKeyUp(key);
  heldKeys.clear();
 }
 @Override protected void onPause() { releaseKeys(); super.onPause(); }
 @Override public void onWindowFocusChanged(boolean focused) {
  if(!focused) releaseKeys();
  super.onWindowFocusChanged(focused);
 }
}
