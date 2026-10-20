package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.AuthResponse;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.Navigator;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginFragment extends Fragment {

    private boolean passwordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText inputEmail = view.findViewById(R.id.inputEmail);
        EditText inputPassword = view.findViewById(R.id.inputPassword);
        ImageButton btnToggle = view.findViewById(R.id.btnTogglePassword);
        TextView txtError = view.findViewById(R.id.txtError);
        TextView btnForgot = view.findViewById(R.id.btnForgotPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        TextView btnRegister = view.findViewById(R.id.btnGoRegister);

        btnToggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            inputPassword.setInputType(passwordVisible
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputPassword.setSelection(inputPassword.getText().length());
        });

        btnForgot.setOnClickListener(v -> nav().navigate(Navigator.FORGOT_PASSWORD));
        btnRegister.setOnClickListener(v -> nav().navigate(Navigator.REGISTER));

        btnLogin.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String password = inputPassword.getText().toString();   // sem trim: espaços fazem parte da senha
            txtError.setVisibility(View.GONE);

            if (email.isEmpty() || password.isEmpty()) {
                showError(txtError, "Preencha todos os campos.");
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Entrando...");

            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("password", password);

            ApiClient.get(requireContext()).login(body).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    if (!isAdded()) return;
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Entrar");
                    if (!response.isSuccessful() || response.body() == null) {
                        showError(txtError, ApiUtil.parseError(response));
                        return;
                    }
                    SessionManager.saveTokens(requireContext(), response.body().token, response.body().refreshToken);
                    AppState.get().user = response.body().user;
                    nav().navigate(Navigator.DASHBOARD);
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable t) {
                    if (!isAdded()) return;
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Entrar");
                    showError(txtError, ApiUtil.networkMessage(requireContext(), t));
                }
            });
        });
    }

    private void showError(TextView txtError, String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
