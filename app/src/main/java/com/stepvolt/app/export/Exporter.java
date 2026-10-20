package com.stepvolt.app.export;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.gps.GeoMath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Gravação de arquivos de exportação (CSV, GPX) e compartilhamento via FileProvider. */
public final class Exporter {

    private Exporter() {}

    public static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "exports");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    /** Escapa um campo CSV: aspas dobradas e campo entre aspas se tiver vírgula, aspas ou quebra de linha. */
    public static String csv(String v) {
        if (v == null) return "";
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public static File sessionsCsv(Context ctx, List<SessionRecord> sessions) throws IOException {
        File f = new File(dir(ctx), "stepvolt-treinos-" + stamp() + ".csv");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"))) {
            w.write("\uFEFF"); // BOM: o Excel abre com acentos certos
            w.write("inicio,fim,tipo,duracao_s,distancia_m,subida_m,passos,energia_mwh,bpm_medio,bpm_max,spo2_medio,tenis\n");
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            for (SessionRecord s : sessions) {
                w.write(iso.format(new Date(s.startedAt)) + ","
                        + (s.endedAt > 0 ? iso.format(new Date(s.endedAt)) : "") + ","
                        + csv(s.kind) + ","
                        + (Math.max(0, s.endedAt - s.startedAt) / 1000) + ","
                        + String.format(Locale.US, "%.1f", s.distanceM) + ","
                        + String.format(Locale.US, "%.1f", s.elevGainM) + ","
                        + s.steps + ","
                        + String.format(Locale.US, "%.2f", s.energy) + ","
                        + String.format(Locale.US, "%.0f", s.bpmAvg) + ","
                        + String.format(Locale.US, "%.0f", s.bpmMax) + ","
                        + String.format(Locale.US, "%.0f", s.spo2Avg) + ","
                        + csv(s.deviceName) + "\n");
            }
        }
        return f;
    }

    /** Rota no formato GPX 1.1, aceito por Strava, Garmin Connect e a maioria dos apps de corrida. */
    public static File routeGpx(Context ctx, SessionRecord s, List<GeoMath.Fix> route) throws IOException {
        File f = new File(dir(ctx), "stepvolt-rota-" + stamp() + ".gpx");
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"))) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<gpx version=\"1.1\" creator=\"StepVolt\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
            w.write("<trk><name>StepVolt " + iso.format(new Date(s.startedAt)) + "</name><trkseg>\n");
            for (GeoMath.Fix p : route) {
                w.write(String.format(Locale.US, "<trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon));
                if (!Double.isNaN(p.alt)) w.write(String.format(Locale.US, "<ele>%.1f</ele>", p.alt));
                w.write("<time>" + iso.format(new Date(p.ts)) + "</time></trkpt>\n");
            }
            w.write("</trkseg></trk></gpx>\n");
        }
        return f;
    }

    public static void share(Context ctx, File file, String mime, String title) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(i, title);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }
}
