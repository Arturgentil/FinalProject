package com.stepvolt.app.data;

/** Um aviso gerado quando uma medida passou do limite configurado. */
public class AlertRecord {

    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int CRITICAL = 2;

    public long id;
    public long ts;
    public String metric;
    public double value;
    public double threshold;
    public int severity;
    public String message;
    public boolean seen;

    public int color() {
        switch (severity) {
            case CRITICAL: return 0xFFFF3D71;
            case WARNING: return 0xFFFFD166;
            default: return 0xFF4CC9F0;
        }
    }

    public String severityLabel() {
        switch (severity) {
            case CRITICAL: return "Crítico";
            case WARNING: return "Atenção";
            default: return "Informação";
        }
    }
}
