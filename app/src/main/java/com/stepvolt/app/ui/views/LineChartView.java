package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal dependency-free line chart, used in place of the `recharts`
 * LineChart from the original screens (Dashboard heart-rate strip,
 * ActivityDetails BPM/SpO2 charts).
 */
public class LineChartView extends View {

    private final List<Float> data = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Float fixedMin = null;
    private Float fixedMax = null;

    public LineChartView(Context context) { super(context); init(); }
    public LineChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(Color.parseColor("#FF6B6B"));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#22FF6B6B"));
    }

    public void setLineColor(int color) {
        linePaint.setColor(color);
        fillPaint.setColor((color & 0x00FFFFFF) | 0x22000000);
        invalidate();
    }

    public void setBounds(float min, float max) {
        fixedMin = min;
        fixedMax = max;
        invalidate();
    }

    public void setData(List<Float> values) {
        data.clear();
        if (values != null) data.addAll(values);
        invalidate();
    }

    public void pushValue(float value, int maxPoints) {
        data.add(value);
        while (data.size() > maxPoints) data.remove(0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.size() < 2) return;

        float w = getWidth();
        float h = getHeight();
        float pad = 6f;

        float min = fixedMin != null ? fixedMin : java.util.Collections.min(data);
        float max = fixedMax != null ? fixedMax : java.util.Collections.max(data);
        if (max - min < 1f) { max += 1f; min -= 1f; }

        Path linePath = new Path();
        Path fillPath = new Path();
        int n = data.size();
        for (int i = 0; i < n; i++) {
            float x = pad + (w - 2 * pad) * i / (n - 1f);
            float norm = (data.get(i) - min) / (max - min);
            float y = pad + (h - 2 * pad) * (1f - norm);
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(w - pad, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}
