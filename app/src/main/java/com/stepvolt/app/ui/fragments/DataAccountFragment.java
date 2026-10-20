package com.stepvolt.app.ui.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.Backup;
import com.stepvolt.app.data.DailyDao;
import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.export.Exporter;
import com.stepvolt.app.export.PdfReport;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.OkResponse;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dados e conta: exportar (CSV, PDF, GPX), backup e restauração em JSON,
 * sincronização com o servidor e verificação do e-mail.
 */
public class DataAccountFragment extends BaseScreenFragment {

    private static final String[] PERIODS = {"7 dias", "30 dias", "90 dias", "Tudo"};
    private static final long[] PERIOD_MS = {7L, 30L, 90L, 36500L};
    private static final int REQ_RESTORE = 4301;

    private int period = 1;

    @Override protected String screenTitle() { return "Dados e conta"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        Context c = requireContext();

        // ---- Exportar
        addBlock(UiKit.sectionTitle(c, "Exportar"), 0);
        addBlock(UiKit.chips(c, PERIODS, period, i -> period = i), 10);
        addBlock(UiKit.ghostButton(c, "Treinos em planilha (CSV)", v -> exportSessionsCsv()), 12);
        addBlock(UiKit.ghostButton(c, "Relatório do período em PDF", v -> exportPeriodPdf()), 8);
        addBlock(UiKit.ghostButton(c, "Rota do último treino com GPS (GPX)", v -> exportLastGpx()), 8);
        addBlock(UiKit.ghostButton(c, "Medições brutas em CSV", v -> exportRawCsv()), 8);
        addBlock(UiKit.caption(c, "O GPX abre no Strava, Garmin Connect e na maioria dos apps de corrida."), 6);

        // ---- Backup
        addBlock(UiKit.sectionTitle(c, "Backup"), 26);
        addBlock(UiKit.caption(c, "Guarda treinos, resumo por dia, tênis, contatos de emergência e metas num arquivo. "
                + "Não inclui senha nem medições brutas. Restaurar soma ao que já existe, sem apagar nada."), 6);
        addBlock(UiKit.primaryButton(c, "Criar backup", v -> createBackup()), 12);
        addBlock(UiKit.ghostButton(c, "Restaurar de um arquivo", v -> pickBackup()), 8);

        // ---- Conta
        addBlock(UiKit.sectionTitle(c, "Conta e sincronização"), 26);
        if (!SessionManager.isLoggedIn(c)) {
            addBlock(UiKit.caption(c, "Você não está conectado a uma conta. Os dados ficam só neste aparelho."), 6);
            return;
        }
        long last = Prefs.getLong(c, Prefs.ACTIVITIES_SYNCED_AT, 0);
        addBlock(UiKit.keyValue(c, "Última sincronização", last > 0 ? Fmt.date(last) + " " + Fmt.clock(last) : "ainda não"), 8);
        addBlock(UiKit.primaryButton(c, "Sincronizar agora", v -> syncNow()), 12);

        if (app().user != null && !app().user.emailVerified) {
            addBlock(UiKit.note(c, "Seu e-mail ainda não foi confirmado. Confirmar protege sua conta e permite recuperar a senha.", 0xFFFFB347), 18);
            addBlock(UiKit.ghostButton(c, "Reenviar e-mail de confirmação", v -> resendVerification()), 8);
        } else {
            addBlock(UiKit.keyValue(c, "E-mail", "confirmado"), 14);
        }
    }

    // ------------------------------------------------------------------
    // Exportar
    // ------------------------------------------------------------------

    private long from() {
        return System.currentTimeMillis() - PERIOD_MS[period] * 86_400_000L;
    }

    private List<SessionRecord> inPeriod(List<SessionRecord> all) {
        List<SessionRecord> out = new ArrayList<>();
        long f = from();
        if (all != null) for (SessionRecord s : all) if (s.startedAt >= f) out.add(s);
        return out;
    }

    private void exportSessionsCsv() {
        final Context app = requireContext().getApplicationContext();
        Repo.get(app).call(() -> Exporter.sessionsCsv(app, inPeriod(Repo.get(app).sessions(100000))), file -> {
            if (!isAdded()) return;
            if (file == null) { toast("Não foi possível gerar o arquivo."); return; }
            Exporter.share(requireContext(), file, "text/csv", "Treinos em CSV");
        });
    }

    private void exportPeriodPdf() {
        final Context app = requireContext().getApplicationContext();
        final long f = from();
        Repo.get(app).call(() -> {
            List<DayStat> days = Repo.get(app).daily.range(f, System.currentTimeMillis());
            List<SessionRecord> sessions = inPeriod(Repo.get(app).sessions(100000));
            return PdfReport.periodReport(app, "Período: " + PERIODS[period] + " (até " + Fmt.date(System.currentTimeMillis()) + ")", days, sessions);
        }, file -> {
            if (!isAdded()) return;
            if (file == null) { toast("Não foi possível gerar o PDF."); return; }
            Exporter.share(requireContext(), file, "application/pdf", "Relatório StepVolt");
        });
    }

    private void exportLastGpx() {
        final Context app = requireContext().getApplicationContext();
        Repo.get(app).call(() -> {
            for (SessionRecord s : Repo.get(app).sessions(200)) {
                List<GeoMath.Fix> route = Repo.get(app).route.points(s.id);
                if (route.size() >= 2) return Exporter.routeGpx(app, s, route);
            }
            return (File) null;
        }, file -> {
            if (!isAdded()) return;
            if (file == null) { toast("Nenhum treino com rota de GPS encontrado."); return; }
            Exporter.share(requireContext(), file, "application/gpx+xml", "Rota GPX");
        });
    }

    private void exportRawCsv() {
        final Context app = requireContext().getApplicationContext();
        Repo.get(app).exportCsv(Exporter.dir(app), from(), System.currentTimeMillis(), file -> {
            if (!isAdded()) return;
            if (file == null) { toast("Não há medições no período."); return; }
            Exporter.share(requireContext(), file, "text/csv", "Medições em CSV");
        });
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    private void createBackup() {
        final Context app = requireContext().getApplicationContext();
        Repo.get(app).call(() -> Backup.write(app), file -> {
            if (!isAdded()) return;
            if (file == null) { toast("Não foi possível criar o backup."); return; }
            Exporter.share(requireContext(), file, "application/json", "Backup StepVolt");
        });
    }

    private void pickBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_RESTORE);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_RESTORE || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final Context app = requireContext().getApplicationContext();
        Repo.get(app).call(() -> {
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                return Backup.restore(app, in).text();
            } catch (Exception e) {
                return "ERRO: " + e.getMessage();
            }
        }, text -> {
            if (!isAdded()) return;
            boolean error = text == null || text.startsWith("ERRO");
            new AlertDialog.Builder(requireContext())
                    .setTitle(error ? "Não foi possível restaurar" : "Backup restaurado")
                    .setMessage(text == null ? "Falha inesperada." : text.replace("ERRO: ", ""))
                    .setPositiveButton("Ok", null).show();
        });
    }

    // ------------------------------------------------------------------
    // Conta
    // ------------------------------------------------------------------

    private void syncNow() {
        telemetry().syncPendingSessions();
        app().refreshActivities(true, (success, data, error) -> {
            if (!isAdded()) return;
            toast(success ? "Sincronizado." : (error == null ? "Falha ao sincronizar." : error));
        });
    }

    private void resendVerification() {
        ApiClient.get(requireContext()).resendVerification().enqueue(new Callback<OkResponse>() {
            @Override public void onResponse(Call<OkResponse> call, Response<OkResponse> r) {
                if (!isAdded()) return;
                toast(r.isSuccessful() ? "E-mail enviado. Olhe também a caixa de spam." : ApiUtil.parseError(r));
            }
            @Override public void onFailure(Call<OkResponse> call, Throwable t) { if (isAdded()) toast("Sem conexão."); }
        });
    }
}
