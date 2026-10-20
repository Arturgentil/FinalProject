package com.stepvolt.app.net;

import com.stepvolt.app.models.Achievement;
import com.stepvolt.app.models.Challenge;
import com.stepvolt.app.models.HealthResponse;
import com.stepvolt.app.models.LeaderboardEntry;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.models.Comment;
import com.stepvolt.app.models.Goal;
import com.stepvolt.app.models.NotificationItem;
import com.stepvolt.app.models.Post;
import com.stepvolt.app.models.SocialUser;
import com.stepvolt.app.models.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body Map<String, Object> body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body Map<String, Object> body);

    /** Acorda o servidor e informa a versão do banco. Chamada ao abrir o app. */
    @GET("api/health")
    Call<HealthResponse> health();

    /** Troca o token de renovação por um novo par de tokens. */
    @POST("api/auth/refresh")
    Call<AuthResponse> refresh(@Body Map<String, Object> body);

    @POST("api/auth/logout")
    Call<OkResponse> logout(@Body Map<String, Object> body);

    @POST("api/auth/resend-verification")
    Call<OkResponse> resendVerification();

    /** Login com Google: o app manda o idToken e o servidor confere com o Google. */
    @POST("api/auth/google")
    Call<AuthResponse> googleLogin(@Body Map<String, Object> body);

    @POST("api/auth/forgot-password")
    Call<OkResponse> forgotPassword(@Body Map<String, Object> body);

    @GET("api/users/me")
    Call<User> getMe();

    @PUT("api/users/me")
    Call<User> updateMe(@Body Map<String, Object> body);

    @PUT("api/users/me/shoe")
    Call<User> updateShoe(@Body Map<String, Object> body);

    @PUT("api/users/me/password")
    Call<OkResponse> changePassword(@Body Map<String, Object> body);

    /** Pede a senha (ou "EXCLUIR" em contas do Google): um token roubado não basta para apagar a conta. */
    @HTTP(method = "DELETE", path = "api/users/me", hasBody = true)
    Call<OkResponse> deleteAccount(@Body Map<String, Object> body);

    /** Foto já reduzida (JPEG 512x512) em base64. */
    @PUT("api/users/me/avatar")
    Call<User> uploadAvatar(@Body Map<String, Object> body);

    @DELETE("api/users/me/avatar")
    Call<User> deleteAvatar();

    @GET("api/users/search")
    Call<List<SocialUser>> searchUsers(@Query("q") String q);

    @GET("api/users/me/following")
    Call<List<SocialUser>> getFollowing();

    @GET("api/users/me/followers")
    Call<List<SocialUser>> getFollowers();

    @POST("api/users/{id}/toggle-follow")
    Call<FollowResponse> toggleFollow(@Path("id") String id);

    @GET("api/feed")
    Call<List<Post>> getFeed();

    @POST("api/feed/{postId}/like")
    Call<LikeResponse> toggleLike(@Path("postId") String postId);

    @POST("api/feed/{postId}/comments")
    Call<Comment> addComment(@Path("postId") String postId, @Body Map<String, Object> body);

    @GET("api/notifications")
    Call<List<NotificationItem>> getNotifications();

    @PUT("api/notifications/read-all")
    Call<OkResponse> markAllNotificationsRead();

    @PUT("api/notifications/{id}/read")
    Call<OkResponse> markNotificationRead(@Path("id") String id);

    @DELETE("api/notifications/{id}")
    Call<OkResponse> deleteNotification(@Path("id") String id);

    @GET("api/goals")
    Call<GoalsResponse> getGoals();

    @POST("api/goals")
    Call<Goal> createGoal(@Body Map<String, Object> body);

    @GET("api/achievements")
    Call<List<Achievement>> getAchievements();

    @GET("api/activities")
    Call<List<ActivityRecord>> getActivities();

    /** Página de atividades; "since" (ISO 8601) traz só o que mudou depois. */
    @GET("api/activities")
    Call<List<ActivityRecord>> getActivitiesPage(@Query("since") String since,
                                                 @Query("limit") int limit,
                                                 @Query("offset") int offset);

    @GET("api/activities/{id}")
    Call<ActivityRecord> getActivity(@Path("id") String id);

    @POST("api/activities")
    Call<ActivityRecord> createActivity(@Body Map<String, Object> body);

    @PUT("api/activities/{id}/share")
    Call<ActivityRecord> shareActivity(@Path("id") String id);

    // ------------------------------------------------------------------
    // Telemetria, dispositivos, avisos e análise (V3)
    // ------------------------------------------------------------------

    /** Envia um lote de amostras cruas para o servidor guardar. */
    @POST("api/telemetry/batch")
    Call<OkResponse> pushTelemetry(@Body Map<String, Object> body);

    /** Resumo agregado de um período, calculado no servidor. */
    @GET("api/telemetry/summary")
    Call<Map<String, Object>> telemetrySummary(@Query("from") long from, @Query("to") long to);

    @GET("api/devices")
    Call<List<Map<String, Object>>> getDevices();

    /** Registra ou atualiza um tênis pareado (endereço, nome, firmware). */
    @POST("api/devices")
    Call<Map<String, Object>> upsertDevice(@Body Map<String, Object> body);

    @DELETE("api/devices/{id}")
    Call<OkResponse> deleteDevice(@Path("id") String id);

    @POST("api/alerts")
    Call<OkResponse> pushAlerts(@Body Map<String, Object> body);

    @GET("api/alerts")
    Call<List<Map<String, Object>>> getAlerts();

    /** Análise do período. O servidor usa um provedor externo se houver chave
     *  configurada; se não houver, devolve a leitura determinística local. */
    @POST("api/ai/insights")
    Call<AiResponse> aiInsights(@Body Map<String, Object> body);

    // ------------------------------------------------------------------
    // Desafios e ranking
    // ------------------------------------------------------------------

    @GET("api/challenges")
    Call<List<Challenge>> getChallenges();

    @POST("api/challenges")
    Call<Challenge> createChallenge(@Body Map<String, Object> body);

    @POST("api/challenges/{id}/join")
    Call<Challenge> joinChallenge(@Path("id") String id);

    @POST("api/challenges/{id}/leave")
    Call<OkResponse> leaveChallenge(@Path("id") String id);

    @GET("api/challenges/{id}/leaderboard")
    Call<List<LeaderboardEntry>> getChallengeLeaderboard(@Path("id") String id);

    /** Ranking entre você e quem você segue. period: week | month; metric: steps | energy | distance. */
    @GET("api/leaderboard")
    Call<List<LeaderboardEntry>> getLeaderboard(@Query("period") String period, @Query("metric") String metric);
}
