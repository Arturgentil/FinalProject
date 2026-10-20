package com.stepvolt.app.models;

import java.util.ArrayList;
import java.util.List;

public class Post {
    public String id;
    public String user;
    public String name;
    public String avatarUrl;
    public String time;
    public long steps;
    public double energy;
    public int duration;
    public int likes;
    public boolean liked;
    public String shoeColor;
    public List<Comment> comments = new ArrayList<>();

    public Post(String id, String user, String name, String avatarUrl, String time,
                long steps, double energy, int duration, int likes, boolean liked, String shoeColor) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.time = time;
        this.steps = steps;
        this.energy = energy;
        this.duration = duration;
        this.likes = likes;
        this.liked = liked;
        this.shoeColor = shoeColor;
    }
}
