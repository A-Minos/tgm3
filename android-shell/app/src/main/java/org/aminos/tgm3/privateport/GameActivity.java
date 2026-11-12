package org.aminos.tgm3.privateport;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
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
 private ViewportTransform picture=new ViewportTransform();
 private ViewportEditor pictureEditor;
 private LinearLayout pictureBar;
 private float pictureAspect=4f/3f; // fallback until the native window supplies its aspect ratio
 private int pictureWidth,pictureHeight;
 private boolean picturePortrait;
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
  // The surface is fitted below; controls stay in full-window coordinates.
  mSurface.setLayoutParams(new RelativeLayout.LayoutParams(-1,-1));
  mLayout.addView(overlay,new RelativeLayout.LayoutParams(-1,-1));
  mLayout.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layoutPicture(r-l,b-t));
  mLayout.post(()->layoutPicture(mLayout.getWidth(),mLayout.getHeight()));
  mSurface.requestFocus();
  immersiveReady=true; scheduleImmersive();
  if(Build.VERSION.SDK_INT>=33) {
   backCallback=this::requestExit;
   getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
  }
 }
 // Native requests must not re-lock the activity to landscape. FULL_USER also
 // avoids SDL's portrait-only SurfaceView readiness guard for a landscape buffer.
 @Override public void setOrientationBis(int w,int h,boolean resizable,String hint) {
  if(w>0&&h>0) pictureAspect=(float)w/h;
  setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
  if(mLayout!=null) mLayout.post(()->layoutPicture(mLayout.getWidth(),mLayout.getHeight()));
 }
 private SharedPreferences picturePrefs() { return getSharedPreferences("picture_viewport_v1",MODE_PRIVATE); }
 private String picturePrefix() { return picturePortrait?"portrait_":"landscape_"; }
 private void readPicture() {
  SharedPreferences p=picturePrefs(); String k=picturePrefix();
  picture=new ViewportTransform(p.getFloat(k+"zoom",1),p.getFloat(k+"x",0),p.getFloat(k+"y",0));
 }
 private void layoutPicture(int width,int height) {
  if(width<=0||height<=0||mSurface==null) return;
  boolean portrait=height>width;
  if(width!=pictureWidth||height!=pictureHeight) {
   // Cancel an unfinished gesture/edit on resize rather than saving into the wrong orientation.
   if(pictureEditor!=null) finishPicture(false);
   pictureWidth=width; pictureHeight=height; picturePortrait=portrait; readPicture();
  }
  int sw=Math.min(width,Math.max(1,Math.round(height*pictureAspect)));
  int sh=Math.max(1,Math.round(sw/pictureAspect));
  RelativeLayout.LayoutParams old=(RelativeLayout.LayoutParams)mSurface.getLayoutParams();
  if(old.width!=sw||old.height!=sh||old.getRule(RelativeLayout.CENTER_IN_PARENT)==0) {
   RelativeLayout.LayoutParams p=new RelativeLayout.LayoutParams(sw,sh);
   p.addRule(RelativeLayout.CENTER_IN_PARENT); mSurface.setLayoutParams(p);
  }
  picture.constrain(width,height,sw,sh);
  mSurface.setPivotX(sw/2f); mSurface.setPivotY(sh/2f);
  mSurface.setScaleX(picture.zoom); mSurface.setScaleY(picture.zoom);
  mSurface.setTranslationX(picture.x*width); mSurface.setTranslationY(picture.y*height);
 }
 private void editPicture() {
  if(quitting||isFinishing()||isDestroyed()||pictureEditor!=null) return;
  releaseKeys(); overlay.setVisibility(View.GONE);
  pictureEditor=new ViewportEditor(this,(factor,ox,oy,nx,ny)->{
   picture.gesture(factor,ox,oy,nx,ny,pictureWidth,pictureHeight);
   layoutPicture(pictureWidth,pictureHeight);
  });
  mLayout.addView(pictureEditor,new RelativeLayout.LayoutParams(-1,-1));
  pictureBar=ModernUi.column(this,8); pictureBar.setBackgroundColor(ModernUi.SURFACE);
  pictureBar.addView(ModernUi.text(this,"Picture: two fingers to zoom / move. Game keeps running.",13,ModernUi.TEXT));
  HorizontalScrollView scroll=new HorizontalScrollView(this);
  LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
  buttons.addView(ModernUi.button(this,"Save",true,()->finishPicture(true)));
  buttons.addView(ModernUi.button(this,"Cancel",false,()->finishPicture(false)));
  buttons.addView(ModernUi.button(this,"Reset",false,()->{picture=new ViewportTransform(); layoutPicture(pictureWidth,pictureHeight);}));
  scroll.addView(buttons); pictureBar.addView(scroll);
  mLayout.addView(pictureBar,new RelativeLayout.LayoutParams(-1,-2));
 }
 private void finishPicture(boolean save) {
  if(pictureEditor==null) return;
  if(save) {
   String k=picturePrefix(); picturePrefs().edit().putFloat(k+"zoom",picture.zoom)
    .putFloat(k+"x",picture.x).putFloat(k+"y",picture.y).apply();
  } else readPicture();
  mLayout.removeView(pictureEditor); mLayout.removeView(pictureBar); pictureEditor=null; pictureBar=null;
  releaseKeys(); overlay.setVisibility(View.VISIBLE);
  layoutPicture(pictureWidth,pictureHeight); mSurface.requestFocus();
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
  if(pictureEditor!=null) { finishPicture(false); return; }
  if(quitting||isFinishing()||isDestroyed()||(exitDialog!=null&&exitDialog.isShowing())) return;
  releaseKeys();
  exitDialog=ModernUi.dialog(this,"Return to launcher?","Your saved files are kept. The current game will end, and you can edit touch controls in the launcher.")
    .setNeutralButton("Adjust picture",(d,w)->mLayout.post(this::editPicture))
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
 private void releaseKeys() {
  if(overlay!=null) overlay.releaseAll();
 }
 @Override protected void onPause() { if(pictureEditor!=null) finishPicture(false); releaseKeys(); super.onPause(); }
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
  if(!focused) { if(pictureEditor!=null) finishPicture(false); releaseKeys(); }
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
