package uk.aman.jvcremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(18, 18, 20);
    private static final int KEY = Color.rgb(44, 44, 50);
    private static final int ACCENT = Color.rgb(60, 110, 230);
    private static final int POWER = Color.rgb(190, 45, 45);

    /** Buttons that repeat while held. */
    private static final List<String> REPEATING = java.util.Arrays.asList(
            "Up", "Down", "Left", "Right", "Vol+", "Vol-", "Ch+", "Ch-");

    private final ExecutorService irThread = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> setButtons = new ArrayList<>();
    private final List<Button> keys = new ArrayList<>();

    private ConsumerIrManager ir;
    private SharedPreferences prefs;
    private TextView setName;
    private TextView status;
    private int setIndex;
    private boolean toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ir = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        prefs = getPreferences(MODE_PRIVATE);
        setIndex = Math.min(prefs.getInt("set", 0), IrCodes.SETS.length - 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView hint = text("Point the top of the phone at the TV and press Menu. "
                + "If nothing happens, try the next code set.", 14, Color.LTGRAY);
        root.addView(hint);

        LinearLayout sets = row();
        for (int i = 0; i < IrCodes.SETS.length; i++) {
            final int index = i;
            Button b = key("Set " + (i + 1), KEY);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectSet(index); }
            });
            setButtons.add(b);
            sets.addView(b, weight());
        }
        root.addView(sets);

        setName = text("", 15, Color.WHITE);
        setName.setGravity(Gravity.CENTER);
        root.addView(setName);

        root.addView(keyRow(new String[]{"Power", "Source", "Home"}));
        root.addView(keyRow(new String[]{"Menu", "Back", "Exit"}));
        root.addView(keyRow(new String[]{null, "Up", null}));
        root.addView(keyRow(new String[]{"Left", "OK", "Right"}));
        root.addView(keyRow(new String[]{null, "Down", null}));
        root.addView(keyRow(new String[]{"Vol+", "Mute", "Ch+"}));
        root.addView(keyRow(new String[]{"Vol-", null, "Ch-"}));
        root.addView(keyRow(new String[]{"1", "2", "3"}));
        root.addView(keyRow(new String[]{"4", "5", "6"}));
        root.addView(keyRow(new String[]{"7", "8", "9"}));
        root.addView(keyRow(new String[]{null, "0", null}));

        status = text("", 13, Color.GRAY);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        if (ir == null || !ir.hasIrEmitter()) {
            status.setText("No IR blaster found on this phone.");
            status.setTextColor(POWER);
        }
        selectSet(setIndex);
    }

    private void selectSet(int index) {
        setIndex = index;
        prefs.edit().putInt("set", index).apply();
        IrCodes.CodeSet set = IrCodes.SETS[index];
        setName.setText(set.name);
        for (int i = 0; i < setButtons.size(); i++) {
            tint(setButtons.get(i), i == index ? ACCENT : KEY);
        }
        for (Button k : keys) {
            boolean available = set.commands.containsKey((String) k.getTag());
            k.setEnabled(available);
            k.setAlpha(available ? 1f : 0.3f);
        }
    }

    private void send(String button) {
        if (ir == null || !ir.hasIrEmitter()) return;
        final IrCodes.CodeSet set = IrCodes.SETS[setIndex];
        toggle = !toggle; // RC5 TVs only accept a new press when the toggle bit flips
        final int[] pattern = set.pattern(button, toggle);
        if (pattern == null) return;
        final String sent = "Sent " + button + " (set " + (setIndex + 1) + ")";
        irThread.execute(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    ir.transmit(set.frequency(), pattern);
                    message = sent;
                } catch (RuntimeException e) {
                    message = "IR error: " + e.getMessage();
                }
                final String text = message;
                handler.post(new Runnable() {
                    @Override public void run() { status.setText(text); }
                });
            }
        });
    }

    private LinearLayout keyRow(String[] labels) {
        LinearLayout r = row();
        for (String label : labels) {
            if (label == null) {
                r.addView(new View(this), weight());
                continue;
            }
            Button b = key(label, label.equals("Power") ? POWER : KEY);
            b.setTag(label);
            attachPress(b, label);
            keys.add(b);
            r.addView(b, weight());
        }
        return r;
    }

    /** Sends on touch-down (feels instant) and repeats while held for arrows, volume and channel. */
    private void attachPress(Button b, final String label) {
        final boolean repeats = REPEATING.contains(label);
        final Runnable repeat = new Runnable() {
            @Override public void run() {
                send(label);
                handler.postDelayed(this, 180);
            }
        };
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (!v.isEnabled()) return true;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        send(label);
                        if (repeats) handler.postDelayed(repeat, 450);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        handler.removeCallbacks(repeat);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private Button key(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(56));
        tint(b, color);
        return b;
    }

    private void tint(Button b, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        irThread.shutdown();
        super.onDestroy();
    }
}
