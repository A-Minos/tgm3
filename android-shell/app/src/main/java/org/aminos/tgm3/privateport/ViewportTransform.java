package org.aminos.tgm3.privateport;

/** Platform-independent, viewport-normalized transform. Never changes the game buffer's aspect. */
final class ViewportTransform {
 float zoom=1, x=0, y=0;
 ViewportTransform() {}
 ViewportTransform(float zoom,float x,float y) {
  this.zoom=finite(zoom)?clamp(zoom,1,6):1;
  this.x=finite(x)?clamp(x,-4,4):0; this.y=finite(y)?clamp(y,-4,4):0;
 }
 ViewportTransform copy() { return new ViewportTransform(zoom,x,y); }
 static boolean finite(float v) { return !Float.isNaN(v)&&!Float.isInfinite(v); }
 static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
 void constrain(float width,float height,float baseWidth,float baseHeight) {
  if(width<=0||height<=0) return;
  float bx=Math.max(0,(baseWidth*zoom+width)/2-width*.15f)/width;
  float by=Math.max(0,(baseHeight*zoom+height)/2-height*.15f)/height;
  x=clamp(x,-bx,bx); y=clamp(y,-by,by);
 }
 void gesture(float factor,float oldX,float oldY,float newX,float newY,float width,float height) {
  if(width<=0||height<=0||!finite(factor)||factor<=0||!finite(oldX)||!finite(oldY)||!finite(newX)||!finite(newY)) return;
  float next=clamp(zoom*factor,1,6), ratio=next/zoom;
  x=(newX-width/2-ratio*(oldX-width/2-x*width))/width;
  y=(newY-height/2-ratio*(oldY-height/2-y*height))/height;
  zoom=next;
 }
}
