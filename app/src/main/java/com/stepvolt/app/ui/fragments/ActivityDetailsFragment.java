package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.R;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.views.LineChartView;
import com.stepvolt.app.util.Fmt;

import java.util.List;

public class ActivityDetailsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_activity_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ActivityRecord act = null;
        if (getArguments() != null) {
            act = (ActivityRecord) getArguments().getSerializable("activity");
        }
        if (act == null) {
            // Nada foi passado (não deveria acontecer navegando pelas telas normais) -
            // volta pro histórico em vez de inventar dados falsos pra mostrar aqui.
            nav().navigate(Navigator.HISTORY);
            return;
        }

        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().navigate(Navigator.HISTORY));

        TextView txtDate = view.findViewById(R.id.txtDate);
        TextView btnShare = view.findViewById(R.id.btnShare);
        TextView txtStart = view.findViewById(R.id.txtStart);
        TextView txtEnd = view.findViewById(R.id.txtEnd);
        TextView txtDuration = view.findViewById(R.id.txtDuration);
        TextView txtSteps = view.findViewById(R.id.txtSteps);
        TextView txtEnergy = view.findViewById(R.id.txtEnergy);
        TextView txtDistance = view.findViewById(R.id.txtDistance);
        TextView txtBpmAvg = view.findViewById(R.id.txtBpmAvg);
        LineChartView chartBpm = view.findViewById(R.id.chartBpm);
        LineChartView chartSpo2 = view.findViewById(R.id.chartSpo2);
        View bpmStatsRow = view.findViewById(R.id.bpmStatsRow);
        TextView txtBpmNoData = view.findViewById(R.id.txtBpmNoData);
        TextView txtSpo2NoData = view.findViewById(R.id.txtSpo2NoData);
        TextView txtBpmMin = view.findViewById(R.id.txtBpmMin);
        TextView txtBpmAvgSmall = view.findViewById(R.id.txtBpmAvgSmall);
        TextView txtBpmMax = view.findViewById(R.id.txtBpmMax);

        txtDate.setText(act.date);
        btnShare.setText(act.shared ? "Compartilhado" : "Compartilhar");
        txtStart.setText(act.time != null ? act.time : "--:--");
        txtEnd.setText(act.endTime != null ? act.endTime : "--:--");
        txtDuration.setText("Dura\u00e7\u00e3o total: " + act.duration + " minutos");
        txtSteps.setText(Fmt.intGrouped(act.steps));
        txtEnergy.setText(Fmt.energy(act.energy) + " mWh");
        txtDistance.setText(act.distance + " km (sem GPS - n\u00e3o medida)");
        txtBpmAvg.setText(String.valueOf(act.bpmAvg));

        ActivityRecord finalAct = act;
        btnShare.setOnClickListener(v -> shareIfNeeded(finalAct, btnShare));

        // Estes gráficos usam as amostras REAIS enviadas pelo ESP32 durante a
        // sessão (guardadas em bpm_samples/spo2_samples) - se a atividade não
        // tiver nenhuma amostra registrada, mostramos um aviso em vez de inventar dados.
        List<Integer> bpmSeries = act.bpmSamples;
        List<Integer> spo2Series = act.spo2Samples;

        if (bpmSeries != null && bpmSeries.size() >= 2) {
            chartBpm.setVisibility(View.VISIBLE);
            bpmStatsRow.setVisibility(View.VISIBLE);
            txtBpmNoData.setVisibility(View.GONE);
            java.util.List<Float> floats = new java.util.ArrayList<>();
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
            for (int v : bpmSeries) { floats.add((float) v); min = Math.min(min, v); max = Math.max(max, v); sum += v; }
            chartBpm.setBounds(Math.max(0, min - 10), max + 10);
            chartBpm.setLineColor(0xFFFF6B6B);
            chartBpm.setData(floats);
            txtBpmMin.setText(min + " bpm");
            txtBpmMax.setText(max + " bpm");
            txtBpmAvgSmall.setText(Math.round(sum / (float) bpmSeries.size()) + " bpm");
        } else {
            chartBpm.setVisibility(View.GONE);
            bpmStatsRow.setVisibility(View.GONE);
            txtBpmNoData.setVisibility(View.VISIBLE);
        }

        if (spo2Series != null && spo2Series.size() >= 2) {
            chartSpo2.setVisibility(View.VISIBLE);
            txtSpo2NoData.setVisibility(View.GONE);
            java.util.List<Float> floats = new java.util.ArrayList<>();
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int v : spo2Series) { floats.add((float) v); min = Math.min(min, v); max = Math.max(max, v); }
            chartSpo2.setBounds(Math.max(0, min - 2), max + 2);
            chartSpo2.setLineColor(0xFF00B4D8);
            chartSpo2.setData(floats);
        } else {
            chartSpo2.setVisibility(View.GONE);
            txtSpo2NoData.setVisibility(View.VISIBLE);
        }
    }

    private void shareIfNeeded(ActivityRecord act, TextView btnShare) {
        if (act.shared) return;
        com.stepvolt.app.net.ApiClient.get(requireContext()).shareActivity(act.id).enqueue(new retrofit2.Callback<ActivityRecord>() {
            @Override
            public void onResponse(retrofit2.Call<ActivityRecord> call, retrofit2.Response<ActivityRecord> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    act.shared = true;
                    btnShare.setText("Compartilhado");
                } else {
                    android.widget.Toast.makeText(requireContext(), com.stepvolt.app.net.ApiUtil.parseError(response), android.widget.Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(retrofit2.Call<ActivityRecord> call, Throwable t) {
                if (isAdded()) android.widget.Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
