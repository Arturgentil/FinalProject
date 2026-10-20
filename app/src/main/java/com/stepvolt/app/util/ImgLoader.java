package com.stepvolt.app.util;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.stepvolt.app.net.SessionManager;

public class ImgLoader {

    /** Versão antiga: sem nome para as iniciais. Mantida para quem ainda a chama. */
    public static void load(ImageView iv, String url) {
        loadAvatar(iv, url, null);
    }

    /**
     * Carrega a foto de perfil. Sem foto (ou se falhar), mostra as iniciais do
     * nome. Importante: SEMPRE limpa a imagem anterior — em listas o
     * RecyclerView reaproveita a mesma ImageView, e sem isso a foto de outra
     * pessoa aparecia no lugar de quem não tem foto.
     */
    public static void loadAvatar(ImageView iv, String url, String name) {
        if (iv == null) return;
        Context ctx = iv.getContext();
        InitialsDrawable fallback = new InitialsDrawable(name);
        String full = AvatarText.resolveUrl(SessionManager.getBaseUrl(ctx), url);
        if (full == null) {
            Glide.with(ctx).clear(iv);
            iv.setImageDrawable(fallback);
            return;
        }
        Glide.with(ctx).load(full).placeholder(fallback).error(fallback).centerCrop().into(iv);
    }
}
