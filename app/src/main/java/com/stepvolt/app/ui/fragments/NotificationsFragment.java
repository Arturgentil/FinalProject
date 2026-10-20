package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.NotificationItem;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.OkResponse;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.adapters.NotificationAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends Fragment implements NotificationAdapter.Listener {

    private RecyclerView recycler;
    private NotificationAdapter adapter;
    private View emptyState;
    private final List<NotificationItem> shown = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View btnBack = view.findViewById(R.id.btnBack);
        View btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);
        recycler = view.findViewById(R.id.recyclerNotifications);
        emptyState = view.findViewById(R.id.emptyState);

        btnBack.setOnClickListener(v -> nav().goBack());
        btnMarkAllRead.setOnClickListener(v -> {
            ApiClient.get(requireContext()).markAllNotificationsRead().enqueue(new Callback<OkResponse>() {
                @Override
                public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                    if (!isAdded()) return;
                    if (response.isSuccessful()) {
                        for (NotificationItem n : AppState.get().notifications) n.read = true;
                        refreshList();
                    } else {
                        Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<OkResponse> call, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        adapter = new NotificationAdapter(shown, this);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        loadNotifications();
    }

    private void loadNotifications() {
        AppState.get().refreshNotifications((success, data, error) -> {
            if (!isAdded()) return;
            if (success) refreshList();
            else Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        });
    }

    private void refreshList() {
        shown.clear();
        shown.addAll(AppState.get().notifications);
        adapter.notifyDataSetChanged();
        boolean empty = shown.isEmpty();
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClicked(NotificationItem item) {
        if (item.read) return;
        ApiClient.get(requireContext()).markNotificationRead(item.id).enqueue(new Callback<OkResponse>() {
            @Override
            public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    item.read = true;
                    refreshList();
                }
            }
            @Override
            public void onFailure(Call<OkResponse> call, Throwable t) {}
        });
    }

    @Override
    public void onRemove(NotificationItem item) {
        ApiClient.get(requireContext()).deleteNotification(item.id).enqueue(new Callback<OkResponse>() {
            @Override
            public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    AppState.get().notifications.remove(item);
                    refreshList();
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<OkResponse> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
