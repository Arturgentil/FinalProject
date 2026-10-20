package com.stepvolt.app.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.models.SocialUser;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.FollowResponse;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.adapters.SocialUserAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchFragment extends Fragment implements SocialUserAdapter.FollowListener {

    private RecyclerView recyclerUsers;
    private SocialUserAdapter adapter;
    private TextView txtLabel;
    private String currentQuery = "";
    private final List<SocialUser> shown = new ArrayList<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View btnBack = view.findViewById(R.id.btnBack);
        EditText inputSearch = view.findViewById(R.id.inputSearch);
        txtLabel = view.findViewById(R.id.txtLabel);
        recyclerUsers = view.findViewById(R.id.recyclerUsers);

        btnBack.setOnClickListener(v -> nav().navigate(Navigator.FEED));

        adapter = new SocialUserAdapter(shown, true, this);
        recyclerUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerUsers.setAdapter(adapter);

        search("");

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> search(s.toString());
                debounceHandler.postDelayed(pendingSearch, 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void search(String query) {
        currentQuery = query;
        AppState.get().refreshSocialUsers(query, (success, data, error) -> {
            if (!isAdded() || !query.equals(currentQuery)) return;
            if (!success) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                return;
            }
            shown.clear();
            shown.addAll(data);
            adapter.notifyDataSetChanged();
            if (query.trim().isEmpty()) {
                txtLabel.setText("\uD83D\uDD25 Usu\u00e1rios em destaque");
            } else {
                txtLabel.setText(shown.size() + " resultado(s) para \"" + query + "\"");
            }
        });
    }

    @Override
    public void onFollowToggled(SocialUser user, Runnable onDone) {
        ApiClient.get(requireContext()).toggleFollow(user.id).enqueue(new Callback<FollowResponse>() {
            @Override
            public void onResponse(Call<FollowResponse> call, Response<FollowResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    user.following = response.body().following;
                } else {
                    Toast.makeText(requireContext(), ApiUtil.parseError(response), Toast.LENGTH_SHORT).show();
                }
                onDone.run();
            }

            @Override
            public void onFailure(Call<FollowResponse> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Sem conex\u00e3o com o servidor.", Toast.LENGTH_SHORT).show();
                onDone.run();
            }
        });
    }

    private Navigator nav() { return (Navigator) requireActivity(); }
}
