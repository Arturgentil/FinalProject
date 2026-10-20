package com.stepvolt.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.stepvolt.app.AppState;
import com.stepvolt.app.models.User;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.util.AvatarProcessor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Troca da foto de perfil: escolher da galeria, tirar uma foto ou remover.
 * Reaproveitado pelas telas de Perfil e Editar perfil — cada uma repassa o
 * onActivityResult e é avisada quando a foto muda.
 */
public class AvatarFlow {

    public interface Listener {
        void onAvatarChanged();
    }

    public static final int REQ_GALLERY = 4401;
    public static final int REQ_CAMERA = 4402;

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    private final Fragment fragment;
    private final Listener listener;
    private AlertDialog busy;

    public AvatarFlow(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        if (!fragment.isAdded()) return;
        final Context c = fragment.requireContext();
        User u = AppState.get().user;
        boolean hasPhoto = u != null && u.avatarUrl != null && !u.avatarUrl.trim().isEmpty();
        final String[] items = hasPhoto
                ? new String[]{"Tirar foto", "Escolher da galeria", "Remover foto"}
                : new String[]{"Tirar foto", "Escolher da galeria"};
        new AlertDialog.Builder(c)
                .setTitle("Foto de perfil")
                .setItems(items, (d, which) -> {
                    if (which == 0) takePhoto();
                    else if (which == 1) pickFromGallery();
                    else confirmRemove();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pickFromGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            fragment.startActivityForResult(Intent.createChooser(i, "Escolher foto"), REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            toast("Nenhum app de galeria encontrado.");
        }
    }

    private File cameraFile() {
        File dir = new File(fragment.requireContext().getCacheDir(), "avatars");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "camera.jpg");
    }

    private void takePhoto() {
        Context c = fragment.requireContext();
        File f = cameraFile();
        if (f.exists()) f.delete();
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.setClipData(ClipData.newRawUri("foto", uri));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            fragment.startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            toast("Nenhum app de câmera encontrado.");
        }
    }

    /** Devolve true se o resultado era desta tela e foi tratado. */
    public boolean onActivityResult(int request, int result, Intent data) {
        if (request != REQ_GALLERY && request != REQ_CAMERA) return false;
        if (result != Activity.RESULT_OK) return true;
        Uri uri;
        if (request == REQ_CAMERA) {
            File f = cameraFile();
            if (!f.exists() || f.length() == 0) { toast("A câmera não salvou a foto. Tente de novo."); return true; }
            uri = Uri.fromFile(f);
        } else {
            uri = data == null ? null : data.getData();
            if (uri == null) { toast("Não consegui abrir essa imagem."); return true; }
        }
        prepareAndUpload(uri, request == REQ_CAMERA);
        return true;
    }

    private void prepareAndUpload(final Uri uri, final boolean fromCamera) {
        final Context app = fragment.requireContext().getApplicationContext();
        showBusy("Preparando a foto…");
        WORK.execute(() -> {
            byte[] bytes = null;
            String error = null;
            try {
                bytes = AvatarProcessor.process(app, uri);
            } catch (IOException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = "Não consegui ler essa imagem.";
            }
            if (fromCamera) {
                File f = new File(new File(app.getCacheDir(), "avatars"), "camera.jpg");
                if (f.exists()) f.delete();   // não deixa foto pessoal parada no cache
            }
            final byte[] result = bytes;
            final String err = error;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (!fragment.isAdded()) { hideBusy(); return; }
                if (result == null) {
                    hideBusy();
                    toast(err == null ? "Não consegui preparar a foto." : err);
                    return;
                }
                upload(result);
            });
        });
    }

    private void upload(byte[] jpeg) {
        showBusy("Enviando a foto…");
        Map<String, Object> body = new HashMap<>();
        body.put("image", Base64.encodeToString(jpeg, Base64.NO_WRAP));
        ApiClient.get(fragment.requireContext()).uploadAvatar(body).enqueue(new Callback<User>() {
            @Override public void onResponse(Call<User> call, Response<User> r) {
                hideBusy();
                if (!fragment.isAdded()) return;
                if (r.isSuccessful() && r.body() != null) {
                    AppState.get().user = r.body();
                    toast("Foto atualizada.");
                    if (listener != null) listener.onAvatarChanged();
                } else {
                    Toast.makeText(fragment.requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<User> call, Throwable t) {
                hideBusy();
                if (fragment.isAdded()) toast("Sem conexão com o servidor. A foto não foi enviada.");
            }
        });
    }

    private void confirmRemove() {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle("Remover foto?")
                .setMessage("Seu perfil volta a mostrar só as suas iniciais.")
                .setPositiveButton("Remover", (d, w) -> remove())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void remove() {
        showBusy("Removendo…");
        ApiClient.get(fragment.requireContext()).deleteAvatar().enqueue(new Callback<User>() {
            @Override public void onResponse(Call<User> call, Response<User> r) {
                hideBusy();
                if (!fragment.isAdded()) return;
                if (r.isSuccessful() && r.body() != null) {
                    AppState.get().user = r.body();
                    toast("Foto removida.");
                    if (listener != null) listener.onAvatarChanged();
                } else {
                    Toast.makeText(fragment.requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<User> call, Throwable t) {
                hideBusy();
                if (fragment.isAdded()) toast("Sem conexão com o servidor.");
            }
        });
    }

    // ---- indicador de progresso simples

    private void showBusy(String text) {
        hideBusy();
        if (!fragment.isAdded()) return;
        Context c = fragment.requireContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(22 * c.getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        row.addView(new ProgressBar(c));
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(15);
        t.setPadding(pad, 0, 0, 0);
        row.addView(t);
        busy = new AlertDialog.Builder(c).setView(row).setCancelable(false).create();
        busy.show();
    }

    private void hideBusy() {
        if (busy != null && busy.isShowing()) busy.dismiss();
        busy = null;
    }

    private void toast(String m) {
        if (fragment.isAdded()) Toast.makeText(fragment.requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
