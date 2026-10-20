/*
  StepVolt — firmware 2.0 para ESP32 (tênis inteligente)
  ------------------------------------------------------
  Lê os sensores DE VERDADE e manda para o app por Bluetooth LE, usando o
  protocolo v2 (JSON + checksum, comandos com confirmação). Nada aqui é
  simulado: sensor ausente ou sem resposta => a grandeza não é enviada.

  Bibliotecas (Gerenciador de Bibliotecas do Arduino):
    - SparkFun MAX3010x Pulse and Proximity Sensor Library   (MAX30102)
    - Adafruit MPU6050   (e a dependência Adafruit Unified Sensor)
    - Adafruit INA219
    - DallasTemperature  (e OneWire)
  O Bluetooth e o Preferences já vêm no core "esp32" da Espressif.

  Perfil BLE (igual ao que o app espera):
    Service  6E400001-B5A3-F393-E0A9-E50E24DCCA9E  (Nordic UART)
    TX notify 6E400003-...  tênis -> celular
    RX write  6E400002-...  celular -> tênis

  Pacote (uma linha, termina em \n):
    {"v":2,"seq":41,"bpm":82,"spo2":97,"steps":3,"energy":0.42,"temp":33.2,
     "battery":74,"cadence":112,"contact":240,"impact":980,"volt":3.31,
     "curr":14.2,"pwr":47.0,"pl_h":410,"pl_m":120,"pl_t":380,"fw":"2.0.0"}*5B

    steps e energy são DELTAS desde o último pacote (o app soma).
    O checksum é o XOR de todos os bytes do JSON, em 2 dígitos hexadecimais.
    "fall":1 e "sos":1 aparecem por alguns segundos quando acontecem.

  Comandos (app -> tênis), no formato CMD:NOME[:ARG]:ID*XX. O tênis responde
  {"v":2,"ack":"NOME","id":ID,"ok":1,"msg":"..."}*XX
    STATUS     envia um pacote agora
    CALIBRATE  zera a pressão com o pé no ar (guarda na memória do ESP32)
    RESET      zera os contadores internos de passos
    BLINK      pisca o LED
  O formato antigo (texto puro, ex.: "STATUS") continua aceito.
*/

#include <Wire.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include "config.h"
#include "StepVoltLogic.h"

#if HAS_MAX30102
  #include "MAX30105.h"
  #include "spo2_algorithm.h"
#endif
#if HAS_MPU6050
  #include <Adafruit_MPU6050.h>
  #include <Adafruit_Sensor.h>
#endif
#if HAS_INA219
  #include <Adafruit_INA219.h>
#endif
#if HAS_DS18B20
  #include <OneWire.h>
  #include <DallasTemperature.h>
#endif

#define SERVICE_UUID  "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHAR_TX_UUID  "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHAR_RX_UUID  "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

// ---------------------------------------------------------------------------
// Estado
// ---------------------------------------------------------------------------
BLEServer* bleServer = nullptr;
BLECharacteristic* txChar = nullptr;
volatile bool connected = false;
volatile uint16_t connId = 0;
volatile uint16_t peerMtu = 23;

Preferences prefs;

uint32_t seq = 0;
int packetsSinceConnect = 0;
uint32_t lastSend = 0;
uint32_t lastSampleMs = 0;

// sensores presentes DE FATO (iniciaram sem erro)
bool okMax = false, okMpu = false, okIna = false, okTemp = false;

// lógica
sv::StepDetector stepDet;
sv::CadenceMeter cadence;
sv::FallDetector fallDet;
sv::EnergyMeter energy;
sv::ContactTimer contact;
sv::FsrScale fsrHeel, fsrMid, fsrToe;

// acumuladores entre pacotes
uint32_t stepsSent = 0;
float peakForceN = 0;
double zoneSumH = 0, zoneSumM = 0, zoneSumT = 0;
uint32_t zoneCount = 0;
uint32_t contactReportedMs = 0;
bool contactPending = false;
float lastVolt = NAN, lastCurrentMa = NAN, lastPowerMw = NAN;

// pulsos de segurança
uint32_t fallUntil = 0, sosUntil = 0;
uint32_t sosPressedSince = 0;
bool sosHeld = false;

// comando recebido (tratado no loop, fora da task do BLE)
volatile bool cmdPending = false;
char cmdLine[96];

#if HAS_MAX30102
MAX30105 maxSensor;
const int SPO2_BUF = 100;
uint32_t irBuf[SPO2_BUF], redBuf[SPO2_BUF];
int bufFill = 0, newSamples = 0;
int32_t spo2Val = 0, hrVal = 0;
int8_t spo2Valid = 0, hrValid = 0;
bool fingerOn = false;
#endif
#if HAS_MPU6050
Adafruit_MPU6050 mpu;
#endif
#if HAS_INA219
Adafruit_INA219 ina;
#endif
#if HAS_DS18B20
OneWire oneWire(PIN_ONEWIRE);
DallasTemperature tempSensor(&oneWire);
float lastTempC = NAN;
uint32_t lastTempReq = 0;
bool tempWaiting = false;
#endif

// ---------------------------------------------------------------------------
// BLE
// ---------------------------------------------------------------------------
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server, esp_ble_gatts_cb_param_t* param) override {
    connected = true;
    connId = param->connect.conn_id;
    peerMtu = 23;
    packetsSinceConnect = 0;
    Serial.println("Celular conectado.");
  }
  void onDisconnect(BLEServer* server) override {
    connected = false;
    Serial.println("Celular desconectado; anunciando de novo.");
    server->getAdvertising()->start();
  }
  void onMtuChanged(BLEServer* server, esp_ble_gatts_cb_param_t* param) override {
    peerMtu = param->mtu.mtu;
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = c->getValue().c_str();
    if (v.length() == 0 || v.length() >= sizeof(cmdLine)) return;
    if (cmdPending) return;               // ainda tratando o anterior: o app repete após o timeout
    strncpy(cmdLine, v.c_str(), sizeof(cmdLine) - 1);
    cmdLine[sizeof(cmdLine) - 1] = 0;
    cmdPending = true;
  }
};

// Envia a linha em pedaços do tamanho que o MTU negociado permite.
void bleSend(const char* data, size_t len) {
  if (!connected || !txChar) return;
  size_t chunk = peerMtu > 23 ? peerMtu - 3 : 20;
  if (chunk > 180) chunk = 180;
  for (size_t off = 0; off < len; off += chunk) {
    size_t n = (len - off < chunk) ? (len - off) : chunk;
    txChar->setValue((uint8_t*)(data + off), n);
    txChar->notify();
    delay(6);
  }
}

void sendSealed(const char* payload) {
  char line[400];
  size_t n = sv::seal(line, sizeof(line), payload);
  if (n == 0) { Serial.println("Pacote grande demais; descartado."); return; }
  bleSend(line, n);
}

// ---------------------------------------------------------------------------
// Sensores
// ---------------------------------------------------------------------------
void blinkLed() {
  for (int i = 0; i < 4; i++) {
    digitalWrite(PIN_LED, HIGH); delay(120);
    digitalWrite(PIN_LED, LOW);  delay(120);
  }
}

float readFsrCounts(int pin) {
  // média de 4 leituras: o ADC do ESP32 é ruidoso
  uint32_t s = 0;
  for (int i = 0; i < 4; i++) s += analogRead(pin);
  return s / 4.0f;
}

void loadCalibration() {
  prefs.begin("stepvolt", true);
  fsrHeel.offset = prefs.getFloat("offH", 0);
  fsrMid.offset  = prefs.getFloat("offM", 0);
  fsrToe.offset  = prefs.getFloat("offT", 0);
  float scale = prefs.getFloat("scale", FSR_NEWTONS_PER_COUNT);
  prefs.end();
  fsrHeel.newtonsPerCount = fsrMid.newtonsPerCount = fsrToe.newtonsPerCount = scale;
}

// Zera os FSR com o pé no ar. Recusa se algum sensor já estiver apertado.
bool calibrateFsr(const char** msg) {
#if HAS_FSR
  float h = 0, m = 0, t = 0;
  const int N = 25;
  for (int i = 0; i < N; i++) {
    h += readFsrCounts(PIN_FSR_HEEL);
    m += readFsrCounts(PIN_FSR_MID);
    t += readFsrCounts(PIN_FSR_TOE);
    delay(20);
  }
  h /= N; m /= N; t /= N;
  if (h > 1500 || m > 1500 || t > 1500) {
    *msg = "Tire o peso do pe e tente de novo.";
    return false;
  }
  fsrHeel.offset = h; fsrMid.offset = m; fsrToe.offset = t;
  prefs.begin("stepvolt", false);
  prefs.putFloat("offH", h); prefs.putFloat("offM", m); prefs.putFloat("offT", t);
  prefs.end();
  *msg = "Calibrado com o pe no ar.";
  return true;
#else
  *msg = "Este tenis nao tem sensores de pressao.";
  return false;
#endif
}

void initSensors() {
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  Wire.setClock(400000);

#if HAS_MAX30102
  okMax = maxSensor.begin(Wire, I2C_SPEED_FAST);
  if (okMax) {
    // 25 amostras/s, média 4, LED 0x24: valores do exemplo oficial de SpO2.
    maxSensor.setup(0x24, 4, 2, 100, 411, 4096);
  }
  Serial.println(okMax ? "MAX30102 ok" : "MAX30102 NAO encontrado: sem bpm/spo2");
#endif
#if HAS_MPU6050
  okMpu = mpu.begin();
  if (okMpu) {
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setFilterBandwidth(MPU6050_BAND_44_HZ);
  }
  Serial.println(okMpu ? "MPU6050 ok" : "MPU6050 NAO encontrado: sem passos/queda");
#endif
#if HAS_INA219
  okIna = ina.begin();
  Serial.println(okIna ? "INA219 ok" : "INA219 NAO encontrado: sem energia gerada");
#endif
#if HAS_DS18B20
  tempSensor.begin();
  tempSensor.setWaitForConversion(false);
  okTemp = tempSensor.getDeviceCount() > 0;
  Serial.println(okTemp ? "DS18B20 ok" : "DS18B20 NAO encontrado: sem temperatura");
#endif
#if HAS_FSR
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_FSR_HEEL, ADC_11db);
  analogSetPinAttenuation(PIN_FSR_MID, ADC_11db);
  analogSetPinAttenuation(PIN_FSR_TOE, ADC_11db);
#endif
#if HAS_BATTERY_ADC
  analogSetPinAttenuation(PIN_BATTERY, ADC_11db);
#endif
#if HAS_SOS_BUTTON
  pinMode(PIN_SOS, INPUT_PULLUP);
#endif
}

// Chamado a cada SAMPLE_PERIOD_MS.
void sampleFast(uint32_t now) {
#if HAS_MPU6050
  if (okMpu) {
    sensors_event_t a, g, t;
    mpu.getEvent(&a, &g, &t);
    float mag = sqrtf(a.acceleration.x * a.acceleration.x
                    + a.acceleration.y * a.acceleration.y
                    + a.acceleration.z * a.acceleration.z) / 9.80665f;
    if (stepDet.update(mag, now)) cadence.step(now);
    if (fallDet.update(mag, now)) {
      fallUntil = now + SIGNAL_PULSE_MS;
      Serial.println("QUEDA detectada pelo acelerometro.");
    }
  }
#endif

#if HAS_FSR
  float h = fsrHeel.toNewtons(readFsrCounts(PIN_FSR_HEEL));
  float m = fsrMid.toNewtons(readFsrCounts(PIN_FSR_MID));
  float t = fsrToe.toNewtons(readFsrCounts(PIN_FSR_TOE));
  float total = h + m + t;
  if (total > peakForceN) peakForceN = total;
  if (total > contact.thresholdN) {       // só acumula durante o apoio
    zoneSumH += h; zoneSumM += m; zoneSumT += t; zoneCount++;
  }
  if (contact.update(total, now)) {
    contactReportedMs = contact.lastContactMs;
    contactPending = true;
  }
#endif

#if HAS_INA219
  if (okIna) {
    float v = ina.getBusVoltage_V();
    float i = ina.getCurrent_mA();
    lastVolt = v;
    lastCurrentMa = i;
    lastPowerMw = v * i;
    energy.sample(lastPowerMw, now);
  }
#endif

#if HAS_SOS_BUTTON
  bool pressed = digitalRead(PIN_SOS) == LOW;
  if (pressed) {
    if (sosPressedSince == 0) sosPressedSince = now;
    if (!sosHeld && now - sosPressedSince >= SOS_HOLD_MS) {
      sosHeld = true;
      sosUntil = now + SIGNAL_PULSE_MS;
      Serial.println("SOS acionado pelo botao.");
    }
  } else {
    sosPressedSince = 0;
    sosHeld = false;
  }
#endif
}

#if HAS_MAX30102
// Lê o que houver no FIFO do MAX30102 e atualiza bpm/SpO2 quando o algoritmo
// tem uma janela válida (precisa de 4 s com o dedo/pele no sensor).
void pollPulseOx() {
  if (!okMax) return;
  maxSensor.check();
  while (maxSensor.available()) {
    uint32_t red = maxSensor.getRed();
    uint32_t ir = maxSensor.getIR();
    maxSensor.nextSample();
    fingerOn = ir > 50000;

    if (bufFill < SPO2_BUF) {
      redBuf[bufFill] = red; irBuf[bufFill] = ir; bufFill++;
    } else {
      memmove(redBuf, redBuf + 1, (SPO2_BUF - 1) * sizeof(uint32_t));
      memmove(irBuf, irBuf + 1, (SPO2_BUF - 1) * sizeof(uint32_t));
      redBuf[SPO2_BUF - 1] = red; irBuf[SPO2_BUF - 1] = ir;
      newSamples++;
    }
    if (bufFill == SPO2_BUF && (newSamples >= 25 || newSamples == 0)) {
      maxim_heart_rate_and_oxygen_saturation(irBuf, SPO2_BUF, redBuf, &spo2Val, &spo2Valid, &hrVal, &hrValid);
      newSamples = 1;
    }
  }
}
#endif

#if HAS_DS18B20
void pollTemperature(uint32_t now) {
  if (!okTemp) return;
  if (!tempWaiting && now - lastTempReq > 2000) {
    tempSensor.requestTemperatures();
    lastTempReq = now;
    tempWaiting = true;
  } else if (tempWaiting && now - lastTempReq > 800) {   // conversão de 12 bits leva ~750 ms
    float c = tempSensor.getTempCByIndex(0);
    tempWaiting = false;
    if (c > -50 && c < 125) lastTempC = c;               // -127 = sensor desconectado
  }
}
#endif

// ---------------------------------------------------------------------------
// Montagem do pacote (só inclui o que foi medido de verdade)
// ---------------------------------------------------------------------------
void sendSample(uint32_t now) {
  char buf[360];
  size_t n = 0;
  n += snprintf(buf + n, sizeof(buf) - n, "{\"v\":2,\"seq\":%lu", (unsigned long)seq++);

#if HAS_MAX30102
  if (okMax && fingerOn) {
    if (hrValid && hrVal > 30 && hrVal < 220)
      n += snprintf(buf + n, sizeof(buf) - n, ",\"bpm\":%ld", (long)hrVal);
    if (spo2Valid && spo2Val >= 70 && spo2Val <= 100)
      n += snprintf(buf + n, sizeof(buf) - n, ",\"spo2\":%ld", (long)spo2Val);
  }
#endif

#if HAS_MPU6050
  if (okMpu) {
    uint32_t delta = stepDet.steps - stepsSent;
    stepsSent = stepDet.steps;
    n += snprintf(buf + n, sizeof(buf) - n, ",\"steps\":%lu", (unsigned long)delta);
    float cad = cadence.perMinute(now);
    if (cad > 0) n += snprintf(buf + n, sizeof(buf) - n, ",\"cadence\":%.0f", cad);
    n += snprintf(buf + n, sizeof(buf) - n, ",\"fall\":%d", now < fallUntil ? 1 : 0);
  }
#endif

#if HAS_INA219
  if (okIna) {
    n += snprintf(buf + n, sizeof(buf) - n, ",\"energy\":%.3f", energy.takeDelta());
    if (!isnan(lastVolt)) n += snprintf(buf + n, sizeof(buf) - n, ",\"volt\":%.2f", lastVolt);
    if (!isnan(lastCurrentMa)) n += snprintf(buf + n, sizeof(buf) - n, ",\"curr\":%.1f", lastCurrentMa);
    if (!isnan(lastPowerMw)) n += snprintf(buf + n, sizeof(buf) - n, ",\"pwr\":%.1f", lastPowerMw);
  }
#endif

#if HAS_DS18B20
  if (okTemp && !isnan(lastTempC)) n += snprintf(buf + n, sizeof(buf) - n, ",\"temp\":%.1f", lastTempC);
#endif

#if HAS_BATTERY_ADC
  {
    int mv = analogReadMilliVolts(PIN_BATTERY) * 2;      // divisor 1:1
    n += snprintf(buf + n, sizeof(buf) - n, ",\"battery\":%d", sv::batteryPercentFromMv(mv));
  }
#endif

#if HAS_FSR
  {
    float h = 0, m = 0, t = 0;
    if (zoneCount > 0) { h = zoneSumH / zoneCount; m = zoneSumM / zoneCount; t = zoneSumT / zoneCount; }
    const char* side = (FOOT_SIDE == 'R') ? "pr" : "pl";
    n += snprintf(buf + n, sizeof(buf) - n, ",\"%s_h\":%.0f,\"%s_m\":%.0f,\"%s_t\":%.0f",
                  side, h, side, m, side, t);
    n += snprintf(buf + n, sizeof(buf) - n, ",\"impact\":%.0f", peakForceN);
    if (contactPending) {
      n += snprintf(buf + n, sizeof(buf) - n, ",\"contact\":%lu", (unsigned long)contactReportedMs);
      contactPending = false;
    }
    zoneSumH = zoneSumM = zoneSumT = 0; zoneCount = 0; peakForceN = 0;
  }
#endif

#if HAS_SOS_BUTTON
  n += snprintf(buf + n, sizeof(buf) - n, ",\"sos\":%d", now < sosUntil ? 1 : 0);
#endif

  if (packetsSinceConnect < 3) {
    n += snprintf(buf + n, sizeof(buf) - n, ",\"fw\":\"%s\"", FIRMWARE_VERSION);
  }
  packetsSinceConnect++;

  if (n + 2 >= sizeof(buf)) { Serial.println("Pacote estourou o buffer."); return; }
  buf[n++] = '}';
  buf[n] = 0;
  sendSealed(buf);
}

// ---------------------------------------------------------------------------
// Comandos
// ---------------------------------------------------------------------------
void sendAck(const sv::Command& c, bool ok, const char* msg) {
  if (!c.sealed) return;               // formato antigo não espera resposta
  char buf[160];
  snprintf(buf, sizeof(buf), "{\"v\":2,\"ack\":\"%s\",\"id\":%d,\"ok\":%d,\"msg\":\"%s\"}",
           c.name, c.id, ok ? 1 : 0, msg);
  sendSealed(buf);
}

void handleCommand() {
  char line[96];
  strncpy(line, cmdLine, sizeof(line) - 1);
  line[sizeof(line) - 1] = 0;
  cmdPending = false;

  sv::Command c = sv::parseCommand(line);
  if (!c.valid) {
    Serial.println("Comando invalido ou com checksum errado; ignorado.");
    return;
  }
  Serial.print("Comando: "); Serial.println(c.name);

  if (strcmp(c.name, "STATUS") == 0) {
    sendSample(millis());
    sendAck(c, true, "Pacote enviado.");
  } else if (strcmp(c.name, "CALIBRATE") == 0) {
    const char* msg = "";
    bool ok = calibrateFsr(&msg);
    sendAck(c, ok, msg);
  } else if (strcmp(c.name, "RESET") == 0) {
    stepDet.steps = 0; stepsSent = 0;
    sendAck(c, true, "Contadores zerados.");
  } else if (strcmp(c.name, "BLINK") == 0) {
    sendAck(c, true, "LED piscando.");
    blinkLed();
  } else {
    sendAck(c, false, "Comando desconhecido.");
  }
}

// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  initSensors();
  loadCalibration();

  BLEDevice::init("StepVolt Shoe");
  BLEDevice::setMTU(185);
  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());
  BLEService* service = bleServer->createService(SERVICE_UUID);

  txChar = service->createCharacteristic(CHAR_TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  txChar->addDescriptor(new BLE2902());
  BLECharacteristic* rx = service->createCharacteristic(
      CHAR_RX_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new RxCallbacks());

  service->start();
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.println("StepVolt pronto, anunciando.");
}

void loop() {
  uint32_t now = millis();

#if HAS_MAX30102
  pollPulseOx();
#endif
#if HAS_DS18B20
  pollTemperature(now);
#endif

  if (now - lastSampleMs >= SAMPLE_PERIOD_MS) {
    lastSampleMs = now;
    sampleFast(now);
  }

  if (cmdPending) handleCommand();

  if (connected && now - lastSend >= SEND_INTERVAL_MS) {
    lastSend = now;
    sendSample(now);
  }
}
