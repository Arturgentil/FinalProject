package com.stepvolt.app.event;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testa só a regra de bypass de cooldown e a ordenação de severidade — a
 * parte do EventEngine que não depende de Context/SQLite e por isso roda
 * como teste de JVM puro (./gradlew testDebugUnitTest), sem emulador.
 *
 * A integração real (persistência, listeners, o app inteiro conversando com
 * o tênis) só dá para validar com o app rodando em aparelho/emulador — não
 * é isso que este arquivo tenta cobrir.
 */
public class EventEngineLogicTest {

    @Test
    public void safetyCategoryAlwaysBypassesCooldown() {
        assertTrue(EventEngine.bypassesCooldown(EventCategory.SAFETY, EventSeverity.INFO));
        assertTrue(EventEngine.bypassesCooldown(EventCategory.SAFETY, EventSeverity.LOW));
    }

    @Test
    public void emergencyCategoryAlwaysBypassesCooldown() {
        assertTrue(EventEngine.bypassesCooldown(EventCategory.EMERGENCY, EventSeverity.MEDIUM));
    }

    @Test
    public void criticalSeverityAlwaysBypassesCooldownRegardlessOfCategory() {
        assertTrue(EventEngine.bypassesCooldown(EventCategory.BIOMETRIC, EventSeverity.CRITICAL));
        assertTrue(EventEngine.bypassesCooldown(EventCategory.POWER, EventSeverity.CRITICAL));
    }

    @Test
    public void ordinaryEventsRespectCooldown() {
        assertFalse(EventEngine.bypassesCooldown(EventCategory.BIOMETRIC, EventSeverity.MEDIUM));
        assertFalse(EventEngine.bypassesCooldown(EventCategory.POWER, EventSeverity.LOW));
        assertFalse(EventEngine.bypassesCooldown(EventCategory.CONNECTIVITY, EventSeverity.HIGH));
    }

    @Test
    public void severityOrderingIsMonotonic() {
        assertTrue(EventSeverity.CRITICAL.atLeast(EventSeverity.HIGH));
        assertTrue(EventSeverity.HIGH.atLeast(EventSeverity.HIGH));
        assertFalse(EventSeverity.MEDIUM.atLeast(EventSeverity.HIGH));
        assertFalse(EventSeverity.INFO.atLeast(EventSeverity.LOW));
    }
}
