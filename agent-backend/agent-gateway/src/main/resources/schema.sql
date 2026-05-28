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
    skill_id BIGINT,
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

-- Skill module
CREATE TABLE IF NOT EXISTS orch_skill (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    agent_config_id BIGINT,
    workflow_id BIGINT,
    tenant_id BIGINT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orch_skill_tool (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    tool_def_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed: 4 built-in tools
INSERT INTO orch_tool_def (id, name, description, parameters, tool_type, tenant_id) VALUES
(1, 'web_search', '搜索互联网获取最新信息，返回网页摘要。适用于查新闻、实时数据、公开信息。', '{"query": "搜索关键词"}', 'built-in', NULL),
(2, 'wikipedia', '查询维基百科获取百科知识。适用于概念解释、历史事件、人物背景。', '{"query": "查询关键词"}', 'built-in', NULL),
(3, 'arxiv', '搜索arXiv学术论文库。适用于科研论文、技术文献查询。', '{"query": "搜索关键词"}', 'built-in', NULL),
(4, 'calculator', '执行数学计算和表达式求值。适用于数值计算、单位换算等。', '{"expression": "数学表达式"}', 'built-in', NULL)
ON CONFLICT (id) DO NOTHING;

-- Seed: 3 agent configs
INSERT INTO orch_agent_config (id, name, description, model, system_prompt, temperature, max_tokens, tenant_id) VALUES
(1, '联网搜索助手', '可联网查询最新信息，获取实时新闻和数据。', 'deepseek-chat', '你是一个智能助手，可以主动使用 web_search 工具搜索互联网获取最新信息。遇到需要实时数据、最新新闻或你不确定的信息时，请务必先搜索再回答。', 0.7, 2048, NULL),
(2, '知识检索助手', '可查询维基百科和学术论文，获取深度知识。', 'deepseek-chat', '你是一个知识渊博的助手，可以使用 wikipedia 和 arxiv 工具查询百科知识和学术论文。对于概念性问题、历史事件、科学知识，请使用工具获取准确信息后回答。', 0.7, 2048, NULL),
(3, '通用对话助手', '纯大语言模型对话，不使用外部工具。', 'deepseek-chat', '你是一个乐于助人的AI助手，回答各种问题，提供有用的建议。', 0.7, 2048, NULL)
ON CONFLICT (id) DO NOTHING;

-- Seed: 3 skills
INSERT INTO orch_skill (id, name, description, agent_config_id, tenant_id) VALUES
(1, '联网搜索', '搜索互联网获取最新信息、实时新闻、天气、股价等时效性内容。当你需要查最新数据时使用此技能。', 1, NULL),
(2, '知识查询', '查询维基百科百科条目和arXiv学术论文，获取深度知识。当你需要了解概念、历史、科学知识时使用此技能。', 2, NULL),
(3, '通用对话', '纯文本对话，不使用外部工具。适用于闲聊、写作、翻译、编程等不需要外部信息的场景。', 3, NULL)
ON CONFLICT (id) DO NOTHING;

-- Seed: skill-tool associations
INSERT INTO orch_skill_tool (skill_id, tool_def_id) VALUES
(1, 1)  -- 联网搜索 → web_search
ON CONFLICT DO NOTHING;
INSERT INTO orch_skill_tool (skill_id, tool_def_id) VALUES
(2, 2),  -- 知识查询 → wikipedia
(2, 3)   -- 知识查询 → arxiv
ON CONFLICT DO NOTHING;

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
