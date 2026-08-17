# Leitor Dinâmico NIPI

Leitor de livros palavra a palavra para Android. O telefone fica deitado, o fundo é
preto e uma palavra branca aparece por vez no centro da tela. Enquanto você mantém o
dedo pressionado, as palavras passam na velocidade que você configurou.

**Versão 0.1-beta.**

## Como se usa

| Gesto | O que faz |
|---|---|
| Segurar o **lado direito** | Avança as palavras na velocidade configurada |
| Segurar o **lado esquerdo** | Volta as palavras na mesma velocidade |
| **Dois toques** no lado esquerdo | Volta de uma vez a quantidade de palavras configurada |
| Toque simples | Anda uma palavra só, para frente ou para trás |
| Botão voltar | Sai da leitura (a posição já está salva) |

A posição é salva sozinha: ao soltar o dedo, ao sair da leitura e ao fechar o app.
Quando você abrir o livro de novo, ele continua exatamente onde parou.

## Formatos aceitos

- **EPUB** — lido na ordem correta dos capítulos, seguindo a espinha do livro.
- **PDF** — extração de texto com PdfBox. Funciona em PDF gerado digitalmente.
  PDF escaneado não tem texto para extrair, só imagens: o app avisa quando isso acontece.
- **TXT**, e qualquer outro arquivo de texto simples.

A extração acontece **uma vez, na importação**. Depois disso o livro vira uma lista de
palavras guardada no aparelho, e ler passa a ser instantâneo — é isso que permite
segurar o dedo e ver as palavras passarem sem engasgo.

## Configurações

- **Velocidade** — de 60 a 1000 palavras por minuto. Padrão 400.
- **Retrocesso do toque duplo** — de 5 a 100 palavras. Padrão 15.
- **Tamanho da palavra** — de 24 a 140 pontos. Palavras longas encolhem para caber.
- **Pausa na pontuação** — segura um instante a mais na vírgula e no ponto final.
  Ligado por padrão; ajuda bastante a entender o texto em velocidade alta.

## Como compilar e instalar no telefone

1. Abra a pasta `leitor-dinamico/` no Android Studio (Open, não Import).
2. Espere o Gradle sincronizar. Ele baixa sozinho o Android SDK que faltar.
3. Ligue o telefone no cabo USB com a **depuração USB** ativada
   (Configurações → Sobre o telefone → toque 7 vezes em "Número da versão" para
   liberar as Opções do desenvolvedor).
4. Escolha o telefone na barra superior e clique em **Run**.

Não é preciso conta de desenvolvedor nem publicar em loja: instalar no seu próprio
aparelho pelo cabo é livre.

Para gerar um APK e passar para outro telefone:

```bash
./gradlew assembleDebug
# o arquivo sai em app/build/outputs/apk/debug/app-debug.apk
```

## Versões usadas

| Item | Versão |
|---|---|
| Plugin do Android (AGP) | 8.7.3 |
| Gradle | 8.9 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8) |
| PdfBox-Android | 2.0.27.0 |

São versões fixadas numa combinação estável. Se o Android Studio oferecer atualizar,
pode aceitar — mas o projeto compila como está, sem mexer em nada.

## Estrutura

```
app/src/main/java/com/nipi/leitordinamico/
├── LibraryActivity.kt    lista de livros, importação, exclusão
├── ReaderActivity.kt     a tela de leitura e o mecanismo de pressionar
├── SettingsActivity.kt   velocidade, retrocesso, tamanho, pausa
├── TextExtractor.kt      EPUB, PDF e texto simples → texto corrido
├── BookStore.kt          livros, texto no disco e posição de leitura
└── Prefs.kt              preferências de leitura
```

## O que ainda não tem

Coisas deixadas de fora desta primeira versão, de propósito:

- Capa do livro e índice de capítulos.
- Ajuste de velocidade dentro da própria leitura, sem ir às configurações.
- OCR para PDF escaneado (exigiria processar o livro fora do telefone).
- Marcadores e anotações.
