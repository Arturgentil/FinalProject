package com.stepvolt.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferências do app, com nomes tipados. Antes da V3 as configurações viviam
 * só em memória dentro do SettingsFragment e sumiam ao fechar o app; aqui tudo
 * é persistido e lido pelas telas e pelo avaliador de alertas.
 */
public final class Prefs {

    private static final String FILE = "stepvolt_prefs";

    // Dispositivo
    public static final String LAST_DEVICE_ADDR = "last_device_addr";
    public static final String LAST_DEVICE_NAME = "last_device_name";
    public static final String AUTO_CONNECT = "auto_connect";
    public static final String KEEP_ALIVE = "keep_alive";

    // Limites de alerta
    public static final String BPM_MAX = "bpm_max";
    public static final String BPM_MIN = "bpm_min";
    public static final String SPO2_MIN = "spo2_min";
    public static final String TEMP_MAX = "temp_max";
    public static final String ALERTS_ON = "alerts_on";

    // Preferências gerais
    public static final String UNITS = "units";
    public static final String DAILY_STEP_GOAL = "daily_step_goal";
    public static final String DAILY_ENERGY_GOAL = "daily_energy_goal";
    public static final String SYNC_AUTO = "sync_auto";
    public static final String AI_ENABLED = "ai_enabled";

    // v3: tema, onboarding, treino, segurança, plano
    public static final String THEME = "theme";                 // texto da opção: "Escuro" | "Claro" | "Seguir sistema"
    public static final String ONBOARDING_DONE = "onboarding_done";
    public static final String WORKOUT_KIND = "workout_kind";
    public static final String WORKOUT_USE_GPS = "workout_use_gps";
    public static final String FALL_DETECT = "fall_detect";
    public static final String SOS_COUNTDOWN_S = "sos_countdown_s";
    public static final String SOS_SEND_LOCATION = "sos_send_location";
    public static final String PLAN_WEEK_START = "plan_week_start";
    public static final String PLAN_WEEKLY_STEPS = "plan_weekly_steps";
    public static final String PLAN_WEEKLY_ENERGY = "plan_weekly_energy";
    public static final String PLAN_ACTIVE_DAYS = "plan_active_days";
    public static final String PLAN_REASONING = "plan_reasoning";
    public static final String ACTIVITIES_SYNCED_AT = "activities_synced_at";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean getBool(Context c, String key, boolean def) { return sp(c).getBoolean(key, def); }
    public static void setBool(Context c, String key, boolean value) { sp(c).edit().putBoolean(key, value).apply(); }
    public static int getInt(Context c, String key, int def) { return sp(c).getInt(key, def); }
    public static void setInt(Context c, String key, int value) { sp(c).edit().putInt(key, value).apply(); }
    public static long getLong(Context c, String key, long def) { return sp(c).getLong(key, def); }
    public static void setLong(Context c, String key, long value) { sp(c).edit().putLong(key, value).apply(); }
    public static float getFloat(Context c, String key, float def) { return sp(c).getFloat(key, def); }
    public static void setFloat(Context c, String key, float value) { sp(c).edit().putFloat(key, value).apply(); }
    public static String getString(Context c, String key, String def) { return sp(c).getString(key, def); }
    public static void setString(Context c, String key, String value) { sp(c).edit().putString(key, value).apply(); }
    public static void remove(Context c, String key) { sp(c).edit().remove(key).apply(); }

    /** Limites padrão, usados enquanto o usuário não muda nada em Ajustes. */
    public static int bpmMax(Context c) { return getInt(c, BPM_MAX, 180); }
    public static int bpmMin(Context c) { return getInt(c, BPM_MIN, 40); }
    public static int spo2Min(Context c) { return getInt(c, SPO2_MIN, 92); }
    public static float tempMax(Context c) { return getFloat(c, TEMP_MAX, 38.5f); }
    public static boolean alertsOn(Context c) { return getBool(c, ALERTS_ON, true); }
    /** O Ajustes guarda o texto da opção ("Escuro", "Claro", "Seguir sistema"); aqui vira um código. */
    public static String theme(Context c) {
        String v = getString(c, THEME, "Escuro");
        if ("Claro".equals(v)) return "light";
        if ("Seguir sistema".equals(v)) return "system";
        return "dark";
    }
    public static boolean fallDetect(Context c) { return getBool(c, FALL_DETECT, true); }
    public static int sosCountdown(Context c) { return getInt(c, SOS_COUNTDOWN_S, 30); }
    public static boolean sosSendLocation(Context c) { return getBool(c, SOS_SEND_LOCATION, true); }
    public static long dailyStepGoal(Context c) { return getInt(c, DAILY_STEP_GOAL, 8000); }
    public static float dailyEnergyGoal(Context c) { return getFloat(c, DAILY_ENERGY_GOAL, 500f); }
}
