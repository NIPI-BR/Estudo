/**
 * Sender do painel de sinalização.
 *
 * Página web (Chrome no desktop) que conecta no Chromecast, sobe o receiver
 * e manda comandos por mensagem custom. O SDK do Cast só existe no Chrome/Edge
 * desktop — em celular seria necessário um app Android/iOS.
 */

const ui = {
  connection: document.getElementById('connection'),
  nowPlaying: document.getElementById('now-playing'),
  toggle: document.getElementById('toggle'),
  sourceUrl: document.getElementById('source-url'),
  setSource: document.getElementById('set-source'),
  configJson: document.getElementById('config-json'),
  sendConfig: document.getElementById('send-config'),
  loadExample: document.getElementById('load-example'),
  jsonError: document.getElementById('json-error'),
  appId: document.getElementById('app-id'),
  commandButtons: Array.from(document.querySelectorAll('[data-command]')),
};

let castSession = null;

/* ------------------------------------------------------------------ */
/* Inicialização do SDK                                                */
/* ------------------------------------------------------------------ */

// O SDK do Cast chama esta função assim que carrega.
window.__onGCastApiAvailable = function (isAvailable) {
  if (!isAvailable) {
    setConnection('Cast indisponível neste navegador', false);
    return;
  }
  initializeCastApi();
};

function initializeCastApi() {
  const context = cast.framework.CastContext.getInstance();

  context.setOptions({
    receiverApplicationId: CAST_APP_ID,
    // ORIGIN_SCOPED: reconecta sozinho a uma sessão iniciada por esta origem.
    autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED,
  });

  context.addEventListener(
    cast.framework.CastContextEventType.SESSION_STATE_CHANGED,
    (event) => {
      const States = cast.framework.SessionState;
      switch (event.sessionState) {
        case States.SESSION_STARTED:
        case States.SESSION_RESUMED:
          attachSession(context.getCurrentSession());
          break;
        case States.SESSION_ENDED:
          detachSession();
          break;
      }
    }
  );

  // Se a página recarregou com uma sessão viva, reaproveita.
  const existing = context.getCurrentSession();
  if (existing) attachSession(existing);
}

function attachSession(session) {
  castSession = session;
  setConnection(`Conectado a ${session.getCastDevice().friendlyName}`, true);

  session.addMessageListener(CAST_NAMESPACE, (namespace, message) => {
    // As mensagens chegam como string, mesmo quando o receiver envia objeto.
    try {
      handleReceiverMessage(typeof message === 'string' ? JSON.parse(message) : message);
    } catch (error) {
      console.warn('[sender] mensagem inválida do receiver', message, error);
    }
  });

  send({ type: 'PING' });
}

function detachSession() {
  castSession = null;
  setConnection('Desconectado', false);
  ui.nowPlaying.textContent = '—';
}

/* ------------------------------------------------------------------ */
/* Envio e recebimento de mensagens                                    */
/* ------------------------------------------------------------------ */

function send(message) {
  if (!castSession) {
    setConnection('Conecte a uma TV primeiro (ícone de Cast)', false);
    return Promise.resolve(false);
  }
  return castSession
    .sendMessage(CAST_NAMESPACE, message)
    .then(() => true)
    .catch((error) => {
      console.error('[sender] falha ao enviar', message, error);
      setConnection(`Erro ao enviar: ${error.description || error}`, false);
      return false;
    });
}

function handleReceiverMessage(message) {
  if (!message || message.type !== 'STATE') return;

  const position = `${message.index + 1}/${message.total}`;
  ui.nowPlaying.textContent = `${position} — ${message.title || 'slide'}${
    message.paused ? ' (pausado)' : ''
  }`;
  ui.toggle.textContent = message.paused ? '▶ Retomar' : '⏸ Pausar';

  if (message.source && !ui.sourceUrl.value) ui.sourceUrl.value = message.source;
}

/* ------------------------------------------------------------------ */
/* Interface                                                           */
/* ------------------------------------------------------------------ */

function setConnection(text, connected) {
  ui.connection.textContent = text;
  ui.connection.className = `badge ${connected ? 'on' : 'off'}`;
  ui.commandButtons.forEach((button) => {
    button.disabled = !connected;
  });
}

ui.commandButtons.forEach((button) => {
  button.addEventListener('click', () => send({ type: button.dataset.command }));
});

ui.setSource.addEventListener('click', () => {
  const url = ui.sourceUrl.value.trim();
  if (!url) return;
  send({ type: 'SET_SOURCE', url });
});

ui.sendConfig.addEventListener('click', () => {
  ui.jsonError.textContent = '';
  let config;
  try {
    config = JSON.parse(ui.configJson.value);
  } catch (error) {
    ui.jsonError.textContent = `JSON inválido: ${error.message}`;
    return;
  }
  if (!Array.isArray(config.slides) || config.slides.length === 0) {
    ui.jsonError.textContent = 'A configuração precisa de uma lista "slides" não vazia.';
    return;
  }
  send({ type: 'SET_CONFIG', config }).then((ok) => {
    if (ok) ui.jsonError.textContent = '';
  });
});

ui.loadExample.addEventListener('click', loadExample);

async function loadExample() {
  try {
    const response = await fetch('../content/slides.json', { cache: 'no-store' });
    ui.configJson.value = JSON.stringify(await response.json(), null, 2);
  } catch (error) {
    ui.jsonError.textContent = `Não foi possível carregar o exemplo: ${error.message}`;
  }
}

ui.appId.textContent = CAST_APP_ID;
setConnection('Desconectado', false);
loadExample();
