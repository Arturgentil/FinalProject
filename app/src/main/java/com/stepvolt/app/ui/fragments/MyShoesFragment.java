package com.stepvolt.app.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.Shoe;
import com.stepvolt.app.energy.ShoeWear;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.List;

/**
 * Meus tênis: quilometragem acumulada (do GPS), passos, energia, bateria,
 * firmware e limite de troca. Para o tênis conectado, também calibra e
 * confirma se o tênis respondeu ao comando.
 */
public class MyShoesFragment extends BaseScreenFragment {

    private LinearLayout body;

    @Override protected String screenTitle() { return "Meus tênis"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 0);
        load();
    }

    private void load() {
        Repo.get(requireContext()).shoes.allAsync(list -> {
            if (!isAdded()) return;
            render(list);
        });
    }

    private void render(List<Shoe> shoes) {
        Context c = requireContext();
        body.removeAllViews();
        if (shoes == null || shoes.isEmpty()) {
            body.addView(UiKit.emptyState(c, "Nenhum tênis ainda",
                    "Conecte o tênis uma vez e ele aparece aqui, com a quilometragem e a bateria."), UiKit.matchWrap(c, 0));
            body.addView(UiKit.primaryButton(c, "Conectar o tênis", v -> nav().navigate(Navigator.DEVICE)),
                    UiKit.matchWrap(c, 16));
            return;
        }
        BleManager ble = app().bleManager;
        String connected = ble.isConnected() ? ble.getConnectedDeviceAddress() : null;
        for (Shoe s : shoes) {
            body.addView(shoeCard(s, s.address.equals(connected)), UiKit.matchWrap(c, 14));
        }
        body.addView(UiKit.caption(c, "A quilometragem vem do GPS dos treinos. Passos e energia contam sempre, com ou sem treino."),
                UiKit.matchWrap(c, 14));
    }

    private LinearLayout shoeCard(final Shoe s, boolean connected) {
        final Context c = requireContext();
        LinearLayout card = UiKit.panel(c);

        LinearLayout head = UiKit.row(c);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.sectionTitle(c, s.title() + (s.retired ? " (aposentado)" : "")));
        titles.addView(UiKit.caption(c, connected ? "Conectado agora" : "Desconectado"));
        head.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(UiKit.ghostButton(c, "Renomear", v -> rename(s)));
        card.addView(head);

        // Desgaste por quilometragem
        double km = s.distanceKm();
        ShoeWear.Level level = ShoeWear.level(km, s.retireKm);
        double remaining = s.retireKm - km;
        card.addView(UiKit.keyValue(c, "Quilometragem", Fmt.decimals(km, 1) + " de " + Fmt.decimals(s.retireKm, 0) + " km"),
                UiKit.matchWrap(c, 12));
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress((int) Math.min(100, ShoeWear.wearFraction(km, s.retireKm) * 100));
        card.addView(bar, UiKit.matchWrap(c, 6));
        int accent = level == ShoeWear.Level.GOOD ? 0xFF00F5A0
                : level == ShoeWear.Level.ATTENTION ? 0xFFFFB347 : 0xFFFF5C7A;
        card.addView(UiKit.note(c, ShoeWear.levelText(level, remaining), accent), UiKit.matchWrap(c, 8));

        card.addView(UiKit.keyValue(c, "Passos", Fmt.intGrouped(s.steps)), UiKit.matchWrap(c, 10));
        card.addView(UiKit.keyValue(c, "Energia gerada", Fmt.energy(s.energy) + " mWh"), UiKit.matchWrap(c, 6));
        card.addView(UiKit.keyValue(c, "Firmware", s.firmware == null || s.firmware.isEmpty() ? "não informado" : s.firmware),
                UiKit.matchWrap(c, 6));

        // Bateria
        if (s.batteryPct >= 0) {
            card.addView(UiKit.keyValue(c, "Bateria na última leitura",
                    Math.round(s.batteryPct) + "% (" + Fmt.ago(System.currentTimeMillis() - s.batteryUpdated) + ")"),
                    UiKit.matchWrap(c, 6));
            double cycles = s.batteryDischarged / 100.0;
            card.addView(UiKit.keyValue(c, "Ciclos equivalentes de descarga", Fmt.decimals(cycles, 1)), UiKit.matchWrap(c, 6));
            double used = ShoeWear.batteryLifeUsedFraction(cycles) * 100;
            card.addView(UiKit.caption(c, "Estimativa: cerca de " + Math.round(used) + "% da vida típica de uma bateria Li-ion ("
                    + (int) ShoeWear.TYPICAL_BATTERY_CYCLES + " ciclos). Não é medição de capacidade."), UiKit.matchWrap(c, 4));
        }

        // Ações
        LinearLayout actions = UiKit.row(c);
        if (connected) {
            actions.addView(UiKit.ghostButton(c, "Calibrar", v -> command("CALIBRATE", "Calibragem")),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = UiKit.dp(c, 8);
            actions.addView(UiKit.ghostButton(c, "Piscar LED", v -> command("BLINK", "Teste do LED")), lp);
            card.addView(actions, UiKit.matchWrap(c, 14));
        }
        LinearLayout row2 = UiKit.row(c);
        row2.addView(UiKit.ghostButton(c, "Limite de km", v -> editLimit(s)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = UiKit.dp(c, 8);
        row2.addView(UiKit.ghostButton(c, s.retired ? "Reativar" : "Aposentar", v -> {
            Repo.get(requireContext()).shoes.setRetired(s.address, !s.retired);
            reloadSoon();
        }), lp2);
        card.addView(row2, UiKit.matchWrap(c, 10));

        card.addView(UiKit.dangerButton(c, "Remover este tênis da lista", v -> confirmRemove(s)), UiKit.matchWrap(c, 10));
        return card;
    }

    private void command(String name, final String label) {
        app().bleManager.sendCommandAcked(name, null, (ok, detail) -> {
            if (!isAdded()) return;
            toast(label + ": " + detail);
        });
    }

    private void rename(final Shoe s) {
        final EditText input = new EditText(requireContext());
        input.setText(s.nickname == null ? "" : s.nickname);
        input.setHint("Ex.: Tênis azul");
        new AlertDialog.Builder(requireContext())
                .setTitle("Nome do tênis")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    Repo.get(requireContext()).shoes.setNickname(s.address, input.getText().toString().trim());
                    reloadSoon();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void editLimit(final Shoe s) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf((int) s.retireKm));
        new AlertDialog.Builder(requireContext())
                .setTitle("Limite de quilometragem")
                .setMessage("Tênis de corrida costumam durar de 500 a 800 km.")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    try {
                        double km = Double.parseDouble(input.getText().toString().trim());
                        if (km < 50 || km > 3000) { toast("Use um valor entre 50 e 3000 km."); return; }
                        Repo.get(requireContext()).shoes.setRetireKm(s.address, km);
                        reloadSoon();
                    } catch (NumberFormatException e) {
                        toast("Digite um número.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmRemove(final Shoe s) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remover " + s.title() + "?")
                .setMessage("A quilometragem e o histórico de bateria deste tênis serão apagados. Os treinos gravados continuam no histórico.")
                .setPositiveButton("Remover", (d, w) -> {
                    Repo.get(requireContext()).shoes.remove(s.address);
                    reloadSoon();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Escritas passam por uma fila; a releitura entra na mesma fila, então já vê o resultado. */
    private void reloadSoon() { load(); }
}
