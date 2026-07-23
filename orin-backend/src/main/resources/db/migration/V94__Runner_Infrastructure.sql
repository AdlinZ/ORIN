-- V94: Runner infrastructure (F01 接入并监控服务器)
-- 为 Runner / RunnerCredential / RunnerEnrollmentToken / RunnerHeartbeatSnapshot 四张表建立 F01 必需基础设施。
-- 仅新增表与索引，不修改既有表；migration 编号按 AGENTS.md §5.2 自 V94 起顺延。
-- Runner Credential 与现有 gateway_secrets / provider_credentials 严格隔离（ADR-001 §8），不混入既有密钥中心。

-- ============================================================
-- 1. runners
-- ============================================================
CREATE TABLE IF NOT EXISTS runners (
    id                       VARCHAR(40)  NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    version                  VARCHAR(40)  NULL,
    hostname                 VARCHAR(255) NULL,
    os                       VARCHAR(40)  NULL,
    arch                     VARCHAR(40)  NULL,
    labels                   JSON         NULL,
    capabilities             JSON         NULL,
    gpu_info                 JSON         NULL,
    cpu_cores                INT          NULL,
    memory_total             BIGINT       NULL,
    disk_total               BIGINT       NULL,
    max_concurrency          INT          NOT NULL DEFAULT 1,
    active_runs              INT          NOT NULL DEFAULT 0,
    queued_runs              INT          NOT NULL DEFAULT 0,
    last_heartbeat_at        BIGINT       NULL,
    last_dependency_health   VARCHAR(20)  NULL,
    drain_requested          BOOLEAN      NOT NULL DEFAULT FALSE,
    drain_ack_at             BIGINT       NULL,
    created_by               VARCHAR(120) NOT NULL,
    created_at               BIGINT       NOT NULL,
    updated_at               BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_runner_name_owner (name, created_by),
    KEY idx_runner_status_last_hb (status, last_heartbeat_at),
    KEY idx_runner_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. runner_credentials
-- ============================================================
CREATE TABLE IF NOT EXISTS runner_credentials (
    id                 VARCHAR(40)  NOT NULL,
    runner_id          VARCHAR(40)  NOT NULL,
    credential_id      VARCHAR(80)  NOT NULL,
    credential_hash    VARCHAR(255) NOT NULL,
    key_prefix         VARCHAR(40)  NULL,
    last4              VARCHAR(10)  NULL,
    status             VARCHAR(20)  NOT NULL,
    created_at         BIGINT       NOT NULL,
    updated_at         BIGINT       NOT NULL,
    revoked_at         BIGINT       NULL,
    revoked_by         VARCHAR(120) NULL,
    -- ADR-002 §D-2.11 R3 目标位（当前 Runner Credential 仅 BCrypt hash；R3 落地时统一 AEAD 字段）。
    encrypted_value    TEXT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_runner_cred_credid (credential_id),
    KEY idx_runner_cred_runner (runner_id),
    KEY idx_runner_cred_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. runner_enrollment_tokens
-- ============================================================
CREATE TABLE IF NOT EXISTS runner_enrollment_tokens (
    id           VARCHAR(40)  NOT NULL,
    token_hash   VARCHAR(255) NOT NULL,
    created_by   VARCHAR(120) NOT NULL,
    created_at   BIGINT       NOT NULL,
    expires_at   BIGINT       NOT NULL,
    used_at      BIGINT       NULL,
    runner_id    VARCHAR(40)  NULL,
    note         VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_runner_enroll_token (token_hash),
    KEY idx_runner_enroll_exp (expires_at),
    KEY idx_runner_enroll_active (used_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. runner_heartbeat_snapshots
-- ============================================================
CREATE TABLE IF NOT EXISTS runner_heartbeat_snapshots (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    runner_id          VARCHAR(40)  NOT NULL,
    cpu_usage          DECIMAL(5,2) NULL,
    memory_used        BIGINT       NULL,
    disk_used          BIGINT       NULL,
    gpu_usage          DECIMAL(5,2) NULL,
    memory_total       BIGINT       NULL,
    disk_total         BIGINT       NULL,
    dependency_health  VARCHAR(20)  NULL,
    reported_at        BIGINT       NOT NULL,
    raw_payload        JSON         NULL,
    PRIMARY KEY (id),
    KEY idx_runner_hb_runner_time (runner_id, reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
