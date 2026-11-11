package org.aminos.tgm3.privateport;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Properties;

/** Original frontend settings. No game resources or game-derived implementation. */
public final class TouchLayout {
 public enum Control { STICK, B1, B2, B3, B4, COIN, START, MENU, SERVICE, TEST }
 public static final class Position {
  public float x, y, size;
  public Position(float x, float y, float size) { this.x=x; this.y=y; this.size=size; }
 }
 public final EnumMap<Control, Position> positions = new EnumMap<>(Control.class);
 public float opacity = .48f;
 public int player = 1;
 public boolean systemKeys;
 public TouchLayout() { reset(false); }
 public void reset(boolean leftHanded) {
  positions.clear();
  put(Control.STICK, .08f, .70f, .28f);
  put(Control.B1, .835f, .76f, .17f); put(Control.B2, .93f, .62f, .17f);
  put(Control.B3, .835f, .48f, .17f); put(Control.B4, .93f, .90f, .17f);
  put(Control.COIN, .08f, .14f, .14f); put(Control.START, .20f, .14f, .14f);
  put(Control.MENU, .93f, .14f, .14f);
  put(Control.SERVICE, .36f, .14f, .13f); put(Control.TEST, .51f, .14f, .13f);
  if (leftHanded) for (Position value : positions.values()) value.x = 1f-value.x;
 }
 private void put(Control c, float x,float y,float size) { positions.put(c,new Position(x,y,size)); }
 public boolean visible(Control c) { return systemKeys || (c!=Control.SERVICE && c!=Control.TEST); }
 public static float clamp(float value,float low,float high) { return Math.max(low,Math.min(high,value)); }
 public float diameter(Control c, int width, int height) {
  return Math.min(Math.min(width,height), Math.max(32f, positions.get(c).size*Math.min(width,height)));
 }
 public float centerX(Control c,int width,int height) { float r=diameter(c,width,height)/2; return clamp(positions.get(c).x*width,r,width-r); }
 public float centerY(Control c,int width,int height) { float r=diameter(c,width,height)/2; return clamp(positions.get(c).y*height,r,height-r); }
 public Control hit(float x,float y,int width,int height) {
  // Last drawn control wins if the user intentionally overlaps controls.
  Control[] values=Control.values();
  for (int i=values.length-1;i>=0;i--) {
   Control c=values[i]; if(!visible(c)) continue;
   float dx=x-centerX(c,width,height),dy=y-centerY(c,width,height),r=diameter(c,width,height)/2;
   if(dx*dx+dy*dy<=r*r) return c;
  }
  return null;
 }
 public void move(Control c,float x,float y,int width,int height) {
  if(width<=0||height<=0) return;
  float r=diameter(c,width,height)/2;
  positions.get(c).x=clamp(x,r,width-r)/width;
  positions.get(c).y=clamp(y,r,height-r)/height;
 }
 public String encode() {
  Properties p=new Properties(); p.setProperty("version","1");
  p.setProperty("opacity",Float.toString(opacity)); p.setProperty("player",Integer.toString(player));
  p.setProperty("system",Boolean.toString(systemKeys));
  for(Control c:Control.values()) { Position v=positions.get(c); p.setProperty(c.name(),v.x+","+v.y+","+v.size); }
  try { StringWriter out=new StringWriter(); p.store(out,"Touch overlay only; game resources and saves are separate"); return out.toString(); }
  catch(IOException impossible) { throw new IllegalStateException(impossible); }
 }
 public static TouchLayout decode(String text) {
  TouchLayout layout=new TouchLayout(); Properties p=new Properties();
  try { p.load(new StringReader(text)); } catch(IOException|IllegalArgumentException ignored) { return layout; }
  if(!"1".equals(p.getProperty("version"))) return layout;
  layout.opacity=number(p.getProperty("opacity"),.48f,.20f,.90f);
  layout.player="2".equals(p.getProperty("player"))?2:1;
  layout.systemKeys=Boolean.parseBoolean(p.getProperty("system"));
  for(Control c:Control.values()) {
   String[] values=p.getProperty(c.name(),"").split(","); if(values.length!=3) continue;
   Position v=layout.positions.get(c);
   v.x=number(values[0],v.x,0,1); v.y=number(values[1],v.y,0,1);
   v.size=number(values[2],v.size,c==Control.STICK?.24f:.09f,c==Control.STICK?.65f:.30f);
  }
  return layout;
 }
 private static float number(String text,float fallback,float min,float max) {
  try { float value=Float.parseFloat(text); return Float.isFinite(value)?clamp(value,min,max):fallback; }
  catch(RuntimeException ignored) { return fallback; }
 }
}
