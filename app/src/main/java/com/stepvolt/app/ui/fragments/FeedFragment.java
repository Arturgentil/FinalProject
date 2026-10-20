package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.Comment;
import com.stepvolt.app.models.Post;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.LikeResponse;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.adapters.FeedAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedFragment extends Fragment implements FeedAdapter.Listener {

    private boolean sortByPopular = false;
    private RecyclerView recyclerFeed;
    private FeedAdapter adapter;
    private List<Post> sortedPosts;
    private TextView txtFeedState;
    private TextView txtNotifBadge;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerFeed = view.findViewById(R.id.recyclerFeed);
        LinearLayout btnSort = view.findViewById(R.id.btnSort);
        TextView txtSortLabel = view.findViewById(R.id.txtSortLabel);
        View btnSearch = view.findViewById(R.id.btnSearch);
        txtFeedState = view.findViewById(R.id.txtFeedState);
        txtNotifBadge = view.findViewById(R.id.txtNotifBadge);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> nav().goBack());
        btnSearch.setOnClickListener(v -> nav().navigate(Navigator.SEARCH));
        view.findViewById(R.id.btnNotifications).setOnClickListener(v -> nav().navigate(Navigator.NOTIFICATIONS));

        sortedPosts = new ArrayList<>(AppState.get().feedPosts);
        adapter = new FeedAdapter(sortedPosts, this);
        recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFeed.setAdapter(adapter);

        btnSort.setOnClickListener(v -> {
            sortByPopular = !sortByPopular;
            txtSortLabel.setText(sortByPopular ? "Popular" : "Recentes");
            applySort();
        });

        loadFeed();
    }

    private void loadFeed() {
        txtFeedState.setVisibility(View.VISIBLE);
        txtFeedState.setText("Carregando\u2026");
        AppState.get().refreshFeed((success, data, error) -> {
            if (!isAdded()) return;
            if (success) {
                applySort();
                if (sortedPosts.isEmpty()) {
                    txtFeedState.setVisibility(View.VISIBLE);
                    txtFeedState.setText("Ainda n\u00e3o h\u00e1 publica\u00e7\u00f5es.\nCompartilhe um treino pelo Resumo do treino ou procure pessoas para seguir.");
                } else {
                    txtFeedState.setVisibility(View.GONE);
                }
            } else {
                txtFeedState.setVisibility(sortedPosts.isEmpty() ? View.VISIBLE : View.GONE);
                txtFeedState.setText("N\u00e3o foi poss\u00edvel carregar o feed.\nToque aqui para tentar de novo.");
                txtFeedState.setOnClickListener(v -> loadFeed());
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
        refreshBadge();
    }

    /** Bolinha com o n\u00famero de notifica\u00e7\u00f5es n\u00e3o lidas. */
    private void refreshBadge() {
        AppState.get().refreshNotifications((success, data, error) -> {
            if (!isAdded() || !success || data == null) return;
            int unread = 0;
            for (com.stepvolt.app.models.NotificationItem n : data) if (!n.read) unread++;
            txtNotifBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            txtNotifBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (txtNotifBadge != null) refreshBadge();   // ao voltar das notifica\u00e7\u00f5es, o contador se atualiza
    }

    private void applySort() {
        sortedPosts.clear();
        sortedPosts.addAll(AppState.get().feedPosts);
        if (sortByPopular) {
            Collections.sort(sortedPosts, (a, b) -> b.likes - a.likes);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onToggleLike(Post post, Runnable onDone) {
        ApiClient.get(requireContext()).toggleLike(post.id).enqueue(new Callback<LikeResponse>() {
            @Override
            public void onResponse(Call<LikeResponse> call, Response<LikeResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    post.liked = response.body().liked;
                    post.likes = response.body().likes;
                    onDone.run();
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LikeResponse> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAddComment(Post post, String text, Runnable onDone) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        ApiClient.get(requireContext()).addComment(post.id, body).enqueue(new Callback<Comment>() {
            @Override
            public void onResponse(Call<Comment> call, Response<Comment> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    post.comments.add(response.body());
                    adapter.notifyDataSetChanged();
                    onDone.run();
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Comment> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
