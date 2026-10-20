package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.ble.BleEvent;
import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.ble.BleState;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tudo sobre o tênis em um lugar só: em que estado a ligação está, quem está
 * do outro lado, o que já chegou e o que deu errado.
 *
 * Na versão anterior a conexão era um botão com dois estados e nenhuma
 * explicação quando falhava. Aqui a tela mostra o motivo, o sinal, o número de
 * pacotes e um registro dos eventos — é o que permite descobrir se o problema
 * é o Bluetooth do celular, a distância ou o firmware.
 */
public class DeviceFragment extends BaseScreenFragment implements Telemetry.Listener {

    private LinearLayout statusPanel;
    private LinearLayout devicesPanel;
    private LinearLayout diagPanel;
    private LinearLayout commandPanel;
    private LinearLayout logPanel;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean logExpanded = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            renderStatus();
            renderDiagnostics();
            renderDevices();
            ticker.postDelayed(this, 1500);
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                boolean ok = true;
                for (Boolean g : grants.values()) if (!Boolean.TRUE.equals(g)) ok = false;
                if (ok) {
                    ble().connect();
                } else {
                    toast("Sem a permissão de Bluetooth não dá para procurar o tênis.");
                }
                renderStatus();
            });

    private BleManager ble() {
        return AppState.get().bleManager;
    }

    @Override
    protected String screenTitle() {
        return "Tênis";
    }

    @Override
    protected void buildContent() {
        addAction(R.drawable.ic_settings, "Ajustes", v -> nav().navigate(Navigator.SETTINGS));

        statusPanel = UiKit.panel(requireContext());
        addBlock(statusPanel, 4);

        devicesPanel = new LinearLayout(requireContext());
        devicesPanel.setOrientation(LinearLayout.VERTICAL);
        addBlock(devicesPanel, 0);

        diagPanel = UiKit.panel(requireContext());
        addBlock(diagPanel, 12);

        commandPanel = UiKit.panel(requireContext());
        addBlock(commandPanel, 12);

        logPanel = UiKit.panel(requireContext());
        addBlock(logPanel, 12);

        renderStatus();
        renderDevices();
        renderDiagnostics();
        renderCommands();
        renderLog();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppState.get().telemetry.addListener(this);
        ticker.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        AppState.get().telemetry.removeListener(this);
        ticker.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private void renderStatus() {
        if (!isAdded() || statusPanel == null) return;
        BleManager b = ble();
        BleState state = b.getState();
        statusPanel.removeAllViews();

        LinearLayout head = UiKit.row(requireContext());
        View dot = new View(requireContext());
        dot.setBackgroundResource(dotFor(state));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(10);
        dotLp.topMargin = dp(6);
        head.addView(dot, dotLp);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.sectionTitle(requireContext(), titleFor(state));
        title.setTextSize(17);
        col.addView(title);
        String detail = b.getStateDetail();
        col.addView(UiKit.caption(requireContext(),
                detail == null || detail.isEmpty() ? subtitleFor(state) : detail));
        head.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusPanel.addView(head);

        if (state == BleState.CONNECTED) {
            statusPanel.addView(UiKit.divider(requireContext()));
            statusPanel.addView(UiKit.keyValue(requireContext(), "Dispositivo",
                    nz(b.getConnectedDeviceName(), "—")));
            statusPanel.addView(UiKit.keyValue(requireContext(), "Endereço",
                    nz(b.getConnectedDeviceAddress(), "—")));
            statusPanel.addView(UiKit.keyValue(requireContext(), "Ligado há",
                    b.getConnectedSince() > 0
                            ? Fmt.duration(System.currentTimeMillis() - b.getConnectedSince()) : "—"));
        }

        LinearLayout actions = UiKit.row(requireContext());
        if (state == BleState.CONNECTED || state.isBusy()) {
            actions.addView(UiKit.dangerButton(requireContext(),
                    state == BleState.CONNECTED ? "Desconectar" : "Cancelar",
                    v -> { ble().disconnect(); renderStatus(); }), half(0));
            actions.addView(UiKit.ghostButton(requireContext(), "Procurar outros",
                    v -> startScan()), half(1));
        } else {
            actions.addView(UiKit.primaryButton(requireContext(), "Conectar",
                    v -> startConnect()), half(0));
            actions.addView(UiKit.ghostButton(requireContext(), "Procurar",
                    v -> startScan()), half(1));
        }
        statusPanel.addView(actions, UiKit.matchWrap(requireContext(), 14));

        String lastName = Prefs.getString(requireContext(), Prefs.LAST_DEVICE_NAME, null);
        if (lastName != null && state != BleState.CONNECTED) {
            statusPanel.addView(UiKit.caption(requireContext(),
                    "Último tênis usado: " + lastName), UiKit.matchWrap(requireContext(), 10));
        }
    }

    private LinearLayout.LayoutParams half(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) lp.leftMargin = dp(10);
        return lp;
    }

    private void startConnect() {
        BleManager b = ble();
        if (!b.isBluetoothOn()) {
            toast("Ligue o Bluetooth do celular primeiro.");
            return;
        }
        if (!b.hasPermissions()) {
            requestPermissions();
            return;
        }
        b.connect();
        renderStatus();
    }

    private void startScan() {
        BleManager b = ble();
        if (!b.isBluetoothOn()) {
            toast("Ligue o Bluetooth do celular primeiro.");
            return;
        }
        if (!b.hasPermissions()) {
            requestPermissions();
            return;
        }
        b.scanOnly();
        renderStatus();
        renderDevices();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN);
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        List<String> missing = new ArrayList<>();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) ble().connect();
        else permissionLauncher.launch(missing.toArray(new String[0]));
    }

    private int dotFor(BleState s) {
        switch (s) {
            case CONNECTED: return R.drawable.circle_dot_green;
            case SCANNING:
            case CONNECTING:
            case DISCOVERING: return R.drawable.dot_oxy;
            case RECONNECTING: return R.drawable.dot_stride;
            case FAILED:
            case BLUETOOTH_OFF:
            case NO_PERMISSION: return R.drawable.dot_pulse;
            default: return R.drawable.dot_idle;
        }
    }

    private String titleFor(BleState s) {
        switch (s) {
            case CONNECTED: return getString(R.string.conn_connected);
            case SCANNING: return getString(R.string.conn_scanning);
            case CONNECTING: return getString(R.string.conn_connecting);
            case DISCOVERING: return getString(R.string.conn_preparing);
            case RECONNECTING: return getString(R.string.conn_reconnecting);
            case BLUETOOTH_OFF: return "Bluetooth desligado";
            case NO_PERMISSION: return "Permissão pendente";
            case FAILED: return "Falha na conexão";
            default: return getString(R.string.conn_disconnected);
        }
    }

    private String subtitleFor(BleState s) {
        switch (s) {
            case CONNECTED: return "Recebendo dados do tênis";
            case BLUETOOTH_OFF: return "Ligue o Bluetooth para continuar";
            case NO_PERMISSION: return "Autorize a busca por dispositivos próximos";
            default: return "Toque em Conectar para procurar o tênis";
        }
    }

    // ------------------------------------------------------------------
    // Dispositivos encontrados
    // ------------------------------------------------------------------

    private void renderDevices() {
        if (!isAdded() || devicesPanel == null) return;
        BleManager b = ble();
        List<BleManager.DiscoveredDevice> found = b.getDiscovered();
        devicesPanel.removeAllViews();

        if (found.isEmpty() && !b.isScanning()) return;

        LinearLayout panel = UiKit.panel(requireContext());
        LinearLayout head = UiKit.row(requireContext());
        head.addView(UiKit.sectionTitle(requireContext(), "Dispositivos por perto"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (b.isScanning()) {
            head.addView(UiKit.caption(requireContext(), "procurando…"));
        }
        panel.addView(head);

        if (found.isEmpty()) {
            panel.addView(UiKit.caption(requireContext(),
                    "Nenhum ainda. Deixe o tênis ligado e a até poucos metros."),
                    UiKit.matchWrap(requireContext(), 10));
        }

        for (final BleManager.DiscoveredDevice d : found) {
            LinearLayout row = UiKit.row(requireContext());
            row.setBackgroundResource(R.drawable.bg_inset);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                ble().connectToAddress(d.address);
                renderStatus();
            });

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(requireContext());
            name.setText(d.displayName());
            name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            name.setTextSize(14);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            col.addView(name);
            col.addView(UiKit.caption(requireContext(), d.address
                    + " · sinal " + Fmt.signal(d.rssi) + " (" + d.rssi + " dBm)"
                    + (d.looksLikeShoe() ? " · parece ser o StepVolt" : "")));
            row.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView go = UiKit.caption(requireContext(), "conectar");
            go.setTextColor(0xFF00F5A0);
            row.addView(go);

            panel.addView(row, UiKit.matchWrap(requireContext(), 10));
        }

        devicesPanel.addView(panel, UiKit.matchWrap(requireContext(), 12));
    }

    // ------------------------------------------------------------------
    // Diagnóstico
    // ------------------------------------------------------------------

    private void renderDiagnostics() {
        if (!isAdded() || diagPanel == null) return;
        BleManager b = ble();
        diagPanel.removeAllViews();
        diagPanel.addView(UiKit.sectionTitle(requireContext(), "Diagnóstico da ligação"));

        diagPanel.addView(UiKit.keyValue(requireContext(), "Sinal",
                b.getRssi() == 0 ? "—" : Fmt.signal(b.getRssi()) + " (" + b.getRssi() + " dBm)"));
        diagPanel.addView(UiKit.keyValue(requireContext(), "Pacotes recebidos",
                Fmt.intGrouped(b.getPacketCount())));
        diagPanel.addView(UiKit.keyValue(requireContext(), "Último pacote",
                b.getLastPacketAt() > 0 ? Fmt.ago(b.getLastPacketAt()) : "—"));
        diagPanel.addView(UiKit.keyValue(requireContext(), "Tamanho do pacote (MTU)",
                b.getMtu() > 0 ? b.getMtu() + " bytes" : "—"));
        diagPanel.addView(UiKit.keyValue(requireContext(), "Firmware",
                nz(b.getFirmwareVersion(), "não informado")));
        diagPanel.addView(UiKit.keyValue(requireContext(), "Bateria do tênis",
                b.getBatteryPct() > 0
                        ? String.format(Locale.getDefault(), "%.0f%%", b.getBatteryPct())
                        : "não informada"));

        if (b.isConnected() && b.isDataStale()) {
            diagPanel.addView(UiKit.note(requireContext(),
                    "A ligação está de pé, mas nenhum pacote novo chegou nos últimos segundos. "
                            + "Isso costuma ser o firmware travado ou o tênis fora de alcance.",
                    0xFFFFD166), UiKit.matchWrap(requireContext(), 12));
        }
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    private void renderCommands() {
        commandPanel.removeAllViews();
        commandPanel.addView(UiKit.sectionTitle(requireContext(), "Comandos"));
        commandPanel.addView(UiKit.caption(requireContext(),
                "Enviados pela mesma ligação, para o firmware responder."));

        LinearLayout row1 = UiKit.row(requireContext());
        row1.addView(UiKit.ghostButton(requireContext(), "Zerar contadores",
                v -> sendCommand("RESET")), half(0));
        row1.addView(UiKit.ghostButton(requireContext(), "Piscar LED",
                v -> sendCommand("BLINK")), half(1));
        commandPanel.addView(row1, UiKit.matchWrap(requireContext(), 14));

        LinearLayout row2 = UiKit.row(requireContext());
        row2.addView(UiKit.ghostButton(requireContext(), "Calibrar sensores",
                v -> sendCommand("CALIBRATE")), half(0));
        row2.addView(UiKit.ghostButton(requireContext(), "Pedir status",
                v -> sendCommand("STATUS")), half(1));
        commandPanel.addView(row2, UiKit.matchWrap(requireContext(), 10));

        commandPanel.addView(UiKit.dangerButton(requireContext(), "Esquecer este tênis", v -> {
            ble().forgetDevice();
            toast("Tênis esquecido. Na próxima vez o app procura de novo.");
            renderStatus();
        }), UiKit.matchWrap(requireContext(), 14));
    }

    private void sendCommand(String cmd) {
        if (!ble().isConnected()) {
            toast("Conecte o tênis antes de enviar comandos.");
            return;
        }
        boolean ok = ble().sendCommand(cmd);
        toast(ok ? "Comando " + cmd + " enviado." : "Não foi possível enviar o comando.");
        renderLog();
    }

    // ------------------------------------------------------------------
    // Registro de eventos
    // ------------------------------------------------------------------

    private void renderLog() {
        if (!isAdded() || logPanel == null) return;
        logPanel.removeAllViews();

        LinearLayout head = UiKit.row(requireContext());
        head.addView(UiKit.sectionTitle(requireContext(), "Registro da conexão"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView toggle = UiKit.caption(requireContext(), logExpanded ? "ver menos" : "ver tudo");
        toggle.setTextColor(0xFF00F5A0);
        toggle.setClickable(true);
        toggle.setOnClickListener(v -> {
            logExpanded = !logExpanded;
            renderLog();
        });
        head.addView(toggle);
        logPanel.addView(head);

        List<BleEvent> log = ble().getLog();
        if (log.isEmpty()) {
            logPanel.addView(UiKit.caption(requireContext(),
                    "Nada registrado ainda. Os eventos aparecem assim que a busca começar."),
                    UiKit.matchWrap(requireContext(), 10));
            return;
        }

        int limit = logExpanded ? Math.min(log.size(), 120) : Math.min(log.size(), 12);
        for (int i = 0; i < limit; i++) {
            BleEvent e = log.get(i);
            TextView line = new TextView(requireContext());
            line.setText(e.clock() + "  " + e.message);
            line.setTextColor(e.color());
            line.setTextSize(11.5f);
            line.setTypeface(Typeface.MONOSPACE);
            logPanel.addView(line, UiKit.matchWrap(requireContext(), 6));
        }

        if (!logExpanded && log.size() > 12) {
            logPanel.addView(UiKit.caption(requireContext(),
                    "+ " + (log.size() - 12) + " eventos anteriores"),
                    UiKit.matchWrap(requireContext(), 8));
        }

        logPanel.addView(UiKit.ghostButton(requireContext(), "Limpar registro", v -> {
            ble().clearLog();
            renderLog();
        }), UiKit.matchWrap(requireContext(), 14));
    }

    private String nz(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    // ------------------------------------------------------------------
    // Telemetry.Listener
    // ------------------------------------------------------------------

    @Override
    public void onConnectionChanged(BleState state, String detail) {
        if (!isAdded()) return;
        renderStatus();
        renderDiagnostics();
        renderDevices();
        renderLog();
    }

    @Override
    public void onLiveData(SensorSample sample) {
        // O diagnóstico já é atualizado pelo temporizador; redesenhar a cada
        // pacote só gastaria bateria.
    }

    @Override
    public void onAlertRaised(AlertRecord alert) {
        // Nada a fazer aqui: os avisos têm tela própria.
    }
}
