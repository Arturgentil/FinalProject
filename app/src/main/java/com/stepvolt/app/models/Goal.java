package com.stepvolt.app.models;

public class Goal {
    public enum GoalType { PASSOS, ENERGIA, BATIMENTOS, OXIGENACAO }
    public enum Period { DIARIO, SEMANAL, MENSAL }

    public String id;
    public GoalType type;
    public double target;
    public double current;
    public Period period;
    public boolean completed;
    public String completedDate;

    public Goal(String id, GoalType type, double target, double current, Period period) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.current = current;
        this.period = period;
    }

    public static String label(GoalType t) {
        switch (t) {
            case PASSOS: return "Passos";
            case ENERGIA: return "Energia";
            case BATIMENTOS: return "Batimentos";
            default: return "Oxigena\u00e7\u00e3o";
        }
    }

    public static String unit(GoalType t) {
        switch (t) {
            case PASSOS: return "passos";
            case ENERGIA: return "mWh";
            case BATIMENTOS: return "bpm";
            default: return "%";
        }
    }

    public static String periodLabel(Period p) {
        switch (p) {
            case DIARIO: return "di\u00e1rio";
            case SEMANAL: return "semanal";
            default: return "mensal";
        }
    }
}
