package com.stepvolt.app.ble;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Uma linha do registro de conexão mostrado na aba do tênis. */
public class BleEvent {

    public static final int INFO = 0;
    public static final int WARN = 1;
    public static final int ERROR = 2;
    public static final int DATA = 3;

    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public final long time;
    public final int level;
    public final String message;

    public BleEvent(int level, String message) {
        this.time = System.currentTimeMillis();
        this.level = level;
        this.message = message;
    }

    public String clock() {
        return CLOCK.format(new Date(time));
    }

    public int color() {
        switch (level) {
            case ERROR: return 0xFFFF3D71;
            case WARN: return 0xFFFFD166;
            case DATA: return 0xFF4CC9F0;
            default: return 0xFF9A9AB8;
        }
    }
}
