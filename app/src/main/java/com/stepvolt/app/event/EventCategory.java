package com.stepvolt.app.event;

/**
 * Categoria de todo evento que passa pelo Event Engine.
 *
 * Um evento pertence a exatamente uma categoria. É por ela que telas futuras
 * (Timeline, Automação, Diagnóstico) vão filtrar o que mostram.
 */
public enum EventCategory {
    SAFETY("Segurança"),
    BIOMETRIC("Biometria"),
    ACTIVITY("Atividade"),
    LOCATION("Localização"),
    POWER("Energia"),
    CONNECTIVITY("Conectividade"),
    DEVICE("Dispositivo"),
    SYSTEM("Sistema"),
    EMERGENCY("Emergência");

    private final String label;

    EventCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
