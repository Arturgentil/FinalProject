package com.stepvolt.app.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.R;
import com.stepvolt.app.models.Comment;
import com.stepvolt.app.models.Post;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.ImgLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the feed. Doesn't talk to the network itself - every action (like,
 * comment) is delegated to the Listener so the Fragment can call the real API
 * and only touch the Post object once the server confirms the change.
 */
public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    public interface Listener {
        void onToggleLike(Post post, Runnable onDone);
        void onAddComment(Post post, String text, Runnable onDone);
    }

    private final List<Post> posts;
    private final Listener listener;
    private final Set<String> expandedComments = new HashSet<>();
    private String commentInputPostId = null;

    public FeedAdapter(List<Post> posts, Listener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_post_full, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Post post = posts.get(position);

        ImgLoader.loadAvatar(h.imgAvatar, post.avatarUrl, post.name);
        h.txtName.setText(post.name);
        h.txtHandle.setText("@" + post.user + " \u00b7 " + post.time);
        try { h.shoeSwatch.setBackgroundColor(Color.parseColor(post.shoeColor)); } catch (Exception ignored) {}
        h.txtSteps.setText(Fmt.intGrouped(post.steps));
        h.txtEnergy.setText(Fmt.energy(post.energy));
        h.txtDuration.setText(String.valueOf(post.duration));
        h.txtCommentsCount.setText(String.valueOf(post.comments.size()));

        renderLike(h, post);

        h.btnLike.setOnClickListener(v -> {
            if (listener == null) return;
            listener.onToggleLike(post, () -> {
                renderLike(h, post);
            });
        });

        boolean expanded = expandedComments.contains(post.id);
        h.containerComments.setVisibility(expanded && !post.comments.isEmpty() ? View.VISIBLE : View.GONE);
        if (expanded) renderComments(h, post);

        h.btnToggleComments.setOnClickListener(v -> {
            expandedComments.add(post.id);
            boolean showingInput = post.id.equals(commentInputPostId);
            commentInputPostId = showingInput ? null : post.id;
            notifyItemChanged(h.getBindingAdapterPosition());
        });

        boolean showInput = post.id.equals(commentInputPostId);
        h.containerCommentInput.setVisibility(showInput ? View.VISIBLE : View.GONE);
        h.inputComment.setText("");

        h.btnSendComment.setOnClickListener(v -> {
            String text = h.inputComment.getText().toString().trim();
            if (text.isEmpty() || listener == null) return;
            listener.onAddComment(post, text, () -> {
                expandedComments.add(post.id);
                commentInputPostId = null;
                notifyItemChanged(h.getBindingAdapterPosition());
            });
        });

        h.btnCloseComment.setOnClickListener(v -> {
            commentInputPostId = null;
            notifyItemChanged(h.getBindingAdapterPosition());
        });
    }

    private void renderLike(VH h, Post post) {
        h.iconLike.setText(post.liked ? "\u2665" : "\u2661");
        h.iconLike.setTextColor(post.liked ? 0xFFFF6B6B : 0xFF4A4A6A);
        h.txtLikes.setText(String.valueOf(post.likes));
        h.txtLikes.setTextColor(post.liked ? 0xFFFF6B6B : 0xFF4A4A6A);
    }

    private void renderComments(VH h, Post post) {
        h.containerComments.removeAllViews();
        for (Comment c : post.comments) {
            LinearLayout row = new LinearLayout(h.itemView.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(h, 6), 0, dp(h, 6));

            TextView bubble = new TextView(h.itemView.getContext());
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            String handle = "@" + c.user + " ";
            sb.append(handle);
            sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF00F5A0), 0, handle.length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, handle.length(), 0);
            sb.append(c.text);
            bubble.setText(sb);
            bubble.setTextColor(0xFF8B8BA7);
            bubble.setTextSize(12.5f);
            row.addView(bubble);
            h.containerComments.addView(row);
        }
    }

    private int dp(VH h, int value) {
        return Math.round(value * h.itemView.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() { return posts.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ShapeableImageView imgAvatar;
        TextView txtName, txtHandle, txtSteps, txtEnergy, txtDuration, iconLike, txtLikes, txtCommentsCount;
        View shoeSwatch, btnLike, btnToggleComments;
        LinearLayout containerComments, containerCommentInput;
        EditText inputComment;
        ImageButton btnSendComment, btnCloseComment;

        VH(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            txtName = itemView.findViewById(R.id.txtName);
            txtHandle = itemView.findViewById(R.id.txtHandle);
            shoeSwatch = itemView.findViewById(R.id.shoeSwatch);
            txtSteps = itemView.findViewById(R.id.txtSteps);
            txtEnergy = itemView.findViewById(R.id.txtEnergy);
            txtDuration = itemView.findViewById(R.id.txtDuration);
            btnLike = itemView.findViewById(R.id.btnLike);
            iconLike = itemView.findViewById(R.id.iconLike);
            txtLikes = itemView.findViewById(R.id.txtLikes);
            btnToggleComments = itemView.findViewById(R.id.btnToggleComments);
            txtCommentsCount = itemView.findViewById(R.id.txtCommentsCount);
            containerComments = itemView.findViewById(R.id.containerComments);
            containerCommentInput = itemView.findViewById(R.id.containerCommentInput);
            inputComment = itemView.findViewById(R.id.inputComment);
            btnSendComment = itemView.findViewById(R.id.btnSendComment);
            btnCloseComment = itemView.findViewById(R.id.btnCloseComment);
        }
    }
}
