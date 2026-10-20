package com.stepvolt.app.ble;

/** Estados possíveis da ligação com o tênis. A UI desenha um por um. */
public enum BleState {
    IDLE,
    BLUETOOTH_OFF,
    NO_PERMISSION,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    CONNECTED,
    RECONNECTING,
    FAILED;

    public boolean isBusy() {
        return this == SCANNING || this == CONNECTING || this == DISCOVERING || this == RECONNECTING;
    }

    public boolean isLive() {
        return this == CONNECTED;
    }
}
