package com.stepvolt.app.event;

/**
 * Nível de urgência de um evento, do menos ao mais grave.
 *
 * A ordem de declaração importa: {@link #atLeast(EventSeverity)} compara pelo
 * ordinal, então CRITICAL sempre vem por último aqui.
 */
public enum EventSeverity {
    INFO(0xFF4CC9F0, "Informação"),
    LOW(0xFF00F5A0, "Baixo"),
    MEDIUM(0xFFFFD166, "Médio"),
    HIGH(0xFFFF8A3D, "Alto"),
    CRITICAL(0xFFFF3D71, "Crítico");

    private final int color;
    private final String label;

    EventSeverity(int color, String label) {
        this.color = color;
        this.label = label;
    }

    public int color() {
        return color;
    }

    public String label() {
        return label;
    }

    /** true se esta severidade é igual ou mais grave que {@code other}. */
    public boolean atLeast(EventSeverity other) {
        return ordinal() >= other.ordinal();
    }
}
