package org.aminos.tgm3.privateport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small native-view design system; no web layer or game/input dependencies. */
final class ModernUi {
 static final int BACKGROUND=0xff10151d, SURFACE=0xff1b2430, BORDER=0xff344152;
 static final int TEXT=0xfff0f4fa, MUTED=0xffacb8c8, ACCENT=0xff8be0cf, DANGER=0xffffb4ab;
 private ModernUi() {}
 static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
 static GradientDrawable shape(Context c,int color,int radius,boolean outline) {
  GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
  if(outline) d.setStroke(dp(c,1),BORDER); return d;
 }
 static TextView text(Context c,String value,int size,int color) {
  TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
  t.setLineSpacing(dp(c,3),1); return t;
 }
 static TextView heading(Context c,String value,int size) {
  TextView t=text(c,value,size,TEXT); t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t;
 }
 static LinearLayout column(Context c,int padding) {
  LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL);
  int p=dp(c,padding); v.setPadding(p,p,p,p); return v;
 }
 static LinearLayout card(Context c) {
  LinearLayout v=column(c,20); v.setBackground(shape(c,SURFACE,24,true)); return v;
 }
 static void space(LinearLayout parent,int height) {
  parent.addView(new View(parent.getContext()),new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
 }
 static Button button(Context c,String title,boolean primary,Runnable action) {
  Button b=new Button(c); b.setText(title); b.setTextSize(14); b.setAllCaps(false);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
    new int[]{MUTED,primary?BACKGROUND:TEXT}));
  int fill=primary?ACCENT:SURFACE;
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337fffff),shape(c,fill,16,!primary),null));
  b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{BORDER,fill}));
  b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48));
  b.setPadding(dp(c,20),dp(c,10),dp(c,20),dp(c,10));
  b.setOnClickListener(v->action.run()); return b;
 }
 static void fullButton(LinearLayout parent,Button b) {
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(parent.getContext(),12); parent.addView(b,p);
 }
 static AlertDialog.Builder dialog(Activity a,String title,String message) {
  AlertDialog.Builder builder=new AlertDialog.Builder(a).setTitle(title);
  if(message!=null) builder.setMessage(message); return builder;
 }
 static void show(AlertDialog d) {
  d.show(); Window w=d.getWindow(); if(w==null) return;
  Context c=d.getContext(); w.setBackgroundDrawable(shape(c,SURFACE,28,true));
  int width=Math.min(dp(c,560),c.getResources().getDisplayMetrics().widthPixels-dp(c,32));
  w.setLayout(width,-2); w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
  android.view.WindowManager.LayoutParams p=w.getAttributes(); p.dimAmount=.65f; w.setAttributes(p);
  for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
   Button b=d.getButton(which); if(b!=null) { b.setAllCaps(false); b.setTextColor(ACCENT); b.setMinHeight(dp(c,48)); }
  }
 }
}
