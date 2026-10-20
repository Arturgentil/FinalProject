package com.stepvolt.app.models;

public class Comment {
    public String id;
    public String user;
    public String text;
    public String time;

    public Comment(String id, String user, String text, String time) {
        this.id = id;
        this.user = user;
        this.text = text;
        this.time = time;
    }
}
