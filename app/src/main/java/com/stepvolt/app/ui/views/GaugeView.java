package com.stepvolt.app.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.stepvolt.app.util.Fmt;

/**
 * Mostrador em anel: o elemento de assinatura do app.
 *
 * Desenha um arco de 260°, com a faixa considerada habitual marcada por dois
 * traços finos, o valor no centro e um ponto na ponta do arco. Quando está no
 * modo "ao vivo", o anel respira devagar — é o único movimento contínuo da
 * interface, e existe para o usuário saber de relance que o dado é de agora.
 */
public class GaugeView extends View {

    private static final float START_ANGLE = 140f;
    private static final float SWEEP_ANGLE = 260f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private float minValue = 0f;
    private float maxValue = 100f;
    private float usualLow = Float.NaN;
    private float usualHigh = Float.NaN;

    private float value = 0f;
    private float drawnValue = 0f;
    private int decimals = 0;
    private String unit = "";
    private String label = "";
    private int accent = 0xFF00F5A0;
    private boolean live = false;
    private boolean hasValue = false;

    private float breath = 1f;
    private ValueAnimator breathAnimator;
    private ValueAnimator valueAnimator;

    public GaugeView(Context context) { this(context, null); }

    public GaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(10 * density);
        trackPaint.setColor(androidx.core.content.ContextCompat.getColor(getContext(), com.stepvolt.app.R.color.glass_border));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(10 * density);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(18 * density);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2 * density);
        tickPaint.setColor(0xFF3A3A5A);

        dotPaint.setStyle(Paint.Style.FILL);

        valuePaint.setColor(0xFFF2F2F7);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        unitPaint.setColor(0xFF9A9AB8);
        unitPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setColor(0xFF74748F);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // ---------------- configuração ----------------

    public void configure(String label, String unit, int accent, float min, float max, int decimals) {
        this.label = label == null ? "" : label;
        this.unit = unit == null ? "" : unit;
        this.accent = accent;
        this.minValue = min;
        this.maxValue = max;
        this.decimals = decimals;
        arcPaint.setColor(accent);
        glowPaint.setColor(withAlpha(accent, 40));
        dotPaint.setColor(accent);
        invalidate();
    }

    public void setUsualRange(float low, float high) {
        this.usualLow = low;
        this.usualHigh = high;
        invalidate();
    }

    /** Novo valor, com o arco subindo até ele em vez de pular. */
    public void setValue(float newValue, boolean animate) {
        this.value = newValue;
        this.hasValue = true;
        if (valueAnimator != null) valueAnimator.cancel();
        if (!animate) {
            drawnValue = newValue;
            invalidate();
            return;
        }
        valueAnimator = ValueAnimator.ofFloat(drawnValue, newValue);
        valueAnimator.setDuration(500);
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(a -> {
            drawnValue = (Float) a.getAnimatedValue();
            invalidate();
        });
        valueAnimator.start();
    }

    public void clearValue() {
        hasValue = false;
        value = 0;
        drawnValue = 0;
        invalidate();
    }

    /** Liga a respiração lenta do anel, usada enquanto o tênis está enviando dados. */
    public void setLive(boolean isLive) {
        if (this.live == isLive) return;
        this.live = isLive;
        if (isLive) {
            startBreathing();
        } else {
            stopBreathing();
        }
    }

    private void startBreathing() {
        stopBreathing();
        breathAnimator = ValueAnimator.ofFloat(1f, 1.05f, 1f);
        breathAnimator.setDuration(2600);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.addUpdateListener(a -> {
            breath = (Float) a.getAnimatedValue();
            invalidate();
        });
        breathAnimator.start();
    }

    private void stopBreathing() {
        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }
        breath = 1f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreathing();
        if (valueAnimator != null) valueAnimator.cancel();
        super.onDetachedFromWindow();
    }

    // ---------------- desenho ----------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int desired = w > 0 ? w : (int) (180 * getResources().getDisplayMetrics().density);
        int h = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float pad = 16 * density;
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - pad;
        if (radius <= 0) return;

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        drawUsualTicks(canvas, cx, cy, radius, density);

        float fraction = fractionOf(drawnValue);
        if (hasValue && fraction > 0.002f) {
            float sweep = SWEEP_ANGLE * fraction;
            if (live) {
                glowPaint.setStrokeWidth(18 * density * breath);
                canvas.drawArc(arcRect, START_ANGLE, sweep, false, glowPaint);
            }
            canvas.drawArc(arcRect, START_ANGLE, sweep, false, arcPaint);

            double angle = Math.toRadians(START_ANGLE + sweep);
            float dx = cx + (float) (radius * Math.cos(angle));
            float dy = cy + (float) (radius * Math.sin(angle));
            canvas.drawCircle(dx, dy, 5 * density * (live ? breath : 1f), dotPaint);
        }

        valuePaint.setTextSize(radius * 0.62f);
        unitPaint.setTextSize(radius * 0.19f);
        labelPaint.setTextSize(radius * 0.19f);

        String text = hasValue ? Fmt.decimals(value, decimals) : "—";
        canvas.drawText(text, cx, cy + radius * 0.18f, valuePaint);

        if (!unit.isEmpty()) {
            canvas.drawText(unit, cx, cy + radius * 0.46f, unitPaint);
        }
        if (!label.isEmpty()) {
            canvas.drawText(label, cx, cy - radius * 0.42f, labelPaint);
        }
    }

    private void drawUsualTicks(Canvas canvas, float cx, float cy, float radius, float density) {
        if (Float.isNaN(usualLow) || Float.isNaN(usualHigh)) return;
        drawTick(canvas, cx, cy, radius, density, fractionOf(usualLow));
        drawTick(canvas, cx, cy, radius, density, fractionOf(usualHigh));
    }

    private void drawTick(Canvas canvas, float cx, float cy, float radius, float density, float fraction) {
        if (fraction <= 0f || fraction >= 1f) return;
        double angle = Math.toRadians(START_ANGLE + SWEEP_ANGLE * fraction);
        float inner = radius - 10 * density;
        float outer = radius + 6 * density;
        canvas.drawLine(
                cx + (float) (inner * Math.cos(angle)), cy + (float) (inner * Math.sin(angle)),
                cx + (float) (outer * Math.cos(angle)), cy + (float) (outer * Math.sin(angle)),
                tickPaint);
    }

    private float fractionOf(float v) {
        if (maxValue <= minValue) return 0f;
        return Math.max(0f, Math.min(1f, (v - minValue) / (maxValue - minValue)));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
