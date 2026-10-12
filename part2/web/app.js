(() => {
  'use strict';

  const conversation = document.getElementById('conversation');
  const form = document.getElementById('composer-form');
  const input = document.getElementById('message-input');
  const results = document.getElementById('query-results');
  const validation = document.getElementById('context-validation');
  const welcomeCard = document.getElementById('welcome-card');
  const connectionLabel = document.getElementById('connection-label');

  // This shell deliberately has no API adapter, endpoint, credentials, or network calls.
  connectionLabel.textContent = 'Backend pendiente';

  function addMessage(text, kind) {
    const message = document.createElement('div');
    message.className = `message${kind === 'notice' ? ' notice' : ''}`;
    message.textContent = text;
    conversation.append(message);
    message.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function clearResults() {
    results.replaceChildren();
    const empty = document.createElement('div');
    empty.className = 'empty-results';
    const icon = document.createElement('span');
    icon.className = 'empty-table-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = '▦';
    const text = document.createElement('p');
    text.textContent = 'Los resultados aparecerán aquí cuando exista una respuesta válida del servicio.';
    empty.append(icon, text);
    results.append(empty);
  }

  function formatCell(value) {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
    try {
      return JSON.stringify(value);
    } catch (error) {
      return '[Dato no disponible]';
    }
  }

  /**
   * Safe table renderer for future read-only API responses.
   * Expected shape: { caption?: string, columns: [{ key: string, label: string }], rows: object[] }.
   * All server-provided values are assigned as text; HTML is never interpreted.
   */
  function renderQueryResult(container, result) {
    if (!(container instanceof Element)) throw new TypeError('Se requiere un contenedor DOM.');
    container.replaceChildren();

    const valid = result && Array.isArray(result.columns) && Array.isArray(result.rows)
      && result.columns.length > 0 && result.columns.length <= 50 && result.rows.length <= 500
      && result.columns.every((column) => column && typeof column.key === 'string' && column.key.length > 0);

    if (!valid) {
      const error = document.createElement('p');
      error.className = 'result-error';
      error.setAttribute('role', 'status');
      error.textContent = 'No se pudo mostrar el resultado: el formato de tabla no es válido.';
      container.append(error);
      return false;
    }

    const scroll = document.createElement('div');
    scroll.className = 'table-scroll';
    const table = document.createElement('table');
    table.className = 'result-table';
    const caption = document.createElement('caption');
    caption.textContent = typeof result.caption === 'string' ? result.caption : 'Resultado de consulta';
    const head = document.createElement('thead');
    const headerRow = document.createElement('tr');

    for (const column of result.columns) {
      const th = document.createElement('th');
      th.scope = 'col';
      th.textContent = typeof column.label === 'string' ? column.label : column.key;
      headerRow.append(th);
    }
    head.append(headerRow);
    const body = document.createElement('tbody');
    for (const row of result.rows) {
      const tr = document.createElement('tr');
      for (const column of result.columns) {
        const td = document.createElement('td');
        td.textContent = formatCell(row && typeof row === 'object' ? row[column.key] : undefined);
        tr.append(td);
      }
      body.append(tr);
    }
    if (result.rows.length === 0) {
      const tr = document.createElement('tr');
      const td = document.createElement('td');
      td.colSpan = result.columns.length;
      td.textContent = 'La consulta no devolvió filas.';
      tr.append(td);
      body.append(tr);
    }
    table.append(caption, head, body);
    scroll.append(table);
    container.append(scroll);
    return true;
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const question = input.value.trim();
    if (!question) return;

    const from = document.getElementById('date-from').value;
    const to = document.getElementById('date-to').value;
    if (from && to && from > to) {
      validation.textContent = 'La fecha inicial debe ser anterior o igual a la fecha final.';
      document.getElementById('date-from').focus();
      return;
    }
    validation.textContent = '';
    if (welcomeCard) welcomeCard.remove();
    addMessage(question, 'user');
    addMessage('Consulta no enviada. El servicio contable aún no está conectado. El texto permanece solo en esta pestaña y no se guarda; no se han consultado datos.', 'notice');
    input.value = '';
    input.focus();
  });

  document.getElementById('clear-chat').addEventListener('click', () => {
    conversation.replaceChildren(welcomeCard ? welcomeCard.cloneNode(true) : document.createElement('div'));
    clearResults();
    validation.textContent = '';
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

  document.getElementById('date-from').addEventListener('change', () => { validation.textContent = ''; });
  document.getElementById('date-to').addEventListener('change', () => { validation.textContent = ''; });

  window.Book01Web = Object.freeze({ renderQueryResult });
})();
