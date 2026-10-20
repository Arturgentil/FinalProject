package com.stepvolt.app.models;

/** Mirrors the `Notification` type from AppContext. Named NotificationItem to avoid
 *  clashing with android.app.Notification. */
public class NotificationItem {
    public enum Type { LIKE, COMMENT, GOAL, RECORD, HEALTH, FOLLOW }

    public String id;
    public Type type;
    public String message;
    public String time;
    public String avatarUrl; // nullable
    public boolean read;

    public NotificationItem(String id, Type type, String message, String time, String avatarUrl, boolean read) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.time = time;
        this.avatarUrl = avatarUrl;
        this.read = read;
    }
}
