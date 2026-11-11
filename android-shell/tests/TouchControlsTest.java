package org.aminos.tgm3.privateport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.aminos.tgm3.privateport.TouchInputState.Key;
import org.aminos.tgm3.privateport.TouchLayout.Control;

/** Standalone JVM assertions: javac TouchLayout.java TouchInputState.java TouchControlsTest.java. */
public final class TouchControlsTest {
 private static int checks;
 private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); checks++; System.out.println("PASS: "+message); }
 private static boolean near(float a,float b) { return Math.abs(a-b)<.0001f; }
 public static void main(String[] ignored) {
  TouchLayout layout=new TouchLayout();
  check(layout.positions.size()==10,"complete layout inventory");
  check(layout.player==1&&!layout.systemKeys,"P1 default, system keys hidden");
  check(layout.visible(Control.MENU)&&!layout.visible(Control.TEST)&&!layout.visible(Control.SERVICE),"menu always available, system keys opt-in");
  check(layout.positions.get(Control.STICK).x<.3&&layout.positions.get(Control.B1).x>.7,"right-handed preset stays at screen sides");
  float x=layout.positions.get(Control.STICK).x; layout.reset(true);
  check(near(layout.positions.get(Control.STICK).x,1-x),"left-handed preset mirrors joystick");
  layout.player=2; layout.systemKeys=true; layout.opacity=.73f;
  layout.positions.get(Control.B2).size=.29f; layout.positions.get(Control.B2).x=.6f;
  TouchLayout restored=TouchLayout.decode(layout.encode());
  check(restored.player==2&&restored.systemKeys&&near(restored.opacity,.73f),"global settings encode/decode");
  for(Control c:Control.values()) {
   TouchLayout.Position a=layout.positions.get(c),b=restored.positions.get(c);
   check(near(a.x,b.x)&&near(a.y,b.y)&&near(a.size,b.size),"layout round-trip "+c);
  }
  TouchLayout invalid=TouchLayout.decode("version=1\nopacity=NaN\nplayer=99\nB1=Infinity,-Infinity,NaN\nSTICK=55,-12,100\nB2=bad\n");
  check(near(invalid.opacity,.48f)&&invalid.player==1,"bad opacity/player falls back");
  check(near(invalid.positions.get(Control.B1).x,.835f)&&near(invalid.positions.get(Control.B1).size,.17f),"nonfinite coordinates/sizes rejected");
  check(invalid.positions.get(Control.STICK).x==1&&invalid.positions.get(Control.STICK).y==0&&near(invalid.positions.get(Control.STICK).size,.65f),"finite out-of-range values bounded");
  check(TouchLayout.decode("version=900\nplayer=2").player==1,"unknown schema safely uses defaults");
  check(TouchLayout.decode("\\uZZZZ").player==1,"malformed properties safe");
  for(int[] viewport:new int[][]{{1280,720},{2400,1080},{720,1280},{320,240}}) {
   int w=viewport[0],h=viewport[1]; TouchLayout fit=new TouchLayout();
   fit.move(Control.STICK,-100,-100,w,h); float r=fit.diameter(Control.STICK,w,h)/2;
   check(near(fit.centerX(Control.STICK,w,h),r)&&near(fit.centerY(Control.STICK,w,h),r),"drag stays in viewport "+w+"x"+h);
   fit.move(Control.B4,99999,99999,w,h); r=fit.diameter(Control.B4,w,h)/2;
   check(near(fit.centerX(Control.B4,w,h),w-r)&&near(fit.centerY(Control.B4,w,h),h-r),"button stays in viewport "+w+"x"+h);
  }
  TouchLayout defaults=new TouchLayout();
  check(defaults.centerX(Control.STICK,1280,720)+defaults.diameter(Control.STICK,1280,720)/2<=1280*.16f,"default joystick stays within outer 16 percent on 16:9 screen");
  for(Control c:Control.values()) if(defaults.visible(c)) check(defaults.hit(defaults.centerX(c,1280,720),defaults.centerY(c,1280,720),1280,720)==c,"default hit target "+c);
  check(defaults.hit(640,360,1280,720)==null,"central board is free of default hit targets");
  check(TouchInputState.direction(0,0,100)==null,"joystick deadzone");
  check(TouchInputState.direction(25,0,100)==null,"deadzone rejects small drift");
  check(TouchInputState.direction(90,20,100)==Key.RIGHT,"four-way right");
  check(TouchInputState.direction(-90,20,100)==Key.LEFT,"four-way left");
  check(TouchInputState.direction(20,-90,100)==Key.UP,"four-way up");
  check(TouchInputState.direction(20,90,100)==Key.DOWN,"four-way down");
  check(TouchInputState.direction(70,-70,100)==Key.RIGHT,"diagonal tie stays horizontal");
  List<String> events=new ArrayList<>(); TouchInputState input=new TouchInputState((key,down)->events.add(key+":"+down));
  input.set(10,Key.DOWN); input.set(20,Key.B1);
  check(input.held(Key.DOWN)&&input.held(Key.B1),"multitouch direction plus rotation");
  check(events.equals(Arrays.asList("DOWN:true","B1:true")),"one press transition per key");
  input.set(20,Key.B1); check(events.size()==2,"repeat MOVE does not duplicate keydown");
  input.set(30,Key.B1); input.release(20); check(input.held(Key.B1)&&events.size()==2,"second pointer keeps shared key held");
  input.release(30); check(!input.held(Key.B1)&&input.held(Key.DOWN),"release only the last key owner");
  events.clear(); input.set(10,Key.LEFT);
  check(events.equals(Arrays.asList("DOWN:false","LEFT:true")),"direction change releases before new press");
  input.set(10,(Key)null); check(!input.held(Key.LEFT),"joystick center releases direction");
  input.set(6,EnumSet.of(Key.B2,Key.RIGHT)); input.set(8,Key.COIN); events.clear(); input.releaseAll();
  check(events.size()==3&&!input.held(Key.B2)&&!input.held(Key.RIGHT)&&!input.held(Key.COIN),"cancel/focus-loss releaseAll releases every active key");
  input.releaseAll(); input.release(123); check(events.size()==3,"duplicate cancel/release is harmless");
  input.set(3,Key.START); input.release(3); check(events.get(events.size()-1).equals("START:false"),"new gesture works after cancel");
  System.out.println("PASS: "+checks+" touch model assertions");
 }
}
