package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.stepvolt.app.biomech.PressureAnalysis;

/**
 * Dois pés lado a lado, cada um dividido em antepé, meio e calcanhar, com a
 * cor de cada zona indo do azul (pouca carga) ao vermelho (muita carga).
 * Mostra a porcentagem da carga em cada zona.
 */
public class FootPressureView extends View {

    private PressureAnalysis.Foot left = new PressureAnalysis.Foot(0, 0, 0);
    private PressureAnalysis.Foot right = new PressureAnalysis.Foot(0, 0, 0);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public FootPressureView(Context c) { super(c); init(); }
    public FootPressureView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.5f * d);
        outline.setColor(0x33FFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12 * d);
        text.setFakeBoldText(true);
    }

    public void setFeet(PressureAnalysis.Foot left, PressureAnalysis.Foot right) {
        if (left != null) this.left = left;
        if (right != null) this.right = right;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (240 * getResources().getDisplayMetrics().density));
    }

    /** 0..1 -> azul, verde, âmbar, vermelho. */
    static int heat(float t) {
        t = Math.max(0f, Math.min(1f, t));
        int[] stops = {0xFF4CC9F0, 0xFF00F5A0, 0xFFFFB347, 0xFFFF3D71};
        float pos = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) pos);
        float f = pos - i;
        int a = stops[i], b = stops[i + 1];
        int rr = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int gg = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bb = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float footW = Math.min(w * 0.36f, 120 * d);
        float footH = h - 28 * d;
        float gap = 28 * d;
        float lx = w / 2f - gap / 2f - footW;
        float rx = w / 2f + gap / 2f;
        drawFoot(c, left, lx, 4 * d, footW, footH, d);
        drawFoot(c, right, rx, 4 * d, footW, footH, d);

        text.setColor(0xFF9A9AB8);
        c.drawText("Esquerdo", lx + footW / 2f, h - 6 * d, text);
        c.drawText("Direito", rx + footW / 2f, h - 6 * d, text);
    }

    private void drawFoot(Canvas c, PressureAnalysis.Foot f, float x, float y, float w, float h, float d) {
        float toeH = h * 0.36f, midH = h * 0.24f, heelH = h * 0.40f;
        float max = (float) Math.max(1.0, Math.max(f.toePct, Math.max(f.midPct, f.heelPct)));

        // Antepé (mais largo), meio (mais estreito), calcanhar (arredondado).
        r.set(x, y, x + w, y + toeH - 3 * d);
        zone(c, r, f, f.toePct, max, 28 * d);
        float mw = w * 0.78f;
        r.set(x + (w - mw) / 2f, y + toeH, x + (w + mw) / 2f, y + toeH + midH - 3 * d);
        zone(c, r, f, f.midPct, max, 14 * d);
        float hw = w * 0.82f;
        r.set(x + (w - hw) / 2f, y + toeH + midH, x + (w + hw) / 2f, y + h);
        zone(c, r, f, f.heelPct, max, 30 * d);
    }

    private void zone(Canvas c, RectF rect, PressureAnalysis.Foot f, double pct, float max, float radius) {
        if (!f.present) {
            fill.setColor(0x22FFFFFF);
        } else {
            fill.setColor(heat((float) (pct / max)));
        }
        c.drawRoundRect(rect, radius, radius, fill);
        c.drawRoundRect(rect, radius, radius, outline);
        if (f.present) {
            text.setColor(0xFF07070F);
            c.drawText(String.format(java.util.Locale.US, "%.0f%%", pct), rect.centerX(),
                    rect.centerY() + 4 * getResources().getDisplayMetrics().density, text);
        }
    }
}
