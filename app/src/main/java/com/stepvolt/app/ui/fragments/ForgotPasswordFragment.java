package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.R;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.OkResponse;
import com.stepvolt.app.ui.Navigator;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forgot_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View btnBack = view.findViewById(R.id.btnBack);
        LinearLayout formState = view.findViewById(R.id.formState);
        LinearLayout sentState = view.findViewById(R.id.sentState);
        EditText inputEmail = view.findViewById(R.id.inputEmail);
        Button btnSend = view.findViewById(R.id.btnSend);
        TextView txtSentMessage = view.findViewById(R.id.txtSentMessage);
        Button btnBackToLogin = view.findViewById(R.id.btnBackToLogin);

        btnBack.setOnClickListener(v -> nav().navigate(Navigator.LOGIN));
        btnBackToLogin.setOnClickListener(v -> nav().navigate(Navigator.LOGIN));

        btnSend.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            if (email.isEmpty()) return;
            btnSend.setEnabled(false);
            btnSend.setText("Enviando...");

            Map<String, Object> body = new HashMap<>();
            body.put("email", email);

            ApiClient.get(requireContext()).forgotPassword(body).enqueue(new Callback<OkResponse>() {
                @Override
                public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                    if (!isAdded()) return;
                    btnSend.setEnabled(true);
                    btnSend.setText("Enviar link de recupera\u00e7\u00e3o");
                    if (!response.isSuccessful()) {
                        android.widget.Toast.makeText(requireContext(), ApiUtil.parseError(response), android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    formState.setVisibility(View.GONE);
                    sentState.setVisibility(View.VISIBLE);
                    txtSentMessage.setText("Confirmamos que " + email + " est\u00e1 cadastrado. O envio de e-mail real ainda n\u00e3o est\u00e1 configurado no servidor (precisa de um provedor SMTP) - pe\u00e7a pra quem administra o backend redefinir sua senha diretamente no banco por enquanto.");
                }

                @Override
                public void onFailure(Call<OkResponse> call, Throwable t) {
                    if (!isAdded()) return;
                    btnSend.setEnabled(true);
                    btnSend.setText("Enviar link de recupera\u00e7\u00e3o");
                    android.widget.Toast.makeText(requireContext(), ApiUtil.networkMessage(requireContext(), t), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
