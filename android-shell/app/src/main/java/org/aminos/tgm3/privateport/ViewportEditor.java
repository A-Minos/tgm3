package org.aminos.tgm3.privateport;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/** Captures every edit-mode touch; only exactly two fingers move the picture. */
final class ViewportEditor extends View {
 interface Gesture { void change(float factor,float oldX,float oldY,float newX,float newY); }
 private final Gesture gesture;
 private int first=-1,second=-1;
 private float x,y,distance;
 ViewportEditor(Context c,Gesture gesture) { super(c); this.gesture=gesture; setContentDescription("Picture adjustment. Use two fingers to zoom and move."); }
 @Override public boolean onTouchEvent(MotionEvent e) {
  int action=e.getActionMasked();
  if(action!=MotionEvent.ACTION_MOVE||e.getPointerCount()!=2) { first=second=-1; return true; }
  int a=e.getPointerId(0),b=e.getPointerId(1);
  float nx=(e.getX(0)+e.getX(1))/2,ny=(e.getY(0)+e.getY(1))/2;
  float nd=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));
  if(a==first&&b==second&&distance>8&&nd>8) gesture.change(nd/distance,x,y,nx,ny);
  first=a; second=b; x=nx; y=ny; distance=nd; return true;
 }
}
