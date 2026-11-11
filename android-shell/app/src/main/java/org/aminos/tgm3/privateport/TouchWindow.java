package org.aminos.tgm3.privateport;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** One viewport policy for the game and its layout editor. */
public final class TouchWindow {
 private TouchWindow() {}
 public static void apply(Activity activity) {
  Window window=activity.getWindow();
  window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
  window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
    |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  if(Build.VERSION.SDK_INT>=28) {
   WindowManager.LayoutParams params=window.getAttributes();
   if(params.layoutInDisplayCutoutMode!=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER) {
    params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
    window.setAttributes(params);
   }
  }
  if(Build.VERSION.SDK_INT>=30) {
   WindowInsetsController controller=window.getInsetsController();
   if(controller!=null) {
    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    controller.hide(WindowInsets.Type.systemBars());
   }
  }
 }
}
