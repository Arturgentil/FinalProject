package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.ble.BleEvent;
import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.List;

/**
 * Ajuda e diagnóstico: estado da conexão com o tênis, qualidade do enlace,
 * permissões, log do Bluetooth e respostas para os problemas mais comuns.
 * O relatório pode ser copiado ou compartilhado para quem for ajudar.
 */
public class HelpFragment extends BaseScreenFragment {

    @Override protected String screenTitle() { return "Ajuda e diagnóstico"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        Context c = requireContext();
        BleManager ble = app().bleManager;

        // ---- Estado
        addBlock(UiKit.sectionTitle(c, "Conexão com o tênis"), 0);
        LinearLayout p = UiKit.panel(c);
        p.addView(UiKit.keyValue(c, "Estado", ble.getState().name()));
        p.addView(UiKit.keyValue(c, "Tênis", ble.getConnectedDeviceName() == null ? "—" : ble.getConnectedDeviceName()), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Firmware", ble.getFirmwareVersion() == null ? "não informado" : ble.getFirmwareVersion()), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Protocolo", ble.isConnected() ? "v" + ble.getProtocolVersion()
                + (ble.getProtocolVersion() >= 2 ? " (com checksum e confirmação)" : " (antigo, sem checksum)") : "—"), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Pacotes recebidos", Fmt.intGrouped(ble.getPacketCount())), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Pacotes descartados", Fmt.intGrouped(ble.getInvalidPackets())), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Pacotes perdidos", Fmt.intGrouped(ble.getLostPackets())), UiKit.matchWrap(c, 6));
        p.addView(UiKit.keyValue(c, "Perda do enlace", Fmt.decimals(ble.getPacketLossPct(), 1) + "%"), UiKit.matchWrap(c, 6));
        addBlock(p, 10);

        // ---- Permissões
        addBlock(UiKit.sectionTitle(c, "Permissões"), 22);
        LinearLayout perms = UiKit.panel(c);
        perms.addView(permRow("Localização (GPS e busca Bluetooth)", Manifest.permission.ACCESS_FINE_LOCATION));
        if (Build.VERSION.SDK_INT >= 31) {
            perms.addView(permRow("Bluetooth (conectar)", Manifest.permission.BLUETOOTH_CONNECT), UiKit.matchWrap(c, 6));
            perms.addView(permRow("Bluetooth (procurar)", Manifest.permission.BLUETOOTH_SCAN), UiKit.matchWrap(c, 6));
        }
        if (Build.VERSION.SDK_INT >= 33) perms.addView(permRow("Notificações", Manifest.permission.POST_NOTIFICATIONS), UiKit.matchWrap(c, 6));
        perms.addView(permRow("SMS (envio automático do SOS)", Manifest.permission.SEND_SMS), UiKit.matchWrap(c, 6));
        addBlock(perms, 10);
        addBlock(UiKit.ghostButton(c, "Abrir ajustes do app", v -> {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(i);
        }), 10);

        // ---- Testes no tênis
        if (ble.isConnected()) {
            addBlock(UiKit.sectionTitle(c, "Testes no tênis"), 22);
            addBlock(UiKit.ghostButton(c, "Pedir o estado ao tênis", v -> test("STATUS")), 8);
            addBlock(UiKit.ghostButton(c, "Piscar o LED", v -> test("BLINK")), 8);
        }

        // ---- Problemas comuns
        addBlock(UiKit.sectionTitle(c, "Problemas comuns"), 22);
        faq("O tênis não aparece na busca",
                "Confira se o tênis está ligado e perto do celular, se o Bluetooth e a localização do celular estão ligados e se o app tem as permissões acima. "
                        + "Se ele estava ligado a outro celular, desconecte lá primeiro.");
        faq("A conexão cai com a tela apagada",
                "Comece um treino: durante o treino o app mantém a conexão em primeiro plano. Em alguns aparelhos, também é preciso tirar o StepVolt da economia de bateria nos ajustes do sistema.");
        faq("A distância do treino está zerada",
                "A distância vem do GPS do celular. Ative “Gravar rota com GPS” ao começar o treino, deixe a localização ligada e conceda a permissão. Sob prédios ou árvores o sinal pode ficar fraco.");
        faq("Muitos pacotes descartados ou perdidos",
                "Chegue o celular mais perto do tênis e afaste de outros aparelhos Bluetooth. Se continuar, atualize o firmware do tênis para a versão com protocolo v2.");
        faq("O SOS não enviou a mensagem",
                "Cadastre pelo menos um contato em Segurança e SOS e, para o envio automático, conceda a permissão de SMS. Sem ela, o app abre as mensagens e você precisa tocar em enviar.");

        // ---- Log
        addBlock(UiKit.sectionTitle(c, "Registro do Bluetooth (últimos eventos)"), 22);
        List<BleEvent> events = ble.getLog();
        int shown = 0;
        LinearLayout log = UiKit.panel(c);
        for (int i = events.size() - 1; i >= 0 && shown < 30; i--, shown++) {
            BleEvent e = events.get(i);
            log.addView(UiKit.caption(c, Fmt.clockWithSeconds(e.time) + "  " + e.message), UiKit.matchWrap(c, 2));
        }
        if (shown == 0) log.addView(UiKit.caption(c, "Nenhum evento ainda."));
        addBlock(log, 10);

        // ---- Relatório
        addBlock(UiKit.primaryButton(c, "Compartilhar relatório de diagnóstico", v -> shareReport()), 22);
        addBlock(UiKit.ghostButton(c, "Copiar relatório", v -> copyReport()), 8);
    }

    private LinearLayout permRow(String label, String permission) {
        Context c = requireContext();
        boolean ok = ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
        return UiKit.keyValue(c, label, ok ? "concedida" : "não concedida");
    }

    private void faq(String q, String a) {
        Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        p.addView(UiKit.label(c, q));
        p.addView(UiKit.caption(c, a), UiKit.matchWrap(c, 4));
        addBlock(p, 8);
    }

    private void test(String name) {
        app().bleManager.sendCommandAcked(name, null, (ok, detail) -> { if (isAdded()) toast(name + ": " + detail); });
    }

    private String report() {
        Context c = requireContext();
        BleManager ble = app().bleManager;
        StringBuilder sb = new StringBuilder("Diagnóstico StepVolt\n");
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            sb.append("App: ").append(pi.versionName).append('\n');
        } catch (Exception ignored) {}
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Bluetooth: ").append(ble.getState().name()).append('\n');
        sb.append("Tênis: ").append(ble.getConnectedDeviceName()).append('\n');
        sb.append("Firmware: ").append(ble.getFirmwareVersion()).append('\n');
        sb.append("Protocolo: v").append(ble.getProtocolVersion()).append('\n');
        sb.append("Pacotes: ").append(ble.getPacketCount()).append(" recebidos, ")
                .append(ble.getInvalidPackets()).append(" descartados, ")
                .append(ble.getLostPackets()).append(" perdidos\n");
        List<BleEvent> events = ble.getLog();
        sb.append("\nÚltimos eventos:\n");
        for (int i = Math.max(0, events.size() - 25); i < events.size(); i++) {
            sb.append(Fmt.clockWithSeconds(events.get(i).time)).append("  ").append(events.get(i).message).append('\n');
        }
        return sb.toString();
    }

    private void shareReport() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, report());
        startActivity(Intent.createChooser(i, "Compartilhar diagnóstico"));
    }

    private void copyReport() {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Diagnóstico StepVolt", report()));
        toast("Relatório copiado.");
    }
}
