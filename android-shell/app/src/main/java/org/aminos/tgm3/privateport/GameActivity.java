package org.aminos.tgm3.privateport;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import org.libsdl.app.SDLActivity;
import java.io.File;
import org.aminos.tgm3.privateport.TouchInputState.Key;

public final class GameActivity extends SDLActivity {
 private TouchOverlayView overlay;
 private AlertDialog exitDialog;
 private boolean quitting;
 private boolean immersiveReady, immersivePosted;
 private OnBackInvokedCallback backCallback;
 @Override protected String[] getLibraries() { return new String[]{"SDL2","tgm3recomp"}; }
 @Override protected String getMainFunction() { return "SDL_main"; }
 @Override protected String[] getArguments() { return new String[]{new File(getFilesDir(),"game/imported").getAbsolutePath()}; }
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  if(mLayout==null||mSurface==null) return;
  TouchLayout layout=TouchSettingsStore.read(this);
  overlay=new TouchOverlayView(this,layout,(key,down)->{
   int code=keyCode(key,layout.player);
   if(down) onNativeKeyDown(code); else onNativeKeyUp(code);
   if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)
    Log.d("TGM3Touch",(down?"down":"up")+" "+key+" key="+code+" p="+layout.player+" t="+SystemClock.uptimeMillis());
  },this::requestExit,false);
  // SDL gets the full viewport. No fixed control row consumes rendering space.
  mSurface.setLayoutParams(new RelativeLayout.LayoutParams(-1,-1));
  mLayout.addView(overlay,new RelativeLayout.LayoutParams(-1,-1));
  mSurface.requestFocus();
  immersiveReady=true; scheduleImmersive();
  if(Build.VERSION.SDK_INT>=33) {
   backCallback=this::requestExit;
   getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
  }
 }
 private static int keyCode(Key key,int player) {
  boolean p2=player==2;
  switch(key) {
   case UP:return p2?KeyEvent.KEYCODE_I:KeyEvent.KEYCODE_DPAD_UP;
   case DOWN:return p2?KeyEvent.KEYCODE_K:KeyEvent.KEYCODE_DPAD_DOWN;
   case LEFT:return p2?KeyEvent.KEYCODE_J:KeyEvent.KEYCODE_DPAD_LEFT;
   case RIGHT:return p2?KeyEvent.KEYCODE_L:KeyEvent.KEYCODE_DPAD_RIGHT;
   case B1:return p2?KeyEvent.KEYCODE_Q:KeyEvent.KEYCODE_Z;
   case B2:return p2?KeyEvent.KEYCODE_W:KeyEvent.KEYCODE_X;
   case B3:return p2?KeyEvent.KEYCODE_E:KeyEvent.KEYCODE_C;
   case B4:return p2?KeyEvent.KEYCODE_R:KeyEvent.KEYCODE_V;
   case COIN:return p2?KeyEvent.KEYCODE_7:KeyEvent.KEYCODE_5;
   case START:return p2?KeyEvent.KEYCODE_2:KeyEvent.KEYCODE_1;
   case SERVICE:return p2?KeyEvent.KEYCODE_8:KeyEvent.KEYCODE_6;
   case TEST:return KeyEvent.KEYCODE_9;
   default:throw new IllegalArgumentException("Unknown touch key");
  }
 }
 // Intercept before SDL's keyboard path consumes KEYCODE_BACK as an ordinary game key.
 @Override public boolean dispatchKeyEvent(KeyEvent event) {
  if(event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
   if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()) requestExit();
   return true;
  }
  return super.dispatchKeyEvent(event);
 }
 @Override public void onBackPressed() { requestExit(); }
 private void requestExit() {
  if(quitting||isFinishing()||isDestroyed()||(exitDialog!=null&&exitDialog.isShowing())) return;
  releaseKeys();
  exitDialog=ModernUi.dialog(this,"Return to launcher?","Your saved files are kept. The current game will end, and you can edit touch controls in the launcher.")
    .setNegativeButton("Keep playing",(d,w)->{})
    .setPositiveButton("Exit game",(d,w)->{
     quitting=true; if(overlay!=null) overlay.stopInput();
     if(mBrokenLibraries) { finish(); return; }
     Log.i("TGM3Touch","Confirmed exit: sending SDL_QUIT");
     // Same event as closing the SDL window. The existing native input pump handles it.
     // Do not call finish() here: SDLActivity.onDestroy joins the native thread on UI.
     nativeSendQuit();
    }).create();
  exitDialog.setOnDismissListener(d->{exitDialog=null; if(!quitting&&mSurface!=null) mSurface.requestFocus();});
  ModernUi.show(exitDialog);
 }
 private void releaseKeys() { if(overlay!=null) overlay.releaseAll(); }
 @Override protected void onPause() { releaseKeys(); super.onPause(); }
 // SDL sends windowed requests both at creation and from native SDL_CreateWindow.
 // Reapply after the queued style message finishes, without modifying SDL Java/native.
 private void scheduleImmersive() {
  if(!immersiveReady||immersivePosted) return;
  immersivePosted=true;
  getWindow().getDecorView().post(()->{
   if(!isFinishing()&&!isDestroyed()) { TouchWindow.apply(this); mFullscreenModeActive=true; }
   immersivePosted=false;
  });
 }
 @Override public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
  super.onWindowAttributesChanged(params);
  if((params.flags&WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)!=0
      ||(params.flags&WindowManager.LayoutParams.FLAG_FULLSCREEN)==0) scheduleImmersive();
 }
 @Override public void onSystemUiVisibilityChange(int visibility) {
  super.onSystemUiVisibilityChange(visibility);
  // A native windowed request removes IMMERSIVE_STICKY. User-revealed transient
  // system bars keep it, so let Android's normal swipe-to-reveal behavior work.
  if((visibility&View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)==0) scheduleImmersive();
 }
 @Override public void onWindowFocusChanged(boolean focused) {
  if(!focused) releaseKeys();
  super.onWindowFocusChanged(focused);
  if(focused) scheduleImmersive();
 }
 @Override protected void onDestroy() {
  immersiveReady=false;
  releaseKeys();
  if(Build.VERSION.SDK_INT>=33&&backCallback!=null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
  if(exitDialog!=null) { exitDialog.setOnDismissListener(null); exitDialog.dismiss(); exitDialog=null; }
  super.onDestroy();
 }
}
