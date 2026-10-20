package com.stepvolt.app.net;

import android.content.Context;

import com.google.gson.Gson;
import com.stepvolt.app.ui.Navigator;

import retrofit2.Response;

public class ApiUtil {

    private static final Gson GSON = new Gson();

    public static String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String json = response.errorBody().string();
                ErrorResponse err = GSON.fromJson(json, ErrorResponse.class);
                if (err != null && err.error != null) return err.error;
            }
        } catch (Exception ignored) {}
        return NetErrors.describeHttp(response.code());
    }

    /** Mensagem para uma falha de rede (onFailure), sabendo se o celular está online. */
    public static String networkMessage(Context ctx, Throwable t) {
        boolean online = true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm == null ? null : cm.getActiveNetwork();
            android.net.NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            online = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException ignored) {
            // sem a permissão de estado de rede: assume online e deixa a mensagem da falha falar
        }
        return NetErrors.describe(t, online, SessionManager.getBaseUrl(ctx));
    }

    /** Returns true (and redirects to Login) if the response was a 401 - callers should stop processing. */
    public static boolean handleAuthError(Context ctx, Response<?> response, Navigator nav) {
        if (response.code() == 401) {
            SessionManager.clearToken(ctx);
            nav.navigate(Navigator.LOGIN);
            return true;
        }
        return false;
    }
}
