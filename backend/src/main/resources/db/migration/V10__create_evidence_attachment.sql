CREATE TABLE evidence_attachment (
  id TEXT PRIMARY KEY,
  evidence_id TEXT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  source_type TEXT NOT NULL CHECK (source_type IN ('LOCAL_PATH', 'EXTERNAL_URL')),
  location TEXT NOT NULL,
  media_type TEXT,
  size_bytes INTEGER CHECK (size_bytes IS NULL OR (size_bytes >= 0 AND size_bytes <= 2199023255552)),
  description TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_evidence_attachment_evidence_active
  ON evidence_attachment(evidence_id, deleted_at, updated_at);
