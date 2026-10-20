package com.stepvolt.app.energy;

/**
 * Desgaste do tênis e bateria.
 *
 * Quilometragem: tênis de corrida costuma durar de 500 a 800 km; o app usa 700
 * km como padrão e deixa a pessoa trocar o limite.
 *
 * Bateria: o firmware só informa a porcentagem. Para estimar o uso da bateria
 * sem inventar dado, somamos o quanto ela já descarregou — 100 pontos de
 * descarga acumulada = 1 ciclo equivalente. Li-ion costuma aguentar algumas
 * centenas de ciclos, então é uma estimativa útil mas não uma medição de
 * capacidade; a tela diz isso.
 */
public final class ShoeWear {

    private ShoeWear() {}

    public static final double DEFAULT_RETIRE_KM = 700.0;
    public static final double TYPICAL_BATTERY_CYCLES = 400.0;

    public enum Level { GOOD, ATTENTION, SOON, REPLACE }

    public static double wearFraction(double distanceKm, double retireKm) {
        if (retireKm <= 0) return 0;
        return distanceKm / retireKm;
    }

    public static Level level(double distanceKm, double retireKm) {
        double f = wearFraction(distanceKm, retireKm);
        if (f >= 1.0) return Level.REPLACE;
        if (f >= 0.9) return Level.SOON;
        if (f >= 0.7) return Level.ATTENTION;
        return Level.GOOD;
    }

    public static String levelText(Level l, double remainingKm) {
        switch (l) {
            case REPLACE: return "Passou do limite de quilometragem. Considere trocar.";
            case SOON: return String.format(java.util.Locale.US, "Faltam cerca de %.0f km para o limite. Vá planejando a troca.", Math.max(0, remainingKm));
            case ATTENTION: return String.format(java.util.Locale.US, "Já passou de 70%% do limite. Faltam cerca de %.0f km.", Math.max(0, remainingKm));
            default: return String.format(java.util.Locale.US, "Em boa fase. Faltam cerca de %.0f km para o limite.", Math.max(0, remainingKm));
        }
    }

    /** Acumula descarga a partir das leituras de bateria em ordem. */
    public static final class BatteryTracker {
        private double lastPct = -1;
        private double discharged = 0;

        public BatteryTracker(double alreadyDischarged) { this.discharged = alreadyDischarged; }

        /** Chame a cada leitura. Subidas (recarga) não contam como descarga. */
        public void onReading(double pct) {
            if (pct < 0 || pct > 100) return;
            if (lastPct >= 0 && pct < lastPct) discharged += lastPct - pct;
            lastPct = pct;
        }

        public double dischargedPoints() { return discharged; }
        public double equivalentCycles() { return discharged / 100.0; }
    }

    public static double batteryLifeUsedFraction(double equivalentCycles) {
        return Math.min(1.0, equivalentCycles / TYPICAL_BATTERY_CYCLES);
    }
}
