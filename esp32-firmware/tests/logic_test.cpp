// Testes da lógica do firmware, rodados no computador (sem ESP32).
#include "../StepVoltFirmware/StepVoltLogic.h"
#include <assert.h>
#include <string>
#include <vector>
#include <iostream>

static int passed = 0;
#define CHECK(cond) do { if (!(cond)) { std::cerr << "FALHOU linha " << __LINE__ << ": " #cond "\n"; return 1; } } while (0)
#define PASS(name) do { passed++; std::cout << "ok  " << name << "\n"; } while (0)

static const float PI_F = 3.14159265f;

int main() {
  // ---- protocolo: o mesmo vetor que o teste Java confere
  {
    const char* payload = "{\"v\":2,\"seq\":1,\"bpm\":82}";
    char out[96];
    size_t n = sv::seal(out, sizeof(out), payload);
    CHECK(n > 0);
    printf("      selado: %s", out);
    std::string s(out);
    CHECK(s.back() == '\n');
    CHECK(s.find("}*") != std::string::npos);
    // 0x5B é o valor que o app (PacketCodec.java) calcula para este mesmo payload.
    CHECK(s == std::string(payload) + "*5B\n");
    PASS("seal gera payload*XX\\n (mesmo valor do app)");
  }
  {
    char out[8];
    CHECK(sv::seal(out, sizeof(out), "{\"v\":2,\"bpm\":82}") == 0);   // não coube
    PASS("seal recusa buffer pequeno");
  }
  {
    // comando selado como o app monta: CMD:CALIBRATE:7*XX
    char body[] = "CMD:CALIBRATE:7";
    char line[40];
    snprintf(line, sizeof(line), "%s*%02X", body, sv::checksum(body, strlen(body)));
    sv::Command c = sv::parseCommand(line);
    CHECK(c.valid && c.sealed);
    CHECK(strcmp(c.name, "CALIBRATE") == 0);
    CHECK(c.id == 7);
    PASS("comando selado é aceito");
  }
  {
    char body[] = "CMD:SOS_TEST:xyz:12";
    char line[48];
    snprintf(line, sizeof(line), "%s*%02X", body, sv::checksum(body, strlen(body)));
    sv::Command c = sv::parseCommand(line);
    CHECK(c.valid);
    CHECK(strcmp(c.name, "SOS_TEST") == 0 && strcmp(c.arg, "xyz") == 0 && c.id == 12);
    PASS("comando com argumento");
  }
  {
    sv::Command c = sv::parseCommand("CMD:CALIBRATE:7*00");
    CHECK(!c.valid);                                    // checksum errado
    c = sv::parseCommand("CMD:CALIBRATE:7");
    CHECK(!c.valid);                                    // comando novo sem checksum
    c = sv::parseCommand("CMD:CALIBRATE*ZZ");
    CHECK(!c.valid);
    c = sv::parseCommand("");
    CHECK(!c.valid);
    PASS("comando corrompido é rejeitado");
  }
  {
    sv::Command c = sv::parseCommand("status\r\n");
    CHECK(c.valid && !c.sealed && strcmp(c.name, "STATUS") == 0);
    PASS("comando antigo (texto puro) continua valendo");
  }

  // ---- energia
  {
    sv::EnergyMeter m;
    m.sample(100, 0);                       // primeira leitura só marca o tempo
    for (uint32_t t = 100; t <= 36000; t += 100) m.sample(100, t);   // 100 mW por 36 s
    float mwh = m.takeDelta();
    CHECK(fabsf(mwh - 1.0f) < 0.01f);       // 100 mW * 36 s = 1 mWh
    CHECK(m.takeDelta() == 0);              // delta, não total
    PASS("energia integra potência em mWh");
  }
  {
    sv::EnergyMeter m;
    m.sample(500, 0);
    m.sample(500, 60000);                   // lacuna de 60 s: não conta
    CHECK(m.takeDelta() == 0);
    m.sample(-50, 60100);                   // reversa: não conta
    CHECK(m.takeDelta() == 0);
    PASS("energia ignora lacuna e corrente reversa");
  }

  // ---- passos
  {
    sv::StepDetector d;
    int counted = 0;
    // caminhada a 2 passos/s por 20 s, 50 Hz, amplitude 0,6 g
    for (int i = 0; i < 1000; i++) {
      uint32_t t = i * 20;
      float mag = 1.0f + 0.6f * fmaxf(0, sinf(2 * PI_F * 2.0f * (t / 1000.0f)));
      if (d.update(mag, t)) counted++;
    }
    CHECK(counted >= 36 && counted <= 44);   // esperado ~40
    PASS("contagem de passos na caminhada");
  }
  {
    sv::StepDetector d;
    int counted = 0;
    for (int i = 0; i < 1000; i++) {
      uint32_t t = i * 20;
      float mag = 1.0f + 0.05f * sinf(i * 0.7f);   // vibração fraca / parado
      if (d.update(mag, t)) counted++;
    }
    CHECK(counted == 0);
    PASS("parado não conta passos");
  }
  {
    sv::StepDetector d;
    int counted = 0;
    // 8 batidas fortes coladas em 100 ms contam como no máximo 1 passo
    for (int i = 0; i < 8; i++) {
      uint32_t t = 5000 + i * 12;
      if (d.update(1.9f, t)) counted++;
      if (d.update(1.0f, t + 6)) counted++;
    }
    CHECK(counted <= 1);
    PASS("intervalo mínimo evita contar a mesma pisada várias vezes");
  }

  // ---- cadência
  {
    sv::CadenceMeter c;
    for (int i = 0; i < 20; i++) c.step(i * 500);           // 2 passos/s = 120/min
    float cad = c.perMinute(9500);
    CHECK(cad > 110 && cad < 135);
    sv::CadenceMeter few;
    few.step(0); few.step(500);
    CHECK(few.perMinute(1000) == 0);                         // poucos passos: não chuta
    CHECK(c.perMinute(60000) == 0);                          // passos velhos saem da janela
    PASS("cadência por janela de 10 s");
  }

  // ---- queda
  auto feed = [](sv::FallDetector& f, std::vector<std::pair<uint32_t, float>> seq) {
    bool fired = false;
    for (auto& s : seq) if (f.update(s.second, s.first)) fired = true;
    return fired;
  };
  {
    sv::FallDetector f;
    std::vector<std::pair<uint32_t, float>> seq;
    uint32_t t = 0;
    for (int i = 0; i < 20; i++, t += 20) seq.push_back({t, 1.0f});          // andando
    for (int i = 0; i < 8; i++, t += 20) seq.push_back({t, 0.2f});           // queda livre 160 ms
    for (int i = 0; i < 3; i++, t += 20) seq.push_back({t, 3.5f});           // impacto
    for (int i = 0; i < 200; i++, t += 20) seq.push_back({t, 1.02f});        // parado 4 s
    CHECK(feed(f, seq));
    PASS("queda livre + impacto + imobilidade dispara");
  }
  {
    sv::FallDetector f;
    std::vector<std::pair<uint32_t, float>> seq;
    uint32_t t = 0;
    for (int i = 0; i < 8; i++, t += 20) seq.push_back({t, 0.2f});
    for (int i = 0; i < 3; i++, t += 20) seq.push_back({t, 3.5f});
    for (int i = 0; i < 200; i++, t += 20) seq.push_back({t, 1.0f + 0.5f * ((i % 12) < 3)});  // volta a andar
    CHECK(!feed(f, seq));
    PASS("pulo seguido de caminhada não dispara");
  }
  {
    sv::FallDetector f;
    std::vector<std::pair<uint32_t, float>> seq;
    uint32_t t = 0;
    for (int i = 0; i < 8; i++, t += 20) seq.push_back({t, 0.2f});           // queda livre
    for (int i = 0; i < 200; i++, t += 20) seq.push_back({t, 1.0f});         // sem impacto
    CHECK(!feed(f, seq));
    PASS("queda livre sem impacto não dispara");
  }
  {
    sv::FallDetector f;
    std::vector<std::pair<uint32_t, float>> seq;
    uint32_t t = 0;
    for (int i = 0; i < 3; i++, t += 20) seq.push_back({t, 3.5f});           // só impacto (passo forte)
    for (int i = 0; i < 300; i++, t += 20) seq.push_back({t, 1.0f});
    CHECK(!feed(f, seq));
    PASS("impacto sem queda livre não dispara");
  }
  {
    sv::FallDetector f;
    std::vector<std::pair<uint32_t, float>> seq;
    uint32_t t = 0;
    for (int i = 0; i < 2; i++, t += 20) seq.push_back({t, 0.2f});           // só 40 ms: ruído
    for (int i = 0; i < 3; i++, t += 20) seq.push_back({t, 3.5f});
    for (int i = 0; i < 200; i++, t += 20) seq.push_back({t, 1.0f});
    CHECK(!feed(f, seq));
    PASS("queda livre curta demais é ruído");
  }

  // ---- contato
  {
    sv::ContactTimer c;
    bool ended = false;
    for (uint32_t t = 0; t <= 600; t += 10) {
      float force = (t >= 100 && t < 400) ? 300.0f : 0.0f;                    // 300 ms no chão
      if (c.update(force, t)) ended = true;
    }
    CHECK(ended);
    CHECK(c.lastContactMs >= 280 && c.lastContactMs <= 320);
    PASS("tempo de contato");
  }

  // ---- FSR
  {
    sv::FsrScale s;
    s.offset = 100; s.newtonsPerCount = 0.5f;
    CHECK(s.toNewtons(90) == 0);                // abaixo da tara não vira negativo
    CHECK(fabsf(s.toNewtons(300) - 100.0f) < 1e-3);
    PASS("FSR: tara e escala");
  }

  // ---- bateria
  {
    CHECK(sv::batteryPercentFromMv(4200) == 100);
    CHECK(sv::batteryPercentFromMv(4300) == 100);
    CHECK(sv::batteryPercentFromMv(3270) == 0);
    CHECK(sv::batteryPercentFromMv(3000) == 0);
    int mid = sv::batteryPercentFromMv(3840);
    CHECK(mid >= 49 && mid <= 51);
    int prev = -1;
    for (int mv = 3200; mv <= 4250; mv += 10) {
      int p = sv::batteryPercentFromMv(mv);
      CHECK(p >= prev);                         // nunca decresce com a tensão
      prev = p;
    }
    PASS("bateria: curva monotônica e limites");
  }

  std::cout << "\n" << passed << " testes do firmware passaram\n";
  return 0;
}
