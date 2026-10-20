package com.stepvolt.app.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Leitura de GPS durante uma sessão, usando só o LocationManager do Android
 * (sem Google Play Services, então funciona também em aparelhos sem ele).
 *
 * Só liga o GPS enquanto há sessão ativa, para não gastar bateria à toa. Cada
 * ponto bruto é entregue aos ouvintes; quem decide o que descartar é o
 * {@link GeoMath}, na hora de calcular a distância.
 */
public class LocationTracker implements LocationListener {

    public interface Listener {
        void onFix(GeoMath.Fix fix, float speedMs);
        default void onGpsStatus(boolean enabled, boolean hasPermission) {}
    }

    private static final long MIN_TIME_MS = 2000;
    private static final float MIN_DISTANCE_M = 1f;

    private final Context ctx;
    private final LocationManager lm;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean running = false;
    private GeoMath.Fix lastFix;
    private float lastSpeed;

    public LocationTracker(Context context) {
        this.ctx = context.getApplicationContext();
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isGpsEnabled() {
        try {
            return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRunning() { return running; }
    public GeoMath.Fix lastFix() { return lastFix; }
    public float lastSpeedMs() { return lastSpeed; }

    /** Liga o GPS. Retorna false se faltar permissão ou o GPS estiver desligado. */
    @SuppressLint("MissingPermission")
    public boolean start() {
        if (running) return true;
        boolean permission = hasPermission();
        boolean enabled = isGpsEnabled();
        for (Listener l : listeners) l.onGpsStatus(enabled, permission);
        if (!permission || !enabled || lm == null) return false;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M,
                    this, Looper.getMainLooper());
            running = true;
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public void stop() {
        if (!running) return;
        try {
            lm.removeUpdates(this);
        } catch (SecurityException ignored) {}
        running = false;
        lastFix = null;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (l == null) return;
        double alt = l.hasAltitude() ? l.getAltitude() : Double.NaN;
        float acc = l.hasAccuracy() ? l.getAccuracy() : 0f;
        GeoMath.Fix fix = new GeoMath.Fix(System.currentTimeMillis(), l.getLatitude(), l.getLongitude(), alt, acc);
        lastFix = fix;
        lastSpeed = l.hasSpeed() ? l.getSpeed() : 0f;
        for (Listener li : listeners) li.onFix(fix, lastSpeed);
    }

    @Override
    public void onProviderEnabled(String provider) {
        for (Listener l : listeners) l.onGpsStatus(true, hasPermission());
    }

    @Override
    public void onProviderDisabled(String provider) {
        for (Listener l : listeners) l.onGpsStatus(false, hasPermission());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
