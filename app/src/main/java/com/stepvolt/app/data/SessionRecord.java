package com.stepvolt.app.data;

/** Uma sessão de uso do tênis, do "Iniciar" ao "Encerrar". */
public class SessionRecord {
    public long id;
    public long startedAt;
    public long endedAt;
    public long steps;
    public double energy;
    public double bpmAvg;
    public double bpmMin;
    public double bpmMax;
    public double spo2Avg;
    public double spo2Min;
    public int sampleCount;
    public String deviceName;
    public boolean synced;
    public String remoteId;
    public double distanceM;
    public double elevGainM;
    public String kind = "walk";
    public String shoeAddress;

    public long durationMs() {
        long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
        return Math.max(0, end - startedAt);
    }

    public int durationMinutes() {
        return (int) Math.max(1, Math.round(durationMs() / 60000.0));
    }

    public boolean isOpen() {
        return endedAt <= 0;
    }
}
