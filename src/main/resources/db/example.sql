-- Example schema placeholder for rules app data.
CREATE SCHEMA IF NOT EXISTS tool_use;

CREATE TABLE IF NOT EXISTS tool_use.tool_use_audit (
  id bigserial PRIMARY KEY,
  request_id text NOT NULL,
  decision text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
