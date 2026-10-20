package com.stepvolt.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.stepvolt.app.core.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Backup local em JSON: treinos, resumo por dia, tênis, contatos de emergência
 * e metas. Não inclui as amostras brutas (são grandes e já estão resumidas nos
 * treinos) nem a senha ou o token da conta.
 *
 * Restaurar soma ao que já existe: nunca apaga nem sobrescreve.
 */
public final class Backup {

    private Backup() {}

    public static final int FORMAT = 1;

    public static File write(Context ctx) throws Exception {
        Repo repo = Repo.get(ctx);
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("app", "StepVolt");
        root.put("createdAt", System.currentTimeMillis());

        JSONArray sessions = new JSONArray();
        for (SessionRecord s : repo.sessions(100000)) {
            JSONObject o = new JSONObject();
            o.put("startedAt", s.startedAt); o.put("endedAt", s.endedAt);
            o.put("steps", s.steps); o.put("energy", s.energy);
            o.put("bpmAvg", s.bpmAvg); o.put("bpmMin", s.bpmMin); o.put("bpmMax", s.bpmMax);
            o.put("spo2Avg", s.spo2Avg); o.put("spo2Min", s.spo2Min);
            o.put("samples", s.sampleCount);
            o.put("device", s.deviceName == null ? JSONObject.NULL : s.deviceName);
            o.put("remoteId", s.remoteId == null ? JSONObject.NULL : s.remoteId);
            o.put("distanceM", s.distanceM); o.put("elevGainM", s.elevGainM);
            o.put("kind", s.kind);
            o.put("shoe", s.shoeAddress == null ? JSONObject.NULL : s.shoeAddress);
            sessions.put(o);
        }
        root.put("sessions", sessions);

        JSONArray daily = new JSONArray();
        for (DayStat d : repo.daily.range(0, System.currentTimeMillis() + 86_400_000L)) {
            JSONObject o = new JSONObject();
            o.put("day", d.day); o.put("steps", d.steps); o.put("energy", d.energy);
            o.put("distanceM", d.distanceM); o.put("durationMs", d.durationMs); o.put("sessions", d.sessions);
            daily.put(o);
        }
        root.put("daily", daily);

        JSONArray shoes = new JSONArray();
        for (Shoe s : repo.shoes.all()) {
            JSONObject o = new JSONObject();
            o.put("address", s.address); o.put("name", s.name == null ? JSONObject.NULL : s.name);
            o.put("nickname", s.nickname == null ? JSONObject.NULL : s.nickname);
            o.put("firmware", s.firmware == null ? JSONObject.NULL : s.firmware);
            o.put("addedAt", s.addedAt); o.put("distanceM", s.distanceM); o.put("steps", s.steps);
            o.put("energy", s.energy); o.put("batteryDischarged", s.batteryDischarged);
            o.put("retireKm", s.retireKm); o.put("retired", s.retired);
            shoes.put(o);
        }
        root.put("shoes", shoes);

        JSONArray contacts = new JSONArray();
        for (EmergencyContact e : repo.contacts.all()) {
            JSONObject o = new JSONObject();
            o.put("name", e.name); o.put("phone", e.phone);
            o.put("relation", e.relation == null ? "" : e.relation); o.put("notifySos", e.notifySos);
            contacts.put(o);
        }
        root.put("contacts", contacts);

        JSONObject goals = new JSONObject();
        goals.put("dailySteps", Prefs.dailyStepGoal(ctx));
        goals.put("dailyEnergy", Prefs.dailyEnergyGoal(ctx));
        root.put("goals", goals);

        File f = new File(com.stepvolt.app.export.Exporter.dir(ctx),
                "stepvolt-backup-" + com.stepvolt.app.export.Exporter.stamp() + ".json");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"))) {
            w.write(root.toString(1));
        }
        return f;
    }

    public static final class Summary {
        public int sessions, days, shoes, contacts;
        public String text() {
            return sessions + " treino(s), " + days + " dia(s) de resumo, " + shoes + " tênis e " + contacts + " contato(s) restaurados.";
        }
    }

    /** Lê o arquivo inteiro e valida antes de gravar qualquer coisa. */
    public static Summary restore(Context ctx, InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(chunk)) > 0) {
            total += n;
            if (total > 50L * 1024 * 1024) throw new IOException("Arquivo grande demais para ser um backup do StepVolt.");
            buf.write(chunk, 0, n);
        }
        JSONObject root = new JSONObject(new String(buf.toByteArray(), Charset.forName("UTF-8")));
        if (!"StepVolt".equals(root.optString("app")) || root.optInt("format", 0) != FORMAT) {
            throw new IOException("Este arquivo não é um backup do StepVolt compatível.");
        }

        Repo repo = Repo.get(ctx);
        Summary sum = new Summary();

        List<SessionRecord> sessions = new ArrayList<>();
        JSONArray sa = root.optJSONArray("sessions");
        if (sa != null) for (int i = 0; i < sa.length(); i++) {
            JSONObject o = sa.getJSONObject(i);
            SessionRecord s = new SessionRecord();
            s.startedAt = o.getLong("startedAt"); s.endedAt = o.optLong("endedAt", 0);
            s.steps = o.optLong("steps"); s.energy = o.optDouble("energy");
            s.bpmAvg = o.optDouble("bpmAvg"); s.bpmMin = o.optDouble("bpmMin"); s.bpmMax = o.optDouble("bpmMax");
            s.spo2Avg = o.optDouble("spo2Avg"); s.spo2Min = o.optDouble("spo2Min");
            s.sampleCount = o.optInt("samples");
            s.deviceName = o.isNull("device") ? null : o.optString("device");
            s.remoteId = o.isNull("remoteId") ? null : o.optString("remoteId");
            s.distanceM = o.optDouble("distanceM"); s.elevGainM = o.optDouble("elevGainM");
            s.kind = o.optString("kind", "walk");
            s.shoeAddress = o.isNull("shoe") ? null : o.optString("shoe");
            sessions.add(s);
        }
        sum.sessions = repo.importSessions(sessions);

        SQLiteDatabase db = repo.writable();
        JSONArray da = root.optJSONArray("daily");
        if (da != null) for (int i = 0; i < da.length(); i++) {
            JSONObject o = da.getJSONObject(i);
            String day = o.getString("day");
            ContentValues cv = new ContentValues();
            cv.put("day", day);
            db.insertWithOnConflict(LocalDb.T_DAILY, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            // Mantém o maior valor: o restaurado nunca reduz o que já está no aparelho.
            db.execSQL("UPDATE " + LocalDb.T_DAILY + " SET steps = MAX(steps, ?), energy = MAX(energy, ?),"
                            + " distance_m = MAX(distance_m, ?), duration_ms = MAX(duration_ms, ?),"
                            + " sessions = MAX(sessions, ?) WHERE day = ?",
                    new Object[]{o.optLong("steps"), o.optDouble("energy"), o.optDouble("distanceM"),
                            o.optLong("durationMs"), o.optInt("sessions"), day});
            sum.days++;
        }

        JSONArray sh = root.optJSONArray("shoes");
        if (sh != null) for (int i = 0; i < sh.length(); i++) {
            JSONObject o = sh.getJSONObject(i);
            ContentValues cv = new ContentValues();
            cv.put("address", o.getString("address"));
            cv.put("name", o.isNull("name") ? null : o.optString("name"));
            cv.put("nickname", o.isNull("nickname") ? null : o.optString("nickname"));
            cv.put("firmware", o.isNull("firmware") ? null : o.optString("firmware"));
            cv.put("added_at", o.optLong("addedAt", System.currentTimeMillis()));
            cv.put("distance_m", o.optDouble("distanceM")); cv.put("steps", o.optLong("steps"));
            cv.put("energy", o.optDouble("energy")); cv.put("battery_discharged", o.optDouble("batteryDischarged"));
            cv.put("retire_km", o.optDouble("retireKm", 700)); cv.put("retired", o.optBoolean("retired") ? 1 : 0);
            if (db.insertWithOnConflict(LocalDb.T_SHOES, null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1) sum.shoes++;
        }

        JSONArray ca = root.optJSONArray("contacts");
        if (ca != null) for (int i = 0; i < ca.length(); i++) {
            JSONObject o = ca.getJSONObject(i);
            String phone = o.getString("phone");
            android.database.Cursor cur = db.rawQuery("SELECT 1 FROM " + LocalDb.T_CONTACTS + " WHERE phone = ?", new String[]{phone});
            boolean exists;
            try { exists = cur.moveToFirst(); } finally { cur.close(); }
            if (exists) continue;
            ContentValues cv = new ContentValues();
            cv.put("name", o.getString("name")); cv.put("phone", phone);
            cv.put("relation", o.optString("relation", "")); cv.put("notify_sos", o.optBoolean("notifySos", true) ? 1 : 0);
            db.insert(LocalDb.T_CONTACTS, null, cv);
            sum.contacts++;
        }
        return sum;
    }
}
