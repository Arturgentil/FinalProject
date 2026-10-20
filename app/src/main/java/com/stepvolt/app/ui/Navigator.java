package com.stepvolt.app.ui;

import android.os.Bundle;

/**
 * Nomes das telas. As cinco primeiras são as abas da barra de baixo; as demais
 * são telas internas, abertas a partir de alguma aba e empilhadas de forma que
 * o botão Voltar do Android funcione como as pessoas esperam.
 */
public interface Navigator {

    // Abas
    String HOME = "home";
    String HEALTH = "health";
    String DEVICE = "device";
    String AI = "ai";
    String PROFILE = "profile";

    // Autenticação
    String LOGIN = "login";
    String REGISTER = "register";
    String FORGOT_PASSWORD = "forgot-password";

    // Telas internas
    String SENSORS = "sensors";
    String ALERTS = "alerts";
    String HISTORY = "history";
    String ACTIVITY_DETAILS = "activity-details";
    String SESSION_DETAILS = "session-details";
    String GOALS = "goals";
    String ACHIEVEMENTS = "achievements";
    String SETTINGS = "settings";
    String EDIT_PROFILE = "edit-profile";
    String SHOE_CUSTOMIZATION = "shoe-customization";
    String FEED = "feed";
    String SEARCH = "search";
    String NOTIFICATIONS = "notifications";

    // v3
    String ONBOARDING = "onboarding";
    String LIVE_WORKOUT = "live-workout";
    String WORKOUT_SUMMARY = "workout-summary";
    String ENERGY = "energy";
    String BIOMECHANICS = "biomechanics";
    String PLANS = "plans";
    String CHALLENGES = "challenges";
    String MY_SHOES = "my-shoes";
    String EMERGENCY = "emergency";
    String DATA_ACCOUNT = "data-account";
    String HELP = "help";

    // Mantido para compatibilidade com código antigo que apontava para o painel.
    String DASHBOARD = HOME;

    void navigate(String screen, Bundle params);

    default void navigate(String screen) { navigate(screen, null); }

    void goBack();

    /** Chamado depois de entrar ou sair da conta, para recolocar o app no lugar certo. */
    void onAuthChanged();
}
