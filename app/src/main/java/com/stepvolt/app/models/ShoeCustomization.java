package com.stepvolt.app.models;

/** Mirrors the `shoeCustomization` object from AppContext (upper/sole/laces/details colors + theme id). */
public class ShoeCustomization {
    public String upper;
    public String sole;
    public String laces;
    public String details;
    public String theme;

    public ShoeCustomization() {}

    public ShoeCustomization(String upper, String sole, String laces, String details, String theme) {
        this.upper = upper;
        this.sole = sole;
        this.laces = laces;
        this.details = details;
        this.theme = theme;
    }

    public ShoeCustomization copy() {
        return new ShoeCustomization(upper, sole, laces, details, theme);
    }

    public static ShoeCustomization defaultNeon() {
        return new ShoeCustomization("#00F5A0", "#1A1A2E", "#FFFFFF", "#7C3AFF", "neon");
    }
}
