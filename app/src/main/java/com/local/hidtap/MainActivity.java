package com.local.hidtap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final String PREF = "hid";
  private static final String KEY_DUCK = "duck";
  private static final String KEY_DELAY = "delay";
  private static final String KEY_EDIT = "edit";
  private static final String KEY_ARMED = "armed";
  private EditText duck;
  private EditText delay;
  private Button run;
  private SharedPreferences prefs;
  private boolean editOn;
  private boolean armed;
  private boolean volUp;
  private boolean volDown;
  private boolean power;
  private boolean comboLock;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Process evProc;
  private Thread evThread;

  private final Runnable editWait = new Runnable() {
    @Override public void run() {
      if (volUp && volDown && !power && !comboLock) {
        comboLock = true;
        toggleEdit();
      }
    }
  };

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    prefs = getSharedPreferences(PREF, MODE_PRIVATE);
    editOn = prefs.getBoolean(KEY_EDIT, false);
    armed = prefs.getBoolean(KEY_ARMED, false);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#111111"));
    int p = (int) (16 * getResources().getDisplayMetrics().density);
    root.setPadding(p, p, p, p);

    delay = new EditText(this);
    delay.setText(prefs.getString(KEY_DELAY, "30"));
    delay.setHint("");
    delay.setInputType(InputType.TYPE_CLASS_NUMBER);
    delay.setTextColor(Color.WHITE);
    delay.setBackgroundColor(Color.parseColor("#1B1B1B"));
    delay.setPadding(p / 2, p / 3, p / 2, p / 3);
    LinearLayout.LayoutParams delayLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    delayLp.bottomMargin = p / 2;
    delay.setLayoutParams(delayLp);
    root.addView(delay);

    duck = new EditText(this);
    duck.setHint("");
    duck.setText(prefs.getString(KEY_DUCK, ""));
    duck.setTextColor(Color.WHITE);
    duck.setBackgroundColor(Color.parseColor("#1B1B1B"));
    duck.setGravity(Gravity.TOP);
    duck.setPadding(p / 2, p / 2, p / 2, p / 2);
    LinearLayout.LayoutParams duckLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    duck.setLayoutParams(duckLp);
    root.addView(duck);

    run = new Button(this);
    run.setText("");
    run.setAllCaps(false);
    LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        (int) (48 * getResources().getDisplayMetrics().density));
    runLp.topMargin = p / 2;
    run.setLayoutParams(runLp);
    run.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { execute(); }
    });
    root.addView(run);
    setContentView(root);
    applyEdit();
    startKeys();
  }

  @Override protected void onPause() {
    super.onPause();
    save();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    stopKeys();
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event) {
    int k = event.getKeyCode();
    if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
        || k == KeyEvent.KEYCODE_POWER) {
      boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
      if (k == KeyEvent.KEYCODE_VOLUME_UP) volUp = down;
      if (k == KeyEvent.KEYCODE_VOLUME_DOWN) volDown = down;
      if (k == KeyEvent.KEYCODE_POWER) power = down;
      onCombo();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  private void onCombo() {
    if (!(volUp && volDown)) {
      handler.removeCallbacks(editWait);
      comboLock = false;
      return;
    }
    if (power) {
      handler.removeCallbacks(editWait);
      if (!comboLock) {
        comboLock = true;
        toggleArm();
      }
      return;
    }
    handler.removeCallbacks(editWait);
    handler.postDelayed(editWait, 350);
  }

  private void toggleEdit() {
    editOn = !editOn;
    applyEdit();
    save();
    log3("EDT", editOn);
  }

  private void toggleArm() {
    armed = !armed;
    save();
    log3("ARM", armed);
  }

  private void applyEdit() {
    int v = editOn ? View.VISIBLE : View.GONE;
    delay.setVisibility(v);
    duck.setVisibility(v);
  }

  private void save() {
    if (duck == null || delay == null || prefs == null) return;
    prefs.edit()
        .putString(KEY_DUCK, duck.getText().toString())
        .putString(KEY_DELAY, delay.getText().toString())
        .putBoolean(KEY_EDIT, editOn)
        .putBoolean(KEY_ARMED, armed)
        .apply();
  }

  private void execute() {
    if (!armed) return;
    save();
    final String text = duck.getText().toString();
    if (text.trim().isEmpty()) {
      log3("EMP", false);
      return;
    }
    String delayMs = delay.getText().toString().trim();
    if (delayMs.isEmpty()) delayMs = "30";
    int ms;
    try {
      ms = Integer.parseInt(delayMs);
      if (ms < 1) ms = 1;
      if (ms > 500) ms = 500;
    } catch (Exception e) {
      ms = 30;
    }
    final int keyDelay = ms;
    run.setEnabled(false);
    new Thread(new Runnable() {
      @Override public void run() {
        String b64 = Base64.encodeToString(
            text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String out = su("echo " + b64
            + " | base64 -d > /data/local/tmp/hid-input.duck"
            + " && export HID_KEY_DELAY_MS=" + keyDelay
            + " && sh /data/adb/modules/hid-tap/hid-ctl.sh exec");
        noteRun(out);
        runOnUiThread(new Runnable() {
          @Override public void run() { run.setEnabled(true); }
        });
      }
    }).start();
  }

  private void noteRun(String out) {
    if (out == null) out = "";
    if (out.contains("CONVERT_FAIL")) log3("CNV", false);
    else if (out.contains("missing /dev/hidg0")) log3("HID", false);
    else if (out.contains("kb_fail") || out.contains("RUN_FAIL")) log3("RUN", false);
    else if (out.contains("RUN_OK")) log3("RUN", true);
    else log3("MOD", false);
  }

  private void log3(final String code, final boolean ok) {
    final String line = code + (ok ? " OK" : " FAIL");
    new Thread(new Runnable() {
      @Override public void run() {
        su("echo '" + line + "' >> /sdcard/hdl.log");
      }
    }).start();
  }

  private void startKeys() {
    evThread = new Thread(new Runnable() {
      @Override public void run() {
        try {
          evProc = Runtime.getRuntime().exec(new String[] {"su", "-c", "getevent -lt"});
          BufferedReader br = new BufferedReader(
              new InputStreamReader(evProc.getInputStream()));
          String line;
          while ((line = br.readLine()) != null) {
            final String s = line;
            final boolean down = s.contains(" DOWN");
            final boolean up = s.contains(" UP");
            if (!down && !up) continue;
            runOnUiThread(new Runnable() {
              @Override public void run() {
                if (s.contains("POWER")) power = down;
                if (s.contains("VOLUMEUP") || s.contains("VOLUME_UP")) volUp = down;
                if (s.contains("VOLUMEDOWN") || s.contains("VOLUME_DOWN")) volDown = down;
                onCombo();
              }
            });
          }
        } catch (Exception ignored) {}
      }
    });
    evThread.start();
  }

  private void stopKeys() {
    try { if (evProc != null) evProc.destroy(); } catch (Exception ignored) {}
  }

  private String su(String cmd) {
    try {
      Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      pump(p.getInputStream(), bos);
      pump(p.getErrorStream(), bos);
      p.waitFor();
      String s = bos.toString("UTF-8").trim();
      if (s.isEmpty()) s = "exit=" + p.exitValue();
      return s;
    } catch (Exception e) {
      return "FAIL " + e.getMessage();
    }
  }

  private void pump(InputStream in, ByteArrayOutputStream bos) {
    try {
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
    } catch (Exception ignored) {}
  }
}
