package uk.aman.jvcremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements Remote.Listener {

    /** Buttons that repeat while held. */
    private static final List<String> REPEATING = Arrays.asList(
            "Up", "Down", "Left", "Right", "Vol+", "Vol-", "Ch+", "Ch-");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> keys = new ArrayList<>();
    private Remote remote;
    private TextView status;
    private Runnable repeat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remote = Remote.get(this);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);

        // Title bar with the settings button.
        LinearLayout top = Ui.row(this);
        TextView title = Ui.text(this, "JVC Remote", 22, 0xFFFFFFFF);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button settings = Ui.key(this, "Settings", Ui.KEY, 20);
        settings.setTextSize(14);
        settings.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        top.addView(settings, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        root.addView(top);

        LinearLayout first = Ui.row(this);
        first.setPadding(0, Ui.dp(this, 10), 0, 0);
        first.addView(remoteKey("Power", "Power", Ui.POWER), Ui.weighted(this, 52));
        first.addView(remoteKey("Source", "Source", Ui.KEY), Ui.weighted(this, 52));
        Button numbers = Ui.key(this, "123", Ui.KEY, 26);
        numbers.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showNumbers(); }
        });
        first.addView(numbers, Ui.weighted(this, 52));
        root.addView(first);

        DPadView dpad = new DPadView(this, Ui.KEY, Ui.PRESSED, Ui.ACCENT, Ui.BG);
        dpad.setListener(new DPadView.Listener() {
            @Override public void onPress(String button) { press(button); }
            @Override public void onRelease() { release(); }
        });
        LinearLayout.LayoutParams dpadParams = new LinearLayout.LayoutParams(Ui.dp(this, 270), Ui.dp(this, 270));
        dpadParams.gravity = Gravity.CENTER_HORIZONTAL;
        dpadParams.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        root.addView(dpad, dpadParams);

        LinearLayout nav = Ui.row(this);
        for (String b : new String[]{"Back", "Home", "Menu", "Exit"}) {
            nav.addView(remoteKey(b, b, Ui.KEY), Ui.weighted(this, 50));
        }
        root.addView(nav);

        // Volume and channel rockers, mute between them.
        LinearLayout rockers = Ui.row(this);
        rockers.setPadding(0, Ui.dp(this, 8), 0, 0);
        rockers.addView(rocker("Vol+", "Vol-", "VOL"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout middle = Ui.column(this);
        middle.setGravity(Gravity.CENTER);
        middle.addView(remoteKey("Mute", "Mute", Ui.KEY), new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 50)));
        rockers.addView(middle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        rockers.addView(rocker("Ch+", "Ch-", "CH"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(rockers);

        status = Ui.text(this, "", 13, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 10), 0, 0);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Greys out buttons the chosen code set doesn't have, and shows which set is in use. */
    private void refresh() {
        for (Button k : keys) {
            boolean available = remote.command((String) k.getTag()) != null;
            k.setEnabled(available);
            k.setAlpha(available ? 1f : 0.3f);
        }
        status.setText(remote.hasIr()
                ? "Set " + (remote.setIndex() + 1) + ": " + remote.set().name
                : "No IR blaster found on this phone.");
    }

    private View rocker(String plus, String minus, String label) {
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(remoteKey(plus, "+", Ui.KEY), new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 56)));
        TextView t = Ui.text(this, label, 13, Ui.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        col.addView(t);
        col.addView(remoteKey(minus, "−", Ui.KEY), new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 56)));
        return col;
    }

    /** A button that sends on touch-down (feels instant) and repeats while held where that makes sense. */
    private Button remoteKey(final String button, String label, int color) {
        Button b = Ui.key(this, label, color, 26);
        b.setTag(button);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (!v.isEnabled()) return true;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        press(button);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        release();
                        return true;
                    default:
                        return true;
                }
            }
        });
        keys.add(b);
        return b;
    }

    private void press(final String button) {
        release();
        remote.send(button, this);
        if (REPEATING.contains(button)) {
            repeat = new Runnable() {
                @Override public void run() {
                    remote.send(button, MainActivity.this);
                    handler.postDelayed(this, 180);
                }
            };
            handler.postDelayed(repeat, 450);
        }
    }

    private void release() {
        if (repeat != null) handler.removeCallbacks(repeat);
        repeat = null;
    }

    @Override
    public void onSent(String message) {
        status.setText(message);
    }

    private void showNumbers() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        int pad = Ui.dp(this, 12);
        grid.setPadding(pad, pad, pad, pad);
        grid.setBackgroundColor(Ui.BG);
        for (String n : new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", ""}) {
            View cell;
            if (n.isEmpty()) {
                cell = new View(this);
            } else {
                final String digit = n;
                Button b = Ui.key(this, n, Ui.KEY, 26);
                b.setTextSize(22);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        remote.send(digit, MainActivity.this);
                    }
                });
                cell = b;
            }
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = Ui.dp(this, 80);
            p.height = Ui.dp(this, 64);
            p.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
            grid.addView(cell, p);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Numbers")
                .setView(grid)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showSettings() {
        String[] items = {
                "Code set: " + (remote.setIndex() + 1) + " (" + remote.set().name + ")",
                "Find buttons that don't work",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Settings")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) chooseSet();
                        else startActivity(new Intent(MainActivity.this, FinderActivity.class));
                    }
                })
                .show();
    }

    private void chooseSet() {
        String[] names = new String[IrCodes.SETS.length];
        for (int i = 0; i < names.length; i++) names[i] = "Set " + (i + 1) + ": " + IrCodes.SETS[i].name;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Code set")
                .setSingleChoiceItems(names, remote.setIndex(), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        remote.setSetIndex(which);
                        refresh();
                        d.dismiss();
                    }
                })
                .show();
    }

    @Override
    protected void onPause() {
        release();
        super.onPause();
    }
}
