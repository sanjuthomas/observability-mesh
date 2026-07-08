CREATE TABLE service_level_objectives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    logical_key TEXT NOT NULL,
    version INT NOT NULL,
    stale BOOLEAN NOT NULL,
    api_version TEXT NOT NULL,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    content JSONB NOT NULL,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (logical_key, version)
);

CREATE INDEX idx_slo_logical_key_stale ON service_level_objectives (logical_key, stale);
CREATE INDEX idx_slo_active_kind ON service_level_objectives (kind) WHERE stale = false;

CREATE TABLE slo_provision_state (
    logical_key TEXT PRIMARY KEY,
    openslo_version INT NOT NULL,
    status TEXT NOT NULL,
    rules_file_name TEXT,
    content_hash TEXT,
    last_synced_at TIMESTAMPTZ NOT NULL,
    last_error TEXT
);
