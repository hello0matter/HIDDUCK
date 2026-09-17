package com.local.hidtap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final String PREF = "hid";
  private static final String KEY_DUCK = "duck";
  private static final String KEY_DELAY = "delay";
  private EditText duck;
  private EditText delay;
  private Button run;
  private SharedPreferences prefs;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    prefs = getSharedPreferences(PREF, MODE_PRIVATE);
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
    delay.setGravity(Gravity.CENTER_VERTICAL);
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
  }

  @Override protected void onPause() {
    super.onPause();
    save();
  }

  private void save() {
    if (duck == null || delay == null || prefs == null) return;
    prefs.edit()
        .putString(KEY_DUCK, duck.getText().toString())
        .putString(KEY_DELAY, delay.getText().toString())
        .apply();
  }

  private void execute() {
    save();
    final String text = duck.getText().toString();
    if (text.trim().isEmpty()) return;
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
        su("echo " + b64
            + " | base64 -d > /data/local/tmp/hid-input.duck"
            + " && export HID_KEY_DELAY_MS=" + keyDelay
            + " && sh /data/adb/modules/hid-tap/hid-ctl.sh exec");
        runOnUiThread(new Runnable() {
          @Override public void run() { run.setEnabled(true); }
        });
      }
    }).start();
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
