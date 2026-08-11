/**
 * Receiver do painel de sinalização.
 *
 * Roda dentro do Chromecast (Custom Web Receiver / CAF v3). Responsabilidades:
 *   1. carregar a lista de slides (de um JSON) e girar entre eles;
 *   2. aceitar comandos do sender (celular/PC) por mensagem custom;
 *   3. recarregar o JSON de tempos em tempos, para o painel se atualizar sozinho.
 *
 * O arquivo também abre direto no navegador (sem Cast) para desenvolvimento:
 * a parte de Cast só é ligada se o SDK estiver presente.
 */

/** Namespace das mensagens custom. Precisa ser idêntico no sender. */
const NAMESPACE = 'urn:x-cast:com.nipi.signage';

/** Origem padrão dos slides, relativa a esta pasta. */
const DEFAULT_SOURCE = '../content/slides.json';

/** Usado quando o JSON não carrega (rede fora do ar, URL errada, etc.). */
const FALLBACK_CONFIG = {
  brand: 'Painel',
  defaultDuration: 10,
  refreshSeconds: 0,
  slides: [
    {
      type: 'text',
      title: 'Painel pronto',
      subtitle: 'Aguardando conteúdo',
      body: 'Nenhum slide foi carregado. Verifique o arquivo de conteúdo ou envie uma configuração pelo controle.',
    },
  ],
};

const el = {
  stage: document.getElementById('stage'),
  slide: document.getElementById('slide'),
  brand: document.getElementById('brand'),
  time: document.getElementById('time'),
  date: document.getElementById('date'),
  progressBar: document.getElementById('progress-bar'),
  status: document.getElementById('status'),
};

const state = {
  config: FALLBACK_CONFIG,
  source: DEFAULT_SOURCE,
  index: 0,
  paused: false,
  /** timestamp (ms) em que o slide atual começou a aparecer */
  slideStartedAt: 0,
  /** duração do slide atual, em ms */
  slideDuration: 0,
  refreshTimer: null,
};

/* ------------------------------------------------------------------ */
/* Carregamento do conteúdo                                            */
/* ------------------------------------------------------------------ */

/**
 * Busca o JSON de slides e aplica. Em caso de erro mantém o conteúdo atual
 * (um painel que já está rodando não deve apagar por causa de uma falha de rede).
 */
async function loadSource(url, { silent = false } = {}) {
  if (!silent) showStatus('Carregando conteúdo…');
  try {
    // cache: 'no-store' garante que o painel pegue a versão nova do arquivo.
    const response = await fetch(url, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const config = await response.json();
    state.source = url;
    applyConfig(config);
    hideStatus();
  } catch (error) {
    console.error('[receiver] falha ao carregar', url, error);
    if (state.config === FALLBACK_CONFIG) {
      applyConfig(FALLBACK_CONFIG);
    }
    showStatus(`Sem conexão com o conteúdo (${error.message})`, 6000);
  }
}

/** Valida o mínimo e passa a usar a configuração recebida. */
function applyConfig(config) {
  if (!config || !Array.isArray(config.slides) || config.slides.length === 0) {
    console.warn('[receiver] configuração sem slides; ignorando');
    return;
  }

  state.config = config;
  state.index = 0;
  el.brand.textContent = config.brand || '';

  scheduleRefresh();
  render();
  broadcastState();
}

/** Reprograma o auto-refresh do JSON conforme `refreshSeconds`. */
function scheduleRefresh() {
  clearInterval(state.refreshTimer);
  const seconds = Number(state.config.refreshSeconds) || 0;
  if (seconds <= 0) return;
  state.refreshTimer = setInterval(() => {
    loadSource(state.source, { silent: true });
  }, seconds * 1000);
}

/* ------------------------------------------------------------------ */
/* Renderização dos slides                                             */
/* ------------------------------------------------------------------ */

function currentSlide() {
  const { slides } = state.config;
  return slides[state.index % slides.length];
}

/** Monta o slide atual no DOM e reinicia o cronômetro de troca. */
function render() {
  const slide = currentSlide();
  if (!slide) return;

  el.slide.className = 'slide';
  el.slide.style.backgroundImage = '';
  el.slide.innerHTML = buildSlideMarkup(slide);

  if (slide.type === 'image') {
    el.slide.classList.add('image');
    if (slide.fit !== 'contain') el.slide.classList.add('cover');
    el.slide.style.backgroundImage = `url("${cssUrl(slide.url)}")`;
  }

  if (slide.background) el.stage.style.background = slide.background;
  else el.stage.style.background = '';

  // Reinicia a transição de opacidade a cada troca.
  requestAnimationFrame(() => el.slide.classList.add('visible'));

  const seconds =
    Number(slide.duration) || Number(state.config.defaultDuration) || 10;
  state.slideDuration = seconds * 1000;
  state.slideStartedAt = performance.now();
}

function buildSlideMarkup(slide) {
  switch (slide.type) {
    case 'image':
      return slide.caption ? `<h2>${escapeHtml(slide.caption)}</h2>` : '';

    case 'metrics':
      return `
        ${slide.title ? `<h1>${escapeHtml(slide.title)}</h1>` : ''}
        <div class="metrics">
          ${(slide.items || [])
            .map(
              (item) => `
                <div class="metric ${statusClass(item.status)}">
                  <span class="value">${escapeHtml(item.value)}</span>
                  <span class="label">${escapeHtml(item.label)}</span>
                </div>`
            )
            .join('')}
        </div>`;

    case 'list':
      return `
        ${slide.title ? `<h1>${escapeHtml(slide.title)}</h1>` : ''}
        <div class="list">
          ${(slide.items || [])
            .map(
              (item) => `
                <div class="row">
                  <span>${escapeHtml(item.text)}</span>
                  ${item.secondary ? `<span class="secondary">${escapeHtml(item.secondary)}</span>` : ''}
                </div>`
            )
            .join('')}
        </div>`;

    case 'text':
    default:
      return `
        ${slide.title ? `<h1>${escapeHtml(slide.title)}</h1>` : ''}
        ${slide.subtitle ? `<h2>${escapeHtml(slide.subtitle)}</h2>` : ''}
        ${slide.body ? `<p>${escapeHtml(slide.body)}</p>` : ''}`;
  }
}

function statusClass(status) {
  return ['ok', 'warn', 'danger'].includes(status) ? status : '';
}

/* ------------------------------------------------------------------ */
/* Loop de rotação                                                     */
/* ------------------------------------------------------------------ */

function tick(now) {
  if (!state.paused && state.slideDuration > 0) {
    const elapsed = now - state.slideStartedAt;
    const ratio = Math.min(elapsed / state.slideDuration, 1);
    el.progressBar.style.width = `${ratio * 100}%`;
    if (ratio >= 1) goTo(state.index + 1);
  }
  requestAnimationFrame(tick);
}

function goTo(index) {
  const total = state.config.slides.length;
  state.index = ((index % total) + total) % total;
  el.slide.classList.remove('visible');
  render();
  broadcastState();
}

/* ------------------------------------------------------------------ */
/* Relógio do overlay                                                  */
/* ------------------------------------------------------------------ */

function updateClock() {
  const now = new Date();
  el.time.textContent = now.toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  });
  el.date.textContent = now.toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
  });
}

/* ------------------------------------------------------------------ */
/* Integração com o Cast                                               */
/* ------------------------------------------------------------------ */

function setupCast() {
  if (typeof cast === 'undefined' || !cast.framework) {
    console.info('[receiver] rodando fora do Cast (modo preview)');
    return;
  }

  const context = cast.framework.CastReceiverContext.getInstance();

  context.addCustomMessageListener(NAMESPACE, (event) => {
    handleCommand(event.data, event.senderId);
  });

  context.addEventListener(cast.framework.system.EventType.SENDER_CONNECTED, () => {
    broadcastState();
  });

  const options = new cast.framework.CastReceiverOptions();
  // Painel de sinalização não toca mídia: sem isso o Chromecast encerra o
  // app por inatividade e a TV volta para o ambiente de fundo.
  options.disableIdleTimeout = true;
  options.customNamespaces = { [NAMESPACE]: cast.framework.system.MessageType.JSON };

  context.start(options);
}

/** Trata um comando vindo do sender. */
function handleCommand(message, senderId) {
  if (!message || typeof message !== 'object') return;
  console.debug('[receiver] comando', message.type, 'de', senderId);

  switch (message.type) {
    case 'SET_CONFIG':
      applyConfig(message.config);
      break;
    case 'SET_SOURCE':
      if (message.url) loadSource(message.url);
      break;
    case 'RELOAD':
      loadSource(state.source);
      break;
    case 'NEXT':
      goTo(state.index + 1);
      break;
    case 'PREV':
      goTo(state.index - 1);
      break;
    case 'GO_TO':
      goTo(Number(message.index) || 0);
      break;
    case 'PAUSE':
      setPaused(true);
      break;
    case 'RESUME':
      setPaused(false);
      break;
    case 'TOGGLE':
      setPaused(!state.paused);
      break;
    case 'PING':
      broadcastState();
      break;
    default:
      console.warn('[receiver] comando desconhecido:', message.type);
  }
}

function setPaused(paused) {
  if (paused === state.paused) return;
  state.paused = paused;
  // Ao retomar, mantém o tempo já decorrido do slide atual.
  if (!paused) {
    const elapsed =
      (parseFloat(el.progressBar.style.width) / 100) * state.slideDuration || 0;
    state.slideStartedAt = performance.now() - elapsed;
  }
  broadcastState();
}

/** Informa os controles conectados sobre o estado atual do painel. */
function broadcastState() {
  if (typeof cast === 'undefined' || !cast.framework) return;
  const slide = currentSlide();
  const payload = {
    type: 'STATE',
    index: state.index,
    total: state.config.slides.length,
    paused: state.paused,
    title: slide ? slide.title || slide.caption || slide.type : '',
    brand: state.config.brand || '',
    source: state.source,
  };
  try {
    cast.framework.CastReceiverContext.getInstance()
      // senderId undefined = transmite para todos os senders conectados.
      .sendCustomMessage(NAMESPACE, undefined, payload);
  } catch (error) {
    console.warn('[receiver] não foi possível enviar o estado', error);
  }
}

/* ------------------------------------------------------------------ */
/* Utilitários                                                         */
/* ------------------------------------------------------------------ */

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** Impede que uma URL feche o `url("…")` do CSS. */
function cssUrl(value) {
  return String(value ?? '').replace(/["\\]/g, '');
}

let statusTimer = null;
function showStatus(text, autoHideMs) {
  el.status.textContent = text;
  el.status.classList.remove('hidden');
  clearTimeout(statusTimer);
  if (autoHideMs) statusTimer = setTimeout(hideStatus, autoHideMs);
}

function hideStatus() {
  clearTimeout(statusTimer);
  el.status.classList.add('hidden');
}

/* ------------------------------------------------------------------ */
/* Início                                                              */
/* ------------------------------------------------------------------ */

// Permite testar outro conteúdo no navegador: receiver/?src=../content/outro.json
const sourceFromQuery = new URLSearchParams(location.search).get('src');

setupCast();
updateClock();
setInterval(updateClock, 1000);
loadSource(sourceFromQuery || DEFAULT_SOURCE);
requestAnimationFrame(tick);
