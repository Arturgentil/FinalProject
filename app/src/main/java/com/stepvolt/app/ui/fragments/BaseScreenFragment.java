package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;

/**
 * Esqueleto comum das telas novas: cabeçalho com título, subtítulo opcional,
 * botão de voltar e espaço para ícones de ação, e abaixo uma área rolável onde
 * cada tela monta o próprio conteúdo.
 */
public abstract class BaseScreenFragment extends Fragment {

    private TextView txtTitle;
    private TextView txtSubtitle;
    private ImageButton btnBack;
    private LinearLayout actionSlot;
    protected LinearLayout content;

    /** Título fixo da tela. */
    protected abstract String screenTitle();

    /** true nas telas internas, que ganham o botão de voltar. */
    protected boolean showsBack() { return false; }

    /** Monta o conteúdo. Chamado uma vez, em onViewCreated. */
    protected abstract void buildContent();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.screen_scaffold, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        txtTitle = view.findViewById(R.id.txtTitle);
        txtSubtitle = view.findViewById(R.id.txtSubtitle);
        btnBack = view.findViewById(R.id.btnBack);
        actionSlot = view.findViewById(R.id.actionSlot);
        content = view.findViewById(R.id.content);

        txtTitle.setText(screenTitle());
        btnBack.setVisibility(showsBack() ? View.VISIBLE : View.GONE);
        btnBack.setOnClickListener(v -> nav().goBack());

        buildContent();
    }

    protected void setSubtitle(String text) {
        if (txtSubtitle == null) return;
        if (text == null || text.isEmpty()) {
            txtSubtitle.setVisibility(View.GONE);
        } else {
            txtSubtitle.setText(text);
            txtSubtitle.setVisibility(View.VISIBLE);
        }
    }

    protected void setTitle(String text) {
        if (txtTitle != null) txtTitle.setText(text);
    }

    protected ImageButton addAction(int iconRes, String description, View.OnClickListener onClick) {
        ImageButton b = new ImageButton(requireContext());
        b.setImageResource(iconRes);
        b.setBackgroundResource(R.drawable.bg_icon_button);
        b.setColorFilter(0xFF9A9AB8);
        b.setContentDescription(description);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(UiKit.dp(requireContext(), 8), UiKit.dp(requireContext(), 8),
                UiKit.dp(requireContext(), 8), UiKit.dp(requireContext(), 8));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                UiKit.dp(requireContext(), 36), UiKit.dp(requireContext(), 36));
        lp.leftMargin = UiKit.dp(requireContext(), 8);
        actionSlot.addView(b, lp);
        return b;
    }

    protected void addBlock(View v, int topMarginDp) {
        if (content == null || v == null) return;
        content.addView(v, UiKit.matchWrap(requireContext(), topMarginDp));
    }

    protected Navigator nav() {
        return (Navigator) requireActivity();
    }

    protected AppState app() {
        return AppState.get();
    }

    protected Telemetry telemetry() {
        return AppState.get().telemetry;
    }

    protected void toast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    protected int dp(int value) {
        return UiKit.dp(requireContext(), value);
    }
}
