package com.stepvolt.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class ShoeDao {

    private static final String COLS = "address, name, nickname, firmware, added_at, distance_m, steps,"
            + " energy, battery_pct, battery_updated, battery_discharged, retire_km, retired";

    private final Repo repo;

    public ShoeDao(Repo repo) { this.repo = repo; }

    private static Shoe read(Cursor c) {
        Shoe s = new Shoe();
        s.address = c.getString(0);
        s.name = c.getString(1);
        s.nickname = c.getString(2);
        s.firmware = c.getString(3);
        s.addedAt = c.getLong(4);
        s.distanceM = c.getDouble(5);
        s.steps = c.getLong(6);
        s.energy = c.getDouble(7);
        s.batteryPct = c.getDouble(8);
        s.batteryUpdated = c.getLong(9);
        s.batteryDischarged = c.getDouble(10);
        s.retireKm = c.getDouble(11);
        s.retired = c.getInt(12) == 1;
        return s;
    }

    /** Registra o tênis na primeira conexão e atualiza nome/firmware nas seguintes. */
    public void seen(final String address, final String name, final String firmware) {
        if (address == null) return;
        repo.exec(() -> {
            SQLiteDatabase db = repo.writable();
            ContentValues cv = new ContentValues();
            cv.put("address", address);
            cv.put("name", name);
            cv.put("firmware", firmware);
            cv.put("added_at", System.currentTimeMillis());
            long inserted = db.insertWithOnConflict(LocalDb.T_SHOES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            if (inserted == -1) {
                ContentValues up = new ContentValues();
                if (name != null && !name.isEmpty()) up.put("name", name);
                if (firmware != null && !firmware.isEmpty()) up.put("firmware", firmware);
                if (up.size() > 0) db.update(LocalDb.T_SHOES, up, "address = ?", new String[]{address});
            }
        });
    }

    public void addSteps(final String address, final long steps, final double energy) {
        if (address == null || (steps <= 0 && energy <= 0)) return;
        repo.exec(() -> repo.writable().execSQL("UPDATE " + LocalDb.T_SHOES
                        + " SET steps = steps + ?, energy = energy + ? WHERE address = ?",
                new Object[]{steps, energy, address}));
    }

    /** Chamado pelo Repo ao fechar a sessão (já na fila de escrita). */
    static void addDistance(SQLiteDatabase db, String address, double meters) {
        if (meters <= 0) return;
        db.execSQL("UPDATE " + LocalDb.T_SHOES + " SET distance_m = distance_m + ? WHERE address = ?",
                new Object[]{meters, address});
    }

    public void updateBattery(final String address, final double pct, final double totalDischarged) {
        if (address == null) return;
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("battery_pct", pct);
            cv.put("battery_updated", System.currentTimeMillis());
            cv.put("battery_discharged", totalDischarged);
            repo.writable().update(LocalDb.T_SHOES, cv, "address = ?", new String[]{address});
        });
    }

    public void setNickname(final String address, final String nickname) {
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("nickname", nickname);
            repo.writable().update(LocalDb.T_SHOES, cv, "address = ?", new String[]{address});
        });
    }

    public void setRetireKm(final String address, final double km) {
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("retire_km", km);
            repo.writable().update(LocalDb.T_SHOES, cv, "address = ?", new String[]{address});
        });
    }

    public void setRetired(final String address, final boolean retired) {
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("retired", retired ? 1 : 0);
            repo.writable().update(LocalDb.T_SHOES, cv, "address = ?", new String[]{address});
        });
    }

    public void resetDistance(final String address) {
        repo.exec(() -> {
            ContentValues cv = new ContentValues();
            cv.put("distance_m", 0);
            repo.writable().update(LocalDb.T_SHOES, cv, "address = ?", new String[]{address});
        });
    }

    public void remove(final String address) {
        repo.exec(() -> repo.writable().delete(LocalDb.T_SHOES, "address = ?", new String[]{address}));
    }

    public List<Shoe> all() {
        List<Shoe> out = new ArrayList<>();
        Cursor c = repo.readable().rawQuery("SELECT " + COLS + " FROM " + LocalDb.T_SHOES
                + " ORDER BY retired ASC, added_at ASC", null);
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Shoe get(String address) {
        if (address == null) return null;
        Cursor c = repo.readable().rawQuery("SELECT " + COLS + " FROM " + LocalDb.T_SHOES
                + " WHERE address = ?", new String[]{address});
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public void allAsync(Repo.Result<List<Shoe>> cb) { repo.call(this::all, cb); }
}
