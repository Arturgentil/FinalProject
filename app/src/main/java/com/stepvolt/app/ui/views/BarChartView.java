package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Gráfico de barras simples: um valor por rótulo, uma linha de meta opcional e
 * uma barra destacada. Sem eixos pesados — o valor de cada barra aparece em
 * cima quando há espaço.
 */
public class BarChartView extends View {

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int accent = 0xFF00F5A0;
    private int dim = 0x5500F5A0;
    private int textColor = 0xFF9A9AB8;
    private float goal = 0;
    private int highlight = -1;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public BarChartView(Context c) { super(c); init(); }
    public BarChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        text.setTextSize(10 * d);
        text.setTextAlign(Paint.Align.CENTER);
        goalLine.setStyle(Paint.Style.STROKE);
        goalLine.setStrokeWidth(1.5f * d);
        goalLine.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
    }

    public void setData(float[] values, String[] labels) {
        this.values = values == null ? new float[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        invalidate();
    }

    public void setAccent(int color) {
        this.accent = color;
        this.dim = (color & 0x00FFFFFF) | 0x55000000;
        invalidate();
    }

    public void setTextColor(int color) { this.textColor = color; invalidate(); }
    public void setGoal(float goal) { this.goal = goal; invalidate(); }
    public void setHighlight(int index) { this.highlight = index; invalidate(); }

    @Override
    protected void onMeasure(int w, int h) {
        int hs = MeasureSpec.getSize(h);
        int minH = (int) (140 * getResources().getDisplayMetrics().density);
        if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED || hs == 0) hs = minH;
        setMeasuredDimension(MeasureSpec.getSize(w), Math.max(hs, minH));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = values.length;
        if (n == 0) return;
        float d = getResources().getDisplayMetrics().density;

        float left = 4 * d, right = getWidth() - 4 * d;
        float top = 18 * d, bottom = getHeight() - 20 * d;
        float max = goal;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;

        float slot = (right - left) / n;
        float barW = Math.min(slot * 0.62f, 34 * d);

        for (int i = 0; i < n; i++) {
            float cx = left + slot * i + slot / 2f;
            float h = (bottom - top) * (values[i] / max);
            float y = bottom - Math.max(h, values[i] > 0 ? 2 * d : 0);
            bar.setColor(i == highlight || highlight < 0 ? accent : dim);
            rect.set(cx - barW / 2f, y, cx + barW / 2f, bottom);
            c.drawRoundRect(rect, 4 * d, 4 * d, bar);

            text.setColor(textColor);
            if (i < labels.length && labels[i] != null && (n <= 14 || i % 5 == 0 || i == n - 1)) {
                c.drawText(labels[i], cx, getHeight() - 5 * d, text);
            }
            if (n <= 8 && values[i] > 0) {
                c.drawText(shortNumber(values[i]), cx, y - 4 * d, text);
            }
        }

        if (goal > 0) {
            float gy = bottom - (bottom - top) * (goal / max);
            goalLine.setColor(0x99FFB347);
            c.drawLine(left, gy, right, gy, goalLine);
        }
    }

    private static String shortNumber(float v) {
        if (v >= 10000) return String.format(java.util.Locale.US, "%.0fk", v / 1000f);
        if (v >= 1000) return String.format(java.util.Locale.US, "%.1fk", v / 1000f);
        if (v >= 100) return String.format(java.util.Locale.US, "%.0f", v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
