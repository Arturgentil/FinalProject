// config.h — o que está ligado no SEU tênis. Ajuste antes de gravar.
//
// Regra do firmware: sensor desligado aqui, ou que não responde ao iniciar,
// simplesmente NÃO envia aquela grandeza. O firmware nunca inventa um valor.
#pragma once

#define FIRMWARE_VERSION "2.0.0"

// ---- Qual pé é este tênis? 'L' = esquerdo, 'R' = direito.
// As chaves de pressão saem como pl_h/pl_m/pl_t (esquerdo) ou pr_h/pr_m/pr_t (direito).
#define FOOT_SIDE 'L'

// ---- Sensores presentes (1 = ligado no circuito, 0 = não tem)
#define HAS_MAX30102   1   // batimentos e oxigenação (I2C)
#define HAS_MPU6050    1   // passos, cadência, queda (I2C)
#define HAS_INA219     1   // tensão/corrente do gerador -> energia (I2C)
#define HAS_DS18B20    1   // temperatura (OneWire)
#define HAS_FSR        1   // 3 sensores de pressão por pé (ADC)
#define HAS_BATTERY_ADC 1  // divisor de tensão na bateria Li-ion
#define HAS_SOS_BUTTON 1   // botão físico de SOS (segurar 1,5 s)

// ---- Pinos (ESP32 DevKit). Use só pinos ADC1 (32-39) para os FSR: o ADC2
// para de funcionar quando o Bluetooth está ligado.
#define PIN_I2C_SDA   21
#define PIN_I2C_SCL   22
#define PIN_ONEWIRE   4
#define PIN_FSR_HEEL  34
#define PIN_FSR_MID   35
#define PIN_FSR_TOE   32
#define PIN_BATTERY   33   // pelo divisor 1:1 (dois resistores iguais) -> 2x a leitura
#define PIN_SOS       27   // botão para o GND (usa pull-up interno)
#define PIN_LED       2

// ---- Calibração padrão do FSR (ajuste com um peso conhecido, veja o README)
#define FSR_NEWTONS_PER_COUNT 0.35f

// ---- Tempos
#define SAMPLE_PERIOD_MS 20      // leitura do acelerômetro e dos FSR (50 Hz)
#define SEND_INTERVAL_MS 1500    // um pacote a cada 1,5 s
#define SOS_HOLD_MS      1500
#define SIGNAL_PULSE_MS  4000    // quanto tempo "fall" e "sos" ficam em 1 no pacote
