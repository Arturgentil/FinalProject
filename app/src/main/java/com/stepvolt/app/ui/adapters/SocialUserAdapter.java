package com.stepvolt.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.R;
import com.stepvolt.app.models.SocialUser;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.ImgLoader;

import java.util.List;

/** Doesn't call the network itself - onFollowToggled delegates to the Fragment,
 *  which hits the real API and only flips SocialUser.following once confirmed. */
public class SocialUserAdapter extends RecyclerView.Adapter<SocialUserAdapter.VH> {

    public interface FollowListener {
        void onFollowToggled(SocialUser user, Runnable onDone);
    }

    private final List<SocialUser> users;
    private final boolean showStats;
    private final FollowListener listener;

    public SocialUserAdapter(List<SocialUser> users, boolean showStats, FollowListener listener) {
        this.users = users;
        this.showStats = showStats;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_social_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SocialUser u = users.get(position);
        ImgLoader.loadAvatar(h.imgAvatar, u.avatarUrl, u.name);
        h.txtName.setText(u.name);
        h.txtHandle.setText("@" + u.username);
        h.txtFollowers.setText(Fmt.intGrouped(u.followers) + " seguidores");
        h.badgeFeatured.setVisibility(u.featured ? View.VISIBLE : View.GONE);
        h.rowStats.setVisibility(showStats ? View.VISIBLE : View.GONE);
        if (showStats) {
            h.txtSteps.setText(Fmt.millions(u.steps));
            h.txtEnergy.setText(Fmt.thousands(u.energy));
        }
        renderFollow(h, u);
        h.btnFollow.setOnClickListener(v -> {
            if (listener == null) return;
            h.btnFollow.setEnabled(false);
            listener.onFollowToggled(u, () -> {
                h.btnFollow.setEnabled(true);
                renderFollow(h, u);
            });
        });
    }

    private void renderFollow(VH h, SocialUser u) {
        if (u.following) {
            h.btnFollow.setText("Seguindo");
            h.btnFollow.setBackgroundResource(R.drawable.bg_card_accent_green);
            h.btnFollow.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.green));
        } else {
            h.btnFollow.setText("Seguir");
            h.btnFollow.setBackgroundResource(R.drawable.bg_button_primary);
            h.btnFollow.setTextColor(0xFF000000);
        }
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ShapeableImageView imgAvatar;
        TextView txtName, txtHandle, txtFollowers, badgeFeatured, txtSteps, txtEnergy;
        View rowStats;
        Button btnFollow;

        VH(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            txtName = itemView.findViewById(R.id.txtName);
            txtHandle = itemView.findViewById(R.id.txtHandle);
            txtFollowers = itemView.findViewById(R.id.txtFollowers);
            badgeFeatured = itemView.findViewById(R.id.badgeFeatured);
            rowStats = itemView.findViewById(R.id.rowStats);
            txtSteps = itemView.findViewById(R.id.txtSteps);
            txtEnergy = itemView.findViewById(R.id.txtEnergy);
            btnFollow = itemView.findViewById(R.id.btnFollow);
        }
    }
}
