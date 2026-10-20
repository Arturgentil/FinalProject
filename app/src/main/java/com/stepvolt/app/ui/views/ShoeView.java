package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Simplified redraw of the SVG sneaker used across ProfileScreen and
 * ShoeCustomizationScreen. Not a pixel-perfect port of the original SVG
 * paths, but the same silhouette + the same 4 customizable zones
 * (upper / sole / laces / details).
 */
public class ShoeView extends View {

    private int upperColor = Color.parseColor("#00F5A0");
    private int soleColor = Color.parseColor("#1A1A2E");
    private int lacesColor = Color.WHITE;
    private int detailsColor = Color.parseColor("#7C3AFF");

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShoeView(Context context) { super(context); }
    public ShoeView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setColors(String upper, String sole, String laces, String details) {
        try { upperColor = Color.parseColor(upper); } catch (Exception ignored) {}
        try { soleColor = Color.parseColor(sole); } catch (Exception ignored) {}
        try { lacesColor = Color.parseColor(laces); } catch (Exception ignored) {}
        try { detailsColor = Color.parseColor(details); } catch (Exception ignored) {}
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Design space is 240x160, scale to view bounds.
        float sx = w / 240f;
        float sy = h / 160f;
        canvas.save();
        canvas.scale(sx, sy);

        // Shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#33000000"));
        canvas.drawOval(40, 142, 200, 156, paint);

        // Sole
        Path sole = new Path();
        sole.moveTo(20, 110);
        sole.cubicTo(20, 110, 30, 132, 80, 136);
        sole.lineTo(200, 136);
        sole.cubicTo(200, 136, 220, 134, 222, 128);
        sole.lineTo(222, 110);
        sole.close();
        paint.setColor(soleColor);
        canvas.drawPath(sole, paint);

        // Upper body
        Path upper = new Path();
        upper.moveTo(18, 110);
        upper.cubicTo(18, 110, 24, 50, 80, 42);
        upper.cubicTo(116, 36, 152, 58, 186, 58);
        upper.lineTo(218, 58);
        upper.cubicTo(218, 58, 228, 64, 228, 80);
        upper.cubicTo(228, 96, 218, 110, 200, 110);
        upper.close();
        paint.setColor(upperColor);
        canvas.drawPath(upper, paint);

        // Toe cap shading
        Path toe = new Path();
        toe.moveTo(18, 110);
        toe.cubicTo(18, 110, 22, 90, 40, 78);
        toe.cubicTo(52, 70, 70, 72, 78, 80);
        toe.cubicTo(86, 88, 82, 110, 60, 110);
        toe.close();
        paint.setColor(Color.parseColor("#26000000"));
        canvas.drawPath(toe, paint);

        // Laces
        paint.setColor(lacesColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(75, 80, 75, 52, paint);
        canvas.drawLine(95, 74, 95, 48, paint);
        canvas.drawLine(115, 70, 115, 46, paint);
        canvas.drawLine(135, 66, 135, 48, paint);

        // Brand medallion + heel tab + energy dots (details color)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(detailsColor);
        canvas.drawCircle(185, 78, 13, paint);
        canvas.drawRoundRect(196, 68, 214, 98, 4, 4, paint);
        canvas.drawCircle(145, 95, 4, paint);
        paint.setAlpha(170);
        canvas.drawCircle(158, 95, 4, paint);
        paint.setAlpha(90);
        canvas.drawCircle(171, 95, 4, paint);
        paint.setAlpha(255);

        canvas.restore();
    }
}
