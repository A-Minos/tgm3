package org.aminos.tgm3.privateport;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Pointer-owned momentary keys: a pointer release cannot release another pointer's key. */
public final class TouchInputState {
 public enum Key { UP, DOWN, LEFT, RIGHT, B1, B2, B3, B4, COIN, START, SERVICE, TEST }
 public interface Sink { void changed(Key key, boolean down); }
 private final Map<Integer,EnumSet<Key>> pointers=new HashMap<>();
 private final EnumSet<Key> held=EnumSet.noneOf(Key.class);
 private final Sink sink;
 public TouchInputState(Sink sink) { this.sink=sink; }
 public void set(int pointer,Key key) { set(pointer,key==null?EnumSet.noneOf(Key.class):EnumSet.of(key)); }
 public void set(int pointer,Set<Key> keys) {
  if(keys.isEmpty()) pointers.remove(pointer); else pointers.put(pointer,EnumSet.copyOf(keys));
  reconcile();
 }
 public void release(int pointer) { pointers.remove(pointer); reconcile(); }
 public void releaseAll() { pointers.clear(); reconcile(); }
 public boolean held(Key key) { return held.contains(key); }
 private void reconcile() {
  EnumSet<Key> next=EnumSet.noneOf(Key.class);
  for(Set<Key> keys:pointers.values()) next.addAll(keys);
  // Release before pressing when a joystick changes directions.
  for(Key key:Key.values()) if(held.contains(key)&&!next.contains(key)) sink.changed(key,false);
  for(Key key:Key.values()) if(!held.contains(key)&&next.contains(key)) sink.changed(key,true);
  held.clear(); held.addAll(next);
 }
 public static Key direction(float dx,float dy,float radius) {
  if(radius<=0||dx*dx+dy*dy<radius*radius*.12f) return null;
  // Four-way digital joystick. No diagonal Up pulse while moving horizontally.
  if(Math.abs(dx)>=Math.abs(dy)) return dx<0?Key.LEFT:Key.RIGHT;
  return dy<0?Key.UP:Key.DOWN;
 }
}
