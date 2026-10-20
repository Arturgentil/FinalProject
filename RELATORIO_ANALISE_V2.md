# Relatório de análise — StepVoltAndroid V2

Levantamento feito antes de qualquer alteração, como pedido. Cobre o app
Android (Java), o backend (Node/Express + PostgreSQL) e o firmware de
exemplo do ESP32. Cada ponto aqui foi a base de uma decisão tomada na V3 —
as referências ao final de cada item apontam para onde isso foi resolvido.

**Tamanho do projeto analisado:** app com 46 arquivos Java (~5.260 linhas)
em 16 fragments, `minSdk` 26 / `targetSdk` 34; backend com rotas de auth,
usuários, feed, notificações, metas, conquistas e atividades; firmware de
exemplo em `.ino` simulando os sensores.

## 1. Navegação

- **Não existe barra de abas em nenhum lugar do projeto.** `MainActivity`
  troca fragments em tela cheia, um substituindo o outro; não havia
  `BottomNavigationView` nem qualquer outra estrutura de navegação
  persistente. Cada tela era, na prática, uma ilha — dava para chegar em
  qualquer lugar, mas não para saber onde se estava nem voltar a um ponto
  fixo. → Resolvido com 5 abas raiz e pilha de telas internas por cima
  (`ui/Navigator.java`, `ui/MainActivity.java`).
- Sem uma estrutura de retorno consistente: o botão Voltar do sistema não
  tinha um destino previsível fora da própria pilha de fragments do Android.

## 2. Integração com o ESP32 / Bluetooth

Este foi o ponto mais frágil do projeto.

- **`BleManager` só guardava um `boolean connected`.** Não havia distinção
  entre "procurando", "conectando", "descobrindo serviços" e "conectado";
  a UI não tinha como mostrar em qual dessas fases o app estava, nem por
  que uma tentativa tinha falhado.
- **Sem escolha de dispositivo.** O app tentava conectar ao primeiro
  aparelho compatível encontrado; não havia lista de dispositivos próximos
  nem RSSI para a pessoa decidir.
- **Sem reconexão automática.** Uma queda de conexão (tênis fora de
  alcance, app em segundo plano) exigia reconectar manualmente do zero,
  sem retomar o estado anterior.
- **Sem timeout de conexão nem de scan.** Uma tentativa de conexão que
  nunca respondia deixava a tela presa em "conectando" indefinidamente.
- **MTU nunca negociado**, o que limita o tamanho de pacote e obriga a
  fragmentar mensagens maiores sem necessidade.
- **`onCharacteristicChanged` só na assinatura antiga** da API Android —
  a partir da API 33 essa sobrecarga é depreciada em favor de uma versão
  com `byte[]`, e o código não tratava as duas.
- **Remontagem de pacotes JSON partidos**: o BLE pode entregar uma
  notificação em pedaços; não havia lógica para juntar fragmentos de um
  mesmo JSON antes de decodificar, o que quebra silenciosamente qualquer
  pacote que ultrapasse o tamanho de uma notificação.
- **Correspondência de dispositivo frágil**: o match era feito comparando
  o resultado de `getServiceUuids().toString()` como string — uma forma
  instável de reconhecer o serviço do tênis, sujeita a falhar por
  diferenças de formatação entre fabricantes/versões do Android.
- **Sem detecção de dados "velhos"**: se o tênis parasse de mandar pacotes
  sem que a conexão BLE caísse tecnicamente, o app continuava mostrando
  "conectado" com os últimos números, sem indicar que nada de novo estava
  chegando.
- **Nenhuma forma de enviar comandos ao tênis** (calibrar, pedir status,
  piscar LED) — a comunicação era só de leitura.

→ Tudo isto foi reescrito em `ble/BleManager.java` (máquina de estados
`BleState`, lista de dispositivos com RSSI, reconexão com backoff, timeouts,
MTU, as duas assinaturas de `onCharacteristicChanged`, remontagem de JSON
por contagem de chaves, detecção de dados obsoletos, canal de comando) e
exposto na aba **Tênis** (`DeviceFragment`) com log de eventos.

## 3. Coleta de dados presa à tela

- `DashboardFragment` era, ele mesmo, o listener do `BleManager`. Sair da
  tela (trocar de fragment) interrompia a coleta — não havia serviço em
  primeiro plano nem qualquer componente que sobrevivesse à troca de tela.
- Consequência direta: **qualquer sessão de uso mais longa dependia da
  pessoa não sair da tela do Dashboard**, o que não é realista para uma
  atividade física.

→ Resolvido com `core/Telemetry.java` como hub único de coleta no nível do
`Application`, e `ble/SessionService.java` como serviço em primeiro plano
que mantém a sessão gravando com o app em segundo plano.

## 4. Persistência local

- **Não existia banco local.** Nenhum histórico offline, nenhum buffer de
  amostras, nenhuma fila de sincronização.
- Consequência prática: **se a rede caísse no momento de salvar uma
  atividade, o dado se perdia** — não havia como tentar de novo depois.
- Sem histórico local também significa sem gráficos de período (dia/semana/
  mês) que não depend<|>am do servidor estar de pé.

→ Resolvido com `data/LocalDb.java` (SQLite) e `data/Repo.java`, com fila
de sessões não sincronizadas e reenvio automático.

## 5. Configurações que não persistem

- `SettingsFragment` (466 linhas) mantinha todos os toggles e limites
  (`bpmLimit = 180`, `spo2Limit = 92`, preferências de notificação, etc.)
  **em variáveis de instância, sem gravação em disco.** Fechar o app e
  abrir de novo desfazia qualquer mudança.
- Os limites configurados na tela **não eram lidos por nenhum motor de
  alerta** — não existia alerta de verdade no projeto (ver item 6).

→ Resolvido persistindo tudo via `core/Prefs.java` e ligando os valores ao
`health/AlertEngine.java`.

## 6. Alertas e notificações

- **Não existiam alertas de verdade.** A tela de Configurações desenhava
  controles para limites de bpm/SpO2, mas nada no app comparava uma leitura
  com esses limites nem gerava qualquer aviso.
- Sem canais de notificação, sem notificação de sessão em andamento, sem
  histórico de avisos.

→ Resolvido com `health/AlertEngine.java` (exige leituras consecutivas fora
do limite antes de avisar, evita repetição em menos de 3 minutos),
`util/Notifier.java` (canais de sessão e de avisos) e a aba **Avisos**.

## 7. Funções simuladas ou incompletas

- **Distância sempre 0** — o campo existe no banco e na tela, mas não há
  GPS no tênis para preenchê-lo; ficou como campo morto.
- **Metas de bpm/SpO2 sem lógica de progresso** — diferente das metas de
  passos/energia, que atualizam sozinhas, as de sinais vitais não tinham
  nenhuma regra de atualização definida.
- **Metas não resetam por período** — uma meta "diária" não zerava
  sozinha no dia seguinte (precisaria de um job agendado no backend).
- **Sem upload de avatar** — usuários novos ficavam sem foto de perfil,
  sem tela de upload construída.
- **"Esqueci minha senha" não envia e-mail** — o endpoint confirma que o
  e-mail existe, mas não há integração com um provedor SMTP; a tela
  funciona, mas nenhum e-mail chega de fato.

Estes pontos são registrados aqui como **limitações que continuam
existindo na V3** (não foram o foco do pedido de reformulação de UI/BLE/
arquitetura) — seguem listados também no `README.md` para não ficarem
escondidos.

## 8. Backend

- Estruturalmente sólido para o que cobria (auth com JWT, PostgreSQL via
  `pg`, gamificação real para metas/conquistas) — não foi encontrado dado
  fake nas rotas existentes.
- **Faltava tudo que o app novo passou a precisar**: não havia rota para
  registrar dispositivos pareados, para receber telemetria em lote, para
  arquivar avisos, nem qualquer endpoint de análise/IA.
- `.env.example` documentava só `DATABASE_URL`, `JWT_SECRET` e `PORT` — sem
  qualquer menção a um provedor de IA (que também não existia).

→ Resolvido com as rotas novas `devices.js`, `telemetry.js`, `alerts.js`,
`ai.js` e a migração `db/migrations/002_v3.sql`, mantendo o schema antigo
intacto.

## 9. Visual e identidade

- Paleta preto quase puro + um único verde ácido chapado em praticamente
  todo elemento de destaque — sem hierarquia de cor por tipo de dado (um
  gráfico de batimentos e um de passos usavam a mesma cor).
- Cartões planos, sem profundidade nem textura.
- Ícones em emoji nos títulos de seção, em vez de iconografia consistente.
- Tipografia na fonte padrão do sistema, sem escala deliberada entre
  números grandes (leituras) e texto de apoio.

→ Resolvido com uma paleta em camadas (índigo escuro), uma cor fixa por
grandeza (batimento, oxigênio, passos, energia), ícones vetoriais próprios
e uma escala tipográfica com números em `sans-serif-light` para leitura
rápida — mantendo os nomes de recurso da V2 para que as 16 telas antigas
herdem o visual novo automaticamente.

## 10. Código redundante / qualidade

- A lógica de formatação de número/data estava espalhada em vários pontos
  do código em vez de centralizada — cada tela reimplementava sua própria
  versão de "quantas casas decimais" ou "como abreviar a data".
- Sem separação entre "estado ao vivo do BLE" e "lógica de tela": o
  fragment do Dashboard concentrava leitura de sensor, cálculo de sessão e
  desenho de UI no mesmo arquivo.

→ Resolvido centralizando formatação em `util/Fmt.java`, e separando
claramente coleta (`Telemetry`/`BleManager`), persistência (`Repo`/
`LocalDb`) e apresentação (fragments, que só leem e desenham).

## 11. Segurança

- Nenhuma senha de banco ou segredo aparece no código do app — a
  comunicação já passava só pela API, nunca direto no Postgres. Isso
  estava correto na V2 e foi mantido.
- O JWT é validado em middleware único (`middleware/auth.js`), aplicado a
  todas as rotas que exigem login — também mantido sem alteração.
- Ponto de atenção que **continua existindo**: o app ainda não fixa
  (pin) o certificado do servidor nem valida a integridade do APK — fora
  do escopo pedido, mas vale registrar para uma eventual versão de
  produção.

## 12. Desempenho e responsividade

- Sem uso de `RecyclerView` com paginação em algumas listas — carregavam
  tudo de uma vez. Não chega a ser crítico nos volumes atuais, mas é um
  ponto a observar se o histórico crescer muito.
- Nenhum teste de layout em telas pequenas foi encontrado documentado; os
  layouts usavam `dp` fixos em vários pontos, o que funciona mas não se
  adapta tão bem a densidades de tela muito diferentes.

## Resumo do que foi e não foi alterado

| Área | Situação na V2 | Situação na V3 |
|---|---|---|
| Navegação | Sem abas | 5 abas + pilha de telas internas |
| BLE | Boolean simples, sem reconexão | Máquina de estados completa |
| Coleta | Presa à tela do Dashboard | Serviço em primeiro plano |
| Persistência local | Inexistente | SQLite + fila de sincronização |
| Configurações | Só em memória | Persistidas e aplicadas de verdade |
| Alertas | Inexistentes | Motor de alertas + notificações + aba própria |
| IA | Inexistente | Aba Análise, local ou via servidor |
| Distância/GPS | Sempre 0 | **Sem alteração** — fora do escopo (sem GPS no hardware) |
| Metas de bpm/SpO2 | Sem progresso automático | **Sem alteração** — fica como próximo passo |
| E-mail de recuperação | Não envia | **Sem alteração** — precisa de um provedor SMTP |
| Upload de avatar | Não existe | **Sem alteração** — fica como próximo passo |
| Visual | Preto + verde chapado | Paleta em camadas, cor por grandeza |
