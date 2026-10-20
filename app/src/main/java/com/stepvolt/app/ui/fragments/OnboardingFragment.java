package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Primeiro uso: explica o que o app faz, pede as permissões com o motivo de
 * cada uma, leva ao pareamento do tênis e oferece cadastrar um contato de
 * emergência. Tudo pode ser pulado e refeito depois.
 */
public class OnboardingFragment extends BaseScreenFragment {

    private static final int REQ_PERMS = 4001;
    private static final int STEPS = 4;

    private int step = 0;
    private LinearLayout body;

    @Override protected String screenTitle() { return "Bem-vindo ao StepVolt"; }

    @Override
    protected void buildContent() {
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 0);
        render();
    }

    private void render() {
        Context c = requireContext();
        body.removeAllViews();
        setSubtitle("Passo " + (step + 1) + " de " + STEPS);

        switch (step) {
            case 0: welcome(c); break;
            case 1: permissions(c); break;
            case 2: pairing(c); break;
            default: safety(c); break;
        }

        LinearLayout nav = UiKit.row(c);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(UiKit.ghostButton(c, "Pular tudo", v -> finish(null)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = UiKit.dp(c, 10);
        if (step < STEPS - 1) {
            nav.addView(UiKit.primaryButton(c, "Continuar", v -> { step++; render(); }), lp);
        } else {
            nav.addView(UiKit.primaryButton(c, "Concluir", v -> finish(null)), lp);
        }
        body.addView(nav, UiKit.matchWrap(c, 28));
    }

    private void welcome(Context c) {
        body.addView(UiKit.sectionTitle(c, "Seu tênis gera energia"));
        body.addView(UiKit.caption(c, "O StepVolt lê o que o tênis mede — passos, energia gerada, batimentos, oxigenação e como você pisa — "
                + "e transforma em histórico, metas e avisos."), UiKit.matchWrap(c, 8));
        body.addView(UiKit.sectionTitle(c, "O que você vai poder fazer"), UiKit.matchWrap(c, 20));
        String[] items = {
                "Treinar com GPS e ver rota, ritmo e energia gerada",
                "Acompanhar a energia por dia e o que ela representa",
                "Ver como a carga se distribui nos seus pés",
                "Receber um plano semanal feito com o seu histórico",
                "Ter um botão de SOS e detecção de queda"};
        for (String it : items) body.addView(UiKit.caption(c, "•  " + it), UiKit.matchWrap(c, 6));
        body.addView(UiKit.note(c, "O app não é um aparelho médico. Os números são estimativas para você se acompanhar.", 0xFF4CC9F0),
                UiKit.matchWrap(c, 20));
    }

    // ------------------------------------------------------------------
    // Permissões
    // ------------------------------------------------------------------

    private List<String[]> permissionList() {
        List<String[]> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(new String[]{Manifest.permission.BLUETOOTH_SCAN, "Bluetooth: procurar o tênis", "Para achar o tênis perto do celular."});
            l.add(new String[]{Manifest.permission.BLUETOOTH_CONNECT, "Bluetooth: conectar", "Para receber as medições do tênis."});
        }
        l.add(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, "Localização",
                "Para gravar a rota do treino e, no Android 11 ou menor, para o Bluetooth achar o tênis. Só é usada durante o treino e no SOS."});
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(new String[]{Manifest.permission.POST_NOTIFICATIONS, "Notificações", "Para avisar de saúde, bateria baixa e do treino em andamento."});
        }
        return l;
    }

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED;
    }

    private void permissions(Context c) {
        body.addView(UiKit.sectionTitle(c, "Permissões"));
        body.addView(UiKit.caption(c, "Cada uma tem um motivo. Você pode negar e usar o resto do app, e mudar depois nos ajustes do celular."),
                UiKit.matchWrap(c, 6));
        final List<String> missing = new ArrayList<>();
        for (String[] p : permissionList()) {
            LinearLayout row = UiKit.panel(c);
            boolean ok = granted(p[0]);
            row.addView(UiKit.keyValue(c, p[1], ok ? "concedida" : "pendente"));
            row.addView(UiKit.caption(c, p[2]), UiKit.matchWrap(c, 4));
            body.addView(row, UiKit.matchWrap(c, 10));
            if (!ok) missing.add(p[0]);
        }
        if (!missing.isEmpty()) {
            body.addView(UiKit.primaryButton(c, "Permitir", v ->
                    requestPermissions(missing.toArray(new String[0]), REQ_PERMS)), UiKit.matchWrap(c, 16));
        } else {
            body.addView(UiKit.note(c, "Tudo certo com as permissões.", 0xFF00F5A0), UiKit.matchWrap(c, 16));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_PERMS && isAdded()) render();
    }

    // ------------------------------------------------------------------
    // Pareamento e segurança
    // ------------------------------------------------------------------

    private void pairing(Context c) {
        body.addView(UiKit.sectionTitle(c, "Conectar o tênis"));
        body.addView(UiKit.caption(c, "Ligue o tênis, deixe-o perto do celular e toque abaixo. O app procura e conecta. "
                + "Você pode fazer isso depois, na aba do tênis."), UiKit.matchWrap(c, 6));
        body.addView(UiKit.primaryButton(c, "Conectar agora", v -> finish(Navigator.DEVICE)), UiKit.matchWrap(c, 18));
        body.addView(UiKit.caption(c, "Sem o tênis por perto, toque em Continuar."), UiKit.matchWrap(c, 10));
    }

    private void safety(Context c) {
        body.addView(UiKit.sectionTitle(c, "Segurança"));
        body.addView(UiKit.caption(c, "Cadastre pelo menos uma pessoa de confiança. Se o app detectar uma possível queda e você não cancelar a contagem, "
                + "ela recebe uma mensagem com a sua localização."), UiKit.matchWrap(c, 6));
        body.addView(UiKit.primaryButton(c, "Cadastrar contato de emergência", v -> finish(Navigator.EMERGENCY)), UiKit.matchWrap(c, 18));
        body.addView(UiKit.caption(c, "Dá para fazer isso depois em Segurança e SOS."), UiKit.matchWrap(c, 10));
    }

    private void finish(String thenGo) {
        Prefs.setBool(requireContext(), Prefs.ONBOARDING_DONE, true);
        // Volta ao início antes de abrir o destino, para o "voltar" do destino não cair de novo aqui.
        nav().goBack();
        if (thenGo != null) nav().navigate(thenGo);
    }
}
