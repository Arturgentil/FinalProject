package com.stepvolt.app.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** One completed walking/running session. Named ActivityRecord to avoid clashing
 *  with android.app.Activity. Mirrors the `activity` objects used across
 *  HistoryScreen / ActivityDetailsScreen / ProfileScreen. */
public class ActivityRecord implements Serializable {
    public String id;
    public String date;
    public String time;
    public String endTime;
    public long steps;
    public double energy;
    public int duration; // minutes
    public double distance; // km
    public int bpmAvg;
    /** Real BPM samples captured during the session via BLE (empty if none were recorded). */
    public List<Integer> bpmSamples = new ArrayList<>();
    /** Real SpO2 samples captured during the session via BLE (empty if none were recorded). */
    public List<Integer> spo2Samples = new ArrayList<>();
    public boolean shared;

    public ActivityRecord() {}

    public ActivityRecord(String id, String date, String time, String endTime, long steps,
                           double energy, int duration, double distance, int bpmAvg, boolean shared) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.endTime = endTime;
        this.steps = steps;
        this.energy = energy;
        this.duration = duration;
        this.distance = distance;
        this.bpmAvg = bpmAvg;
        this.shared = shared;
    }
}
