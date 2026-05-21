-- Make project_id nullable for UX evaluations so UX can be created without a project
ALTER TABLE ux_evaluations ALTER COLUMN project_id DROP NOT NULL;

-- Add generated_script column to store a validated script when provided by UI
ALTER TABLE ux_evaluations ADD COLUMN IF NOT EXISTS generated_script text;

-- Note: If the table does not yet exist in your environment, ensure migrations create it
-- without NOT NULL on project_id and with the generated_script column.
