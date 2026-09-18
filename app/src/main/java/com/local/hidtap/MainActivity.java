package com.local.hidtap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final String PREF = "hid";
  private static final long TAP_WIN = 900;
  private static final String CTL = "/data/local/tmp/hid-ctl.sh";
  private static final String DUCK = "/data/local/tmp/hid-input.duck";
  private EditText duck;
  private EditText delay;
  private Button run;
  private SharedPreferences prefs;
  private boolean editOn;
  private boolean armed;
  private int upN;
  private int dnN;
  private long upT;
  private long dnT;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    prefs = getSharedPreferences(PREF, MODE_PRIVATE);
    editOn = prefs.getBoolean("edit", false);
    armed = prefs.getBoolean("armed", false);
    killOldKeys();

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#111111"));
    int p = (int) (16 * getResources().getDisplayMetrics().density);
    root.setPadding(p, p, p, p);

    delay = new EditText(this);
    delay.setText(prefs.getString("delay", "30"));
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
    duck.setText(prefs.getString("duck", ""));
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
  }

  @Override protected void onPause() {
    super.onPause();
    save();
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event) {
    int k = event.getKeyCode();
    if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) {
      return super.dispatchKeyEvent(event);
    }
    if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
      return true;
    }
    long now = SystemClock.uptimeMillis();
    if (k == KeyEvent.KEYCODE_VOLUME_UP) {
      if (now - upT > TAP_WIN) upN = 0;
      upT = now;
      upN++;
      dnN = 0;
      if (upN >= 3) {
        upN = 0;
        toggleEdit();
      }
    } else {
      if (now - dnT > TAP_WIN) dnN = 0;
      dnT = now;
      dnN++;
      upN = 0;
      if (dnN >= 3) {
        dnN = 0;
        toggleArm();
      }
    }
    return true;
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
        .putString("duck", duck.getText().toString())
        .putString("delay", delay.getText().toString())
        .putBoolean("edit", editOn)
        .putBoolean("armed", armed)
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
    if (delayMs.isEmpty()) delayMs = "0";
    int ms;
    try {
      ms = Integer.parseInt(delayMs);
      if (ms < 0) ms = 0;
      if (ms > 2000) ms = 2000;
    } catch (Exception e) {
      ms = 0;
    }
    final int keyDelay = ms;
    run.setEnabled(false);
    new Thread(new Runnable() {
      @Override public void run() {
        String out = runDuck(text, keyDelay);
        noteRun(out);
        runOnUiThread(new Runnable() {
          @Override public void run() { run.setEnabled(true); }
        });
      }
    }).start();
  }

  private String installCtl() {
    String ctl = asset("hid-ctl.sh");
    if (ctl.isEmpty()) return "MOD_NO_ASSET";
    try {
      File ctlF = new File(getCacheDir(), "hid-ctl.sh");
      writeFile(ctlF, ctl);
      return su("cp " + ctlF.getAbsolutePath() + " " + CTL + " && chmod 755 " + CTL);
    } catch (Exception e) {
      return "FAIL " + e.getMessage();
    }
  }

  private String runDuck(String text, int keyDelay) {
    String ins = installCtl();
    if (ins.contains("MOD_NO_ASSET") || ins.startsWith("FAIL ")) return ins;
    try {
      File duckF = new File(getCacheDir(), "hid-input.duck");
      writeFile(duckF, text);
      return su(
          "cp " + duckF.getAbsolutePath() + " " + DUCK
              + " && HID_KEY_DELAY_MS=" + keyDelay + " sh " + CTL + " exec");
    } catch (Exception e) {
      return "FAIL " + e.getMessage();
    }
  }

  private void writeFile(File f, String s) throws Exception {
    FileOutputStream os = new FileOutputStream(f);
    os.write(s.getBytes(StandardCharsets.UTF_8));
    os.close();
  }

  private String asset(String name) {
    try {
      InputStream in = getAssets().open(name);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      pump(in, bos);
      in.close();
      return bos.toString("UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  private void noteRun(String out) {
    if (out == null) out = "";
    if (out.contains("missing /dev/hidg0")) log3("HID", false);
    else if (out.contains("CONVERT_FAIL")) log3("CNV", false);
    else if (out.contains("kb_fail") || out.contains("RUN_FAIL")) log3("RUN", false);
    else if (out.contains("RUN_OK")) log3("RUN", true);
    else log3("MOD", false);
  }

  private void log3(final String code, final boolean ok) {
    final String line = code + (ok ? " OK" : " FAIL");
    new Thread(new Runnable() {
      @Override public void run() {
        su("mkdir -p /sdcard/Download"
            + " && echo '" + line + "' >> /sdcard/hdl.log"
            + " && echo '" + line + "' >> /sdcard/Download/hdl.log");
      }
    }).start();
  }

  private void killOldKeys() {
    new Thread(new Runnable() {
      @Override public void run() {
        su("pkill -f 'getevent -lt' >/dev/null 2>&1 || true");
      }
    }).start();
  }

  private String su(String cmd) {
    return su(cmd, null);
  }

  private String su(String cmd, String stdin) {
    try {
      Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
      OutputStream os = p.getOutputStream();
      if (stdin != null) {
        os.write(stdin.getBytes(StandardCharsets.UTF_8));
      }
      os.close();
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
