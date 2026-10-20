package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.EmergencyContact;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.safety.SosManager;
import com.stepvolt.app.ui.UiKit;

import java.util.List;

/**
 * Segurança e SOS: contatos de emergência, detecção de queda e envio de
 * pedido de ajuda com a localização.
 */
public class EmergencyFragment extends BaseScreenFragment {

    private static final int REQ_SMS = 4201;
    private static final int[] COUNTDOWNS = {15, 30, 60};

    private LinearLayout body;

    @Override protected String screenTitle() { return "Segurança e SOS"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 0);
        load();
    }

    private void load() {
        Repo.get(requireContext()).contacts.allAsync(list -> {
            if (!isAdded()) return;
            render(list);
        });
    }

    private void render(List<EmergencyContact> contacts) {
        final Context c = requireContext();
        body.removeAllViews();

        // Como funciona
        body.addView(UiKit.note(c, "Se o app (ou o tênis) detectar uma possível queda, abre uma contagem para você cancelar. "
                + "Se não cancelar, manda uma mensagem com sua localização aos contatos marcados. "
                + "O SOS depende do celular ter sinal e do GPS ligado para enviar a localização.", 0xFF4CC9F0),
                UiKit.matchWrap(c, 0));

        // Contatos
        body.addView(UiKit.sectionTitle(c, "Contatos de emergência"), UiKit.matchWrap(c, 20));
        if (contacts == null || contacts.isEmpty()) {
            body.addView(UiKit.emptyState(c, "Nenhum contato",
                    "Cadastre pelo menos uma pessoa para receber o SOS."), UiKit.matchWrap(c, 8));
        } else {
            for (EmergencyContact e : contacts) body.addView(contactRow(e), UiKit.matchWrap(c, 8));
        }
        body.addView(UiKit.primaryButton(c, "Adicionar contato", v -> editContact(null)), UiKit.matchWrap(c, 12));

        // Configurações
        body.addView(UiKit.sectionTitle(c, "Configurações"), UiKit.matchWrap(c, 24));
        body.addView(toggle("Detectar quedas",
                "Abre a contagem do SOS quando há um impacto forte seguido de imobilidade.",
                Prefs.fallDetect(c), on -> Prefs.setBool(requireContext(), Prefs.FALL_DETECT, on)), UiKit.matchWrap(c, 8));
        body.addView(toggle("Enviar localização",
                "Inclui um link do mapa com a última posição conhecida.",
                Prefs.sosSendLocation(c), on -> Prefs.setBool(requireContext(), Prefs.SOS_SEND_LOCATION, on)), UiKit.matchWrap(c, 8));

        body.addView(UiKit.caption(c, "Tempo para cancelar antes de enviar:"), UiKit.matchWrap(c, 14));
        int cur = Prefs.sosCountdown(c);
        int sel = 1;
        for (int i = 0; i < COUNTDOWNS.length; i++) if (COUNTDOWNS[i] == cur) sel = i;
        body.addView(UiKit.chips(c, new String[]{"15 s", "30 s", "60 s"}, sel,
                i -> Prefs.setInt(requireContext(), Prefs.SOS_COUNTDOWN_S, COUNTDOWNS[i])), UiKit.matchWrap(c, 8));

        // Envio direto de SMS
        boolean direct = SosManager.get(c).canSendSmsDirect();
        body.addView(UiKit.sectionTitle(c, "Envio da mensagem"), UiKit.matchWrap(c, 24));
        if (direct) {
            body.addView(UiKit.note(c, "O app envia o SMS sozinho, sem precisar abrir nada.", 0xFF00F5A0), UiKit.matchWrap(c, 8));
        } else {
            body.addView(UiKit.note(c, "Sem a permissão de SMS, o app abre o aplicativo de mensagens já preenchido e você precisa tocar em enviar. "
                    + "Para o envio automático, conceda a permissão. Pode haver cobrança de SMS pela operadora.", 0xFFFFB347),
                    UiKit.matchWrap(c, 8));
            body.addView(UiKit.ghostButton(c, "Permitir envio automático de SMS",
                    v -> requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQ_SMS)), UiKit.matchWrap(c, 8));
        }

        // Ações
        body.addView(UiKit.sectionTitle(c, "Ações"), UiKit.matchWrap(c, 24));
        body.addView(UiKit.ghostButton(c, "Testar a contagem do SOS",
                v -> testCountdown()), UiKit.matchWrap(c, 8));
        body.addView(UiKit.dangerButton(c, "Pedir ajuda agora", v -> confirmSendNow()), UiKit.matchWrap(c, 10));
        body.addView(UiKit.caption(c, "Em risco de vida, ligue 192 (SAMU) ou 190 (Polícia). O app não faz chamadas."),
                UiKit.matchWrap(c, 10));
    }

    private interface OnToggle { void on(boolean on); }

    private LinearLayout toggle(String title, String subtitle, boolean checked, final OnToggle cb) {
        Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        LinearLayout row = UiKit.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UiKit.label(c, title));
        t.addView(UiKit.caption(c, subtitle));
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> cb.on(on));
        row.addView(sw);
        p.addView(row);
        return p;
    }

    private LinearLayout contactRow(final EmergencyContact e) {
        Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        LinearLayout head = UiKit.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UiKit.label(c, e.name + (e.relation != null && !e.relation.isEmpty() ? " · " + e.relation : "")));
        t.addView(UiKit.caption(c, e.phone + (e.notifySos ? "" : " — não recebe o SOS")));
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(UiKit.ghostButton(c, "Editar", v -> editContact(e)));
        p.addView(head);
        return p;
    }

    private void editContact(final EmergencyContact existing) {
        final Context c = requireContext();
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(c, 20);
        form.setPadding(pad, UiKit.dp(c, 8), pad, 0);

        final EditText name = new EditText(c);
        name.setHint("Nome");
        final EditText phone = new EditText(c);
        phone.setHint("Telefone com DDD");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        final EditText relation = new EditText(c);
        relation.setHint("Relação (opcional)");
        final Switch notify = new Switch(c);
        notify.setText("Receber o SOS");
        notify.setChecked(true);
        form.addView(name); form.addView(phone); form.addView(relation); form.addView(notify);

        if (existing != null) {
            name.setText(existing.name);
            phone.setText(existing.phone);
            relation.setText(existing.relation);
            notify.setChecked(existing.notifySos);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(existing == null ? "Novo contato" : "Editar contato")
                .setView(form)
                .setPositiveButton("Salvar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String ph = phone.getText().toString().replaceAll("[^0-9+]", "");
                    if (n.isEmpty() || ph.length() < 8) {
                        toast("Informe o nome e um telefone válido.");
                        return;
                    }
                    EmergencyContact e = existing != null ? existing : new EmergencyContact();
                    e.name = n; e.phone = ph;
                    e.relation = relation.getText().toString().trim();
                    e.notifySos = notify.isChecked();
                    Repo.get(requireContext()).contacts.save(e, id -> { if (isAdded()) load(); });
                })
                .setNegativeButton("Cancelar", null);
        if (existing != null) {
            b.setNeutralButton("Excluir", (d, w) -> {
                Repo.get(requireContext()).contacts.delete(existing.id);
                load();
            });
        }
        b.show();
    }

    private void testCountdown() {
        if (SosManager.get(requireContext()).isCounting()) { toast("Já há uma contagem em andamento."); return; }
        new AlertDialog.Builder(requireContext())
                .setTitle("Testar o SOS")
                .setMessage("Vai iniciar a contagem de verdade. Se você não cancelar, a mensagem de SOS será ENVIADA aos seus contatos. Cancele logo que a contagem aparecer.")
                .setPositiveButton("Iniciar teste", (d, w) -> SosManager.get(requireContext()).startCountdown("teste do SOS"))
                .setNegativeButton("Voltar", null)
                .show();
    }

    private void confirmSendNow() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Pedir ajuda agora?")
                .setMessage("A mensagem de SOS vai para os contatos marcados, com sua localização.")
                .setPositiveButton("Enviar agora", (d, w) -> SosManager.get(requireContext()).sendNow("Pedido manual de ajuda"))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_SMS && isAdded()) load();
    }
}
