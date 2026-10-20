# StepVolt V3 — reformulação completa

Este é o app StepVolt reconstruído a partir da V2: mesma ideia (tênis com
gerador de energia + ESP32 por Bluetooth), interface nova organizada em
abas, integração BLE de verdade (com reconexão, diagnóstico e log), banco
local para funcionar offline, e uma aba de IA que lê os dados sem nunca dar
diagnóstico. O que já funcionava na V2 foi mantido — nada foi jogado fora,
só completado.

Leia também `RELATORIO_ANALISE_V2.md`: é o levantamento detalhado dos
problemas da versão anterior que motivou cada decisão tomada aqui.

> **Compilar:** use o **JDK 17** (o Gradle 8.4 não roda com Java 22+; erro "Unsupported class file major version").
>
> **Versão atual: V4+.** Leia primeiro o `V4_NOVIDADES.md` — ele diz o que foi
> adicionado, **como cada parte foi verificada** e o que ficou de fora. Em
> resumo: o app compila com `javac` contra o `android.jar` (com stubs das
> bibliotecas externas) e os testes passam, o backend foi testado contra um
> PostgreSQL de verdade, a lógica do firmware foi testada no PC — mas **nada
> disso passou pelo Gradle nem por um aparelho**. O primeiro passo na sua
> máquina é `./gradlew assembleDebug`; me mande qualquer erro.

## O que mudou, em uma imagem

```
V2                                   V3
──────────────────────────────────   ──────────────────────────────────
Sem barra de abas                    5 abas: Início / Saúde / Tênis / Análise / Você
BLE: conectado sim/não                BLE: máquina de estados, reconexão,
                                       RSSI, MTU, log de eventos
Coleta presa à tela do Dashboard      Coleta em serviço de primeiro plano
                                       (Telemetry + SessionService) — sai da
                                       tela e continua gravando
Sem banco local                       SQLite local: amostras, sessões, avisos
                                       — funciona sem servidor
Limites de alerta só em memória       Persistidos (Prefs) e realmente usados
                                       pelo AlertEngine
Sem alertas de verdade                Aba Avisos + notificações do Android
IA: não existia                       Aba Análise: leitura por regras no
                                       aparelho, ou por um provedor externo
                                       no servidor se você configurar uma chave
Paleta preto + verde chapado          Base índigo em camadas, uma cor fixa
                                       por grandeza, tipografia com escala
```

## Arquitetura

```
Tênis (ESP32) --BLE (Nordic UART)--> App Android (Java)
                                        │
                                        ├── SQLite local (LocalDb/Repo)
                                        │   funciona sem rede
                                        │
                                        └── HTTP/JSON + JWT --> API Node/Express --> PostgreSQL
                                                                     │
                                                                     └── /api/ai/insights
                                                                         (provedor externo opcional)
```

O app nunca fala com o Postgres diretamente. Ele também nunca *depende* do
servidor para as telas de dados: tudo que envolve o tênis (medidor ao vivo,
sessão, histórico, avisos, análise) lê primeiro do banco local; o servidor é
para sincronizar entre aparelhos e guardar o que já foi processado.

## Estrutura

```
StepVoltAndroid/
  app/                                - o app Android (Java)
    src/main/java/com/stepvolt/app/
      core/       Prefs, Telemetry (hub central de coleta)
      event/      EventCategory, EventSeverity, EventStatus, AppEvent, EventEngine  <- novo
      ble/        BleManager, BleState, Metric, SensorCatalog, SensorSample, BleEvent
      data/       LocalDb (SQLite), Repo, Point, SessionRecord, AlertRecord, Stats
      health/     AlertEngine (dispara avisos), Insights (leitura local)
      ai/         AiClient (fala com /api/ai/insights, cai na leitura local se falhar)
      ui/         Navigator, MainActivity, UiKit, views/ (GaugeView, SparklineView, BarsView)
      ui/fragments/
        HomeFragment, HealthFragment, DeviceFragment, SensorsFragment,
        AiFragment, AlertsFragment, SessionDetailsFragment   <- novos
        LoginFragment, RegisterFragment, ProfileFragment, SettingsFragment,
        HistoryFragment, GoalsFragment, AchievementsFragment, ...  <- da V2, completados
      util/       Fmt, Anim, Notifier
    src/main/res/ - cores, dimensões, temas, ícones e shapes novos (mesmos
                    nomes de recurso da V2, então as 16 telas antigas herdam
                    o visual novo sem precisar editar os layouts)
  backend/
    src/
      server.js
      db/
        schema.sql                    - tabelas da V2 (users, activities, goals, ...)
        migrations/002_v3.sql         - tabelas novas (devices, telemetry_samples, alerts)
        migrate.js                    - aplica schema + migração + seed, nessa ordem
      routes/
        auth.js, users.js, feed.js, notifications.js, goals.js,
        achievements.js, activities.js        <- da V2
        devices.js, telemetry.js, alerts.js, ai.js    <- novos
      middleware/auth.js
      services/gamification.js
  esp32-firmware/                    <- V4: substitui o exemplo antigo
    StepVoltFirmware/                - sketch Arduino (sensores reais, protocolo v2)
      StepVoltFirmware.ino, config.h, StepVoltLogic.h
    tests/                           - testes da lógica, rodam no PC (g++)
    README.md                        - ligações, calibração, protocolo
```

Na V4 o app ganhou também os pacotes `protocol/`, `gps/`, `energy/`, `plan/`,
`biomech/`, `safety/`, `export/`, `widget/` e 11 telas novas — a lista completa
está em `V4_NOVIDADES.md`.

## As 5 abas

| Aba | O que responde |
|---|---|
| **Início** | O tênis está ligado? O que ele está medindo agora? Estou gravando uma sessão? Como foi meu dia? |
| **Saúde** | Os mesmos números, por período (Hoje / 7 dias / 30 dias), com mínimo/média/máximo e tendência |
| **Tênis** | Tudo sobre a conexão: estado, sinal, pacotes, dispositivos por perto, comandos, log de eventos |
| **Análise** | Leitura em português do que os dados mostram, com aviso de que não é diagnóstico |
| **Você** | Perfil, metas, conquistas, feed, ajustes — tudo que já existia na V2 |

Sensores, Histórico, Avisos, Detalhe de sessão/atividade, Ajustes e as telas
de conta ficam empilhadas por cima da aba de onde foram abertas (o botão
Voltar do Android funciona do jeito que se espera).

## 1. Suba o PostgreSQL

```bash
sudo apt update
sudo apt install -y postgresql postgresql-contrib
sudo service postgresql start

sudo -u postgres psql -c "CREATE USER stepvolt WITH PASSWORD 'escolha-uma-senha';"
sudo -u postgres psql -c "CREATE DATABASE stepvolt OWNER stepvolt;"
```

(Se preferir usar o banco Neon que você já tem, pule isso e só use a
`DATABASE_URL` dele no `.env`.)

## 2. Configure e rode a API

```bash
cd StepVoltAndroid/backend
npm install
cp .env.example .env
# edite o .env: DATABASE_URL, JWT_SECRET
# opcional: AI_PROVIDER / AI_API_KEY / AI_MODEL, para a aba Análise usar um
# provedor externo em vez da leitura por regras local

npm run migrate     # aplica schema.sql + migrations/002_v3.sql + seed.sql
npm run dev          # ou: npm start
```

```bash
curl http://localhost:3000/api/health
# {"ok":true}
```

Descubra o IP local da máquina (o app precisa dele, não de `localhost`):

```bash
hostname -I | awk '{print $1}'
```

Se for usar o backend já publicado (Render + Neon), não precisa rodar nada
disso — só aponte o app para a URL de produção em Configurações.

## 3. Compile e instale o app

```bash
cd StepVoltAndroid
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Na primeira abertura: **Login → Criar conta**. Se der erro de conexão, vá em
**Você → Ajustes → Servidor** e cole `http://SEU_IP:3000/` (com a porta e a
barra no final) — não precisa recompilar para trocar o servidor.

## Protocolo Bluetooth (app ↔ ESP32)

Mesmo perfil **Nordic UART Service** de antes, com o pacote JSON agora
extensível:

| | UUID |
|---|---|
| Service | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX (ESP32 → celular, notify) | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX (celular → ESP32, write) | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |

```json
{"bpm":82,"spo2":97,"steps":3,"energy":1.8,"temp":33.2,"battery":74,"fw":"1.0.0"}
```

`steps` e `energy` são o delta desde o último pacote; as demais chaves são
valores instantâneos. **Qualquer chave numérica nova aparece sozinha** na
aba Sensores — não precisa alterar o app para dar suporte a um sensor
adicional, só somar a chave no firmware e (se quiser cor/faixa/ícone
próprios) uma linha em `ble/SensorCatalog.java`.

Comandos aceitos pelo firmware de exemplo, enviados pela tela **Tênis**:
`STATUS`, `RESET`, `CALIBRATE`, `BLINK`.

## O que é novo de verdade (não só visual)

- **Coleta sobrevive a sair da tela.** `Telemetry` (em `core/`) é o único
  listener do `BleManager` no nível do app; toda tela só se inscreve nele.
  Uma sessão iniciada no Início continua sendo gravada — com notificação de
  progresso — mesmo se você for para outra aba ou trocar de app.
- **BLE com estados de verdade.** `BleState` cobre ligando, procurando,
  conectando, descobrindo serviços, conectado, reconectando (com backoff),
  falhou, sem permissão, Bluetooth desligado. A aba Tênis mostra qual desses
  estados está ativo e por quê, junto com sinal, pacotes recebidos, MTU e um
  log dos últimos eventos.
- **Banco local (SQLite).** Toda amostra recebida é gravada; os gráficos de
  Início/Saúde/Análise/Histórico leem dali. Sessões não enviadas ficam numa
  fila e sobem sozinhas quando a rede volta (`Telemetry.syncPendingSessions`,
  chamado ao abrir o app e ao entrar em Ajustes → Enviar agora).
- **Limites de alerta persistidos e realmente aplicados.** O que era só
  UI na V2 agora vive em `Prefs` e é lido por `AlertEngine`, que exige três
  leituras seguidas fora do limite antes de avisar (evita alarme por um
  pico isolado) e não repete o mesmo aviso antes de três minutos.
- **Aba Análise com camada dupla.** Ela tenta `/api/ai/insights` no
  servidor; se você configurar `AI_API_KEY` no backend, ele chama um
  provedor externo (Anthropic ou OpenAI, configurável) e devolve cartões
  prontos. Sem chave — ou sem rede — o app monta a mesma leitura sozinho a
  partir das estatísticas locais (`health/Insights.java`). Em ambos os
  casos a tela deixa claro de onde veio o texto, e o aviso de "isto não é
  diagnóstico" vem antes de qualquer resultado.
- **Ajustes agora persiste de verdade.** Toggles e limites que a V2 perdia
  ao fechar o app agora usam `Prefs`; ganhou seções de metas diárias,
  conexão com o tênis e exportação de CSV.

## Event Engine (novo, depois da V3)

A spec de um sistema de wearable mais amplo (queda, SOS, GPS, Oracle, Black
Box, Energy OS...) pede um barramento central por onde todo evento do
sistema deveria passar, em vez de cada tela cuidar do seu próprio aviso.
Essa camada começou a entrar agora, só no aplicativo — o firmware real do
ESP32-C3 ainda não existe, isso é só o lado Android:

- `event/EventCategory.java`, `EventSeverity.java` (5 níveis, INFO a
  CRITICAL), `EventStatus.java`, `AppEvent.java` — o modelo.
- `event/EventEngine.java` — cooldown/dedup por tipo de evento (SAFETY,
  EMERGENCY e severidade CRITICAL nunca são filtrados: um segundo aviso
  real de queda ou SOS não pode ser suprimido), persistência em SQLite
  (tabela `events` nova, banco migra de v1 para v2 sem apagar nada) e
  listeners.
- **Aditivo, não substitutivo.** `AlertEngine` continua gerando
  `AlertRecord` exatamente como antes — é o que `Notifier`, `AlertsFragment`,
  `HomeFragment`, `DeviceFragment` e `SensorsFragment` leem hoje — e agora
  *também* publica o mesmo aviso como `AppEvent` (categoria
  BIOMETRIC/POWER). `Telemetry` passou a publicar CONNECTIVITY quando o
  tênis conecta/desconecta de verdade, coisa que antes só aparecia ao vivo
  na tela e não ficava registrada em lugar nenhum.
- CRITICAL fica reservado para SAFETY/EMERGENCY de verdade (queda
  confirmada, SOS) — um bpm alto vira HIGH, não CRITICAL.

O que ainda não existe: nada além da gravação consome o Event Engine — não
tem Timeline, e as telas citadas acima ainda leem só o caminho antigo.
Migrar as duas coisas para uma Timeline única fica para quando Fall
Detection/Safety Engine também estiverem publicando eventos; mexer nas 4
telas antes disso seria risco sem ganho nenhum agora.

Testado: `event/EventEngineLogicTest.java` (JUnit, roda com
`./gradlew testDebugUnitTest`, sem precisar de emulador) cobre a regra de
bypass de cooldown e a ordenação de severidade — 12 verificações, todas
passando. O resto (persistência de verdade, integração com o BLE) só dá
para validar rodando o app num aparelho.

## Herdado da V2 sem mudança de comportamento

Login/registro, perfil, feed, metas de passos/energia, conquistas,
notificações, busca, customização do tênis e o backend de gamificação
continuam como estavam — só herdam a paleta e os componentes novos porque
`colors.xml`/`themes.xml`/os drawables mantiveram os mesmos nomes de
recurso que os layouts antigos já usavam.

## Limitações conhecidas

- **E-mail de "esqueci a senha"**: o endpoint confirma que o e-mail existe
  mas não envia nada de verdade (precisaria de um provedor SMTP).
- **Metas de batimentos/oxigenação**: criam-se normalmente, mas o progresso
  não é atualizado sozinho como o de passos/energia.
- **Metas não resetam por período**: uma meta diária não zera sozinha no
  dia seguinte — precisaria de um job agendado no backend.
- **Sem upload de avatar.**
- **Distância (km)**: sempre 0 — o tênis não tem GPS; o campo existe só por
  compatibilidade com o design original.
- **Sem Android SDK neste ambiente**: nada foi compilado nem testado num
  dispositivo real. BLE também não passa pelo Waydroid na prática — use os
  estados vazios (que funcionam sem tênis) para testar o resto do app por
  lá, e um celular físico para validar a conexão de verdade.
- **Event Engine e AlertRecord ainda são dois caminhos paralelos**: por
  segurança (evitar quebrar as 4 telas que já liam o caminho antigo), o
  Event Engine por enquanto só grava — nada na UI foi migrado para ler dali
  ainda.

## Testando sem hardware

- **Backend**: `curl` nos endpoints, incluindo os novos —
  `POST /api/telemetry/batch`, `GET /api/telemetry/summary`,
  `POST/GET /api/devices`, `POST/GET /api/alerts`, `POST /api/ai/insights`.
- **App sem tênis**: todas as telas novas têm estado vazio pensado (Início
  esconde o bloco ao vivo, Saúde mostra "nada medido neste período" com
  atalho para o Tênis, Análise pede para conectar antes de gerar). O resto
  do app (perfil, feed, metas, ajustes) funciona normalmente sem BLE.

## Próximos passos sugeridos

1. Compilar (`./gradlew assembleDebug`) e me mandar qualquer erro de tipo —
   a validação aqui foi só sintática.
2. Rodar `./gradlew testDebugUnitTest` para conferir o teste novo do Event
   Engine (não precisa de emulador).
3. Rodar `npm run migrate` no backend para aplicar as tabelas novas.
4. Testar com o firmware de exemplo (ou o seu real) num celular físico —
   é onde a máquina de estados do BLE, a coleta em segundo plano e o banco
   migrando de v1 para v2 realmente se provam.
5. Se quiser, configurar `AI_API_KEY` no backend para a aba Análise usar um
   modelo de verdade em vez da leitura por regras.
6. Próximo candidato natural em cima do Event Engine: Fall Detection
   Engine (usa MPU6050 — ainda não existe no firmware de exemplo) ou Safety
   Engine (SOS). Os dois publicam na categoria SAFETY/EMERGENCY, que já
   ignora cooldown por decisão de projeto.
# FinalProjectbyArtur
