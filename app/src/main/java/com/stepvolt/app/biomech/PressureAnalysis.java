package com.stepvolt.app.biomech;

/**
 * Leitura de pressão plantar por zona (calcanhar, meio, antepé).
 *
 * Isto descreve como a carga se distribui — não diagnostica pisada, lesão ou
 * condição de saúde. O texto da tela deixa isso claro.
 */
public final class PressureAnalysis {

    private PressureAnalysis() {}

    public enum Strike { HEEL, MID, FORE, UNKNOWN }
    public enum SymmetryLevel { GOOD, ATTENTION, HIGH, UNKNOWN }

    /** Distribuição de um pé, em porcentagem do total (soma 100). */
    public static final class Foot {
        public final double heelN, midN, toeN;
        public final double heelPct, midPct, toePct;
        public final boolean present;

        public Foot(double heelN, double midN, double toeN) {
            this.heelN = Math.max(0, heelN);
            this.midN = Math.max(0, midN);
            this.toeN = Math.max(0, toeN);
            double total = this.heelN + this.midN + this.toeN;
            this.present = total > 1.0;
            if (present) {
                heelPct = this.heelN / total * 100.0;
                midPct = this.midN / total * 100.0;
                toePct = this.toeN / total * 100.0;
            } else {
                heelPct = midPct = toePct = 0;
            }
        }

        public double totalN() { return heelN + midN + toeN; }

        public Strike strike() {
            if (!present) return Strike.UNKNOWN;
            // O padrão é definido pela zona dominante, com margem para não
            // classificar como "calcanhar" um pé equilibrado.
            if (heelPct >= 45 && heelPct > toePct) return Strike.HEEL;
            if (toePct >= 45 && toePct > heelPct) return Strike.FORE;
            return Strike.MID;
        }
    }

    public static String strikeLabel(Strike s) {
        switch (s) {
            case HEEL: return "Carga maior no calcanhar";
            case FORE: return "Carga maior no antepé";
            case MID: return "Carga distribuída";
            default: return "Sem leitura";
        }
    }

    /**
     * Índice de simetria: diferença entre os pés dividida pela média, em %.
     * 0% = pés idênticos. Definição clássica usada em análise de marcha.
     */
    public static double symmetryIndex(double left, double right) {
        double mean = (left + right) / 2.0;
        if (mean <= 0) return 0;
        return Math.abs(left - right) / mean * 100.0;
    }

    public static SymmetryLevel symmetryLevel(double indexPct) {
        if (Double.isNaN(indexPct)) return SymmetryLevel.UNKNOWN;
        if (indexPct < 10) return SymmetryLevel.GOOD;
        if (indexPct < 20) return SymmetryLevel.ATTENTION;
        return SymmetryLevel.HIGH;
    }

    public static String symmetryText(double indexPct, boolean leftHeavier) {
        SymmetryLevel level = symmetryLevel(indexPct);
        String side = leftHeavier ? "esquerdo" : "direito";
        switch (level) {
            case GOOD:
                return "Carga bem equilibrada entre os pés.";
            case ATTENTION:
                return "O pé " + side + " recebe um pouco mais de carga. É comum variar com o piso e o ritmo.";
            case HIGH:
                return "O pé " + side + " recebe bem mais carga. Se persistir em vários treinos, vale conversar com um profissional de educação física ou fisioterapia.";
            default:
                return "É preciso ler os dois pés para calcular a simetria.";
        }
    }

    /** Média simples de uma lista de leituras (ignora zeros de pé no ar). */
    public static double meanPositive(double[] values) {
        double sum = 0; int n = 0;
        for (double v : values) if (v > 0) { sum += v; n++; }
        return n == 0 ? 0 : sum / n;
    }
}
