# Gerar o APK Android (NIPI OBD)

Este projeto empacota o **mesmo app** (`../index.html`) como um aplicativo Android usando
**Capacitor**, com Bluetooth **clássico** (SPP) para falar com o adaptador ELM327 que pareia
como “OBDII”.

> Não dá pra compilar o APK no ambiente da nuvem (falta o Android SDK). Rode os passos abaixo
> num computador com **Android Studio** instalado. É rápido.

## Pré-requisitos
- Node.js 18+ e npm
- **Android Studio** (traz o Android SDK e o JDK)
- Um celular Android com **Depuração USB** ligada (ou o emulador)

## Passo a passo

```bash
cd obd-scanner/android-app

# 1. instala as dependências (Capacitor + plugin Bluetooth clássico)
npm install

# 2. copia o app web e cria o projeto Android nativo (pasta android/)
npm run add:android

# 3. sincroniza os plugins (Bluetooth) para o projeto nativo
npm run sync
```

### Permissões de Bluetooth (Android 12+)
Abra `android/app/src/main/AndroidManifest.xml` e confirme que existem estas linhas dentro de
`<manifest>` (o plugin costuma adicionar; se faltar, cole):

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

No Android 12+ o sistema pede a permissão **“Dispositivos por perto”** na primeira conexão —
aceite quando aparecer.

### Compilar / rodar
```bash
# abre no Android Studio (Run ▶ para instalar no celular)
npm run open:android

# OU direto pelo terminal, com o celular conectado:
npm run run:android
```

Para gerar um **APK** de instalação: no Android Studio → *Build → Build Bundle(s)/APK(s) → Build APK(s)*.
O arquivo sai em `android/app/build/outputs/apk/debug/app-debug.apk`.

## Como usar no carro
1. Pareie o adaptador nas **Configurações → Bluetooth** do Android (nome “OBDII”, PIN `1234`).
2. Ligue a chave do carro (motor pode estar desligado, mas com contato).
3. Abra o app **NIPI OBD** → toque em **📲 Bluetooth (app Android)** → aba **Falhas** → **Ler falhas**.

## Como funciona por dentro
- O app é o `index.html` (mesmo código do computador).
- Dentro do Android existe `window.bluetoothSerial` (plugin `cordova-plugin-bluetooth-serial`);
  o app detecta isso e ativa a classe **`BtSppTransport`**, que usa `list/connect/write/subscribe`.
- A leitura e decodificação de falhas (`bytesToDTC`, `parseDTCs`, `DTC_DB`) é **a mesma** do web.

## Atualizar o app depois de mexer no `../index.html`
```bash
npm run sync   # copia o index.html novo e re-sincroniza
```
