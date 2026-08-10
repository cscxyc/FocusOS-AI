-- FocusOS AI Database Schema
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS focusos_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE focusos_db;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User settings table
CREATE TABLE IF NOT EXISTS user_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_setting (user_id, setting_key),
    INDEX idx_user_settings_user (user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Learning plans table
CREATE TABLE IF NOT EXISTS learning_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    goal TEXT,
    start_date DATE,
    end_date DATE,
    daily_target_minutes INT,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_learning_plans_user (user_id),
    INDEX idx_learning_plans_status (status),
    CONSTRAINT fk_learning_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Learning sessions table
CREATE TABLE IF NOT EXISTS learning_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT,
    subject VARCHAR(100),
    duration_minutes INT,
    session_date DATE,
    notes TEXT,
    focus_level INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_learning_sessions_user (user_id),
    INDEX idx_learning_sessions_plan (plan_id),
    INDEX idx_learning_sessions_date (session_date),
    CONSTRAINT fk_learning_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_sessions_plan FOREIGN KEY (plan_id) REFERENCES learning_plans(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Knowledge documents table
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    file_type VARCHAR(50),
    file_path VARCHAR(500),
    file_size BIGINT,
    category VARCHAR(100),
    tags TEXT,
    is_vectorized BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_documents_user (user_id),
    INDEX idx_knowledge_documents_category (category),
    CONSTRAINT fk_knowledge_documents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Document chunks table
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT,
    embedding_id VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_chunks_document (document_id),
    INDEX idx_document_chunks_index (document_id, chunk_index),
    CONSTRAINT fk_document_chunks_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Career profiles table
CREATE TABLE IF NOT EXISTS career_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(200),
    summary TEXT,
    skills TEXT,
    experience TEXT,
    education TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_career_profiles_user (user_id),
    CONSTRAINT fk_career_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Job applications table
CREATE TABLE IF NOT EXISTS job_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company VARCHAR(200),
    position VARCHAR(200),
    job_description TEXT,
    status VARCHAR(30) DEFAULT 'PENDING',
    match_score DOUBLE,
    notes TEXT,
    applied_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_job_applications_user (user_id),
    INDEX idx_job_applications_status (status),
    CONSTRAINT fk_job_applications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Schedule events table
CREATE TABLE IF NOT EXISTS schedule_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    event_date DATE,
    start_time TIME,
    end_time TIME,
    is_completed BOOLEAN DEFAULT FALSE,
    event_type VARCHAR(50),
    priority VARCHAR(30) DEFAULT 'MEDIUM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_schedule_events_user (user_id),
    INDEX idx_schedule_events_date (event_date),
    CONSTRAINT fk_schedule_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Conversation messages table
CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agent_type VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tokens_used INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_messages_user (user_id),
    INDEX idx_conversation_messages_agent (agent_type),
    CONSTRAINT fk_conversation_messages_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Sprint 8-C: Personal Memory System (用户长期记忆表)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID，严格用户隔离',
    memory_type VARCHAR(50) COMMENT '记忆类型: SKILL/PROJECT/EXPERIENCE/GOAL/LEARNING_PROGRESS/PREFERENCE/ACHIEVEMENT',
    memory_key VARCHAR(100) COMMENT '记忆Key: 技能名/项目名/目标名等',
    memory_value TEXT COMMENT '记忆内容: 具体描述或详情沉淀',
    source VARCHAR(200) COMMENT '来源: MANUAL/LEARNING_COMPLETED/PROJECT_SUBMISSION/RESUME/CHAT/MEMORY_AGENT 等',
    confidence DOUBLE COMMENT '可信度 0.0~1.0，根据证据强度判断',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_memory_user (user_id),
    INDEX idx_memory_type (memory_type),
    INDEX idx_memory_user_type_key (user_id, memory_type, memory_key),
    CONSTRAINT fk_user_memories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint 8-C Personal Memory: 用户长期成长沉淀（技能/项目/经验/目标/学习进度/偏好/成就）';

-- ============================================================
-- Sprint 8-D: Agent Evaluation Framework (评估记录表)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_evaluation_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID，严格用户隔离',
    workflow_id VARCHAR(64) COMMENT '关联的Workflow ID',
    agent_type VARCHAR(50) NOT NULL COMMENT '被评估的Agent类型',
    evaluation_type VARCHAR(50) NOT NULL COMMENT '评估类型: CAREER_ANALYSIS/RESUME_GENERATION/INTERVIEW/RAG_RETRIEVAL/MEMORY_EXTRACTION/GROWTH_PLAN',
    input_text TEXT COMMENT 'Agent输入（截断存储）',
    output_text TEXT COMMENT 'Agent输出（截断存储）',
    score INT COMMENT '综合质量评分0-100',
    metrics_json TEXT COMMENT '结构化指标JSON',
    feedback TEXT COMMENT '评估反馈/问题列表',
    prompt_version VARCHAR(20) COMMENT 'Prompt版本标识(A/B Testing)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_user (user_id),
    INDEX idx_eval_workflow (workflow_id),
    INDEX idx_eval_agent (agent_type),
    INDEX idx_eval_type (evaluation_type),
    INDEX idx_eval_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint 8-D Agent Evaluation: Agent输出质量评估记录';

-- ============================================================
-- Sprint 8-D Task6: Prompt 版本管理表
-- ============================================================
CREATE TABLE IF NOT EXISTS prompt_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_type VARCHAR(50) NOT NULL COMMENT 'Agent类型',
    version VARCHAR(20) NOT NULL COMMENT '版本号: v1/v2/vA/vB',
    prompt_content TEXT NOT NULL COMMENT 'Prompt内容',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否当前启用',
    description VARCHAR(500) COMMENT '版本描述',
    avg_score DOUBLE DEFAULT 0.0 COMMENT '平均评估得分',
    eval_count INT DEFAULT 0 COMMENT '评估次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pv_agent (agent_type),
    INDEX idx_pv_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint 8-D Prompt A/B Testing: Prompt版本管理';

-- ============================================================
-- Sprint 8-E Task 1: Workflow 状态持久化表
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL COMMENT 'Workflow 唯一标识（UUID 截断）',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    workflow_type VARCHAR(50) NOT NULL COMMENT 'Workflow 类型: CAREER_ANALYSIS/LEARNING_PLAN 等',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/RETRYING/PAUSED',
    current_task VARCHAR(200) COMMENT '当前执行的任务描述',
    progress INT COMMENT '执行进度 0-100',
    error_message TEXT COMMENT '错误信息（FAILED 状态时填充）',
    started_at DATETIME COMMENT '开始执行时间',
    completed_at DATETIME COMMENT '完成时间（SUCCESS / FAILED）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wf_user (user_id),
    INDEX idx_wf_workflow_id (workflow_id),
    INDEX idx_wf_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint 8-E Workflow 状态持久化';

-- ============================================================
-- Sprint 8-E Task 8: 用户配额表
-- ============================================================
CREATE TABLE IF NOT EXISTS user_quotas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户 ID（唯一）',
    tier VARCHAR(20) NOT NULL DEFAULT 'DEFAULT' COMMENT '用户等级: DEFAULT/PREMIUM',
    daily_token_limit BIGINT NOT NULL COMMENT '每日 Token 上限',
    used_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '当日已使用 Token 数',
    reset_date DATE NOT NULL COMMENT '配额重置日期（跨日重置）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quota_user (user_id),
    INDEX idx_quota_reset (reset_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint 8-E 用户 Token 配额管理';

-- ============================================================
-- Sprint 8-E Task 9: LLMCallLog 增加 estimated_cost 字段（如已存在表则 ALTER 补列）
-- ============================================================
-- 注意：通过 Hibernate ddl-auto=update 自动添加 estimated_cost 列；以下语句作为兜底
-- ALTER TABLE llm_call_logs ADD COLUMN IF NOT EXISTS estimated_cost DOUBLE COMMENT '估算成本（美元）';
