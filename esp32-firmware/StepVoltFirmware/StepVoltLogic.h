// StepVoltLogic.h — lógica do firmware SEM nenhuma dependência do Arduino.
//
// Tudo aqui é cálculo puro (checksum, contagem de passos, detecção de queda,
// energia, bateria...). Fica separado do .ino de propósito: dá para compilar e
// testar no computador (veja ../tests/run_tests.sh) antes de gravar no ESP32.
#pragma once

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>

namespace sv {

// ---------------------------------------------------------------------------
// Protocolo v2 (mesmo contrato de PacketCodec.java no app)
//   pacote:  {"v":2,"seq":41,...}*3F\n     checksum = XOR de todos os bytes do JSON
//   comando: CMD:NOME[:ARG]:ID*XX
// ---------------------------------------------------------------------------

inline uint8_t checksum(const char* s, size_t n) {
  uint8_t x = 0;
  for (size_t i = 0; i < n; i++) x ^= (uint8_t)s[i];
  return x;
}

// Escreve "payload*XX\n" em out. Devolve o tamanho escrito, ou 0 se não coube.
inline size_t seal(char* out, size_t cap, const char* payload) {
  size_t n = strlen(payload);
  if (n + 5 > cap) return 0;
  memcpy(out, payload, n);
  snprintf(out + n, cap - n, "*%02X\n", checksum(payload, n));
  return n + 4;
}

struct Command {
  char name[20];
  char arg[20];
  int id;
  bool sealed;   // veio com checksum
  bool valid;    // checksum conferiu (ou é o formato antigo, sem checksum)
};

// Aceita "CMD:NOME[:ARG]:ID*XX" e também o formato antigo, texto puro ("STATUS").
inline Command parseCommand(const char* line) {
  Command c;
  memset(&c, 0, sizeof(c));
  c.id = -1;

  char buf[80];
  size_t len = strlen(line);
  while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r' || line[len - 1] == ' ')) len--;
  if (len == 0 || len >= sizeof(buf)) return c;
  memcpy(buf, line, len);
  buf[len] = 0;

  char* star = strrchr(buf, '*');
  if (star && strlen(star) == 3) {
    c.sealed = true;
    char* end = nullptr;
    long expected = strtol(star + 1, &end, 16);
    if (*end != 0) return c;
    if ((uint8_t)expected != checksum(buf, (size_t)(star - buf))) return c;
    *star = 0;
  }

  if (strncmp(buf, "CMD:", 4) == 0) {
    if (!c.sealed) return c;                       // comando novo sem checksum não vale
    char* p = buf + 4;
    char* parts[3] = {nullptr, nullptr, nullptr};
    int n = 0;
    parts[n++] = p;
    for (char* q = p; *q && n < 3; q++) {
      if (*q == ':') { *q = 0; parts[n++] = q + 1; }
    }
    if (n < 2) return c;                            // precisa de NOME e ID
    strncpy(c.name, parts[0], sizeof(c.name) - 1);
    if (n == 3) {
      strncpy(c.arg, parts[1], sizeof(c.arg) - 1);
      c.id = atoi(parts[2]);
    } else {
      c.id = atoi(parts[1]);
    }
    for (char* q = c.name; *q; q++) if (*q >= 'a' && *q <= 'z') *q -= 32;
    c.valid = c.name[0] != 0 && c.id >= 0;
    return c;
  }

  // Formato antigo (firmware/app v1): só o nome do comando.
  if (!c.sealed) {
    strncpy(c.name, buf, sizeof(c.name) - 1);
    for (char* q = c.name; *q; q++) if (*q >= 'a' && *q <= 'z') *q -= 32;
    c.valid = c.name[0] != 0;
  }
  return c;
}

// ---------------------------------------------------------------------------
// Energia: integra a potência medida (mW) no tempo e devolve mWh.
// ---------------------------------------------------------------------------
struct EnergyMeter {
  double pendingMwh = 0;
  uint32_t lastMs = 0;
  bool started = false;

  void sample(float powerMw, uint32_t nowMs) {
    if (!started) { started = true; lastMs = nowMs; return; }
    uint32_t dt = nowMs - lastMs;
    lastMs = nowMs;
    if (powerMw < 0) powerMw = 0;               // corrente reversa não é energia gerada
    if (dt > 5000) return;                       // lacuna grande: não inventa energia no buraco
    pendingMwh += (double)powerMw * dt / 3600000.0;
  }

  // Devolve o que acumulou desde a última chamada (o app espera DELTA, não total).
  float takeDelta() {
    float v = (float)pendingMwh;
    pendingMwh = 0;
    return v;
  }
};

// ---------------------------------------------------------------------------
// Passos a partir da magnitude da aceleração (em g), ~50 Hz.
// ---------------------------------------------------------------------------
struct StepDetector {
  float baseline = 1.0f;       // média lenta (gravidade)
  uint32_t lastStepMs = 0;
  bool armed = true;
  uint32_t steps = 0;

  float threshold = 0.30f;     // g acima da média para contar um passo
  uint32_t minIntervalMs = 280;

  // Devolve true quando um passo acabou de ser detectado.
  bool update(float magG, uint32_t nowMs) {
    baseline += 0.02f * (magG - baseline);
    float dyn = magG - baseline;
    if (dyn < threshold * 0.4f) armed = true;           // histerese: precisa baixar antes do próximo
    if (armed && dyn > threshold && (nowMs - lastStepMs) > minIntervalMs) {
      armed = false;
      lastStepMs = nowMs;
      steps++;
      return true;
    }
    return false;
  }
};

// Cadência (passos/min) pela janela dos últimos 10 s.
struct CadenceMeter {
  static const int N = 24;
  uint32_t times[N];
  int count = 0;
  int head = 0;

  void step(uint32_t nowMs) {
    times[head] = nowMs;
    head = (head + 1) % N;
    if (count < N) count++;
  }

  float perMinute(uint32_t nowMs) const {
    int inWindow = 0;
    uint32_t oldest = nowMs;
    for (int i = 0; i < count; i++) {
      uint32_t age = nowMs - times[i];
      if (age <= 10000) { inWindow++; if (times[i] < oldest) oldest = times[i]; }
    }
    if (inWindow < 3) return 0;
    uint32_t span = nowMs - oldest;
    if (span < 2000) return 0;
    return inWindow * 60000.0f / span;
  }
};

// ---------------------------------------------------------------------------
// Detecção de queda no tênis: queda livre -> impacto -> imobilidade.
// Pular e voltar a andar NÃO dispara: precisa ficar parado depois.
// ---------------------------------------------------------------------------
struct FallDetector {
  enum State { IDLE, FREEFALL, WAIT_IMPACT, WAIT_STILL };
  State state = IDLE;
  uint32_t stateSince = 0;
  uint32_t stillSince = 0;
  bool stillRunning = false;

  float freefallG = 0.45f;
  uint32_t freefallMinMs = 60;
  float impactG = 2.5f;
  uint32_t impactWindowMs = 800;
  float stillBandG = 0.15f;
  uint32_t stillNeededMs = 2500;
  uint32_t stillWindowMs = 6000;

  // true no instante em que a queda é confirmada.
  bool update(float magG, uint32_t nowMs) {
    switch (state) {
      case IDLE:
        if (magG < freefallG) { state = FREEFALL; stateSince = nowMs; }
        break;
      case FREEFALL:
        if (magG >= freefallG) {
          if (nowMs - stateSince >= freefallMinMs) { state = WAIT_IMPACT; stateSince = nowMs; }
          else state = IDLE;
        }
        break;
      case WAIT_IMPACT:
        if (magG >= impactG) { state = WAIT_STILL; stateSince = nowMs; stillRunning = false; }
        else if (nowMs - stateSince > impactWindowMs) state = IDLE;
        break;
      case WAIT_STILL: {
        bool still = fabsf(magG - 1.0f) < stillBandG;
        if (still) {
          if (!stillRunning) { stillRunning = true; stillSince = nowMs; }
          if (nowMs - stillSince >= stillNeededMs) { state = IDLE; stillRunning = false; return true; }
        } else {
          stillRunning = false;
        }
        if (nowMs - stateSince > stillWindowMs) state = IDLE;
        break;
      }
    }
    return false;
  }
};

// ---------------------------------------------------------------------------
// Tempo de contato do pé com o chão (ms) a partir da força total (N).
// ---------------------------------------------------------------------------
struct ContactTimer {
  float thresholdN = 25.0f;
  bool inContact = false;
  uint32_t since = 0;
  uint32_t lastContactMs = 0;

  // Devolve true quando um contato terminou; o valor fica em lastContactMs.
  bool update(float forceN, uint32_t nowMs) {
    if (!inContact && forceN > thresholdN) { inContact = true; since = nowMs; }
    else if (inContact && forceN < thresholdN * 0.6f) {
      inContact = false;
      uint32_t d = nowMs - since;
      if (d >= 60 && d <= 1500) { lastContactMs = d; return true; }
    }
    return false;
  }
};

// ---------------------------------------------------------------------------
// FSR (sensor de pressão): contagem do ADC -> Newtons, com zero e escala.
// O FSR é não linear; isto é uma aproximação linear que PRECISA ser
// calibrada com um peso conhecido (veja o README do firmware).
// ---------------------------------------------------------------------------
struct FsrScale {
  float offset = 0;           // contagem com o pé no ar (tara)
  float newtonsPerCount = 0.35f;

  float toNewtons(float counts) const {
    float d = counts - offset;
    if (d < 0) d = 0;
    return d * newtonsPerCount;
  }
};

// ---------------------------------------------------------------------------
// Bateria Li-ion: tensão (mV) -> %, por interpolação numa curva típica.
// ---------------------------------------------------------------------------
inline int batteryPercentFromMv(int mv) {
  static const int V[]   = {3270, 3610, 3690, 3710, 3730, 3750, 3770, 3790, 3800, 3820,
                            3840, 3850, 3870, 3910, 3950, 3980, 4020, 4080, 4110, 4150, 4200};
  static const int P[]   = {0,    5,    10,   15,   20,   25,   30,   35,   40,   45,
                            50,   55,   60,   65,   70,   75,   80,   85,   90,   95,   100};
  const int n = sizeof(V) / sizeof(V[0]);
  if (mv <= V[0]) return 0;
  if (mv >= V[n - 1]) return 100;
  for (int i = 1; i < n; i++) {
    if (mv <= V[i]) {
      float f = (float)(mv - V[i - 1]) / (V[i] - V[i - 1]);
      return (int)(P[i - 1] + f * (P[i] - P[i - 1]) + 0.5f);
    }
  }
  return 100;
}

}  // namespace sv
