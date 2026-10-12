import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { JSDOM } from 'jsdom';
import { initWeb, isHttpsUrl, isValidBookId } from '../src/app.js';

const doms = [];
const BOOK_ID = '10000000-0000-4000-8000-000000000001';
const SECOND_BOOK_ID = '10000000-0000-4000-8000-000000000002';
const CONFIG = {
  supabaseUrl: 'https://project.example.test',
  supabasePublishableKey: 'sb_publishable_test_value',
  chatServiceBaseUrl: 'https://chat.example.test',
};

afterEach(() => {
  while (doms.length) doms.pop().window.close();
});

function makeDocument() {
  const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://web.example.test' });
  doms.push(dom);
  const doc = dom.window.document;
  const add = (tag, id, parent = doc.body) => {
    const element = doc.createElement(tag);
    element.id = id;
    parent.append(element);
    return element;
  };
  const signIn = add('form', 'sign-in-form');
  add('input', 'email-input', signIn);
  add('input', 'password-input', signIn);
  add('button', 'sign-in-button', signIn);
  add('button', 'sign-out-button');
  add('span', 'session-label');
  add('select', 'book-id');
  add('button', 'load-more-books');
  add('span', 'connection-label');
  add('p', 'context-validation');
  add('article', 'welcome-card', add('div', 'conversation'));
  const form = add('form', 'composer-form');
  add('textarea', 'message-input', form);
  add('button', 'send-button', form);
  add('button', 'clear-chat');
  add('div', 'query-results');
  return { dom, doc };
}

function makeAuth() {
  const state = { signInCalls: [], signOutCalls: [], stateListener: null };
  const auth = {
    async getSession() { return { data: { session: null }, error: null }; },
    onAuthStateChange(listener) {
      state.stateListener = listener;
      return { data: { subscription: { unsubscribe() {} } } };
    },
    async signInWithPassword(credentials) {
      state.signInCalls.push(credentials);
      return { data: { session: { access_token: 'memory-only-token' } }, error: null };
    },
    async signOut(options) { state.signOutCalls.push(options); return { error: null }; },
  };
  return { auth, state };
}

function submit(dom, element) {
  element.dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true }));
}

const turn = () => new Promise((resolve) => setImmediate(resolve));
const bookList = { items: [{ id: BOOK_ID, name: '<img src=x onerror=alert(1)> Main book' }], nextCursor: null };

test('HTTPS and RFC UUID validation remain strict', () => {
  assert.equal(isHttpsUrl('https://chat.example.test'), true);
  assert.equal(isHttpsUrl('http://localhost:8000'), false);
  assert.equal(isHttpsUrl('https://user:pass@chat.example.test'), false);
  assert.equal(isHttpsUrl('https://chat.example.test?debug=1'), false);
  assert.equal(isValidBookId(BOOK_ID), true);
  assert.equal(isValidBookId('not-a-uuid'), false);
  assert.equal(isValidBookId('00000000-0000-0000-0000-000000000000'), false);
});

test('sign-in loads RLS-visible books, selecting one enables chat, and untrusted names/results render as text', async () => {
  const { dom, doc } = makeDocument();
  const { auth, state } = makeAuth();
  const requests = [];
  const fetchImpl = async (url, options) => {
    requests.push({ url, options });
    if (options.method === 'GET') return { ok: true, status: 200, async json() { return bookList; } };
    return {
      ok: true, status: 200,
      async json() {
        return {
          text: '<img src=x onerror=alert(1)> safe assistant text',
          period: { dateFrom: '2025-01-01', dateTo: '2025-01-31' },
          currency: 'USD', rows: [{ name: '<script>row</script>', amount: '12.00' }], summary: { totals: { balanced: true } },
        };
      },
    };
  };
  initWeb({ auth, config: CONFIG, fetchImpl, doc });
  const send = doc.getElementById('send-button');
  assert.equal(send.disabled, true);
  assert.equal(doc.getElementById('book-id').tagName, 'SELECT');
  assert.equal(doc.querySelector('#book-id input'), null);

  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'never-store-this';
  submit(dom, doc.getElementById('sign-in-form'));
  assert.equal(doc.getElementById('password-input').value, '', 'password clears immediately');
  await turn(); await turn();
  assert.equal(state.signInCalls.length, 1);
  assert.deepEqual(state.signInCalls[0], { email: 'owner@example.test', password: 'never-store-this' });
  assert.equal(requests[0].url, 'https://chat.example.test/v1/books?pageSize=100');
  assert.equal(requests[0].options.method, 'GET');
  assert.equal(requests[0].options.credentials, 'omit');
  assert.equal(requests[0].options.cache, 'no-store');
  assert.equal(requests[0].options.headers.Authorization, 'Bearer memory-only-token');
  assert.equal(doc.getElementById('book-id').options[1].value, BOOK_ID);
  assert.match(doc.getElementById('book-id').options[1].textContent, /<img src=x onerror=alert\(1\)> Main book/);
  assert.equal(doc.querySelector('#book-id img'), null);
  assert.equal(send.disabled, true, 'a book must be selected');
  assert.equal(dom.window.localStorage.length, 0);
  assert.equal(dom.window.sessionStorage.length, 0);

  const book = doc.getElementById('book-id');
  book.value = BOOK_ID;
  book.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
  assert.equal(send.disabled, false);
  doc.getElementById('message-input').value = 'Consulta el balance';
  submit(dom, doc.getElementById('composer-form'));
  await turn(); await turn();

  assert.equal(requests.length, 2);
  assert.equal(requests[1].url, 'https://chat.example.test/v1/chat');
  assert.equal(requests[1].options.method, 'POST');
  assert.equal(requests[1].options.credentials, 'omit');
  assert.equal(requests[1].options.redirect, 'error');
  assert.equal(requests[1].options.headers.Authorization, 'Bearer memory-only-token');
  assert.deepEqual(JSON.parse(requests[1].options.body), { bookId: BOOK_ID, message: 'Consulta el balance' });
  assert.equal('dateFrom' in JSON.parse(requests[1].options.body), false);
  assert.equal(doc.querySelector('#query-results img, #query-results script'), null);
  assert.match(doc.getElementById('conversation').textContent, /<img src=x onerror=alert\(1\)> safe assistant text/);
  assert.match(doc.getElementById('query-results').textContent, /<script>row<\/script>/);
  assert.match(doc.getElementById('query-results').textContent, /balanced/);
  assert.equal(state.signOutCalls.length, 0);
});

test('empty book list disables chat and presents an explicit no-books state', async () => {
  const { dom, doc } = makeDocument();
  const { auth } = makeAuth();
  initWeb({ auth, config: CONFIG, doc, fetchImpl: async () => ({ ok: true, status: 200, async json() { return { items: [], nextCursor: null }; } }) });
  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'password';
  submit(dom, doc.getElementById('sign-in-form'));
  await turn(); await turn();
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.match(doc.getElementById('context-validation').textContent, /No hay libros disponibles/);
});

test('book-list 401 clears the in-memory session and authorized book options', async () => {
  const { dom, doc } = makeDocument();
  const { auth, state } = makeAuth();
  initWeb({ auth, config: CONFIG, doc, fetchImpl: async () => ({ ok: false, status: 401, async json() { return { code: 'authentication_required' }; } }) });
  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'password';
  submit(dom, doc.getElementById('sign-in-form'));
  await turn(); await turn();
  assert.deepEqual(state.signOutCalls, [{ scope: 'local' }]);
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.equal(doc.getElementById('book-id').options.length, 1);
  assert.match(doc.getElementById('context-validation').textContent, /sesión venció/);
});

test('chat 401 clears the in-memory session', async () => {
  const { dom, doc } = makeDocument();
  const { auth, state } = makeAuth();
  let called = 0;
  initWeb({ auth, config: CONFIG, doc, fetchImpl: async (_url, options) => {
    called += 1;
    if (options.method === 'GET') return { ok: true, status: 200, async json() { return bookList; } };
    return { ok: false, status: 401, async json() { return { code: 'authentication_required' }; } };
  } });
  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'password';
  submit(dom, doc.getElementById('sign-in-form'));
  await turn(); await turn();
  const book = doc.getElementById('book-id');
  book.value = BOOK_ID;
  book.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
  doc.getElementById('message-input').value = 'Consulta';
  submit(dom, doc.getElementById('composer-form'));
  await turn(); await turn();
  assert.equal(called, 2);
  assert.deepEqual(state.signOutCalls, [{ scope: 'local' }]);
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.equal(doc.getElementById('book-id').options.length, 1);
  assert.match(doc.getElementById('context-validation').textContent, /sesión venció|válida/);
});

test('load-more pagination appends choices and sends the selected opaque book id', async () => {
  const { dom, doc } = makeDocument();
  const { auth } = makeAuth();
  const requests = [];
  initWeb({ auth, config: CONFIG, doc, fetchImpl: async (url, options) => {
    requests.push({ url, options });
    return { ok: true, status: 200, async json() {
      return requests.length === 1 ? { items: [{ id: BOOK_ID, name: 'First' }], nextCursor: 'cursor-1' }
        : { items: [{ id: SECOND_BOOK_ID, name: 'Second' }], nextCursor: null };
    } };
  } });
  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'password';
  submit(dom, doc.getElementById('sign-in-form'));
  await turn(); await turn();
  assert.equal(doc.getElementById('load-more-books').hidden, false);
  doc.getElementById('load-more-books').click();
  await turn(); await turn();
  assert.match(requests[1].url, /cursor=cursor-1/);
  assert.equal(doc.getElementById('book-id').options.length, 3);
  assert.equal(doc.getElementById('load-more-books').hidden, true);
  doc.getElementById('book-id').value = SECOND_BOOK_ID;
  doc.getElementById('book-id').dispatchEvent(new dom.window.Event('change', { bubbles: true }));
  doc.getElementById('message-input').value = 'Consulta otro libro';
  submit(dom, doc.getElementById('composer-form'));
  await turn(); await turn();
  assert.deepEqual(JSON.parse(requests[2].options.body), { bookId: SECOND_BOOK_ID, message: 'Consulta otro libro' });
});

test('unconfigured or insecure endpoints fail closed', () => {
  const { doc } = makeDocument();
  initWeb({ auth: makeAuth().auth, config: { ...CONFIG, chatServiceBaseUrl: 'http://chat.example.test' }, doc, fetchImpl: async () => assert.fail('fetch must not run') });
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.equal(doc.getElementById('sign-in-button').disabled, true);
  assert.match(doc.getElementById('context-validation').textContent, /HTTPS/);
});
