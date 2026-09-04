# NIPI OBD — Scanner de Diagnóstico (protótipo)

App de diagnóstico automotivo **OBD‑II / ELM327**, feito para o **Fiat Mobi** mas compatível com
qualquer carro OBD‑II. Roda **no computador e no Android** (Chrome) a partir de um único arquivo.

## Como usar

1. Abra `index.html` no Chrome (PC ou Android).
2. Toque em **▶ Modo Demonstração** para ver o app lendo falhas com dados simulados
   (reproduz as luzes do painel: motor, ABS, airbag, rede).
3. Para ler o carro de verdade, conecte o adaptador:
   - **USB (Serial)** — adaptadores com cabo.
   - **Bluetooth (BLE)** — adaptadores Bluetooth 4.0/LE.
   - Adaptadores **Bluetooth clássico** e **Wi‑Fi** **não** funcionam no navegador — precisam do
     app Android/desktop (próximas fases). Veja o porquê em `DIAGNOSTICO-FIAT-MOBI.md`.

> Para acessar hardware real, sirva o arquivo por `https://` ou `http://localhost` (o Chrome
> exige contexto seguro para Web Bluetooth/Serial). Ex.: `python3 -m http.server` na pasta.

## O que já funciona

- Leitura de falhas: **Modo 03** (armazenadas), **07** (pendentes), **0A** (permanentes).
- **Limpar falhas** (Modo 04).
- **Dados ao vivo** (Modo 01): RPM, velocidade, temperatura, carga, borboleta, admissão, tensão, avanço.
- **Console ELM327** para enviar comandos AT à mão (ex.: `ATRV`, `03`, `0100`).
- Decodificação de DTC genérica (P/C/B/U) + dicionário em **português** (`DTC_DB`).

## Arquivos

| Arquivo | O que é |
|---------|---------|
| `index.html` | O app completo (um arquivo só, sem dependências). |
| `DIAGNOSTICO-FIAT-MOBI.md` | Pesquisa: luzes do painel, protocolo ELM327, tipos de adaptador, DTCs comuns. |

## Próximas fases

- App **Android nativo** (Bluetooth clássico — a maioria dos adaptadores).
- App **desktop** (Wi‑Fi / USB, com exportação de relatório).
- Integração com o **sistema de som / multimídia** do carro.

> ⚠️ Uso educativo. Faça diagnósticos com o carro **parado e freio de mão**. Apagar falhas não
> conserta o defeito — se ele persistir, a luz volta.
