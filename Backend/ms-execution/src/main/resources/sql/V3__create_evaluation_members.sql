-- evaluation_members table for UX evaluation access control
-- ms-execution uses ddl-auto=validate, so the table must exist before startup

CREATE TABLE IF NOT EXISTS evaluation_members (
    id              BIGSERIAL PRIMARY KEY,
    evaluation_id   BIGINT       NOT NULL REFERENCES functional_evaluations(id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
    assigned_at     TIMESTAMP    DEFAULT NOW(),
    UNIQUE (evaluation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_eval_members_eval_id ON evaluation_members(evaluation_id);
CREATE INDEX IF NOT EXISTS idx_eval_members_user_id ON evaluation_members(user_id);
