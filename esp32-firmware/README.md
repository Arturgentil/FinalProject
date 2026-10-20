# Firmware StepVolt 2.0 (ESP32)

Firmware que lê os sensores **de verdade** e fala o protocolo v2 com o app
(JSON + checksum, comandos com confirmação). Substitui o exemplo anterior, que
gerava números aleatórios.

> **O que foi e o que não foi verificado.** A lógica (`StepVoltLogic.h`:
> checksum, comandos, passos, cadência, queda, energia, contato, bateria) tem
> 20 testes que rodam no computador (`tests/run_tests.sh`) e o checksum foi
> conferido contra o do app. O arquivo `.ino` (leitura dos sensores e Bluetooth)
> **não foi compilado nem gravado em um ESP32** — este ambiente não tem o
> Arduino. Compile na sua máquina e, se algum nome de biblioteca mudar na sua
> versão, é ali que o ajuste será.

## O que enviar, e de onde vem cada número

| Chave | Sensor | Observação |
|---|---|---|
| `bpm`, `spo2` | MAX30102 | só quando há pele/dedo no sensor e o algoritmo tem janela válida (~4 s) |
| `steps`, `cadence`, `fall` | MPU6050 | passos por pico de aceleração; queda = queda livre → impacto → imobilidade |
| `energy`, `volt`, `curr`, `pwr` | INA219 | energia = potência medida integrada no tempo (mWh) |
| `temp` | DS18B20 | |
| `pl_h/m/t` ou `pr_h/m/t`, `impact`, `contact` | 3 FSR por pé | N aproximados; **precisam de calibração** (abaixo) |
| `battery` | divisor na bateria | curva típica de Li-ion |
| `sos` | botão | segurar 1,5 s |

Sensor desligado em `config.h` ou que não responde ao iniciar **não envia** a
grandeza. Nada é preenchido com valor falso.

## Ligações (padrão em `config.h`)

- I2C (SDA 21, SCL 22): MAX30102, MPU6050, INA219
- DS18B20: GPIO 4 (resistor de 4,7 kΩ entre dados e 3,3 V)
- FSR: calcanhar 34, meio 35, antepé 32 — cada um num divisor com resistor de ~10 kΩ ao GND. **Use só pinos ADC1** (32–39); o ADC2 não funciona com Bluetooth ligado.
- Bateria: GPIO 33 por um divisor 1:1 (dois resistores iguais)
- Botão SOS: GPIO 27 para o GND

O INA219 vai **na saída do circuito gerador** (antes de carregar a bateria), para
medir o que o tênis de fato gera.

## Um tênis por pé

Cada ESP32 manda só o pé dele (`FOOT_SIDE` `'L'` ou `'R'` em `config.h`). O app
conecta a um tênis por vez; a tela de Biomecânica mostra o pé conectado ao vivo
e, em "Média de 7 dias", junta o que cada pé gravou nos dias em que foi usado.

## Calibração da pressão

O FSR não é linear e varia de peça para peça, então os Newtons são aproximados.

1. Com o tênis no ar, mande **Calibrar** (Meus tênis). Isso grava o zero na
   memória do ESP32. Se algum sensor estiver apertado, o tênis recusa e avisa.
2. Para a escala, pise numa balança de cozinha/banheiro com um peso conhecido,
   compare com o `impact` e ajuste `FSR_NEWTONS_PER_COUNT` em `config.h`.

Mesmo sem ajustar a escala, as **porcentagens por zona** e a **simetria** (que
são relativas) continuam úteis; só os Newtons absolutos dependem dela.

## Queda e SOS

A queda é decidida no tênis: queda livre (< 0,45 g por ≥ 60 ms), impacto
(> 2,5 g em até 0,8 s) e depois ~2,5 s de imobilidade. Pular e voltar a andar
não dispara. O tênis só avisa o app (`fall:1`); quem abre a contagem de 30 s
para cancelar e envia a mensagem é o app. O limite de 2,5 g e os tempos estão
em `StepVoltLogic.h` e **devem ser testados com o seu hardware** — o
acelerômetro dentro de um tênis vê coisas diferentes de um no pulso.

## Protocolo

Veja o comentário no topo de `StepVoltFirmware.ino`. Resumo: linha JSON, `*XX`
de checksum (XOR dos bytes do JSON), `\n` no fim. Firmware v1 (sem checksum)
continua funcionando com o app novo.

## Testes

```
sh tests/run_tests.sh      # precisa só de g++
```
