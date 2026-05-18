-- Flyway migration: create reports table
-- Run by Flyway if configured, otherwise apply manually against the ms-execution database

CREATE TABLE IF NOT EXISTS reports (
  id BIGSERIAL PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  filename VARCHAR(255) NOT NULL,
  file_path TEXT NOT NULL,
  generated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reports_campaign_id ON reports(campaign_id);
