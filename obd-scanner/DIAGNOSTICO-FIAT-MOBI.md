# Diagnóstico do Fiat Mobi via OBD‑II — pesquisa e guia

Documento de apoio ao protótipo **NIPI OBD** (scanner de diagnóstico).
Objetivo: entender **por que as luzes do painel acendem**, como o adaptador que você comprou
conversa com o carro, e o caminho para os apps (computador, Android e integração com o som).

---

## 1. O que o painel do seu Mobi está mostrando

Pela foto do painel (hodômetro **327.138 km**), há **várias luzes de alerta acesas ao mesmo tempo**.
Cores no padrão universal:

| Cor | Significado | Ação |
|-----|-------------|------|
| 🟢 Verde/Azul | Só indica um sistema **ligado** (não é defeito) | Nenhuma |
| 🟡 Amarelo/Laranja | **Alerta** — algo fora do normal | Diagnosticar em breve |
| 🔴 Vermelho | **Grave** — atenção imediata | Parar / não rodar |

Luzes prováveis na foto (a leitura OBD confirma o código exato):

- **ABS (amarela)** — falha no sistema antibloqueio. O freio comum funciona, mas sem ABS.
- **Airbag (laranja)** — falha no sistema de airbag; em uma batida pode não disparar.
- **Injeção eletrônica / motor (amarela)** — falha no motor ou nas emissões.
- Ícones vermelhos adicionais (freio/óleo/porta) — confirmar com o scanner.

> ⚠️ Importante: **a luz do painel só diz que existe um problema, não qual é.** O código de
> falha (DTC) guardado no módulo é que aponta o defeito. Por isso o app é útil: ele lê esses
> códigos. É exatamente o que o **Modo Demonstração** do protótipo mostra (P0300 motor,
> C0035 ABS, B0001 airbag, U0100 rede).

---

## 2. Como funciona o OBD‑II + adaptador ELM327

O seu adaptador (“**OBDII Diagnostic Interface / Car Diagnostic Scanner**”) é um **ELM327** —
o chip padrão dos scanners baratos. Ele se pluga na tomada **OBD‑II** do carro (embaixo do painel,
lado do motorista) e traduz comandos de texto simples ↔ barramento **CAN** do carro.

### 2.1 Comandos AT (configuração do adaptador)
| Comando | Função |
|---------|--------|
| `ATZ`   | Reinicia o adaptador |
| `ATE0`  | Desliga eco dos comandos |
| `ATL0`  | Sem quebra de linha extra |
| `ATSP0` | Auto‑detectar protocolo do carro |
| `ATRV`  | Lê a **tensão da bateria** |
| `ATDPN` | Mostra o protocolo detectado |

### 2.2 Modos OBD (serviços) usados pelo app
| Modo | Para quê |
|------|----------|
| `01` | **Dados ao vivo** (RPM, velocidade, temperatura, etc.) |
| `03` | **Ler falhas armazenadas** (DTCs que acendem a luz) |
| `04` | **Apagar falhas** e a luz |
| `07` | Falhas **pendentes** (ainda não confirmadas) |
| `0A` | Falhas **permanentes** (só saem sozinhas) |

### 2.3 Como um DTC é montado
A resposta do modo `03` vem em **bytes**. Cada falha são 2 bytes (A,B):
- 2 bits mais altos de A = sistema → **P** (motor), **C** (chassi/ABS), **B** (carroceria/airbag), **U** (rede).
- O resto forma os 4 dígitos. Ex.: bytes `40 35` → **C0035** (sensor de roda do ABS).

O protótipo faz exatamente essa conta na função `bytesToDTC()`.

---

## 3. Qual é o SEU adaptador? (isto decide a plataforma)

Os ELM327 baratos vêm em **4 tipos de conexão** e isso muda o que consegue ler o carro:

| Tipo | Como identificar | Funciona no navegador (este protótipo)? | Caminho recomendado |
|------|------------------|:--------------------------------------:|---------------------|
| **Bluetooth clássico** | Pareia como “OBDII”, PIN `1234`/`0000` (o mais comum e barato) | ❌ Não (navegador só fala BLE) | **App Android nativo** (Bluetooth SPP) |
| **Bluetooth LE / 4.0** | Anúncios “BLE”, funciona no iPhone | ✅ Sim (botão *Bluetooth BLE*) | Navegador / Android |
| **Wi‑Fi** | Cria rede “WiFi_OBDII”, IP `192.168.0.10:35000` | ❌ Não (navegador não abre TCP puro) | **App desktop** (Windows/Linux) |
| **USB / cabo** | Conector USB | ✅ Sim (botão *USB Serial*) | Computador |

👉 **Preciso saber qual é o seu** para acertar a próxima fase. Na caixa/anúncio costuma estar escrito
“Bluetooth”, “BLE”, “Wi‑Fi” ou “USB”.

---

## 4. Falhas comuns do Fiat Mobi (dicionário do app)

O protótipo já traz descrições em português para os códigos genéricos e alguns específicos Fiat.
Exemplos:

- **P0300 / P0301‑P0304** — falha de combustão (misfire), motor “engasgando”.
- **P0171 / P0172** — mistura pobre / rica (entrada de ar falsa, bico, sonda).
- **P0130 / P0135** — sonda lambda (sensor de oxigênio).
- **P0420** — catalisador com baixa eficiência.
- **P0442 / P0455** — vazamento no EVAP (muitas vezes **tampa do tanque** solta).
- **P0335 / P0340** — sensor de rotação (CKP) / de fase (CMP).
- **C0035–C0050** — sensores de velocidade das rodas (ABS).
- **B0001–B0051** — circuitos de airbag e pré‑tensionador do cinto.
- **U0100 / U0121 / U0155** — perda de comunicação com motor / ABS / painel.
- **P1610 / B1901 / U1601** — específicos Fiat (imobilizador, airbag, body computer).

> A lista completa (com nível de gravidade) está no arquivo `index.html`, objeto `DTC_DB`.
> É fácil de ampliar conforme aparecerem códigos reais no seu carro.

---

## 5. Roadmap (o que você pediu)

1. ✅ **Protótipo que lê as falhas** — este `index.html` (navegador, PC + Android, com Demo).
2. ⏳ **Versão Android nativa** — para adaptadores Bluetooth clássicos (a maioria). Reaproveita a
   mesma lógica de decodificação deste protótipo.
3. ⏳ **Versão desktop (PC)** — para adaptadores Wi‑Fi e USB, com log e exportação.
4. ⏳ **Integração com o sistema de som** — mostrar dados/falhas na tela do multimídia
   (via app Android no head‑unit, ou saída para a central).

---

## 6. Fontes

- Significado das luzes do painel (Fiat): <https://blog.deltafiat.com.br/significado-das-luzes-do-painel-do-seu-carro/>
- Luz de airbag — o que significa: <https://www.minutoseguros.com.br/blog/luz-de-airbag-acesa-pode-significar-algum-problema/>
- ELM327 — guia de comandos AT / OBD‑II: <https://shuvabratadey.github.io/OBD-II/index.html>
- Referência completa de PIDs OBD‑II: <https://github.com/evrenonur/obd2-elm327-pid-reference/blob/master/OBD2_Complete_PID_Reference_EN.md>
- Emulador ELM327 (para testes sem carro): <https://github.com/jimwhitelaw/ELMulator>
