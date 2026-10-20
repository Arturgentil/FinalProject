package com.stepvolt.app.net;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Holds the JWT session token and the API base URL. The base URL is
 * user-configurable (Settings > Servidor) because it points at whatever
 * machine is running the Node/Postgres backend on the local network - there
 * is no fixed/hardcoded server.
 */
public class SessionManager {

    private static final String PREFS = "stepvolt_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_BASE_URL = "base_url";

    // Trocado no primeiro uso - veja Configurações > Servidor no app.
    // 10.0.2.2 só funciona no emulador padrão do Android Studio; no Waydroid
    // ou num celular físico use o IP real da máquina que roda a API
    // (ex.: http://192.168.0.42:3000/).
    public static final String DEFAULT_BASE_URL = "https://stepvolt-backend.onrender.com/";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveToken(Context ctx, String token) {
        prefs(ctx).edit().putString(KEY_TOKEN, token).apply();
    }

    /** Guarda o token de acesso (curto) e o de renovação (longo). */
    public static void saveTokens(Context ctx, String token, String refreshToken) {
        SharedPreferences.Editor e = prefs(ctx).edit().putString(KEY_TOKEN, token);
        if (refreshToken != null) e.putString(KEY_REFRESH, refreshToken);
        e.apply();
    }

    public static String getRefreshToken(Context ctx) {
        return prefs(ctx).getString(KEY_REFRESH, null);
    }

    public static String getToken(Context ctx) {
        return prefs(ctx).getString(KEY_TOKEN, null);
    }

    public static void clearToken(Context ctx) {
        prefs(ctx).edit().remove(KEY_TOKEN).remove(KEY_REFRESH).apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return getToken(ctx) != null;
    }

    public static String getBaseUrl(Context ctx) {
        return prefs(ctx).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public static void setBaseUrl(Context ctx, String url) {
        String trimmed = url.trim();
        if (!trimmed.endsWith("/")) trimmed = trimmed + "/";
        prefs(ctx).edit().putString(KEY_BASE_URL, trimmed).apply();
    }
}
