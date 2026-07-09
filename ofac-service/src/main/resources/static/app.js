const MAX_ROWS = 500;

const tbody = document.getElementById("scan-requests-body");
const emptyState = document.getElementById("empty-state");
const loadStatus = document.getElementById("load-status");
const statTotal = document.getElementById("stat-total");
const lifecycleFilter = document.getElementById("lifecycle-filter");
const resultFilter = document.getElementById("result-filter");
const paymentFilter = document.getElementById("payment-filter");
const lobFilter = document.getElementById("lob-filter");
const refreshBtn = document.getElementById("refresh-btn");
const pauseBtn = document.getElementById("pause-btn");
const clearBtn = document.getElementById("clear-btn");

let scanRequests = [];
let paused = false;
let pollTimer = null;

function formatTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toISOString().replace("T", " ").replace(".000Z", "Z");
}

function scanRequestLink(paymentId, paymentVersion) {
  if (!paymentId) return "—";
  const href = `/ui/scan-requests/${encodeURIComponent(paymentId)}?payment_version=${paymentVersion}`;
  return `<a class="id-link mono" href="${href}">${paymentId}</a>`;
}

function instructionLink(instructionId) {
  if (!instructionId) return "—";
  const href = `http://localhost:9000/ui/instructions/${encodeURIComponent(instructionId)}`;
  return `<a class="id-link mono" href="${href}" target="_blank" rel="noopener">${instructionId}</a>`;
}

function lifecycleBadge(status) {
  return `<span class="badge badge-${status || "OPEN"}">${status || "OPEN"}</span>`;
}

function resultBadge(result) {
  if (!result) return "—";
  return `<span class="badge badge-${result}">${result}</span>`;
}

function passesClientFilters(request) {
  const paymentId = paymentFilter.value.trim();
  if (paymentId && request.payment_id !== paymentId) return false;
  if (lobFilter.value !== "ALL" && request.owning_lob !== lobFilter.value) return false;
  return true;
}

function updateLobOptions() {
  const lobs = new Set(scanRequests.map((r) => r.owning_lob).filter(Boolean));
  const current = lobFilter.value;
  lobFilter.innerHTML = '<option value="ALL">All</option>';
  [...lobs].sort().forEach((lob) => {
    const opt = document.createElement("option");
    opt.value = lob;
    opt.textContent = lob;
    lobFilter.appendChild(opt);
  });
  if ([...lobs, "ALL"].includes(current)) lobFilter.value = current;
}

function renderTable() {
  tbody.innerHTML = "";
  const visible = scanRequests.filter(passesClientFilters);
  emptyState.classList.toggle("hidden", visible.length > 0);
  statTotal.textContent = String(visible.length);

  visible.forEach((request) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td class="col-id">${scanRequestLink(request.payment_id, request.payment_version)}</td>
      <td class="mono">${request.payment_version ?? "—"}</td>
      <td class="mono">${request.version_number ?? "—"}</td>
      <td>${lifecycleBadge(request.lifecycle_status)}</td>
      <td>${resultBadge(request.result)}</td>
      <td class="col-id">${instructionLink(request.instruction_id)}</td>
      <td class="mono">${request.owning_lob || "—"}</td>
      <td class="mono">${request.creditor_name || "—"}</td>
      <td class="mono">${formatTime(request.requested_at)}</td>
      <td class="mono">${formatTime(request.in)}</td>
    `;
    tbody.appendChild(row);
  });
}

function buildScanRequestsUrl() {
  const params = new URLSearchParams({ limit: String(MAX_ROWS) });
  if (lifecycleFilter.value !== "ALL") {
    params.set("lifecycle_status", lifecycleFilter.value);
  }
  if (resultFilter.value !== "ALL") {
    params.set("result", resultFilter.value);
  }
  return `/api/ui/scan-requests?${params.toString()}`;
}

function setLoadStatus(state, label) {
  loadStatus.className = `status-pill status-${state}`;
  loadStatus.textContent = label;
}

async function loadScanRequests() {
  if (!AdminAuth.loadSession()) {
    setLoadStatus("error", "Sign in required");
    return;
  }
  setLoadStatus("connecting", "Loading");
  try {
    const response = await AdminAuth.adminFetch(buildScanRequestsUrl());
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    scanRequests = payload.scan_requests || [];
    updateLobOptions();
    renderTable();
    setLoadStatus("live", `Loaded ${scanRequests.length}`);
  } catch (error) {
    setLoadStatus("error", "Load failed");
    console.error(error);
  }
}

function connectStream() {
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    if (!paused) {
      void loadScanRequests();
    }
  }, 2000);
}

lifecycleFilter.addEventListener("change", () => void loadScanRequests());
resultFilter.addEventListener("change", () => void loadScanRequests());
paymentFilter.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    renderTable();
  }
});
paymentFilter.addEventListener("input", () => renderTable());
lobFilter.addEventListener("change", () => renderTable());

refreshBtn.addEventListener("click", () => void loadScanRequests());

pauseBtn.addEventListener("click", () => {
  paused = !paused;
  pauseBtn.textContent = paused ? "Resume live feed" : "Pause live feed";
});

clearBtn.addEventListener("click", () => {
  scanRequests = [];
  renderTable();
});

AdminAuth.bindAdminAuthPanel({
  statusEl: document.getElementById("auth-status"),
  userEl: document.getElementById("auth-user"),
  passwordEl: document.getElementById("auth-password"),
  loginBtn: document.getElementById("auth-login-btn"),
  logoutBtn: document.getElementById("auth-logout-btn"),
  onAuthenticated: () => {
    void loadScanRequests();
    connectStream();
  },
});
