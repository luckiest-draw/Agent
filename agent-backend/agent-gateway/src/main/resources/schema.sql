-- Auth module
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    avatar VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(100),
    parent_code VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

-- Tenant module
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    enabled BOOLEAN DEFAULT TRUE,
    api_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_tenant_quota (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    max_documents BIGINT DEFAULT 1000,
    max_tokens_per_day BIGINT DEFAULT 100000,
    max_conversations BIGINT DEFAULT 100,
    max_agents BIGINT DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Knowledge module
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    file_size BIGINT,
    storage_path VARCHAR(500),
    status VARCHAR(20),
    chunk_count INTEGER DEFAULT 0,
    tenant_id BIGINT,
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    content TEXT,
    chunk_type VARCHAR(10),
    image_path VARCHAR(500),
    chunk_index INTEGER,
    vectorized BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Conversation module
CREATE TABLE IF NOT EXISTS conv_conversation (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    user_id BIGINT,
    tenant_id BIGINT,
    agent_config_id BIGINT,
    message_count INTEGER DEFAULT 0,
    parent_id BIGINT,
    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conv_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(10),
    content TEXT,
    image_url VARCHAR(500),
    token_count INTEGER,
    response_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Orchestration module
CREATE TABLE IF NOT EXISTS orch_agent_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    model VARCHAR(50),
    system_prompt TEXT,
    temperature DOUBLE PRECISION DEFAULT 0.7,
    max_tokens INTEGER DEFAULT 2048,
    prompt_template_id BIGINT,
    tenant_id BIGINT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orch_prompt_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    template TEXT,
    variables VARCHAR(500),
    tenant_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orch_tool_def (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parameters TEXT,
    tool_type VARCHAR(20),
    endpoint VARCHAR(500),
    tenant_id BIGINT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Workflow module
CREATE TABLE IF NOT EXISTS wf_workflow_def (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_workflow_node (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    node_type VARCHAR(20),
    label VARCHAR(100),
    agent_config_id BIGINT,
    tool_def_id BIGINT,
    position VARCHAR(50),
    node_config TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_workflow_edge (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    source_node_id BIGINT,
    target_node_id BIGINT,
    label VARCHAR(100),
    condition VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Monitor module
CREATE TABLE IF NOT EXISTS mon_api_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    model VARCHAR(50),
    method VARCHAR(10),
    path VARCHAR(200),
    status_code INTEGER,
    response_time_ms BIGINT,
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mon_token_usage (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    model VARCHAR(50),
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    usage_date VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
