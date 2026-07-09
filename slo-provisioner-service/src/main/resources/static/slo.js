const subtitle = document.getElementById("slo-subtitle");
const detailError = document.getElementById("detail-error");
const detailSummary = document.getElementById("detail-summary");
const indicatorSection = document.getElementById("indicator-section");
const indicatorSummary = document.getElementById("indicator-summary");
const rulesSection = document.getElementById("rules-section");
const rulesJson = document.getElementById("rules-json");
const detailJsonSection = document.getElementById("detail-json-section");
const detailJson = document.getElementById("detail-json");
const copyJsonBtn = document.getElementById("copy-json-btn");
const copyRulesBtn = document.getElementById("copy-rules-btn");

function nameFromPath() {
  const parts = window.location.pathname.split("/").filter(Boolean);
  const idx = parts.indexOf("slos");
  return idx >= 0 ? decodeURIComponent(parts[idx + 1]) : null;
}

function statusBadge(status) {
  return `<span class="badge badge-${status || "NOT_PROVISIONED"}">${status || "NOT_PROVISIONED"}</span>`;
}

function showError(message) {
  detailError.textContent = message;
  detailError.classList.remove("hidden");
  detailSummary.classList.add("hidden");
  indicatorSection.classList.add("hidden");
  rulesSection.classList.add("hidden");
  detailJsonSection.classList.add("hidden");
}

function renderIndicator(indicator) {
  if (!indicator) {
    indicatorSection.classList.add("hidden");
    return;
  }
  indicatorSummary.innerHTML = `
    <dl class="detail-grid">
      <div class="detail-field">
        <dt>Name</dt>
        <dd class="mono">${indicator.name}</dd>
      </div>
      <div class="detail-field">
        <dt>Datasource</dt>
        <dd class="mono">${indicator.datasource || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Good PromQL</dt>
        <dd class="mono">${indicator.good_query || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Total PromQL</dt>
        <dd class="mono">${indicator.total_query || "—"}</dd>
      </div>
    </dl>
  `;
  indicatorSection.classList.remove("hidden");
}

function renderSlo(slo) {
  subtitle.textContent = slo.display_name || slo.name;
  detailSummary.innerHTML = `
    <div class="detail-card-header">
      ${statusBadge(slo.provision_status)}
      <span class="muted">${slo.logical_key}</span>
    </div>
    <dl class="detail-grid">
      <div class="detail-field">
        <dt>Name</dt>
        <dd class="mono">${slo.name}</dd>
      </div>
      <div class="detail-field">
        <dt>Service</dt>
        <dd class="mono">${slo.service || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Target</dt>
        <dd class="mono">${slo.objective_target ?? "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Time window</dt>
        <dd class="mono">${slo.time_window || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Indicator</dt>
        <dd class="mono">${slo.indicator_ref || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Rules file</dt>
        <dd class="mono">${slo.rules_file_name || "—"}</dd>
      </div>
      <div class="detail-field">
        <dt>Last synced</dt>
        <dd class="mono">${slo.last_synced_at || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Description</dt>
        <dd>${slo.description || "—"}</dd>
      </div>
      <div class="detail-field detail-field-wide">
        <dt>Last error</dt>
        <dd class="mono">${slo.last_error || "—"}</dd>
      </div>
    </dl>
  `;
  detailSummary.classList.remove("hidden");
  renderIndicator(slo.indicator);

  if (slo.prometheus_rules) {
    rulesJson.textContent = slo.prometheus_rules;
    rulesSection.classList.remove("hidden");
  } else {
    rulesSection.classList.add("hidden");
  }

  detailJson.textContent = JSON.stringify(slo.content, null, 2);
  detailJsonSection.classList.remove("hidden");
  detailError.classList.add("hidden");
}

async function loadSlo() {
  const name = nameFromPath();
  if (!name) {
    showError("Missing SLO name in URL.");
    return;
  }
  try {
    const response = await AdminAuth.adminFetch(`/api/ui/slos/${encodeURIComponent(name)}`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    renderSlo(payload.slo);
  } catch (error) {
    showError(error.message || "Failed to load SLO.");
  }
}

async function copyText(button, text, successLabel, sourceLabel) {
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = successLabel;
    window.setTimeout(() => {
      button.textContent = sourceLabel;
    }, 1200);
  } catch {
    button.textContent = "Copy failed";
  }
}

copyJsonBtn?.addEventListener("click", () =>
  copyText(copyJsonBtn, detailJson.textContent, "Copied", "Copy JSON"));
copyRulesBtn?.addEventListener("click", () =>
  copyText(copyRulesBtn, rulesJson.textContent, "Copied", "Copy YAML"));

AdminAuth.bindAdminAuthPanel({
  statusEl: document.getElementById("auth-status"),
  userEl: document.getElementById("auth-user"),
  passwordEl: document.getElementById("auth-password"),
  loginBtn: document.getElementById("auth-login-btn"),
  logoutBtn: document.getElementById("auth-logout-btn"),
  onAuthenticated: () => void loadSlo(),
});

void loadSlo();
