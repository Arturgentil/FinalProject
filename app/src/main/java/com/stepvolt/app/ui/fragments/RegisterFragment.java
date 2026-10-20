package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.AuthResponse;
import com.stepvolt.app.net.NetErrors;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.DatePickerHelper;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.util.AuthRules;
import com.stepvolt.app.util.ProfileRules;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Criar conta. As mesmas regras do servidor valem aqui antes de enviar; erros
 * aparecem no campo certo; senha com medidor; data por calendário; e mensagens
 * diferentes para "servidor acordando", "sem internet" e "e-mail já usado".
 */
public class RegisterFragment extends Fragment {

    private EditText inputName, inputUsername, inputEmail, inputWeight, inputHeight, inputPassword, inputConfirm;
    private TextView txtBirth, txtError, txtStrength, txtMatch;
    private Button btnRegister;
    private final View[] seg = new View[4];
    private String birthIso = "";
    private boolean lastAttemptTimedOut = false;
    private String lastAttemptEmail = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        inputName = view.findViewById(R.id.inputName);
        inputUsername = view.findViewById(R.id.inputUsername);
        inputEmail = view.findViewById(R.id.inputEmail);
        inputWeight = view.findViewById(R.id.inputWeight);
        inputHeight = view.findViewById(R.id.inputHeight);
        inputPassword = view.findViewById(R.id.inputPassword);
        inputConfirm = view.findViewById(R.id.inputConfirmPassword);
        txtBirth = view.findViewById(R.id.txtBirth);
        txtError = view.findViewById(R.id.txtError);
        txtStrength = view.findViewById(R.id.txtStrength);
        txtMatch = view.findViewById(R.id.txtMatch);
        btnRegister = view.findViewById(R.id.btnRegister);
        seg[0] = view.findViewById(R.id.seg1);
        seg[1] = view.findViewById(R.id.seg2);
        seg[2] = view.findViewById(R.id.seg3);
        seg[3] = view.findViewById(R.id.seg4);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().goBack());
        view.findViewById(R.id.btnGoLogin).setOnClickListener(v -> nav().navigate(Navigator.LOGIN));
        view.findViewById(R.id.boxBirth).setOnClickListener(v ->
                DatePickerHelper.show(requireContext(), birthIso, iso -> {
                    birthIso = iso;
                    txtBirth.setText(formatBr(iso));
                    txtBirth.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                }));

        wireEye((ImageButton) view.findViewById(R.id.btnTogglePassword), inputPassword);
        wireEye((ImageButton) view.findViewById(R.id.btnToggleConfirm), inputConfirm);

        inputPassword.addTextChangedListener(new Watcher(this::refreshPasswordHints));
        inputConfirm.addTextChangedListener(new Watcher(this::refreshPasswordHints));
        refreshPasswordHints();

        // Apaga o erro do campo assim que a pessoa volta a digitar nele.
        for (EditText e : new EditText[]{inputName, inputUsername, inputEmail, inputWeight, inputHeight}) {
            e.addTextChangedListener(new Watcher(() -> { e.setError(null); txtError.setVisibility(View.GONE); }));
        }

        btnRegister.setOnClickListener(v -> submit((ScrollView) view));
    }

    // ------------------------------------------------------------------
    // Senha: olho e medidor
    // ------------------------------------------------------------------

    private void wireEye(ImageButton eye, final EditText field) {
        eye.setOnClickListener(v -> {
            boolean hidden = (field.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            int sel = field.getSelectionEnd();
            field.setInputType(InputType.TYPE_CLASS_TEXT | (hidden
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            field.setSelection(Math.max(0, Math.min(sel, field.getText().length())));
        });
    }

    private static final int[] STRENGTH_COLORS = {0xFFFF5C7A, 0xFFFF5C7A, 0xFFFFB347, 0xFF4CC9F0, 0xFF00F5A0};

    private void refreshPasswordHints() {
        String p = inputPassword.getText().toString();
        int s = AuthRules.strength(p);
        for (int i = 0; i < seg.length; i++) {
            seg[i].setBackgroundColor(i < s ? STRENGTH_COLORS[s]
                    : ContextCompat.getColor(requireContext(), R.color.glass_border));
        }
        txtStrength.setText(AuthRules.strengthLabel(s));

        String c = inputConfirm.getText().toString();
        if (c.isEmpty()) {
            txtMatch.setVisibility(View.GONE);
        } else {
            boolean same = c.equals(p);
            txtMatch.setVisibility(View.VISIBLE);
            txtMatch.setText(same ? "As senhas são iguais." : "As senhas ainda não são iguais.");
            txtMatch.setTextColor(same ? 0xFF00F5A0 : 0xFFFFB347);
        }
    }

    private static String formatBr(String iso) {
        int[] d = ProfileRules.parseDate(iso);
        return d == null ? iso : String.format(java.util.Locale.US, "%02d/%02d/%04d", d[2], d[1], d[0]);
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    private boolean fail(EditText f, String message) {
        f.setError(message);
        f.requestFocus();
        return false;
    }

    private boolean validate() {
        String err = ProfileRules.validateName(inputName.getText().toString());
        if (err != null) return fail(inputName, err);
        err = ProfileRules.validateUsername(inputUsername.getText().toString());
        if (err != null) return fail(inputUsername, err);
        err = AuthRules.validateEmail(inputEmail.getText().toString());
        if (err != null) return fail(inputEmail, err);
        if (!birthIso.isEmpty()) {
            err = ProfileRules.validateBirthDate(birthIso, Calendar.getInstance());
            if (err != null) { showError(err); return false; }
        }
        err = ProfileRules.validateWeight(inputWeight.getText().toString());
        if (err != null) return fail(inputWeight, err);
        err = ProfileRules.validateHeight(inputHeight.getText().toString());
        if (err != null) return fail(inputHeight, err);
        err = AuthRules.validatePassword(inputPassword.getText().toString());
        if (err != null) return fail(inputPassword, err);
        err = AuthRules.validateConfirm(inputPassword.getText().toString(), inputConfirm.getText().toString());
        if (err != null) return fail(inputConfirm, err);
        return true;
    }

    private void submit(final ScrollView scroll) {
        txtError.setVisibility(View.GONE);
        if (!validate()) return;

        final String email = inputEmail.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        setBusy(true);

        Map<String, Object> body = new HashMap<>();
        body.put("name", inputName.getText().toString().trim());
        body.put("username", inputUsername.getText().toString().trim());
        body.put("email", email);
        body.put("password", inputPassword.getText().toString());
        body.put("birthDate", birthIso);
        Double w = ProfileRules.parseNumber(inputWeight.getText().toString());
        Double h = ProfileRules.parseNumber(inputHeight.getText().toString());
        body.put("weight", w == null ? 0 : w);
        body.put("height", h == null ? 0 : h);

        ApiClient.get(requireContext()).register(body).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (!isAdded()) return;
                setBusy(false);
                if (response.isSuccessful() && response.body() != null) {
                    SessionManager.saveTokens(requireContext(), response.body().token, response.body().refreshToken);
                    AppState.get().user = response.body().user;
                    Toast.makeText(requireContext(), "Conta criada! Bem-vindo ao StepVolt.", Toast.LENGTH_SHORT).show();
                    nav().navigate(Navigator.DASHBOARD);
                    return;
                }
                String msg = ApiUtil.parseError(response);
                if (response.code() == 409) {
                    if (msg.toLowerCase().contains("e-mail")) {
                        inputEmail.setError("Já cadastrado");
                        if (lastAttemptTimedOut && email.equals(lastAttemptEmail)) {
                            msg = "Sua conta pode ter sido criada na tentativa anterior, que demorou demais. "
                                    + "Toque em \"Já tenho conta\" e entre com a mesma senha.";
                        }
                    } else {
                        inputUsername.setError("Já em uso");
                    }
                }
                lastAttemptTimedOut = false;
                showError(msg);
                scrollToError(scroll);
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                if (!isAdded()) return;
                setBusy(false);
                lastAttemptTimedOut = NetErrors.isTransient(t);
                lastAttemptEmail = email;
                showError(ApiUtil.networkMessage(requireContext(), t));
                scrollToError(scroll);
            }
        });
    }

    private void setBusy(boolean busy) {
        btnRegister.setEnabled(!busy);
        btnRegister.setText(busy ? "Criando conta…" : "Criar minha conta");
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }

    private void scrollToError(final ScrollView scroll) {
        scroll.post(() -> scroll.smoothScrollTo(0, txtError.getBottom()));
    }

    private static class Watcher implements TextWatcher {
        private final Runnable r;
        Watcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
