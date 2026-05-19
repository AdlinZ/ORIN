-- Add explicit terminal cancellation state for workflow task_info rows.
ALTER TABLE task_info
    MODIFY COLUMN status ENUM('QUEUED','RUNNING','RETRYING','COMPLETED','FAILED','DEAD','CANCELLED') NOT NULL;
