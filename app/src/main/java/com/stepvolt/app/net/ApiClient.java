package com.stepvolt.app.net;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit;
    private static String cachedBaseUrl;

    private static final Object REFRESH_LOCK = new Object();

    /**
     * Quando o servidor responde 401, tenta renovar o token com o token de
     * renovação e repete a chamada uma vez. Se a renovação falhar, devolve
     * null e o 401 segue para o app, que leva ao login (ApiUtil).
     */
    private static Authenticator refreshAuthenticator(final Context appCtx, final String baseUrl) {
        return (route, response) -> {
            // Não tenta de novo se a repetição já falhou, nem em login/refresh.
            if (response.priorResponse() != null) return null;
            String refresh = SessionManager.getRefreshToken(appCtx);
            if (refresh == null) return null;

            Request failed = response.request();
            String sentHeader = failed.header("Authorization");

            synchronized (REFRESH_LOCK) {
                // Outra chamada pode já ter renovado enquanto esta esperava.
                String current = SessionManager.getToken(appCtx);
                if (current != null && sentHeader != null && !sentHeader.equals("Bearer " + current)) {
                    return failed.newBuilder().header("Authorization", "Bearer " + current).build();
                }
                try {
                    Retrofit bare = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(new OkHttpClient.Builder()
                                    .connectTimeout(10, TimeUnit.SECONDS)
                                    .readTimeout(15, TimeUnit.SECONDS).build())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    Map<String, Object> body = new HashMap<>();
                    body.put("refreshToken", refresh);
                    retrofit2.Response<AuthResponse> r = bare.create(ApiService.class).refresh(body).execute();
                    if (r.isSuccessful() && r.body() != null && r.body().token != null) {
                        SessionManager.saveTokens(appCtx, r.body().token, r.body().refreshToken);
                        return failed.newBuilder().header("Authorization", "Bearer " + r.body().token).build();
                    }
                } catch (Exception ignored) {
                    // sem rede: não apaga a sessão, só não renova agora
                }
            }
            return null;
        };
    }

    public static ApiService get(Context context) {
        Context appCtx = context.getApplicationContext();
        String baseUrl = SessionManager.getBaseUrl(appCtx);

        if (retrofit == null || !baseUrl.equals(cachedBaseUrl)) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    // O plano gratuito do Render dorme quando fica parado e leva até ~1 min para
                    // acordar. Com 10/15 s o primeiro cadastro do dia sempre "falhava".
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(70, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        String token = SessionManager.getToken(appCtx);
                        if (token == null) return chain.proceed(original);
                        Request authed = original.newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(authed);
                    })
                    .authenticator(refreshAuthenticator(appCtx, baseUrl))
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            cachedBaseUrl = baseUrl;
        }
        return retrofit.create(ApiService.class);
    }
}
