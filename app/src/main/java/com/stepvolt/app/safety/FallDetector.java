package com.stepvolt.app.safety;

/**
 * Detecta queda a partir do que o tênis manda: um pico de impacto seguido de
 * imobilidade. Passo normal tem impacto alto, mas é seguido de mais passos; já
 * a queda tem um pico (ou ausência de padrão) e depois nada.
 *
 * A decisão é deliberadamente conservadora: o app não aciona o SOS sozinho —
 * ele abre uma contagem regressiva para a pessoa cancelar. Falso alarme custa
 * 30 segundos; alarme perdido custa muito mais.
 *
 * Funciona só com o que existe no tênis ("impact" em N e "steps"). Entradas:
 * chame onSample() a cada pacote, em ordem, com o relógio em milissegundos.
 */
public class FallDetector {

    public interface Listener {
        void onFallSuspected(long at, double impact);
    }

    private final double impactThresholdN;
    private final long stillnessMs;
    private final long cooldownMs;
    private Listener listener;

    private long peakAt = 0;
    private double peakImpact = 0;
    private long lastStepAt = 0;
    private long lastTriggerAt = -1;
    private boolean armed = false;

    public FallDetector(double impactThresholdN, long stillnessMs, long cooldownMs) {
        this.impactThresholdN = impactThresholdN;
        this.stillnessMs = stillnessMs;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Limites padrão do app: 2.500 N, 8 s parado, 2 min entre alarmes.
     * Esta regra olha só a pressão no pé, que é um sinal fraco para queda (uma
     * corrida forte seguida de parada também parece "impacto + imobilidade").
     * Por isso o limite é alto e o app só a usa quando o tênis NÃO tem
     * acelerômetro próprio; com o firmware v2 (MPU6050) vale a detecção feita
     * no tênis, que usa queda livre + impacto + imobilidade.
     */
    public FallDetector() { this(2500, 8000, 120000); }

    public void setListener(Listener l) { this.listener = l; }

    public void onSample(long now, double impactN, long stepsDelta) {
        if (stepsDelta > 0) {
            lastStepAt = now;
            // Voltou a andar: o pico anterior era só um passo forte.
            if (armed && now - peakAt > 500) armed = false;
        }

        if (impactN >= impactThresholdN) {
            armed = true;
            peakAt = now;
            peakImpact = impactN;
            return;
        }

        if (armed && now - peakAt >= stillnessMs) {
            boolean movedSincePeak = lastStepAt > peakAt + 500;
            armed = false;
            if (!movedSincePeak && (lastTriggerAt < 0 || now - lastTriggerAt >= cooldownMs)) {
                lastTriggerAt = now;
                if (listener != null) listener.onFallSuspected(now, peakImpact);
            }
        }
    }

    public void reset() {
        armed = false;
        peakAt = 0;
        peakImpact = 0;
    }
}
