package com.stepvolt.app.models;

/** Mirrors the `user` object kept in AppContext on the React side. */
public class User {
    public int id;
    public String name;
    public String username;
    public String email;
    public String birthDate;
    public double weight;
    public double height;
    public String bio;
    public String avatarUrl;
    /** true por padrão: servidores antigos não enviam o campo e não devem mostrar aviso à toa. */
    public boolean emailVerified = true;

    /** Privacidade: guardada no servidor, que é quem a aplica. */
    public boolean publicProfile = true;
    /** "all" | "followers" | "none" */
    public String commentPolicy = "all";
    public boolean autoShare = false;
    public boolean notifGoal = true;
    public boolean notifRecord = true;
    public boolean notifSocial = true;

    public int following;
    public int followers;
    public int activeDays;

    public long totalSteps;
    public double totalEnergy;
    public long recordSteps;
    public double recordEnergy;

    public ShoeCustomization shoeCustomization = ShoeCustomization.defaultNeon();

    /** Blank placeholder used only until the real profile has loaded from the API. */
    public static User empty() {
        User u = new User();
        u.name = "";
        u.username = "";
        u.email = "";
        u.birthDate = "";
        u.bio = "";
        u.avatarUrl = "";
        return u;
    }
}
