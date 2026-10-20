package com.stepvolt.app.ui.fragments;

import android.graphics.Color;
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
import com.stepvolt.app.models.ShoeCustomization;
import com.stepvolt.app.models.User;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.views.ShoeView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShoeCustomizationFragment extends Fragment {

    private static final String[] COLOR_PRESETS = {
            "#00F5A0", "#00B4D8", "#7C3AFF", "#FF6B35",
            "#FF3366", "#FFC800", "#FFFFFF", "#1A1A2E",
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96E6A1"
    };

    private static class Theme {
        final String id, label, upper, sole, laces, details;
        Theme(String id, String label, String upper, String sole, String laces, String details) {
            this.id = id; this.label = label; this.upper = upper; this.sole = sole; this.laces = laces; this.details = details;
        }
    }

    private static final Theme[] THEMES = {
            new Theme("neon", "Neon", "#00F5A0", "#1A1A2E", "#FFFFFF", "#7C3AFF"),
            new Theme("sport", "Esportivo", "#FF6B35", "#FFFFFF", "#1A1A2E", "#FF3366"),
            new Theme("urban", "Urbano", "#4A4A6A", "#2A2A3A", "#00F5A0", "#8B8BA7"),
            new Theme("minimal", "Minimalista", "#FFFFFF", "#F0F0F0", "#4A4A6A", "#1A1A2E"),
    };

    private static final String[] PART_KEYS = {"upper", "sole", "laces", "details"};
    private static final String[] PART_LABELS = {"Cabedal", "Solado", "Cadar\u00e7os", "Detalhes"};

    private ShoeCustomization colors;
    private String selectedPart = "upper";

    private ShoeView shoePreview;
    private LinearLayout partSelector, colorGrid, themesContainer;
    private TextView txtColorFor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shoe_customization, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        colors = AppState.get().user.shoeCustomization.copy();

        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().navigate(Navigator.PROFILE));

        shoePreview = view.findViewById(R.id.shoePreview);
        partSelector = view.findViewById(R.id.partSelector);
        colorGrid = view.findViewById(R.id.colorGrid);
        themesContainer = view.findViewById(R.id.themesContainer);
        txtColorFor = view.findViewById(R.id.txtColorFor);
        view.findViewById(R.id.btnSave).setOnClickListener(v -> save());

        buildPartSelector();
        buildColorGrid();
        buildThemes();
        renderPreview();
    }

    private String colorOf(String part) {
        switch (part) {
            case "sole": return colors.sole;
            case "laces": return colors.laces;
            case "details": return colors.details;
            default: return colors.upper;
        }
    }

    private void setColorOf(String part, String hex) {
        switch (part) {
            case "sole": colors.sole = hex; break;
            case "laces": colors.laces = hex; break;
            case "details": colors.details = hex; break;
            default: colors.upper = hex; break;
        }
    }

    private void buildPartSelector() {
        partSelector.removeAllViews();
        for (int i = 0; i < PART_KEYS.length; i++) {
            String key = PART_KEYS[i];
            String label = PART_LABELS[i];

            LinearLayout btn = new LinearLayout(requireContext());
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(6), dp(10), dp(6), dp(10));
            btn.setBackgroundResource(key.equals(selectedPart) ? R.drawable.bg_pill_selected : R.drawable.bg_icon_button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(i == 0 ? 0 : dp(6));
            partSelector.addView(btn, lp);

            View dot = new View(requireContext());
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            try { dot.setBackgroundColor(Color.parseColor(colorOf(key))); } catch (Exception ignored) {}
            btn.addView(dot, dotLp);

            TextView tv = new TextView(requireContext());
            tv.setText(label);
            tv.setTextSize(10.5f);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setTextColor(key.equals(selectedPart) ? ContextCompat.getColor(requireContext(), R.color.green) : ContextCompat.getColor(requireContext(), R.color.text_muted));
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = dp(4);
            btn.addView(tv, tvLp);

            btn.setOnClickListener(v -> {
                selectedPart = key;
                buildPartSelector();
                buildColorGrid();
            });
        }
    }

    private void buildColorGrid() {
        colorGrid.removeAllViews();
        txtColorFor.setText("Cor do " + labelFor(selectedPart).toLowerCase());

        int columns = 6;
        LinearLayout row = null;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = dp(10);
                colorGrid.addView(row, rowLp);
            }
            String hex = COLOR_PRESETS[i];
            boolean selected = hex.equalsIgnoreCase(colorOf(selectedPart));

            FrameSquare square = new FrameSquare(requireContext());
            square.setBackgroundColor(Color.parseColor(hex));
            LinearLayout.LayoutParams sqLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            sqLp.leftMargin = i % columns == 0 ? 0 : dp(8);
            row.addView(square, sqLp);
            square.setSelectedRing(selected, hex.equalsIgnoreCase("#FFFFFF"));

            square.setOnClickListener(v -> {
                setColorOf(selectedPart, hex);
                buildPartSelector();
                buildColorGrid();
                renderPreview();
            });
        }
    }

    /** Small square swatch that draws a check mark + border ring when selected. */
    private static class FrameSquare extends android.widget.FrameLayout {
        private final TextView check;
        FrameSquare(android.content.Context ctx) {
            super(ctx);
            check = new TextView(ctx);
            check.setText("\u2713");
            check.setTextSize(14);
            check.setGravity(Gravity.CENTER);
            addView(check, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        void setSelectedRing(boolean selected, boolean isWhite) {
            check.setVisibility(selected ? VISIBLE : INVISIBLE);
            check.setTextColor(isWhite ? Color.BLACK : Color.WHITE);
            setForeground(selected ? new android.graphics.drawable.ColorDrawable(0x22FFFFFF) : null);
        }
    }

    private String labelFor(String key) {
        for (int i = 0; i < PART_KEYS.length; i++) if (PART_KEYS[i].equals(key)) return PART_LABELS[i];
        return key;
    }

    private void buildThemes() {
        themesContainer.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < THEMES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = dp(10);
                themesContainer.addView(row, rowLp);
            }
            Theme theme = THEMES[i];
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackgroundResource(theme.id.equals(colors.theme) ? R.drawable.bg_card_accent_green : R.drawable.bg_icon_button);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardLp.setMarginStart(i % 2 == 0 ? 0 : dp(8));
            row.addView(card, cardLp);

            for (String hex : new String[]{theme.upper, theme.sole, theme.laces, theme.details}) {
                View dot = new View(requireContext());
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
                dotLp.setMarginEnd(dp(3));
                try { dot.setBackgroundColor(Color.parseColor(hex)); } catch (Exception ignored) {}
                card.addView(dot, dotLp);
            }

            TextView label = new TextView(requireContext());
            label.setText(theme.label);
            label.setTextSize(12.5f);
            label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            label.setTextColor(theme.id.equals(colors.theme) ? ContextCompat.getColor(requireContext(), R.color.green) : ContextCompat.getColor(requireContext(), R.color.text_secondary));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.leftMargin = dp(6);
            card.addView(label, labelLp);

            card.setOnClickListener(v -> {
                colors = new ShoeCustomization(theme.upper, theme.sole, theme.laces, theme.details, theme.id);
                buildPartSelector();
                buildColorGrid();
                buildThemes();
                renderPreview();
            });
        }
    }

    private void renderPreview() {
        shoePreview.setColors(colors.upper, colors.sole, colors.laces, colors.details);
    }

    private void save() {
        Map<String, Object> body = new HashMap<>();
        body.put("upper", colors.upper);
        body.put("sole", colors.sole);
        body.put("laces", colors.laces);
        body.put("details", colors.details);
        body.put("theme", colors.theme);

        ApiClient.get(requireContext()).updateShoe(body).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    AppState.get().user = response.body();
                    Toast.makeText(requireContext(), "Personaliza\u00e7\u00e3o salva!", Toast.LENGTH_SHORT).show();
                    nav().navigate(Navigator.PROFILE);
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "N\u00e3o foi poss\u00edvel salvar (sem conex\u00e3o com o servidor).", Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
