package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.Achievement;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.models.SocialUser;
import com.stepvolt.app.models.User;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.FollowResponse;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.views.ShoeView;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.ImgLoader;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private final String[] tabs = {"Atividades", "Conquistas", "Seguindo", "Seguidores"};
    private String activeTab = "Atividades";

    private LinearLayout tabsContainer, tabContent;
    private ShoeView shoePreview;
    private TextView txtShoeTheme;
    private TextView txtName, txtUsername, txtBio, txtFollowing, txtFollowers, txtActiveDays;
    private TextView txtTotalSteps, txtTotalEnergy, txtRecordSteps, txtRecordEnergy;
    private ShapeableImageView imgAvatar;

    private final List<SocialUser> followingList = new ArrayList<>();
    private final List<SocialUser> followersList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ImageButton btnSettings = view.findViewById(R.id.btnSettings);
        ImageButton btnLogout = view.findViewById(R.id.btnLogout);
        imgAvatar = view.findViewById(R.id.imgAvatar);
        txtName = view.findViewById(R.id.txtName);
        txtUsername = view.findViewById(R.id.txtUsername);
        txtBio = view.findViewById(R.id.txtBio);
        txtFollowing = view.findViewById(R.id.txtFollowing);
        txtFollowers = view.findViewById(R.id.txtFollowers);
        txtActiveDays = view.findViewById(R.id.txtActiveDays);
        android.widget.Button btnEditProfile = view.findViewById(R.id.btnEditProfile);
        android.widget.Button btnCustomizeShoe = view.findViewById(R.id.btnCustomizeShoe);
        txtTotalSteps = view.findViewById(R.id.txtTotalSteps);
        txtTotalEnergy = view.findViewById(R.id.txtTotalEnergy);
        txtRecordSteps = view.findViewById(R.id.txtRecordSteps);
        txtRecordEnergy = view.findViewById(R.id.txtRecordEnergy);
        View cardShoePreview = view.findViewById(R.id.cardShoePreview);
        shoePreview = view.findViewById(R.id.shoePreview);
        txtShoeTheme = view.findViewById(R.id.txtShoeTheme);
        tabsContainer = view.findViewById(R.id.tabsContainer);
        tabContent = view.findViewById(R.id.tabContent);

        btnSettings.setOnClickListener(v -> nav().navigate(Navigator.SETTINGS));
        btnLogout.setOnClickListener(v -> confirmLogout());

        avatarFlow = new com.stepvolt.app.ui.AvatarFlow(this, this::renderHeader);
        view.findViewById(R.id.avatarHolder).setOnClickListener(v -> avatarFlow.show());

        btnEditProfile.setOnClickListener(v -> nav().navigate(Navigator.EDIT_PROFILE));
        btnCustomizeShoe.setOnClickListener(v -> nav().navigate(Navigator.SHOE_CUSTOMIZATION));
        cardShoePreview.setOnClickListener(v -> nav().navigate(Navigator.SHOE_CUSTOMIZATION));

        renderHeader();
        buildTabs();
        renderTabContent();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppState.get().refreshUser((success, data, error) -> {
            if (success && isAdded()) renderHeader();
        });
        if (activeTab.equals("Atividades") || activeTab.equals("Conquistas")) {
            renderTabContent();
        }
    }

    private com.stepvolt.app.ui.AvatarFlow avatarFlow;

    @Override
    public void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (avatarFlow != null) avatarFlow.onActivityResult(request, result, data);
    }

    /** Sair pede confirma\u00e7\u00e3o (um toque sem querer n\u00e3o desconecta) e encerra a sess\u00e3o no servidor. */
    private void confirmLogout() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Sair da conta?")
                .setMessage("Os dados j\u00e1 gravados neste aparelho continuam aqui. Voc\u00ea precisar\u00e1 entrar de novo para sincronizar.")
                .setPositiveButton("Sair", (d, w) -> {
                    final android.content.Context app = requireContext().getApplicationContext();
                    String refresh = SessionManager.getRefreshToken(app);
                    if (refresh != null) {
                        java.util.Map<String, Object> body = new java.util.HashMap<>();
                        body.put("refreshToken", refresh);
                        // Melhor esfor\u00e7o: se estiver sem rede, o token local \u00e9 apagado do mesmo jeito.
                        com.stepvolt.app.net.ApiClient.get(app).logout(body).enqueue(new retrofit2.Callback<com.stepvolt.app.net.OkResponse>() {
                            @Override public void onResponse(retrofit2.Call<com.stepvolt.app.net.OkResponse> c, retrofit2.Response<com.stepvolt.app.net.OkResponse> r) {}
                            @Override public void onFailure(retrofit2.Call<com.stepvolt.app.net.OkResponse> c, Throwable t) {}
                        });
                    }
                    SessionManager.clearToken(requireContext());
                    nav().navigate(Navigator.LOGIN);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void renderHeader() {
        User user = AppState.get().user;
        ImgLoader.loadAvatar(imgAvatar, user.avatarUrl, user.name);
        txtName.setText(user.name);
        txtUsername.setText("@" + user.username);
        txtBio.setText(user.bio);
        txtFollowing.setText(String.valueOf(user.following));
        txtFollowers.setText(String.valueOf(user.followers));
        txtActiveDays.setText(String.valueOf(user.activeDays));
        txtTotalSteps.setText(Fmt.intGrouped(user.totalSteps));
        txtTotalEnergy.setText(Fmt.energy(user.totalEnergy) + " mWh");
        txtRecordSteps.setText(Fmt.intGrouped(user.recordSteps));
        txtRecordEnergy.setText(Fmt.energy(user.recordEnergy) + " mWh");
        shoePreview.setColors(user.shoeCustomization.upper, user.shoeCustomization.sole,
                user.shoeCustomization.laces, user.shoeCustomization.details);
        txtShoeTheme.setText("Tema: " + user.shoeCustomization.theme);
    }

    private void buildTabs() {
        tabsContainer.removeAllViews();
        for (String tab : tabs) {
            TextView tv = new TextView(requireContext());
            tv.setText(tab);
            tv.setTextSize(12);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(8), 0, dp(8));
            styleTab(tv, tab.equals(activeTab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tabsContainer.addView(tv, lp);
            tv.setOnClickListener(v -> {
                activeTab = tab;
                buildTabs();
                renderTabContent();
            });
        }
    }

    private void styleTab(TextView tv, boolean active) {
        if (active) {
            tv.setBackgroundResource(R.drawable.bg_pill_selected);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        } else {
            tv.setBackgroundResource(R.drawable.bg_pill_unselected);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        }
    }

    private void renderTabContent() {
        tabContent.removeAllViews();
        switch (activeTab) {
            case "Atividades":
                AppState.get().refreshActivities((success, data, error) -> {
                    if (isAdded() && activeTab.equals("Atividades")) {
                        if (success) renderActivities(); else showError(error);
                    }
                });
                break;
            case "Conquistas":
                AppState.get().refreshAchievements((success, data, error) -> {
                    if (isAdded() && activeTab.equals("Conquistas")) {
                        if (success) renderAchievements(); else showError(error);
                    }
                });
                break;
            case "Seguindo":
                ApiClient.get(requireContext()).getFollowing().enqueue(socialCallback(followingList, true));
                break;
            default:
                ApiClient.get(requireContext()).getFollowers().enqueue(socialCallback(followersList, false));
                break;
        }
    }

    private Callback<List<SocialUser>> socialCallback(List<SocialUser> target, boolean isFollowingTab) {
        return new Callback<List<SocialUser>>() {
            @Override
            public void onResponse(Call<List<SocialUser>> call, Response<List<SocialUser>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    target.clear();
                    target.addAll(response.body());
                    renderSocial(target);
                } else {
                    showError(ApiUtil.parseError(response));
                }
            }
            @Override
            public void onFailure(Call<List<SocialUser>> call, Throwable t) {
                if (isAdded()) showError("Sem conex\u00e3o com o servidor.");
            }
        };
    }

    private void showError(String error) {
        TextView tv = new TextView(requireContext());
        tv.setText(error);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        tv.setPadding(dp(4), dp(12), dp(4), dp(12));
        tabContent.addView(tv);
    }

    private void renderActivities() {
        tabContent.removeAllViews();
        List<ActivityRecord> activities = AppState.get().activities;
        if (activities.isEmpty()) { showError("Nenhuma atividade registrada ainda."); return; }
        int max = Math.min(4, activities.size());
        for (int i = 0; i < max; i++) {
            ActivityRecord act = activities.get(i);
            LinearLayout row = simpleRow();
            TextView icon = smallIcon("\uD83D\uDCC5");
            row.addView(icon);

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(requireContext());
            t1.setText(act.date);
            t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            t1.setTextSize(14);
            t1.setTypeface(t1.getTypeface(), android.graphics.Typeface.BOLD);
            TextView t2 = new TextView(requireContext());
            t2.setText(Fmt.intGrouped(act.steps) + " passos \u00b7 " + Fmt.energy(act.energy) + " mWh \u00b7 " + act.duration + " min");
            t2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            t2.setTextSize(12);
            col.addView(t1);
            col.addView(t2);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colLp.leftMargin = dp(10);
            row.addView(col, colLp);

            if (act.shared) {
                TextView badge = new TextView(requireContext());
                badge.setText("P\u00fablico");
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
                badge.setTextSize(10);
                badge.setBackgroundResource(R.drawable.bg_pill_selected);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                row.addView(badge);
            }
            row.setOnClickListener(v -> nav().navigate(Navigator.ACTIVITY_DETAILS, activityBundle(act)));
            tabContent.addView(row);
        }
    }

    private android.os.Bundle activityBundle(ActivityRecord act) {
        android.os.Bundle b = new android.os.Bundle();
        b.putSerializable("activity", act);
        return b;
    }

    private void renderAchievements() {
        tabContent.removeAllViews();
        List<Achievement> achievements = AppState.get().achievements;
        int max = Math.min(6, achievements.size());
        for (int i = 0; i < max; i++) {
            Achievement ach = achievements.get(i);
            LinearLayout row = simpleRow();
            row.setAlpha(ach.unlocked ? 1f : 0.5f);
            TextView icon = smallIcon(ach.unlocked ? ach.icon : "\uD83D\uDD12");
            row.addView(icon);

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(requireContext());
            t1.setText(ach.name);
            t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            t1.setTextSize(14);
            t1.setTypeface(t1.getTypeface(), android.graphics.Typeface.BOLD);
            TextView t2 = new TextView(requireContext());
            t2.setText(ach.desc);
            t2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            t2.setTextSize(12);
            col.addView(t1);
            col.addView(t2);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colLp.leftMargin = dp(10);
            row.addView(col, colLp);
            tabContent.addView(row);
        }
    }

    private void renderSocial(List<SocialUser> users) {
        tabContent.removeAllViews();
        if (users.isEmpty()) {
            showError(activeTab.equals("Seguindo") ? "Voc\u00ea ainda n\u00e3o segue ningu\u00e9m." : "Ningu\u00e9m te segue ainda.");
            return;
        }
        for (SocialUser u : users) {
            LinearLayout row = simpleRow();
            ShapeableImageView img = new ShapeableImageView(requireContext());
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            img.setBackgroundResource(R.drawable.bg_avatar_placeholder);
            ImgLoader.loadAvatar(img, u.avatarUrl, u.name);
            row.addView(img, imgLp);

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(requireContext());
            t1.setText(u.name);
            t1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            t1.setTextSize(14);
            t1.setTypeface(t1.getTypeface(), android.graphics.Typeface.BOLD);
            TextView t2 = new TextView(requireContext());
            t2.setText("@" + u.username);
            t2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_faint));
            t2.setTextSize(12);
            col.addView(t1);
            col.addView(t2);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colLp.leftMargin = dp(10);
            row.addView(col, colLp);

            TextView btn = new TextView(requireContext());
            btn.setPadding(dp(14), dp(8), dp(14), dp(8));
            btn.setTextSize(13);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
            renderFollowButton(btn, u.following);
            btn.setOnClickListener(v -> {
                btn.setEnabled(false);
                ApiClient.get(requireContext()).toggleFollow(u.id).enqueue(new Callback<FollowResponse>() {
                    @Override
                    public void onResponse(Call<FollowResponse> call, Response<FollowResponse> response) {
                        if (!isAdded()) return;
                        btn.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            u.following = response.body().following;
                            renderFollowButton(btn, u.following);
                        } else {
                            Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<FollowResponse> call, Throwable t) {
                        if (!isAdded()) return;
                        btn.setEnabled(true);
                        Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
            row.addView(btn);

            tabContent.addView(row);
        }
    }

    private void renderFollowButton(TextView btn, boolean following) {
        btn.setText(following ? "Seguindo" : "Seguir");
        btn.setBackgroundResource(following ? R.drawable.bg_card_accent_green : R.drawable.bg_button_primary);
        btn.setTextColor(following ? ContextCompat.getColor(requireContext(), R.color.green) : 0xFF000000);
    }

    private LinearLayout simpleRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView smallIcon(String emoji) {
        TextView tv = new TextView(requireContext());
        tv.setText(emoji);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        tv.setLayoutParams(lp);
        tv.setBackgroundResource(R.drawable.bg_icon_button);
        return tv;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
