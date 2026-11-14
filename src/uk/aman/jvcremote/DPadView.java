package uk.aman.jvcremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** A round D-pad: four arrow segments around a central OK button. */
final class DPadView extends View {

    interface Listener {
        void onPress(String button);
        void onRelease();
    }

    private static final String[] DIRECTIONS = {"Right", "Down", "Left", "Up"}; // clockwise from 0 degrees

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF outer = new RectF();
    private final RectF inner = new RectF();
    private final int keyColor, pressedColor, okColor, bgColor;
    private Listener listener;
    private String pressed;

    DPadView(Context context, int keyColor, int pressedColor, int okColor, int bgColor) {
        super(context);
        this.keyColor = keyColor;
        this.pressedColor = pressedColor;
        this.okColor = okColor;
        this.bgColor = bgColor;
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.96f, okR = r * 0.38f;
        outer.set(cx - r, cy - r, cx + r, cy + r);
        inner.set(cx - okR, cy - okR, cx + okR, cy + okR);

        for (int i = 0; i < 4; i++) {
            float start = i * 90 - 45;
            path.reset();
            path.arcTo(outer, start, 90);
            path.arcTo(inner, start + 90, -90);
            path.close();
            fill.setColor(DIRECTIONS[i].equals(pressed) ? pressedColor : keyColor);
            canvas.drawPath(path, fill);

            // Arrow head pointing outwards, in the middle of the segment.
            double a = Math.toRadians(i * 90);
            float mid = (r + okR) / 2, s = r * 0.09f;
            float ax = cx + (float) Math.cos(a) * mid, ay = cy + (float) Math.sin(a) * mid;
            float px = (float) -Math.sin(a), py = (float) Math.cos(a);
            path.reset();
            path.moveTo(ax + (float) Math.cos(a) * s, ay + (float) Math.sin(a) * s);
            path.lineTo(ax - (float) Math.cos(a) * s * 0.6f + px * s, ay - (float) Math.sin(a) * s * 0.6f + py * s);
            path.lineTo(ax - (float) Math.cos(a) * s * 0.6f - px * s, ay - (float) Math.sin(a) * s * 0.6f - py * s);
            path.close();
            fill.setColor(0xFFFFFFFF);
            canvas.drawPath(path, fill);
        }

        // Gaps between segments.
        fill.setColor(bgColor);
        fill.setStrokeWidth(r * 0.025f);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            canvas.drawLine(cx + (float) Math.cos(a) * okR, cy + (float) Math.sin(a) * okR,
                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, fill);
        }
        canvas.drawCircle(cx, cy, okR + r * 0.03f, fill);

        fill.setColor("OK".equals(pressed) ? pressedColor : okColor);
        canvas.drawCircle(cx, cy, okR, fill);
        text.setTextSize(okR * 0.5f);
        canvas.drawText("OK", cx, cy + okR * 0.18f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                String button = hit(e.getX(), e.getY());
                if (button == null) return false;
                pressed = button;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                if (listener != null) listener.onPress(button);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                invalidate();
                if (listener != null) listener.onRelease();
                return true;
            default:
                return true;
        }
    }

    private String hit(float x, float y) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.96f;
        double dx = x - cx, dy = y - cy, d = Math.hypot(dx, dy);
        if (d > r) return null;
        if (d < r * 0.38f) return "OK";
        double deg = (Math.toDegrees(Math.atan2(dy, dx)) + 360 + 45) % 360;
        return DIRECTIONS[(int) (deg / 90)];
    }
}
