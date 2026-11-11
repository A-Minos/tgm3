package org.aminos.tgm3.privateport;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import org.aminos.tgm3.privateport.TouchLayout.Control;

/** Launcher-only layout editing. Nothing here opens or rewrites the game directory. */
public final class TouchSettingsActivity extends Activity {
 private TouchOverlayView preview;
 private Button options;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); immersive();
  TouchLayout layout=state==null?TouchSettingsStore.read(this):TouchLayout.decode(state.getString("layout",""));
  preview=new TouchOverlayView(this,layout,(key,down)->{},()->{},true);
  FrameLayout frame=new FrameLayout(this); frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout toolbar=new LinearLayout(this); toolbar.setBackgroundColor(0xdd142332);
  options=button("Settings · STICK",this::showOptions); toolbar.addView(options);
  toolbar.addView(button("Save",()->{
   try { TouchSettingsStore.write(this,preview.layout()); Toast.makeText(this,"Touch layout saved",Toast.LENGTH_SHORT).show(); finish(); }
   catch(IOException e) { new AlertDialog.Builder(this).setTitle("Could not save").setMessage("Settings were not saved. Check available storage and try again.").setPositiveButton("OK",null).show(); }
  }));
  toolbar.addView(button("Cancel",this::discard));
  FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  frame.addView(toolbar,bar); setContentView(frame);
  preview.setEditorListener(c->options.setText("Settings · "+c.name()));
 }
 private Button button(String text,Runnable run) { Button b=new Button(this); b.setText(text); b.setOnClickListener(v->run.run()); return b; }
 private void immersive() { TouchWindow.apply(this); }
 @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused) immersive(); }
 private void showOptions() {
  TouchLayout layout=preview.layout(); Control selected=preview.selected();
  LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
  int pad=(int)(16*getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
  TextView help=new TextView(this);
  help.setText("Drag any control in the preview, then adjust its size here. Positions and sizes scale with the screen. These keys use the game's default keyboard bindings; custom bindings may differ. Only four action buttons are mapped. B1/B2/B3/B4 = Z/X/C/V for P1, Q/W/E/R for P2."); content.addView(help);
  addSlider(content,"Opacity",Math.round(layout.opacity*100),20,90,value->{layout.opacity=value/100f; preview.invalidate();});
  int low=selected==Control.STICK?24:9, high=selected==Control.STICK?65:30;
  addSlider(content,selected.name()+" size",Math.round(layout.positions.get(selected).size*100),low,high,value->{layout.positions.get(selected).size=value/100f; preview.invalidate();});
  Button player=button("",()->{}); player.setText("Player: P"+layout.player+" (tap to switch)");
  player.setOnClickListener(v->{layout.player=layout.player==1?2:1; player.setText("Player: P"+layout.player+" (tap to switch)"); preview.invalidate();}); content.addView(player);
  CheckBox system=new CheckBox(this); system.setText("Show Service and Test keys (arcade system menus)"); system.setChecked(layout.systemKeys);
  system.setOnCheckedChangeListener((b,checked)->{layout.systemKeys=checked; preview.invalidate();}); content.addView(system);
  ScrollView scroll=new ScrollView(this); scroll.addView(content);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Touch settings · "+selected.name()).setView(scroll).setPositiveButton("Done",null).create();
  content.addView(button("Right-handed layout (joystick left)",()->{layout.reset(false); preview.invalidate(); dialog.dismiss();}));
  content.addView(button("Left-handed layout (joystick right)",()->{layout.reset(true); preview.invalidate(); dialog.dismiss();}));
  content.addView(button("Reset all touch settings",()->{preview.replace(new TouchLayout()); options.setText("Settings · "+preview.selected().name()); dialog.dismiss();}));
  dialog.show();
 }
 private interface ValueChanged { void changed(int value); }
 private void addSlider(LinearLayout parent,String label,int value,int low,int high,ValueChanged callback) {
  TextView caption=new TextView(this); caption.setText(label+": "+value+"%"); parent.addView(caption);
  SeekBar bar=new SeekBar(this); bar.setMax(high-low); bar.setProgress(value-low); bar.setContentDescription(label);
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
   @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) { int n=progress+low; caption.setText(label+": "+n+"%"); callback.changed(n); }
   @Override public void onStartTrackingTouch(SeekBar b) {}
   @Override public void onStopTrackingTouch(SeekBar b) {}
  }); parent.addView(bar);
 }
 private void discard() { new AlertDialog.Builder(this).setTitle("Discard layout changes?").setMessage("Your last saved touch layout will be kept.").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->finish()).show(); }
 @Override public void onBackPressed() { discard(); }
 @Override protected void onSaveInstanceState(Bundle out) { out.putString("layout",preview.layout().encode()); super.onSaveInstanceState(out); }
}
