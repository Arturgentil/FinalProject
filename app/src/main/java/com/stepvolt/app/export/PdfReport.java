package com.stepvolt.app.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.util.Fmt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** PDF simples (A4) gerado no aparelho, sem biblioteca externa. */
public final class PdfReport {

    private PdfReport() {}

    private static final int W = 595, H = 842, M = 40;

    private static final class Pen {
        final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint h2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint route = new Paint(Paint.ANTI_ALIAS_FLAG);

        Pen() {
            title.setTextSize(22); title.setTypeface(Typeface.DEFAULT_BOLD); title.setColor(0xFF111111);
            h2.setTextSize(14); h2.setTypeface(Typeface.DEFAULT_BOLD); h2.setColor(0xFF1F3A6E);
            body.setTextSize(11); body.setColor(0xFF222222);
            small.setTextSize(9); small.setColor(0xFF777777);
            line.setColor(0xFFCCCCCC); line.setStrokeWidth(0.8f);
            route.setStyle(Paint.Style.STROKE); route.setStrokeWidth(2f); route.setColor(0xFF00A86B);
            route.setStrokeJoin(Paint.Join.ROUND); route.setStrokeCap(Paint.Cap.ROUND);
        }
    }

    public static File sessionReport(Context ctx, SessionRecord s, List<GeoMath.Fix> routePts) throws IOException {
        PdfDocument doc = new PdfDocument();
        Pen p = new Pen();
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        Canvas c = page.getCanvas();
        float y = M + 10;

        c.drawText("Resumo do treino — StepVolt", M, y, p.title);
        y += 18;
        c.drawText(Fmt.date(s.startedAt) + " às " + Fmt.clock(s.startedAt), M, y, p.small);
        y += 22;
        c.drawLine(M, y, W - M, y, p.line);
        y += 22;

        long dur = Math.max(0, s.endedAt - s.startedAt);
        double km = s.distanceM / 1000.0;
        String[][] rows = {
                {"Distância", s.distanceM >= 10 ? String.format(Locale.US, "%.2f km", km) : "sem GPS"},
                {"Duração", Fmt.duration(dur)},
                {"Ritmo médio", s.distanceM >= 100 && dur > 0 ? GeoMath.formatPace((dur / 1000.0) / km) + " /km" : "—"},
                {"Passos", String.valueOf(s.steps)},
                {"Energia gerada", String.format(Locale.US, "%.1f mWh", s.energy)},
                {"Energia por km", GeoMath.mwhPerKm(s.energy, s.distanceM) > 0 ? String.format(Locale.US, "%.1f mWh/km", GeoMath.mwhPerKm(s.energy, s.distanceM)) : "—"},
                {"Batimentos (média / máx.)", s.bpmAvg > 0 ? String.format(Locale.US, "%.0f / %.0f bpm", s.bpmAvg, s.bpmMax) : "—"},
                {"Subida acumulada", s.elevGainM > 0 ? String.format(Locale.US, "%.0f m", s.elevGainM) : "—"},
        };
        for (String[] r : rows) {
            c.drawText(r[0], M, y, p.body);
            c.drawText(r[1], M + 220, y, p.body);
            y += 18;
        }

        y += 14;
        c.drawText("Rota", M, y, p.h2);
        y += 10;
        drawRoute(c, p, routePts, M, y, W - 2 * M, 300);
        y += 320;

        c.drawText("Gerado pelo StepVolt. Os valores vêm do tênis e do GPS do celular e são estimativas, não medições clínicas.", M, H - M, p.small);
        doc.finishPage(page);
        return save(ctx, doc, "stepvolt-treino-");
    }

    public static File periodReport(Context ctx, String title, List<DayStat> days, List<SessionRecord> sessions) throws IOException {
        PdfDocument doc = new PdfDocument();
        Pen p = new Pen();
        int pageNo = 1;
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        Canvas c = page.getCanvas();
        float y = M + 10;

        c.drawText("Relatório StepVolt", M, y, p.title);
        y += 18;
        c.drawText(title, M, y, p.small);
        y += 22;
        c.drawLine(M, y, W - M, y, p.line);
        y += 22;

        long steps = 0; double energy = 0, dist = 0; long dur = 0; int sess = 0;
        for (DayStat d : days) { steps += d.steps; energy += d.energy; dist += d.distanceM; dur += d.durationMs; sess += d.sessions; }
        String[][] totals = {
                {"Passos", String.valueOf(steps)},
                {"Energia gerada", String.format(Locale.US, "%.1f mWh", energy)},
                {"Distância com GPS", String.format(Locale.US, "%.2f km", dist / 1000.0)},
                {"Tempo em treino", Fmt.duration(dur)},
                {"Treinos", String.valueOf(sess)},
        };
        for (String[] r : totals) {
            c.drawText(r[0], M, y, p.body);
            c.drawText(r[1], M + 220, y, p.body);
            y += 18;
        }

        y += 16;
        c.drawText("Treinos", M, y, p.h2);
        y += 18;
        for (SessionRecord s : sessions) {
            if (y > H - M - 30) {
                doc.finishPage(page);
                page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++pageNo).create());
                c = page.getCanvas();
                y = M + 10;
            }
            String line = Fmt.date(s.startedAt) + " " + Fmt.clock(s.startedAt)
                    + "  ·  " + Fmt.duration(Math.max(0, s.endedAt - s.startedAt))
                    + "  ·  " + (s.distanceM >= 10 ? String.format(Locale.US, "%.2f km", s.distanceM / 1000.0) : "sem GPS")
                    + "  ·  " + s.steps + " passos  ·  " + String.format(Locale.US, "%.1f mWh", s.energy);
            c.drawText(line, M, y, p.body);
            y += 16;
        }
        if (sessions.isEmpty()) c.drawText("Nenhum treino no período.", M, y, p.body);

        c.drawText("Gerado pelo StepVolt. Valores estimados, sem finalidade clínica.", M, H - M + 10, p.small);
        doc.finishPage(page);
        return save(ctx, doc, "stepvolt-relatorio-");
    }

    private static void drawRoute(Canvas c, Pen p, List<GeoMath.Fix> pts, float x, float y, float w, float h) {
        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(0xFFCCCCCC);
        c.drawRect(x, y, x + w, y + h, frame);

        java.util.ArrayList<GeoMath.Fix> ok = new java.util.ArrayList<>();
        if (pts != null) for (GeoMath.Fix f : pts) if (f.accuracy <= GeoMath.MAX_ACCURACY_M) ok.add(f);
        if (ok.size() < 2) {
            c.drawText("Sem rota gravada", x + 12, y + 22, p.small);
            return;
        }
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (GeoMath.Fix f : ok) {
            minLat = Math.min(minLat, f.lat); maxLat = Math.max(maxLat, f.lat);
            minLon = Math.min(minLon, f.lon); maxLon = Math.max(maxLon, f.lon);
        }
        double lonScale = Math.cos(Math.toRadians((minLat + maxLat) / 2.0));
        double spanX = Math.max((maxLon - minLon) * lonScale, 1e-6);
        double spanY = Math.max(maxLat - minLat, 1e-6);
        float pad = 16;
        double scale = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
        float offX = (float) (x + pad + ((w - 2 * pad) - spanX * scale) / 2.0);
        float offY = (float) (y + pad + ((h - 2 * pad) - spanY * scale) / 2.0);

        Path path = new Path();
        for (int i = 0; i < ok.size(); i++) {
            GeoMath.Fix f = ok.get(i);
            float px = (float) (offX + (f.lon - minLon) * lonScale * scale);
            float py = (float) (offY + (maxLat - f.lat) * scale);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        c.drawPath(path, p.route);
    }

    private static File save(Context ctx, PdfDocument doc, String prefix) throws IOException {
        File f = new File(Exporter.dir(ctx), prefix + Exporter.stamp() + ".pdf");
        try (FileOutputStream out = new FileOutputStream(f)) {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
        return f;
    }
}
