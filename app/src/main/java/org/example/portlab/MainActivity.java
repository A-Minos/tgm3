package org.example.portlab;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Original platform-only rendering/JNI probe. This is not a game. */
public final class MainActivity extends Activity {
    private GLSurfaceView surface;
    private TextView status;
    private static native String nativeSmoke();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(15, 20, 30));
        // Target SDK 35 is edge-to-edge. Keep diagnostic text clear of system bars.
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);
        title.setText("Port Lab: no game included");
        title.setTextSize(21);
        title.setTextColor(Color.WHITE);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        title.setPadding(pad, pad, pad, pad);
        layout.addView(title);
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(new ProbeRenderer());
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        layout.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.WHITE);
        status.setPadding(pad, pad, pad, pad);
        status.setText("Synthetic GLES2 triangle.\nNative smoke: running...");
        scroll.addView(status);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        layout.requestApplyInsets();
        // Reservation tests must not block the UI thread. Activity recreation reruns them.
        new Thread(() -> {
            String report;
            try {
                System.loadLibrary("portlab");
                report = nativeSmoke();
            } catch (LinkageError error) {
                report = "Native smoke: library load/link failed\n" + error;
            }
            final String result = report;
            runOnUiThread(() -> {
                if (!isDestroyed()) status.setText("Synthetic GLES2 triangle.\n" + result);
            });
        }, "portlab-smoke").start();
    }

    @Override protected void onResume() { super.onResume(); surface.onResume(); }
    @Override protected void onPause() { surface.onPause(); super.onPause(); }

    private final class ProbeRenderer implements GLSurfaceView.Renderer {
        private final FloatBuffer vertices = ByteBuffer.allocateDirect(6 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private int program;
        private int position;
        ProbeRenderer() {
            vertices.put(new float[]{0f, 0.65f, -0.65f, -0.65f, 0.65f, -0.65f}).position(0);
        }
        private int shader(int type, String source) {
            int id = GLES20.glCreateShader(type);
            GLES20.glShaderSource(id, source);
            GLES20.glCompileShader(id);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(id);
                GLES20.glDeleteShader(id);
                throw new IllegalStateException(log);
            }
            return id;
        }
        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = 0;
            GLES20.glClearColor(0.06f, 0.08f, 0.12f, 1f);
            int vertex = 0, fragment = 0, candidate = 0;
            try {
                vertex = shader(GLES20.GL_VERTEX_SHADER,
                        "attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.0,1.0);}");
                fragment = shader(GLES20.GL_FRAGMENT_SHADER,
                        "precision mediump float; void main(){gl_FragColor=vec4(0.15,0.8,0.65,1.0);}");
                candidate = GLES20.glCreateProgram();
                GLES20.glAttachShader(candidate, vertex);
                GLES20.glAttachShader(candidate, fragment);
                GLES20.glLinkProgram(candidate);
                int[] ok = new int[1];
                GLES20.glGetProgramiv(candidate, GLES20.GL_LINK_STATUS, ok, 0);
                if (ok[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(candidate));
                position = GLES20.glGetAttribLocation(candidate, "aPosition");
                program = candidate;
            } catch (RuntimeException error) {
                if (candidate != 0) GLES20.glDeleteProgram(candidate);
                runOnUiThread(() -> {
                    if (!isDestroyed()) android.widget.Toast.makeText(MainActivity.this,
                            "GLES2 probe failed: " + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                });
            } finally {
                if (vertex != 0) GLES20.glDeleteShader(vertex);
                if (fragment != 0) GLES20.glDeleteShader(fragment);
            }
        }
        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            int size = Math.min(width, height);
            GLES20.glViewport((width-size)/2, (height-size)/2, size, size);
        }
        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (program == 0) return;
            GLES20.glUseProgram(program);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
            GLES20.glDisableVertexAttribArray(position);
        }
    }
}
