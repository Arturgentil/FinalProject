package com.stepvolt.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Resumo por dia. Existe porque as amostras brutas fora de sessão são apagadas
 * depois de 30 dias — sem este resumo a tela de Energia só enxergaria um mês.
 */
public class DailyDao {

    private final Repo repo;

    public DailyDao(Repo repo) { this.repo = repo; }

    public static String dayKey(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ts));
    }

    /** Soma passos e energia de um pacote no dia dele. */
    public void addUsage(final long ts, final long steps, final double energy) {
        if (steps <= 0 && energy <= 0) return;
        repo.exec(() -> {
            SQLiteDatabase db = repo.writable();
            String day = dayKey(ts);
            ensureRow(db, day);
            db.execSQL("UPDATE " + LocalDb.T_DAILY + " SET steps = steps + ?, energy = energy + ? WHERE day = ?",
                    new Object[]{steps, energy, day});
        });
    }

    /** Chamado pelo Repo ao fechar a sessão (já dentro da fila de escrita). */
    static void addSessionClosed(SQLiteDatabase db, long startedAt, long durationMs, double distanceM) {
        String day = dayKey(startedAt);
        ensureRow(db, day);
        db.execSQL("UPDATE " + LocalDb.T_DAILY + " SET distance_m = distance_m + ?,"
                        + " duration_ms = duration_ms + ?, sessions = sessions + 1 WHERE day = ?",
                new Object[]{distanceM, durationMs, day});
    }

    private static void ensureRow(SQLiteDatabase db, String day) {
        ContentValues cv = new ContentValues();
        cv.put("day", day);
        db.insertWithOnConflict(LocalDb.T_DAILY, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<DayStat> range(long fromTs, long toTs) {
        List<DayStat> out = new ArrayList<>();
        Cursor c = repo.readable().rawQuery(
                "SELECT day, steps, energy, distance_m, duration_ms, sessions FROM " + LocalDb.T_DAILY
                        + " WHERE day BETWEEN ? AND ? ORDER BY day ASC",
                new String[]{dayKey(fromTs), dayKey(toTs)});
        try {
            while (c.moveToNext()) {
                DayStat d = new DayStat();
                d.day = c.getString(0);
                d.steps = c.getLong(1);
                d.energy = c.getDouble(2);
                d.distanceM = c.getDouble(3);
                d.durationMs = c.getLong(4);
                d.sessions = c.getInt(5);
                out.add(d);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void rangeAsync(final long fromTs, final long toTs, Repo.Result<List<DayStat>> cb) {
        repo.call(() -> range(fromTs, toTs), cb);
    }

    /** Preenche os dias sem registro com zeros, para o gráfico não "pular" dias. */
    public static List<DayStat> fillGaps(List<DayStat> found, long fromTs, int days) {
        List<DayStat> out = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromTs);
        for (int i = 0; i < days; i++) {
            String key = dayKey(cal.getTimeInMillis());
            DayStat match = null;
            for (DayStat d : found) if (d.day.equals(key)) { match = d; break; }
            if (match == null) { match = new DayStat(); match.day = key; }
            out.add(match);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    /** Segunda-feira 00:00 da semana que contém ts. */
    public static long weekStart(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK); // domingo = 1
        int back = (dow + 5) % 7;              // segunda = 0
        c.add(Calendar.DAY_OF_YEAR, -back);
        return c.getTimeInMillis();
    }
}
