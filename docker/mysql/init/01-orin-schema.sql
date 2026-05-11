# ORIN schema snapshot for Docker quickstart
# Generated from: 919856f at 2026-05-11 15:07:27 +0800
# Source: developer local MySQL localhost:3306/orindb
# Future note: once historical Flyway migrations run cleanly on an empty DB, this script can be changed to dump from a fresh temporary container.
# DO NOT EDIT MANUALLY. Regenerate via scripts/regenerate-schema-snapshot.sh
# Snapshot covers Flyway migrations V1 through V83

-- MySQL dump 10.13  Distrib 8.4.6, for macos15 (arm64)
--
-- Host: localhost    Database: orindb
-- ------------------------------------------------------
-- Server version	8.4.6

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `agent_access_profiles`
--

DROP TABLE IF EXISTS `agent_access_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_access_profiles` (
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dataset_api_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `connection_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`agent_id`),
  KEY `idx_connection_status` (`connection_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_chat_session`
--

DROP TABLE IF EXISTS `agent_chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(255) NOT NULL,
  `agent_id` varchar(255) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `history` text,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `attached_kb_ids` json DEFAULT NULL,
  `kb_doc_filters` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_health_status`
--

DROP TABLE IF EXISTS `agent_health_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_health_status` (
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `health_score` int DEFAULT NULL,
  `status` enum('RUNNING','STOPPED','HIGH_LOAD','ERROR','UNKNOWN') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_heartbeat` bigint DEFAULT NULL,
  `provider_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mode` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `view_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`agent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_job`
--

DROP TABLE IF EXISTS `agent_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `job_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int DEFAULT NULL,
  `result_data` text COLLATE utf8mb4_unicode_ci,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('PENDING','RUNNING','SUCCESS','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `triggered_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_t7bl9hsj3yjl1l4yj8uajbhvr` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_logs`
--

DROP TABLE IF EXISTS `agent_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `response` text COLLATE utf8mb4_unicode_ci,
  `session_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `tokens` int DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `level` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_type` (`type`),
  KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_memories`
--

DROP TABLE IF EXISTS `agent_memories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_memories` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `memory_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `memory_value` text COLLATE utf8mb4_unicode_ci,
  `metadata` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON metadata for context',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_metadata`
--

DROP TABLE IF EXISTS `agent_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_metadata` (
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `icon` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mode` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `temperature` double DEFAULT NULL,
  `top_p` double DEFAULT NULL,
  `max_tokens` int DEFAULT NULL,
  `system_prompt` text COLLATE utf8mb4_unicode_ci,
  `sync_time` timestamp NULL DEFAULT NULL,
  `topp` double DEFAULT NULL,
  `parameters` text COLLATE utf8mb4_unicode_ci,
  `view_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tool_calling_override` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`agent_id`),
  KEY `idx_provider_type` (`provider_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_metrics`
--

DROP TABLE IF EXISTS `agent_metrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cpu_usage` double DEFAULT NULL,
  `daily_requests` int DEFAULT NULL,
  `memory_usage` double DEFAULT NULL,
  `response_latency` int DEFAULT NULL,
  `timestamp` bigint NOT NULL,
  `token_cost` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_time` (`agent_id`,`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_tool_binding`
--

DROP TABLE IF EXISTS `agent_tool_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_tool_binding` (
  `agent_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tool_ids` json DEFAULT NULL,
  `kb_ids` json DEFAULT NULL,
  `skill_ids` json DEFAULT NULL,
  `mcp_ids` json DEFAULT NULL,
  `enable_suggestions` tinyint(1) NOT NULL DEFAULT '1',
  `show_retrieved_context` tinyint(1) NOT NULL DEFAULT '1',
  `auto_rename_session` tinyint(1) NOT NULL DEFAULT '1',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agent_versions`
--

DROP TABLE IF EXISTS `agent_versions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_versions` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_number` int NOT NULL,
  `version_tag` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `config_snapshot` json NOT NULL,
  `change_description` text COLLATE utf8mb4_unicode_ci,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_active` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version` (`agent_id`,`version_number`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_history`
--

DROP TABLE IF EXISTS `alert_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_history` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alert_message` text COLLATE utf8mb4_unicode_ci,
  `resolved_at` datetime(6) DEFAULT NULL,
  `repeat_count` int DEFAULT '1',
  `last_triggered_at` datetime DEFAULT NULL,
  `rule_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `triggered_at` datetime(6) NOT NULL,
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fingerprint` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rule_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_triggered_at` (`triggered_at`),
  KEY `idx_alert_history_fingerprint_status` (`fingerprint`,`status`),
  KEY `idx_alert_fingerprint_status` (`fingerprint`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_notification_config`
--

DROP TABLE IF EXISTS `alert_notification_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_notification_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email_enabled` tinyint(1) DEFAULT '1',
  `email_recipients` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dingtalk_enabled` tinyint(1) DEFAULT '0',
  `dingtalk_webhook` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wecom_enabled` tinyint(1) DEFAULT '0',
  `wecom_webhook` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `critical_only` bit(1) DEFAULT NULL,
  `desktop_notification` bit(1) DEFAULT NULL,
  `instant_push` bit(1) DEFAULT NULL,
  `merge_interval_minutes` int DEFAULT NULL,
  `notify_email` bit(1) DEFAULT NULL,
  `notify_inapp` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_record`
--

DROP TABLE IF EXISTS `alert_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_record` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alert_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alert_agent_id` (`agent_id`),
  KEY `idx_alert_type` (`alert_type`),
  KEY `idx_alert_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_records`
--

DROP TABLE IF EXISTS `alert_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_records` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alert_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_rules`
--

DROP TABLE IF EXISTS `alert_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_rules` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `condition_expr` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cooldown_minutes` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `notification_channels` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_list` text COLLATE utf8mb4_unicode_ci,
  `rule_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `threshold_value` double DEFAULT NULL,
  `target_scope` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'ALL',
  `target_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metric_window_minutes` int DEFAULT '5',
  `min_sample_count` int DEFAULT '1',
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alert_rules_type_target_enabled` (`rule_type`,`target_scope`,`target_id`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `api_access_log`
--

DROP TABLE IF EXISTS `api_access_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `ip_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `query_params` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_body` text COLLATE utf8mb4_unicode_ci,
  `response_status` int DEFAULT NULL,
  `response_time_ms` bigint DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `api_endpoints`
--

DROP TABLE IF EXISTS `api_endpoints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_endpoints` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avg_response_time_ms` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `enabled` bit(1) NOT NULL,
  `failed_calls` bigint DEFAULT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_required` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rate_limit_per_day` int DEFAULT NULL,
  `rate_limit_per_hour` int DEFAULT NULL,
  `rate_limit_per_minute` int DEFAULT NULL,
  `require_auth` bit(1) NOT NULL,
  `success_calls` bigint DEFAULT NULL,
  `total_calls` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_path_method` (`path`,`method`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `api_keys`
--

DROP TABLE IF EXISTS `api_keys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_keys` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `key_hash` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `key_prefix` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `permissions` text COLLATE utf8mb4_unicode_ci,
  `rate_limit_per_minute` int DEFAULT '100',
  `rate_limit_per_day` int DEFAULT '10000',
  `monthly_token_quota` bigint DEFAULT '1000000',
  `used_tokens` bigint DEFAULT '0',
  `expires_at` timestamp NULL DEFAULT NULL,
  `last_used_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT NULL,
  `encrypted_secret` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Encrypted API key plaintext for admin reveal',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key_hash` (`key_hash`),
  KEY `idx_key_hash` (`key_hash`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `api_key_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `conversation_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `workflow_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `endpoint` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_params` text COLLATE utf8mb4_unicode_ci,
  `response_content` text COLLATE utf8mb4_unicode_ci,
  `status_code` int DEFAULT NULL,
  `response_time` bigint DEFAULT NULL,
  `prompt_tokens` int DEFAULT '0',
  `completion_tokens` int DEFAULT '0',
  `total_tokens` int DEFAULT '0',
  `estimated_cost` double DEFAULT '0',
  `success` tinyint(1) DEFAULT '1',
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `external_price` decimal(19,8) DEFAULT NULL,
  `internal_cost` decimal(19,8) DEFAULT NULL,
  `profit` decimal(19,8) DEFAULT NULL,
  `tenant_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `download_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_api_key_id` (`api_key_id`),
  KEY `idx_endpoint` (`endpoint`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_event_log`
--

DROP TABLE IF EXISTS `collab_event_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_event_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sub_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_data` json DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB AUTO_INCREMENT=515 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_message`
--

DROP TABLE IF EXISTS `collab_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `turn_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stage` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `metadata` json DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_collab_message_session` (`session_id`),
  KEY `idx_collab_message_turn` (`turn_id`),
  KEY `idx_collab_message_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=344 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_package`
--

DROP TABLE IF EXISTS `collab_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `root_task_id` bigint DEFAULT NULL,
  `intent` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `intent_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `intent_priority` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `intent_complexity` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `need_review` tinyint(1) DEFAULT '0',
  `need_consensus` tinyint(1) DEFAULT '0',
  `collaboration_mode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'SEQUENTIAL',
  `shared_context` json DEFAULT NULL,
  `strategy` json DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'PLANNING',
  `result` text COLLATE utf8mb4_unicode_ci,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  `timeout_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `package_id` (`package_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_participant`
--

DROP TABLE IF EXISTS `collab_participant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `capabilities` json DEFAULT NULL,
  `cost_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'IDLE',
  PRIMARY KEY (`id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_session`
--

DROP TABLE IF EXISTS `collab_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `main_agent_policy` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'STATIC_THEN_BID',
  `quality_threshold` double DEFAULT '0.82',
  `max_critique_rounds` int DEFAULT '3',
  `draft_parallelism` int DEFAULT '4',
  `main_agent_static_default` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bid_whitelist` json DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_id` (`session_id`),
  KEY `idx_collab_session_created_by` (`created_by`),
  KEY `idx_collab_session_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_subtask`
--

DROP TABLE IF EXISTS `collab_subtask`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_subtask` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sub_task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `expected_role` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `depends_on` json DEFAULT NULL,
  `input_data` json DEFAULT NULL,
  `output_data` json DEFAULT NULL,
  `confidence` double DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `result` text COLLATE utf8mb4_unicode_ci,
  `executed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subtask` (`package_id`,`sub_task_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=179 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_task`
--

DROP TABLE IF EXISTS `collab_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'SEQUENTIAL',
  `current_agent_index` int DEFAULT '0',
  `result` text COLLATE utf8mb4_unicode_ci,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_task_agents`
--

DROP TABLE IF EXISTS `collab_task_agents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_task_agents` (
  `task_id` bigint NOT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`task_id`,`agent_id`),
  CONSTRAINT `collab_task_agents_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `collab_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collab_turn`
--

DROP TABLE IF EXISTS `collab_turn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_turn` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `turn_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_message` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'RUNNING',
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `selection_meta` json DEFAULT NULL,
  `started_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `turn_id` (`turn_id`),
  KEY `idx_collab_turn_session` (`session_id`),
  KEY `idx_collab_turn_status` (`status`),
  KEY `idx_collab_turn_started` (`started_at`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `conversation_logs`
--

DROP TABLE IF EXISTS `conversation_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_logs` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `conversation_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `query` text COLLATE utf8mb4_unicode_ci,
  `response` text COLLATE utf8mb4_unicode_ci,
  `response_time` bigint DEFAULT NULL,
  `success` bit(1) DEFAULT NULL,
  `total_tokens` int DEFAULT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `download_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conv_id` (`conversation_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dify_apps`
--

DROP TABLE IF EXISTS `dify_apps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dify_apps` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_from` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `icon_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dify_conversations`
--

DROP TABLE IF EXISTS `dify_conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dify_conversations` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `app_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `from_end_user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `from_source` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_deleted` bit(1) DEFAULT NULL,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dify_workflows`
--

DROP TABLE IF EXISTS `dify_workflows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dify_workflows` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `app_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_from` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `dsl_definition` longtext COLLATE utf8mb4_unicode_ci,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `external_provider_keys`
--

DROP TABLE IF EXISTS `external_provider_keys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_provider_keys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_acl_rules`
--

DROP TABLE IF EXISTS `gateway_acl_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_acl_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_pattern` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `path_pattern` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key_required` tinyint(1) DEFAULT '0',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int DEFAULT '0',
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_audit_logs`
--

DROP TABLE IF EXISTS `gateway_audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_service` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status_code` int DEFAULT NULL,
  `latency_ms` bigint DEFAULT NULL,
  `client_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gal_route_id` (`route_id`),
  KEY `idx_gal_trace_id` (`trace_id`),
  KEY `idx_gal_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=26924 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_circuit_breaker_policies`
--

DROP TABLE IF EXISTS `gateway_circuit_breaker_policies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_circuit_breaker_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_threshold` int DEFAULT '5',
  `success_threshold` int DEFAULT '2',
  `timeout_seconds` int DEFAULT '60',
  `half_open_max_requests` int DEFAULT '3',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_rate_limit_policies`
--

DROP TABLE IF EXISTS `gateway_rate_limit_policies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_rate_limit_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dimension` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'GLOBAL',
  `capacity` int DEFAULT '100',
  `window_seconds` int DEFAULT '60',
  `burst` int DEFAULT '10',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_retry_policies`
--

DROP TABLE IF EXISTS `gateway_retry_policies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_retry_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_attempts` int DEFAULT '3',
  `retry_on_status_codes` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '500,502,503,504',
  `retry_on_exceptions` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `backoff_multiplier` double DEFAULT '2',
  `initial_interval_ms` int DEFAULT '100',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_routes`
--

DROP TABLE IF EXISTS `gateway_routes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_routes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path_pattern` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'ALL',
  `service_id` bigint DEFAULT NULL,
  `target_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `strip_prefix` tinyint(1) DEFAULT '0',
  `rewrite_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `timeout_ms` int DEFAULT '30000',
  `retry_count` int DEFAULT '0',
  `load_balance` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ROUND_ROBIN',
  `auth_required` tinyint(1) DEFAULT '1',
  `rate_limit_policy_id` bigint DEFAULT NULL,
  `circuit_breaker_policy_id` bigint DEFAULT NULL,
  `retry_policy_id` bigint DEFAULT NULL,
  `priority` int DEFAULT '0',
  `enabled` tinyint(1) DEFAULT '1',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `service_id` (`service_id`),
  KEY `rate_limit_policy_id` (`rate_limit_policy_id`),
  KEY `circuit_breaker_policy_id` (`circuit_breaker_policy_id`),
  KEY `retry_policy_id` (`retry_policy_id`),
  CONSTRAINT `gateway_routes_ibfk_1` FOREIGN KEY (`service_id`) REFERENCES `gateway_services` (`id`) ON DELETE SET NULL,
  CONSTRAINT `gateway_routes_ibfk_2` FOREIGN KEY (`rate_limit_policy_id`) REFERENCES `gateway_rate_limit_policies` (`id`) ON DELETE SET NULL,
  CONSTRAINT `gateway_routes_ibfk_3` FOREIGN KEY (`circuit_breaker_policy_id`) REFERENCES `gateway_circuit_breaker_policies` (`id`) ON DELETE SET NULL,
  CONSTRAINT `gateway_routes_ibfk_4` FOREIGN KEY (`retry_policy_id`) REFERENCES `gateway_retry_policies` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_secrets`
--

DROP TABLE IF EXISTS `gateway_secrets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_secrets` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_type` enum('CLIENT_ACCESS','PROVIDER_CREDENTIAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','DISABLED','DELETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `key_hash` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `key_prefix` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `encrypted_secret` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last4` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rate_limit_per_minute` int DEFAULT NULL,
  `rate_limit_per_day` int DEFAULT NULL,
  `monthly_token_quota` bigint DEFAULT NULL,
  `used_tokens` bigint DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `last_used_at` datetime DEFAULT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rotation_at` datetime DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `secret_id` (`secret_id`),
  KEY `idx_gateway_secret_type_provider_status` (`secret_type`,`provider`,`status`),
  KEY `idx_gateway_secret_user` (`user_id`),
  KEY `idx_gateway_secret_key_hash` (`key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_service_instances`
--

DROP TABLE IF EXISTS `gateway_service_instances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_service_instances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_id` bigint NOT NULL,
  `host` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `port` int NOT NULL,
  `weight` int DEFAULT '100',
  `health_check_path` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'UP',
  `last_heartbeat` datetime DEFAULT NULL,
  `consecutive_failures` int DEFAULT '0',
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `service_id` (`service_id`),
  CONSTRAINT `gateway_service_instances_ibfk_1` FOREIGN KEY (`service_id`) REFERENCES `gateway_services` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_services`
--

DROP TABLE IF EXISTS `gateway_services`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_services` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `protocol` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'HTTP',
  `base_path` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `service_key` (`service_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `graph_entities`
--

DROP TABLE IF EXISTS `graph_entities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `graph_entities` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `graph_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `source_document_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_chunk_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `properties` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_graph_id` (`graph_id`),
  KEY `idx_entity_type` (`entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `graph_relations`
--

DROP TABLE IF EXISTS `graph_relations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `graph_relations` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `graph_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_entity_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_entity_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `relation_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `source_document_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_chunk_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `properties` text COLLATE utf8mb4_unicode_ci,
  `weight` double DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_graph_id` (`graph_id`),
  KEY `idx_source_entity` (`source_entity_id`),
  KEY `idx_target_entity` (`target_entity_id`),
  KEY `idx_relation_type` (`relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `help_article`
--

DROP TABLE IF EXISTS `help_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `help_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT 'general',
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `enabled` tinyint(1) DEFAULT '1',
  `view_count` int DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `page_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_external_resource_mapping`
--

DROP TABLE IF EXISTS `integration_external_resource_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_external_resource_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint NOT NULL,
  `platform_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `orin_resource_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `orin_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_resource_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_version` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_updated_at` datetime DEFAULT NULL,
  `last_synced_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_direction` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `raw_snapshot` text COLLATE utf8mb4_unicode_ci,
  `compatibility_report` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mapping_integration_orin` (`integration_id`,`orin_resource_type`,`orin_resource_id`),
  KEY `idx_mapping_orin_resource` (`orin_resource_type`,`orin_resource_id`),
  KEY `idx_mapping_external_resource` (`platform_type`,`external_resource_type`,`external_resource_id`),
  KEY `idx_mapping_integration` (`integration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_sync_conflict`
--

DROP TABLE IF EXISTS `integration_sync_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_sync_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint NOT NULL,
  `platform_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `orin_resource_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `orin_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conflict_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `local_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `local_snapshot` text COLLATE utf8mb4_unicode_ci,
  `external_snapshot` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sync_conflict_integration` (`integration_id`),
  KEY `idx_sync_conflict_resource` (`orin_resource_type`,`orin_resource_id`),
  KEY `idx_sync_conflict_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_sync_cursor`
--

DROP TABLE IF EXISTS `integration_sync_cursor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_sync_cursor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint NOT NULL,
  `resource_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `direction` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cursor_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_seen_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sync_cursor` (`integration_id`,`resource_type`,`direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_sync_item`
--

DROP TABLE IF EXISTS `integration_sync_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_sync_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sync_job_id` bigint NOT NULL,
  `orin_resource_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orin_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_resource_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `change_log_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sync_item_job` (`sync_job_id`),
  KEY `idx_sync_item_orin_resource` (`orin_resource_type`,`orin_resource_id`),
  KEY `idx_sync_item_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_sync_job`
--

DROP TABLE IF EXISTS `integration_sync_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_sync_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint NOT NULL,
  `platform_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `direction` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_scope` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  `total_count` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `failure_count` int DEFAULT '0',
  `conflict_count` int DEFAULT '0',
  `cursor_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `details` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_sync_job_integration` (`integration_id`),
  KEY `idx_sync_job_status` (`status`),
  KEY `idx_sync_job_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kb_document_chunks`
--

DROP TABLE IF EXISTS `kb_document_chunks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_document_chunks` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `char_count` int DEFAULT NULL,
  `chunk_index` int NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vector_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `children_ids` text COLLATE utf8mb4_unicode_ci,
  `chunk_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` int DEFAULT NULL,
  `source` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`document_id`),
  KEY `idx_chunk_index` (`chunk_index`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_chunk_type` (`chunk_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kb_documents`
--

DROP TABLE IF EXISTS `kb_documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_documents` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `knowledge_base_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `storage_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_preview` text COLLATE utf8mb4_unicode_ci,
  `vector_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `vector_index_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `chunk_count` int DEFAULT '0',
  `char_count` int DEFAULT '0',
  `upload_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_modified` timestamp NULL DEFAULT NULL,
  `uploaded_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metadata` text COLLATE utf8mb4_unicode_ci,
  `chunk_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `chunk_overlap` int DEFAULT NULL,
  `chunk_size` int DEFAULT NULL,
  `media_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parse_error` text COLLATE utf8mb4_unicode_ci,
  `parse_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parsed_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `storage_root` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_category` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parsed_text_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int DEFAULT '1',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_flag` tinyint(1) DEFAULT '0',
  `sync_checkpoint` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `object_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `primary_backend` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `replica_backends` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `replication_status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_replicated_at` datetime DEFAULT NULL,
  `last_replication_error` text COLLATE utf8mb4_unicode_ci,
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`knowledge_base_id`),
  KEY `idx_vector_status` (`vector_status`),
  KEY `idx_upload_time` (`upload_time`),
  KEY `idx_kb_documents_parse_status` (`parse_status`),
  KEY `idx_kb_documents_version` (`version`),
  KEY `idx_kb_documents_content_hash` (`content_hash`),
  KEY `idx_kb_documents_deleted_flag` (`deleted_flag`),
  KEY `idx_kb_documents_last_modified` (`last_modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kb_parsing_tasks`
--

DROP TABLE IF EXISTS `kb_parsing_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_parsing_tasks` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `document_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `input_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `knowledge_base_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int DEFAULT NULL,
  `retry_count` int DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kb_skills`
--

DROP TABLE IF EXISTS `kb_skills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_skills` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `definition` text COLLATE utf8mb4_unicode_ci,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_bases`
--

DROP TABLE IF EXISTS `knowledge_bases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_bases` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'UNSTRUCTURED',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `doc_count` int DEFAULT NULL,
  `total_size_mb` double DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_time` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `configuration` text COLLATE utf8mb4_unicode_ci,
  `category` enum('UNSTRUCTURED','STRUCTURED','PROCEDURAL','META_MEMORY') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asr_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asr_provider` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ocr_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ocr_provider` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parsing_enabled` bit(1) DEFAULT NULL,
  `rich_text_enabled` tinyint(1) DEFAULT '1',
  `chunk_size` int DEFAULT NULL,
  `chunk_overlap` int DEFAULT NULL,
  `top_k` int DEFAULT NULL,
  `similarity_threshold` double DEFAULT NULL,
  `alpha` double DEFAULT '0.7',
  `enable_rerank` bit(1) DEFAULT NULL,
  `rerank_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_source_agent_id` (`source_agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_configs`
--

DROP TABLE IF EXISTS `knowledge_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_name` varchar(100) NOT NULL,
  `vector_db_type` varchar(50) NOT NULL,
  `vector_db_host` varchar(200) DEFAULT NULL,
  `vector_db_port` int DEFAULT NULL,
  `vector_db_api_key` varchar(500) DEFAULT NULL,
  `collection_name` varchar(100) NOT NULL,
  `embedding_model` varchar(100) DEFAULT NULL,
  `dimension` int DEFAULT NULL,
  `top_k` int DEFAULT '5',
  `similarity_threshold` decimal(3,2) DEFAULT '0.70',
  `status` varchar(20) DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_dify_document_mapping`
--

DROP TABLE IF EXISTS `knowledge_dify_document_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_dify_document_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint DEFAULT NULL,
  `local_doc_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `local_kb_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dify_dataset_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dify_doc_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING' COMMENT 'SYNCED, PENDING, FAILED, DELETED',
  `local_version` int DEFAULT NULL,
  `dify_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_on_dify` tinyint(1) DEFAULT '0',
  `last_synced_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `idx_local_doc_id` (`local_doc_id`),
  KEY `idx_dify_dataset_doc` (`dify_dataset_id`,`dify_doc_id`),
  KEY `idx_integration_id` (`integration_id`),
  KEY `idx_idempotency_key` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_external_integration`
--

DROP TABLE IF EXISTS `knowledge_external_integration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_external_integration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_config` text COLLATE utf8mb4_unicode_ci,
  `auth_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `capabilities` text COLLATE utf8mb4_unicode_ci,
  `consecutive_failures` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extra_config` text COLLATE utf8mb4_unicode_ci,
  `health_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `integration_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `knowledge_base_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_health_check` datetime(6) DEFAULT NULL,
  `last_sync_time` datetime(6) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_direction` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_graphs`
--

DROP TABLE IF EXISTS `knowledge_graphs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_graphs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `knowledge_base_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `build_status` enum('PENDING','ENTITY_EXTRACTING','RELATION_EXTRACTING','SUCCESS','FAILED','BUILDING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_count` int DEFAULT '0',
  `relation_count` int DEFAULT '0',
  `last_build_at` datetime DEFAULT NULL,
  `last_success_build_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_graph_name` (`name`),
  KEY `idx_graph_status` (`build_status`),
  KEY `idx_graph_kb_id` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_integration_audit_log`
--

DROP TABLE IF EXISTS `knowledge_integration_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_integration_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `integration_id` bigint DEFAULT NULL,
  `integration_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CREATE, UPDATE, DELETE, HEALTH_CHECK, SYNC',
  `operator` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_state` text COLLATE utf8mb4_unicode_ci COMMENT 'Masked config before change',
  `after_state` text COLLATE utf8mb4_unicode_ci COMMENT 'Masked config after change',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_integration_id` (`integration_id`),
  KEY `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_sync_change_log`
--

DROP TABLE IF EXISTS `knowledge_sync_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_sync_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `knowledge_base_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `change_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ADDED, UPDATED, DELETED',
  `version` int NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed_at` datetime NOT NULL,
  `synced` tinyint(1) DEFAULT '0',
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `integration_id` bigint DEFAULT NULL,
  `platform_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orin_resource_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orin_resource_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resource_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_snapshot` text COLLATE utf8mb4_unicode_ci,
  `change_source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ORIN',
  `sync_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `retry_count` int DEFAULT '0',
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `idx_change_log_agent_document` (`agent_id`,`document_id`),
  KEY `idx_change_log_changed_at` (`changed_at`),
  KEY `idx_change_log_synced` (`synced`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_sync_record`
--

DROP TABLE IF EXISTS `knowledge_sync_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_sync_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `added_count` int DEFAULT NULL,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_count` int DEFAULT NULL,
  `details` text COLLATE utf8mb4_unicode_ci,
  `end_time` datetime(6) DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_count` int DEFAULT NULL,
  `direction` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'PULL' COMMENT 'PULL (from Dify), PUSH (to Dify)',
  `checkpoint` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Incremental sync checkpoint',
  `duration_ms` bigint DEFAULT NULL COMMENT 'Sync duration in milliseconds',
  `total_docs` int DEFAULT '0' COMMENT 'Total documents processed',
  `sync_direction` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'INBOUND' COMMENT 'INBOUND (pull), OUTBOUND (push)',
  `conflict_count` int DEFAULT '0' COMMENT 'Number of conflicts detected during sync',
  `integration_id` bigint DEFAULT NULL,
  `platform_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_job_id` bigint DEFAULT NULL,
  `trigger_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resource_scope` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sync_record_direction` (`direction`),
  KEY `idx_sync_record_checkpoint` (`checkpoint`),
  KEY `idx_sync_record_sync_direction` (`sync_direction`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_sync_webhook`
--

DROP TABLE IF EXISTS `knowledge_sync_webhook`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_sync_webhook` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `webhook_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `webhook_secret` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `event_types` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Comma-separated events: document_added,document_updated,document_deleted,sync_completed',
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `disabled` bit(1) DEFAULT NULL,
  `failure_count` int DEFAULT NULL,
  `last_failure_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_failure_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_webhook_agent_id` (`agent_id`),
  KEY `idx_webhook_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_tasks`
--

DROP TABLE IF EXISTS `knowledge_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_tasks` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID of the related asset (file, chunk, etc)',
  `asset_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Type of asset: MULTIMODAL_FILE, DOCUMENT',
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Type of task: CAPTIONING, EMBEDDING, INDEXING',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Task status',
  `retry_count` int DEFAULT '0',
  `max_retries` int DEFAULT '3',
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at` datetime(6) DEFAULT NULL,
  `execution_time_ms` bigint DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`status`),
  KEY `idx_asset_ref` (`asset_id`,`asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mcp_services`
--

DROP TABLE IF EXISTS `mcp_services`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mcp_services` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tool_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('STDIO','SSE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `command` text COLLATE utf8mb4_unicode_ci,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `env_vars` text COLLATE utf8mb4_unicode_ci,
  `description` text COLLATE utf8mb4_unicode_ci,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `status` enum('CONNECTED','DISCONNECTED','ERROR','TESTING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_connected` datetime DEFAULT NULL,
  `last_error` text COLLATE utf8mb4_unicode_ci,
  `health_score` int DEFAULT '100',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_mcp_tool_key` (`tool_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `model_config`
--

DROP TABLE IF EXISTS `model_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `base_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `timeout` int DEFAULT NULL,
  `llama_factory_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `llama_factory_webui` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_save_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  `dify_endpoint` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dify_api_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `silicon_flow_endpoint` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `silicon_flow_api_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `silicon_flow_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `auto_analysis_enabled` bit(1) DEFAULT NULL,
  `embedding_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vlm_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `system_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ollama_api_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ollama_endpoint` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ollama_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `desc_generation_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `embedding_api_key_id` bigint DEFAULT NULL,
  `embedding_provider` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `model_metadata`
--

DROP TABLE IF EXISTS `model_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_metadata` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `model_pricing`
--

DROP TABLE IF EXISTS `model_pricing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_pricing` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `billing_mode` enum('PER_TOKEN','PER_REQUEST','PER_SECOND') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_cost_unit` decimal(19,8) DEFAULT NULL,
  `input_price_unit` decimal(19,8) DEFAULT NULL,
  `output_cost_unit` decimal(19,8) DEFAULT NULL,
  `output_price_unit` decimal(19,8) DEFAULT NULL,
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tenant_group` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1s2vifncp9btejjo1p8dkk5t4` (`provider_id`,`tenant_group`)
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mon_prometheus_config`
--

DROP TABLE IF EXISTS `mon_prometheus_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mon_prometheus_config` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `instance_label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prometheus_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cache_ttl` int DEFAULT NULL,
  `refresh_interval` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mon_rate_limit_config`
--

DROP TABLE IF EXISTS `mon_rate_limit_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mon_rate_limit_config` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `requests_per_minute` int DEFAULT '60',
  `requests_per_day` int DEFAULT '10000',
  `bucket_size` int DEFAULT '60',
  `refill_rate` double DEFAULT '1',
  `enable_user_limit` tinyint(1) DEFAULT '1',
  `enable_api_key_limit` tinyint(1) DEFAULT '1',
  `enable_agent_limit` tinyint(1) DEFAULT '0',
  `algorithm` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'TOKEN_BUCKET',
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `multimodal_files`
--

DROP TABLE IF EXISTS `multimodal_files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `multimodal_files` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `duration` int DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_size` bigint DEFAULT NULL,
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `height` int DEFAULT NULL,
  `metadata` text COLLATE utf8mb4_unicode_ci,
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ocr_text` text COLLATE utf8mb4_unicode_ci,
  `storage_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `thumbnail_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transcription` text COLLATE utf8mb4_unicode_ci,
  `uploaded_at` datetime(6) NOT NULL,
  `uploaded_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `width` int DEFAULT NULL,
  `ai_summary` text COLLATE utf8mb4_unicode_ci COMMENT 'AI generated semantic summary',
  `embedding_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING' COMMENT 'Vector embedding status: PENDING, PROCESSING, COMPLETED, FAILED',
  `task_retry_count` int DEFAULT '0' COMMENT 'Number of retries for async tasks',
  `object_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `primary_backend` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `replica_backends` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `replication_status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_replicated_at` datetime DEFAULT NULL,
  `last_replication_error` text COLLATE utf8mb4_unicode_ci,
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_file_type` (`file_type`),
  KEY `idx_uploaded_at` (`uploaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `multimodal_tasks`
--

DROP TABLE IF EXISTS `multimodal_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `multimodal_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `execution_time` bigint DEFAULT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_url` longtext COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `platform_adapters`
--

DROP TABLE IF EXISTS `platform_adapters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_adapters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform_name` varchar(50) NOT NULL,
  `adapter_name` varchar(100) NOT NULL,
  `base_url` varchar(500) NOT NULL,
  `api_key` varchar(500) DEFAULT NULL,
  `auth_type` varchar(50) DEFAULT NULL,
  `auth_config` json DEFAULT NULL,
  `adapter_config` json DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  `last_health_check` timestamp NULL DEFAULT NULL,
  `health_status` varchar(20) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_adapter` (`platform_name`,`adapter_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台适配器配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `playground_conversations`
--

DROP TABLE IF EXISTS `playground_conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `playground_conversations` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workflow_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(240) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `playground_messages`
--

DROP TABLE IF EXISTS `playground_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `playground_messages` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `agent_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `playground_runs`
--

DROP TABLE IF EXISTS `playground_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `playground_runs` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workflow_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `workflow_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_input` text COLLATE utf8mb4_unicode_ci,
  `assistant_message` text COLLATE utf8mb4_unicode_ci,
  `trace_json` text COLLATE utf8mb4_unicode_ci,
  `graph_json` text COLLATE utf8mb4_unicode_ci,
  `artifacts_json` text COLLATE utf8mb4_unicode_ci,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `playground_workflows`
--

DROP TABLE IF EXISTS `playground_workflows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `playground_workflows` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `specialist_agent_ids_json` text COLLATE utf8mb4_unicode_ci,
  `router_prompt` text COLLATE utf8mb4_unicode_ci,
  `execution_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dag_subtasks_json` text COLLATE utf8mb4_unicode_ci,
  `agent_max_tokens` int DEFAULT NULL,
  `finalizer_enabled` tinyint(1) DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `prompt_templates`
--

DROP TABLE IF EXISTS `prompt_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prompt_templates` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `server_hardware_metrics`
--

DROP TABLE IF EXISTS `server_hardware_metrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server_hardware_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cpu_cores` int DEFAULT NULL,
  `cpu_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cpu_usage` double DEFAULT NULL,
  `disk_total` bigint DEFAULT NULL,
  `disk_usage` double DEFAULT NULL,
  `disk_used` bigint DEFAULT NULL,
  `error_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gpu_memory` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gpu_memory_usage` double DEFAULT NULL,
  `gpu_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gpu_usage` double DEFAULT NULL,
  `memory_total` bigint DEFAULT NULL,
  `memory_usage` double DEFAULT NULL,
  `memory_used` bigint DEFAULT NULL,
  `network_download` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `network_upload` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `online` bit(1) DEFAULT NULL,
  `os` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recorded_at` datetime(6) DEFAULT NULL,
  `timestamp` bigint NOT NULL,
  `server_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hardware_time` (`timestamp` DESC),
  KEY `idx_hardware_server_id_time` (`server_id`,`timestamp`)
) ENGINE=InnoDB AUTO_INCREMENT=34487 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `server_info`
--

DROP TABLE IF EXISTS `server_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cpu_cores` int DEFAULT NULL,
  `cpu_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `disk_total` bigint DEFAULT NULL,
  `first_online_time` datetime(6) DEFAULT NULL,
  `gpu_memory_total` bigint DEFAULT NULL,
  `gpu_model` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_offline_time` datetime(6) DEFAULT NULL,
  `last_online_time` datetime(6) DEFAULT NULL,
  `memory_total` bigint DEFAULT NULL,
  `online` bit(1) DEFAULT NULL,
  `os` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prometheus_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `server_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_rxvyqytlfl0x2u4kjnru7o3sr` (`server_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `session_tool_binding`
--

DROP TABLE IF EXISTS `session_tool_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session_tool_binding` (
  `session_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tool_ids` json DEFAULT NULL,
  `kb_ids` json DEFAULT NULL,
  `skill_ids` json DEFAULT NULL,
  `mcp_ids` json DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  KEY `idx_session_tool_binding_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skills`
--

DROP TABLE IF EXISTS `skills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `skill_name` varchar(100) NOT NULL,
  `skill_type` enum('API','KNOWLEDGE','COMPOSITE','SHELL') NOT NULL,
  `description` text,
  `mcp_metadata` json DEFAULT NULL,
  `skill_md_content` text,
  `api_endpoint` varchar(500) DEFAULT NULL,
  `api_method` varchar(10) DEFAULT NULL,
  `api_headers` json DEFAULT NULL,
  `knowledge_config_id` bigint DEFAULT NULL,
  `workflow_id` bigint DEFAULT NULL,
  `external_platform` varchar(50) DEFAULT NULL,
  `external_reference` varchar(500) DEFAULT NULL,
  `input_schema` json DEFAULT NULL,
  `output_schema` json DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE','DEPRECATED') DEFAULT NULL,
  `version` varchar(20) DEFAULT '1.0.0',
  `created_by` varchar(100) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shell_command` text,
  PRIMARY KEY (`id`),
  KEY `idx_skill_name` (`skill_name`),
  KEY `fk_skills_knowledge_config` (`knowledge_config_id`),
  KEY `fk_skills_workflow` (`workflow_id`),
  CONSTRAINT `fk_skills_knowledge_config` FOREIGN KEY (`knowledge_config_id`) REFERENCES `knowledge_configs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_skills_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflows` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能注册表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_replication_tasks`
--

DROP TABLE IF EXISTS `storage_replication_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_replication_tasks` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `object_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_backend` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_backend` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_locator` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_locator` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_REPAIR',
  `retry_count` int DEFAULT '0',
  `max_retries` int DEFAULT '8',
  `last_error` text COLLATE utf8mb4_unicode_ci,
  `last_attempt_at` datetime DEFAULT NULL,
  `next_retry_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_storage_replication_status` (`status`),
  KEY `idx_storage_replication_next_retry` (`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `structured_data_table`
--

DROP TABLE IF EXISTS `structured_data_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `structured_data_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `column_count` int DEFAULT NULL,
  `columns` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `row_count` int DEFAULT NULL,
  `table_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_department`
--

DROP TABLE IF EXISTS `sys_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `department_id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `department_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `leader` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_num` int DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `UK_h2oc1bqp4ayn30ljbaixb9y80` (`department_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_log_config`
--

DROP TABLE IF EXISTS `sys_log_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log_config` (
  `config_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_mail_config`
--

DROP TABLE IF EXISTS `sys_mail_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_mail_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `from_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `from_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `smtp_host` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `smtp_port` int DEFAULT NULL,
  `ssl_enabled` bit(1) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mailer_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'smtp',
  `imap_enabled` bit(1) DEFAULT NULL,
  `imap_host` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `imap_password` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `imap_port` int DEFAULT NULL,
  `imap_username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_mail_inbox`
--

DROP TABLE IF EXISTS `sys_mail_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_mail_inbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci,
  `content_html` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `folder` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `from_email` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `from_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `is_starred` bit(1) DEFAULT NULL,
  `message_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `subject` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_email` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ipokmfgn8t479yjw5oadssid4` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_mail_send_log`
--

DROP TABLE IF EXISTS `sys_mail_send_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_mail_send_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipients` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mailer_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mail_log_status` (`status`),
  KEY `idx_mail_log_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_mail_template`
--

DROP TABLE IF EXISTS `sys_mail_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_mail_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板代码',
  `subject` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮件主题',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮件内容',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认模板',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_code` (`code`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮件模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_provider_config`
--

DROP TABLE IF EXISTS `sys_provider_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_provider_config` (
  `provider_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_order` int NOT NULL,
  `enabled` bit(1) NOT NULL,
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `department_id` bigint DEFAULT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `UK_jqdita2l45v2gglry7bp8kl1f` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_system_config`
--

DROP TABLE IF EXISTS `sys_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ab7e1g2qdmt3uk49as42yho53` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_login_time` datetime DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK_51bvuyvihefoh4kp5syh2jpi4` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmjo3l593cr1835oo1asuch8u` (`user_id`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_message`
--

DROP TABLE IF EXISTS `system_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'INFO',
  `receiver_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sender_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `read` tinyint(1) DEFAULT '0',
  `expire_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `scope` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dedupe_key` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fingerprint` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `repeat_count` int DEFAULT '1',
  `last_occurred_at` datetime DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_receiver_id` (`receiver_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_read` (`read`),
  KEY `idx_system_message_scope` (`scope`),
  KEY `idx_system_message_dedupe_status` (`dedupe_key`,`status`),
  KEY `idx_system_message_last_occurred` (`last_occurred_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1544 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_message_user_state`
--

DROP TABLE IF EXISTS `system_message_user_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message_user_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint NOT NULL,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `read_at` datetime DEFAULT NULL,
  `dismissed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_message_user_state` (`message_id`,`user_id`),
  KEY `idx_system_message_user_state_user` (`user_id`),
  KEY `idx_system_message_user_state_message` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1544 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_info`
--

DROP TABLE IF EXISTS `task_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workflow_id` bigint NOT NULL,
  `workflow_instance_id` bigint DEFAULT NULL,
  `priority` enum('HIGH','NORMAL','LOW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('QUEUED','RUNNING','RETRYING','COMPLETED','FAILED','DEAD') COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_data` json DEFAULT NULL,
  `output_data` json DEFAULT NULL,
  `triggered_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `max_retries` int DEFAULT '3',
  `next_retry_at` datetime DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `error_stack` text COLLATE utf8mb4_unicode_ci,
  `dead_letter_reason` text COLLATE utf8mb4_unicode_ci,
  `queued_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `task_category` enum('WORKFLOW','SYNC') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_workflow_instance_id` (`workflow_instance_id`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status_priority` (`status`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_queue`
--

DROP TABLE IF EXISTS `task_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'GENERAL',
  `content` text COLLATE utf8mb4_unicode_ci,
  `priority` int DEFAULT '5',
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `result` text COLLATE utf8mb4_unicode_ci,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `retry_count` int DEFAULT '0',
  `max_retry` int DEFAULT '3',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `executor_node` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tool_catalog`
--

DROP TABLE IF EXISTS `tool_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tool_catalog` (
  `tool_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `schema_json` json DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `runtime_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'context_only',
  `health_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SYSTEM',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tool_execution_log`
--

DROP TABLE IF EXISTS `tool_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tool_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `agent_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tool_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `runtime_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `success` tinyint(1) NOT NULL DEFAULT '1',
  `error_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latency_ms` bigint DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tool_execution_log_session_id` (`session_id`),
  KEY `idx_tool_execution_log_agent_id` (`agent_id`),
  KEY `idx_tool_execution_log_tool_id` (`tool_id`),
  KEY `idx_tool_execution_log_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=392 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_settings`
--

DROP TABLE IF EXISTS `user_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `setting_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT 'general',
  `setting_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `setting_value` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_key` (`user_id`,`setting_key`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflow_definitions`
--

DROP TABLE IF EXISTS `workflow_definitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_definitions` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflow_instances`
--

DROP TABLE IF EXISTS `workflow_instances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_instances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_id` bigint NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `status` enum('RUNNING','SUCCESS','FAILED','TIMEOUT','CANCELLED') NOT NULL,
  `input_data` json DEFAULT NULL,
  `output_data` json DEFAULT NULL,
  `started_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` timestamp NULL DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `error_message` text,
  `error_stack` text,
  `triggered_by` varchar(100) DEFAULT NULL,
  `trigger_source` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_id` (`workflow_id`),
  CONSTRAINT `fk_workflow_instances_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflows` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流执行实例表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflow_nodes`
--

DROP TABLE IF EXISTS `workflow_nodes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_nodes` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `configuration` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `next_nodes` text COLLATE utf8mb4_unicode_ci,
  `position` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `workflow_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflow_steps`
--

DROP TABLE IF EXISTS `workflow_steps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_steps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_id` bigint NOT NULL,
  `step_order` int NOT NULL,
  `step_name` varchar(100) NOT NULL,
  `skill_id` bigint NOT NULL,
  `input_mapping` json DEFAULT NULL,
  `output_mapping` json DEFAULT NULL,
  `condition_expression` varchar(500) DEFAULT NULL,
  `depends_on` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `agent_id` bigint DEFAULT NULL,
  `step_type` enum('SKILL','AGENT','LOGIC') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `fk_workflow_steps_skill` (`skill_id`),
  CONSTRAINT `fk_workflow_steps_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_workflow_steps_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflows` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流步骤表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflow_traces`
--

DROP TABLE IF EXISTS `workflow_traces`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_traces` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(64) NOT NULL,
  `instance_id` bigint NOT NULL,
  `step_id` bigint DEFAULT NULL,
  `step_name` varchar(100) DEFAULT NULL,
  `skill_id` bigint DEFAULT NULL,
  `skill_name` varchar(100) DEFAULT NULL,
  `status` enum('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED') NOT NULL,
  `input_data` json DEFAULT NULL,
  `output_data` json DEFAULT NULL,
  `started_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` timestamp NULL DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `error_message` text,
  `error_details` json DEFAULT NULL,
  `cpu_usage` decimal(5,2) DEFAULT NULL,
  `memory_usage` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_status` (`status`),
  KEY `idx_started_at` (`started_at`),
  CONSTRAINT `fk_workflow_traces_instance` FOREIGN KEY (`instance_id`) REFERENCES `workflow_instances` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=704 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全链路追踪表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workflows`
--

DROP TABLE IF EXISTS `workflows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_name` varchar(100) NOT NULL,
  `description` text,
  `workflow_type` enum('SEQUENTIAL','PARALLEL','DAG') DEFAULT NULL,
  `workflow_definition` json NOT NULL,
  `timeout_seconds` int DEFAULT '300',
  `retry_policy` json DEFAULT NULL,
  `status` enum('DRAFT','ACTIVE','ARCHIVED') DEFAULT NULL,
  `version` varchar(20) DEFAULT '1.0.0',
  `created_by` varchar(100) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_name` (`workflow_name`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zeroclaw_analysis_reports`
--

DROP TABLE IF EXISTS `zeroclaw_analysis_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeroclaw_analysis_reports` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci,
  `details` text COLLATE utf8mb4_unicode_ci,
  `root_cause` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recommendations` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `analysis_start` timestamp NULL DEFAULT NULL,
  `analysis_end` timestamp NULL DEFAULT NULL,
  `data_start_time` bigint DEFAULT NULL,
  `data_end_time` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_zeroclaw_reports_agent_id` (`agent_id`),
  KEY `idx_zeroclaw_reports_type` (`report_type`),
  KEY `idx_zeroclaw_reports_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zeroclaw_configs`
--

DROP TABLE IF EXISTS `zeroclaw_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeroclaw_configs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `access_token` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `enable_analysis` tinyint(1) DEFAULT '1',
  `enable_self_healing` tinyint(1) DEFAULT '1',
  `heartbeat_interval` int DEFAULT '60',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT NULL,
  `agent_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_zeroclaw_configs_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zeroclaw_self_healing_logs`
--

DROP TABLE IF EXISTS `zeroclaw_self_healing_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeroclaw_self_healing_logs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_resource` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_reason` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `execution_details` text COLLATE utf8mb4_unicode_ci,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `before_snapshot` text COLLATE utf8mb4_unicode_ci,
  `after_snapshot` text COLLATE utf8mb4_unicode_ci,
  `started_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `auto_executed` tinyint(1) DEFAULT '1',
  `executed_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_zeroclaw_logs_action_type` (`action_type`),
  KEY `idx_zeroclaw_logs_status` (`status`),
  KEY `idx_zeroclaw_logs_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'orindb'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-11 15:07:28

-- flyway_schema_history data only; no application table data is included.
-- MySQL dump 10.13  Distrib 8.4.6, for macos15 (arm64)
--
-- Host: localhost    Database: orindb
-- ------------------------------------------------------
-- Server version	8.4.6

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','Initial schema','SQL','V1__Initial_schema.sql',NULL,'root','2026-01-23 01:47:37',76,1),(2,'2','Add system roles','SQL','V2__Add_system_roles.sql',NULL,'root','2026-01-23 02:20:00',17,1),(3,'3','Add default users','SQL','V3__Add_default_users.sql',NULL,'root','2026-01-23 02:20:10',5,1),(4,'4','Fix passwords','SQL','V4__Fix_passwords.sql',NULL,'root','2026-01-25 06:23:56',5,1),(5,'5','Semantic Asset Center','SQL','V5__Semantic_Asset_Center.sql',NULL,'root','2026-02-01 12:14:24',32,1),(6,'6','Resize TargetUrl LongText','SQL','V6__Resize_TargetUrl_LongText.sql',NULL,'root','2026-02-02 04:06:22',24,1),(7,'7','Fix Invalid Execution Times','SQL','V7__Fix_Invalid_Execution_Times.sql',NULL,'root','2026-02-02 05:15:50',10,1),(8,'8','Migrate Audit To Conversation','SQL','V8__Migrate_Audit_To_Conversation.sql',NULL,'root','2026-02-03 08:51:04',20,1),(9,'9','Add ZeroClaw Integration','SQL','V9__Add_ZeroClaw_Integration.sql',NULL,'root','2026-02-27 17:06:00',65,1),(10,'10','Add ZeroClaw AI Config','SQL','V10__Add_ZeroClaw_AI_Config.sql',NULL,'root','2026-03-10 18:13:17',11,1),(11,'11','Add Parent Child Chunking','SQL','V11__Add_Parent_Child_Chunking.sql',NULL,'root','2026-03-10 18:16:00',9,1),(12,'12','Add Description Model','SQL','V12__Add_Description_Model.sql',NULL,'root','2026-03-10 18:20:28',0,1),(13,'13','Add Desc Generation Model','SQL','V13__Add_Desc_Generation_Model.sql',NULL,'root','2026-03-10 18:20:28',0,1),(14,'14','Add Chunk Method And Size','SQL','V14__Add_Chunk_Method_And_Size.sql',NULL,'root','2026-03-10 18:20:28',0,1),(15,'15','Add User Bio Address','SQL','V15__Add_User_Bio_Address.sql',NULL,'root','2026-03-10 18:20:28',0,1),(16,'16','Add Embedding Provider And ApiKey','SQL','V16__Add_Embedding_Provider_And_ApiKey.sql',NULL,'root','2026-03-10 18:20:28',0,1),(17,'17','Multimodal Knowledge Base','SQL','V17__Multimodal_Knowledge_Base.sql',NULL,'root','2026-03-10 18:20:28',0,1),(18,'18','Add Knowledge Parsing Config','SQL','V18__Add_Knowledge_Parsing_Config.sql',NULL,'root','2026-03-10 18:20:28',0,1),(19,'19','Add Provider Config','SQL','V19__Add_Provider_Config.sql',NULL,'root','2026-03-10 18:22:42',18,1),(20,'20','Add Agent Metrics','SQL','V20__Add_Agent_Metrics.sql',NULL,'root','2026-03-11 06:43:04',11,1),(21,'21','Add Multimodal Fields','SQL','V21__Add_Multimodal_Fields.sql',NULL,'root','2026-03-11 06:50:33',51,1),(22,'22','Knowledge Sync Record','SQL','V22__Knowledge_Sync_Record.sql',NULL,'root','2026-03-11 06:50:33',4,1),(23,'23','Add RichText Enabled','SQL','V23__Add_RichText_Enabled.sql',NULL,'root','2026-03-11 13:16:16',36,1),(24,'24','Add KnowledgeBase Retrieval Config','SQL','V24__Add_KnowledgeBase_Retrieval_Config.sql',NULL,'root','2026-03-13 11:33:04',86,1),(25,'25','Add User Last Login Time','SQL','V25__Add_User_Last_Login_Time.sql',NULL,'root','2026-03-13 11:59:33',38,1),(26,'26','Fix User Role Field','SQL','V26__Fix_User_Role_Field.sql',NULL,'root','2026-03-13 12:06:51',6,1),(27,'27','Add Default User','SQL','V27__Add_Default_User.sql',NULL,'root','2026-03-13 12:18:49',8,1),(28,'28','Add User Last Login Time','SQL','V28__Add_User_Last_Login_Time.sql',NULL,'root','2026-03-13 14:43:04',0,1),(29,'29','Add Mail Config','SQL','V29__Add_Mail_Config.sql',NULL,'root','2026-03-13 17:02:13',20,1),(30,'30','Add MailerSend Config','SQL','V30__Add_MailerSend_Config.sql',NULL,'root','2026-03-13 18:14:38',30,1),(31,'31','Add Notification Config','SQL','V31__Add_Notification_Config.sql',NULL,'root','2026-03-13 18:14:38',16,1),(32,'32','Add Mail Send Log','SQL','V32__Add_Mail_Send_Log.sql',NULL,'root','2026-03-13 18:14:38',22,1),(33,'33','Add Mail Template','SQL','V33__Add_Mail_Template.sql',NULL,'root','2026-03-13 19:09:54',24,1),(34,'34','Add Task Queue Support','SQL','V34__Add_Task_Queue_Support.sql',NULL,'root','2026-03-18 09:05:41',46,1),(35,'35','Knowledge Sync Enhancement','SQL','V35__Knowledge_Sync_Enhancement.sql',NULL,'root','2026-03-18 19:28:30',240,1),(36,'40','Collaboration Task','SQL','V40__Collaboration_Task.sql',NULL,'root','2026-03-19 06:23:32',31,1),(37,'41','Task Queue','SQL','V41__Task_Queue.sql',NULL,'root','2026-03-19 16:18:09',50,1),(38,'42','System Message','SQL','V42__System_Message.sql',NULL,'root','2026-03-19 16:18:09',12,1),(39,'43','User Settings','SQL','V43__User_Settings.sql',NULL,'root','2026-03-19 16:18:09',8,1),(40,'44','Help Article','SQL','V44__Help_Article.sql',NULL,'root','2026-03-19 16:59:04',11,1),(41,'45','Add User Department','SQL','V45__Add_User_Department.sql',NULL,'root','2026-03-25 10:46:03',9,1),(42,'46','Create Department Table','SQL','V46__Create_Department_Table.sql',NULL,'root','2026-03-25 13:01:47',10,1),(43,'47','Add Role Department','SQL','V47__Add_Role_Department.sql',NULL,'root','2026-03-25 13:01:47',11,1),(44,'48','Update User Departments','SQL','V48__Update_User_Departments.sql',NULL,'root','2026-03-25 13:34:43',6,1),(45,'49','Create MCP Services Table','SQL','V49__Create_MCP_Services_Table.sql',NULL,'root','2026-03-25 17:53:43',21,1),(46,'50','Collab Package Schema','SQL','V50__Collab_Package_Schema.sql',NULL,'root','2026-03-25 17:53:43',24,1),(47,'51','Normalize Knowledge Type','SQL','V51__Normalize_Knowledge_Type.sql',NULL,'root','2026-03-27 08:54:07',63,1),(48,'52','Agent Chat Session','SQL','V52__Agent_Chat_Session.sql',NULL,'root','2026-03-28 14:54:37',9,1),(49,'53','agent chat session kb fields','SQL','V53__agent_chat_session_kb_fields.sql',NULL,'root','2026-03-28 19:08:33',29,1),(50,'54','Gateway Schema','SQL','V54__Gateway_Schema.sql',NULL,'root','2026-03-31 07:32:12',81,1),(51,'55','Add ApiKey Encrypted Secret','SQL','V55__Add_ApiKey_Encrypted_Secret.sql',NULL,'root','2026-04-06 07:01:31',84,1),(53,'57','Add integration audit log','SQL','V57__Add_integration_audit_log.sql',NULL,'root','2026-04-09 06:43:19',11,1),(54,'58','Add dify document mapping','SQL','V58__Add_dify_document_mapping.sql',NULL,'root','2026-04-09 06:43:19',14,1),(55,'59','Add idempotency key to sync change log','SQL','V59__Add_idempotency_key_to_sync_change_log.sql',NULL,'root','2026-04-09 06:43:19',14,1),(56,'60','Add task category to task info','SQL','V60__Add_task_category_to_task_info.sql',NULL,'root','2026-04-09 06:43:19',30,1),(57,'61','Fix server hardware metrics server columns','SQL','V61__Fix_server_hardware_metrics_server_columns.sql',NULL,'root','2026-04-09 07:45:55',168,1),(58,'62','Create Collab Session Tables','SQL','V62__Create_Collab_Session_Tables.sql',NULL,'root','2026-04-09 14:43:46',69,1),(59,'63','Add tool key and enabled to mcp services','SQL','V63__Add_tool_key_and_enabled_to_mcp_services.sql',NULL,'root','2026-04-13 10:55:43',29,1),(60,'64','Add Tool Calling Override To Agent Metadata','SQL','V64__Add_Tool_Calling_Override_To_Agent_Metadata.sql',NULL,'root','2026-04-18 13:49:32',37,1),(61,'65','Make sync change log agent id nullable','SQL','V65__Make_sync_change_log_agent_id_nullable.sql',NULL,'root','2026-04-19 17:39:17',44,1),(62,'66','Create Knowledge Graph Tables','SQL','V66__Create_Knowledge_Graph_Tables.sql',NULL,'root','2026-04-20 09:18:09',33,1),(63,'67','Backfill Knowledge Graphs','SQL','V67__Backfill_Knowledge_Graphs.sql',NULL,'root','2026-04-20 09:18:09',4,1),(64,'68','Add Error Message To Knowledge Graph','SQL','V68__Add_Error_Message_To_Knowledge_Graph.sql',NULL,'root','2026-04-20 14:11:38',35,1),(65,'69','Add Advanced System Roles','SQL','V69__Add_Advanced_System_Roles.sql',NULL,'root','2026-04-21 08:13:01',9,1),(66,'70','Grant Super Admin To Default Admin','SQL','V70__Grant_Super_Admin_To_Default_Admin.sql',NULL,'root','2026-04-21 10:04:13',7,1),(67,'71','Agent Tooling Refactor','SQL','V71__Agent_Tooling_Refactor.sql',NULL,'root','2026-04-21 11:45:38',56,1),(68,'72','Gateway Secret Center','SQL','V72__Gateway_Secret_Center.sql',NULL,'root','2026-04-22 11:07:02',45,1),(69,'73','Add Default Modern Mail Template','SQL','V73__Add_Default_Modern_Mail_Template.sql',NULL,'root','2026-04-22 11:21:35',6,1),(70,'74','Dual Object Storage','SQL','V74__Dual_Object_Storage.sql',NULL,'root','2026-04-22 17:22:54',114,1),(71,'75','Create Playground Workflows Table','SQL','V75__Create_Playground_Workflows_Table.sql',NULL,'root','2026-04-24 11:03:10',52,1),(72,'76','Create Playground Runtime Tables','SQL','V76__Create_Playground_Runtime_Tables.sql',NULL,'root','2026-04-24 11:38:00',52,1),(73,'77','Playground Workflow Execution Mode And Dag','SQL','V77__Playground_Workflow_Execution_Mode_And_Dag.sql',NULL,'root','2026-04-25 02:15:01',38,1),(74,'78','Add Playground Agent Max Tokens','SQL','V78__Add_Playground_Agent_Max_Tokens.sql',NULL,'root','2026-04-25 02:15:01',6,1),(75,'79','Integration Sync Core','SQL','V79__Integration_Sync_Core.sql',NULL,'root','2026-05-01 11:17:53',396,1),(76,'80','Notification Alert Mail Reliability','SQL','V80__Notification_Alert_Mail_Reliability.sql',NULL,'root','2026-05-04 07:49:22',101,1),(77,'81','Unified Gateway Default Local Routes','SQL','V81__Unified_Gateway_Default_Local_Routes.sql',NULL,'root','2026-05-05 14:18:47',15,1),(78,'82','Alert Notification Aggregation','SQL','V82__Alert_Notification_Aggregation.sql',NULL,'root','2026-05-06 18:50:15',626,1),(79,'83','Alert Rule Target And Window','SQL','V83__Alert_Rule_Target_And_Window.sql',NULL,'root','2026-05-06 20:00:25',125,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-11 15:07:28
