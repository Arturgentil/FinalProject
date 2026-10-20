package com.stepvolt.app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.stepvolt.app.gps.GeoMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Desenha a rota como uma linha, sem mapa de fundo.
 *
 * Mapa com ruas exige uma chave de serviço de mapas e internet; a rota em si
 * já responde o que a pessoa quer ver depois de treinar (por onde passou, onde
 * começou e terminou) e funciona offline. Pontos de baixa precisão não entram
 * no desenho.
 */
public class RouteMapView extends View {

    private List<GeoMath.Fix> fixes = new ArrayList<>();
    private int accent = 0xFF00F5A0;
    private String emptyText = "Sem rota gravada";

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public RouteMapView(Context c) { super(c); init(); }
    public RouteMapView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3.5f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f * d);
        grid.setColor(0x22FFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(13 * d);
        text.setColor(0xFF74748F);
    }

    public void setRoute(List<GeoMath.Fix> fixes) {
        this.fixes = fixes == null ? new ArrayList<GeoMath.Fix>() : fixes;
        invalidate();
    }

    public void setAccent(int color) { this.accent = color; invalidate(); }
    public void setEmptyText(String t) { this.emptyText = t; invalidate(); }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int minH = (int) (200 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(width, minH);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();

        // Grade leve, só para dar noção de escala de tela.
        for (int i = 1; i < 4; i++) {
            c.drawLine(w * i / 4f, 0, w * i / 4f, h, grid);
            c.drawLine(0, h * i / 4f, w, h * i / 4f, grid);
        }

        List<GeoMath.Fix> pts = new ArrayList<>();
        for (GeoMath.Fix f : fixes) if (f.accuracy <= GeoMath.MAX_ACCURACY_M) pts.add(f);
        if (pts.size() < 2) {
            c.drawText(emptyText, w / 2f, h / 2f, text);
            return;
        }

        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (GeoMath.Fix f : pts) {
            minLat = Math.min(minLat, f.lat); maxLat = Math.max(maxLat, f.lat);
            minLon = Math.min(minLon, f.lon); maxLon = Math.max(maxLon, f.lon);
        }
        // Corrige o achatamento da longitude para a rota não ficar esticada.
        double midLat = (minLat + maxLat) / 2.0;
        double lonScale = Math.cos(Math.toRadians(midLat));
        double spanX = Math.max((maxLon - minLon) * lonScale, 1e-6);
        double spanY = Math.max(maxLat - minLat, 1e-6);

        float pad = 22 * d;
        float availW = w - 2 * pad, availH = h - 2 * pad;
        double scale = Math.min(availW / spanX, availH / spanY);
        float offX = (float) (pad + (availW - spanX * scale) / 2.0);
        float offY = (float) (pad + (availH - spanY * scale) / 2.0);

        path.reset();
        float sx = 0, sy = 0, ex = 0, ey = 0;
        for (int i = 0; i < pts.size(); i++) {
            GeoMath.Fix f = pts.get(i);
            float x = (float) (offX + (f.lon - minLon) * lonScale * scale);
            float y = (float) (offY + (maxLat - f.lat) * scale);
            if (i == 0) { path.moveTo(x, y); sx = x; sy = y; } else path.lineTo(x, y);
            ex = x; ey = y;
        }
        line.setColor(accent);
        c.drawPath(path, line);

        dot.setColor(0xFFFFFFFF);
        c.drawCircle(sx, sy, 6 * d, dot);
        dot.setColor(0xFF07070F);
        c.drawCircle(sx, sy, 3 * d, dot);
        dot.setColor(accent);
        c.drawCircle(ex, ey, 7 * d, dot);
    }
}
