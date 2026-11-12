package org.aminos.tgm3.privateport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import org.aminos.tgm3.privateport.TouchLayout.Control;

/** Launcher-only layout editing. Nothing here opens or rewrites the game directory. */
public final class TouchSettingsActivity extends Activity {
 private TouchOverlayView preview;
 private Button options;
 private AlertDialog activeDialog;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); immersive();
  TouchLayout layout=state==null?TouchSettingsStore.read(this):TouchLayout.decode(state.getString("layout",""));
  preview=new TouchOverlayView(this,layout,(key,down)->{},()->{},true);
  FrameLayout frame=new FrameLayout(this); frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
  int gap=ModernUi.dp(this,6); toolbar.setPadding(gap,gap,gap,gap);
  toolbar.setBackground(ModernUi.shape(this,0xee1b2430,24,true));
  toolbar.setElevation(ModernUi.dp(this,8));
  options=button("STICK · Settings",this::showOptions); toolbar.addView(options);
  Button cancel=button("Cancel",this::discard); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2); cp.setMargins(gap,0,gap,0); toolbar.addView(cancel,cp);
  toolbar.addView(ModernUi.button(this,"Save",true,()->{
   try { TouchSettingsStore.write(this,preview.layout()); Toast.makeText(this,"Touch layout saved",Toast.LENGTH_SHORT).show(); finish(); }
   catch(IOException e) { show(ModernUi.dialog(this,"Could not save","Settings were not saved. Check available storage and try again.").setPositiveButton("OK",null).create()); }
  }));
  FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  bar.setMargins(gap,gap,gap,0);
  android.widget.HorizontalScrollView toolbarScroll=new android.widget.HorizontalScrollView(this);
  toolbarScroll.setHorizontalScrollBarEnabled(false); toolbarScroll.addView(toolbar);
  frame.addView(toolbarScroll,bar); setContentView(frame);
  preview.setEditorListener(c->options.setText(c.name()+" · Settings"));
 }
 private Button button(String text,Runnable run) { return ModernUi.button(this,text,false,run); }
 private void immersive() { TouchWindow.apply(this); }
 @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused) immersive(); }
 private void show(AlertDialog dialog) {
  if(activeDialog!=null&&activeDialog.isShowing()) return;
  activeDialog=dialog; dialog.setOnDismissListener(d->{if(activeDialog==dialog) activeDialog=null; immersive();}); ModernUi.show(dialog);
 }
 private void showOptions() {
  if(activeDialog!=null&&activeDialog.isShowing()) return;
  TouchLayout layout=preview.layout(); Control selected=preview.selected();
  LinearLayout content=ModernUi.column(this,24);
  content.addView(ModernUi.text(this,"Drag controls on the canvas to move them. Adjust the selected control below.",14,ModernUi.MUTED));
  ModernUi.space(content,20);
  addSlider(content,"Overlay opacity",Math.round(layout.opacity*100),20,90,value->{layout.opacity=value/100f; preview.invalidate();});
  int low=selected==Control.STICK?24:9, high=selected==Control.STICK?65:30;
  addSlider(content,selected.name()+" size",Math.round(layout.positions.get(selected).size*100),low,high,value->{layout.positions.get(selected).size=value/100f; preview.invalidate();});
  ModernUi.space(content,8); content.addView(ModernUi.heading(this,"Input",16));
  Button player=button("Player "+layout.player+" · Tap to switch",()->{});
  player.setOnClickListener(v->{layout.player=layout.player==1?2:1; player.setText("Player "+layout.player+" · Tap to switch"); preview.invalidate();}); ModernUi.fullButton(content,player);
  Switch system=new Switch(this); system.setText("Service & Test keys"); system.setTextColor(ModernUi.TEXT); system.setTextSize(14);
  system.setMinHeight(ModernUi.dp(this,56)); system.setSwitchPadding(ModernUi.dp(this,16)); system.setChecked(layout.systemKeys);
  system.setOnCheckedChangeListener((b,checked)->{layout.systemKeys=checked; preview.invalidate();}); content.addView(system);
  content.addView(ModernUi.text(this,"Arcade system menus. Leave off for normal play.",12,ModernUi.MUTED));
  ModernUi.space(content,24); content.addView(ModernUi.heading(this,"Layout presets",16));
  content.addView(ModernUi.text(this,"Presets reposition controls; changes are saved only when you tap Save on the canvas.",12,ModernUi.MUTED));
  ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.addView(content);
  AlertDialog dialog=ModernUi.dialog(this,"Customize · "+selected.name(),null).setView(scroll).setPositiveButton("Done",null).create();
  ModernUi.fullButton(content,button("Right-handed · Joystick left",()->{layout.reset(false); preview.invalidate(); dialog.dismiss();}));
  ModernUi.fullButton(content,button("Left-handed · Joystick right",()->{layout.reset(true); preview.invalidate(); dialog.dismiss();}));
  Button reset=button("Reset all settings",()->{
   dialog.dismiss();
   show(ModernUi.dialog(this,"Reset touch settings?","All controls return to the default layout. Tap Save on the canvas to keep the reset.")
    .setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{preview.replace(new TouchLayout()); options.setText(preview.selected().name()+" · Settings");}).create());
  }); reset.setTextColor(ModernUi.DANGER); ModernUi.fullButton(content,reset);
  ModernUi.space(content,20);
  content.addView(ModernUi.text(this,"Default bindings: B1–B4 = Z / X / C / V for P1, Q / W / E / R for P2. Custom game bindings may differ.",12,ModernUi.MUTED));
  show(dialog);
 }
 private interface ValueChanged { void changed(int value); }
 private void addSlider(LinearLayout parent,String label,int value,int low,int high,ValueChanged callback) {
  LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
  TextView caption=ModernUi.heading(this,label,14); row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
  TextView amount=ModernUi.text(this,value+"%",14,ModernUi.ACCENT); row.addView(amount); parent.addView(row);
  SeekBar bar=new SeekBar(this); bar.setMax(high-low); bar.setProgress(value-low); bar.setContentDescription(label);
  bar.setProgressTintList(ColorStateList.valueOf(ModernUi.ACCENT)); bar.setThumbTintList(ColorStateList.valueOf(ModernUi.ACCENT));
  bar.setMinimumHeight(ModernUi.dp(this,48));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
   @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) { int n=progress+low; amount.setText(n+"%"); callback.changed(n); }
   @Override public void onStartTrackingTouch(SeekBar b) {}
   @Override public void onStopTrackingTouch(SeekBar b) {}
  }); parent.addView(bar,new LinearLayout.LayoutParams(-1,ModernUi.dp(this,48))); ModernUi.space(parent,12);
 }
 private void discard() { show(ModernUi.dialog(this,"Discard changes?","Your last saved touch layout will be kept.").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->finish()).create()); }
 @Override public void onBackPressed() { discard(); }
 @Override protected void onSaveInstanceState(Bundle out) { out.putString("layout",preview.layout().encode()); super.onSaveInstanceState(out); }
 @Override protected void onDestroy() { if(activeDialog!=null) { activeDialog.setOnDismissListener(null); activeDialog.dismiss(); activeDialog=null; } super.onDestroy(); }
}
