package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.ble.BleState;
import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.BarsView;
import com.stepvolt.app.ui.views.GaugeView;
import com.stepvolt.app.ui.views.SparklineView;
import com.stepvolt.app.util.Anim;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.ImgLoader;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Painel principal.
 *
 * A ordem da tela segue a pergunta que a pessoa faz ao abrir o app: o tênis
 * está ligado? o que ele está medindo agora? estou gravando? como foi o dia?
 * Nada aqui é decorativo — cada bloco responde a uma dessas perguntas, e o
 * bloco ao vivo só aparece quando há dados de verdade chegando.
 */
public class HomeFragment extends Fragment implements Telemetry.Listener {

    private TextView txtGreeting, txtGreetingName;
    private ImageButton btnAlerts;
    private TextView badgeAlerts;
    private ShapeableImageView imgProfile;

    private LinearLayout cardConnection;
    private View dotConnection;
    private TextView txtConnTitle, txtConnSub, btnConnAction;

    private LinearLayout liveSection, panelGauge, tileRow, panelSession;
    private GaugeView gaugeMain;
    private SparklineView sparkLive;
    private TextView txtLiveMeta;

    private TextView txtStopwatch, txtSessionSteps, txtSessionEnergy, btnSession, txtSessionHint;

    private TextView txtTodayEnergy, txtTodaySteps, txtTodayGoal, txtTodayHint;
    private ProgressBar progressToday;
    private BarsView barsToday;

    private LinearLayout alertsPreview, quickLinks;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean liveRevealed = false;
    private double lastSessionEnergy = 0;
    private long lastSessionSteps = 0;

    /** Grandeza mostrada no medidor grande. Batimentos por ser a mais lida. */
    private String gaugeMetric = SensorCatalog.BPM;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                boolean granted = true;
                for (Boolean g : grants.values()) if (!Boolean.TRUE.equals(g)) granted = false;
                if (granted) {
                    ble().connect();
                } else {
                    Toast.makeText(requireContext(),
                            "Sem permissão de Bluetooth o app não consegue achar o tênis.",
                            Toast.LENGTH_LONG).show();
                }
                refreshConnection();
            });

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            refreshSession();
            refreshConnection();
            ticker.postDelayed(this, 1000);
        }
    };

    private BleManager ble() {
        return AppState.get().bleManager;
    }

    private Telemetry telemetry() {
        return AppState.get().telemetry;
    }

    private Navigator nav() {
        return (Navigator) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        txtGreeting = v.findViewById(R.id.txtGreeting);
        txtGreetingName = v.findViewById(R.id.txtGreetingName);
        btnAlerts = v.findViewById(R.id.btnAlerts);
        badgeAlerts = v.findViewById(R.id.badgeAlerts);
        imgProfile = v.findViewById(R.id.imgProfile);

        cardConnection = v.findViewById(R.id.cardConnection);
        dotConnection = v.findViewById(R.id.dotConnection);
        txtConnTitle = v.findViewById(R.id.txtConnTitle);
        txtConnSub = v.findViewById(R.id.txtConnSub);
        btnConnAction = v.findViewById(R.id.btnConnAction);

        liveSection = v.findViewById(R.id.liveSection);
        panelGauge = v.findViewById(R.id.panelGauge);
        gaugeMain = v.findViewById(R.id.gaugeMain);
        sparkLive = v.findViewById(R.id.sparkLive);
        txtLiveMeta = v.findViewById(R.id.txtLiveMeta);
        tileRow = v.findViewById(R.id.tileRow);
        panelSession = v.findViewById(R.id.panelSession);

        txtStopwatch = v.findViewById(R.id.txtStopwatch);
        txtSessionSteps = v.findViewById(R.id.txtSessionSteps);
        txtSessionEnergy = v.findViewById(R.id.txtSessionEnergy);
        btnSession = v.findViewById(R.id.btnSession);
        txtSessionHint = v.findViewById(R.id.txtSessionHint);

        txtTodayEnergy = v.findViewById(R.id.txtTodayEnergy);
        txtTodaySteps = v.findViewById(R.id.txtTodaySteps);
        txtTodayGoal = v.findViewById(R.id.txtTodayGoal);
        txtTodayHint = v.findViewById(R.id.txtTodayHint);
        progressToday = v.findViewById(R.id.progressToday);
        barsToday = v.findViewById(R.id.barsToday);

        alertsPreview = v.findViewById(R.id.alertsPreview);
        quickLinks = v.findViewById(R.id.quickLinks);

        setupHeader();
        setupGauge();
        setupSession();
        buildQuickLinks();

        cardConnection.setOnClickListener(c -> Anim.press(c, () -> nav().navigate(Navigator.DEVICE)));
        btnConnAction.setOnClickListener(c -> onConnectAction());
        gaugeMain.setOnClickListener(c -> cycleGaugeMetric());
    }

    @Override
    public void onResume() {
        super.onResume();
        telemetry().addListener(this);
        ticker.post(tick);
        refreshConnection();
        refreshSession();
        refreshLive();
        loadToday();
        loadAlerts();
        refreshHeader();
    }

    @Override
    public void onPause() {
        super.onPause();
        telemetry().removeListener(this);
        ticker.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------
    // Cabeçalho
    // ------------------------------------------------------------------

    private void setupHeader() {
        btnAlerts.setOnClickListener(v -> nav().navigate(Navigator.ALERTS));
        imgProfile.setOnClickListener(v -> nav().navigate(Navigator.PROFILE));
        txtGreeting.setText(greeting());
    }

    private void refreshHeader() {
        String name = AppState.get().user != null ? AppState.get().user.name : null;
        if (name != null && !name.isEmpty()) {
            int space = name.indexOf(' ');
            txtGreetingName.setText(space > 0 ? name.substring(0, space) : name);
        } else {
            txtGreetingName.setText("Bem-vindo");
        }
        if (AppState.get().user != null) {
            ImgLoader.loadAvatar(imgProfile, AppState.get().user.avatarUrl, AppState.get().user.name);
        }
        // O painel continua útil sem o servidor: dados ao vivo e histórico local
        // não dependem dele, então uma falha aqui apenas deixa o nome como está.
        AppState.get().refreshUser((success, data, error) -> {
            if (!isAdded() || !success || data == null) return;
            String n = data.name;
            if (n != null && !n.isEmpty()) {
                int space = n.indexOf(' ');
                txtGreetingName.setText(space > 0 ? n.substring(0, space) : n);
            }
            ImgLoader.loadAvatar(imgProfile, data.avatarUrl, data.name);
        });
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Boa madrugada";
        if (h < 12) return "Bom dia";
        if (h < 18) return "Boa tarde";
        return "Boa noite";
    }

    // ------------------------------------------------------------------
    // Estado da conexão
    // ------------------------------------------------------------------

    private void onConnectAction() {
        BleManager b = ble();
        if (b.isConnected() || b.getState().isBusy()) {
            b.disconnect();
            return;
        }
        if (!b.isBluetoothOn()) {
            Toast.makeText(requireContext(), "Ligue o Bluetooth para procurar o tênis.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!b.hasPermissions()) {
            requestBlePermissions();
            return;
        }
        b.connect();
        refreshConnection();
    }

    private void requestBlePermissions() {
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
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (missing.isEmpty()) {
            ble().connect();
        } else {
            permissionLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private void refreshConnection() {
        if (!isAdded()) return;
        BleManager b = ble();
        BleState state = b.getState();

        int dot;
        String title;
        String sub;
        String action;

        switch (state) {
            case CONNECTED:
                dot = R.drawable.circle_dot_green;
                title = getString(R.string.conn_connected);
                String name = b.getConnectedDeviceName();
                sub = (name == null ? "Tênis" : name)
                        + " · sinal " + Fmt.signal(b.getRssi())
                        + " · " + b.getPacketCount() + " pacotes";
                if (b.isDataStale()) sub = "Ligado, mas sem pacote novo há alguns segundos";
                action = getString(R.string.conn_disconnect);
                break;
            case SCANNING:
                dot = R.drawable.dot_oxy;
                title = getString(R.string.conn_scanning);
                sub = b.getDiscovered().isEmpty()
                        ? "Deixe o tênis por perto e ligado"
                        : b.getDiscovered().size() + " dispositivo(s) encontrado(s)";
                action = "Parar";
                break;
            case CONNECTING:
                dot = R.drawable.dot_oxy;
                title = getString(R.string.conn_connecting);
                sub = b.getStateDetail();
                action = "Cancelar";
                break;
            case DISCOVERING:
                dot = R.drawable.dot_oxy;
                title = getString(R.string.conn_preparing);
                sub = b.getStateDetail();
                action = "Cancelar";
                break;
            case RECONNECTING:
                dot = R.drawable.dot_stride;
                title = getString(R.string.conn_reconnecting);
                sub = b.getStateDetail();
                action = "Parar";
                break;
            case BLUETOOTH_OFF:
                dot = R.drawable.dot_pulse;
                title = "Bluetooth desligado";
                sub = "Ligue o Bluetooth do celular para continuar";
                action = getString(R.string.conn_connect);
                break;
            case NO_PERMISSION:
                dot = R.drawable.dot_pulse;
                title = "Permissão pendente";
                sub = "O Android precisa autorizar a busca por dispositivos";
                action = "Autorizar";
                break;
            case FAILED:
                dot = R.drawable.dot_pulse;
                title = "Não foi possível conectar";
                sub = b.getStateDetail();
                action = "Tentar de novo";
                break;
            case IDLE:
            default:
                dot = R.drawable.dot_idle;
                title = getString(R.string.conn_disconnected);
                String last = Prefs.getString(requireContext(), Prefs.LAST_DEVICE_NAME, null);
                sub = last != null ? "Último usado: " + last : "Toque para procurar o tênis";
                action = getString(R.string.conn_connect);
                break;
        }

        dotConnection.setBackgroundResource(dot);
        txtConnTitle.setText(title);
        txtConnSub.setText(sub == null ? "" : sub);
        txtConnSub.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        btnConnAction.setText(action);
        btnConnAction.setBackgroundResource(state == BleState.CONNECTED
                ? R.drawable.bg_button_ghost : R.drawable.bg_button_primary);
        btnConnAction.setTextColor(state == BleState.CONNECTED
                ? ContextCompat.getColor(requireContext(), R.color.text_secondary)
                : ContextCompat.getColor(requireContext(), R.color.black_text));

        gaugeMain.setLive(state == BleState.CONNECTED && !b.isDataStale());
    }

    // ------------------------------------------------------------------
    // Bloco ao vivo
    // ------------------------------------------------------------------

    private void setupGauge() {
        applyGaugeMetric();
        sparkLive.setCapacity(120);
        sparkLive.setShowDot(true);
    }

    /** Toque no medidor troca a grandeza mostrada, sem sair da tela. */
    private void cycleGaugeMetric() {
        List<Metric> options = SensorCatalog.instantaneous();
        int idx = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key.equals(gaugeMetric)) idx = i;
        }
        gaugeMetric = options.get((idx + 1) % options.size()).key;
        applyGaugeMetric();
        refreshLive();
    }

    private void applyGaugeMetric() {
        Metric m = SensorCatalog.byKey(gaugeMetric);
        if (m == null) m = SensorCatalog.byKey(SensorCatalog.BPM);
        gaugeMain.configure(m.label, m.unit, m.color, m.min, m.max, m.decimals);
        gaugeMain.setUsualRange(m.usualLow, m.usualHigh);
        sparkLive.setAccent(m.color);
        sparkLive.clear();
    }

    private void refreshLive() {
        if (!isAdded()) return;
        Telemetry t = telemetry();
        boolean hasData = !t.liveMetrics().isEmpty();
        liveSection.setVisibility(hasData ? View.VISIBLE : View.GONE);
        if (!hasData) {
            liveRevealed = false;
            return;
        }

        Double value = t.latest(gaugeMetric);
        if (value != null) {
            gaugeMain.setValue(value.floatValue(), true);
        } else {
            gaugeMain.clearValue();
        }

        float[] series = t.liveValues(gaugeMetric);
        if (series.length > 1) sparkLive.setValues(series);

        long at = t.latestAt(gaugeMetric);
        txtLiveMeta.setText(at > 0
                ? "Última leitura " + Fmt.ago(at) + " · " + series.length + " pontos no gráfico"
                : "Aguardando a primeira leitura desta grandeza");

        buildTiles();

        if (!liveRevealed) {
            liveRevealed = true;
            Anim.revealStaggered(new View[]{panelGauge, tileRow}, 70);
        }
    }

    /** Uma peça por grandeza recebida, fora a que já está no medidor. */
    private void buildTiles() {
        Telemetry t = telemetry();
        List<String> keys = t.liveMetrics();
        tileRow.removeAllViews();

        int added = 0;
        for (String key : keys) {
            if (key.equals(gaugeMetric)) continue;
            if (SensorCatalog.isHidden(key)) continue;
            if (added >= 3) break;
            Metric m = SensorCatalog.byKey(key);
            Double v = t.latest(key);
            if (v == null) continue;

            String label = m != null ? m.label : key;
            String unit = m != null ? m.unit : "";
            int color = m != null ? m.color : 0xFF9A9AB8;
            int icon = m != null ? m.iconRes : R.drawable.ic_sensor;
            int tint = tintFor(key);
            int decimals = m != null ? m.decimals : 1;

            LinearLayout tile = UiKit.statTile(requireContext(), icon,
                    Fmt.decimals(v, decimals), unit, label, color, tint);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (added > 0) lp.leftMargin = UiKit.dp(requireContext(), 10);
            tileRow.addView(tile, lp);
            added++;
        }
        tileRow.setVisibility(added == 0 ? View.GONE : View.VISIBLE);
    }

    private int tintFor(String key) {
        if (SensorCatalog.BPM.equals(key)) return R.drawable.tint_pulse;
        if (SensorCatalog.SPO2.equals(key)) return R.drawable.tint_oxy;
        if (SensorCatalog.STEPS.equals(key)) return R.drawable.tint_stride;
        if (SensorCatalog.ENERGY.equals(key)) return R.drawable.tint_volt;
        return R.drawable.tint_neutral;
    }

    // ------------------------------------------------------------------
    // Sessão
    // ------------------------------------------------------------------

    private void setupSession() {
        btnSession.setOnClickListener(v -> {
            Telemetry t = telemetry();
            if (t.isSessionRunning()) {
                t.stopSession();
                Toast.makeText(requireContext(), "Sessão salva no histórico.", Toast.LENGTH_SHORT).show();
                loadToday();
            } else {
                if (!t.startSession()) {
                    Toast.makeText(requireContext(),
                            "Conecte o tênis antes de iniciar a sessão.", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastSessionEnergy = 0;
                lastSessionSteps = 0;
            }
            refreshSession();
        });
    }

    private void refreshSession() {
        if (!isAdded()) return;
        Telemetry t = telemetry();
        boolean running = t.isSessionRunning();

        txtStopwatch.setText(running ? Fmt.stopwatch(t.sessionElapsedMs()) : "0:00");

        if (running) {
            long steps = t.sessionSteps();
            double energy = t.sessionEnergy();
            if (steps != lastSessionSteps) {
                Anim.countTo(txtSessionSteps, lastSessionSteps, steps, 0, "");
                lastSessionSteps = steps;
            }
            if (Math.abs(energy - lastSessionEnergy) > 0.05) {
                Anim.countTo(txtSessionEnergy, lastSessionEnergy, energy, 1, "");
                lastSessionEnergy = energy;
            }
        } else {
            txtSessionSteps.setText("0");
            txtSessionEnergy.setText("0,0");
            lastSessionSteps = 0;
            lastSessionEnergy = 0;
        }

        btnSession.setText(running ? "Encerrar sessão" : "Iniciar sessão");
        btnSession.setBackgroundResource(running
                ? R.drawable.bg_button_danger : R.drawable.bg_button_primary);
        btnSession.setTextColor(running
                ? ContextCompat.getColor(requireContext(), R.color.text_primary)
                : ContextCompat.getColor(requireContext(), R.color.black_text));

        if (running) {
            txtSessionHint.setText("Gravando " + t.sessionSamples()
                    + " leituras. Você pode sair da tela: a coleta continua.");
        } else if (ble().isConnected()) {
            txtSessionHint.setText("Tênis pronto. A sessão guarda passos, energia e sinais vitais.");
        } else {
            txtSessionHint.setText("Conecte o tênis para gravar uma sessão.");
        }
    }

    // ------------------------------------------------------------------
    // Resumo do dia
    // ------------------------------------------------------------------

    private void loadToday() {
        if (!isAdded()) return;
        final long start = startOfToday();
        final long now = System.currentTimeMillis();
        final Repo repo = Repo.get(requireContext());
        final long stepGoal = Prefs.dailyStepGoal(requireContext());

        repo.totalAsync(SensorCatalog.STEPS, start, now, steps -> {
            if (!isAdded()) return;
            long s = Math.round(steps);
            txtTodaySteps.setText(Fmt.intGrouped(s));
            int pct = (int) Math.min(100, Math.round(s * 100.0 / Math.max(1, stepGoal)));
            progressToday.setProgress(pct);
            txtTodayGoal.setText(String.format(Locale.getDefault(),
                    "de %s passos · %d%% da meta", Fmt.intGrouped(stepGoal), pct));
        });

        repo.totalAsync(SensorCatalog.ENERGY, start, now, energy -> {
            if (!isAdded()) return;
            txtTodayEnergy.setText(Fmt.energy(energy) + " mWh gerados");
        });

        repo.bucketsAsync(SensorCatalog.STEPS, start, now, 3_600_000L, true, points -> {
            if (!isAdded()) return;
            renderHourBars(points);
        });
    }

    /** Barras por hora. As horas sem leitura ficam com zero, para não mentir o eixo. */
    private void renderHourBars(List<Point> points) {
        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        int from = Math.max(0, currentHour - 11);
        int slots = currentHour - from + 1;

        float[] values = new float[slots];
        String[] labels = new String[slots];
        for (int i = 0; i < slots; i++) {
            int hour = from + i;
            labels[i] = (i % 3 == 0 || i == slots - 1) ? String.format(Locale.getDefault(), "%02dh", hour) : "";
            values[i] = 0f;
        }

        for (Point p : points) {
            cal.setTimeInMillis(p.ts);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int idx = hour - from;
            if (idx >= 0 && idx < slots) values[idx] += (float) p.value;
        }

        boolean anything = false;
        for (float f : values) if (f > 0) anything = true;

        if (anything) {
            barsToday.setVisibility(View.VISIBLE);
            barsToday.setAccent(0xFFFFB347);
            barsToday.setData(values, labels);
            txtTodayHint.setText("Passos por hora, das " + String.format(Locale.getDefault(), "%02dh", from)
                    + " até agora.");
        } else {
            barsToday.setVisibility(View.GONE);
            txtTodayHint.setText("Nenhum passo registrado hoje. Conecte o tênis para começar.");
        }
    }

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ------------------------------------------------------------------
    // Avisos e atalhos
    // ------------------------------------------------------------------

    private void loadAlerts() {
        final Repo repo = Repo.get(requireContext());
        repo.unseenAlertCountAsync(count -> {
            if (!isAdded()) return;
            if (count != null && count > 0) {
                badgeAlerts.setVisibility(View.VISIBLE);
                badgeAlerts.setText(count > 9 ? "9+" : String.valueOf(count));
            } else {
                badgeAlerts.setVisibility(View.GONE);
            }
        });

        repo.alertsAsync(3, list -> {
            if (!isAdded()) return;
            alertsPreview.removeAllViews();
            if (list == null || list.isEmpty()) return;

            alertsPreview.addView(UiKit.sectionTitle(requireContext(), "Avisos recentes"),
                    UiKit.matchWrap(requireContext(), 20));
            for (AlertRecord a : list) {
                LinearLayout n = UiKit.note(requireContext(),
                        Fmt.clock(a.ts) + " · " + a.message, a.color());
                n.setOnClickListener(v -> nav().navigate(Navigator.ALERTS));
                alertsPreview.addView(n, UiKit.matchWrap(requireContext(), 8));
            }
        });
    }

    private void buildQuickLinks() {
        quickLinks.removeAllViews();
        quickLinks.addView(UiKit.sectionTitle(requireContext(), "Atalhos"),
                UiKit.matchWrap(requireContext(), 20));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_play,
                "Treino ao vivo", "GPS, ritmo, distância e energia em tempo real", 0xFF00F5A0,
                v -> nav().navigate(Navigator.LIVE_WORKOUT)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_bolt,
                "Energia", "Quanto o tênis gerou e o que isso representa", 0xFFFFB347,
                v -> nav().navigate(Navigator.ENERGY)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_sensor,
                "Sensores", "O que cada grandeza está medindo agora", 0xFF4CC9F0,
                v -> nav().navigate(Navigator.SENSORS)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_nav_shoe,
                "Biomecânica do pé", "Pressão por zona e simetria entre os pés", 0xFF8B5CFF,
                v -> nav().navigate(Navigator.BIOMECHANICS)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_history,
                "Histórico", "Sessões gravadas e atividades enviadas", 0xFF8B5CFF,
                v -> nav().navigate(Navigator.HISTORY)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_target,
                "Metas", "Acompanhe passos, energia e distância", 0xFF00F5A0,
                v -> nav().navigate(Navigator.GOALS)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_trophy,
                "Conquistas", "O que você já desbloqueou", 0xFFFFB347,
                v -> nav().navigate(Navigator.ACHIEVEMENTS)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_target,
                "Planos e metas", "A meta da semana feita com o seu histórico", 0xFF00F5A0,
                v -> nav().navigate(Navigator.PLANS)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_people,
                "Comunidade", "Publica\u00e7\u00f5es, busca de pessoas e notifica\u00e7\u00f5es", 0xFF8B5CFF,
                v -> nav().navigate(Navigator.FEED)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_people,
                "Desafios e ranking", "Compare-se com quem você segue", 0xFF4CC9F0,
                v -> nav().navigate(Navigator.CHALLENGES)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_nav_shoe,
                "Meus tênis", "Quilometragem, bateria e firmware", 0xFFFFB347,
                v -> nav().navigate(Navigator.MY_SHOES)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_shield,
                "Segurança e SOS", "Contatos de emergência e detecção de queda", 0xFFFF5C7A,
                v -> nav().navigate(Navigator.EMERGENCY)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_export,
                "Dados e conta", "Exportar, backup e sincronização", 0xFF9A9AB8,
                v -> nav().navigate(Navigator.DATA_ACCOUNT)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_info,
                "Ajuda e diagnóstico", "Conexão, permissões e problemas comuns", 0xFF9A9AB8,
                v -> nav().navigate(Navigator.HELP)), UiKit.matchWrap(requireContext(), 8));

        quickLinks.addView(UiKit.linkRow(requireContext(), R.drawable.ic_settings,
                "Ajustes", "Limites de alerta, metas, tema e conexão", 0xFF9A9AB8,
                v -> nav().navigate(Navigator.SETTINGS)), UiKit.matchWrap(requireContext(), 8));
    }

    // ------------------------------------------------------------------
    // Telemetry.Listener
    // ------------------------------------------------------------------

    @Override
    public void onLiveData(SensorSample sample) {
        if (!isAdded()) return;
        refreshLive();
        if (telemetry().isSessionRunning()) refreshSession();
    }

    @Override
    public void onSessionChanged(boolean running) {
        if (!isAdded()) return;
        refreshSession();
        if (!running) loadToday();
    }

    @Override
    public void onAlertRaised(AlertRecord alert) {
        if (!isAdded()) return;
        loadAlerts();
        Anim.tick(btnAlerts);
    }

    @Override
    public void onConnectionChanged(BleState state, String detail) {
        if (!isAdded()) return;
        refreshConnection();
        refreshSession();
        if (state == BleState.IDLE || state == BleState.FAILED) {
            liveRevealed = false;
        }
    }
}
