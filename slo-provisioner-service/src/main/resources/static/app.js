const MAX_ROWS = 500;

const tabSlos = document.getElementById("tab-slos");
const tabSlis = document.getElementById("tab-slis");
const sloPanel = document.getElementById("slo-panel");
const sliPanel = document.getElementById("sli-panel");
const sloToolbar = document.getElementById("slo-toolbar");
const sliToolbar = document.getElementById("sli-toolbar");
const slosBody = document.getElementById("slos-body");
const slisBody = document.getElementById("slis-body");
const sloEmptyState = document.getElementById("slo-empty-state");
const sliEmptyState = document.getElementById("sli-empty-state");
const loadStatus = document.getElementById("load-status");
const statTotal = document.getElementById("stat-total");
const statusFilter = document.getElementById("status-filter");
const refreshBtn = document.getElementById("refresh-btn");
const pauseBtn = document.getElementById("pause-btn");
const refreshSliBtn = document.getElementById("refresh-sli-btn");
const pauseSliBtn = document.getElementById("pause-sli-btn");

let slos = [];
let slis = [];
let activeTab = "slos";
let paused = false;
let pollTimer = null;

function formatTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toISOString().replace("T", " ").replace(".000Z", "Z");
}

function statusBadge(status) {
  const level = status || "NOT_PROVISIONED";
  return `<span class="badge badge-${level}">${level}</span>`;
}

function sloLink(name) {
  if (!name) return "—";
  const href = `/ui/slos/${encodeURIComponent(name)}`;
  return `<a class="id-link mono" href="${href}">${name}</a>`;
}

function sliLink(name) {
  if (!name) return "—";
  const href = `/ui/slis/${encodeURIComponent(name)}`;
  return `<a class="id-link mono" href="${href}">${name}</a>`;
}

function truncate(text, max = 72) {
  if (!text) return "—";
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function renderSlos() {
  slosBody.innerHTML = "";
  sloEmptyState.classList.toggle("hidden", slos.length > 0);
  statTotal.textContent = String(slos.length);

  slos.forEach((slo) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td class="col-id">${sloLink(slo.name)}</td>
      <td class="mono">${slo.service || "—"}</td>
      <td class="mono">${slo.objective_target ?? "—"}</td>
      <td class="mono">${slo.time_window || "—"}</td>
      <td class="mono">${sliLink(slo.indicator_ref)}</td>
      <td>${statusBadge(slo.provision_status)}</td>
      <td class="mono">${slo.rules_file_name || "—"}</td>
      <td class="mono">${formatTime(slo.last_synced_at)}</td>
    `;
    slosBody.appendChild(row);
  });
}

function renderSlis() {
  slisBody.innerHTML = "";
  sliEmptyState.classList.toggle("hidden", slis.length > 0);
  statTotal.textContent = String(slis.length);

  slis.forEach((sli) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td class="col-id">${sliLink(sli.name)}</td>
      <td class="mono">${sli.datasource || "—"}</td>
      <td class="mono query-cell" title="${sli.good_query || ""}">${truncate(sli.good_query)}</td>
      <td class="mono query-cell" title="${sli.total_query || ""}">${truncate(sli.total_query)}</td>
    `;
    slisBody.appendChild(row);
  });
}

function setLoadStatus(state, label) {
  loadStatus.className = `status-pill status-${state}`;
  loadStatus.textContent = label;
}

async function loadSlos() {
  setLoadStatus("connecting", "Loading");
  try {
    const params = new URLSearchParams({
      limit: String(MAX_ROWS),
      provision_status: statusFilter.value,
    });
    const response = await AdminAuth.adminFetch(`/api/ui/slos?${params.toString()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    slos = payload.slos || [];
    renderSlos();
    setLoadStatus("live", `Loaded ${slos.length} SLOs`);
  } catch (error) {
    setLoadStatus("error", "Load failed");
    console.error(error);
  }
}

async function loadSlis() {
  setLoadStatus("connecting", "Loading");
  try {
    const response = await AdminAuth.adminFetch(`/api/ui/slis?limit=${MAX_ROWS}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    slis = payload.slis || [];
    renderSlis();
    setLoadStatus("live", `Loaded ${slis.length} SLIs`);
  } catch (error) {
    setLoadStatus("error", "Load failed");
    console.error(error);
  }
}

function loadActiveTab() {
  if (activeTab === "slos") {
    void loadSlos();
  } else {
    void loadSlis();
  }
}

function showTab(tab) {
  activeTab = tab;
  const isSlos = tab === "slos";
  tabSlos.classList.toggle("active", isSlos);
  tabSlis.classList.toggle("active", !isSlos);
  sloPanel.classList.toggle("hidden", !isSlos);
  sliPanel.classList.toggle("hidden", isSlos);
  sloToolbar.classList.toggle("hidden", !isSlos);
  sliToolbar.classList.toggle("hidden", isSlos);
  loadActiveTab();
}

function connectStream() {
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    if (!paused) {
      loadActiveTab();
    }
  }, 5000);
}

tabSlos.addEventListener("click", () => showTab("slos"));
tabSlis.addEventListener("click", () => showTab("slis"));
statusFilter.addEventListener("change", () => void loadSlos());
refreshBtn.addEventListener("click", () => void loadSlos());
refreshSliBtn.addEventListener("click", () => void loadSlis());

pauseBtn.addEventListener("click", () => {
  paused = !paused;
  pauseBtn.textContent = paused ? "Resume live feed" : "Pause live feed";
  pauseSliBtn.textContent = pauseBtn.textContent;
});

pauseSliBtn.addEventListener("click", () => pauseBtn.click());

AdminAuth.bindAdminAuthPanel({
  statusEl: document.getElementById("auth-status"),
  userEl: document.getElementById("auth-user"),
  passwordEl: document.getElementById("auth-password"),
  loginBtn: document.getElementById("auth-login-btn"),
  logoutBtn: document.getElementById("auth-logout-btn"),
  onAuthenticated: () => {
    loadActiveTab();
    connectStream();
  },
});

loadActiveTab();
connectStream();
