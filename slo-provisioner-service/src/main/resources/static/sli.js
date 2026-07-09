const subtitle = document.getElementById("sli-subtitle");
const detailError = document.getElementById("detail-error");
const detailSummary = document.getElementById("detail-summary");
const detailJsonSection = document.getElementById("detail-json-section");
const detailJson = document.getElementById("detail-json");
const copyJsonBtn = document.getElementById("copy-json-btn");

function nameFromPath() {
  const parts = window.location.pathname.split("/").filter(Boolean);
  const idx = parts.indexOf("slis");
  return idx >= 0 ? decodeURIComponent(parts[idx + 1]) : null;
}

function showError(message) {
  detailError.textContent = message;
  detailError.classList.remove("hidden");
  detailSummary.classList.add("hidden");
  detailJsonSection.classList.add("hidden");
}

function renderSli(sli) {
  subtitle.textContent = sli.name;
  detailSummary.innerHTML = `
    <dl class="detail-grid">
      <div class="detail-field">
        <dt>Name</dt>
        <dd class="mono">${sli.name}</dd>
      </div>
      <div class="detail-field">
        <dt>Logical key</dt>
        <dd class="mono">${sli.logical_key}</dd>
      </div>
      <div class="detail-field">
        <dt>Version</dt>
        <dd class="mono">${sli.version}</dd>
      </div>
      <div class="detail-field">
        <dt>Datasource</dt>
        <dd class="mono">${sli.datasource || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Good PromQL</dt>
        <dd class="mono">${sli.good_query || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Total PromQL</dt>
        <dd class="mono">${sli.total_query || "—"}</dd>
      </div>
    </dl>
  `;
  detailSummary.classList.remove("hidden");
  detailJson.textContent = JSON.stringify(sli.content, null, 2);
  detailJsonSection.classList.remove("hidden");
  detailError.classList.add("hidden");
}

async function loadSli() {
  const name = nameFromPath();
  if (!name) {
    showError("Missing SLI name in URL.");
    return;
  }
  try {
    const response = await AdminAuth.adminFetch(`/api/ui/slis/${encodeURIComponent(name)}`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    renderSli(payload.sli);
  } catch (error) {
    showError(error.message || "Failed to load SLI.");
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
  onAuthenticated: () => void loadSli(),
});

void loadSli();
