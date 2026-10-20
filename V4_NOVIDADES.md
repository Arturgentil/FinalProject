# StepVolt V4 — o que mudou, o que foi testado e o que ficou de fora

Este documento é o relatório honesto da V4. Primeiro o que existe agora, depois
**como cada coisa foi verificada**, e por fim o que **não** foi feito ou só foi
feito em parte.

## V7 — repaginação, ícone novo e cadastro corrigido (esta rodada)

**Antes de compilar:** o Gradle 8.4 do projeto **não roda com Java 22 ou mais novo**
("Unsupported class file major version 69" = Java 25). Use o **JDK 17**:
`sudo apt install openjdk-17-jdk`, depois
`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew --stop && ./gradlew assembleDebug`.

**Criar conta dava erro — causas encontradas (várias eram minhas)**

| Causa | Correção |
|---|---|
| O servidor novo precisa de tabelas/colunas novas e **ninguém rodava as migrações** ao publicar: sem elas o cadastro e o login davam erro 500 | O servidor agora **atualiza o banco sozinho ao iniciar** (registra o que já aplicou; seguro rodar de novo; `AUTO_MIGRATE=0` desliga). `/api/health` mostra a versão do banco |
| Quando o 500 acontecia, a **conta já tinha sido criada** e a segunda tentativa dizia "e-mail já cadastrado" | Conta e token agora são gravados numa transação única: se algo falha, nada fica gravado (há teste que simula a falha) |
| O app esperava 10–15 s, mas o Render gratuito leva até ~1 min para acordar: o primeiro cadastro do dia **sempre "falhava"** com a mensagem errada ("confira o endereço do servidor") | Tempos de 30/70 s; o app **acorda o servidor ao abrir**; mensagens separadas para servidor acordando, sem internet, endereço errado e certificado |
| Eu tinha feito o servidor **se recusar a iniciar** com `JWT_SECRET` curto | Agora só avisa no log |
| A tela dizia "mínimo 6 caracteres" e o código exigia 8 | Texto, medidor de força e regra batem (8) |
| Data de nascimento digitada à mão ("17/05/1990") era aceita no cadastro e depois quebrava ao editar o perfil | Calendário; o servidor valida o formato também no cadastro |
| Erro de duplicidade genérico | Diz se é o **e-mail** ou o **usuário**; se a tentativa anterior estourou o tempo, avisa que a conta pode já existir |
| O login apagava espaços do começo/fim da senha | Senha usada exatamente como digitada |

**Repaginação "Aurora"**
- Fundo com degradê e brilhos suaves; cartões de vidro com borda fina e cantos de 22 dp;
  botão principal em degradê verde→ciano; chips, campos, barra de navegação (cantos
  arredondados em cima) e widget no mesmo estilo. Tudo definido por *tokens* de cor
  (escuro e claro), então o tema claro acompanha.
- Telas de **Entrar** e **Criar conta** refeitas: marca no topo, cartões, olho em
  ambas as senhas, medidor de força, indicador "senhas iguais", campos de peso/altura
  lado a lado, erro em caixa destacada.
- Abertura sem o emoji ⚡ (que mudava de aparência em cada celular): usa a marca nova.
- Atalhos ganham ladrilho colorido e **onda ao toque** (antes pareciam "mortos").

**Ícone novo:** tênis branco com sola em degradê verde→ciano e um raio, sobre azul-marinho
com brilho. Inclui versão monocromática (ícones temáticos do Android 13+) e um PNG de 512 px
para a Play Store (`app/src/main/ic_launcher-playstore.png`; prévia em `icone/`).
Foi desenhado e **conferido em imagem** nos formatos quadrado, arredondado e redondo e
dentro da área segura; no aparelho real pode haver pequenas diferenças.

**Como foi verificado nesta rodada:** além dos testes, os recursos agora passam por um
**aapt2 de verdade** (a ferramenta do Gradle que validou os recursos), com stubs só para os
atributos das bibliotecas — é exatamente o tipo de checagem que teria pegado o erro do
`bg_widget`. Testes: app 95, backend 27 (PostgreSQL real), firmware 20 — todos passam.

**Limites (franqueza):** não vi as telas rodando; a repaginação foi feita por
tokens e componentes e **precisa do seu olho no celular** (me diga o que ainda está
feio, por tela). Telas só varridas (Tênis, Personalizar tênis, Análise, Histórico, Saúde,
Metas, Alertas, Conquistas, Sensores) herdam o novo visual, mas não foram redesenhadas uma a uma.

## V6 — revisão geral

Auditoria feita com varreduras automáticas (botões sem ação, rotas sem caminho
até elas, referências de recursos, APIs que o Android não tem) e leitura das
telas Ajustes, Perfil, Editar perfil, Feed, Busca e Notificações.

**O que estava errado e foi corrigido**

| Problema | Correção |
|---|---|
| **Feed, Busca e Notificações não tinham nenhum botão que levasse até elas** (a parte social inteira estava inalcançável) | Atalho "Comunidade" no Início; o Feed ganhou botão de voltar, busca e sino com contador de não lidas |
| **Não dava para trocar a foto de perfil** (a tela dizia "toque para alterar", mas o toque não fazia nada) | Foto tocável no Perfil e em Editar perfil: tirar foto, escolher da galeria ou remover. Gira conforme o EXIF, recorta quadrado, reduz a 512 px (~50 KB), envia e guarda no banco |
| Pessoa sem foto aparecia **com a foto de outra pessoa** nas listas (a imagem reaproveitada não era limpa) | Agora sempre limpa e mostra as iniciais coloridas (ou uma silhueta) |
| Avatares ora redondos, ora quadrados | Todos redondos |
| 7 interruptores dos Ajustes **salvavam a escolha mas nada usava** | Meta, conquistas e atividade social: o servidor só cria o aviso se estiver ligado. Perfil público, comentários e compartilhar automático: o servidor aplica. "Envio automático" e "Reconectar sozinho": o app obedece. "Dicas e novidades": removido (não existe nada que as envie) |
| Aviso de meta diária nunca existiu | Notificação local quando bate a meta de passos/energia (uma por dia) |
| Seção "Ajuda" dos Ajustes era texto sem toque | Abre Ajuda e diagnóstico; "Rever boas-vindas"; "Contato com suporte" removido (não há endereço real) |
| Sair da conta sem confirmar e sem encerrar a sessão no servidor | Pede confirmação e revoga o token |
| Excluir conta sem senha | Pede a senha (servidor confere) |
| Trocar a senha mantinha as outras sessões e deixava o app com token morto | Servidor encerra as outras sessões e devolve um par novo; o app o guarda |
| Editar perfil: data digitada à mão, sem validação, perdia tudo ao voltar | Calendário, validação (app e servidor), contador da bio, aviso "descartar alterações?" |
| Setas de voltar e vários ícones eram do Android antigo | Ícones próprios, no mesmo traço do resto |
| 2 layouts mortos (`fragment_dashboard`, `item_feed_post_preview`) | Removidos |

**Defeitos do servidor achados pelos testes**

- Um aparelho antigo tentando renovar o token depois da troca de senha **derrubava
  a sessão nova** (a detecção de roubo era sensível demais). Agora só o reuso de
  um token já *trocado* aciona a proteção.
- Dois pedidos de confirmação de e-mail ao mesmo tempo colidiam (erro 500).
- Perfil sem nenhuma validação; comentário sem limite de tamanho; comentário em
  post inexistente dava erro 500; perfil privado e política de comentário
  não eram respeitados. Tudo corrigido.

**Para publicar o servidor:** rode as migrações (`node src/db/migrate.js`; o
`004_v5.sql` é novo) e faça o deploy no Render. O app novo precisa do servidor
novo para trocar foto e salvar privacidade.

**Ainda não feito (dito com franqueza)**

- **Nada disto foi aberto em aparelho.** "Bonito" aqui significa consistência
  (avatares, ícones, selos, iniciais, estados vazios, mensagens); não há captura
  de tela e o espaçamento real só aparece no celular.
- **Perfil de outra pessoa:** tocar numa pessoa no Feed/Busca ainda não abre o
  perfil dela (falta a rota pública no servidor). É a próxima lacuna social.
- Feed: não dá para apagar o próprio post nem um comentário.
- Telas **só varridas** (botões mortos e ícones), sem leitura detalhada:
  Tênis, Personalizar tênis, Análise (IA), Histórico, Saúde, Metas, Alertas,
  Conquistas, Sensores. Personalizar tênis e Análise ainda não bloqueiam toque duplo.
- A foto de perfil é pública por endereço (qualquer um com o número do usuário
  a vê), como em quase todo app com avatar; o feed precisa disso para carregar
  sem login.
- O envio de foto (câmera/galeria) usa as telas do sistema e **não foi testado em aparelho**.
- Tema Claro continua experimental; idiomas e unidades continuam fora.

Testes: app 82 (passam), backend 22 (passam, PostgreSQL real), firmware 20 (passam).

## Histórico de correções

- **v5.1:** o primeiro `assembleDebug` real acusou `bg_widget.xml` apontando para
  uma cor que não existe (`bg_card`). Corrigido. Passei a conferir também toda
  referência `@color/@drawable/@string/...` dentro dos XMLs e a recompilar com
  `--release 8` (para pegar APIs de Java que o Android não tem) — as duas
  checagens passam. Outros erros só visíveis no Gradle ainda podem aparecer.

## Como foi verificado (leia isto antes de rodar)

| Parte | Verificação feita aqui | Limite |
|---|---|---|
| App Android (Java) | Compilado com `javac` contra o `android.jar` (API 34) — **0 erros**. As bibliotecas externas (androidx, Retrofit, OkHttp, Glide, Gson) foram trocadas por stubs escritos por mim, só com as assinaturas que o código usa. | **Não passou pelo Gradle**: sem Android SDK, sem AGP, sem aparelho. Os stubs podem divergir da assinatura real em algum detalhe. Rode `./gradlew assembleDebug` primeiro. |
| Testes do app | 65 testes JUnit (alerta, insights, protocolo, GPS, energia, plano, queda, pressão, desgaste do tênis, Event Engine) rodando na JVM — **65 passam**. | Rodados com um executor mínimo no lugar do JUnit (sem acesso ao Maven). São JUnit 4 padrão e devem rodar com `./gradlew test`. |
| Banco local (SQLite) | O SQL da migração v2→v3 (colunas novas, tabelas novas, recuperação do resumo por dia) foi executado em SQLite real sobre um banco v2 com dados. | Não rodou dentro do Android. |
| Backend (Node/Express) | 15 testes de integração contra **PostgreSQL 16 de verdade**: cadastro, login, limite de tentativas, renovação e reuso de token, logout, paginação, sincronização incremental, desafios, ranking, confirmação de e-mail, JSON inválido. **15 passam.** Migrações aplicadas duas vezes (idempotentes). | Envio real de e-mail e login com Google **não** foram testados (precisam de SMTP e de um cliente OAuth). |
| Firmware | A lógica (`StepVoltLogic.h`) tem 20 testes em C++ rodando no PC — **20 passam**. O checksum foi conferido contra o do app (mesmo payload → `*5B` nos dois). | O `.ino` (leitura dos sensores e Bluetooth) **não foi compilado nem gravado em ESP32**. |
| Telas | Nenhuma foi aberta em aparelho ou emulador. | Layout, espaçamentos e fluxos podem precisar de ajustes visuais. |

## App — o que foi adicionado

**Telas novas (11):** Boas-vindas, Treino ao vivo, Resumo do treino, Energia,
Biomecânica do pé, Planos e metas, Desafios e ranking, Meus tênis, Segurança e
SOS, Dados e conta, Ajuda e diagnóstico. Atalhos na tela Início.

- **GPS e treino ao vivo:** tipo de treino (caminhada, corrida, trilha, outro),
  tempo, distância, ritmo, passos, energia, batimentos, pausar/retomar. A
  distância descarta pontos de baixa precisão (> 30 m), saltos impossíveis e
  jitter parado. A rota é desenhada como linha **sem mapa de fundo** (mapa com
  ruas exige chave de serviço e internet). Exporta GPX.
- **Energia:** 7/30/90 dias, comparação com o período anterior, equivalências
  (celular, smartwatch, lâmpada LED), eficiência em mWh por 1.000 passos, e por
  tipo de treino. O resumo por dia sobrevive à limpeza das medições antigas.
- **Biomecânica:** mapa de pressão por zona, padrão de carga, índice de
  simetria, cadência, tempo de contato. Não diagnostica pisada nem lesão.
- **Planos:** meta semanal a partir da média das últimas semanas (degrau de
  ~10%; recua se a semana passada foi fraca). Fica fixa durante a semana.
- **Desafios e ranking:** desafios com prazo (passos, energia, distância) e
  ranking entre você e quem você segue. O servidor calcula o progresso.
- **Meus tênis:** quilometragem do GPS, limite de troca ajustável, bateria
  (descarga acumulada → ciclos equivalentes), firmware, calibrar e piscar LED
  com **confirmação do tênis** (ou aviso de que não respondeu).
- **Segurança:** contatos, detecção de queda (no tênis ou no app) com
  contagem de 15/30/60 s para cancelar, botão SOS do tênis, envio por SMS com
  link da localização. Com a permissão `SEND_SMS` envia sozinho; sem ela abre as
  mensagens já preenchidas.
- **Dados:** CSV de treinos, PDF do período, PDF de um treino, GPX, medições
  brutas, backup/restauração em JSON (restaurar só soma, nunca apaga).
- **Widget** de passos/energia do dia e **botão Parar** na notificação do treino.
- **Tema** Escuro (padrão) / Claro (experimental) / Seguir o sistema.
- **Protocolo BLE v2:** JSON + checksum, descarte de pacote corrompido, contagem
  de perdidos, comandos com confirmação e nova tentativa. Firmware v1 continua
  funcionando.

## Backend — o que foi adicionado

Token de acesso curto + token de renovação com **rotação e detecção de reuso**;
confirmação de e-mail; rota de login com Google; paginação e `?since=` nas
atividades; validação de entrada; limite de requisições (geral e por rota);
cabeçalhos de segurança; erros de JSON tratados; desafios e ranking; migração
`003_v4.sql`. Contas antigas continuam funcionando e contam como confirmadas.

## Firmware 2.0

Lê os sensores de verdade (MAX30102, MPU6050, INA219, DS18B20, 3 FSR por pé,
bateria, botão SOS). Sensor ausente → grandeza não enviada; **nada é simulado**.
Veja `esp32-firmware/README.md` (ligações e calibração).

## Correções feitas no caminho

- A equivalência de energia do cartão de Análise usava `energia/40` como
  "% de um celular" (errado por uma ordem de grandeza); agora usa 15,4 Wh.
- O alerta de bateria baixa e as regras do `AlertEngine` foram separados do
  Android para poderem ser testados.
- As opções "Unidades" e "Idioma" dos Ajustes salvavam a escolha mas nada as
  usava; foram removidas em vez de continuarem fingindo funcionar.
- Senha mínima subiu de 6 para 8 caracteres (app e servidor).
- `allowBackup` passou para `false` (o app guarda token de sessão).

## O que NÃO foi feito, ou foi feito só em parte

1. **Idiomas (English/Español): não feito.** Os textos do app estão no código
   em português; extrair tudo para `strings.xml` é um trabalho à parte.
2. **Unidades (milhas/lb): não feito.**
3. **Login com Google: só o servidor.** A rota está pronta e valida o token com
   o Google, mas o **botão no app não existe** (precisa do SDK do Google e de um
   cliente OAuth criado por você).
4. **Refatoração de `SettingsFragment` e `HomeFragment`: não feita.** Para não
   crescerem, as telas novas ficaram em arquivos próprios; mas a Home ganhou
   mais atalhos e continua grande.
5. **Tema Claro é experimental:** várias cores de destaque ainda estão fixas no
   código (pensadas para fundo escuro); alguns textos podem ficar com pouco
   contraste.
6. **Mapa sem ruas**, só a linha da rota.
7. **Queda:** os limites (2,5 g, tempos) são pontos de partida e **precisam ser
   testados no seu hardware**. Pode haver falso alarme (por isso a contagem
   para cancelar) e queda não detectada.
8. **SMS:** a permissão `SEND_SMS` é restrita pelo Google Play; para publicar
   na loja, tire-a do manifesto (o app volta a abrir as mensagens preenchidas).
9. **Pressão em Newtons é aproximada** até você calibrar o FSR.
10. **Biomecânica ao vivo mostra um pé por vez** (um tênis conectado por vez).
    A média de 7 dias junta os dois.
11. **Recuperação de senha por e-mail** continua só confirmando que a conta existe.
12. **Limitador de requisições** é em memória (vale para uma instância).
