const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_ROWS = 200;
const MAX_COLUMNS = 50;
const MAX_SUMMARY_ITEMS = 30;
const MAX_RENDERED_CHARS = 2000;

export function isHttpsUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash;
  } catch {
    return false;
  }
}

export function isValidBookId(value) {
  return UUID_PATTERN.test(String(value || '').trim());
}

function formatCell(value) {
  if (value === null || value === undefined) return '—';
  let text;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') text = String(value);
  else {
    try { text = JSON.stringify(value); } catch { text = '[Dato no disponible]'; }
  }
  return text.slice(0, MAX_RENDERED_CHARS);
}

function setEmptyResults(container, doc = document) {
  container.replaceChildren();
  const empty = doc.createElement('div');
  empty.className = 'empty-results';
  const icon = doc.createElement('span');
  icon.className = 'empty-table-icon';
  icon.setAttribute('aria-hidden', 'true');
  icon.textContent = '▦';
  const text = doc.createElement('p');
  text.textContent = 'No se recibieron datos estructurados para esta consulta.';
  empty.append(icon, text);
  container.append(empty);
}

export function renderQueryResult(container, result, doc = document) {
  if (!container || typeof container.replaceChildren !== 'function') throw new TypeError('Se requiere un contenedor DOM.');
  const columns = result && Array.isArray(result.rows) && result.rows.length
    ? Object.keys(result.rows[0] || {}).filter((key) => key.length > 0).slice(0, MAX_COLUMNS).map((key) => ({ key, label: key }))
    : [];
  if (!columns.length) {
    setEmptyResults(container, doc);
    return false;
  }

  const safeResult = { caption: 'Datos estructurados de la consulta', columns, rows: result.rows.slice(0, MAX_ROWS) };
  container.replaceChildren();
  const scroll = doc.createElement('div');
  scroll.className = 'table-scroll';
  const table = doc.createElement('table');
  table.className = 'result-table';
  const caption = doc.createElement('caption');
  caption.textContent = safeResult.caption;
  const head = doc.createElement('thead');
  const headerRow = doc.createElement('tr');
  for (const column of safeResult.columns) {
    const th = doc.createElement('th');
    th.scope = 'col';
    th.textContent = column.label;
    headerRow.append(th);
  }
  head.append(headerRow);
  const body = doc.createElement('tbody');
  for (const row of safeResult.rows) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) continue;
    const tr = doc.createElement('tr');
    for (const column of safeResult.columns) {
      const td = doc.createElement('td');
      td.textContent = formatCell(row[column.key]);
      tr.append(td);
    }
    body.append(tr);
  }
  table.append(caption, head, body);
  scroll.append(table);
  container.append(scroll);
  return true;
}

function renderSummary(container, payload, doc = document) {
  const entries = payload && payload.summary && typeof payload.summary === 'object' && !Array.isArray(payload.summary)
    ? Object.entries(payload.summary).slice(0, MAX_SUMMARY_ITEMS)
    : [];
  if (!entries.length && !payload?.period && !payload?.currency) return;

  const section = doc.createElement('section');
  section.className = 'result-summary';
  const heading = doc.createElement('h3');
  heading.textContent = 'Resumen autorizado';
  const list = doc.createElement('dl');
  const metadata = [];
  if (payload.period && typeof payload.period === 'object') metadata.push(['Periodo', `${formatCell(payload.period.dateFrom)} – ${formatCell(payload.period.dateTo)}`]);
  if (typeof payload.currency === 'string') metadata.push(['Moneda', payload.currency]);
  for (const [key, value] of entries) metadata.push([key, formatCell(value)]);
  for (const [key, value] of metadata.slice(0, MAX_SUMMARY_ITEMS)) {
    const term = doc.createElement('dt');
    const description = doc.createElement('dd');
    term.textContent = key.slice(0, 100);
    description.textContent = value;
    list.append(term, description);
  }
  section.append(heading, list);
  container.append(section);
}

function serviceMessage(payload, fallback) {
  return payload && typeof payload.message === 'string' ? payload.message.slice(0, 500) : fallback;
}

export function initWeb({ auth, config, fetchImpl = window.fetch.bind(window), doc = document } = {}) {
  const conversation = doc.getElementById('conversation');
  const form = doc.getElementById('composer-form');
  const input = doc.getElementById('message-input');
  const results = doc.getElementById('query-results');
  const validation = doc.getElementById('context-validation');
  const welcomeCard = doc.getElementById('welcome-card');
  const connectionLabel = doc.getElementById('connection-label');
  const signInForm = doc.getElementById('sign-in-form');
  const emailInput = doc.getElementById('email-input');
  const passwordInput = doc.getElementById('password-input');
  const signInButton = doc.getElementById('sign-in-button');
  const signOutButton = doc.getElementById('sign-out-button');
  const sessionLabel = doc.getElementById('session-label');
  const bookSelect = doc.getElementById('book-id');
  const loadMoreBooks = doc.getElementById('load-more-books');
  const sendButton = doc.getElementById('send-button');

  const readyConfig = Boolean(auth && isHttpsUrl(config?.supabaseUrl) && config?.supabasePublishableKey
    && isHttpsUrl(config?.chatServiceBaseUrl));
  let accessToken = '';
  let busy = false;
  let bookLoading = false;
  let nextBooksCursor = null;
  let booksRequestId = 0;
  connectionLabel.textContent = readyConfig ? 'Servicio no verificado' : 'Configuración pendiente';

  const updateAvailability = () => {
    sendButton.disabled = busy || bookLoading || !readyConfig || !accessToken || !isValidBookId(bookSelect.value);
    signInButton.disabled = busy || !readyConfig || Boolean(accessToken);
    signOutButton.disabled = busy || !accessToken;
    bookSelect.disabled = !accessToken || bookLoading || bookSelect.options.length <= 1;
    loadMoreBooks.disabled = busy || bookLoading || !accessToken;
  };

  function addMessage(text, kind = 'assistant') {
    const message = doc.createElement('div');
    message.className = `message${kind === 'notice' ? ' notice' : kind === 'assistant' ? ' assistant-message' : ''}`;
    message.textContent = text;
    conversation.append(message);
    message.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }

  function clearResults() { setEmptyResults(results, doc); }

  function resetBookPicker(message = 'Inicia sesión para cargar tus libros.') {
    bookSelect.replaceChildren();
    const placeholder = doc.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Selecciona un libro';
    bookSelect.append(placeholder);
    nextBooksCursor = null;
    loadMoreBooks.hidden = true;
    loadMoreBooks.textContent = 'Cargar más libros';
    if (message) validation.textContent = message;
  }

  function clearSession(message) {
    accessToken = '';
    booksRequestId += 1;
    bookLoading = false;
    resetBookPicker(message || 'Inicia sesión para cargar tus libros.');
    sessionLabel.textContent = 'Sesión no iniciada';
    connectionLabel.textContent = readyConfig ? 'Sesión requerida' : 'Configuración pendiente';
    updateAvailability();
  }

  async function loadBooks(token, cursor = null, append = false) {
    if (!token || !readyConfig) return;
    const requestId = ++booksRequestId;
    if (!append) {
      resetBookPicker('Cargando los libros disponibles…');
    }
    bookLoading = true;
    updateAvailability();
    const params = new URLSearchParams({ pageSize: '100' });
    if (cursor) params.set('cursor', cursor);
    try {
      const response = await fetchImpl(`${config.chatServiceBaseUrl.replace(/\/$/, '')}/v1/books?${params}`, {
        method: 'GET', mode: 'cors', credentials: 'omit', redirect: 'error', cache: 'no-store',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      });
      let payload = {};
      try { payload = await response.json(); } catch { /* use safe local fallback */ }
      if (requestId !== booksRequestId) return;
      if (response.status === 401) {
        await auth.signOut({ scope: 'local' }).catch(() => {});
        clearSession('La sesión venció o ya no es válida. Vuelve a iniciar sesión para cargar tus libros.');
        return;
      }
      if (response.status === 403) {
        resetBookPicker('El servicio denegó el acceso a la lista de libros.');
        return;
      }
      if (!response.ok) {
        resetBookPicker(serviceMessage(payload, `No se pudieron cargar los libros (HTTP ${response.status}).`));
        return;
      }
      if (!Array.isArray(payload.items) || (payload.nextCursor !== null && typeof payload.nextCursor !== 'string')) throw new Error('invalid_book_list');
      const validItems = payload.items.filter((book) => book && isValidBookId(book.id)
        && typeof book.name === 'string' && book.name.trim().length > 0 && book.name.length <= 200);
      if (validItems.length !== payload.items.length) throw new Error('invalid_book_list');
      const priorSelection = bookSelect.value;
      for (const book of validItems) {
        const option = doc.createElement('option');
        option.value = book.id;
        option.textContent = book.name;
        bookSelect.append(option);
      }
      if (priorSelection && Array.from(bookSelect.options).some((option) => option.value === priorSelection)) bookSelect.value = priorSelection;
      nextBooksCursor = payload.nextCursor;
      loadMoreBooks.hidden = !nextBooksCursor;
      if (bookSelect.options.length <= 1) {
        resetBookPicker('No hay libros disponibles para esta cuenta. Pide acceso a un libro o verifica la configuración de acceso.');
      } else if (append) {
        validation.textContent = 'Se cargaron más libros disponibles. Selecciona uno para habilitar el chat.';
      } else {
        validation.textContent = 'Libros cargados. Selecciona un libro para habilitar el chat.';
      }
    } catch (error) {
      if (requestId !== booksRequestId) return;
      resetBookPicker(error?.message === 'invalid_book_list'
        ? 'El servicio devolvió una lista de libros con formato inválido.'
        : 'No se pudieron cargar los libros desde el servicio HTTPS. Comprueba la configuración y la conexión.');
    } finally {
      if (requestId === booksRequestId) {
        bookLoading = false;
        updateAvailability();
      }
    }
  }

  if (!readyConfig) {
    validation.textContent = 'Configura VITE_SUPABASE_URL, VITE_SUPABASE_PUBLISHABLE_KEY y VITE_CHAT_SERVICE_BASE_URL con orígenes HTTPS antes de iniciar sesión.';
  }
  if (!auth) {
    signInForm.hidden = true;
    sessionLabel.textContent = 'Configuración pendiente';
  }
  resetBookPicker(readyConfig ? 'Inicia sesión para cargar tus libros.' : 'Configura VITE_SUPABASE_URL, VITE_SUPABASE_PUBLISHABLE_KEY y VITE_CHAT_SERVICE_BASE_URL con orígenes HTTPS antes de iniciar sesión.');

  signInForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!readyConfig || busy || accessToken) return;
    const email = emailInput.value.trim();
    const password = passwordInput.value;
    passwordInput.value = '';
    if (!email || !password) return;
    busy = true;
    validation.textContent = 'Iniciando sesión…';
    updateAvailability();
    try {
      const { data, error } = await auth.signInWithPassword({ email, password });
      if (error || !data?.session?.access_token) {
        clearSession('No se pudo iniciar sesión. Verifica las credenciales y que el proveedor de acceso esté habilitado.');
        return;
      }
      accessToken = data.session.access_token;
      sessionLabel.textContent = 'Sesión iniciada';
      connectionLabel.textContent = 'Sesión lista';
      await loadBooks(accessToken);
    } catch {
      clearSession('No se pudo contactar el proveedor de autenticación. Intenta de nuevo más tarde.');
    } finally {
      passwordInput.value = '';
      busy = false;
      updateAvailability();
    }
  });

  signOutButton.addEventListener('click', async () => {
    if (busy) return;
    busy = true;
    updateAvailability();
    try { await auth?.signOut({ scope: 'local' }); } catch { /* clear in-memory credentials regardless */ }
    clearSession('Sesión cerrada.');
    busy = false;
    updateAvailability();
  });

  bookSelect.addEventListener('change', updateAvailability);
  loadMoreBooks.addEventListener('click', async () => {
    if (!nextBooksCursor || !accessToken || bookLoading) return;
    await loadBooks(accessToken, nextBooksCursor, true);
  });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const question = input.value.trim();
    const bookId = bookSelect.value;
    if (!question || busy || bookLoading || !readyConfig || !accessToken) return;
    if (!isValidBookId(bookId)) {
      validation.textContent = 'Selecciona un libro disponible antes de consultar.';
      bookSelect.focus();
      updateAvailability();
      return;
    }
    if (welcomeCard?.isConnected) welcomeCard.remove();
    addMessage(question, 'user');
    input.value = '';
    busy = true;
    connectionLabel.textContent = 'Consultando…';
    validation.textContent = 'El servidor verificará el acceso al libro antes de enviar la pregunta al modelo.';
    updateAvailability();
    try {
      const response = await fetchImpl(`${config.chatServiceBaseUrl.replace(/\/$/, '')}/v1/chat`, {
        method: 'POST', mode: 'cors', credentials: 'omit', redirect: 'error', cache: 'no-store',
        headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ bookId, message: question }),
      });
      let payload = {};
      try { payload = await response.json(); } catch { /* use typed fallback below */ }
      if (response.status === 401) {
        await auth.signOut({ scope: 'local' }).catch(() => {});
        clearSession('La sesión venció o ya no es válida. Se cerró la sesión; vuelve a iniciar sesión para continuar.');
        addMessage('La sesión venció o fue rechazada. Inicia sesión de nuevo antes de consultar.', 'notice');
        return;
      }
      if (response.status === 403) {
        connectionLabel.textContent = 'Acceso denegado';
        validation.textContent = 'El servidor denegó el acceso a este libro o a sus datos.';
        addMessage('No tienes acceso al libro o a los datos solicitados.', 'notice');
        return;
      }
      if (!response.ok) {
        const message = serviceMessage(payload, `La consulta falló (HTTP ${response.status}).`);
        validation.textContent = message;
        connectionLabel.textContent = 'Error de servicio';
        addMessage(message, 'notice');
        return;
      }
      if (typeof payload.text !== 'string') throw new Error('invalid_response');
      connectionLabel.textContent = 'Respuesta recibida';
      validation.textContent = 'Consulta completada. El ID de libro seleccionado se autorizó mediante el API contable; no se deriva del token.';
      addMessage(payload.text.slice(0, 800), 'assistant');
      renderQueryResult(results, payload, doc);
      renderSummary(results, payload, doc);
    } catch (error) {
      const message = error?.message === 'invalid_response'
        ? 'El servicio devolvió una respuesta con formato inválido.'
        : 'No se pudo conectar con el servicio HTTPS. Comprueba la configuración y la conexión.';
      connectionLabel.textContent = 'Servicio no disponible';
      validation.textContent = message;
      addMessage(message, 'notice');
    } finally {
      busy = false;
      updateAvailability();
      input.focus();
    }
  });

  doc.getElementById('clear-chat').addEventListener('click', () => {
    if (welcomeCard) conversation.replaceChildren(welcomeCard.cloneNode(true));
    else conversation.replaceChildren();
    clearResults();
    validation.textContent = readyConfig ? '' : validation.textContent;
    input.value = '';
    input.focus();
  });

  conversation.addEventListener('click', (event) => {
    const button = event.target.closest('[data-prompt]');
    if (!button) return;
    input.value = button.dataset.prompt;
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  });

  if (auth && readyConfig) {
    auth.getSession().then(({ data, error }) => {
      if (error) return;
      const token = data?.session?.access_token;
      if (token) {
        accessToken = token;
        sessionLabel.textContent = 'Sesión iniciada';
        connectionLabel.textContent = 'Sesión lista';
        void loadBooks(token);
      }
      updateAvailability();
    }).catch(() => {});
    auth.onAuthStateChange((_event, session) => {
      const token = session?.access_token || '';
      if (!token) {
        if (accessToken) clearSession('La sesión terminó. Inicia sesión para cargar tus libros.');
        return;
      }
      if (accessToken === token) return;
      accessToken = token;
      sessionLabel.textContent = 'Sesión iniciada';
      connectionLabel.textContent = 'Sesión lista';
      void loadBooks(token);
      updateAvailability();
    });
  }

  updateAvailability();
  return Object.freeze({ updateAvailability, clearSession });
}
