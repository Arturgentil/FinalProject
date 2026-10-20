package com.stepvolt.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.stepvolt.app.gps.GeoMath;

import java.util.ArrayList;
import java.util.List;

/** Pontos de GPS de cada sessão. Gravados na mesma fila do Repo, para chegarem antes do fechamento da sessão. */
public class RouteDao {

    private final Repo repo;

    public RouteDao(Repo repo) { this.repo = repo; }

    public void add(final long sessionId, final GeoMath.Fix fix, final float speed) {
        if (sessionId <= 0 || fix == null) return;
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("session_id", sessionId);
            cv.put("ts", fix.ts);
            cv.put("lat", fix.lat);
            cv.put("lon", fix.lon);
            if (!Double.isNaN(fix.alt)) cv.put("alt", fix.alt);
            cv.put("accuracy", fix.accuracy);
            cv.put("speed", speed);
            repo.writable().insert(LocalDb.T_ROUTE, null, cv);
        });
    }

    public static List<GeoMath.Fix> pointsSync(SQLiteDatabase db, long sessionId) {
        List<GeoMath.Fix> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT ts, lat, lon, alt, accuracy FROM " + LocalDb.T_ROUTE
                + " WHERE session_id = ? ORDER BY ts ASC", new String[]{String.valueOf(sessionId)});
        try {
            while (c.moveToNext()) {
                double alt = c.isNull(3) ? Double.NaN : c.getDouble(3);
                out.add(new GeoMath.Fix(c.getLong(0), c.getDouble(1), c.getDouble(2), alt, (float) c.getDouble(4)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<GeoMath.Fix> points(long sessionId) {
        return pointsSync(repo.readable(), sessionId);
    }

    public void pointsAsync(final long sessionId, Repo.Result<List<GeoMath.Fix>> cb) {
        repo.call(() -> points(sessionId), cb);
    }
}
