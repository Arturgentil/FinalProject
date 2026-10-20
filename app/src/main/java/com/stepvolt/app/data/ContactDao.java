package com.stepvolt.app.data;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class ContactDao {

    private final Repo repo;

    public ContactDao(Repo repo) { this.repo = repo; }

    public List<EmergencyContact> all() {
        List<EmergencyContact> out = new ArrayList<>();
        Cursor c = repo.readable().rawQuery("SELECT id, name, phone, relation, notify_sos, remote_id FROM "
                + LocalDb.T_CONTACTS + " ORDER BY id ASC", null);
        try {
            while (c.moveToNext()) {
                EmergencyContact e = new EmergencyContact();
                e.id = c.getLong(0);
                e.name = c.getString(1);
                e.phone = c.getString(2);
                e.relation = c.getString(3);
                e.notifySos = c.getInt(4) == 1;
                e.remoteId = c.getString(5);
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void allAsync(Repo.Result<List<EmergencyContact>> cb) { repo.call(this::all, cb); }

    public void save(final EmergencyContact e, final Repo.Result<Long> cb) {
        repo.call(() -> {
            ContentValues cv = new ContentValues();
            cv.put("name", e.name);
            cv.put("phone", e.phone);
            cv.put("relation", e.relation);
            cv.put("notify_sos", e.notifySos ? 1 : 0);
            cv.put("remote_id", e.remoteId);
            if (e.id > 0) {
                repo.writable().update(LocalDb.T_CONTACTS, cv, "id = ?", new String[]{String.valueOf(e.id)});
                return e.id;
            }
            return repo.writable().insert(LocalDb.T_CONTACTS, null, cv);
        }, cb);
    }

    public void delete(final long id) {
        repo.exec(() -> repo.writable().delete(LocalDb.T_CONTACTS, "id = ?", new String[]{String.valueOf(id)}));
    }
}
