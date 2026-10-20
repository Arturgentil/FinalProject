package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Barras de um período (horas do dia, dias da semana). Cada barra guarda um
 * rótulo curto; a de maior valor fica em cor cheia e as demais em tom apagado,
 * para o olho achar o pico sem precisar de legenda.
 */
public class BarsView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int accent = 0xFF00F5A0;
    private int highlightIndex = -1;

    public BarsView(Context context) { this(context, null); }

    public BarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0xFF5C5C7A);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(10 * getResources().getDisplayMetrics().density);
    }

    public void setAccent(int color) {
        this.accent = color;
        invalidate();
    }

    public void setData(float[] values, String[] labels) {
        this.values = values == null ? new float[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        highlightIndex = -1;
        float best = -Float.MAX_VALUE;
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] > best) {
                best = this.values[i];
                highlightIndex = i;
            }
        }
        invalidate();
    }

    public boolean isEmpty() {
        if (values.length == 0) return true;
        for (float v : values) if (v > 0) return false;
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || values.length == 0) return;

        float density = getResources().getDisplayMetrics().density;
        float labelH = labels.length > 0 ? 16 * density : 0;
        float chartH = h - labelH;
        float gap = 3 * density;
        float barW = (w - gap * (values.length - 1)) / (float) values.length;
        if (barW <= 0) return;

        float max = 0;
        for (float v : values) if (v > max) max = v;
        if (max <= 0) max = 1;

        float radius = Math.min(barW / 2f, 5 * density);

        for (int i = 0; i < values.length; i++) {
            float x = i * (barW + gap);
            float barH = Math.max(2 * density, chartH * (values[i] / max));
            rect.set(x, chartH - barH, x + barW, chartH);
            barPaint.setColor(i == highlightIndex ? accent : withAlpha(accent, 70));
            canvas.drawRoundRect(rect, radius, radius, barPaint);

            if (i < labels.length && labels[i] != null) {
                canvas.drawText(labels[i], x + barW / 2f, h - 3 * density, labelPaint);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
