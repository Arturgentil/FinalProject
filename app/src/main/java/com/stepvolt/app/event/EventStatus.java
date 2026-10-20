package com.stepvolt.app.event;

/**
 * Ciclo de vida de um evento depois de registrado.
 *
 * ACTIVE: acabou de acontecer, ninguém tratou ainda.
 * ACKNOWLEDGED: alguém viu (hoje, principalmente "a tela foi aberta").
 * RESOLVED: o que gerou o evento já passou (ex.: bateria que estava baixa e
 * foi recarregada, queda que foi cancelada pelo usuário). Fases futuras
 * (Safety Engine, automações) é que vão decidir quando marcar RESOLVED.
 */
public enum EventStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
