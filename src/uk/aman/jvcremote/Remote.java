package uk.aman.jvcremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.ConsumerIrManager;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared IR state: the chosen code set, learned buttons, and a background thread for transmitting. */
final class Remote {

    interface Listener {
        void onSent(String message);
    }

    private static Remote instance;

    static synchronized Remote get(Context context) {
        if (instance == null) instance = new Remote(context.getApplicationContext());
        return instance;
    }

    private final ConsumerIrManager ir;
    private final SharedPreferences prefs;
    private final ExecutorService irThread = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean toggle;

    private Remote(Context context) {
        ir = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = context.getSharedPreferences("remote", Context.MODE_PRIVATE);
    }

    boolean hasIr() {
        return ir != null && ir.hasIrEmitter();
    }

    int setIndex() {
        return Math.min(prefs.getInt("set", 0), IrCodes.SETS.length - 1);
    }

    void setSetIndex(int index) {
        prefs.edit().putInt("set", index).apply();
    }

    IrCodes.CodeSet set() {
        return IrCodes.SETS[setIndex()];
    }

    /** The code for a button: one learned with the button finder, else the set's default. */
    Integer command(String button) {
        int learned = prefs.getInt(learnedKey(button), -1);
        return learned >= 0 ? Integer.valueOf(learned) : set().commands.get(button);
    }

    boolean isLearned(String button) {
        return prefs.getInt(learnedKey(button), -1) >= 0;
    }

    void learn(String button, int command) {
        prefs.edit().putInt(learnedKey(button), command).apply();
    }

    void forgetLearned() {
        SharedPreferences.Editor e = prefs.edit();
        String prefix = "learned." + setIndex() + ".";
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) e.remove(key);
        }
        e.apply();
    }

    private String learnedKey(String button) {
        return "learned." + setIndex() + "." + button;
    }

    void send(String button, Listener listener) {
        Integer command = command(button);
        if (command != null) sendCommand(command, button, listener);
    }

    void sendCommand(int command, final String label, final Listener listener) {
        if (!hasIr()) return;
        final IrCodes.CodeSet set = set();
        toggle = !toggle; // RC5 TVs only accept a new press when the toggle bit flips
        final int[] pattern = set.pattern(command, toggle);
        irThread.execute(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    ir.transmit(set.frequency(), pattern);
                    message = "Sent " + label;
                } catch (RuntimeException e) {
                    message = "IR error: " + e.getMessage();
                }
                final String text = message;
                if (listener != null) {
                    main.post(new Runnable() {
                        @Override public void run() { listener.onSent(text); }
                    });
                }
            }
        });
    }
}
