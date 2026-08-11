# Painel Chromecast (sinalização digital)

App de Chromecast para deixar uma TV mostrando um painel fixo — avisos, indicadores,
agenda, cardápio, imagens — girando sozinho e se atualizando de tempos em tempos.

São duas partes, como todo app de Cast:

| Parte | Onde roda | Pasta |
|---|---|---|
| **Receiver** | dentro do Chromecast, na TV | `receiver/` |
| **Sender** (controle) | Chrome no PC | `sender/` |
| **Conteúdo** | JSON com os slides | `content/` |

O receiver funciona sozinho: assim que sobe, ele carrega o JSON, gira os slides e
recarrega o arquivo a cada X segundos. O sender serve para escolher a TV, trocar a
fonte do conteúdo e controlar (próximo, pausar, recarregar).

## Estrutura

```
chromecast/
├── receiver/
│   ├── index.html      página que abre na TV
│   ├── receiver.js     rotação dos slides + comandos do Cast
│   └── receiver.css    layout pensado para TV (fontes grandes, margem de overscan)
├── sender/
│   ├── index.html      painel de controle
│   ├── sender.js       conexão com o Chromecast e envio de comandos
│   ├── sender.css
│   └── config.js       ← seu App ID vai aqui
└── content/
    └── slides.json     o que aparece na tela
```

## Como colocar no ar

### 1. Publique o receiver em HTTPS

O Chromecast só carrega receiver por HTTPS. A forma mais rápida é o GitHub Pages:
ative o Pages deste repositório e a URL do receiver fica

```
https://<usuario>.github.io/<repo>/chromecast/receiver/
```

Qualquer hospedagem estática com HTTPS serve (Netlify, Vercel, Cloudflare Pages, S3+CloudFront).

### 2. Registre o app no Cast Developer Console

1. Acesse <https://cast.google.com/publish> (é preciso pagar a taxa única de US$ 5 de registro de desenvolvedor).
2. **Add new application → Custom Receiver**.
3. Em *Receiver Application URL*, cole a URL do passo 1.
4. Salve. O console gera o **Application ID** (8 caracteres, tipo `A1B2C3D4`).
5. Em **Devices**, registre o número de série do seu Chromecast — sem isso o app
   não publicado não abre no aparelho. O serial está atrás do dispositivo e no app Google Home.
6. Reinicie o Chromecast e espere ~15 minutos para o registro propagar.

Enquanto o app não é publicado, ele só funciona nos dispositivos registrados. Isso
basta para uso interno; publicar só é necessário para distribuir a terceiros.

### 3. Aponte o sender para o seu App ID

Em `sender/config.js`:

```js
const CAST_APP_ID = 'A1B2C3D4'; // o ID gerado no console
```

### 4. Abra o controle

O sender também precisa de HTTPS (ou `localhost`) e só funciona no **Chrome/Edge desktop** —
o SDK de Cast para web não existe em navegador de celular. Para controlar pelo celular
seria preciso um app Android/iOS usando o SDK nativo.

## Desenvolvimento local

```bash
cd chromecast
python3 -m http.server 8080
```

- Prévia do painel sem Chromecast: <http://localhost:8080/receiver/>
  (roda em modo preview, sem a parte de Cast — dá para ajustar layout e conteúdo)
- Controle: <http://localhost:8080/sender/>

Para testar com o Chromecast de verdade, o **receiver** precisa estar publicado em
HTTPS, porque quem baixa a página é o aparelho, não o seu PC. O sender pode continuar
rodando em `localhost`.

Dica: para depurar o receiver, abra `chrome://inspect` no Chrome com o Chromecast na
mesma rede — o console da TV aparece ali. É preciso ligar a opção de debug no
Cast Developer Console.

## Formato do `slides.json`

```json
{
  "brand": "Nome que aparece no canto",
  "defaultDuration": 12,
  "refreshSeconds": 300,
  "slides": [ ... ]
}
```

- `defaultDuration` — segundos por slide, quando o slide não define o seu.
- `refreshSeconds` — de quanto em quanto tempo o painel rebusca o JSON. `0` desliga.

### Tipos de slide

**`text`** — título, subtítulo e um parágrafo.

```json
{ "type": "text", "title": "Bom dia!", "subtitle": "Avisos", "body": "Texto...", "duration": 10 }
```

**`metrics`** — cartões com números. `status` (`ok`, `warn`, `danger`) colore o valor.

```json
{
  "type": "metrics",
  "title": "Números da semana",
  "items": [{ "label": "Pedidos", "value": "128", "status": "ok" }]
}
```

**`list`** — linhas com texto à esquerda e um complemento à direita (horário, preço).

```json
{
  "type": "list",
  "title": "Agenda de hoje",
  "items": [{ "text": "Reunião", "secondary": "09:00" }]
}
```

**`image`** — imagem em tela cheia. `fit` aceita `cover` (padrão, preenche cortando)
ou `contain` (mostra inteira).

```json
{ "type": "image", "url": "https://…/foto.jpg", "fit": "cover", "caption": "Legenda opcional" }
```

Todo slide aceita `duration` (segundos) e `background` (cor de fundo em CSS).

## Protocolo entre sender e receiver

Namespace: `urn:x-cast:com.nipi.signage` (definido nos dois lados; se mudar, mude nos dois).

Sender → receiver:

| Mensagem | Efeito |
|---|---|
| `{"type":"NEXT"}` / `{"type":"PREV"}` | troca de slide |
| `{"type":"GO_TO","index":2}` | vai para um slide |
| `{"type":"PAUSE"}` / `{"type":"RESUME"}` / `{"type":"TOGGLE"}` | pausa a rotação |
| `{"type":"RELOAD"}` | rebusca o JSON atual |
| `{"type":"SET_SOURCE","url":"https://…/slides.json"}` | troca a fonte do conteúdo |
| `{"type":"SET_CONFIG","config":{…}}` | envia os slides direto, sem arquivo |
| `{"type":"PING"}` | pede o estado atual |

Receiver → sender: `{"type":"STATE","index":0,"total":4,"paused":false,"title":"…","source":"…"}`,
enviado a cada troca de slide e quando um controle conecta.

## Detalhes que costumam morder

- **Idle timeout** — o Chromecast encerra apps que não tocam mídia. O receiver liga
  `disableIdleTimeout` justamente para o painel não cair sozinho. Ainda assim, a TV
  pode entrar em descanso; para painel 24/7 vale desativar o modo ambiente/sleep na TV.
- **Overscan** — muita TV corta as bordas. O layout já reserva 3% de margem.
- **Conteúdo misto** — receiver em HTTPS não carrega imagem em HTTP. Use HTTPS nas imagens.
- **CORS** — se o `slides.json` estiver em outro domínio, ele precisa responder com
  `Access-Control-Allow-Origin`.
- **Cache** — as buscas usam `no-store`, mas CDN na frente do JSON ainda pode segurar
  a versão antiga; ajuste o `Cache-Control` do arquivo se o painel demorar a atualizar.

## Próximos passos possíveis

- Puxar os números de uma planilha ou API em vez de um JSON estático.
- App Android/iOS de controle (o SDK web não roda em celular).
- Agendamento: mostrar conjuntos de slides diferentes por horário ou dia da semana.
