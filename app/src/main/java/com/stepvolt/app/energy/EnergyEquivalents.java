package com.stepvolt.app.energy;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduz energia gerada (mWh) em coisas do dia a dia. Os valores de referência
 * são ordens de grandeza típicas, e a tela diz isso — a ideia é dar noção de
 * escala, não fazer uma medição.
 */
public final class EnergyEquivalents {

    private EnergyEquivalents() {}

    /** Bateria de celular comum: ~4.000 mAh a 3,85 V ≈ 15,4 Wh. */
    public static final double PHONE_BATTERY_MWH = 15_400.0;
    /** Lâmpada LED de 9 W. */
    public static final double LED_9W_MW = 9_000.0;
    /** Um LED indicador de 20 mA a 2 V = 40 mW. */
    public static final double INDICATOR_LED_MW = 40.0;
    /** Smartwatch: bateria de ~300 mAh a 3,85 V ≈ 1,155 Wh. */
    public static final double WATCH_BATTERY_MWH = 1_155.0;

    public static final class Item {
        public final String title;
        public final String detail;
        public Item(String title, String detail) { this.title = title; this.detail = detail; }
    }

    /** Fração de uma carga de celular (0..n). */
    public static double phoneCharges(double mwh) {
        return mwh / PHONE_BATTERY_MWH;
    }

    public static double watchCharges(double mwh) {
        return mwh / WATCH_BATTERY_MWH;
    }

    /** Quantos minutos uma lâmpada LED de 9 W ficaria acesa. */
    public static double ledBulbMinutes(double mwh) {
        return (mwh / LED_9W_MW) * 60.0;
    }

    /** Quantos minutos um LED indicador ficaria aceso. */
    public static double indicatorLedMinutes(double mwh) {
        return (mwh / INDICATOR_LED_MW) * 60.0;
    }

    public static List<Item> describe(double mwh) {
        List<Item> out = new ArrayList<>();
        if (mwh <= 0) return out;

        double phone = phoneCharges(mwh);
        if (phone >= 0.01) {
            out.add(new Item(String.format(java.util.Locale.US, "%.1f%% de uma carga de celular", phone * 100),
                    "Bateria de celular comum, cerca de 15 Wh."));
        }
        double watch = watchCharges(mwh);
        if (watch >= 0.01) {
            out.add(new Item(String.format(java.util.Locale.US, "%.0f%% de uma carga de smartwatch", watch * 100),
                    "Bateria de relógio inteligente, cerca de 1,2 Wh."));
        }
        double led = ledBulbMinutes(mwh);
        if (led >= 0.1) {
            out.add(new Item(formatMinutes(led) + " de uma lâmpada LED de 9 W acesa",
                    "Lâmpada doméstica comum."));
        }
        out.add(new Item(formatMinutes(indicatorLedMinutes(mwh)) + " de um LED indicador aceso",
                "LED de 20 mA, como o de um carregador."));
        return out;
    }

    public static String formatMinutes(double minutes) {
        if (minutes < 1) return String.format(java.util.Locale.US, "%.0f s", minutes * 60);
        if (minutes < 120) return String.format(java.util.Locale.US, "%.0f min", minutes);
        double hours = minutes / 60.0;
        if (hours < 48) return String.format(java.util.Locale.US, "%.1f h", hours);
        return String.format(java.util.Locale.US, "%.1f dias", hours / 24.0);
    }

    /**
     * Eficiência: mWh gerado por 1.000 passos. É a medida que deixa comparar
     * dias, terrenos e jeitos de pisar sem depender de quanto a pessoa andou.
     */
    public static double mwhPer1000Steps(double mwh, long steps) {
        if (steps <= 0) return 0;
        return mwh / steps * 1000.0;
    }
}
