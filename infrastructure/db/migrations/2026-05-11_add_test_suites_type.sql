ALTER TABLE ux_evaluations RENAME TO functional_evaluations;

ALTER TABLE functional_evaluations RENAME COLUMN ux_summary TO test_summary;

ALTER TABLE functional_evaluations RENAME COLUMN ux_analysis TO ai_analysis;

ALTER TABLE functional_evaluations ADD COLUMN page_content TEXT;
