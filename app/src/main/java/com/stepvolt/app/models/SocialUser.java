package com.stepvolt.app.models;

public class SocialUser {
    public String id;
    public String name;
    public String username;
    public String avatarUrl;
    public long steps;
    public double energy;
    public int followers;
    public boolean following;
    public boolean featured;

    public SocialUser(String id, String name, String username, String avatarUrl, long steps,
                       double energy, int followers, boolean following, boolean featured) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.steps = steps;
        this.energy = energy;
        this.followers = followers;
        this.following = following;
        this.featured = featured;
    }
}
