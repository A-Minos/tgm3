package org.aminos.tgm3.privateport;
/** Standalone JVM checks; no Android dependency. */
public final class ViewportTransformTest {
 private static int checks;
 private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); checks++; }
 private static boolean near(float a,float b) { return Math.abs(a-b)<.0001f; }
 public static void main(String[] args) {
  ViewportTransform p=new ViewportTransform();
  p.gesture(2,50,100,50,100,100,200);
  check(near(p.zoom,2)&&near(p.x,0)&&near(p.y,0),"centered zoom");
  p.gesture(1,50,100,75,150,100,200);
  check(near(p.x,.25f)&&near(p.y,.25f),"normalized drag");
  p=new ViewportTransform(); p.gesture(2,75,100,75,100,100,200);
  check(near(p.x,-.25f),"off-center pinch keeps focus anchored");
  p.gesture(99,50,100,50,100,100,200); check(near(p.zoom,6),"upper zoom bound");
  p.gesture(.0001f,50,100,50,100,100,200); check(near(p.zoom,1),"lower zoom bound");
  p=new ViewportTransform(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN);
  check(near(p.zoom,1)&&near(p.x,0)&&near(p.y,0),"invalid preferences sanitized");
  p.gesture(Float.NaN,0,0,0,0,100,200); check(near(p.zoom,1),"invalid factor ignored");
  p.gesture(2,0,0,0,0,0,200); check(near(p.zoom,1),"unlaid viewport ignored");
  p=new ViewportTransform(6,3,-3); p.constrain(100,200,100,75);
  check(p.x<=3.35f&&p.y>=-1.475f,"picture cannot disappear beyond pan bounds");
  ViewportTransform copy=p.copy(); copy.x=0; check(p.x!=copy.x,"copy independent");
  p=new ViewportTransform(); p.gesture(2,Float.NaN,0,0,0,100,200);
  check(near(p.zoom,1)&&near(p.x,0),"invalid coordinates ignored");
  p=new ViewportTransform(6,4,4); p.constrain(100,100,100,75);
  copy=p.copy(); check(near(p.x,copy.x)&&near(p.y,copy.y),"bounded transform round trips without drift");
  System.out.println("PASS: "+checks+" viewport transform checks");
 }
}
