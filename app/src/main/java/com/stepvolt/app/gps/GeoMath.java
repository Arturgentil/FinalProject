package com.stepvolt.app.gps;

import java.util.List;

/**
 * Contas de rota sem nenhuma dependência do Android — dá para testar na JVM.
 *
 * GPS de celular tem ruído: parado, o ponto "anda" alguns metros; em prédios e
 * sob árvores, pula. Por isso a distância não é a soma crua dos segmentos:
 * descartamos pontos de baixa precisão, saltos fisicamente impossíveis e
 * deslocamentos menores que o erro típico.
 */
public final class GeoMath {

    private GeoMath() {}

    public static final double EARTH_RADIUS_M = 6_371_000.0;

    /** Um ponto da rota. */
    public static final class Fix {
        public final long ts;
        public final double lat;
        public final double lon;
        public final double alt;      // NaN se desconhecida
        public final float accuracy;  // metros; <=0 se desconhecida

        public Fix(long ts, double lat, double lon, double alt, float accuracy) {
            this.ts = ts; this.lat = lat; this.lon = lon; this.alt = alt; this.accuracy = accuracy;
        }
    }

    public static final class Track {
        public double distanceM;
        public double elevationGainM;
        public double elevationLossM;
        public long movingMs;
        public long elapsedMs;
        public int usedPoints;
        public int rejectedPoints;

        /** Ritmo médio em segundos por km (0 se não houve movimento). */
        public double paceSecPerKm() {
            if (distanceM < 10 || movingMs <= 0) return 0;
            return (movingMs / 1000.0) / (distanceM / 1000.0);
        }

        public double avgSpeedKmh() {
            if (movingMs <= 0) return 0;
            return (distanceM / 1000.0) / (movingMs / 3_600_000.0);
        }
    }

    /** Precisão acima da qual o ponto é ignorado (metros). */
    public static final float MAX_ACCURACY_M = 30f;
    /** Velocidade acima da qual o salto é considerado erro (m/s ≈ 43 km/h). */
    public static final double MAX_SPEED_MS = 12.0;
    /** Deslocamento mínimo para contar como movimento (metros). */
    public static final double MIN_STEP_M = 3.0;
    /** Variação de altitude mínima para entrar na soma de subida/descida (metros). */
    public static final double MIN_ELEV_STEP_M = 2.0;

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static Track analyze(List<Fix> fixes) {
        Track t = new Track();
        if (fixes == null || fixes.isEmpty()) return t;

        t.elapsedMs = Math.max(0, fixes.get(fixes.size() - 1).ts - fixes.get(0).ts);

        Fix last = null;
        double lastAlt = Double.NaN;
        for (Fix f : fixes) {
            if (f.accuracy > MAX_ACCURACY_M) { t.rejectedPoints++; continue; }
            if (last == null) { last = f; lastAlt = f.alt; t.usedPoints++; continue; }

            long dt = f.ts - last.ts;
            if (dt <= 0) { t.rejectedPoints++; continue; }
            double d = haversine(last.lat, last.lon, f.lat, f.lon);
            double speed = d / (dt / 1000.0);
            if (speed > MAX_SPEED_MS) { t.rejectedPoints++; continue; }
            if (d < MIN_STEP_M) { continue; } // parado ou ruído

            t.distanceM += d;
            t.movingMs += dt;
            t.usedPoints++;

            if (!Double.isNaN(f.alt) && !Double.isNaN(lastAlt)) {
                double dz = f.alt - lastAlt;
                if (Math.abs(dz) >= MIN_ELEV_STEP_M) {
                    if (dz > 0) t.elevationGainM += dz; else t.elevationLossM += -dz;
                    lastAlt = f.alt;
                }
            } else if (!Double.isNaN(f.alt)) {
                lastAlt = f.alt;
            }
            last = f;
        }
        return t;
    }

    /** "5:42" a partir de segundos por km; "—" se não houver ritmo. */
    public static String formatPace(double secPerKm) {
        if (secPerKm <= 0 || Double.isInfinite(secPerKm) || secPerKm > 3600) return "—";
        int total = (int) Math.round(secPerKm);
        return (total / 60) + ":" + (total % 60 < 10 ? "0" : "") + (total % 60);
    }

    /**
     * Estimativa de energia por quilômetro, a partir do que foi medido na
     * sessão. Se a distância for pequena demais, devolve 0 em vez de um
     * número instável.
     */
    public static double mwhPerKm(double energyMwh, double distanceM) {
        if (distanceM < 100) return 0;
        return energyMwh / (distanceM / 1000.0);
    }
}
