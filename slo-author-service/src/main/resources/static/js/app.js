const AUTH_STORAGE_KEY = 'sloauthor.session';
const DOCUMENTS_API = '/api/v1/documents';

const state = {
  token: null,
  username: null,
  documents: [],
  currentLogicalKey: null,
  currentVersion: null,
  isNew: true,
  dirty: false
};

const editor = CodeMirror.fromTextArea(document.getElementById('yaml-editor'), {
  mode: 'yaml',
  theme: 'material-darker',
  lineNumbers: true,
  lineWrapping: true,
  tabSize: 2
});

editor.on('change', () => {
  state.dirty = true;
  updateSaveButton();
});

const elements = {
  documentList: document.getElementById('document-list'),
  versionList: document.getElementById('version-list'),
  statusBanner: document.getElementById('status-banner'),
  docMeta: document.getElementById('doc-meta'),
  authStatus: document.getElementById('auth-status'),
  loginBtn: document.getElementById('btn-login'),
  kindSelect: document.getElementById('kind-select'),
  saveBtn: document.getElementById('btn-save'),
  loginDialog: document.getElementById('login-dialog')
};

document.getElementById('btn-new').addEventListener('click', startNewDocument);
document.getElementById('btn-template').addEventListener('click', loadTemplate);
document.getElementById('btn-save').addEventListener('click', saveDocument);
document.getElementById('btn-validate').addEventListener('click', validateDocument);
elements.loginBtn.addEventListener('click', handleAuthButtonClick);
document.getElementById('login-cancel').addEventListener('click', () => elements.loginDialog.close());
document.getElementById('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const userId = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  await signIn(userId, password);
});

function saveSession(username, token) {
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ username, token }));
}

function clearSession() {
  sessionStorage.removeItem(AUTH_STORAGE_KEY);
  state.token = null;
  state.username = null;
}

function updateAuthUI() {
  const signedIn = Boolean(state.token);
  elements.authStatus.textContent = signedIn
    ? `Signed in as ${state.username}`
    : 'Not authenticated';
  elements.loginBtn.textContent = signedIn ? 'Sign out' : 'Sign in';
  updateSaveButton();
}

function handleAuthButtonClick() {
  if (state.token) {
    signOut();
    return;
  }
  elements.loginDialog.showModal();
}

async function signIn(userId, password) {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, password })
    });

    const text = await response.text();
    const body = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw new Error(body?.message || 'Sign in failed. Check your credentials.');
    }

    state.token = body.session_token;
    state.username = body.user_id || userId;

    const me = await api('/api/auth/me');
    state.username = me.username;
    saveSession(state.username, state.token);
    elements.loginDialog.close();
    document.getElementById('login-form').reset();
    updateAuthUI();
    await refreshDocuments();
    clearStatus();
  } catch (err) {
    clearSession();
    updateAuthUI();
    showStatus(err.message, 'error');
  }
}

function signOut() {
  clearSession();
  state.documents = [];
  renderDocumentList();
  elements.versionList.innerHTML = '<p class="muted">Select or save a document to view versions.</p>';
  updateAuthUI();
  showStatus('Signed out', 'info');
}

async function restoreSession() {
  const raw = sessionStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    updateAuthUI();
    return;
  }

  try {
    const { username, token } = JSON.parse(raw);
    state.token = token;
    state.username = username;
    const me = await api('/api/auth/me');
    state.username = me.username;
    updateAuthUI();
    await refreshDocuments();
  } catch {
    clearSession();
    updateAuthUI();
  }
}

function encodeLogicalKey(key) {
  return key.replace(/\//g, '~');
}

function showStatus(message, type = 'info') {
  elements.statusBanner.textContent = message;
  elements.statusBanner.className = `status-banner ${type}`;
}

function clearStatus() {
  elements.statusBanner.className = 'status-banner hidden';
}

function updateSaveButton() {
  elements.saveBtn.disabled = !state.token || !state.dirty;
}

function updateDocMeta() {
  if (state.isNew) {
    elements.docMeta.textContent = 'New document (unsaved)';
    return;
  }
  elements.docMeta.textContent = `${state.currentLogicalKey} · v${state.currentVersion}`;
}

async function api(path, options = {}) {
  if (!state.token) {
    throw new Error('Sign in required');
  }

  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${state.token}`,
      ...(options.headers || {})
    }
  });

  if (response.status === 401) {
    clearSession();
    updateAuthUI();
    throw new Error('Authentication failed. Please sign in again.');
  }

  const text = await response.text();
  const body = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }

  return body;
}

async function refreshDocuments() {
  if (!state.token) {
    elements.documentList.innerHTML = '<p class="muted">Sign in to view documents.</p>';
    return;
  }

  try {
    state.documents = await api(DOCUMENTS_API);
    renderDocumentList();
    clearStatus();
  } catch (err) {
    showStatus(err.message, 'error');
  }
}

function renderDocumentList() {
  if (!state.documents.length) {
    elements.documentList.innerHTML = '<p class="muted">No documents yet. Create one.</p>';
    return;
  }

  elements.documentList.innerHTML = state.documents.map((doc) => `
    <div class="doc-item ${doc.logicalKey === state.currentLogicalKey ? 'active' : ''}"
         data-key="${doc.logicalKey}">
      <div class="kind">${doc.kind}</div>
      <div class="name">${doc.displayName || doc.name}</div>
      <div class="meta">v${doc.version} · ${new Date(doc.createdAt).toLocaleString()}</div>
    </div>
  `).join('');

  elements.documentList.querySelectorAll('.doc-item').forEach((item) => {
    item.addEventListener('click', () => openDocument(item.dataset.key));
  });
}

async function openDocument(logicalKey) {
  try {
    const doc = await api(`${DOCUMENTS_API}/${encodeLogicalKey(logicalKey)}`);
    state.currentLogicalKey = doc.logicalKey;
    state.currentVersion = doc.version;
    state.isNew = false;
    state.dirty = false;

    const yaml = await api(`${DOCUMENTS_API}/to-yaml`, {
      method: 'POST',
      body: JSON.stringify({ content: doc.content })
    });

    editor.setValue(yaml.yaml);
    elements.kindSelect.value = doc.content.kind;
    updateDocMeta();
    updateSaveButton();
    renderDocumentList();
    await loadVersions(logicalKey);
    clearStatus();
  } catch (err) {
    showStatus(err.message, 'error');
  }
}

async function loadVersions(logicalKey) {
  try {
    const versions = await api(`${DOCUMENTS_API}/${encodeLogicalKey(logicalKey)}/versions`);
    elements.versionList.innerHTML = versions.map((v) => `
      <div class="version-item ${v.stale ? 'stale' : ''}">
        <span>v${v.version} · ${v.stale ? 'stale' : 'active'} · ${new Date(v.createdAt).toLocaleString()}</span>
        <button type="button" data-id="${v.id}">View</button>
      </div>
    `).join('');

    elements.versionList.querySelectorAll('button').forEach((btn) => {
      btn.addEventListener('click', () => viewVersion(btn.dataset.id));
    });
  } catch (err) {
    elements.versionList.innerHTML = `<p class="muted">${err.message}</p>`;
  }
}

async function viewVersion(id) {
  try {
    const doc = await api(`${DOCUMENTS_API}/id/${id}`);
    const yaml = await api(`${DOCUMENTS_API}/to-yaml`, {
      method: 'POST',
      body: JSON.stringify({ content: doc.content })
    });
    editor.setValue(yaml.yaml);
    state.currentLogicalKey = doc.logicalKey;
    state.currentVersion = doc.version;
    state.isNew = false;
    state.dirty = false;
    elements.kindSelect.value = doc.content.kind;
    updateDocMeta();
    updateSaveButton();
    showStatus(`Viewing version ${doc.version}${doc.stale ? ' (stale)' : ''}`, 'info');
  } catch (err) {
    showStatus(err.message, 'error');
  }
}

function startNewDocument() {
  state.isNew = true;
  state.currentLogicalKey = null;
  state.currentVersion = null;
  state.dirty = true;
  loadTemplate();
  elements.versionList.innerHTML = '<p class="muted">Save to create the first version.</p>';
  updateDocMeta();
  updateSaveButton();
  renderDocumentList();
  clearStatus();
}

function loadTemplate() {
  const kind = elements.kindSelect.value;
  editor.setValue(OPEN_SLO_TEMPLATES[kind] || OPEN_SLO_TEMPLATES.SLO);
  state.dirty = true;
  updateSaveButton();
}

async function validateDocument() {
  try {
    const content = await api(`${DOCUMENTS_API}/parse-yaml`, {
      method: 'POST',
      body: JSON.stringify({ yaml: editor.getValue() })
    });

    const result = await api(`${DOCUMENTS_API}/validate`, {
      method: 'POST',
      body: JSON.stringify({ content })
    });

    let message = `Valid ${content.kind} document: ${content.metadata?.name} (${result.logicalKey})`;
    if (state.isNew && await checkExists(result.logicalKey)) {
      message += ' — an active document with this identity already exists.';
      showStatus(message, 'error');
      return;
    }

    showStatus(message, 'success');
  } catch (err) {
    showStatus(err.message, 'error');
  }
}

async function checkExists(logicalKey) {
  try {
    const result = await api(`${DOCUMENTS_API}/exists/${encodeLogicalKey(logicalKey)}`);
    return result.exists;
  } catch {
    return false;
  }
}

async function saveDocument() {
  try {
    const content = await api(`${DOCUMENTS_API}/parse-yaml`, {
      method: 'POST',
      body: JSON.stringify({ yaml: editor.getValue() })
    });

    let saved;
    if (state.isNew) {
      saved = await api(DOCUMENTS_API, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      showStatus(`Created ${saved.content.kind} "${saved.content.metadata.name}" (v${saved.version})`, 'success');
    } else {
      saved = await api(`${DOCUMENTS_API}/${encodeLogicalKey(state.currentLogicalKey)}`, {
        method: 'PUT',
        body: JSON.stringify({ content })
      });
      showStatus(`Saved new version v${saved.version}. Previous version marked stale.`, 'success');
    }

    state.isNew = false;
    state.currentLogicalKey = saved.logicalKey;
    state.currentVersion = saved.version;
    state.dirty = false;
    updateDocMeta();
    updateSaveButton();
    await refreshDocuments();
    await loadVersions(saved.logicalKey);
  } catch (err) {
    showStatus(err.message, 'error');
  }
}

async function init() {
  startNewDocument();
  await restoreSession();
}

init();
