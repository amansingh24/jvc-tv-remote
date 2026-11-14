package uk.aman.jvcremote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours and small view builders shared by the screens. */
final class Ui {
    static final int BG = Color.rgb(18, 18, 20);
    static final int KEY = Color.rgb(46, 46, 52);
    static final int PRESSED = Color.rgb(90, 90, 100);
    static final int ACCENT = Color.rgb(60, 110, 230);
    static final int POWER = Color.rgb(190, 45, 45);
    static final int MUTED = Color.rgb(150, 150, 160);

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Button key(Context c, String label, int color, float radiusDp) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setStateListAnimator(null);
        setColor(b, color, radiusDp);
        return b;
    }

    static void setColor(Button b, int color, float radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(b.getContext(), PRESSED, radiusDp));
        states.addState(new int[]{}, shape(b.getContext(), color, radiusDp));
        b.setBackground(states);
    }

    private static GradientDrawable shape(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    static LinearLayout column(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    static LinearLayout.LayoutParams weighted(Context c, float heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, heightDp), 1f);
        p.setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
        return p;
    }

    static LinearLayout.LayoutParams fullWidth(Context c, float heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, heightDp));
        p.setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
        return p;
    }

    private Ui() {}
}
