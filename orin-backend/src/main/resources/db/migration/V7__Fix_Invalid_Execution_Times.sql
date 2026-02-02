-- Clean up historical data where execution_time was incorrectly stored as a timestamp
-- Current timestamp is ~1.7e12, valid execution durations are likely < 1 day (8.6e7)
-- We set valid threshold to 100,000,000 ms (~27 hours) to be safe.
UPDATE multimodal_tasks 
SET execution_time = NULL 
WHERE execution_time > 100000000;
