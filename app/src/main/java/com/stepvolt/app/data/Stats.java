package com.stepvolt.app.data;

import java.util.List;

/** Resumo estatístico de uma série. Usado nos cartões, na aba Saúde e na análise. */
public class Stats {

    public int count;
    public double avg;
    public double min;
    public double max;
    public double stdev;
    public double first;
    public double last;
    /** Inclinação da reta de tendência, em unidades por minuto. */
    public double slopePerMinute;

    public boolean isEmpty() { return count == 0; }

    /** Variação percentual do começo ao fim da janela. */
    public double changePct() {
        if (first == 0) return 0;
        return (last - first) / Math.abs(first) * 100.0;
    }

    public String trendLabel() {
        if (count < 4 || Math.abs(changePct()) < 3) return "estável";
        return changePct() > 0 ? "em alta" : "em queda";
    }

    public static Stats of(List<Point> points) {
        Stats s = new Stats();
        if (points == null || points.isEmpty()) return s;

        s.count = points.size();
        s.min = Double.MAX_VALUE;
        s.max = -Double.MAX_VALUE;
        double sum = 0;
        for (Point p : points) {
            sum += p.value;
            if (p.value < s.min) s.min = p.value;
            if (p.value > s.max) s.max = p.value;
        }
        s.avg = sum / s.count;
        s.first = points.get(0).value;
        s.last = points.get(s.count - 1).value;

        double varSum = 0;
        for (Point p : points) {
            double d = p.value - s.avg;
            varSum += d * d;
        }
        s.stdev = Math.sqrt(varSum / s.count);

        // Regressão linear simples sobre o tempo, para a inclinação da tendência.
        if (s.count >= 2) {
            double t0 = points.get(0).ts;
            double sx = 0, sy = 0, sxy = 0, sxx = 0;
            for (Point p : points) {
                double x = (p.ts - t0) / 60000.0; // minutos
                sx += x;
                sy += p.value;
                sxy += x * p.value;
                sxx += x * x;
            }
            double denom = s.count * sxx - sx * sx;
            s.slopePerMinute = Math.abs(denom) < 1e-9 ? 0 : (s.count * sxy - sx * sy) / denom;
        }
        return s;
    }
}
