package com.stepvolt.app;

import android.app.Application;

import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.models.Achievement;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.models.Goal;
import com.stepvolt.app.models.NotificationItem;
import com.stepvolt.app.models.Post;
import com.stepvolt.app.models.SocialUser;
import com.stepvolt.app.models.User;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.GoalsResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * App-wide shared state. NOTHING here is seeded/mocked - every list starts
 * empty and is filled by calling the corresponding refreshX() method, which
 * hits the real StepVolt API (Node/Express + PostgreSQL). Screens call
 * refreshX() in onViewCreated/onResume and render whatever comes back;
 * one-off actions (like, follow, create goal, mark notification read, etc.)
 * are called directly against ApiClient from the fragment that triggered
 * them, then the relevant list here is updated from the server's response.
 */
public class AppState extends Application {

    private static AppState instance;
    public static AppState get() { return instance; }

    public User user = User.empty();

    public List<NotificationItem> notifications = new ArrayList<>();
    public List<ActivityRecord> activities = new ArrayList<>();
    public List<Goal> activeGoals = new ArrayList<>();
    public List<Goal> completedGoals = new ArrayList<>();
    public List<Achievement> achievements = new ArrayList<>();
    public List<Post> feedPosts = new ArrayList<>();
    public List<SocialUser> socialUsers = new ArrayList<>();

    public BleManager bleManager;

    /**
     * Coleta de dados no nível do app. Antes quem escutava o tênis era a tela
     * do painel, então sair dela parava a leitura; agora o hub vive aqui e as
     * telas apenas se inscrevem nele.
     */
    public Telemetry telemetry;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        com.stepvolt.app.core.ThemeHelper.apply(this);
        warmUpServer();
        bleManager = new BleManager(this);
        telemetry = new Telemetry(this, bleManager);
    }

    public int unreadNotifications() {
        int n = 0;
        for (NotificationItem it : notifications) if (!it.read) n++;
        return n;
    }

    /** success/data/error - exactly one of (data) or (error) is non-null. */
    public interface Callback<T> {
        void onResult(boolean success, T data, String error);
    }

    private <T> void enqueue(Call<T> call, Callback<T> cb) {
        call.enqueue(new retrofit2.Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onResult(true, response.body(), null);
                } else {
                    cb.onResult(false, null, ApiUtil.parseError(response));
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                cb.onResult(false, null, "Falha de conexão com o servidor: " + t.getMessage());
            }
        });
    }

    public void refreshUser(Callback<User> cb) {
        enqueue(ApiClient.get(this).getMe(), (success, data, error) -> {
            if (success) {
                user = data;
                if (data != null) {
                    com.stepvolt.app.core.Prefs.setBool(this, "notif_goal", data.notifGoal);
                    com.stepvolt.app.core.Prefs.setBool(this, "notif_record", data.notifRecord);
                    com.stepvolt.app.core.Prefs.setBool(this, "notif_social", data.notifSocial);
                    com.stepvolt.app.core.Prefs.setBool(this, "public_profile", data.publicProfile);
                    com.stepvolt.app.core.Prefs.setBool(this, "auto_share", data.autoShare);
                }
            }
            cb.onResult(success, data, error);
        });
    }

    public void refreshNotifications(Callback<List<NotificationItem>> cb) {
        enqueue(ApiClient.get(this).getNotifications(), (success, data, error) -> {
            if (success) notifications = data;
            cb.onResult(success, data, error);
        });
    }

    public void refreshFeed(Callback<List<Post>> cb) {
        enqueue(ApiClient.get(this).getFeed(), (success, data, error) -> {
            if (success) feedPosts = data;
            cb.onResult(success, data, error);
        });
    }

    public void refreshGoals(Callback<GoalsResponse> cb) {
        enqueue(ApiClient.get(this).getGoals(), (success, data, error) -> {
            if (success) {
                activeGoals = data.active;
                completedGoals = data.completed;
            }
            cb.onResult(success, data, error);
        });
    }

    public void refreshAchievements(Callback<List<Achievement>> cb) {
        enqueue(ApiClient.get(this).getAchievements(), (success, data, error) -> {
            if (success) achievements = data;
            cb.onResult(success, data, error);
        });
    }

    /**
     * Acorda o servidor em segundo plano ao abrir o app. No plano gratuito do Render o
     * primeiro acesso leva até ~1 minuto; assim, quando a pessoa terminar de preencher o
     * cadastro, o servidor já está de pé. O resultado é ignorado de propósito.
     */
    private void warmUpServer() {
        try {
            ApiClient.get(this).health().enqueue(new retrofit2.Callback<com.stepvolt.app.models.HealthResponse>() {
                @Override public void onResponse(retrofit2.Call<com.stepvolt.app.models.HealthResponse> c,
                                                 retrofit2.Response<com.stepvolt.app.models.HealthResponse> r) {}
                @Override public void onFailure(retrofit2.Call<com.stepvolt.app.models.HealthResponse> c, Throwable t) {}
            });
        } catch (RuntimeException ignored) {
            // endereço do servidor inválido: o erro aparece na tela que precisar dele
        }
    }

    public void refreshActivities(Callback<List<ActivityRecord>> cb) {
        refreshActivities(false, cb);
    }

    /**
     * Sincronização de atividades em páginas de 100. Com incremental=true e
     * uma lista já carregada, pede só o que mudou desde a última
     * sincronização e junta com o que já existe, em vez de baixar tudo.
     */
    public void refreshActivities(boolean incremental, Callback<List<ActivityRecord>> cb) {
        String since = null;
        long last = com.stepvolt.app.core.Prefs.getLong(this, com.stepvolt.app.core.Prefs.ACTIVITIES_SYNCED_AT, 0);
        if (incremental && activities != null && !activities.isEmpty() && last > 0) {
            java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            since = iso.format(new java.util.Date(last - 60_000L)); // 1 min de folga contra diferença de relógio
        }
        final boolean merge = since != null;
        final long startedAt = System.currentTimeMillis();
        fetchActivityPages(since, 0, new ArrayList<ActivityRecord>(), (success, data, error) -> {
            if (success) {
                if (merge) {
                    java.util.LinkedHashMap<String, ActivityRecord> byId = new java.util.LinkedHashMap<>();
                    for (ActivityRecord a : data) byId.put(a.id, a);
                    for (ActivityRecord a : activities) if (!byId.containsKey(a.id)) byId.put(a.id, a);
                    activities = new ArrayList<>(byId.values());
                } else {
                    activities = data;
                }
                com.stepvolt.app.core.Prefs.setLong(this, com.stepvolt.app.core.Prefs.ACTIVITIES_SYNCED_AT, startedAt);
            }
            cb.onResult(success, success ? activities : data, error);
        });
    }

    private static final int PAGE = 100;
    private static final int MAX_PAGES = 20;

    private void fetchActivityPages(final String since, final int offset, final List<ActivityRecord> acc,
                                    final Callback<List<ActivityRecord>> done) {
        enqueue(ApiClient.get(this).getActivitiesPage(since, PAGE, offset), (success, data, error) -> {
            if (!success) { done.onResult(false, null, error); return; }
            if (data != null) acc.addAll(data);
            boolean more = data != null && data.size() >= PAGE && (offset / PAGE) < MAX_PAGES;
            if (more) fetchActivityPages(since, offset + PAGE, acc, done);
            else done.onResult(true, acc, null);
        });
    }

    public void refreshSocialUsers(String query, Callback<List<SocialUser>> cb) {
        enqueue(ApiClient.get(this).searchUsers(query == null ? "" : query), (success, data, error) -> {
            if (success) socialUsers = data;
            cb.onResult(success, data, error);
        });
    }
}
