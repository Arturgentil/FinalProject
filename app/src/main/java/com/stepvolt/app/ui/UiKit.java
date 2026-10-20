package com.stepvolt.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.R;
import com.stepvolt.app.ui.views.SparklineView;

/**
 * Peças de interface repetidas nas telas novas.
 *
 * Boa parte do conteúdo do StepVolt é dinâmico — a lista de sensores depende do
 * que o firmware manda, a de dispositivos depende da varredura, a de análises
 * depende do período. Montar esses trechos em código evita um XML enorme cheio
 * de views escondidas, e manter os construtores num só lugar é o que garante
 * que tudo saia com o mesmo espaçamento, a mesma tipografia e o mesmo raio.
 */
public final class UiKit {

    private UiKit() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    /** Cartão padrão: fundo em degradê, borda de 1dp, cantos de 20dp. */
    public static LinearLayout panel(Context c) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundResource(R.drawable.bg_card);
        p.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        return p;
    }

    public static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    public static TextView sectionTitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
        t.setTextSize(13);
        return t;
    }

    public static TextView caption(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, R.color.text_muted));
        t.setTextSize(11);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView readout(Context c, String text, int sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sizeSp);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(ContextCompat.getColor(c, R.color.glass_border));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2));
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 12);
        v.setLayoutParams(lp);
        return v;
    }

    /** Linha "rótulo à esquerda, valor à direita" — usada nos painéis técnicos. */
    public static LinearLayout keyValue(Context c, String key, String value) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 6), 0, dp(c, 6));
        TextView k = label(c, key);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(k, kp);
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        v.setTextSize(13);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        r.addView(v);
        return r;
    }

    public static TextView primaryButton(Context c, String text, View.OnClickListener onClick) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(0xFF000000);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setBackgroundResource(R.drawable.bg_button_primary);
        b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    public static TextView ghostButton(Context c, String text, View.OnClickListener onClick) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackgroundResource(R.drawable.bg_button_ghost);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    public static TextView dangerButton(Context c, String text, View.OnClickListener onClick) {
        TextView b = ghostButton(c, text, onClick);
        b.setBackgroundResource(R.drawable.bg_button_danger);
        b.setTextColor(0xFFFF5C7A);
        return b;
    }

    public interface OnChipSelected {
        void onSelected(int index);
    }

    /** Fileira de filtros por período. O selecionado fica com o fundo da marca. */
    public static HorizontalScrollView chips(Context c, String[] items, int selectedIndex,
                                             final OnChipSelected callback) {
        final LinearLayout strip = row(c);
        strip.setPadding(0, 0, 0, 0);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView chip = new TextView(c);
            chip.setText(items[i]);
            chip.setTextSize(13);
            chip.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
            boolean on = i == selectedIndex;
            chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            chip.setTextColor(on ? 0xFF00F5A0 : ContextCompat.getColor(c, R.color.text_secondary));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setTag(index);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dp(c, 8);
            chip.setOnClickListener(v -> {
                for (int j = 0; j < strip.getChildCount(); j++) {
                    TextView other = (TextView) strip.getChildAt(j);
                    boolean isOn = ((Integer) other.getTag()) == index;
                    other.setBackgroundResource(isOn ? R.drawable.bg_chip_on : R.drawable.bg_chip);
                    other.setTextColor(isOn ? 0xFF00F5A0 : ContextCompat.getColor(c, R.color.text_secondary));
                }
                callback.onSelected(index);
            });
            strip.addView(chip, lp);
        }

        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(strip);
        return scroll;
    }

    /** Bloco quadrado com ícone, número grande e rótulo. */
    public static LinearLayout statTile(Context c, int iconRes, String value, String unit,
                                        String labelText, int accent, int tintRes) {
        LinearLayout tile = panel(c);
        tile.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));

        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setBackgroundResource(tintRes);
        icon.setPadding(dp(c, 7), dp(c, 7), dp(c, 7), dp(c, 7));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));

        LinearLayout valueRow = row(c);
        TextView v = readout(c, value, 24, ContextCompat.getColor(c, R.color.text_primary));
        valueRow.addView(v);
        if (unit != null && !unit.isEmpty()) {
            TextView u = new TextView(c);
            u.setText(" " + unit);
            u.setTextColor(ContextCompat.getColor(c, R.color.text_muted));
            u.setTextSize(11);
            valueRow.addView(u);
        }
        tile.addView(valueRow, matchWrap(c, 10));
        tile.addView(caption(c, labelText), matchWrap(c, 2));
        return tile;
    }

    /** Linha de sensor: nome, leitura, quando chegou e a miniatura da série. */
    public static LinearLayout metricRow(Context c, String name, String valueText, String meta,
                                         int accent, float[] series) {
        LinearLayout p = panel(c);

        LinearLayout head = row(c);
        LinearLayout titleCol = new LinearLayout(c);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.addView(sectionTitle(c, name));
        titleCol.addView(caption(c, meta), matchWrap(c, 2));
        head.addView(titleCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = readout(c, valueText, 26, accent);
        head.addView(value);
        p.addView(head);

        if (series != null && series.length > 1) {
            SparklineView spark = new SparklineView(c);
            spark.setAccent(accent);
            spark.setCapacity(series.length);
            spark.setValues(series);
            LinearLayout.LayoutParams lp = matchWrap(c, 12);
            lp.height = dp(c, 44);
            p.addView(spark, lp);
        }
        return p;
    }

    /** Estado vazio: diz o que falta fazer, não só que não há nada. */
    public static LinearLayout emptyState(Context c, String title, String body) {
        LinearLayout p = panel(c);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        p.setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 28));
        TextView t = sectionTitle(c, title);
        t.setGravity(Gravity.CENTER);
        p.addView(t);
        TextView b = caption(c, body);
        b.setGravity(Gravity.CENTER);
        p.addView(b, matchWrap(c, 8));
        return p;
    }

    /** Aviso/anotação com uma barra colorida à esquerda. */
    public static LinearLayout note(Context c, String text, int accent) {
        LinearLayout p = row(c);
        p.setBackgroundResource(R.drawable.bg_card_flat);
        p.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));

        View bar = new View(c);
        bar.setBackgroundColor(accent);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(c, 3), dp(c, 28));
        barLp.rightMargin = dp(c, 12);
        p.addView(bar, barLp);

        TextView t = caption(c, text);
        p.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return p;
    }

    /** Item clicável de navegação: ícone, título, descrição e seta. */
    public static LinearLayout linkRow(Context c, int iconRes, String title, String subtitle,
                                       int accent, View.OnClickListener onClick) {
        LinearLayout r = row(c);
        r.setBackgroundResource(R.drawable.bg_card);
        r.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        r.setClickable(true);
        r.setFocusable(true);
        r.setOnClickListener(onClick);
        addRipple(r);

        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setPadding(dp(c, 9), dp(c, 9), dp(c, 9), dp(c, 9));
        // Ladrilho na cor do próprio ícone (14% de opacidade): cada atalho ganha identidade sem poluir.
        android.graphics.drawable.GradientDrawable tile = new android.graphics.drawable.GradientDrawable();
        tile.setCornerRadius(dp(c, 12));
        tile.setColor((accent & 0x00FFFFFF) | 0x24000000);
        icon.setBackground(tile);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
        ip.rightMargin = dp(c, 14);
        r.addView(icon, ip);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            col.addView(caption(c, subtitle));
        }
        r.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ContextCompat.getColor(c, R.color.text_faint));
        r.addView(chevron, new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)));
        return r;
    }

    /** Retorno visual ao toque (onda) por cima do fundo. Sem isto um cartão clicável parece "morto". */
    public static void addRipple(View v) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            v.setForeground(androidx.core.content.ContextCompat.getDrawable(v.getContext(), tv.resourceId));
        }
    }
}
