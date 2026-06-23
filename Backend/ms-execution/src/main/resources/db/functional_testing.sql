-- ============================================================================
-- Test fonctionnel — migration manuelle (ms-execution est en ddl-auto=validate)
-- À exécuter sur la base Test_platform_db AVANT de redémarrer ms-execution.
-- ============================================================================

-- 1) Mode de revue des verdicts (AUTO = sans validation, SUPERVISED = le testeur valide)
ALTER TABLE functional_evaluations
    ADD COLUMN IF NOT EXISTS review_mode VARCHAR(20) DEFAULT 'AUTO';

-- 2) Résultats détaillés des tests fonctionnels (un enregistrement par cas de test)
CREATE TABLE IF NOT EXISTS functional_test_results (
    id              BIGSERIAL PRIMARY KEY,
    evaluation_id   BIGINT NOT NULL,
    form_label      TEXT,
    scenario        VARCHAR(40),
    target_field    TEXT,
    input_data      TEXT,
    expected        TEXT,
    observed        TEXT,
    status          VARCHAR(20),
    severity        VARCHAR(20),
    confidence      DOUBLE PRECISION,
    evidence        TEXT,
    human_validated BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_functional_test_results_evaluation
    ON functional_test_results (evaluation_id);
