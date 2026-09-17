package com.local.hidtap;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final String DEFAULT_DUCK =
      "DELAY 500\nSTRING NH HID SAFE TEST\nENTER";
  private EditText duck;
  private EditText delay;
  private TextView status;
  private Button run;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#111111"));
    int p = (int) (16 * getResources().getDisplayMetrics().density);
    root.setPadding(p, p, p, p);

    TextView title = new TextView(this);
    title.setText("HID Duck");
    title.setTextColor(Color.WHITE);
    title.setTextSize(22);
    root.addView(title);

    TextView hint = new TextView(this);
    hint.setText("USB 插电脑，先点记事本，再点执行。DELAY 是命令间停顿；下面间隔是每个按键的间隔。");
    hint.setTextColor(Color.parseColor("#CCCCCC"));
    hint.setPadding(0, p / 2, 0, p / 2);
    root.addView(hint);

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    TextView delayLabel = new TextView(this);
    delayLabel.setText("按键间隔 ms ");
    delayLabel.setTextColor(Color.WHITE);
    delayLabel.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(delayLabel);
    delay = new EditText(this);
    delay.setText("30");
    delay.setInputType(InputType.TYPE_CLASS_NUMBER);
    delay.setTextColor(Color.WHITE);
    delay.setBackgroundColor(Color.parseColor("#1B1B1B"));
    delay.setPadding(p / 2, p / 3, p / 2, p / 3);
    delay.setMinWidth(p * 4);
    row.addView(delay);
    root.addView(row);

    duck = new EditText(this);
    duck.setHint("DELAY 500\nSTRING NH HID SAFE TEST\nENTER");
    duck.setHintTextColor(Color.GRAY);
    duck.setText(DEFAULT_DUCK);
    duck.setTextColor(Color.WHITE);
    duck.setBackgroundColor(Color.parseColor("#1B1B1B"));
    duck.setMinLines(8);
    duck.setGravity(Gravity.TOP);
    duck.setPadding(p / 2, p / 2, p / 2, p / 2);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    duck.setLayoutParams(lp);
    root.addView(duck);

    run = new Button(this);
    run.setText("执行");
    run.setTextSize(20);
    run.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { execute(); }
    });
    root.addView(run);

    status = new TextView(this);
    status.setText("ready");
    status.setTextColor(Color.parseColor("#A5D6A7"));
    status.setPadding(0, p, 0, 0);
    root.addView(status);
    setContentView(root);
  }

  private void execute() {
    final String text = duck.getText().toString();
    if (text.trim().isEmpty()) {
      status.setText("empty");
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
    status.setText("running...");
    new Thread(new Runnable() {
      @Override public void run() {
        String b64 = Base64.encodeToString(
            text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        final String out = su(
            "echo " + b64
            + " | base64 -d > /data/local/tmp/hid-input.duck"
            + " && export HID_KEY_DELAY_MS=" + keyDelay
            + " && sh /data/adb/modules/hid-tap/hid-ctl.sh exec");
        runOnUiThread(new Runnable() {
          @Override public void run() {
            status.setText(out);
            run.setEnabled(true);
          }
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
