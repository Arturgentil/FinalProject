package com.stepvolt.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import com.stepvolt.app.event.AppEvent;
import com.stepvolt.app.event.EventCategory;
import com.stepvolt.app.event.EventSeverity;
import com.stepvolt.app.event.EventStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * Acesso ao banco local. Escritas vão para uma fila de fundo (os pacotes do
 * tênis chegam mais ou menos uma vez por segundo e não podem travar a tela);
 * leituras têm versão síncrona, para quem já está fora da thread principal, e
 * versão com callback, para as telas.
 */
public class Repo {

    public interface Result<T> {
        void onReady(T data);
    }

    private static Repo instance;

    public static synchronized Repo get(Context context) {
        if (instance == null) instance = new Repo(context.getApplicationContext());
        return instance;
    }

    private final LocalDb helper;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Repo(Context context) {
        this.helper = LocalDb.get(context);
        this.route = new RouteDao(this);
        this.daily = new DailyDao(this);
        this.shoes = new ShoeDao(this);
        this.contacts = new ContactDao(this);
    }

    public final RouteDao route;
    public final DailyDao daily;
    public final ShoeDao shoes;
    public final ContactDao contacts;

    // Acesso para os DAOs da v3 (rota, dias, tênis, contatos). Todos passam pela
    // mesma fila de escrita, então a ordem entre gravações é sempre respeitada
    // (ex.: os pontos de GPS entram antes de a sessão ser fechada).
    public void exec(Runnable r) { io.execute(r); }
    public SQLiteDatabase writable() { return helper.getWritableDatabase(); }
    public SQLiteDatabase readable() { return helper.getReadableDatabase(); }
    public <T> void call(java.util.concurrent.Callable<T> work, Result<T> cb) { async(work, cb); }

    private <T> void async(final java.util.concurrent.Callable<T> work, final Result<T> cb) {
        io.execute(() -> {
            T out = null;
            try {
                out = work.call();
            } catch (Exception ignored) {
            }
            final T result = out;
            if (cb != null) main.post(() -> cb.onReady(result));
        });
    }

    // ------------------------------------------------------------------
    // Amostras
    // ------------------------------------------------------------------

    /** Grava todas as grandezas de um pacote. sessionId 0 = fora de sessão. */
    public void writeSample(final long ts, final Map<String, Double> values, final long sessionId) {
        if (values == null || values.isEmpty()) return;
        final Map<String, Double> copy = new java.util.LinkedHashMap<>(values);
        io.execute(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                for (Map.Entry<String, Double> e : copy.entrySet()) {
                    if (e.getValue() == null) continue;
                    ContentValues cv = new ContentValues();
                    cv.put("ts", ts);
                    cv.put("metric", e.getKey());
                    cv.put("value", e.getValue());
                    cv.put("session_id", sessionId);
                    db.insert(LocalDb.T_SAMPLES, null, cv);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    /** Série bruta de uma grandeza num intervalo. */
    public List<Point> series(String metric, long fromTs, long toTs, int limit) {
        List<Point> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT ts, value FROM " + LocalDb.T_SAMPLES
                        + " WHERE metric = ? AND ts BETWEEN ? AND ? ORDER BY ts ASC LIMIT ?",
                new String[]{metric, String.valueOf(fromTs), String.valueOf(toTs), String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(new Point(c.getLong(0), c.getDouble(1)));
        } finally {
            c.close();
        }
        return out;
    }

    public void seriesAsync(final String metric, final long fromTs, final long toTs,
                            final int limit, Result<List<Point>> cb) {
        async(() -> series(metric, fromTs, toTs, limit), cb);
    }

    /**
     * Série agregada em baldes de tempo (média por balde). É o que os gráficos
     * de período usam: 24h em baldes de 1h, 7 dias em baldes de 1 dia, etc.
     */
    public List<Point> buckets(String metric, long fromTs, long toTs, long bucketMs, boolean sum) {
        List<Point> out = new ArrayList<>();
        if (bucketMs <= 0) return out;
        SQLiteDatabase db = helper.getReadableDatabase();
        String agg = sum ? "SUM(value)" : "AVG(value)";
        Cursor c = db.rawQuery(
                "SELECT (ts / " + bucketMs + ") * " + bucketMs + " AS bucket, " + agg
                        + " FROM " + LocalDb.T_SAMPLES
                        + " WHERE metric = ? AND ts BETWEEN ? AND ?"
                        + " GROUP BY bucket ORDER BY bucket ASC",
                new String[]{metric, String.valueOf(fromTs), String.valueOf(toTs)});
        try {
            while (c.moveToNext()) out.add(new Point(c.getLong(0), c.getDouble(1)));
        } finally {
            c.close();
        }
        return out;
    }

    public void bucketsAsync(final String metric, final long fromTs, final long toTs,
                             final long bucketMs, final boolean sum, Result<List<Point>> cb) {
        async(() -> buckets(metric, fromTs, toTs, bucketMs, sum), cb);
    }

    /** Soma de uma grandeza cumulativa (passos, energia) desde um instante. */
    public double total(String metric, long fromTs, long toTs) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(value), 0) FROM " + LocalDb.T_SAMPLES
                        + " WHERE metric = ? AND ts BETWEEN ? AND ?",
                new String[]{metric, String.valueOf(fromTs), String.valueOf(toTs)});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    public void totalAsync(final String metric, final long fromTs, final long toTs, Result<Double> cb) {
        async(() -> total(metric, fromTs, toTs), cb);
    }

    /** Quais grandezas realmente já chegaram do tênis (para montar a aba Sensores). */
    public List<String> knownMetrics(long sinceTs) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT metric, COUNT(*) FROM " + LocalDb.T_SAMPLES
                        + " WHERE ts >= ? GROUP BY metric ORDER BY COUNT(*) DESC",
                new String[]{String.valueOf(sinceTs)});
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            c.close();
        }
        return out;
    }

    public void knownMetricsAsync(final long sinceTs, Result<List<String>> cb) {
        async(() -> knownMetrics(sinceTs), cb);
    }

    // ------------------------------------------------------------------
    // Sessões
    // ------------------------------------------------------------------

    /** Abre uma sessão e devolve o id local na hora (usado pelo serviço). */
    public long openSession(String deviceName) {
        return openSession(deviceName, null, "walk");
    }

    public long openSession(String deviceName, String shoeAddress, String kind) {
        ContentValues cv = new ContentValues();
        cv.put("started_at", System.currentTimeMillis());
        cv.put("device_name", deviceName);
        cv.put("shoe_address", shoeAddress);
        cv.put("kind", kind == null ? "walk" : kind);
        return helper.getWritableDatabase().insert(LocalDb.T_SESSIONS, null, cv);
    }

    public void closeSession(final long sessionId) {
        io.execute(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            Cursor c = db.rawQuery(
                    "SELECT"
                            + " COALESCE(SUM(CASE WHEN metric='steps' THEN value END),0),"
                            + " COALESCE(SUM(CASE WHEN metric='energy' THEN value END),0),"
                            + " COALESCE(AVG(CASE WHEN metric='bpm' THEN value END),0),"
                            + " COALESCE(MIN(CASE WHEN metric='bpm' THEN value END),0),"
                            + " COALESCE(MAX(CASE WHEN metric='bpm' THEN value END),0),"
                            + " COALESCE(AVG(CASE WHEN metric='spo2' THEN value END),0),"
                            + " COALESCE(MIN(CASE WHEN metric='spo2' THEN value END),0),"
                            + " COUNT(*)"
                            + " FROM " + LocalDb.T_SAMPLES + " WHERE session_id = ?",
                    new String[]{String.valueOf(sessionId)});
            ContentValues cv = new ContentValues();
            cv.put("ended_at", System.currentTimeMillis());
            try {
                if (c.moveToFirst()) {
                    cv.put("steps", Math.round(c.getDouble(0)));
                    cv.put("energy", c.getDouble(1));
                    cv.put("bpm_avg", c.getDouble(2));
                    cv.put("bpm_min", c.getDouble(3));
                    cv.put("bpm_max", c.getDouble(4));
                    cv.put("spo2_avg", c.getDouble(5));
                    cv.put("spo2_min", c.getDouble(6));
                    cv.put("sample_count", c.getInt(7));
                }
            } finally {
                c.close();
            }
            // Distância e subida vêm da rota gravada pelo GPS (se houve).
            com.stepvolt.app.gps.GeoMath.Track track = com.stepvolt.app.gps.GeoMath.analyze(
                    RouteDao.pointsSync(db, sessionId));
            cv.put("distance_m", track.distanceM);
            cv.put("elev_gain_m", track.elevationGainM);
            db.update(LocalDb.T_SESSIONS, cv, "id = ?", new String[]{String.valueOf(sessionId)});

            // Resumo do dia e desgaste do tênis.
            Cursor sc = db.rawQuery("SELECT started_at, COALESCE(ended_at, started_at), shoe_address"
                    + " FROM " + LocalDb.T_SESSIONS + " WHERE id = ?", new String[]{String.valueOf(sessionId)});
            try {
                if (sc.moveToFirst()) {
                    long started = sc.getLong(0);
                    long ended = sc.getLong(1);
                    String shoe = sc.getString(2);
                    DailyDao.addSessionClosed(db, started, Math.max(0, ended - started), track.distanceM);
                    if (shoe != null) ShoeDao.addDistance(db, shoe, track.distanceM);
                }
            } finally {
                sc.close();
            }
        });
    }

    private SessionRecord readSession(Cursor c) {
        SessionRecord s = new SessionRecord();
        s.id = c.getLong(0);
        s.startedAt = c.getLong(1);
        s.endedAt = c.getLong(2);
        s.steps = c.getLong(3);
        s.energy = c.getDouble(4);
        s.bpmAvg = c.getDouble(5);
        s.bpmMin = c.getDouble(6);
        s.bpmMax = c.getDouble(7);
        s.spo2Avg = c.getDouble(8);
        s.spo2Min = c.getDouble(9);
        s.sampleCount = c.getInt(10);
        s.deviceName = c.getString(11);
        s.synced = c.getInt(12) == 1;
        s.remoteId = c.getString(13);
        s.distanceM = c.getDouble(14);
        s.elevGainM = c.getDouble(15);
        s.kind = c.getString(16);
        s.shoeAddress = c.getString(17);
        return s;
    }

    private static final String SESSION_COLS =
            "id, started_at, COALESCE(ended_at,0), steps, energy, bpm_avg, bpm_min, bpm_max,"
                    + " spo2_avg, spo2_min, sample_count, device_name, synced, remote_id,"
                    + " COALESCE(distance_m,0), COALESCE(elev_gain_m,0), COALESCE(kind,'walk'), shoe_address";

    public List<SessionRecord> sessions(int limit) {
        List<SessionRecord> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + SESSION_COLS + " FROM " + LocalDb.T_SESSIONS
                        + " WHERE ended_at IS NOT NULL ORDER BY started_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(readSession(c));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Restauração de backup: insere sessões que ainda não existem (mesmo
     * horário de início) sem apagar nada. Devolve quantas entraram.
     */
    public int importSessions(List<SessionRecord> list) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int added = 0;
        for (SessionRecord s : list) {
            Cursor c = db.rawQuery("SELECT 1 FROM " + LocalDb.T_SESSIONS + " WHERE started_at = ?",
                    new String[]{String.valueOf(s.startedAt)});
            boolean exists;
            try { exists = c.moveToFirst(); } finally { c.close(); }
            if (exists) continue;
            ContentValues cv = new ContentValues();
            cv.put("started_at", s.startedAt);
            cv.put("ended_at", s.endedAt);
            cv.put("steps", s.steps);
            cv.put("energy", s.energy);
            cv.put("bpm_avg", s.bpmAvg); cv.put("bpm_min", s.bpmMin); cv.put("bpm_max", s.bpmMax);
            cv.put("spo2_avg", s.spo2Avg); cv.put("spo2_min", s.spo2Min);
            cv.put("sample_count", s.sampleCount);
            cv.put("device_name", s.deviceName);
            cv.put("synced", s.remoteId != null ? 1 : 0);
            cv.put("remote_id", s.remoteId);
            cv.put("distance_m", s.distanceM);
            cv.put("elev_gain_m", s.elevGainM);
            cv.put("kind", s.kind == null ? "walk" : s.kind);
            cv.put("shoe_address", s.shoeAddress);
            if (db.insert(LocalDb.T_SESSIONS, null, cv) != -1) added++;
        }
        return added;
    }

    public SessionRecord session(long id) {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + SESSION_COLS + " FROM " + LocalDb.T_SESSIONS + " WHERE id = ?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readSession(c) : null;
        } finally {
            c.close();
        }
    }

    public void sessionAsync(final long id, Result<SessionRecord> cb) {
        async(() -> session(id), cb);
    }

    public void sessionsAsync(final int limit, Result<List<SessionRecord>> cb) {
        async(() -> sessions(limit), cb);
    }

    public List<SessionRecord> unsyncedSessions() {
        List<SessionRecord> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + SESSION_COLS + " FROM " + LocalDb.T_SESSIONS
                        + " WHERE synced = 0 AND ended_at IS NOT NULL ORDER BY started_at ASC LIMIT 50", null);
        try {
            while (c.moveToNext()) out.add(readSession(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void unsyncedSessionsAsync(Result<List<SessionRecord>> cb) {
        async(this::unsyncedSessions, cb);
    }

    public void markSynced(final long sessionId, final String remoteId) {
        io.execute(() -> {
            ContentValues cv = new ContentValues();
            cv.put("synced", 1);
            cv.put("remote_id", remoteId);
            helper.getWritableDatabase().update(LocalDb.T_SESSIONS, cv, "id = ?",
                    new String[]{String.valueOf(sessionId)});
        });
    }

    /** Amostras de uma grandeza dentro de uma sessão (gráficos do detalhe). */
    public List<Point> sessionSeries(long sessionId, String metric) {
        List<Point> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT ts, value FROM " + LocalDb.T_SAMPLES
                        + " WHERE session_id = ? AND metric = ? ORDER BY ts ASC",
                new String[]{String.valueOf(sessionId), metric});
        try {
            while (c.moveToNext()) out.add(new Point(c.getLong(0), c.getDouble(1)));
        } finally {
            c.close();
        }
        return out;
    }

    public void sessionSeriesAsync(final long sessionId, final String metric, Result<List<Point>> cb) {
        async(() -> sessionSeries(sessionId, metric), cb);
    }

    // ------------------------------------------------------------------
    // Avisos
    // ------------------------------------------------------------------

    public void insertAlert(final AlertRecord a) {
        io.execute(() -> {
            ContentValues cv = new ContentValues();
            cv.put("ts", a.ts);
            cv.put("metric", a.metric);
            cv.put("value", a.value);
            cv.put("threshold", a.threshold);
            cv.put("severity", a.severity);
            cv.put("message", a.message);
            cv.put("seen", a.seen ? 1 : 0);
            helper.getWritableDatabase().insert(LocalDb.T_ALERTS, null, cv);
        });
    }

    public List<AlertRecord> alerts(int limit) {
        List<AlertRecord> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT id, ts, metric, value, threshold, severity, message, seen FROM "
                        + LocalDb.T_ALERTS + " ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                AlertRecord a = new AlertRecord();
                a.id = c.getLong(0);
                a.ts = c.getLong(1);
                a.metric = c.getString(2);
                a.value = c.getDouble(3);
                a.threshold = c.getDouble(4);
                a.severity = c.getInt(5);
                a.message = c.getString(6);
                a.seen = c.getInt(7) == 1;
                out.add(a);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void alertsAsync(final int limit, Result<List<AlertRecord>> cb) {
        async(() -> alerts(limit), cb);
    }

    public int unseenAlertCount() {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + LocalDb.T_ALERTS + " WHERE seen = 0", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public void unseenAlertCountAsync(Result<Integer> cb) {
        async(this::unseenAlertCount, cb);
    }

    public void markAlertsSeen() {
        io.execute(() -> {
            ContentValues cv = new ContentValues();
            cv.put("seen", 1);
            helper.getWritableDatabase().update(LocalDb.T_ALERTS, cv, "seen = 0", null);
        });
    }

    public void clearAlerts() {
        io.execute(() -> helper.getWritableDatabase().delete(LocalDb.T_ALERTS, null, null));
    }

    // ------------------------------------------------------------------
    // Eventos (Event Engine) — ver com.stepvolt.app.event.EventEngine
    // ------------------------------------------------------------------

    private static final String EVENT_COLS =
            "id, ts, type, category, severity, confidence, duration_ms, source, message, metadata, status";

    private AppEvent readEvent(Cursor c) {
        AppEvent e = new AppEvent();
        e.id = c.getLong(0);
        e.ts = c.getLong(1);
        e.type = c.getString(2);
        e.category = EventCategory.valueOf(c.getString(3));
        e.severity = EventSeverity.valueOf(c.getString(4));
        e.confidence = c.getFloat(5);
        e.durationMs = c.getLong(6);
        e.source = c.getString(7);
        e.message = c.getString(8);
        e.metadata = c.getString(9);
        e.status = EventStatus.valueOf(c.getString(10));
        return e;
    }

    /** Grava um evento e preenche o id gerado no próprio objeto. */
    public void insertEvent(final AppEvent e) {
        io.execute(() -> {
            ContentValues cv = new ContentValues();
            cv.put("ts", e.ts);
            cv.put("type", e.type);
            cv.put("category", e.category.name());
            cv.put("severity", e.severity.name());
            cv.put("confidence", e.confidence);
            cv.put("duration_ms", e.durationMs);
            cv.put("source", e.source);
            cv.put("message", e.message);
            cv.put("metadata", e.metadata);
            cv.put("status", e.status.name());
            e.id = helper.getWritableDatabase().insert(LocalDb.T_EVENTS, null, cv);
        });
    }

    public List<AppEvent> events(int limit) {
        List<AppEvent> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + EVENT_COLS + " FROM " + LocalDb.T_EVENTS + " ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(readEvent(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void eventsAsync(final int limit, Result<List<AppEvent>> cb) {
        async(() -> events(limit), cb);
    }

    public List<AppEvent> eventsByCategory(EventCategory category, int limit) {
        List<AppEvent> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + EVENT_COLS + " FROM " + LocalDb.T_EVENTS
                        + " WHERE category = ? ORDER BY ts DESC LIMIT ?",
                new String[]{category.name(), String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(readEvent(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void eventsByCategoryAsync(final EventCategory category, final int limit,
                                      Result<List<AppEvent>> cb) {
        async(() -> eventsByCategory(category, limit), cb);
    }

    public int unresolvedEventCount() {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + LocalDb.T_EVENTS + " WHERE status = ?",
                new String[]{EventStatus.ACTIVE.name()});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public void unresolvedEventCountAsync(Result<Integer> cb) {
        async(this::unresolvedEventCount, cb);
    }

    public void setEventStatus(final long id, final EventStatus status) {
        io.execute(() -> {
            ContentValues cv = new ContentValues();
            cv.put("status", status.name());
            helper.getWritableDatabase().update(LocalDb.T_EVENTS, cv, "id = ?",
                    new String[]{String.valueOf(id)});
        });
    }

    public void clearEvents() {
        io.execute(() -> helper.getWritableDatabase().delete(LocalDb.T_EVENTS, null, null));
    }

    // ------------------------------------------------------------------
    // Manutenção e exportação
    // ------------------------------------------------------------------

    /** Apaga amostras antigas para o banco não crescer sem limite. */
    public void pruneOlderThan(final int days) {
        io.execute(() -> {
            long cutoff = System.currentTimeMillis() - days * 24L * 3600_000L;
            helper.getWritableDatabase().delete(LocalDb.T_SAMPLES,
                    "ts < ? AND (session_id IS NULL OR session_id = 0)",
                    new String[]{String.valueOf(cutoff)});
        });
    }

    /** Exporta as amostras de um período para CSV e devolve o arquivo gerado. */
    public void exportCsv(final File dir, final long fromTs, final long toTs, Result<File> cb) {
        async(() -> {
            File out = new File(dir, "stepvolt-dados.csv");
            Cursor c = helper.getReadableDatabase().rawQuery(
                    "SELECT ts, metric, value, session_id FROM " + LocalDb.T_SAMPLES
                            + " WHERE ts BETWEEN ? AND ? ORDER BY ts ASC",
                    new String[]{String.valueOf(fromTs), String.valueOf(toTs)});
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(out), Charset.forName("UTF-8"));
            try {
                w.write("timestamp_ms,data_hora,grandeza,valor,sessao\n");
                java.text.SimpleDateFormat fmt =
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                while (c.moveToNext()) {
                    long ts = c.getLong(0);
                    w.write(ts + "," + fmt.format(new java.util.Date(ts)) + ","
                            + c.getString(1) + "," + c.getDouble(2) + "," + c.getLong(3) + "\n");
                }
                w.flush();
            } finally {
                try { w.close(); } catch (Exception ignored) {}
                c.close();
            }
            return out;
        }, cb);
    }

    /** Média de uma grandeza dentro de uma sessão (0 se não houve leitura). */
    public double sessionAverage(long sessionId, String metric) {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COALESCE(AVG(value), 0) FROM " + LocalDb.T_SAMPLES
                        + " WHERE session_id = ? AND metric = ? AND value > 0",
                new String[]{String.valueOf(sessionId), metric});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    public void wipeAll() {
        io.execute(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.delete(LocalDb.T_SAMPLES, null, null);
            db.delete(LocalDb.T_SESSIONS, null, null);
            db.delete(LocalDb.T_ALERTS, null, null);
            db.delete(LocalDb.T_EVENTS, null, null);
            db.delete(LocalDb.T_ROUTE, null, null);
            db.delete(LocalDb.T_DAILY, null, null);
            db.delete(LocalDb.T_SHOES, null, null);
            db.delete(LocalDb.T_CONTACTS, null, null);
        });
    }
}
