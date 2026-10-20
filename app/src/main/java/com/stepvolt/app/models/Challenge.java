package com.stepvolt.app.models;

/** Desafio entre pessoas. O progresso de cada um é calculado no servidor a partir das atividades dentro do período. */
public class Challenge {
    public String id;
    public String title;
    /** "steps" | "energy" | "distance" */
    public String metric;
    public double targetValue;
    public String startsAt;
    public String endsAt;
    public int participants;
    public boolean joined;
    public boolean mine;
    public String ownerName;
    public double myValue;
    /** "active" | "finished" */
    public String status;

    public static String metricLabel(String m) {
        if ("energy".equals(m)) return "Energia (mWh)";
        if ("distance".equals(m)) return "Distância (km)";
        return "Passos";
    }

    public static String metricUnit(String m) {
        if ("energy".equals(m)) return "mWh";
        if ("distance".equals(m)) return "km";
        return "passos";
    }
}
