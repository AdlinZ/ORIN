-- V97: Run Logs — F04 观察控制
-- Runner 在执行期间逐行推送日志，前端拉取展示。

CREATE TABLE IF NOT EXISTS run_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      VARCHAR(40)  NOT NULL COMMENT 'FK → runs.id',
    sequence    INT          NOT NULL COMMENT '日志序号（从 0 递增）',
    level       VARCHAR(10)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO / WARN / ERROR / DEBUG',
    message     TEXT         NOT NULL,
    created_at  BIGINT       NOT NULL COMMENT 'epoch millis',
    INDEX idx_run_logs_run_id_seq (run_id, sequence),
    CONSTRAINT fk_run_logs_run FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
