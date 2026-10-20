package com.stepvolt.app.ui.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.Goal;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.util.Fmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GoalsFragment extends Fragment {

    private LinearLayout containerActive, containerCompleted;
    private TextView txtActiveCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_goals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().navigate(Navigator.DASHBOARD));
        view.findViewById(R.id.btnAddGoal).setOnClickListener(v -> showCreateGoalDialog());

        containerActive = view.findViewById(R.id.containerActiveGoals);
        containerCompleted = view.findViewById(R.id.containerCompletedGoals);
        txtActiveCount = view.findViewById(R.id.txtActiveCount);

        loadGoals();
    }

    private void loadGoals() {
        AppState.get().refreshGoals((success, data, error) -> {
            if (!isAdded()) return;
            if (success) {
                renderActive();
                renderCompleted();
            } else {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderActive() {
        containerActive.removeAllViews();
        List<Goal> goals = AppState.get().activeGoals;
        txtActiveCount.setText("Metas ativas (" + goals.size() + ")");
        for (Goal goal : goals) {
            containerActive.addView(buildActiveGoalCard(goal));
        }
    }

    private View buildActiveGoalCard(Goal goal) {
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

        TextView icon = new TextView(requireContext());
        icon.setText(iconFor(goal.type));
        icon.setTextSize(20);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_icon_button);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        header.addView(icon, iconLp);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(requireContext());
        title.setText(Goal.label(goal.type));
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(title, titleLp);
        TextView period = new TextView(requireContext());
        period.setText(Goal.periodLabel(goal.period));
        period.setTextColor(colorFor(goal.type));
        period.setTextSize(11);
        period.setTypeface(period.getTypeface(), android.graphics.Typeface.BOLD);
        period.setBackgroundResource(R.drawable.bg_pill_selected);
        period.setPadding(dp(8), dp(3), dp(8), dp(3));
        titleRow.addView(period);
        col.addView(titleRow);

        TextView progressText = new TextView(requireContext());
        progressText.setText(Fmt.intGrouped((long) goal.current) + " / " + Fmt.intGrouped((long) goal.target) + " " + Goal.unit(goal.type));
        progressText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        progressText.setTextSize(13);
        col.addView(progressText);

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(10);
        header.addView(col, colLp);
        card.addView(header);

        ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int pct = goal.target > 0 ? (int) Math.min(100, Math.round((goal.current / goal.target) * 100)) : 0;
        bar.setProgress(pct);
        bar.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.progress_generic));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(12);
        card.addView(bar, barLp);

        TextView pctText = new TextView(requireContext());
        pctText.setText(pct >= 100 ? "\u2705 Conclu\u00edda!" : pct + "%");
        pctText.setTextColor(pct >= 100 ? ContextCompat.getColor(requireContext(), R.color.green) : ContextCompat.getColor(requireContext(), R.color.text_muted));
        pctText.setTextSize(12);
        pctText.setGravity(Gravity.END);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pctLp.topMargin = dp(4);
        card.addView(pctText, pctLp);

        if (!goal.type.equals(Goal.GoalType.PASSOS) && !goal.type.equals(Goal.GoalType.ENERGIA)) {
            TextView note = new TextView(requireContext());
            note.setText("Este tipo de meta n\u00e3o \u00e9 atualizado automaticamente ainda.");
            note.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
            note.setTextSize(10.5f);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            noteLp.topMargin = dp(4);
            card.addView(note, noteLp);
        }

        return card;
    }

    private void renderCompleted() {
        containerCompleted.removeAllViews();
        for (Goal goal : AppState.get().completedGoals) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_accent_green);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);

            TextView icon = new TextView(requireContext());
            icon.setText(iconFor(goal.type));
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            row.addView(icon, iconLp);

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(requireContext());
            t1.setText(Goal.label(goal.type) + " " + Goal.periodLabel(goal.period));
            t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            t1.setTextSize(13);
            t1.setTypeface(t1.getTypeface(), android.graphics.Typeface.BOLD);
            TextView t2 = new TextView(requireContext());
            t2.setText("Meta: " + Fmt.intGrouped((long) goal.target) + " " + Goal.unit(goal.type));
            t2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            t2.setTextSize(12);
            col.addView(t1);
            col.addView(t2);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            colLp.leftMargin = dp(10);
            row.addView(col, colLp);

            TextView date = new TextView(requireContext());
            date.setText(goal.completedDate);
            date.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
            date.setTextSize(10);
            row.addView(date);

            containerCompleted.addView(row);
        }
    }

    private void showCreateGoalDialog() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(requireContext());
        title.setText("\uD83C\uDFAF Nova meta");
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView typeLabel = new TextView(requireContext());
        typeLabel.setText("Tipo");
        typeLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        typeLabel.setTextSize(13);
        LinearLayout.LayoutParams typeLabelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeLabelLp.topMargin = dp(16);
        root.addView(typeLabel, typeLabelLp);

        LinearLayout typeRow = new LinearLayout(requireContext());
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams typeRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeRowLp.topMargin = dp(8);
        root.addView(typeRow, typeRowLp);

        Goal.GoalType[] types = Goal.GoalType.values();
        TextView[] typeButtons = new TextView[types.length];
        int[] selectedType = {0};

        EditText targetInput = new EditText(requireContext());
        TextView unitLabel = new TextView(requireContext());

        for (int i = 0; i < types.length; i++) {
            int idx = i;
            TextView btn = new TextView(requireContext());
            btn.setText(Goal.label(types[i]));
            btn.setTextSize(11);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(4), dp(12), dp(4), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(i == 0 ? 0 : dp(4));
            typeButtons[i] = btn;
            typeRow.addView(btn, lp);
            btn.setOnClickListener(v -> {
                selectedType[0] = idx;
                unitLabel.setText("Valor alvo (" + Goal.unit(types[idx]) + ")");
                for (int j = 0; j < typeButtons.length; j++) styleTypeButton(typeButtons[j], types[j], j == idx);
            });
        }
        styleTypeButton(typeButtons[0], types[0], true);

        unitLabel.setText("Valor alvo (" + Goal.unit(types[0]) + ")");
        unitLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        unitLabel.setTextSize(13);
        LinearLayout.LayoutParams unitLabelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        unitLabelLp.topMargin = dp(16);
        root.addView(unitLabel, unitLabelLp);

        LinearLayout inputRow = new LinearLayout(requireContext());
        inputRow.setBackgroundResource(R.drawable.bg_pill_input);
        inputRow.setPadding(dp(16), 0, dp(16), 0);
        targetInput.setHint("Ex: 10000");
        targetInput.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        targetInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        targetInput.setBackground(null);
        targetInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        targetInput.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(targetInput, targetLp);
        LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputRowLp.topMargin = dp(8);
        root.addView(inputRow, inputRowLp);

        TextView periodLabel = new TextView(requireContext());
        periodLabel.setText("Prazo");
        periodLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        periodLabel.setTextSize(13);
        LinearLayout.LayoutParams periodLabelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        periodLabelLp.topMargin = dp(16);
        root.addView(periodLabel, periodLabelLp);

        LinearLayout periodRow = new LinearLayout(requireContext());
        LinearLayout.LayoutParams periodRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        periodRowLp.topMargin = dp(8);
        root.addView(periodRow, periodRowLp);

        Goal.Period[] periods = Goal.Period.values();
        TextView[] periodButtons = new TextView[periods.length];
        int[] selectedPeriod = {0};
        for (int i = 0; i < periods.length; i++) {
            int idx = i;
            TextView btn = new TextView(requireContext());
            btn.setText(Goal.periodLabel(periods[i]));
            btn.setTextSize(12);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(4), dp(10), dp(4), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(i == 0 ? 0 : dp(6));
            periodButtons[i] = btn;
            periodRow.addView(btn, lp);
            btn.setOnClickListener(v -> {
                selectedPeriod[0] = idx;
                for (int j = 0; j < periodButtons.length; j++) stylePeriodButton(periodButtons[j], j == idx);
            });
        }
        stylePeriodButton(periodButtons[0], true);

        Button createBtn = new Button(requireContext());
        createBtn.setText("Criar meta");
        createBtn.setBackgroundResource(R.drawable.bg_button_primary);
        createBtn.setTextColor(Color.BLACK);
        createBtn.setTypeface(createBtn.getTypeface(), android.graphics.Typeface.BOLD);
        createBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = dp(20);
        root.addView(createBtn, createLp);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        createBtn.setOnClickListener(v -> {
            String targetStr = targetInput.getText().toString().trim();
            if (targetStr.isEmpty()) return;
            double target;
            try { target = Double.parseDouble(targetStr); } catch (Exception e) { return; }

            createBtn.setEnabled(false);
            Map<String, Object> body = new HashMap<>();
            body.put("type", types[selectedType[0]].name());
            body.put("target", target);
            body.put("period", periods[selectedPeriod[0]].name());

            ApiClient.get(requireContext()).createGoal(body).enqueue(new Callback<Goal>() {
                @Override
                public void onResponse(Call<Goal> call, Response<Goal> response) {
                    if (!isAdded()) return;
                    createBtn.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        AppState.get().activeGoals.add(response.body());
                        renderActive();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onFailure(Call<Goal> call, Throwable t) {
                    if (!isAdded()) return;
                    createBtn.setEnabled(true);
                    Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void styleTypeButton(TextView btn, Goal.GoalType t, boolean selected) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_pill_selected);
            btn.setTextColor(colorFor(t));
        } else {
            btn.setBackgroundResource(R.drawable.bg_icon_button);
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        }
    }

    private void stylePeriodButton(TextView btn, boolean selected) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_pill_selected);
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        } else {
            btn.setBackgroundResource(R.drawable.bg_icon_button);
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        }
    }

    private String iconFor(Goal.GoalType type) {
        switch (type) {
            case PASSOS: return "\uD83D\uDC5F";
            case ENERGIA: return "\u26A1";
            case BATIMENTOS: return "\u2764\uFE0F";
            default: return "\uD83D\uDCA7";
        }
    }

    private int colorFor(Goal.GoalType type) {
        switch (type) {
            case PASSOS: return 0xFFFFC800;
            case ENERGIA: return 0xFF00F5A0;
            case BATIMENTOS: return 0xFFFF6B6B;
            default: return 0xFF00B4D8;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
