-- V72: Unified Gateway Secret Center

CREATE TABLE IF NOT EXISTS gateway_secrets (
    id VARCHAR(64) PRIMARY KEY,
    secret_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    secret_type VARCHAR(40) NOT NULL,
    provider VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    key_hash VARCHAR(256),
    key_prefix VARCHAR(40),
    encrypted_secret VARCHAR(2048) NOT NULL,
    last4 VARCHAR(10),
    base_url VARCHAR(500),
    user_id VARCHAR(120),
    description VARCHAR(500),
    rate_limit_per_minute INT,
    rate_limit_per_day INT,
    monthly_token_quota BIGINT,
    used_tokens BIGINT,
    expires_at DATETIME,
    last_used_at DATETIME,
    last_error VARCHAR(1000),
    rotation_at DATETIME,
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_gateway_secret_type_provider_status (secret_type, provider, status),
    INDEX idx_gateway_secret_user (user_id),
    INDEX idx_gateway_secret_key_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 兼容部分环境 external_provider_keys 未创建的情况
CREATE TABLE IF NOT EXISTS external_provider_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    api_key TEXT NOT NULL,
    base_url VARCHAR(500),
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 从历史调用方 API key 迁移到统一密钥中心
INSERT INTO gateway_secrets (
    id, secret_id, name, secret_type, provider, status,
    key_hash, key_prefix, encrypted_secret, last4,
    user_id, description, rate_limit_per_minute, rate_limit_per_day,
    monthly_token_quota, used_tokens, expires_at, last_used_at,
    rotation_at, created_by, updated_by, created_at, updated_at
)
SELECT
    k.id,
    CONCAT('gsec_client_', k.id),
    k.name,
    'CLIENT_ACCESS',
    NULL,
    CASE WHEN COALESCE(k.enabled, TRUE) THEN 'ACTIVE' ELSE 'DISABLED' END,
    k.key_hash,
    k.key_prefix,
    COALESCE(k.encrypted_secret, k.key_prefix),
    RIGHT(COALESCE(k.key_prefix, ''), 4),
    k.user_id,
    k.description,
    k.rate_limit_per_minute,
    k.rate_limit_per_day,
    k.monthly_token_quota,
    COALESCE(k.used_tokens, 0),
    k.expires_at,
    k.last_used_at,
    COALESCE(k.updated_at, k.created_at, NOW()),
    'migration-v72',
    'migration-v72',
    COALESCE(k.created_at, NOW()),
    COALESCE(k.updated_at, NOW())
FROM api_keys k
WHERE NOT EXISTS (
    SELECT 1 FROM gateway_secrets g WHERE g.id = k.id
);

-- 从历史 external_provider_keys 迁移 provider credential
INSERT INTO gateway_secrets (
    id, secret_id, name, secret_type, provider, status,
    encrypted_secret, last4, base_url, description,
    rotation_at, created_by, updated_by, created_at, updated_at
)
SELECT
    CONCAT('prov_', CAST(epk.id AS CHAR)),
    CONCAT('gsec_provider_', CAST(epk.id AS CHAR)),
    epk.name,
    'PROVIDER_CREDENTIAL',
    LOWER(epk.provider),
    CASE WHEN COALESCE(epk.enabled, TRUE) THEN 'ACTIVE' ELSE 'DISABLED' END,
    epk.api_key,
    RIGHT(COALESCE(epk.api_key, ''), 4),
    epk.base_url,
    epk.description,
    COALESCE(epk.update_time, epk.create_time, NOW()),
    'migration-v72',
    'migration-v72',
    COALESCE(epk.create_time, NOW()),
    COALESCE(epk.update_time, NOW())
FROM external_provider_keys epk
WHERE epk.api_key IS NOT NULL
  AND epk.api_key <> ''
  AND NOT EXISTS (
      SELECT 1 FROM gateway_secrets g
      WHERE g.secret_type = 'PROVIDER_CREDENTIAL'
        AND g.provider = LOWER(epk.provider)
        AND g.last4 = RIGHT(COALESCE(epk.api_key, ''), 4)
  );

-- 从 model_config 一次性迁移 provider key
INSERT INTO gateway_secrets (
    id, secret_id, name, secret_type, provider, status,
    encrypted_secret, last4, base_url, description,
    rotation_at, created_by, updated_by, created_at, updated_at
)
SELECT
    'prov_siliconflow',
    'gsec_provider_900000001',
    'siliconflow migrated credential',
    'PROVIDER_CREDENTIAL',
    'siliconflow',
    'ACTIVE',
    mc.silicon_flow_api_key,
    RIGHT(COALESCE(mc.silicon_flow_api_key, ''), 4),
    mc.silicon_flow_endpoint,
    'migrated from model_config.silicon_flow_api_key',
    NOW(),
    'migration-v72',
    'migration-v72',
    NOW(),
    NOW()
FROM model_config mc
WHERE mc.silicon_flow_api_key IS NOT NULL
  AND mc.silicon_flow_api_key <> ''
  AND NOT EXISTS (
      SELECT 1 FROM gateway_secrets g
      WHERE g.secret_type = 'PROVIDER_CREDENTIAL' AND g.provider = 'siliconflow'
  )
LIMIT 1;

INSERT INTO gateway_secrets (
    id, secret_id, name, secret_type, provider, status,
    encrypted_secret, last4, base_url, description,
    rotation_at, created_by, updated_by, created_at, updated_at
)
SELECT
    'prov_dify',
    'gsec_provider_900000002',
    'dify migrated credential',
    'PROVIDER_CREDENTIAL',
    'dify',
    'ACTIVE',
    mc.dify_api_key,
    RIGHT(COALESCE(mc.dify_api_key, ''), 4),
    mc.dify_endpoint,
    'migrated from model_config.dify_api_key',
    NOW(),
    'migration-v72',
    'migration-v72',
    NOW(),
    NOW()
FROM model_config mc
WHERE mc.dify_api_key IS NOT NULL
  AND mc.dify_api_key <> ''
  AND NOT EXISTS (
      SELECT 1 FROM gateway_secrets g
      WHERE g.secret_type = 'PROVIDER_CREDENTIAL' AND g.provider = 'dify'
  )
LIMIT 1;

INSERT INTO gateway_secrets (
    id, secret_id, name, secret_type, provider, status,
    encrypted_secret, last4, base_url, description,
    rotation_at, created_by, updated_by, created_at, updated_at
)
SELECT
    'prov_ollama',
    'gsec_provider_900000003',
    'ollama migrated credential',
    'PROVIDER_CREDENTIAL',
    'ollama',
    'ACTIVE',
    mc.ollama_api_key,
    RIGHT(COALESCE(mc.ollama_api_key, ''), 4),
    mc.ollama_endpoint,
    'migrated from model_config.ollama_api_key',
    NOW(),
    'migration-v72',
    'migration-v72',
    NOW(),
    NOW()
FROM model_config mc
WHERE mc.ollama_api_key IS NOT NULL
  AND mc.ollama_api_key <> ''
  AND NOT EXISTS (
      SELECT 1 FROM gateway_secrets g
      WHERE g.secret_type = 'PROVIDER_CREDENTIAL' AND g.provider = 'ollama'
  )
LIMIT 1;
