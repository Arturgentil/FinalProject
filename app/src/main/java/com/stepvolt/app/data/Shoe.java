package com.stepvolt.app.data;

/** Um tênis do usuário, identificado pelo endereço Bluetooth. */
public class Shoe {
    public String address;
    public String name;
    public String nickname;
    public String firmware;
    public long addedAt;
    public double distanceM;
    public long steps;
    public double energy;
    public double batteryPct = -1;
    public long batteryUpdated;
    public double batteryDischarged;
    public double retireKm = 700;
    public boolean retired;

    public double distanceKm() { return distanceM / 1000.0; }

    public String title() {
        if (nickname != null && !nickname.trim().isEmpty()) return nickname;
        if (name != null && !name.trim().isEmpty()) return name;
        return address;
    }
}
