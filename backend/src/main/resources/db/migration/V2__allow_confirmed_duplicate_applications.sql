ALTER TABLE application_record ADD COLUMN duplicate_confirmed_at TEXT;

DROP INDEX uq_application_active_per_job;

CREATE UNIQUE INDEX uq_application_unconfirmed_active_per_job
  ON application_record(job_id)
  WHERE deleted_at IS NULL
    AND duplicate_confirmed_at IS NULL
    AND status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'ON_HOLD');

CREATE INDEX idx_application_active_by_job
  ON application_record(job_id, status)
  WHERE deleted_at IS NULL
    AND status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'ON_HOLD');
