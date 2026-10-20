package com.stepvolt.app.data;

/** Um ponto de série temporal: instante e valor. */
public class Point {
    public final long ts;
    public final double value;

    public Point(long ts, double value) {
        this.ts = ts;
        this.value = value;
    }
}
