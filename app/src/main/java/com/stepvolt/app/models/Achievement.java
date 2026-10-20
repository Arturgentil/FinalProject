package com.stepvolt.app.models;

public class Achievement {
    public enum Rarity { COMUM, RARO, EPICO, LENDARIO }

    public String id;
    public String icon;
    public String name;
    public String desc;
    public boolean unlocked;
    public String date; // nullable
    public Rarity rarity;
    public String category;

    public Achievement(String id, String icon, String name, String desc, boolean unlocked,
                        String date, Rarity rarity, String category) {
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.desc = desc;
        this.unlocked = unlocked;
        this.date = date;
        this.rarity = rarity;
        this.category = category;
    }

    public static String rarityLabel(Rarity r) {
        switch (r) {
            case RARO: return "raro";
            case EPICO: return "\u00e9pico";
            case LENDARIO: return "lend\u00e1rio";
            default: return "comum";
        }
    }
}
