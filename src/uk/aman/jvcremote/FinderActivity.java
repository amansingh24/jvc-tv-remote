package uk.aman.jvcremote;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Steps through every command code for the chosen set so buttons whose default code doesn't work
 * on this TV can be found and saved. Power and Menu are skipped so the TV stays on with the menu open.
 */
public class FinderActivity extends Activity implements Remote.Listener {

    private static final String[] LEARNABLE = {"Up", "Down", "Left", "Right", "OK", "Back", "Exit", "Home"};
    private static final int AUTO_DELAY_MS = 1500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> codes = new ArrayList<>();
    private Remote remote;
    private int position = -1;
    private boolean auto;
    private TextView codeLabel, status, learned;
    private Button autoButton;

    private final Runnable autoStep = new Runnable() {
        @Override public void run() {
            if (!auto) return;
            step(1);
            if (position < codes.size() - 1) handler.postDelayed(this, AUTO_DELAY_MS);
            else setAuto(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remote = Remote.get(this);

        IrCodes.CodeSet set = remote.set();
        Integer power = set.commands.get("Power"), menu = set.commands.get("Menu");
        for (int c = 0; c < set.commandRange(); c++) {
            if ((power != null && c == power) || (menu != null && c == menu)) continue;
            codes.add(c);
        }

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(Ui.text(this, "Find buttons", 22, 0xFFFFFFFF));
        TextView help = Ui.text(this,
                "1. Open the TV's menu with the real Menu button in the app.\n"
                + "2. Point the phone at the TV and tap Next (or Auto). Each tap tries a different code.\n"
                + "3. When the highlight on the TV moves, tap the direction it moved below. "
                + "That code is then used for that button.\n\n"
                + "Some codes may change the picture, input or channel. That's harmless: "
                + "reopen the menu and carry on. Power is never sent.", 14, Ui.MUTED);
        help.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        root.addView(help);

        codeLabel = Ui.text(this, "", 20, 0xFFFFFFFF);
        codeLabel.setGravity(Gravity.CENTER);
        codeLabel.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        root.addView(codeLabel);

        LinearLayout controls = Ui.row(this);
        controls.addView(action("◀ Prev", Ui.KEY, new Runnable() { public void run() { step(-1); } }), Ui.weighted(this, 56));
        controls.addView(action("Again", Ui.KEY, new Runnable() { public void run() { step(0); } }), Ui.weighted(this, 56));
        controls.addView(action("Next ▶", Ui.ACCENT, new Runnable() { public void run() { step(1); } }), Ui.weighted(this, 56));
        root.addView(controls);

        autoButton = action("", Ui.KEY, new Runnable() { public void run() { setAuto(!auto); } });
        root.addView(autoButton, Ui.fullWidth(this, 50));
        setAuto(false);

        TextView assign = Ui.text(this, "The highlight moved? Tap which way, or what it did:", 14, Ui.MUTED);
        assign.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        root.addView(assign);
        LinearLayout r1 = Ui.row(this), r2 = Ui.row(this);
        for (int i = 0; i < LEARNABLE.length; i++) {
            final String button = LEARNABLE[i];
            Button b = action(button, Ui.KEY, new Runnable() { public void run() { learn(button); } });
            (i < 4 ? r1 : r2).addView(b, Ui.weighted(this, 52));
        }
        root.addView(r1);
        root.addView(r2);

        learned = Ui.text(this, "", 14, 0xFFFFFFFF);
        learned.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        root.addView(learned);

        Button reset = action("Forget found buttons", Ui.KEY, new Runnable() {
            public void run() { remote.forgetLearned(); showLearned(); }
        });
        root.addView(reset, Ui.fullWidth(this, 46));

        status = Ui.text(this, "", 13, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.addView(root);
        setContentView(scroll);

        updateLabel();
        showLearned();
    }

    private Button action(String label, int color, final Runnable run) {
        Button b = Ui.key(this, label, color, 22);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { run.run(); }
        });
        return b;
    }

    private void step(int delta) {
        if (codes.isEmpty()) return;
        position = Math.max(0, Math.min(codes.size() - 1, position + delta));
        int code = codes.get(position);
        remote.sendCommand(code, "code " + hex(code), this);
        updateLabel();
    }

    private void setAuto(boolean on) {
        auto = on;
        handler.removeCallbacks(autoStep);
        autoButton.setText(on ? "Stop auto" : "Auto: try a code every 1.5 s");
        Ui.setColor(autoButton, on ? Ui.POWER : Ui.KEY, 22);
        if (on) handler.post(autoStep);
    }

    private void learn(String button) {
        if (position < 0) {
            status.setText("Tap Next first to send a code.");
            return;
        }
        setAuto(false);
        int code = codes.get(position);
        remote.learn(button, code);
        status.setText(button + " now uses code " + hex(code));
        showLearned();
    }

    private void updateLabel() {
        codeLabel.setText(position < 0
                ? "Ready: " + codes.size() + " codes to try"
                : "Code " + hex(codes.get(position)) + "   (" + (position + 1) + " of " + codes.size() + ")");
    }

    private void showLearned() {
        StringBuilder sb = new StringBuilder("Found so far: ");
        boolean any = false;
        for (String b : LEARNABLE) {
            if (remote.isLearned(b)) {
                sb.append(any ? ", " : "").append(b).append(" = ").append(hex(remote.command(b)));
                any = true;
            }
        }
        learned.setText(any ? sb.toString() : "Found so far: none");
    }

    private static String hex(int code) {
        return String.format("0x%02X", code);
    }

    @Override
    public void onSent(String message) {
        status.setText(message);
    }

    @Override
    protected void onPause() {
        setAuto(false);
        super.onPause();
    }
}
