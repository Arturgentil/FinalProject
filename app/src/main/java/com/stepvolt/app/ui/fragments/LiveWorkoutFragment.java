package com.stepvolt.app.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.stepvolt.app.R;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.RouteMapView;
import com.stepvolt.app.util.Fmt;

/**
 * Treino ao vivo: escolhe o tipo, liga o GPS junto (opcional) e mostra tempo,
 * distância, ritmo, passos e energia enquanto a sessão grava. Ao parar, abre o
 * resumo da sessão.
 */
public class LiveWorkoutFragment extends BaseScreenFragment implements Telemetry.Listener {

    private static final String[] KINDS = {"Caminhada", "Corrida", "Trilha", "Outro"};
    private static final String[] KIND_KEYS = {"walk", "run", "hike", "other"};
    private static final int REQ_LOCATION = 4101;

    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            if (telemetry().isSessionRunning()) refreshRunning();
            tick.postDelayed(this, 1000);
        }
    };

    private int kindIndex = 0;
    private boolean useGps = true;

    private LinearLayout setupBox, runBox;
    private TextView txtClock, txtDistance, txtPace, txtSteps, txtEnergy, txtBpm, txtGpsState, txtPauseBtn;
    private RouteMapView map;

    @Override protected String screenTitle() { return "Treino ao vivo"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        String saved = Prefs.getString(requireContext(), Prefs.WORKOUT_KIND, "walk");
        for (int i = 0; i < KIND_KEYS.length; i++) if (KIND_KEYS[i].equals(saved)) kindIndex = i;
        useGps = Prefs.getBool(requireContext(), Prefs.WORKOUT_USE_GPS, true);

        setupBox = new LinearLayout(requireContext());
        setupBox.setOrientation(LinearLayout.VERTICAL);
        runBox = new LinearLayout(requireContext());
        runBox.setOrientation(LinearLayout.VERTICAL);
        addBlock(setupBox, 0);
        addBlock(runBox, 0);

        buildSetup();
        buildRunning();
        render();
    }

    // ------------------------------------------------------------------
    // Antes de começar
    // ------------------------------------------------------------------

    private void buildSetup() {
        setupBox.removeAllViews();
        setupBox.addView(UiKit.sectionTitle(requireContext(), "Tipo de treino"));
        setupBox.addView(UiKit.chips(requireContext(), KINDS, kindIndex, i -> {
            kindIndex = i;
            Prefs.setString(requireContext(), Prefs.WORKOUT_KIND, KIND_KEYS[i]);
        }), UiKit.matchWrap(requireContext(), 10));

        LinearLayout gpsPanel = UiKit.panel(requireContext());
        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(UiKit.label(requireContext(), "Gravar rota com GPS"));
        text.addView(UiKit.caption(requireContext(), "Mostra distância, ritmo e o caminho percorrido. Gasta mais bateria do celular."));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch sw = new android.widget.Switch(requireContext());
        sw.setChecked(useGps);
        sw.setOnCheckedChangeListener((b, on) -> {
            useGps = on;
            Prefs.setBool(requireContext(), Prefs.WORKOUT_USE_GPS, on);
        });
        row.addView(sw);
        gpsPanel.addView(row);
        setupBox.addView(gpsPanel, UiKit.matchWrap(requireContext(), 16));

        boolean connected = app().bleManager.isConnected();
        if (!connected) {
            setupBox.addView(UiKit.note(requireContext(),
                    "Conecte o tênis para começar. A sessão grava passos e energia direto dele.",
                    0xFFFFB347), UiKit.matchWrap(requireContext(), 16));
            setupBox.addView(UiKit.primaryButton(requireContext(), "Conectar o tênis",
                    v -> nav().navigate(Navigator.DEVICE)), UiKit.matchWrap(requireContext(), 16));
        } else {
            setupBox.addView(UiKit.primaryButton(requireContext(), "Começar treino", v -> start()),
                    UiKit.matchWrap(requireContext(), 20));
        }
    }

    private void start() {
        if (useGps && !hasLocation()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        begin();
    }

    private boolean hasLocation() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQ_LOCATION) return;
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (!ok) {
            useGps = false;
            toast("Sem permissão de localização, o treino vai gravar só passos e energia.");
        }
        begin();
    }

    private void begin() {
        boolean gpsOn = useGps && hasLocation();
        if (gpsOn && !telemetry().location().isGpsEnabled()) {
            toast("O GPS do celular está desligado. Ligue-o nos ajustes para gravar a rota.");
        }
        if (!telemetry().startSession(KIND_KEYS[kindIndex], gpsOn)) {
            toast("Não foi possível começar. Confira a conexão com o tênis.");
            return;
        }
        render();
    }

    // ------------------------------------------------------------------
    // Durante o treino
    // ------------------------------------------------------------------

    private void buildRunning() {
        runBox.removeAllViews();
        android.content.Context c = requireContext();

        txtClock = UiKit.readout(c, "00:00", 56, ContextCompat.getColor(c, R.color.text_primary));
        txtClock.setGravity(Gravity.CENTER);
        runBox.addView(txtClock, UiKit.matchWrap(c, 0));

        txtGpsState = UiKit.caption(c, "");
        txtGpsState.setGravity(Gravity.CENTER);
        runBox.addView(txtGpsState, UiKit.matchWrap(c, 2));

        map = new RouteMapView(c);
        map.setEmptyText("Aguardando sinal de GPS…");
        runBox.addView(map, UiKit.matchWrap(c, 14));

        LinearLayout r1 = UiKit.row(c);
        txtDistance = tile(r1, R.drawable.ic_target, "Distância", 0xFF4CC9F0, R.drawable.tint_stride);
        txtPace = tile(r1, R.drawable.ic_history, "Ritmo /km", 0xFF8B5CFF, R.drawable.tint_oxy);
        runBox.addView(r1, UiKit.matchWrap(c, 14));

        LinearLayout r2 = UiKit.row(c);
        txtSteps = tile(r2, R.drawable.ic_nav_shoe, "Passos", 0xFF00F5A0, R.drawable.tint_volt);
        txtEnergy = tile(r2, R.drawable.ic_bolt, "Energia (mWh)", 0xFFFFB347, R.drawable.tint_volt);
        runBox.addView(r2, UiKit.matchWrap(c, 10));

        LinearLayout r3 = UiKit.row(c);
        txtBpm = tile(r3, R.drawable.ic_nav_health, "Batimentos", 0xFFFF5C7A, R.drawable.tint_pulse);
        r3.addView(new android.view.View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        runBox.addView(r3, UiKit.matchWrap(c, 10));

        LinearLayout buttons = UiKit.row(c);
        txtPauseBtn = UiKit.ghostButton(c, "Pausar", v -> togglePause());
        buttons.addView(txtPauseBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView stop = UiKit.dangerButton(c, "Parar e ver resumo", v -> stop());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        lp.leftMargin = UiKit.dp(c, 10);
        buttons.addView(stop, lp);
        runBox.addView(buttons, UiKit.matchWrap(c, 20));
    }

    /** Cria um bloco com o número grande e o rótulo, e devolve o TextView do número. */
    private TextView tile(LinearLayout parent, int icon, String label, int accent, int tint) {
        android.content.Context c = requireContext();
        LinearLayout t = UiKit.statTile(c, icon, "—", "", label, accent, tint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (parent.getChildCount() > 0) lp.leftMargin = UiKit.dp(c, 10);
        parent.addView(t, lp);
        // O primeiro filho do bloco depois do ícone é a linha do valor; o primeiro TextView dela é o número.
        LinearLayout valueRow = (LinearLayout) t.getChildAt(1);
        return (TextView) valueRow.getChildAt(0);
    }

    private void togglePause() {
        if (telemetry().isPaused()) telemetry().resumeSession(); else telemetry().pauseSession();
        refreshRunning();
    }

    private void stop() {
        Telemetry t = telemetry();
        t.stopSession();
        long id = t.lastFinishedSessionId();
        Bundle args = new Bundle();
        args.putLong("sessionId", id);
        nav().navigate(Navigator.WORKOUT_SUMMARY, args);
    }

    private void refreshRunning() {
        if (txtClock == null) return;
        Telemetry t = telemetry();
        txtClock.setText(Fmt.stopwatch(t.sessionElapsedMs()));
        txtSteps.setText(Fmt.intGrouped(t.sessionSteps()));
        txtEnergy.setText(Fmt.energy(t.sessionEnergy()));
        Double bpm = t.latest(SensorCatalog.BPM);
        txtBpm.setText(bpm == null || bpm <= 0 ? "—" : String.valueOf(Math.round(bpm)));
        txtPauseBtn.setText(t.isPaused() ? "Retomar" : "Pausar");

        boolean gps = t.location().isRunning();
        if (gps) {
            double km = t.routeDistanceM() / 1000.0;
            txtDistance.setText(Fmt.decimals(km, 2) + " km");
            txtPace.setText(GeoMath.formatPace(t.sessionPaceSecPerKm()));
            map.setRoute(t.routeFixes());
            GeoMath.Fix last = t.location().lastFix();
            if (last == null) txtGpsState.setText("Procurando sinal de GPS…");
            else if (last.accuracy > GeoMath.MAX_ACCURACY_M) txtGpsState.setText("Sinal de GPS fraco (±" + Math.round(last.accuracy) + " m)");
            else txtGpsState.setText("GPS ok (±" + Math.round(last.accuracy) + " m)");
        } else {
            txtDistance.setText("—");
            txtPace.setText("—");
            txtGpsState.setText(t.isPaused() ? "Treino pausado" : "Sem GPS neste treino");
            map.setEmptyText("Treino sem rota");
        }
        if (t.isPaused()) txtGpsState.setText("Treino pausado");
    }

    private void render() {
        boolean running = telemetry().isSessionRunning();
        setupBox.setVisibility(running ? android.view.View.GONE : android.view.View.VISIBLE);
        runBox.setVisibility(running ? android.view.View.VISIBLE : android.view.View.GONE);
        if (running) refreshRunning(); else buildSetup();
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    public void onResume() {
        super.onResume();
        telemetry().addListener(this);
        render();
        tick.post(ticker);
    }

    @Override
    public void onPause() {
        super.onPause();
        telemetry().removeListener(this);
        tick.removeCallbacks(ticker);
    }

    @Override public void onSessionChanged(boolean running) { if (isAdded()) render(); }
    @Override public void onLiveData(SensorSample sample) { if (isAdded() && telemetry().isSessionRunning()) refreshRunning(); }
    @Override public void onRouteUpdated(GeoMath.Fix fix, double distanceM) { if (isAdded() && telemetry().isSessionRunning()) refreshRunning(); }
    @Override public void onConnectionChanged(com.stepvolt.app.ble.BleState state, String detail) { if (isAdded() && !telemetry().isSessionRunning()) buildSetup(); }
}
