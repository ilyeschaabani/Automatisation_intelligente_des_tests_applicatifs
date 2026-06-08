-- Run this SQL in pgAdmin or psql against Test_platform_db
-- ms-execution uses ddl-auto=validate, so the table must exist before startup

CREATE TABLE IF NOT EXISTS ux_navigation_steps (
    id              BIGSERIAL PRIMARY KEY,
    evaluation_id   BIGINT NOT NULL REFERENCES functional_evaluations(id) ON DELETE CASCADE,
    step_number     INTEGER NOT NULL,
    step_name       VARCHAR(255),
    action_performed TEXT,
    observation     TEXT,
    page_url        VARCHAR(2048),
    page_title      VARCHAR(512),
    screenshot_base64 TEXT,
    gemini_raw_response TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ux_nav_steps_eval_id ON ux_navigation_steps(evaluation_id);
