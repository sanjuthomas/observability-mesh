const subtitle = document.getElementById("scan-request-subtitle");
const detailError = document.getElementById("detail-error");
const detailSummary = document.getElementById("detail-summary");
const detailJsonSection = document.getElementById("detail-json-section");
const detailJson = document.getElementById("detail-json");
const copyJsonBtn = document.getElementById("copy-json-btn");

function paymentIdFromPath() {
  const parts = window.location.pathname.split("/").filter(Boolean);
  const idx = parts.indexOf("scan-requests");
  return idx >= 0 ? parts[idx + 1] : null;
}

function paymentVersionFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const value = params.get("payment_version");
  return value ? Number(value) : null;
}

function lifecycleBadge(status) {
  return `<span class="badge badge-${status || "OPEN"}">${status || "OPEN"}</span>`;
}

function resultBadge(result) {
  if (!result) return "—";
  return `<span class="badge badge-${result}">${result}</span>`;
}

function showError(message) {
  detailError.textContent = message;
  detailError.classList.remove("hidden");
  detailSummary.classList.add("hidden");
  detailJsonSection.classList.add("hidden");
}

function renderSummary(scanRequest) {
  subtitle.textContent = `${scanRequest.payment_id} · payment v${scanRequest.payment_version}`;
  detailSummary.innerHTML = `
    <div class="detail-card-header">
      ${lifecycleBadge(scanRequest.lifecycle_status)}
      ${resultBadge(scanRequest.result)}
    </div>
    <dl class="detail-grid">
      <div class="detail-field">
        <dt>Payment ID</dt>
        <dd class="mono">${scanRequest.payment_id}</dd>
      </div>
      <div class="detail-field">
        <dt>Payment version</dt>
        <dd class="mono">${scanRequest.payment_version}</dd>
      </div>
      <div class="detail-field">
        <dt>Document version</dt>
        <dd class="mono">${scanRequest.version_number}</dd>
      </div>
      <div class="detail-field">
        <dt>Instruction ID</dt>
        <dd class="mono">${scanRequest.instruction_id || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Owning LOB</dt>
        <dd class="mono">${scanRequest.owning_lob || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Creditor name</dt>
        <dd class="mono">${scanRequest.creditor_name || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Requested at</dt>
        <dd class="mono">${scanRequest.requested_at || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Valid in</dt>
        <dd class="mono">${scanRequest.in || "—"}</dd>
      </div>
    </dl>
  `;
  detailSummary.classList.remove("hidden");
  detailJson.textContent = JSON.stringify(scanRequest, null, 2);
  detailJsonSection.classList.remove("hidden");
  detailError.classList.add("hidden");
}

async function loadScanRequest() {
  const paymentId = paymentIdFromPath();
  const paymentVersion = paymentVersionFromQuery();
  if (!paymentId || paymentVersion === null || Number.isNaN(paymentVersion)) {
    showError("Missing payment id or payment_version query parameter.");
    return;
  }
  if (!AdminAuth.loadSession()) {
    showError("Admin sign-in required.");
    return;
  }
  try {
    const response = await AdminAuth.adminFetch(
      `/api/ui/scan-requests/${encodeURIComponent(paymentId)}?payment_version=${paymentVersion}`
    );
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    renderSummary(payload.scan_request);
  } catch (error) {
    showError(error.message || "Failed to load scan request.");
  }
}

copyJsonBtn?.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(detailJson.textContent);
    copyJsonBtn.textContent = "Copied";
    window.setTimeout(() => {
      copyJsonBtn.textContent = "Copy JSON";
    }, 1200);
  } catch {
    copyJsonBtn.textContent = "Copy failed";
  }
});

AdminAuth.bindAdminAuthPanel({
  statusEl: document.getElementById("auth-status"),
  userEl: document.getElementById("auth-user"),
  passwordEl: document.getElementById("auth-password"),
  loginBtn: document.getElementById("auth-login-btn"),
  logoutBtn: document.getElementById("auth-logout-btn"),
  onAuthenticated: () => void loadScanRequest(),
});
