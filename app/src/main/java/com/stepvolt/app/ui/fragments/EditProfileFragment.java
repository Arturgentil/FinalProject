package com.stepvolt.app.ui.fragments;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.User;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.ui.AvatarFlow;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.util.ImgLoader;
import com.stepvolt.app.util.ProfileRules;
import com.stepvolt.app.util.ViewFactory;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Editar perfil: foto (câmera, galeria ou remover), dados pessoais com
 * validação, data de nascimento por calendário e aviso ao sair sem salvar.
 */
public class EditProfileFragment extends Fragment {

    private EditText inputName, inputUsername, inputBirthDate, inputWeight, inputHeight, inputBio;
    private ShapeableImageView imgAvatar;
    private TextView txtBioCount;
    private AvatarFlow avatarFlow;
    private OnBackPressedCallback backGuard;
    private String initialSnapshot = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = AppState.get().user;

        View btnBack = view.findViewById(R.id.btnBack);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnSaveBottom = view.findViewById(R.id.btnSaveBottom);
        imgAvatar = view.findViewById(R.id.imgAvatar);
        View avatarHolder = view.findViewById(R.id.avatarHolder);
        LinearLayout fieldsContainer = view.findViewById(R.id.containerFields);
        inputBio = view.findViewById(R.id.inputBio);
        txtBioCount = view.findViewById(R.id.txtBioCount);

        avatarFlow = new AvatarFlow(this, this::showAvatar);
        showAvatar();
        avatarHolder.setOnClickListener(v -> avatarFlow.show());

        inputName = ViewFactory.addLabeledInput(requireContext(), fieldsContainer, "Nome completo", "Seu nome",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, user.name);
        inputUsername = ViewFactory.addLabeledInput(requireContext(), fieldsContainer, "Nome de usu\u00e1rio", "usuario",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, user.username);
        inputBirthDate = ViewFactory.addLabeledInput(requireContext(), fieldsContainer, "Data de nascimento", "Toque para escolher",
                InputType.TYPE_NULL, user.birthDate);
        inputWeight = ViewFactory.addLabeledInput(requireContext(), fieldsContainer, "Peso (kg)", "70",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, numberText(user.weight));
        inputHeight = ViewFactory.addLabeledInput(requireContext(), fieldsContainer, "Altura (cm)", "175",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, numberText(user.height));
        inputBio.setText(user.bio);
        inputBio.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ProfileRules.BIO_MAX)});

        // Data por calendário: digitar AAAA-MM-DD à mão é erro certo.
        inputBirthDate.setFocusable(false);
        inputBirthDate.setCursorVisible(false);
        inputBirthDate.setOnClickListener(v -> pickBirthDate());
        ((View) inputBirthDate.getParent()).setOnClickListener(v -> pickBirthDate());

        updateBioCount();
        inputBio.addTextChangedListener(new SimpleWatcher(this::updateBioCount));

        initialSnapshot = snapshot();

        backGuard = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, backGuard);
        btnBack.setOnClickListener(v -> leave());

        View.OnClickListener saveListener = v -> save(btnSave, btnSaveBottom);
        btnSave.setOnClickListener(saveListener);
        btnSaveBottom.setOnClickListener(saveListener);
    }

    private static String numberText(double v) {
        if (v <= 0) return "";
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void showAvatar() {
        if (!isAdded()) return;
        User u = AppState.get().user;
        ImgLoader.loadAvatar(imgAvatar, u.avatarUrl, u.name);
    }

    private void updateBioCount() {
        int n = inputBio.getText().length();
        txtBioCount.setText(n + "/" + ProfileRules.BIO_MAX);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (avatarFlow != null) avatarFlow.onActivityResult(request, result, data);
    }

    // ------------------------------------------------------------------
    // Data de nascimento
    // ------------------------------------------------------------------

    private void pickBirthDate() {
        Calendar cal = Calendar.getInstance();
        int[] parsed = ProfileRules.parseDate(inputBirthDate.getText().toString());
        if (parsed != null) cal.set(parsed[0], parsed[1] - 1, parsed[2]);
        else cal.set(cal.get(Calendar.YEAR) - 25, 0, 1);
        DatePickerDialog d = new DatePickerDialog(requireContext(), (picker, y, m, day) ->
                inputBirthDate.setText(ProfileRules.formatDate(y, m + 1, day)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        Calendar min = Calendar.getInstance();
        min.set(1900, 0, 1);
        d.getDatePicker().setMinDate(min.getTimeInMillis());
        d.getDatePicker().setMaxDate(System.currentTimeMillis());
        d.show();
    }

    // ------------------------------------------------------------------
    // Sair sem salvar
    // ------------------------------------------------------------------

    private String snapshot() {
        return inputName.getText() + "|" + inputUsername.getText() + "|" + inputBirthDate.getText() + "|"
                + inputWeight.getText() + "|" + inputHeight.getText() + "|" + inputBio.getText();
    }

    private void leave() {
        if (!snapshot().equals(initialSnapshot)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Descartar altera\u00e7\u00f5es?")
                    .setMessage("Voc\u00ea mudou alguns dados e ainda n\u00e3o salvou.")
                    .setPositiveButton("Descartar", (d, w) -> exit())
                    .setNegativeButton("Continuar editando", null)
                    .show();
        } else {
            exit();
        }
    }

    private void exit() {
        backGuard.setEnabled(false);
        nav().navigate(Navigator.PROFILE);
    }

    // ------------------------------------------------------------------
    // Salvar
    // ------------------------------------------------------------------

    private boolean fail(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
        return false;
    }

    private boolean validate() {
        String err = ProfileRules.validateName(inputName.getText().toString());
        if (err != null) return fail(inputName, err);
        err = ProfileRules.validateUsername(inputUsername.getText().toString());
        if (err != null) return fail(inputUsername, err);
        String birth = inputBirthDate.getText().toString().trim();
        if (!birth.isEmpty()) {
            err = ProfileRules.validateBirthDate(birth, Calendar.getInstance());
            if (err != null) return fail(inputBirthDate, err);
        }
        err = ProfileRules.validateWeight(inputWeight.getText().toString());
        if (err != null) return fail(inputWeight, err);
        err = ProfileRules.validateHeight(inputHeight.getText().toString());
        if (err != null) return fail(inputHeight, err);
        return true;
    }

    private void save(final Button... buttons) {
        if (!validate()) return;
        for (Button b : buttons) b.setEnabled(false);

        Map<String, Object> body = new HashMap<>();
        body.put("name", inputName.getText().toString().trim());
        body.put("username", inputUsername.getText().toString().trim());
        body.put("birthDate", inputBirthDate.getText().toString().trim());
        body.put("bio", inputBio.getText().toString().trim());
        Double w = ProfileRules.parseNumber(inputWeight.getText().toString());
        Double h = ProfileRules.parseNumber(inputHeight.getText().toString());
        if (w != null) body.put("weight", w);
        if (h != null) body.put("height", h);

        ApiClient.get(requireContext()).updateMe(body).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (!isAdded()) return;
                for (Button b : buttons) b.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null) {
                    String msg = ApiUtil.parseError(response);
                    if (response.code() == 409) inputUsername.setError(msg);
                    else Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    return;
                }
                AppState.get().user = response.body();
                Toast.makeText(requireContext(), "Perfil atualizado!", Toast.LENGTH_SHORT).show();
                exitSaved();
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                if (!isAdded()) return;
                for (Button b : buttons) b.setEnabled(true);
                Toast.makeText(requireContext(), "N\u00e3o foi poss\u00edvel salvar (sem conex\u00e3o com o servidor).", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitSaved() {
        initialSnapshot = snapshot();   // nada a descartar
        exit();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable r;
        SimpleWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
