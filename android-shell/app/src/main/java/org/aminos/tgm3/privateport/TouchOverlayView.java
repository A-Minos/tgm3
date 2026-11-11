package org.aminos.tgm3.privateport;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;
import org.aminos.tgm3.privateport.TouchLayout.Control;
import org.aminos.tgm3.privateport.TouchInputState.Key;

/** Transparent full-viewport overlay; owns every gesture until its final UP/CANCEL. */
public final class TouchOverlayView extends View {
 public interface EditorListener { void selected(Control control); }
 private TouchLayout layout;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Map<Integer,Control> owners=new HashMap<>();
 private final TouchInputState input;
 private final Runnable menu;
 private final boolean editing;
 private EditorListener listener;
 private Control selected=Control.STICK;
 private int dragPointer=-1;
 private float dragOffsetX,dragOffsetY;
 private boolean enabled=true;
 public TouchOverlayView(Context c,TouchLayout layout,TouchInputState.Sink sink,Runnable menu,boolean editing) {
  super(c); this.layout=layout; this.menu=menu; this.editing=editing; input=new TouchInputState(sink);
  setFocusable(false); setSoundEffectsEnabled(false);
  setContentDescription(editing?"Touch layout editor. Drag controls; use Settings for selected size and opacity.":"Game touch controls: joystick, four actions, Coin, Start and Menu");
 }
 public void setEditorListener(EditorListener listener) { this.listener=listener; }
 public TouchLayout layout() { return layout; }
 public Control selected() { return selected; }
 public void replace(TouchLayout layout) { releaseAll(); this.layout=layout; invalidate(); }
 public void stopInput() { enabled=false; releaseAll(); }
 public void releaseAll() { owners.clear(); dragPointer=-1; input.releaseAll(); invalidate(); }
 private String label(Control c) {
  switch(c) {
   case STICK:return "P"+layout.player;
   case COIN:return "Coin"; case START:return "Start"; case MENU:return "Menu";
   case SERVICE:return "Service"; case TEST:return "Test";
   default:return c.name();
  }
 }
 @Override protected void onDraw(Canvas canvas) {
  super.onDraw(canvas);
  if(editing) {
   canvas.drawColor(Color.rgb(12,19,29));
   paint.setColor(Color.rgb(26,40,55)); paint.setStyle(Paint.Style.FILL);
   canvas.drawRect(getWidth()*.32f,getHeight()*.20f,getWidth()*.68f,getHeight()*.96f,paint);
   paint.setColor(0xff9baabd); paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);
   paint.setTextAlign(Paint.Align.CENTER);
   canvas.drawText("Layout preview · drag a control",getWidth()*.5f,getHeight()*.44f,paint);
   canvas.drawText("4-way joystick · P"+layout.player,getWidth()*.5f,getHeight()*.52f,paint);
  }
  for(Control c:Control.values()) {
   if(!layout.visible(c)) continue;
   float x=layout.centerX(c,getWidth(),getHeight()),y=layout.centerY(c,getWidth(),getHeight());
   float r=layout.diameter(c,getWidth(),getHeight())/2;
   boolean pressed=false;
   if(c==Control.STICK) pressed=input.held(Key.UP)||input.held(Key.DOWN)||input.held(Key.LEFT)||input.held(Key.RIGHT);
   else if(c!=Control.MENU) pressed=input.held(Key.valueOf(c.name()));
   int alpha=(int)(layout.opacity*255);
   paint.setStyle(Paint.Style.FILL); paint.setColor(pressed?Color.argb(Math.min(230,alpha+70),56,165,205):Color.argb(alpha,22,36,52));
   canvas.drawCircle(x,y,r,paint);
   paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(editing&&selected==c?4:2);
   paint.setColor(editing&&selected==c?0xff70d5ff:Color.argb(Math.min(240,alpha+65),220,236,250));
   canvas.drawCircle(x,y,r-2,paint); paint.setStyle(Paint.Style.FILL);
   paint.setColor(Color.argb(Math.min(255,alpha+100),240,247,255));
   paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Math.min(r*.47f,18*getResources().getDisplayMetrics().scaledDensity));
   canvas.drawText(label(c),x,y-(paint.ascent()+paint.descent())/2,paint);
   if(c==Control.STICK) {
    paint.setTextSize(r*.31f);
    canvas.drawText("▲",x,y-r*.52f,paint); canvas.drawText("▼",x,y+r*.74f,paint);
    canvas.drawText("◀",x-r*.63f,y+r*.10f,paint); canvas.drawText("▶",x+r*.63f,y+r*.10f,paint);
   }
  }
 }
 @SuppressLint("ClickableViewAccessibility") // Momentary multitouch controls, not click toggles.
 @Override public boolean onTouchEvent(MotionEvent event) {
  if(!enabled) return true;
  int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);
  if(action==MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
  if(editing) return edit(event);
  if(action==MotionEvent.ACTION_DOWN) releaseAll();
  if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
   Control c=layout.hit(event.getX(index),event.getY(index),getWidth(),getHeight());
   if(c!=null && (c!=Control.STICK||!owners.containsValue(Control.STICK))) owners.put(id,c);
  }
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
   Control c=owners.remove(id); input.release(id);
   boolean open=c==Control.MENU && layout.hit(event.getX(index),event.getY(index),getWidth(),getHeight())==Control.MENU;
   if(action==MotionEvent.ACTION_UP) releaseAll();
   invalidate();
   if(open) { releaseAll(); menu.run(); }
   return true;
  }
  for(int i=0;i<event.getPointerCount();i++) {
   int pointer=event.getPointerId(i); Control c=owners.get(pointer); if(c==null) continue;
   Key key=null;
   if(c==Control.STICK) key=TouchInputState.direction(event.getX(i)-layout.centerX(c,getWidth(),getHeight()),event.getY(i)-layout.centerY(c,getWidth(),getHeight()),layout.diameter(c,getWidth(),getHeight())/2);
   else if(c!=Control.MENU && layout.hit(event.getX(i),event.getY(i),getWidth(),getHeight())==c) key=Key.valueOf(c.name());
   input.set(pointer,key);
  }
  invalidate(); return true;
 }
 private boolean edit(MotionEvent event) {
  int action=event.getActionMasked(),index=event.getActionIndex();
  if(action==MotionEvent.ACTION_DOWN) {
   Control c=layout.hit(event.getX(),event.getY(),getWidth(),getHeight());
   if(c!=null) {
    selected=c; dragPointer=event.getPointerId(0);
    dragOffsetX=layout.centerX(c,getWidth(),getHeight())-event.getX();
    dragOffsetY=layout.centerY(c,getWidth(),getHeight())-event.getY();
    if(listener!=null) listener.selected(c); invalidate();
   }
  } else if(action==MotionEvent.ACTION_MOVE&&dragPointer>=0) {
   int i=event.findPointerIndex(dragPointer);
   if(i>=0) { layout.move(selected,event.getX(i)+dragOffsetX,event.getY(i)+dragOffsetY,getWidth(),getHeight()); invalidate(); }
  } else if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&event.getPointerId(index)==dragPointer) dragPointer=-1;
  return true;
 }
 @Override protected void onDetachedFromWindow() { releaseAll(); super.onDetachedFromWindow(); }
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { releaseAll(); super.onSizeChanged(w,h,oldw,oldh); }
}
