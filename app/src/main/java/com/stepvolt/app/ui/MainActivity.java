package com.stepvolt.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.safety.SosManager;
import com.stepvolt.app.ui.fragments.BiomechanicsFragment;
import com.stepvolt.app.ui.fragments.ChallengesFragment;
import com.stepvolt.app.ui.fragments.DataAccountFragment;
import com.stepvolt.app.ui.fragments.EmergencyFragment;
import com.stepvolt.app.ui.fragments.EnergyFragment;
import com.stepvolt.app.ui.fragments.HelpFragment;
import com.stepvolt.app.ui.fragments.LiveWorkoutFragment;
import com.stepvolt.app.ui.fragments.MyShoesFragment;
import com.stepvolt.app.ui.fragments.OnboardingFragment;
import com.stepvolt.app.ui.fragments.PlansFragment;
import com.stepvolt.app.ui.fragments.WorkoutSummaryFragment;
import com.stepvolt.app.ui.fragments.AchievementsFragment;
import com.stepvolt.app.ui.fragments.ActivityDetailsFragment;
import com.stepvolt.app.ui.fragments.AiFragment;
import com.stepvolt.app.ui.fragments.AlertsFragment;
import com.stepvolt.app.ui.fragments.DeviceFragment;
import com.stepvolt.app.ui.fragments.EditProfileFragment;
import com.stepvolt.app.ui.fragments.FeedFragment;
import com.stepvolt.app.ui.fragments.ForgotPasswordFragment;
import com.stepvolt.app.ui.fragments.GoalsFragment;
import com.stepvolt.app.ui.fragments.HealthFragment;
import com.stepvolt.app.ui.fragments.HistoryFragment;
import com.stepvolt.app.ui.fragments.HomeFragment;
import com.stepvolt.app.ui.fragments.LoginFragment;
import com.stepvolt.app.ui.fragments.NotificationsFragment;
import com.stepvolt.app.ui.fragments.ProfileFragment;
import com.stepvolt.app.ui.fragments.RegisterFragment;
import com.stepvolt.app.ui.fragments.SearchFragment;
import com.stepvolt.app.ui.fragments.SensorsFragment;
import com.stepvolt.app.ui.fragments.SessionDetailsFragment;
import com.stepvolt.app.ui.fragments.SettingsFragment;
import com.stepvolt.app.ui.fragments.ShoeCustomizationFragment;

/**
 * Casca do app: uma única Activity com a barra de abas embaixo e uma pilha de
 * fragmentos em cima.
 *
 * A versão anterior trocava telas cheias sem nenhuma barra — dava para chegar
 * em qualquer lugar, mas não dava para saber onde se estava nem voltar para um
 * lugar fixo. Aqui as cinco abas são raízes: trocar de aba limpa a pilha, e
 * cada tela interna empilha em cima da aba de onde saiu.
 */
public class MainActivity extends AppCompatActivity implements Navigator {

    private BottomNavigationView bottomNav;
    private String currentTab = HOME;
    private boolean suppressNavCallback = false;
    private boolean onboardingShownThisRun = false;
    private AlertDialog sosDialog;
    private final SosManager.Listener sosListener = new SosManager.Listener() {
        @Override public void onTick(int secondsLeft, String reason) { showSosDialog(secondsLeft, reason); }
        @Override public void onCancelled() { dismissSosDialog(); }
        @Override public void onSent(SosManager.SendResult r) {
            dismissSosDialog();
            String msg = r.contacts == 0 ? "Nenhum contato de emergência cadastrado."
                    : r.sentDirect > 0 ? "SOS enviado para " + r.sentDirect + " contato(s)."
                    : r.openedComposer > 0 ? "Mensagem de SOS pronta. Toque em enviar no app de mensagens."
                    : "Não foi possível enviar o SOS.";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavCallback) return true;
            String screen = screenForMenuId(item.getItemId());
            if (screen == null) return false;
            showTab(screen);
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> {
            // Tocar de novo na aba atual volta para a raiz dela.
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        });

        getSupportFragmentManager().addOnBackStackChangedListener(this::syncChrome);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        if (savedInstanceState == null) {
            if (SessionManager.isLoggedIn(this)) {
                showTab(HOME);
            } else {
                navigate(LOGIN, null);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SosManager sos = SosManager.get(this);
        sos.addListener(sosListener);
        if (sos.isCounting()) showSosDialog(Math.max(1, Prefs.sosCountdown(this)), sos.reason());
    }

    @Override
    protected void onStop() {
        super.onStop();
        SosManager.get(this).removeListener(sosListener);
        dismissSosDialog();
    }

    /** Contagem do SOS, visível em qualquer tela: cancelar é o botão grande. */
    private void showSosDialog(int secondsLeft, String reason) {
        String text = "Possível emergência: " + reason + ".\n\nEnviando o pedido de ajuda aos seus contatos em "
                + secondsLeft + " s. Se estiver tudo bem, cancele.";
        if (sosDialog == null || !sosDialog.isShowing()) {
            sosDialog = new AlertDialog.Builder(this)
                    .setTitle("SOS em andamento")
                    .setMessage(text)
                    .setCancelable(false)
                    .setPositiveButton("Estou bem, cancelar", (d, w) -> SosManager.get(this).cancel())
                    .setNegativeButton("Enviar agora", (d, w) -> SosManager.get(this).sendNow(reason))
                    .create();
            sosDialog.show();
        } else {
            sosDialog.setMessage(text);
        }
    }

    private void dismissSosDialog() {
        if (sosDialog != null && sosDialog.isShowing()) sosDialog.dismiss();
        sosDialog = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Se o app ficou offline durante uma sessão, é aqui que a fila sobe.
        if (AppState.get() != null && AppState.get().telemetry != null) {
            AppState.get().telemetry.syncPendingSessionsAuto();
        }
    }

    // ------------------------------------------------------------------
    // Navegação
    // ------------------------------------------------------------------

    @Override
    public void navigate(String screen, Bundle params) {
        if (screen == null) return;
        if (isTab(screen)) {
            showTab(screen);
            return;
        }
        Fragment fragment = create(screen, params);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(R.anim.push_enter, R.anim.push_exit, R.anim.pop_enter, R.anim.pop_exit);
        tx.replace(R.id.fragment_container, fragment, screen);
        tx.addToBackStack(screen);
        tx.commit();
        getSupportFragmentManager().executePendingTransactions();
        syncChrome();
    }

    private void showTab(String tabScreen) {
        currentTab = tabScreen;
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction tx = fm.beginTransaction();
        tx.setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit);
        tx.replace(R.id.fragment_container, create(tabScreen, null), tabScreen);
        tx.commit();
        fm.executePendingTransactions();

        selectNavItem(tabScreen);
        syncChrome();

        // Primeiro uso: mostra as boas-vindas uma vez por abertura do app.
        if (HOME.equals(tabScreen) && !onboardingShownThisRun && SessionManager.isLoggedIn(this)
                && !Prefs.getBool(this, Prefs.ONBOARDING_DONE, false)) {
            onboardingShownThisRun = true;
            navigate(ONBOARDING, null);
        }
    }

    @Override
    public void goBack() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }
        if (!HOME.equals(currentTab) && SessionManager.isLoggedIn(this)) {
            showTab(HOME);
            return;
        }
        finish();
    }

    @Override
    public void onAuthChanged() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (SessionManager.isLoggedIn(this)) {
            showTab(HOME);
        } else {
            currentTab = LOGIN;
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit);
            tx.replace(R.id.fragment_container, create(LOGIN, null), LOGIN);
            tx.commit();
            syncChrome();
        }
    }

    // ------------------------------------------------------------------
    // Barra de abas
    // ------------------------------------------------------------------

    private boolean isTab(String screen) {
        return HOME.equals(screen) || HEALTH.equals(screen) || DEVICE.equals(screen)
                || AI.equals(screen) || PROFILE.equals(screen);
    }

    private String screenForMenuId(int id) {
        if (id == R.id.tab_home) return HOME;
        if (id == R.id.tab_health) return HEALTH;
        if (id == R.id.tab_device) return DEVICE;
        if (id == R.id.tab_ai) return AI;
        if (id == R.id.tab_you) return PROFILE;
        return null;
    }

    private int menuIdForScreen(String screen) {
        if (HEALTH.equals(screen)) return R.id.tab_health;
        if (DEVICE.equals(screen)) return R.id.tab_device;
        if (AI.equals(screen)) return R.id.tab_ai;
        if (PROFILE.equals(screen)) return R.id.tab_you;
        return R.id.tab_home;
    }

    private void selectNavItem(String screen) {
        suppressNavCallback = true;
        bottomNav.setSelectedItemId(menuIdForScreen(screen));
        suppressNavCallback = false;
    }

    /** A barra some nas telas de login/cadastro e volta no resto. */
    private void syncChrome() {
        Fragment top = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        boolean authScreen = top instanceof LoginFragment
                || top instanceof RegisterFragment
                || top instanceof ForgotPasswordFragment
                || top instanceof OnboardingFragment;
        bottomNav.setVisibility(authScreen ? View.GONE : View.VISIBLE);

        // As telas herdadas da versão anterior não sabem que existe uma barra
        // embaixo. Empurrar o container evita que o último cartão fique escondido.
        View container = findViewById(R.id.fragment_container);
        int bottomPad = authScreen ? 0 : getResources().getDimensionPixelSize(R.dimen.nav_height);
        if (container != null && container.getPaddingBottom() != bottomPad) {
            container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                    container.getPaddingRight(), bottomPad);
        }
    }

    @NonNull
    private Fragment create(String screen, Bundle params) {
        Fragment f;
        switch (screen) {
            case HEALTH: f = new HealthFragment(); break;
            case DEVICE: f = new DeviceFragment(); break;
            case AI: f = new AiFragment(); break;
            case PROFILE: f = new ProfileFragment(); break;
            case SENSORS: f = new SensorsFragment(); break;
            case ALERTS: f = new AlertsFragment(); break;
            case HISTORY: f = new HistoryFragment(); break;
            case ACTIVITY_DETAILS: f = new ActivityDetailsFragment(); break;
            case SESSION_DETAILS: f = new SessionDetailsFragment(); break;
            case GOALS: f = new GoalsFragment(); break;
            case ACHIEVEMENTS: f = new AchievementsFragment(); break;
            case SETTINGS: f = new SettingsFragment(); break;
            case EDIT_PROFILE: f = new EditProfileFragment(); break;
            case SHOE_CUSTOMIZATION: f = new ShoeCustomizationFragment(); break;
            case FEED: f = new FeedFragment(); break;
            case SEARCH: f = new SearchFragment(); break;
            case NOTIFICATIONS: f = new NotificationsFragment(); break;
            case REGISTER: f = new RegisterFragment(); break;
            case FORGOT_PASSWORD: f = new ForgotPasswordFragment(); break;
            case ONBOARDING: f = new OnboardingFragment(); break;
            case LIVE_WORKOUT: f = new LiveWorkoutFragment(); break;
            case WORKOUT_SUMMARY: f = new WorkoutSummaryFragment(); break;
            case ENERGY: f = new EnergyFragment(); break;
            case BIOMECHANICS: f = new BiomechanicsFragment(); break;
            case PLANS: f = new PlansFragment(); break;
            case CHALLENGES: f = new ChallengesFragment(); break;
            case MY_SHOES: f = new MyShoesFragment(); break;
            case EMERGENCY: f = new EmergencyFragment(); break;
            case DATA_ACCOUNT: f = new DataAccountFragment(); break;
            case HELP: f = new HelpFragment(); break;
            case LOGIN: f = new LoginFragment(); break;
            case HOME:
            default: f = new HomeFragment(); break;
        }
        if (params != null) f.setArguments(params);
        return f;
    }
}
