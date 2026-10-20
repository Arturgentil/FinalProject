package com.stepvolt.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Banco local do app.
 *
 * Existe por dois motivos: os dados do tênis chegam rápido demais para mandar
 * um a um pela rede, e o app precisa continuar mostrando histórico e gráficos
 * quando o celular está sem internet. O servidor continua sendo a fonte oficial
 * das sessões — este banco é a memória de curto prazo e a fila de envio.
 *
 * A tabela de amostras é "alta" (uma linha por grandeza) de propósito: assim um
 * sensor novo no ESP32 não exige alterar o esquema nem migrar nada.
 */
public class LocalDb extends SQLiteOpenHelper {

    private static final String NAME = "stepvolt_local.db";
    private static final int VERSION = 3;

    public static final String T_SAMPLES = "samples";
    public static final String T_SESSIONS = "sessions";
    public static final String T_ALERTS = "alerts";
    public static final String T_EVENTS = "events";
    public static final String T_ROUTE = "route_points";
    public static final String T_DAILY = "daily_stats";
    public static final String T_SHOES = "shoes";
    public static final String T_CONTACTS = "emergency_contacts";

    private static LocalDb instance;

    public static synchronized LocalDb get(Context context) {
        if (instance == null) instance = new LocalDb(context.getApplicationContext());
        return instance;
    }

    private LocalDb(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_SAMPLES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "metric TEXT NOT NULL,"
                + "value REAL NOT NULL,"
                + "session_id INTEGER)");
        db.execSQL("CREATE INDEX idx_samples_metric_ts ON " + T_SAMPLES + "(metric, ts)");
        db.execSQL("CREATE INDEX idx_samples_session ON " + T_SAMPLES + "(session_id)");

        db.execSQL("CREATE TABLE " + T_SESSIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "started_at INTEGER NOT NULL,"
                + "ended_at INTEGER,"
                + "steps INTEGER DEFAULT 0,"
                + "energy REAL DEFAULT 0,"
                + "bpm_avg REAL DEFAULT 0,"
                + "bpm_min REAL DEFAULT 0,"
                + "bpm_max REAL DEFAULT 0,"
                + "spo2_avg REAL DEFAULT 0,"
                + "spo2_min REAL DEFAULT 0,"
                + "sample_count INTEGER DEFAULT 0,"
                + "device_name TEXT,"
                + "synced INTEGER DEFAULT 0,"
                + "remote_id TEXT,"
                + "distance_m REAL DEFAULT 0,"
                + "elev_gain_m REAL DEFAULT 0,"
                + "kind TEXT DEFAULT 'walk',"
                + "shoe_address TEXT)");
        db.execSQL("CREATE INDEX idx_sessions_started ON " + T_SESSIONS + "(started_at DESC)");

        db.execSQL("CREATE TABLE " + T_ALERTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "metric TEXT NOT NULL,"
                + "value REAL NOT NULL,"
                + "threshold REAL NOT NULL,"
                + "severity INTEGER DEFAULT 1,"
                + "message TEXT NOT NULL,"
                + "seen INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_alerts_ts ON " + T_ALERTS + "(ts DESC)");

        createEventsTable(db);
        createV3Tables(db);
    }

    /**
     * Tabela do Event Engine (v2). Categoria/severidade/status ficam como TEXT
     * (o nome do enum) em vez de INTEGER: são poucas linhas por dia, e dá pra
     * olhar o banco na mão sem precisar decorar o que cada número significa.
     */
    private void createEventsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_EVENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "type TEXT NOT NULL,"
                + "category TEXT NOT NULL,"
                + "severity TEXT NOT NULL,"
                + "confidence REAL DEFAULT 1,"
                + "duration_ms INTEGER DEFAULT 0,"
                + "source TEXT,"
                + "message TEXT NOT NULL,"
                + "metadata TEXT,"
                + "status TEXT DEFAULT 'ACTIVE')");
        db.execSQL("CREATE INDEX idx_events_ts ON " + T_EVENTS + "(ts DESC)");
        db.execSQL("CREATE INDEX idx_events_category ON " + T_EVENTS + "(category, ts DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Cada "if" cobre quem está vindo de uma versão específica — nunca com
        // DROP TABLE, os dados de quem já usa o app continuam intactos.
        if (oldVersion < 2) {
            createEventsTable(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN distance_m REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN elev_gain_m REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN kind TEXT DEFAULT 'walk'");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN shoe_address TEXT");
            createV3Tables(db);
            backfillDailyFromSessions(db);
        }
    }

    /**
     * Tabelas da v3: rota de GPS, resumo por dia (sobrevive à limpeza de amostras
     * antigas, por isso a tela de Energia consegue mostrar meses), tênis do
     * usuário e contatos de emergência.
     */
    private void createV3Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_ROUTE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "session_id INTEGER NOT NULL,"
                + "ts INTEGER NOT NULL,"
                + "lat REAL NOT NULL,"
                + "lon REAL NOT NULL,"
                + "alt REAL,"
                + "accuracy REAL,"
                + "speed REAL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_session ON " + T_ROUTE + "(session_id, ts)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DAILY + " ("
                + "day TEXT PRIMARY KEY,"
                + "steps INTEGER DEFAULT 0,"
                + "energy REAL DEFAULT 0,"
                + "distance_m REAL DEFAULT 0,"
                + "duration_ms INTEGER DEFAULT 0,"
                + "sessions INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SHOES + " ("
                + "address TEXT PRIMARY KEY,"
                + "name TEXT,"
                + "nickname TEXT,"
                + "firmware TEXT,"
                + "added_at INTEGER NOT NULL,"
                + "distance_m REAL DEFAULT 0,"
                + "steps INTEGER DEFAULT 0,"
                + "energy REAL DEFAULT 0,"
                + "battery_pct REAL DEFAULT -1,"
                + "battery_updated INTEGER DEFAULT 0,"
                + "battery_discharged REAL DEFAULT 0,"
                + "retire_km REAL DEFAULT 700,"
                + "retired INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_CONTACTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "phone TEXT NOT NULL,"
                + "relation TEXT,"
                + "notify_sos INTEGER DEFAULT 1,"
                + "remote_id TEXT)");
    }

    /**
     * Quem já usava o app antes da v3 tem sessões mas não tem resumo por dia.
     * Reconstrói o resumo a partir das sessões fechadas, para a tela de Energia
     * não começar vazia.
     */
    private void backfillDailyFromSessions(SQLiteDatabase db) {
        android.database.Cursor c = db.rawQuery(
                "SELECT strftime('%Y-%m-%d', started_at/1000, 'unixepoch', 'localtime'),"
                        + " SUM(steps), SUM(energy), SUM(COALESCE(ended_at, started_at) - started_at), COUNT(*)"
                        + " FROM " + T_SESSIONS + " WHERE ended_at IS NOT NULL GROUP BY 1", null);
        try {
            while (c.moveToNext()) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("day", c.getString(0));
                cv.put("steps", c.getLong(1));
                cv.put("energy", c.getDouble(2));
                cv.put("duration_ms", c.getLong(3));
                cv.put("sessions", c.getInt(4));
                db.insertWithOnConflict(T_DAILY, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } finally {
            c.close();
        }
    }
}
