package com.stepvolt.app.core;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** Aplica o tema escolhido nos Ajustes. O padrão do app é o escuro. */
public final class ThemeHelper {

    private ThemeHelper() {}

    public static void apply(Context ctx) {
        String t = Prefs.theme(ctx);
        int mode = "light".equals(t) ? AppCompatDelegate.MODE_NIGHT_NO
                : "system".equals(t) ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
