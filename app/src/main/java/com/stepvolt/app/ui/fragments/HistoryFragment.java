package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment {

    private final String[] periods = {"hoje", "semana", "m\u00eas", "total"};
    private String activePeriod = "total";
    private final String[] sorts = {"data", "passos", "energia"};
    private String activeSort = "data";

    private LinearLayout periodContainer, sortContainer, activitiesContainer;
    private TextView txtTotalSteps, txtTotalEnergy, txtAvgBpm, txtAvgSpo2;

    /** Sessões gravadas no próprio aparelho — existem mesmo sem servidor. */
    private final List<SessionRecord> localSessions = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().goBack());

        periodContainer = view.findViewById(R.id.periodContainer);
        sortContainer = view.findViewById(R.id.sortContainer);
        activitiesContainer = view.findViewById(R.id.activitiesContainer);
        txtTotalSteps = view.findViewById(R.id.txtTotalSteps);
        txtTotalEnergy = view.findViewById(R.id.txtTotalEnergy);
        txtAvgBpm = view.findViewById(R.id.txtAvgBpm);
        txtAvgSpo2 = view.findViewById(R.id.txtAvgSpo2);

        buildPeriodPills();
        buildSortPills();
        loadLocalSessions();
        loadActivities();
    }

    /**
     * O histórico tem duas origens: o que o tênis gravou aqui e o que o
     * servidor guardou. A lista local vem primeiro porque é a que existe mesmo
     * offline — e é dela que sai a fila de envio.
     */
    private void loadLocalSessions() {
        Repo.get(requireContext()).sessionsAsync(60, list -> {
            if (!isAdded()) return;
            localSessions.clear();
            if (list != null) localSessions.addAll(list);
            renderAll();
        });
    }

    private boolean matchesPeriod(long ts) {
        if (activePeriod.equals("total")) return true;
        long diffDays = (System.currentTimeMillis() - ts) / (1000L * 60 * 60 * 24);
        if (activePeriod.equals("hoje")) return diffDays == 0;
        if (activePeriod.equals("semana")) return diffDays <= 7;
        return diffDays <= 30;
    }

    private void renderLocalSessions() {
        List<SessionRecord> visible = new ArrayList<>();
        for (SessionRecord s : localSessions) {
            if (matchesPeriod(s.startedAt)) visible.add(s);
        }
        if (visible.isEmpty()) return;

        int pending = 0;
        for (SessionRecord s : visible) if (!s.synced) pending++;

        activitiesContainer.addView(UiKit.sectionTitle(requireContext(),
                "Sessões gravadas no aparelho"));
        if (pending > 0) {
            activitiesContainer.addView(UiKit.caption(requireContext(),
                    pending + (pending == 1 ? " sessão aguardando envio ao servidor."
                            : " sessões aguardando envio ao servidor.")),
                    UiKit.matchWrap(requireContext(), 4));
        }

        for (final SessionRecord s : visible) {
            LinearLayout card = UiKit.panel(requireContext());
            card.setPadding(dp(14), dp(14), dp(14), dp(14));

            LinearLayout head = UiKit.row(requireContext());
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(requireContext());
            title.setText(Fmt.date(s.startedAt) + " · " + Fmt.clock(s.startedAt));
            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            title.setTextSize(14);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            col.addView(title);
            col.addView(UiKit.caption(requireContext(),
                    Fmt.duration(s.durationMs()) + " · " + Fmt.intGrouped(s.steps) + " passos · "
                            + Fmt.energy(s.energy) + " mWh"
                            + (s.synced ? "" : " · na fila de envio")));
            head.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView chevron = new TextView(requireContext());
            chevron.setText(" \u203A");
            chevron.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
            chevron.setTextSize(18);
            head.addView(chevron);
            card.addView(head);

            card.setOnClickListener(v -> {
                android.os.Bundle b = new android.os.Bundle();
                b.putLong(SessionDetailsFragment.ARG_SESSION_ID, s.id);
                nav().navigate(Navigator.SESSION_DETAILS, b);
            });

            activitiesContainer.addView(card, UiKit.matchWrap(requireContext(), 10));
        }

        activitiesContainer.addView(UiKit.sectionTitle(requireContext(),
                "Atividades no servidor"), UiKit.matchWrap(requireContext(), 22));
    }

    private void loadActivities() {
        AppState.get().refreshActivities((success, data, error) -> {
            if (!isAdded()) return;
            if (success) renderAll();
            else Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        });
    }

    private void buildPeriodPills() {
        periodContainer.removeAllViews();
        for (String p : periods) {
            TextView tv = new TextView(requireContext());
            tv.setText(p);
            tv.setTextSize(12.5f);
            tv.setTypeface(tv.getTypeface(), p.equals(activePeriod) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(8), 0, dp(8));
            tv.setBackgroundResource(p.equals(activePeriod) ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
            tv.setTextColor(p.equals(activePeriod) ? ContextCompat.getColor(requireContext(), R.color.green) : ContextCompat.getColor(requireContext(), R.color.text_muted));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            periodContainer.addView(tv, lp);
            tv.setOnClickListener(v -> { activePeriod = p; buildPeriodPills(); renderAll(); });
        }
    }

    private void buildSortPills() {
        sortContainer.removeAllViews();
        for (String s : sorts) {
            TextView tv = new TextView(requireContext());
            tv.setText(s);
            tv.setTextSize(12);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setPadding(dp(12), dp(6), dp(12), dp(6));
            tv.setBackgroundResource(s.equals(activeSort) ? R.drawable.bg_pill_selected : R.drawable.bg_icon_button);
            tv.setTextColor(s.equals(activeSort) ? ContextCompat.getColor(requireContext(), R.color.green) : ContextCompat.getColor(requireContext(), R.color.text_muted));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            sortContainer.addView(tv, lp);
            tv.setOnClickListener(v -> { activeSort = s; buildSortPills(); renderAll(); });
        }
    }

    /** Filtra por período com base na data guardada como "dd/MM/yyyy" (comparação de string simples). */
    private boolean matchesPeriod(ActivityRecord act) {
        if (activePeriod.equals("total")) return true;
        try {
            String[] parts = act.date.split("/");
            java.util.Calendar actCal = java.util.Calendar.getInstance();
            actCal.set(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[0]), 0, 0, 0);
            java.util.Calendar now = java.util.Calendar.getInstance();
            long diffDays = (now.getTimeInMillis() - actCal.getTimeInMillis()) / (1000L * 60 * 60 * 24);
            if (activePeriod.equals("hoje")) return diffDays == 0;
            if (activePeriod.equals("semana")) return diffDays >= 0 && diffDays <= 7;
            return diffDays >= 0 && diffDays <= 30; // "mês"
        } catch (Exception e) {
            return true;
        }
    }

    private void renderAll() {
        List<ActivityRecord> sorted = new ArrayList<>();
        for (ActivityRecord a : AppState.get().activities) {
            if (matchesPeriod(a)) sorted.add(a);
        }
        if (activeSort.equals("passos")) {
            Collections.sort(sorted, (a, b) -> Long.compare(b.steps, a.steps));
        } else if (activeSort.equals("energia")) {
            Collections.sort(sorted, (a, b) -> Double.compare(b.energy, a.energy));
        }

        long totalSteps = 0;
        double totalEnergy = 0;
        int bpmSum = 0;
        for (ActivityRecord a : sorted) {
            totalSteps += a.steps;
            totalEnergy += a.energy;
            bpmSum += a.bpmAvg;
        }
        int avgBpm = sorted.isEmpty() ? 0 : Math.round(bpmSum / (float) sorted.size());

        txtTotalSteps.setText(Fmt.intGrouped(totalSteps));
        txtTotalEnergy.setText(Fmt.energy(totalEnergy) + " mWh");
        txtAvgBpm.setText(avgBpm + " bpm");
        txtAvgSpo2.setText(averageSpo2(sorted) + "%");

        activitiesContainer.removeAllViews();
        renderLocalSessions();
        if (sorted.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(localSessions.isEmpty()
                    ? "Nenhuma atividade neste per\u00edodo ainda. Conecte o t\u00eanis, inicie uma sess\u00e3o no In\u00edcio e ela aparece aqui."
                    : "Nada enviado ao servidor neste per\u00edodo. As sess\u00f5es acima sobem sozinhas quando houver rede.");
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            empty.setPadding(0, dp(20), 0, dp(20));
            activitiesContainer.addView(empty);
            return;
        }
        for (ActivityRecord act : sorted) {
            activitiesContainer.addView(buildActivityRow(act));
        }
    }

    private int averageSpo2(List<ActivityRecord> list) {
        int sum = 0, count = 0;
        for (ActivityRecord a : list) {
            for (int v : a.spo2Samples) { sum += v; count++; }
        }
        return count == 0 ? 0 : Math.round(sum / (float) count);
    }

    private View buildActivityRow(ActivityRecord act) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView date = new TextView(requireContext());
        date.setText(act.date);
        date.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        date.setTextSize(14);
        date.setTypeface(date.getTypeface(), android.graphics.Typeface.BOLD);
        TextView time = new TextView(requireContext());
        time.setText(act.time + " \u2192 " + act.endTime);
        time.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        time.setTextSize(12);
        col.addView(date);
        col.addView(time);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(col, colLp);
        if (act.shared) {
            TextView shared = new TextView(requireContext());
            shared.setText("\uD83D\uDD17");
            shared.setTextSize(16);
            header.addView(shared);
        }
        TextView chevron = new TextView(requireContext());
        chevron.setText(" \u203A");
        chevron.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        chevron.setTextSize(18);
        header.addView(chevron);
        card.addView(header);

        LinearLayout statsRow = new LinearLayout(requireContext());
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statsRowLp.topMargin = dp(10);
        statsRow.setLayoutParams(statsRowLp);
        statsRow.addView(statChip("\uD83D\uDC5F " + Fmt.intGrouped(act.steps), 0xFFFFC800));
        statsRow.addView(statChip("\u26A1 " + Fmt.energy(act.energy) + " mWh", 0xFF00F5A0));
        statsRow.addView(statChip(act.duration + " min", 0xFF8B8BA7));
        card.addView(statsRow);

        card.setOnClickListener(v -> {
            android.os.Bundle b = new android.os.Bundle();
            b.putSerializable("activity", act);
            nav().navigate(Navigator.ACTIVITY_DETAILS, b);
        });

        return card;
    }

    private TextView statChip(String text, int color) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12.5f);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(14));
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
