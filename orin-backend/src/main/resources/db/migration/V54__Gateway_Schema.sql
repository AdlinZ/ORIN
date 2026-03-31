-- Gateway Module Schema
-- V54: Initial gateway tables for routing, services, ACL, and policies
-- Order matters: tables with foreign keys must be created after the tables they reference

-- 1. Gateway Services (no FK dependencies)
CREATE TABLE IF NOT EXISTS gateway_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_key VARCHAR(100) NOT NULL UNIQUE,
    service_name VARCHAR(100) NOT NULL,
    protocol VARCHAR(20) DEFAULT 'HTTP',
    base_path VARCHAR(200),
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Policy tables (no FK dependencies, must be before gateway_routes)
CREATE TABLE IF NOT EXISTS gateway_rate_limit_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    dimension VARCHAR(20) DEFAULT 'GLOBAL',
    capacity INT DEFAULT 100,
    window_seconds INT DEFAULT 60,
    burst INT DEFAULT 10,
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gateway_circuit_breaker_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    failure_threshold INT DEFAULT 5,
    success_threshold INT DEFAULT 2,
    timeout_seconds INT DEFAULT 60,
    half_open_max_requests INT DEFAULT 3,
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gateway_retry_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    max_attempts INT DEFAULT 3,
    retry_on_status_codes VARCHAR(100) DEFAULT '500,502,503,504',
    retry_on_exceptions VARCHAR(500),
    backoff_multiplier DOUBLE DEFAULT 2.0,
    initial_interval_ms INT DEFAULT 100,
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Gateway Routes (depends on gateway_services and policy tables)
CREATE TABLE IF NOT EXISTS gateway_routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    path_pattern VARCHAR(500) NOT NULL,
    method VARCHAR(10) DEFAULT 'ALL',
    service_id BIGINT,
    target_url VARCHAR(500),
    strip_prefix BOOLEAN DEFAULT FALSE,
    rewrite_path VARCHAR(500),
    timeout_ms INT DEFAULT 30000,
    retry_count INT DEFAULT 0,
    load_balance VARCHAR(20) DEFAULT 'ROUND_ROBIN',
    auth_required BOOLEAN DEFAULT TRUE,
    rate_limit_policy_id BIGINT,
    circuit_breaker_policy_id BIGINT,
    retry_policy_id BIGINT,
    priority INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    description VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (service_id) REFERENCES gateway_services(id) ON DELETE SET NULL,
    FOREIGN KEY (rate_limit_policy_id) REFERENCES gateway_rate_limit_policies(id) ON DELETE SET NULL,
    FOREIGN KEY (circuit_breaker_policy_id) REFERENCES gateway_circuit_breaker_policies(id) ON DELETE SET NULL,
    FOREIGN KEY (retry_policy_id) REFERENCES gateway_retry_policies(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Gateway Service Instances (depends on gateway_services)
CREATE TABLE IF NOT EXISTS gateway_service_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    host VARCHAR(200) NOT NULL,
    port INT NOT NULL,
    weight INT DEFAULT 100,
    health_check_path VARCHAR(200),
    status VARCHAR(20) DEFAULT 'UP',
    last_heartbeat DATETIME,
    consecutive_failures INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (service_id) REFERENCES gateway_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Gateway ACL Rules (no FK dependencies)
CREATE TABLE IF NOT EXISTS gateway_acl_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    ip_pattern VARCHAR(500),
    path_pattern VARCHAR(500),
    api_key_required BOOLEAN DEFAULT FALSE,
    description VARCHAR(500),
    priority INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Gateway Audit Logs (no FK dependencies)
CREATE TABLE IF NOT EXISTS gateway_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT,
    trace_id VARCHAR(64),
    method VARCHAR(10),
    path VARCHAR(500),
    target_service VARCHAR(100),
    target_url VARCHAR(500),
    status_code INT,
    latency_ms BIGINT,
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    api_key_id VARCHAR(100),
    result VARCHAR(20),
    error_message VARCHAR(1000),
    created_at DATETIME,
    INDEX idx_gal_route_id (route_id),
    INDEX idx_gal_trace_id (trace_id),
    INDEX idx_gal_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
