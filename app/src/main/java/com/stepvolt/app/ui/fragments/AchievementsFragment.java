package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.stepvolt.app.models.Achievement;
import com.stepvolt.app.ui.Navigator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AchievementsFragment extends Fragment {

    private TextView badgeTotal, txtProgressLabel;
    private ProgressBar progressTotal;
    private LinearLayout categoriesContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_achievements, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().navigate(Navigator.PROFILE));

        badgeTotal = view.findViewById(R.id.badgeTotal);
        txtProgressLabel = view.findViewById(R.id.txtProgressLabel);
        progressTotal = view.findViewById(R.id.progressTotal);
        categoriesContainer = view.findViewById(R.id.categoriesContainer);

        AppState.get().refreshAchievements((success, data, error) -> {
            if (!isAdded()) return;
            if (success) render(data);
            else Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        });
    }

    private void render(List<Achievement> all) {
        int unlocked = 0;
        for (Achievement a : all) if (a.unlocked) unlocked++;
        int total = all.size();

        badgeTotal.setText("\uD83C\uDFC6 " + unlocked + "/" + total);
        txtProgressLabel.setText(unlocked + " de " + total + " desbloqueadas");
        progressTotal.setMax(100);
        progressTotal.setProgress(total > 0 ? Math.round(unlocked * 100f / total) : 0);

        categoriesContainer.removeAllViews();
        Map<String, List<Achievement>> byCategory = new LinkedHashMap<>();
        for (Achievement a : all) {
            byCategory.computeIfAbsent(a.category, k -> new java.util.ArrayList<>()).add(a);
        }
        for (Map.Entry<String, List<Achievement>> entry : byCategory.entrySet()) {
            categoriesContainer.addView(buildCategorySection(entry.getKey(), entry.getValue()));
        }
    }

    private View buildCategorySection(String category, List<Achievement> items) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.bottomMargin = dp(18);
        section.setLayoutParams(sectionLp);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        int unlockedCount = 0;
        for (Achievement a : items) if (a.unlocked) unlockedCount++;
        TextView title = new TextView(requireContext());
        title.setText(category);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleLp);
        TextView count = new TextView(requireContext());
        count.setText(unlockedCount + "/" + items.size());
        count.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        count.setTextSize(13);
        header.addView(count);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(10);
        section.addView(header, headerLp);

        for (Achievement a : items) {
            section.addView(buildAchievementRow(a));
        }
        return section;
    }

    private View buildAchievementRow(Achievement a) {
        int rarityColor = rarityColor(a.rarity);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setAlpha(a.unlocked ? 1f : 0.55f);
        row.setBackgroundColor(a.unlocked ? (rarityColor & 0x00FFFFFF | 0x14000000) : 0x08FFFFFF);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);

        TextView icon = new TextView(requireContext());
        icon.setText(a.unlocked ? a.icon : "\uD83D\uDD12");
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        row.addView(icon, iconLp);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(requireContext());
        name.setText(a.name);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(name);
        TextView rarity = new TextView(requireContext());
        rarity.setText(Achievement.rarityLabel(a.rarity));
        rarity.setTextColor(rarityColor);
        rarity.setTextSize(9);
        rarity.setTypeface(rarity.getTypeface(), android.graphics.Typeface.BOLD);
        rarity.setPadding(dp(6), dp(2), dp(6), dp(2));
        LinearLayout.LayoutParams rarityLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rarityLp.leftMargin = dp(8);
        titleRow.addView(rarity, rarityLp);
        col.addView(titleRow);

        TextView desc = new TextView(requireContext());
        desc.setText(a.desc);
        desc.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
        desc.setTextSize(12);
        col.addView(desc);

        if (a.unlocked && a.date != null) {
            TextView date = new TextView(requireContext());
            date.setText("Desbloqueada em " + a.date);
            date.setTextColor(rarityColor);
            date.setTextSize(11);
            date.setTypeface(date.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dateLp.topMargin = dp(2);
            col.addView(date, dateLp);
        }

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(10);
        row.addView(col, colLp);

        return row;
    }

    private int rarityColor(Achievement.Rarity r) {
        switch (r) {
            case RARO: return 0xFF00B4D8;
            case EPICO: return 0xFF7C3AFF;
            case LENDARIO: return 0xFFFFC800;
            default: return 0xFF8B8BA7;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
