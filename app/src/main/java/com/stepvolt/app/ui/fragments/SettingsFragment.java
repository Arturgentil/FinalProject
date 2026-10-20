package com.stepvolt.app.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.OkResponse;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.util.ViewFactory;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsFragment extends Fragment {

    private LinearLayout sectionsContainer;

    private final Map<String, Boolean> toggles = new HashMap<>();
    private final Map<String, String> selects = new HashMap<>();

    /**
     * Cada chave de toggle aponta para a preferência persistida correspondente.
     * Na versão anterior tudo isto vivia só em memória: mudar um limite e
     * fechar o app desfazia a mudança, e o motor de alertas nunca via o valor.
     */
    private static final Map<String, String> TOGGLE_PREFS = new HashMap<>();

    static {
        TOGGLE_PREFS.put("highBpm", Prefs.ALERTS_ON);
        TOGGLE_PREFS.put("syncAuto", Prefs.SYNC_AUTO);
        TOGGLE_PREFS.put("autoConnect", Prefs.AUTO_CONNECT);
        TOGGLE_PREFS.put("keepAlive", Prefs.KEEP_ALIVE);
        TOGGLE_PREFS.put("aiServer", Prefs.AI_ENABLED);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().goBack());
        sectionsContainer = view.findViewById(R.id.sectionsContainer);

        loadPrefs();

        buildServerSection();
        buildConnectionSection();
        buildGeneralSection();
        buildNotificationsSection();
        buildPrivacySection();
        buildHealthSection();
        buildGoalsSection();
        buildDataSection();
        buildAccountSection();
        buildHelpSection();
    }

    /** Lê tudo do disco ao abrir, para a tela refletir o que o app usa de verdade. */
    private void loadPrefs() {
        // Preferências guardadas no servidor (ele é quem as aplica); se o perfil ainda não
        // chegou, usa a última cópia local.
        com.stepvolt.app.models.User me = AppState.get().user;
        toggles.put("goalAlert", me != null ? me.notifGoal : Prefs.getBool(requireContext(), "notif_goal", true));
        toggles.put("recordAlert", me != null ? me.notifRecord : Prefs.getBool(requireContext(), "notif_record", true));
        toggles.put("socialAlert", me != null ? me.notifSocial : Prefs.getBool(requireContext(), "notif_social", true));
        toggles.put("highBpm", Prefs.alertsOn(requireContext()));
        toggles.put("publicProfile", me != null ? me.publicProfile : Prefs.getBool(requireContext(), "public_profile", true));
        toggles.put("autoShare", me != null ? me.autoShare : Prefs.getBool(requireContext(), "auto_share", false));
        toggles.put("syncAuto", Prefs.getBool(requireContext(), Prefs.SYNC_AUTO, true));
        toggles.put("autoConnect", Prefs.getBool(requireContext(), Prefs.AUTO_CONNECT, true));
        toggles.put("keepAlive", Prefs.getBool(requireContext(), Prefs.KEEP_ALIVE, true));
        toggles.put("aiServer", Prefs.getBool(requireContext(), Prefs.AI_ENABLED, true));

        selects.put("theme", Prefs.getString(requireContext(), "theme", "Escuro"));
        selects.put("commenters", commentLabel(me != null ? me.commentPolicy : "all"));
    }

    private static final String[] COMMENT_LABELS = {"Todos", "Apenas seguidores", "Ningu\u00e9m"};
    private static final String[] COMMENT_VALUES = {"all", "followers", "none"};

    private static String commentLabel(String value) {
        for (int i = 0; i < COMMENT_VALUES.length; i++) if (COMMENT_VALUES[i].equals(value)) return COMMENT_LABELS[i];
        return COMMENT_LABELS[0];
    }

    private static String commentValue(String label) {
        for (int i = 0; i < COMMENT_LABELS.length; i++) if (COMMENT_LABELS[i].equals(label)) return COMMENT_VALUES[i];
        return "all";
    }

    /** Campos do servidor: chave do toggle -> nome do campo na API. */
    private static final Map<String, String> SERVER_FIELDS = new HashMap<>();
    static {
        SERVER_FIELDS.put("goalAlert", "notifGoal");
        SERVER_FIELDS.put("recordAlert", "notifRecord");
        SERVER_FIELDS.put("socialAlert", "notifSocial");
        SERVER_FIELDS.put("publicProfile", "publicProfile");
        SERVER_FIELDS.put("autoShare", "autoShare");
    }

    private final Map<String, SwitchMaterial> switches = new HashMap<>();
    private boolean suppressSwitch = false;

    private void persistToggle(String key, boolean value) {
        String serverField = SERVER_FIELDS.get(key);
        if (serverField != null) {
            saveServerPref(serverField, value, () -> revertSwitch(key, !value));
            return;
        }
        String prefKey = TOGGLE_PREFS.containsKey(key) ? TOGGLE_PREFS.get(key) : key;
        Prefs.setBool(requireContext(), prefKey, value);
    }

    private void revertSwitch(String key, boolean value) {
        toggles.put(key, value);
        SwitchMaterial sw = switches.get(key);
        if (sw != null) {
            suppressSwitch = true;
            sw.setChecked(value);
            suppressSwitch = false;
        }
    }

    /**
     * Manda uma preferência ao servidor. Se falhar (sem rede, sem login), a chave
     * volta ao valor anterior em vez de mostrar uma escolha que nada aplicou.
     */
    private void saveServerPref(String field, Object value, Runnable onFail) {
        if (!SessionManager.isLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), "Entre na sua conta para mudar esta prefer\u00eancia.", Toast.LENGTH_SHORT).show();
            onFail.run();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put(field, value);
        ApiClient.get(requireContext()).updateMe(body).enqueue(new Callback<com.stepvolt.app.models.User>() {
            @Override
            public void onResponse(Call<com.stepvolt.app.models.User> call, Response<com.stepvolt.app.models.User> r) {
                if (!isAdded()) return;
                if (r.isSuccessful() && r.body() != null) {
                    AppState.get().user = r.body();
                    mirrorServerPrefs(r.body());
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
                    onFail.run();
                }
            }
            @Override
            public void onFailure(Call<com.stepvolt.app.models.User> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Sem conex\u00e3o: a prefer\u00eancia n\u00e3o foi salva.", Toast.LENGTH_SHORT).show();
                onFail.run();
            }
        });
    }

    /** Cópia local, para o app saber o que aplicar mesmo antes do perfil carregar. */
    static void mirrorServerPrefs(com.stepvolt.app.models.User u) {
        android.content.Context c = AppState.get();
        Prefs.setBool(c, "notif_goal", u.notifGoal);
        Prefs.setBool(c, "notif_record", u.notifRecord);
        Prefs.setBool(c, "notif_social", u.notifSocial);
        Prefs.setBool(c, "public_profile", u.publicProfile);
        Prefs.setBool(c, "auto_share", u.autoShare);
    }

    private void persistSelect(String key, String value) {
        if ("commenters".equals(key)) {
            String previous = selects.get(key);
            saveServerPref("commentPolicy", commentValue(value), () -> {
                selects.put(key, previous);
                Toast.makeText(requireContext(), "Mantido: " + previous, Toast.LENGTH_SHORT).show();
            });
            return;
        }
        Prefs.setString(requireContext(), key, value);
        if ("theme".equals(key)) com.stepvolt.app.core.ThemeHelper.apply(requireContext());
    }

    // ---------------- section scaffolding ----------------

    private LinearLayout addSection(String icon, String title, boolean startOpen) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        sectionsContainer.addView(card, cardLp);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setClickable(true);
        header.setFocusable(true);
        card.addView(header);

        TextView iconView = new TextView(requireContext());
        iconView.setText(icon);
        iconView.setTextSize(18);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackgroundResource(R.drawable.bg_icon_button);
        header.addView(iconView, iconLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        titleView.setTextSize(15);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        header.addView(titleView, titleLp);

        TextView chevron = new TextView(requireContext());
        chevron.setText("\u25BE");
        chevron.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        chevron.setTextSize(16);
        header.addView(chevron);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(16));
        content.setVisibility(startOpen ? View.VISIBLE : View.GONE);
        chevron.setRotation(startOpen ? 180f : 0f);
        card.addView(content);

        header.setOnClickListener(v -> {
            boolean opening = content.getVisibility() != View.VISIBLE;
            content.setVisibility(opening ? View.VISIBLE : View.GONE);
            chevron.setRotation(opening ? 180f : 0f);
        });

        return content;
    }

    private void addToggleRow(LinearLayout parent, String key, String label, String desc) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        parent.addView(row);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(requireContext());
        t1.setText(label);
        t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        t1.setTextSize(14);
        col.addView(t1);
        if (desc != null) {
            TextView t2 = new TextView(requireContext());
            t2.setText(desc);
            t2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
            t2.setTextSize(12);
            col.addView(t2);
        }
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(col, colLp);

        SwitchMaterial sw = new SwitchMaterial(requireContext());
        sw.setChecked(Boolean.TRUE.equals(toggles.get(key)));
        switches.put(key, sw);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressSwitch) return;
            toggles.put(key, checked);
            persistToggle(key, checked);
        });
        row.addView(sw);
    }

    private void addSelectRow(LinearLayout parent, String key, String label, String[] options) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        parent.addView(row);

        TextView t1 = new TextView(requireContext());
        t1.setText(label);
        t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        t1.setTextSize(14);
        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t1, t1Lp);

        TextView value = new TextView(requireContext());
        value.setText(selects.get(key));
        value.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        value.setTextSize(13);
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        value.setBackgroundResource(R.drawable.bg_icon_button);
        value.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.addView(value);

        value.setOnClickListener(v -> {
            int idx = java.util.Arrays.asList(options).indexOf(selects.get(key));
            String next = options[(idx + 1) % options.length];
            selects.put(key, next);
            persistSelect(key, next);
            value.setText(next);
            if ("commenters".equals(key)) {
                value.postDelayed(() -> { if (isAdded()) value.setText(selects.get(key)); }, 1500);
            }
        });
    }

    private void addSliderRow(LinearLayout parent, String label, int min, int max, int initial, String unit, java.util.function.IntConsumer onChange) {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colLp.topMargin = dp(6);
        parent.addView(col, colLp);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView t1 = new TextView(requireContext());
        t1.setText(label);
        t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        t1.setTextSize(14);
        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t1, t1Lp);
        TextView value = new TextView(requireContext());
        value.setText(initial + " " + unit);
        value.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
        value.setTextSize(14);
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(value);
        col.addView(row);

        SeekBar seekBar = new SeekBar(requireContext());
        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(v + " " + unit);
                onChange.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        col.addView(seekBar);

        LinearLayout rangeRow = new LinearLayout(requireContext());
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView minTv = new TextView(requireContext());
        minTv.setText(min + " " + unit);
        minTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        minTv.setTextSize(11);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rangeRow.addView(minTv, minLp);
        TextView maxTv = new TextView(requireContext());
        maxTv.setText(max + " " + unit);
        maxTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        maxTv.setTextSize(11);
        maxTv.setGravity(Gravity.END);
        LinearLayout.LayoutParams maxLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rangeRow.addView(maxTv, maxLp);
        col.addView(rangeRow);
    }

    private void addActionRow(LinearLayout parent, String label, int textColor, Runnable onClick) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_icon_button);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(row, lp);

        TextView t = new TextView(requireContext());
        t.setText(label);
        t.setTextColor(textColor);
        t.setTextSize(14);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t, tLp);

        TextView chevron = new TextView(requireContext());
        chevron.setText("\u203A");
        chevron.setTextColor(textColor);
        chevron.setTextSize(18);
        row.addView(chevron);

        row.setOnClickListener(v -> onClick.run());
    }

    // ---------------- sections ----------------

    private void buildServerSection() {
        LinearLayout content = addSection("\uD83D\uDDA5\uFE0F", "Servidor", true);

        TextView hint = new TextView(requireContext());
        hint.setText("Endere\u00e7o da API (Node/PostgreSQL). Use o IP local da m\u00e1quina que roda o backend, ex.: http://192.168.0.42:3000/");
        hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        hint.setTextSize(12);
        content.addView(hint);

        LinearLayout inputContainer = new LinearLayout(requireContext());
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        EditText input = ViewFactory.addLabeledInput(requireContext(), inputContainer, "URL da API", "http://192.168.0.42:3000/",
                InputType.TYPE_TEXT_VARIATION_URI, SessionManager.getBaseUrl(requireContext()));
        content.addView(inputContainer);

        TextView btnSave = new TextView(requireContext());
        btnSave.setText("Salvar endere\u00e7o");
        btnSave.setTextColor(0xFF000000);
        btnSave.setTextSize(13);
        btnSave.setTypeface(btnSave.getTypeface(), android.graphics.Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackgroundResource(R.drawable.bg_button_primary);
        btnSave.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(12);
        content.addView(btnSave, btnLp);

        btnSave.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) return;
            SessionManager.setBaseUrl(requireContext(), url);
            Toast.makeText(requireContext(), "Endere\u00e7o salvo.", Toast.LENGTH_SHORT).show();
        });
    }

    private void buildGeneralSection() {
        LinearLayout content = addSection("\uD83C\uDF10", "Prefer\u00eancias gerais", false);
        addSelectRow(content, "theme", "Tema (Claro ainda \u00e9 experimental)", new String[]{"Escuro", "Claro", "Seguir sistema"});
        TextView note = new TextView(requireContext());
        note.setText("O app est\u00e1 em portugu\u00eas e usa km e kg. Outros idiomas e unidades ainda n\u00e3o foram implementados.");
        note.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        note.setTextSize(12);
        content.addView(note);
    }

    private void buildNotificationsSection() {
        LinearLayout content = addSection("\uD83D\uDD14", "Notifica\u00e7\u00f5es", false);
        addToggleRow(content, "goalAlert", "Meta atingida", "Aviso na aba de notifica\u00e7\u00f5es quando voc\u00ea completa uma meta");
        addToggleRow(content, "recordAlert", "Conquistas", "Quando voc\u00ea desbloqueia uma conquista");
        addToggleRow(content, "highBpm", "Batimentos elevados", "Acima do limite definido");
        addToggleRow(content, "lowSpo2", "Oxigena\u00e7\u00e3o baixa", "Abaixo do limite definido");
        addToggleRow(content, "socialAlert", "Atividade de amigos", "Curtidas, coment\u00e1rios, seguidores");
    }

    private void buildPrivacySection() {
        LinearLayout content = addSection("\uD83D\uDEE1\uFE0F", "Privacidade", false);
        addToggleRow(content, "publicProfile", "Perfil p\u00fablico", "Qualquer pessoa pode ver suas atividades");
        addToggleRow(content, "autoShare", "Compartilhamento autom\u00e1tico", "Publica atividades automaticamente");
        addSelectRow(content, "commenters", "Quem pode comentar", new String[]{"Todos", "Apenas seguidores", "Ningu\u00e9m"});
    }

    private void buildHealthSection() {
        LinearLayout content = addSection("\u2764\uFE0F", "Limites de sa\u00fade", false);
        addToggleRow(content, "highBpm", "Gerar avisos",
                "Compara cada leitura com os limites abaixo");
        addSliderRow(content, "Batimentos acima de", 120, 220,
                Prefs.bpmMax(requireContext()), "bpm",
                v -> Prefs.setInt(requireContext(), Prefs.BPM_MAX, v));
        addSliderRow(content, "Batimentos abaixo de", 30, 60,
                Prefs.bpmMin(requireContext()), "bpm",
                v -> Prefs.setInt(requireContext(), Prefs.BPM_MIN, v));
        addSliderRow(content, "Oxigena\u00e7\u00e3o abaixo de", 85, 98,
                Prefs.spo2Min(requireContext()), "%",
                v -> Prefs.setInt(requireContext(), Prefs.SPO2_MIN, v));
        addSliderRow(content, "Temperatura acima de", 35, 42,
                Math.round(Prefs.tempMax(requireContext())), "\u00b0C",
                v -> Prefs.setFloat(requireContext(), Prefs.TEMP_MAX, v));
        addNoteRow(content, "Um aviso s\u00f3 dispara depois de tr\u00eas leituras seguidas fora do "
                + "limite, e cada tipo se repete no m\u00e1ximo a cada tr\u00eas minutos.");
    }

    private void buildConnectionSection() {
        LinearLayout content = addSection("\uD83D\uDCF6", "Conex\u00e3o com o t\u00eanis", false);
        addToggleRow(content, "autoConnect", "Reconectar sozinho",
                "Tenta voltar \u00e0 liga\u00e7\u00e3o quando ela cai");
        addToggleRow(content, "keepAlive", "Manter coleta em segundo plano",
                "Mant\u00e9m a sess\u00e3o gravando com o app fechado");

        String last = Prefs.getString(requireContext(), Prefs.LAST_DEVICE_NAME, null);
        addNoteRow(content, last == null
                ? "Nenhum t\u00eanis lembrado ainda."
                : "T\u00eanis lembrado: " + last);

        addActionRow(content, "Esquecer t\u00eanis lembrado", 0xFFFFC800, () -> {
            AppState.get().bleManager.forgetDevice();
            Toast.makeText(requireContext(), "T\u00eanis esquecido.", Toast.LENGTH_SHORT).show();
        });
    }

    private void buildGoalsSection() {
        LinearLayout content = addSection("\uD83C\uDFAF", "Metas di\u00e1rias", false);
        addSliderRow(content, "Passos por dia", 2000, 20000,
                (int) Prefs.dailyStepGoal(requireContext()), "passos",
                v -> Prefs.setInt(requireContext(), Prefs.DAILY_STEP_GOAL, v));
        addSliderRow(content, "Energia por dia", 50, 2000,
                Math.round(Prefs.dailyEnergyGoal(requireContext())), "mWh",
                v -> Prefs.setFloat(requireContext(), Prefs.DAILY_ENERGY_GOAL, v));
        addNoteRow(content, "As metas alimentam a barra do In\u00edcio e a leitura da aba An\u00e1lise.");
    }

    private void buildDataSection() {
        LinearLayout content = addSection("\uD83D\uDCBE", "Dados e armazenamento", false);
        addToggleRow(content, "syncAuto", "Envio autom\u00e1tico",
                "Sobe as sess\u00f5es gravadas assim que houver rede");
        addToggleRow(content, "aiServer", "An\u00e1lise no servidor",
                "Desligado, a aba An\u00e1lise calcula tudo no aparelho");

        addActionRow(content, "Enviar sess\u00f5es pendentes agora", 0xFF00F5A0, () -> {
            AppState.get().telemetry.syncPendingSessions();
            Toast.makeText(requireContext(), "Envio solicitado.", Toast.LENGTH_SHORT).show();
        });

        addActionRow(content, "Exportar medi\u00e7\u00f5es em CSV", 0xFF4CC9F0, this::exportCsv);

        addActionRow(content, "Apagar dados locais", 0xFFFF6B6B, () ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Apagar dados locais?")
                        .setMessage("Isto remove as medi\u00e7\u00f5es, sess\u00f5es e avisos guardados neste "
                                + "aparelho. O que j\u00e1 subiu para o servidor continua l\u00e1.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Apagar", (d, w) -> {
                            Repo.get(requireContext()).wipeAll();
                            Toast.makeText(requireContext(), "Dados locais apagados.",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show());
    }

    /** Exporta o banco local para um CSV e abre a folha de compartilhamento. */
    private void exportCsv() {
        java.io.File dir = requireContext().getExternalFilesDir(null);
        if (dir == null) dir = requireContext().getFilesDir();
        long to = System.currentTimeMillis();
        long from = to - 30L * 86_400_000L;
        Toast.makeText(requireContext(), "Gerando arquivo\u2026", Toast.LENGTH_SHORT).show();
        Repo.get(requireContext()).exportCsv(dir, from, to, file -> {
            if (!isAdded()) return;
            if (file == null) {
                Toast.makeText(requireContext(), "N\u00e3o foi poss\u00edvel gerar o arquivo.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(), "Salvo em " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        });
    }

    /** Linha de texto explicativo dentro de uma se\u00e7\u00e3o. */
    private void addNoteRow(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        tv.setTextSize(12);
        tv.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(10);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        parent.addView(tv, lp);
    }

    private void buildAccountSection() {
        LinearLayout content = addSection("\uD83D\uDC64", "Conta", false);
        addActionRow(content, "Alterar senha", 0xFFFF6B35, this::showChangePasswordDialog);
        addActionRow(content, "Excluir conta", 0xFFFF6B6B, this::confirmDeleteAccount);
    }

    private void showChangePasswordDialog() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(requireContext());
        title.setText("Alterar senha");
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        EditText current = ViewFactory.addLabeledInput(requireContext(), root, "Senha atual", "\u2022\u2022\u2022\u2022\u2022\u2022",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
        EditText newPass = ViewFactory.addLabeledInput(requireContext(), root, "Nova senha", "M\u00ednimo 8 caracteres",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);

        TextView btnConfirm = new TextView(requireContext());
        btnConfirm.setText("Salvar nova senha");
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setTextColor(0xFF000000);
        btnConfirm.setTypeface(btnConfirm.getTypeface(), android.graphics.Typeface.BOLD);
        btnConfirm.setBackgroundResource(R.drawable.bg_button_primary);
        btnConfirm.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(18);
        root.addView(btnConfirm, btnLp);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        btnConfirm.setOnClickListener(v -> {
            if (newPass.getText().length() < 8) { newPass.setError("Use pelo menos 8 caracteres."); newPass.requestFocus(); return; }
            if (newPass.getText().toString().equals(current.getText().toString())) {
                newPass.setError("A nova senha deve ser diferente da atual."); newPass.requestFocus(); return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("currentPassword", current.getText().toString());
            body.put("newPassword", newPass.getText().toString());
            ApiClient.get(requireContext()).changePassword(body).enqueue(new Callback<OkResponse>() {
                @Override
                public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                    if (!isAdded()) return;
                    if (response.isSuccessful()) {
                        // O servidor encerra as outras sess\u00f5es e entrega um par novo para esta.
                        if (response.body() != null && response.body().token != null) {
                            SessionManager.saveTokens(requireContext(), response.body().token, response.body().refreshToken);
                        }
                        Toast.makeText(requireContext(), "Senha alterada. Os outros aparelhos foram desconectados.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onFailure(Call<OkResponse> call, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void confirmDeleteAccount() {
        final EditText password = new EditText(requireContext());
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Sua senha");
        LinearLayout box = new LinearLayout(requireContext());
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(password, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(requireContext())
                .setTitle("Excluir conta")
                .setMessage("Isso apaga permanentemente sua conta, seus treinos enviados, posts e conquistas do servidor. "
                        + "N\u00e3o d\u00e1 para desfazer. Digite sua senha para confirmar.")
                .setView(box)
                .setPositiveButton("Excluir para sempre", (d, w) -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("password", password.getText().toString());
                    ApiClient.get(requireContext()).deleteAccount(body).enqueue(new Callback<OkResponse>() {
                        @Override
                        public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                            if (!isAdded()) return;
                            if (response.isSuccessful()) {
                                SessionManager.clearToken(requireContext());
                                nav().navigate(Navigator.LOGIN);
                            } else {
                                Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_LONG).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<OkResponse> call, Throwable t) {
                            if (isAdded()) Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void buildHelpSection() {
        LinearLayout content = addSection("\u2753", "Ajuda e suporte", false);
        addActionRow(content, "Ajuda, perguntas frequentes e diagn\u00f3stico", ContextCompat.getColor(requireContext(), R.color.text_primary),
                () -> nav().navigate(Navigator.HELP));
        addActionRow(content, "Rever as boas-vindas", ContextCompat.getColor(requireContext(), R.color.text_primary),
                () -> nav().navigate(Navigator.ONBOARDING));
        addNoteRow(content, "StepVolt, vers\u00e3o " + appVersion() + ".");
    }

    private String appVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
