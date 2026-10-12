import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { JSDOM } from 'jsdom';
import { initWeb, isHttpsUrl, isValidBookId } from '../src/app.js';

const doms = [];
const BOOK_ID = '10000000-0000-4000-8000-000000000001';
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
  add('input', 'book-id');
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

test('HTTPS and RFC UUID configuration validation are strict', () => {
  assert.equal(isHttpsUrl('https://chat.example.test'), true);
  assert.equal(isHttpsUrl('http://localhost:8000'), false);
  assert.equal(isHttpsUrl('https://user:pass@chat.example.test'), false);
  assert.equal(isHttpsUrl('https://chat.example.test?debug=1'), false);
  assert.equal(isValidBookId(BOOK_ID), true);
  assert.equal(isValidBookId('not-a-uuid'), false);
  assert.equal(isValidBookId('00000000-0000-0000-0000-000000000000'), false);
});

test('sign-in gates chat, sends only bookId/message over HTTPS, and renders untrusted content as text', async () => {
  const { dom, doc } = makeDocument();
  const { auth, state } = makeAuth();
  const requests = [];
  const fetchImpl = async (url, options) => {
    requests.push({ url, options });
    return {
      ok: true,
      status: 200,
      async json() {
        return {
          text: '<img src=x onerror=alert(1)> safe assistant text',
          period: { dateFrom: '2025-01-01', dateTo: '2025-01-31' },
          currency: 'USD',
          rows: [{ name: '<script>row</script>', amount: '12.00' }],
          summary: { totals: { balanced: true } },
        };
      },
    };
  };
  initWeb({ auth, config: CONFIG, fetchImpl, doc });
  const send = doc.getElementById('send-button');
  assert.equal(send.disabled, true);

  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'never-store-this';
  submit(dom, doc.getElementById('sign-in-form'));
  assert.equal(doc.getElementById('password-input').value, '', 'password clears immediately');
  await turn();
  assert.equal(state.signInCalls.length, 1);
  assert.deepEqual(state.signInCalls[0], { email: 'owner@example.test', password: 'never-store-this' });
  assert.equal(send.disabled, true, 'a valid book UUID is also required');

  const book = doc.getElementById('book-id');
  book.value = BOOK_ID;
  book.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  assert.equal(send.disabled, false);
  doc.getElementById('message-input').value = 'Consulta el balance';
  submit(dom, doc.getElementById('composer-form'));
  await turn();
  await turn();

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, 'https://chat.example.test/v1/chat');
  assert.equal(requests[0].options.method, 'POST');
  assert.equal(requests[0].options.credentials, 'omit');
  assert.equal(requests[0].options.redirect, 'error');
  assert.equal(requests[0].options.headers.Authorization, 'Bearer memory-only-token');
  assert.deepEqual(JSON.parse(requests[0].options.body), { bookId: BOOK_ID, message: 'Consulta el balance' });
  assert.equal('dateFrom' in JSON.parse(requests[0].options.body), false);
  assert.equal(doc.querySelector('#query-results img, #query-results script'), null);
  assert.match(doc.getElementById('conversation').textContent, /<img src=x onerror=alert\(1\)> safe assistant text/);
  assert.match(doc.getElementById('query-results').textContent, /<script>row<\/script>/);
  assert.match(doc.getElementById('query-results').textContent, /balanced/);
  assert.equal(state.signOutCalls.length, 0);
});

test('expired/unauthorized service response clears the in-memory session', async () => {
  const { dom, doc } = makeDocument();
  const { auth, state } = makeAuth();
  initWeb({
    auth,
    config: CONFIG,
    doc,
    fetchImpl: async () => ({ ok: false, status: 401, async json() { return { code: 'authentication_required' }; } }),
  });
  doc.getElementById('email-input').value = 'owner@example.test';
  doc.getElementById('password-input').value = 'password';
  submit(dom, doc.getElementById('sign-in-form'));
  await turn();
  const book = doc.getElementById('book-id');
  book.value = BOOK_ID;
  book.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  doc.getElementById('message-input').value = 'Consulta';
  submit(dom, doc.getElementById('composer-form'));
  await turn();
  await turn();
  assert.deepEqual(state.signOutCalls, [{ scope: 'local' }]);
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.match(doc.getElementById('context-validation').textContent, /venció|válida/);
});

test('unconfigured or insecure endpoints fail closed', () => {
  const { doc } = makeDocument();
  initWeb({ auth: makeAuth().auth, config: { ...CONFIG, chatServiceBaseUrl: 'http://chat.example.test' }, doc, fetchImpl: async () => assert.fail('fetch must not run') });
  assert.equal(doc.getElementById('send-button').disabled, true);
  assert.equal(doc.getElementById('sign-in-button').disabled, true);
  assert.match(doc.getElementById('context-validation').textContent, /HTTPS/);
});
