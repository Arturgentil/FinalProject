package com.stepvolt.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.stepvolt.app.R;
import com.stepvolt.app.models.NotificationItem;
import com.stepvolt.app.util.ImgLoader;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface Listener {
        void onClicked(NotificationItem item);
        void onRemove(NotificationItem item);
    }

    private final List<NotificationItem> items;
    private final Listener listener;

    public NotificationAdapter(List<NotificationItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NotificationItem n = items.get(position);
        h.txtMessage.setText(n.message);
        h.txtTime.setText(n.time);
        h.txtMessage.setTextColor(n.read ? 0xFFC0C0D0 : 0xFFFFFFFF);
        h.dotUnread.setVisibility(n.read ? View.GONE : View.VISIBLE);
        h.itemView.setBackgroundResource(n.read ? R.drawable.bg_card : R.drawable.bg_card_accent_green);

        if (n.avatarUrl != null) {
            h.imgAvatar.setVisibility(View.VISIBLE);
            h.iconContainer.setVisibility(View.GONE);
            ImgLoader.loadAvatar(h.imgAvatar, n.avatarUrl, null);
        } else {
            h.imgAvatar.setVisibility(View.GONE);
            h.iconContainer.setVisibility(View.VISIBLE);
            h.txtIcon.setText(iconFor(n.type));
        }

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClicked(n); });
        h.btnRemove.setOnClickListener(v -> { if (listener != null) listener.onRemove(n); });
    }

    private String iconFor(NotificationItem.Type type) {
        switch (type) {
            case LIKE: return "\u2764\uFE0F";
            case COMMENT: return "\uD83D\uDCAC";
            case GOAL: return "\uD83C\uDFAF";
            case RECORD: return "\uD83C\uDFC6";
            case HEALTH: return "\u26A0\uFE0F";
            default: return "\uD83D\uDC65";
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ShapeableImageView imgAvatar;
        FrameLayout iconContainer;
        TextView txtIcon, txtMessage, txtTime;
        View dotUnread;
        ImageButton btnRemove;

        VH(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            txtIcon = itemView.findViewById(R.id.txtIcon);
            txtMessage = itemView.findViewById(R.id.txtMessage);
            txtTime = itemView.findViewById(R.id.txtTime);
            dotUnread = itemView.findViewById(R.id.dotUnread);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
