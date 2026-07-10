/* =====================================================================
   NIPI Línguas — Backend (Cloudflare Worker)
   Um único Worker com 3 rotas:
     POST /ai       -> IA (Anthropic). Faz a IA funcionar em qualquer lugar.
     POST /catalog  -> Recomendações de filmes/séries (TMDB).
     POST /music    -> Busca de música (metadados + YouTube + letra opcional).

   COMO USAR (resumo no fim do arquivo):
   1) crie o Worker no Cloudflare e cole este arquivo
   2) configure as variáveis (Settings > Variables and Secrets)
   3) no app, em Gerenciar > Dados, cole as URLs:
        IA       -> https://SEU-WORKER.workers.dev/ai
        Catálogo -> https://SEU-WORKER.workers.dev/catalog
        Música   -> https://SEU-WORKER.workers.dev/music
   ===================================================================== */

const CORS = {
  'Access-Control-Allow-Origin': '*',            // troque por https://nipi-br.github.io para restringir
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const url = new URL(req.url);
    try {
      if (url.pathname.endsWith('/ai'))      return await handleAI(req, env);
      if (url.pathname.endsWith('/catalog')) return await handleCatalog(req, env);
      if (url.pathname.endsWith('/music'))   return await handleMusic(req, env);
      return json({ error: 'rota não encontrada. Use /ai, /catalog ou /music' }, 404);
    } catch (e) {
      return json({ error: String(e && e.message || e) }, 500);
    }
  },
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  });
}

/* ---------------------------------------------------------------------
   1) IA — proxy para a Anthropic Messages API
   Recebe { system, messages, model, max_tokens } e injeta a chave.
   Mantém o MESMO formato de resposta da Anthropic (o app já entende).
   --------------------------------------------------------------------- */
async function handleAI(req, env) {
  if (!env.ANTHROPIC_API_KEY) return json({ error: 'ANTHROPIC_API_KEY não configurada' }, 500);
  const body = await req.json();
  const r = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': env.ANTHROPIC_API_KEY,
      'anthropic-version': '2023-06-01',
    },
    body: JSON.stringify({
      model: body.model || 'claude-sonnet-4-6',
      max_tokens: body.max_tokens || 1000,
      system: body.system || undefined,
      messages: body.messages || [],
    }),
  });
  const data = await r.json();
  return json(data, r.status);
}

/* ---------------------------------------------------------------------
   2) CATÁLOGO — recomendações via TMDB (dados de streaming via JustWatch)
   Recebe { language, country, providers, page }
   Devolve { results: [ { title, year, poster, providers } ] }
   --------------------------------------------------------------------- */
const TMDB_PROVIDER_IDS = {  // IDs do TMDB (podem variar por região; ajuste se precisar)
  netflix: 8, prime: 119, disney: 337, max: 1899,
  appletv: 350, paramount: 531, crunchyroll: 283, globoplay: 307, viki: 344,
};
const PROVIDER_NAMES = {
  netflix: 'Netflix', prime: 'Prime Video', disney: 'Disney+', max: 'Max',
  appletv: 'Apple TV+', paramount: 'Paramount+', crunchyroll: 'Crunchyroll',
  globoplay: 'Globoplay', viki: 'Rakuten Viki',
};

async function handleCatalog(req, env) {
  if (!env.TMDB_API_KEY) return json({ error: 'TMDB_API_KEY não configurada' }, 500);
  const { language, country = 'BR', providers = [], page = 1 } = await req.json();
  const ids = providers.map(p => TMDB_PROVIDER_IDS[p]).filter(Boolean).join('|');
  const base = 'https://api.themoviedb.org/3';
  const common =
    `api_key=${env.TMDB_API_KEY}&watch_region=${country}` +
    `&with_watch_providers=${ids}&sort_by=popularity.desc&page=${page}&language=pt-BR`;
  const lang = language ? `&with_original_language=${language}` : '';

  const [mv, tv] = await Promise.all([
    fetch(`${base}/discover/movie?${common}${lang}`).then(r => r.json()).catch(() => ({})),
    fetch(`${base}/discover/tv?${common}${lang}`).then(r => r.json()).catch(() => ({})),
  ]);

  const provNames = providers.map(p => PROVIDER_NAMES[p] || p);
  const map = (arr, isTv) => (arr.results || []).map(x => ({
    title: isTv ? x.name : x.title,
    year: ((isTv ? x.first_air_date : x.release_date) || '').slice(0, 4),
    poster: x.poster_path ? `https://image.tmdb.org/t/p/w185${x.poster_path}` : '',
    providers: provNames,
  }));

  const results = [...map(mv, false), ...map(tv, true)].filter(r => r.title).slice(0, 30);
  return json({ results });
}

/* ---------------------------------------------------------------------
   3) MÚSICA — metadados (iTunes) + vídeo (YouTube) + letra (opcional)
   Recebe { query } e devolve { title, artist, artwork, youtube, lyrics, lyricsNote }

   LETRA: por padrão NÃO retorna letra (é conteúdo protegido por direitos).
   Para ativar, defina a variável LYRICS_PROVIDER:
     - "lyricsovh"  -> usa api.lyrics.ovh (gratuita, NÃO-oficial; use por sua conta e risco)
     - (recomendado: um provedor licenciado, como Musixmatch, com a sua chave)
   --------------------------------------------------------------------- */
async function handleMusic(req, env) {
  const { query } = await req.json();
  if (!query) return json({ error: 'query vazia' }, 400);

  // (a) metadados via iTunes Search (sem chave, com CORS)
  let meta = {};
  try {
    const it = await fetch(
      `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&entity=song&limit=1`
    ).then(r => r.json());
    const t = (it.results || [])[0];
    if (t) meta = { title: t.trackName, artist: t.artistName, artwork: t.artworkUrl100 };
  } catch (e) {}

  const title = meta.title || query;
  const artist = meta.artist || '';

  // (b) vídeo do YouTube (precisa de YOUTUBE_API_KEY)
  let youtube = '';
  if (env.YOUTUBE_API_KEY) {
    try {
      const q = encodeURIComponent(`${artist} ${title}`.trim());
      const yt = await fetch(
        `https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q=${q}&key=${env.YOUTUBE_API_KEY}`
      ).then(r => r.json());
      const id = ((yt.items || [])[0] || {}).id?.videoId;
      if (id) youtube = `https://www.youtube.com/watch?v=${id}`;
    } catch (e) {}
  }

  // (c) letra (opcional — só se você ativar um provedor)
  let lyrics = '';
  let lyricsNote = '';
  if (env.LYRICS_PROVIDER === 'lyricsovh' && artist) {
    try {
      const lo = await fetch(
        `https://api.lyrics.ovh/v1/${encodeURIComponent(artist)}/${encodeURIComponent(title)}`
      ).then(r => r.json());
      if (lo && lo.lyrics) lyrics = lo.lyrics;
    } catch (e) {}
  }
  if (!lyrics) {
    lyricsNote = 'Letra não obtida automaticamente — cole manualmente ou configure um provedor de letras.';
  }

  return json({ ...meta, title, artist, youtube, lyrics, lyricsNote });
}

/* =====================================================================
   PASSO A PASSO

   A) Criar o Worker
      1. Acesse dash.cloudflare.com -> Workers & Pages -> Create -> Worker
      2. Edite o código, apague o exemplo e cole TODO este arquivo. Deploy.

   B) Variáveis (Settings -> Variables and Secrets -> Add)
      ANTHROPIC_API_KEY  = sua chave da Anthropic            (obrigatória p/ IA)
      TMDB_API_KEY       = sua chave da TMDB (themoviedb.org) (p/ recomendações)
      YOUTUBE_API_KEY    = chave da YouTube Data API v3       (opcional, p/ achar o vídeo)
      LYRICS_PROVIDER    = lyricsovh                          (opcional, p/ letra)
      Marque ANTHROPIC_API_KEY como "Secret" (encrypt).

   C) Conectar no app (Gerenciar -> Dados)
      IA       -> https://SEU-WORKER.workers.dev/ai
      Catálogo -> https://SEU-WORKER.workers.dev/catalog
      Música   -> https://SEU-WORKER.workers.dev/music

   Pronto: a IA (tutor, tradução, geração de vocabulário e de testes) passa a
   funcionar em qualquer lugar, inclusive no GitHub Pages.

   Dica de segurança: depois de testar, troque o Access-Control-Allow-Origin
   de '*' para a URL do seu site (ex.: 'https://nipi-br.github.io') para evitar
   que outros usem o seu backend.
   ===================================================================== */
