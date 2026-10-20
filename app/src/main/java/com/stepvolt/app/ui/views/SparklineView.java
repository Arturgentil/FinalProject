package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Linha fina do fluxo ao vivo: curva suave, preenchimento em degradê e um ponto
 * na ponta direita marcando a leitura mais recente.
 *
 * A escala vertical se ajusta sozinha aos valores da janela, com uma margem de
 * folga, para que uma variação pequena não fique achatada nem uma grande saia
 * fora da área.
 */
public class SparklineView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private final List<Float> values = new ArrayList<>();
    private int capacity = 60;
    private int accent = 0xFF00F5A0;
    private boolean showDot = true;
    private float fixedMin = Float.NaN;
    private float fixedMax = Float.NaN;

    public SparklineView(Context context) { this(context, null); }

    public SparklineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.2f * d);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
        baselinePaint.setStyle(Paint.Style.STROKE);
        baselinePaint.setStrokeWidth(1f);
        baselinePaint.setColor(androidx.core.content.ContextCompat.getColor(getContext(), com.stepvolt.app.R.color.glass_border));
        setAccent(accent);
    }

    public void setAccent(int color) {
        this.accent = color;
        linePaint.setColor(color);
        dotPaint.setColor(color);
        invalidate();
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(4, capacity);
    }

    public void setShowDot(boolean show) {
        this.showDot = show;
    }

    /** Fixa a escala vertical; use quando a faixa da grandeza é conhecida. */
    public void setRange(float min, float max) {
        this.fixedMin = min;
        this.fixedMax = max;
        invalidate();
    }

    public void push(float value) {
        values.add(value);
        while (values.size() > capacity) values.remove(0);
        invalidate();
    }

    public void setValues(float[] data) {
        values.clear();
        if (data != null) {
            int start = Math.max(0, data.length - capacity);
            for (int i = start; i < data.length; i++) values.add(data[i]);
        }
        invalidate();
    }

    public void clear() {
        values.clear();
        invalidate();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float baseY = h - 1f;
        canvas.drawLine(0, baseY, w, baseY, baselinePaint);
        if (values.size() < 2) return;

        float min = Float.NaN, max = Float.NaN;
        if (!Float.isNaN(fixedMin) && !Float.isNaN(fixedMax)) {
            min = fixedMin;
            max = fixedMax;
        } else {
            min = Float.MAX_VALUE;
            max = -Float.MAX_VALUE;
            for (float v : values) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            float span = Math.max(1f, max - min);
            min -= span * 0.15f;
            max += span * 0.15f;
        }
        if (max - min < 0.0001f) max = min + 1f;

        float padTop = h * 0.08f;
        float usableH = h - padTop - 2f;
        float stepX = (float) w / (values.size() - 1);

        linePath.reset();
        fillPath.reset();

        float[] xs = new float[values.size()];
        float[] ys = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            xs[i] = i * stepX;
            float norm = (values.get(i) - min) / (max - min);
            ys[i] = padTop + usableH * (1f - Math.max(0f, Math.min(1f, norm)));
        }

        linePath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) {
            // Curva suave: pontos de controle no meio do caminho horizontal.
            float cx = (xs[i - 1] + xs[i]) / 2f;
            linePath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
        }

        fillPath.addPath(linePath);
        fillPath.lineTo(xs[xs.length - 1], h);
        fillPath.lineTo(xs[0], h);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, padTop, 0, h,
                withAlpha(accent, 60), withAlpha(accent, 0), Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        if (showDot) {
            float d = getResources().getDisplayMetrics().density;
            int last = xs.length - 1;
            dotPaint.setAlpha(60);
            canvas.drawCircle(xs[last], ys[last], 6 * d, dotPaint);
            dotPaint.setAlpha(255);
            canvas.drawCircle(xs[last], ys[last], 3 * d, dotPaint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
