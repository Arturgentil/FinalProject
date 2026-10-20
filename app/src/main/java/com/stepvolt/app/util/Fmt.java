package com.stepvolt.app.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formatação de números, tempos e datas, sempre com a vírgula do pt-BR. */
public final class Fmt {

    private static final DecimalFormatSymbols BR = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat GROUPED = new DecimalFormat("#,##0", BR);
    private static final DecimalFormat ONE = new DecimalFormat("#,##0.0", BR);
    private static final DecimalFormat TWO = new DecimalFormat("#,##0.00", BR);

    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat CLOCK_SEC = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private static final SimpleDateFormat WEEKDAY = new SimpleDateFormat("EEE", new Locale("pt", "BR"));

    private Fmt() {}

    public static String intGrouped(long value) { return GROUPED.format(value); }

    public static String millions(long value) {
        return String.format(new java.util.Locale("pt", "BR"), "%.1fM", value / 1_000_000f);
    }

    public static String thousands(double value) {
        return String.format(new java.util.Locale("pt", "BR"), "%.1fk", value / 1000f);
    }

    public static String energy(double mwh) { return ONE.format(mwh); }

    public static String decimals(double value, int places) {
        if (places <= 0) return GROUPED.format(Math.round(value));
        if (places == 1) return ONE.format(value);
        return TWO.format(value);
    }

    public static String clock(long millis) { return CLOCK.format(new Date(millis)); }
    public static String clockWithSeconds(long millis) { return CLOCK_SEC.format(new Date(millis)); }
    public static String date(long millis) { return DATE.format(new Date(millis)); }
    public static String dateShort(long millis) { return DATE_SHORT.format(new Date(millis)); }
    public static String weekday(long millis) { return WEEKDAY.format(new Date(millis)); }

    /** Duração legível: "8 min", "1 h 12 min". */
    public static String duration(long millis) {
        long totalMin = millis / 60000L;
        if (totalMin < 1) return Math.max(1, millis / 1000L) + " s";
        if (totalMin < 60) return totalMin + " min";
        long h = totalMin / 60;
        long m = totalMin % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** Cronômetro para a sessão em andamento: "12:04". */
    public static String stopwatch(long millis) {
        long totalSec = Math.max(0, millis / 1000L);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    /** "agora", "há 5 min", "há 2 h", senão a data. */
    public static String ago(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000L) return "agora";
        if (diff < 3_600_000L) return "há " + (diff / 60_000L) + " min";
        if (diff < 86_400_000L) return "há " + (diff / 3_600_000L) + " h";
        if (diff < 7 * 86_400_000L) return "há " + (diff / 86_400_000L) + " dias";
        return date(millis);
    }

    /** Qualidade do sinal a partir do RSSI, em palavras. */
    public static String signal(int rssi) {
        if (rssi == 0) return "—";
        if (rssi > -60) return "excelente";
        if (rssi > -72) return "bom";
        if (rssi > -85) return "fraco";
        return "muito fraco";
    }
}
