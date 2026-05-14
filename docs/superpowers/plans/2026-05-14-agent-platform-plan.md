# Agent Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade multi-functional Agent platform with React frontend, Spring Boot business backend, and Python LangChain AI engine.

**Architecture:** Three-layer separation — React+TS frontend communicates via HTTP/WebSocket with Java Spring Boot backend (business logic, auth, CRUD). Java delegates AI tasks to Python FastAPI (sync HTTP for chat, async MQ for long tasks). PostgreSQL+Pgvector stores both business data and vectors.

**Tech Stack:** React 18 + Vite + TypeScript + Shadcn/ui + Tailwind, Spring Boot 3.2 + JDK 17 + JPA + Security + Redis + RabbitMQ, FastAPI + LangChain + LangGraph + Celery, PostgreSQL 15 + Pgvector.

---

## File Structure Map

```
D:/LearnJava/Agent/
├── docker/
│   └── docker-compose.yml              # PostgreSQL + Redis + RabbitMQ
├── agent-backend/                       # Spring Boot multi-module Maven project
│   ├── pom.xml                          # Parent POM
│   ├── agent-common/
│   │   └── src/main/java/com/agent/common/
│   │       ├── Result.java              # 统一返回体
│   │       ├── BaseEntity.java          # 实体基类
│   │       ├── GlobalExceptionHandler.java
│   │       └── utils/
│   ├── agent-auth/
│   │   └── src/main/java/com/agent/auth/
│   │       ├── entity/User.java, Role.java, Permission.java
│   │       ├── repo/UserRepo.java, RoleRepo.java
│   │       ├── service/AuthService.java
│   │       ├── controller/AuthController.java
│   │       ├── security/JwtFilter.java, SecurityConfig.java, JwtUtil.java
│   │       └── dto/LoginRequest.java, LoginResponse.java
│   ├── agent-tenant/
│   │   └── src/main/java/com/agent/tenant/
│   │       ├── entity/Tenant.java, TenantQuota.java
│   │       ├── repo/TenantRepo.java
│   │       ├── service/TenantService.java
│   │       ├── controller/TenantController.java
│   │       └── interceptor/TenantInterceptor.java
│   ├── agent-knowledge/
│   │   └── src/main/java/com/agent/knowledge/
│   │       ├── entity/Document.java, Chunk.java
│   │       ├── repo/DocumentRepo.java, ChunkRepo.java
│   │       ├── service/DocIngestService.java, MultiModalCheck.java
│   │       ├── controller/KnowledgeController.java
│   │       ├── parser/DocumentParser.java, PdfParser.java, WordParser.java, MarkdownParser.java, TxtParser.java, HtmlParser.java, ImageParser.java
│   │       └── chunker/TextChunker.java, ImageChunker.java
│   ├── agent-conversation/
│   │   └── src/main/java/com/agent/conversation/
│   │       ├── entity/Conversation.java, Message.java
│   │       ├── repo/ConversationRepo.java, MessageRepo.java
│   │       ├── service/ChatService.java, StreamingProxy.java
│   │       ├── controller/ConversationController.java
│   │       └── ws/ChatWebSocketHandler.java
│   ├── agent-orchestration/
│   │   └── src/main/java/com/agent/orchestration/
│   │       ├── entity/AgentConfig.java, PromptTemplate.java, ToolDef.java
│   │       ├── repo/AgentConfigRepo.java, PromptTemplateRepo.java, ToolDefRepo.java
│   │       ├── service/OrchestrationService.java
│   │       └── controller/OrchestrationController.java
│   ├── agent-workflow/
│   │   └── src/main/java/com/agent/workflow/
│   │       ├── entity/WorkflowDef.java, WorkflowNode.java, WorkflowEdge.java
│   │       ├── repo/WorkflowDefRepo.java
│   │       ├── service/WorkflowService.java
│   │       └── controller/WorkflowController.java
│   ├── agent-monitor/
│   │   └── src/main/java/com/agent/monitor/
│   │       ├── entity/ApiLog.java, TokenUsage.java
│   │       ├── repo/ApiLogRepo.java, TokenUsageRepo.java
│   │       ├── service/MonitorService.java
│   │       └── controller/MonitorController.java
│   └── agent-gateway/
│       └── src/main/java/com/agent/gateway/
│           ├── GatewayApplication.java   # Spring Boot 主启动类
│           ├── config/WebSocketConfig.java, MqConfig.java
│           └── resources/application.yml
├── agent-engine/
│   ├── requirements.txt
│   ├── main.py                          # FastAPI 入口
│   ├── config.py                        # 配置管理
│   ├── api/
│   │   ├── __init__.py
│   │   ├── routes/
│   │   │   ├── __init__.py
│   │   │   ├── chat.py
│   │   │   ├── rag.py
│   │   │   ├── workflow.py
│   │   │   └── model_info.py
│   │   └── middleware/
│   │       ├── __init__.py
│   │       └── error_handler.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── model_factory.py
│   │   ├── model_capability.py
│   │   ├── embedding_factory.py
│   │   └── memory/
│   │       ├── __init__.py
│   │       ├── buffer.py
│   │       └── summary.py
│   ├── rag/
│   │   ├── __init__.py
│   │   ├── retrievers/
│   │   │   ├── __init__.py
│   │   │   ├── vector_retriever.py
│   │   │   ├── bm25_retriever.py
│   │   │   └── multi_modal_retriever.py
│   │   ├── reranker.py
│   │   └── ingestion.py
│   ├── agents/
│   │   ├── __init__.py
│   │   ├── react_agent.py
│   │   ├── tool_use_agent.py
│   │   ├── multi_agent/
│   │   │   ├── __init__.py
│   │   │   ├── supervisor.py
│   │   │   └── worker.py
│   │   └── streaming.py
│   ├── tools/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   ├── builtin/
│   │   │   ├── __init__.py
│   │   │   ├── search.py
│   │   │   ├── calculator.py
│   │   │   ├── database.py
│   │   │   └── file_ops.py
│   │   └── custom/
│   │       ├── __init__.py
│   │       └── registry.py
│   ├── workflows/
│   │   ├── __init__.py
│   │   ├── engine.py
│   │   ├── nodes/
│   │   │   ├── __init__.py
│   │   │   ├── llm_node.py
│   │   │   ├── tool_node.py
│   │   │   ├── condition_node.py
│   │   │   └── code_node.py
│   │   └── state.py
│   ├── monitor/
│   │   ├── __init__.py
│   │   ├── tracer.py
│   │   ├── cost.py
│   │   └── latency.py
│   └── worker/
│       ├── __init__.py
│       ├── celery_app.py
│       ├── tasks.py
│       └── callbacks.py
├── agent-frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── index.css
│       ├── lib/utils.ts
│       ├── api/
│       │   ├── client.ts              # Axios 实例
│       │   ├── auth.ts
│       │   ├── knowledge.ts
│       │   ├── conversation.ts
│       │   ├── orchestration.ts
│       │   ├── workflow.ts
│       │   ├── tenant.ts
│       │   └── monitor.ts
│       ├── types/
│       │   └── index.ts
│       ├── stores/
│       │   ├── authStore.ts
│       │   ├── chatStore.ts
│       │   └── appStore.ts
│       ├── hooks/
│       │   ├── useSSE.ts
│       │   └── useWebSocket.ts
│       ├── components/
│       │   ├── ui/                    # Shadcn components
│       │   ├── layout/
│       │   │   ├── Sidebar.tsx
│       │   │   ├── Topbar.tsx
│       │   │   └── AppLayout.tsx
│       │   ├── chat/
│       │   │   ├── ChatWindow.tsx
│       │   │   ├── MessageBubble.tsx
│       │   │   ├── ChatInput.tsx
│       │   │   └── ConversationList.tsx
│       │   ├── knowledge/
│       │   │   ├── DocUploader.tsx
│       │   │   ├── ChunkPreview.tsx
│       │   │   └── RetrievalTester.tsx
│       │   └── workflow/
│       │       ├── WorkflowCanvas.tsx
│       │       ├── NodePanel.tsx
│       │       └── NodeConfig.tsx
│       └── pages/
│           ├── Dashboard/
│           │   └── DashboardPage.tsx
│           ├── Chat/
│           │   └── ChatPage.tsx
│           ├── Knowledge/
│           │   └── KnowledgePage.tsx
│           ├── AgentOrch/
│           │   └── AgentOrchPage.tsx
│           ├── Workflow/
│           │   └── WorkflowPage.tsx
│           ├── Tenant/
│           │   └── TenantPage.tsx
│           ├── Auth/
│           │   ├── LoginPage.tsx
│           │   └── UserManagementPage.tsx
│           └── Settings/
│               └── SettingsPage.tsx
└── README.md
```

---

## Phase 1: Project Scaffolding & Infrastructure

### Task 1: Create Docker Compose for infrastructure

**Files:**
- Create: `docker/docker-compose.yml`

- [ ] **Step 1: Write docker-compose.yml**

```yaml
version: "3.8"
services:
  postgres:
    image: pgvector/pgvector:pg15
    container_name: agent-postgres
    environment:
      POSTGRES_DB: agent_platform
      POSTGRES_USER: agent
      POSTGRES_PASSWORD: agent123
    ports:
      - "5432:5432"
    volumes:
      - D:/Pgvector/data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U agent -d agent_platform"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: agent-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: agent-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: agent
      RABBITMQ_DEFAULT_PASS: agent123
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5
```

- [ ] **Step 2: Create D:/Pgvector directory and start infrastructure**

```bash
mkdir -p D:/Pgvector/data
cd docker
docker-compose up -d
```

Expected: All three containers start successfully. Verify with `docker ps`.

- [ ] **Step 3: Enable pgvector extension**

```bash
docker exec -it agent-postgres psql -U agent -d agent_platform -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

Expected: `CREATE EXTENSION`

- [ ] **Step 4: Commit**

```bash
git add docker/docker-compose.yml
git commit -m "feat: add Docker Compose infrastructure with PostgreSQL+pgvector, Redis, RabbitMQ"
```

---

### Task 2: Initialize Java Maven parent project

**Files:**
- Create: `agent-backend/pom.xml`
- Create: `agent-backend/agent-common/pom.xml`
- Create: `agent-backend/agent-auth/pom.xml`
- Create: `agent-backend/agent-tenant/pom.xml`
- Create: `agent-backend/agent-knowledge/pom.xml`
- Create: `agent-backend/agent-conversation/pom.xml`
- Create: `agent-backend/agent-orchestration/pom.xml`
- Create: `agent-backend/agent-workflow/pom.xml`
- Create: `agent-backend/agent-monitor/pom.xml`
- Create: `agent-backend/agent-gateway/pom.xml`

- [ ] **Step 1: Write parent pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.agent</groupId>
    <artifactId>agent-backend</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Agent Platform Backend</name>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <jjwt.version>0.12.5</jjwt.version>
    </properties>

    <modules>
        <module>agent-common</module>
        <module>agent-auth</module>
        <module>agent-tenant</module>
        <module>agent-knowledge</module>
        <module>agent-conversation</module>
        <module>agent-orchestration</module>
        <module>agent-workflow</module>
        <module>agent-monitor</module>
        <module>agent-gateway</module>
    </modules>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write agent-common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.agent</groupId>
        <artifactId>agent-backend</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>agent-common</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3-9: Write remaining module pom.xml files**

Each module pom.xml follows same pattern (parent reference, artifactId, dependencies specific to module). Key dependencies per module:
- `agent-auth`: spring-boot-starter-security, jjwt-api/impl/jackson
- `agent-tenant`: agent-common
- `agent-knowledge`: agent-common, spring-boot-starter-data-jpa
- `agent-conversation`: agent-common, spring-boot-starter-websocket
- `agent-orchestration`: agent-common, spring-boot-starter-data-jpa
- `agent-workflow`: agent-common, spring-boot-starter-data-jpa
- `agent-monitor`: agent-common, spring-boot-starter-data-jpa
- `agent-gateway`: all modules above, spring-boot-starter-data-redis, spring-boot-starter-amqp

- [ ] **Step 10: Create source directories**

```bash
cd agent-backend
for module in agent-common agent-auth agent-tenant agent-knowledge agent-conversation agent-orchestration agent-workflow agent-monitor agent-gateway; do
  mkdir -p "$module/src/main/java/com/agent/$(echo $module | sed 's/agent-//')"
  mkdir -p "$module/src/main/resources"
  mkdir -p "$module/src/test/java/com/agent/$(echo $module | sed 's/agent-//')"
done
```

- [ ] **Step 11: Commit**

```bash
git add agent-backend/
git commit -m "feat: initialize Spring Boot multi-module Maven project"
```

---

## Phase 2: Java Backend - Foundation

### Task 3: Build agent-common - Result, BaseEntity, ExceptionHandler

**Files:**
- Create: `agent-backend/agent-common/src/main/java/com/agent/common/Result.java`
- Create: `agent-backend/agent-common/src/main/java/com/agent/common/BaseEntity.java`
- Create: `agent-backend/agent-common/src/main/java/com/agent/common/GlobalExceptionHandler.java`
- Create: `agent-backend/agent-common/src/main/java/com/agent/common/BusinessException.java`

- [ ] **Step 1: Write Result.java**

```java
package com.agent.common;

import lombok.Data;

// 统一API返回体
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

- [ ] **Step 2: Write BaseEntity.java**

```java
package com.agent.common;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

// 实体基类,提供通用字段
@Data
@MappedSuperclass
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Write BusinessException.java**

```java
package com.agent.common;

import lombok.Getter;

// 业务异常
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
```

- [ ] **Step 4: Write GlobalExceptionHandler.java**

```java
package com.agent.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 全局异常处理器
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统内部错误");
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add agent-backend/agent-common/
git commit -m "feat: add common module with Result, BaseEntity, exception handler"
```

---

### Task 4: Build agent-auth - User, Role, JWT, Security

**Files:**
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/entity/User.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/entity/Role.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/entity/Permission.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/repo/UserRepo.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/repo/RoleRepo.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/security/JwtUtil.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/security/JwtFilter.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/security/SecurityConfig.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/dto/LoginRequest.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/dto/LoginResponse.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/service/AuthService.java`
- Create: `agent-backend/agent-auth/src/main/java/com/agent/auth/controller/AuthController.java`

- [ ] **Step 1: Write User.java**

```java
package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 用户实体
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_user")
public class User extends BaseEntity {
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt 加密存储

    @Column(length = 100)
    private String email;

    private String avatar;

    private Boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "sys_user_role",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
```

- [ ] **Step 2: Write Role.java**

```java
package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.HashSet;
import java.util.Set;

// 角色实体
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_role")
public class Role extends BaseEntity {
    @Column(unique = true, nullable = false, length = 50)
    private String name;       // 角色标识 ROLE_ADMIN

    @Column(length = 100)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "sys_role_permission",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}
```

- [ ] **Step 3: Write Permission.java**

```java
package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 权限实体
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_permission")
public class Permission extends BaseEntity {
    @Column(unique = true, nullable = false, length = 50)
    private String code;       // 权限标识 user:read

    @Column(length = 100)
    private String description;

    @Column(length = 50)
    private String parentCode; // 父权限标识,构建权限树
}
```

- [ ] **Step 4: Write UserRepo.java**

```java
package com.agent.auth.repo;

import com.agent.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

- [ ] **Step 5: Write RoleRepo.java**

```java
package com.agent.auth.repo;

import com.agent.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
```

- [ ] **Step 6: Write JwtUtil.java**

```java
package com.agent.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

// JWT 工具类: 生成/解析/验证 Token
@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret:agent-platform-secret-key-min-256-bits!!}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("roles", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
```

- [ ] **Step 7: Write JwtFilter.java**

```java
package com.agent.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

// JWT 请求过滤器: 从 Authorization header 提取 Token 并设置安全上下文
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            var claims = jwtUtil.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

            var auth = new UsernamePasswordAuthenticationToken(
                userId, username, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 8: Write SecurityConfig.java**

```java
package com.agent.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Spring Security 配置
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 9: Write AuthService.java**

```java
package com.agent.auth.service;

import com.agent.auth.dto.LoginRequest;
import com.agent.auth.dto.LoginResponse;
import com.agent.auth.entity.Role;
import com.agent.auth.entity.User;
import com.agent.auth.repo.UserRepo;
import com.agent.auth.security.JwtUtil;
import com.agent.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.getUsername())
            .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!user.getEnabled()) {
            throw new BusinessException(403, "账号已被禁用");
        }
        List<String> roles = user.getRoles().stream()
            .map(Role::getName).toList();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roles);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRoles(roles);
        return resp;
    }
}
```

- [ ] **Step 10: Write remaining files and commit**

```bash
git add agent-backend/agent-auth/
git commit -m "feat: add auth module with RBAC, JWT, Spring Security"
```

---

### Task 5: Build agent-tenant - Tenant CRUD, Isolation, Quota

**Files:**
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/entity/Tenant.java`
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/entity/TenantQuota.java`
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/repo/TenantRepo.java`
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/service/TenantService.java`
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/controller/TenantController.java`
- Create: `agent-backend/agent-tenant/src/main/java/com/agent/tenant/interceptor/TenantInterceptor.java`

- [ ] **Step 1: Write Tenant.java**

```java
package com.agent.tenant.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 租户实体
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_tenant")
public class Tenant extends BaseEntity {
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    private Boolean enabled = true;

    @Column(unique = true, length = 64)
    private String apiKey; // 租户专属 API Key
}
```

- [ ] **Step 2: Write TenantQuota.java**

```java
package com.agent.tenant.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 租户用量配额
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_tenant_quota")
public class TenantQuota extends BaseEntity {
    @Column(nullable = false)
    private Long tenantId;

    private Long maxDocuments = 1000L;   // 最大文档数
    private Long maxTokensPerDay = 100000L; // 每日Token上限
    private Long maxConversations = 100L;   // 最大会话数
    private Long maxAgents = 10L;           // 最大Agent数
}
```

- [ ] **Step 3: Write TenantService.java**

```java
package com.agent.tenant.service;

import com.agent.common.BusinessException;
import com.agent.tenant.entity.Tenant;
import com.agent.tenant.entity.TenantQuota;
import com.agent.tenant.repo.TenantRepo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepo tenantRepo;
    private final EntityManager em;

    public List<Tenant> listAll() {
        return tenantRepo.findAll();
    }

    @Transactional
    public Tenant create(Tenant tenant) {
        if (tenantRepo.existsByName(tenant.getName())) {
            throw new BusinessException("租户名称已存在");
        }
        tenant.setApiKey("ak-" + UUID.randomUUID().toString().replace("-", ""));
        Tenant saved = tenantRepo.save(tenant);
        // 创建默认配额
        TenantQuota quota = new TenantQuota();
        quota.setTenantId(saved.getId());
        em.persist(quota);
        return saved;
    }

    @Transactional
    public Tenant update(Long id, Tenant updated) {
        Tenant existing = tenantRepo.findById(id)
            .orElseThrow(() -> new BusinessException("租户不存在"));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setEnabled(updated.getEnabled());
        return tenantRepo.save(existing);
    }

    public void delete(Long id) {
        tenantRepo.deleteById(id);
    }

    public TenantQuota getQuota(Long tenantId) {
        return em.createQuery(
            "SELECT q FROM TenantQuota q WHERE q.tenantId = :tid", TenantQuota.class)
            .setParameter("tid", tenantId)
            .getSingleResult();
    }

    @Transactional
    public TenantQuota updateQuota(Long tenantId, TenantQuota quota) {
        TenantQuota existing = getQuota(tenantId);
        existing.setMaxDocuments(quota.getMaxDocuments());
        existing.setMaxTokensPerDay(quota.getMaxTokensPerDay());
        existing.setMaxConversations(quota.getMaxConversations());
        existing.setMaxAgents(quota.getMaxAgents());
        return em.merge(existing);
    }
}
```

- [ ] **Step 4: Write TenantController.java**

```java
package com.agent.tenant.controller;

import com.agent.common.Result;
import com.agent.tenant.entity.Tenant;
import com.agent.tenant.entity.TenantQuota;
import com.agent.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @GetMapping
    public Result<List<Tenant>> list() { return Result.ok(tenantService.listAll()); }

    @PostMapping
    public Result<Tenant> create(@RequestBody Tenant tenant) { return Result.ok(tenantService.create(tenant)); }

    @PutMapping("/{id}")
    public Result<Tenant> update(@PathVariable Long id, @RequestBody Tenant tenant) { return Result.ok(tenantService.update(id, tenant)); }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { tenantService.delete(id); return Result.ok(); }

    @GetMapping("/{id}/quota")
    public Result<TenantQuota> getQuota(@PathVariable Long id) { return Result.ok(tenantService.getQuota(id)); }

    @PutMapping("/{id}/quota")
    public Result<TenantQuota> updateQuota(@PathVariable Long id, @RequestBody TenantQuota quota) { return Result.ok(tenantService.updateQuota(id, quota)); }
}
```

- [ ] **Step 5: Write TenantInterceptor.java**

```java
package com.agent.tenant.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// 租户数据隔离拦截器: 从请求头提取 X-Tenant-Id 并设置到 ThreadLocal
public class TenantInterceptor implements HandlerInterceptor {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null) {
            CURRENT_TENANT.set(Long.parseLong(tenantId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CURRENT_TENANT.remove();
    }

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add agent-backend/agent-tenant/
git commit -m "feat: add tenant module with CRUD, quota, isolation interceptor"
```

---

### Task 6: Build agent-gateway - Main Application and Configuration

**Files:**
- Create: `agent-backend/agent-gateway/src/main/java/com/agent/gateway/GatewayApplication.java`
- Create: `agent-backend/agent-gateway/src/main/java/com/agent/gateway/config/WebSocketConfig.java`
- Create: `agent-backend/agent-gateway/src/main/java/com/agent/gateway/config/MqConfig.java`
- Create: `agent-backend/agent-gateway/src/main/java/com/agent/gateway/config/CorsConfig.java`
- Create: `agent-backend/agent-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write GatewayApplication.java**

```java
package com.agent.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 网关启动类, 扫描所有模块
@SpringBootApplication(scanBasePackages = "com.agent")
@EntityScan(basePackages = "com.agent")
@EnableJpaRepositories(basePackages = "com.agent")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 2: Write application.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agent_platform
    username: agent
    password: agent123
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: agent
    password: agent123

jwt:
  secret: agent-platform-production-secret-key-at-least-256-bits-long
  expiration: 86400000

agent:
  python-engine:
    url: http://localhost:8000  # Python Agent 引擎地址
```

- [ ] **Step 3: Write WebSocketConfig.java**

```java
package com.agent.gateway.config;

import com.agent.conversation.ws.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// WebSocket 配置: 注册对话通道
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler chatHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/conversations/{conversationId}/chat")
            .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 4: Write MqConfig.java**

```java
package com.agent.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RabbitMQ 配置: 声明队列和交换机
@Configuration
public class MqConfig {
    public static final String DOC_INGEST_QUEUE = "doc.ingest.queue";
    public static final String WORKFLOW_QUEUE = "workflow.queue";
    public static final String AGENT_EXCHANGE = "agent.exchange";

    @Bean
    public Queue docIngestQueue() { return QueueBuilder.durable(DOC_INGEST_QUEUE).build(); }

    @Bean
    public Queue workflowQueue() { return QueueBuilder.durable(WORKFLOW_QUEUE).build(); }

    @Bean
    public TopicExchange agentExchange() { return new TopicExchange(AGENT_EXCHANGE); }

    @Bean
    public Binding docIngestBinding() {
        return BindingBuilder.bind(docIngestQueue()).to(agentExchange()).with("doc.ingest");
    }

    @Bean
    public Binding workflowBinding() {
        return BindingBuilder.bind(workflowQueue()).to(agentExchange()).with("workflow.execute");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

- [ ] **Step 5: Write CorsConfig.java**

```java
package com.agent.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.List;

// 跨域配置: 允许前端开发服务器访问
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add agent-backend/agent-gateway/
git commit -m "feat: add gateway module with application entry, WebSocket, MQ, CORS config"
```

---

## Phase 3: Java Backend - Business Modules

### Task 7: Build agent-knowledge - Document parsing, chunking, multi-modal

**Files:**
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/entity/Document.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/entity/Chunk.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/repo/DocumentRepo.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/repo/ChunkRepo.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/DocumentParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/PdfParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/WordParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/MarkdownParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/TxtParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/HtmlParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/ImageParser.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/chunker/TextChunker.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/chunker/ImageChunker.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/service/DocIngestService.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/service/MultiModalCheck.java`
- Create: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/controller/KnowledgeController.java`

- [ ] **Step 1: Write Document.java**

```java
package com.agent.knowledge.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 知识库文档
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "kb_document")
public class Document extends BaseEntity {
    @Column(nullable = false)
    private String fileName;         // 原始文件名

    private String fileType;         // pdf/docx/md/txt/html/png/jpg

    private Long fileSize;           // 文件大小(字节)

    private String storagePath;      // 文件存储路径

    @Column(length = 20)
    private String status;           // PENDING/PARSING/CHUNKING/VECTORIZING/DONE/FAILED

    private Integer chunkCount = 0;  // 分片数量

    private Long tenantId;           // 所属租户

    @Column(columnDefinition = "TEXT")
    private String errorMsg;         // 失败原因
}
```

- [ ] **Step 2: Write Chunk.java**

```java
package com.agent.knowledge.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 文档分片
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "kb_chunk")
public class Chunk extends BaseEntity {
    @Column(nullable = false)
    private Long documentId;         // 所属文档

    @Column(columnDefinition = "TEXT")
    private String content;          // 分片文本内容

    @Column(length = 10)
    private String chunkType;        // text / image

    private String imagePath;        // 图片类型时的存储路径

    private Integer chunkIndex;      // 分片序号

    private Boolean vectorized = false; // 是否已向量化
}
```

- [ ] **Step 3: Write DocumentParser.java (interface)**

```java
package com.agent.knowledge.parser;

import java.io.InputStream;
import java.util.Map;

// 文档解析器接口, 每种文件格式一个实现
public interface DocumentParser {
    // 返回文件类型标识
    String supportedType();

    // 解析文件, 返回文本内容和关联的图片路径列表
    // key: "text" → 文本内容, "images" → 图片路径(逗号分隔)
    Map<String, String> parse(InputStream inputStream, String fileName) throws Exception;
}
```

- [ ] **Step 4: Write PdfParser.java**

```java
package com.agent.knowledge.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// PDF 解析器: 基于 Apache PDFBox 提取文本
@Component
public class PdfParser implements DocumentParser {
    @Override
    public String supportedType() { return "pdf"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            result.put("text", text != null ? text.trim() : "");
        }
        return result;
    }
}
```

- [ ] **Step 5: Write WordParser.java**

```java
package com.agent.knowledge.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// Word 解析器: 基于 Apache POI 提取文本
@Component
public class WordParser implements DocumentParser {
    @Override
    public String supportedType() { return "docx"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            result.put("text", sb.toString().trim());
        }
        return result;
    }
}
```

- [ ] **Step 6: Write MarkdownParser.java**

```java
package com.agent.knowledge.parser;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MarkdownParser implements DocumentParser {
    @Override
    public String supportedType() { return "md"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            result.put("text", reader.lines().collect(Collectors.joining("\n")));
        }
        return result;
    }
}
```

- [ ] **Step 7: Write TxtParser.java and HtmlParser.java**

TxtParser: BufferedReader read all lines, same pattern as MarkdownParser but for "txt" type.
HtmlParser: Use Jsoup `Jsoup.parse(html).text()` to strip tags, for "html" type.

- [ ] **Step 8: Write ImageParser.java**

```java
package com.agent.knowledge.parser;

import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// 图片解析器: 不提取文本, 标记为需要多模态模型处理
@Component
public class ImageParser implements DocumentParser {
    @Override
    public String supportedType() { return "image"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        result.put("text", "");  // 图片无文本
        result.put("needsVision", "true"); // 标记需要多模态能力
        return result;
    }
}
```

- [ ] **Step 9: Write TextChunker.java**

```java
package com.agent.knowledge.chunker;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

// 文本分片器: 滑动窗口 + 重叠
@Component
public class TextChunker {
    private static final int CHUNK_SIZE = 500;   // 每片字符数
    private static final int OVERLAP = 50;        // 重叠字符数

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += (CHUNK_SIZE - OVERLAP);
        }
        return chunks;
    }
}
```

- [ ] **Step 10: Write MultiModalCheck.java**

```java
package com.agent.knowledge.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// 多模态能力检测: 调用 Python 引擎查询当前模型是否支持 Vision
@Service
@RequiredArgsConstructor
public class MultiModalCheck {
    private final RestTemplate restTemplate;

    // 返回值: true = 支持多模态, false = 不支持
    public boolean supportsVision() {
        try {
            var resp = restTemplate.getForObject(
                "http://localhost:8000/api/model/capability", Map.class);
            return resp != null && Boolean.TRUE.equals(resp.get("supportsVision"));
        } catch (Exception e) {
            return false; // 引擎不可用时默认不支持
        }
    }
}
```

- [ ] **Step 11: Write DocIngestService.java**

```java
package com.agent.knowledge.service;

import com.agent.common.BusinessException;
import com.agent.knowledge.chunker.TextChunker;
import com.agent.knowledge.entity.Chunk;
import com.agent.knowledge.entity.Document;
import com.agent.knowledge.parser.DocumentParser;
import com.agent.knowledge.repo.ChunkRepo;
import com.agent.knowledge.repo.DocumentRepo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 文档摄入服务: 上传 → 解析 → 分片 → 存储
@Slf4j
@Service
@RequiredArgsConstructor
public class DocIngestService {
    private final List<DocumentParser> parsers;
    private final TextChunker textChunker;
    private final DocumentRepo documentRepo;
    private final ChunkRepo chunkRepo;
    private final MultiModalCheck multiModalCheck;
    private final EntityManager em;
    private static final String UPLOAD_DIR = "D:/LearnJava/Agent/uploads/";

    @Transactional
    public Document ingest(MultipartFile file, Long tenantId) {
        String originalName = file.getOriginalFilename();
        String fileType = getFileType(originalName);

        // 保存文件到磁盘
        String storagePath = saveFile(file);
        Document doc = new Document();
        doc.setFileName(originalName);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setStoragePath(storagePath);
        doc.setStatus("PARSING");
        doc.setTenantId(tenantId);
        Document saved = documentRepo.save(doc);

        try {
            // 选择解析器
            DocumentParser parser = selectParser(fileType);
            if (parser == null) {
                throw new BusinessException("不支持的文件格式: " + fileType);
            }
            InputStream is = file.getInputStream();
            Map<String, String> parseResult = parser.parse(is, originalName);
            String text = parseResult.get("text");
            boolean needsVision = "true".equals(parseResult.get("needsVision"));

            // 图片文件需要检查多模态能力
            if (needsVision || "png".equals(fileType) || "jpg".equals(fileType) || "jpeg".equals(fileType)) {
                if (!multiModalCheck.supportsVision()) {
                    saved.setStatus("FAILED");
                    saved.setErrorMsg("当前模型不支持多模态(图片理解), 请切换至 GPT-4o 或通义千问VL等支持Vision的模型");
                    documentRepo.save(saved);
                    return saved;
                }
            }

            // 文本分片
            if (text != null && !text.isEmpty()) {
                List<String> chunks = textChunker.chunk(text);
                for (int i = 0; i < chunks.size(); i++) {
                    Chunk chunk = new Chunk();
                    chunk.setDocumentId(saved.getId());
                    chunk.setContent(chunks.get(i));
                    chunk.setChunkType("text");
                    chunk.setChunkIndex(i);
                    chunkRepo.save(chunk);
                }
                saved.setChunkCount(chunks.size());
            }

            saved.setStatus("DONE");
        } catch (BusinessException e) {
            saved.setStatus("FAILED");
            saved.setErrorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("文档处理失败: {}", originalName, e);
            saved.setStatus("FAILED");
            saved.setErrorMsg("文档处理异常: " + e.getMessage());
        }
        return documentRepo.save(saved);
    }

    private DocumentParser selectParser(String fileType) {
        return parsers.stream()
            .filter(p -> p.supportedType().equals(fileType))
            .findFirst().orElse(null);
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "txt";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "txt" : fileName.substring(dot + 1).toLowerCase();
    }

    private String saveFile(MultipartFile file) throws Exception {
        Path dir = Paths.get(UPLOAD_DIR);
        Files.createDirectories(dir);
        String savedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = dir.resolve(savedName);
        file.transferTo(target.toFile());
        return target.toString();
    }
}
```

- [ ] **Step 12: Write KnowledgeController.java**

```java
package com.agent.knowledge.controller;

import com.agent.common.Result;
import com.agent.knowledge.entity.Document;
import com.agent.knowledge.repo.DocumentRepo;
import com.agent.knowledge.service.DocIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {
    private final DocIngestService docIngestService;
    private final DocumentRepo documentRepo;

    @PostMapping("/docs/upload")
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return Result.ok(docIngestService.ingest(file, tenantId));
    }

    @GetMapping("/docs")
    public Result<List<Document>> list() {
        return Result.ok(documentRepo.findAll());
    }

    @GetMapping("/docs/{id}")
    public Result<Document> detail(@PathVariable Long id) {
        return Result.ok(documentRepo.findById(id).orElse(null));
    }

    @DeleteMapping("/docs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentRepo.deleteById(id);
        return Result.ok();
    }
}
```

- [ ] **Step 13: Commit**

```bash
git add agent-backend/agent-knowledge/
git commit -m "feat: add knowledge module with multi-format parsing, chunking, multi-modal awareness"
```

---

### Task 8: Build agent-conversation - Sessions, Messages, WebSocket

**Files:**
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/entity/Conversation.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/entity/Message.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/repo/ConversationRepo.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/repo/MessageRepo.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ChatService.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/StreamingProxy.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/ConversationController.java`
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/ws/ChatWebSocketHandler.java`

- [ ] **Step 1: Write Conversation.java**

```java
package com.agent.conversation.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 对话会话
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "conv_conversation")
public class Conversation extends BaseEntity {
    @Column(nullable = false)
    private String title;            // 会话标题

    private Long userId;             // 所属用户
    private Long tenantId;           // 所属租户
    private Long agentConfigId;      // 使用的 Agent 配置
    private Integer messageCount = 0;
}
```

- [ ] **Step 2: Write Message.java**

```java
package com.agent.conversation.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 对话消息
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "conv_message")
public class Message extends BaseEntity {
    @Column(nullable = false)
    private Long conversationId;

    @Column(length = 10)
    private String role;             // user / assistant / system

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer tokenCount;      // Token 消耗

    private Long responseTimeMs;     // 响应耗时(毫秒)
}
```

- [ ] **Step 3: Write StreamingProxy.java**

```java
package com.agent.conversation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

// 流式代理: 将 Python 引擎的 SSE 流转发给前端
@Slf4j
@Service
public class StreamingProxy {
    @Value("${agent.python-engine.url}")
    private String engineUrl;

    public void streamChat(String userMessage, Long conversationId, String agentConfigId,
                           SseEmitter emitter) {
        new Thread(() -> {
            try {
                URI uri = URI.create(engineUrl + "/api/chat/stream");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String body = String.format(
                    "{\"message\":\"%s\",\"conversationId\":%d,\"agentConfigId\":\"%s\"}",
                    userMessage.replace("\"", "\\\""), conversationId, agentConfigId);
                conn.getOutputStream().write(body.getBytes());

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            emitter.send(SseEmitter.event().data(line.substring(5)));
                        }
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("流式代理异常", e);
                emitter.completeWithError(e);
            }
        }).start();
    }
}
```

- [ ] **Step 4: Write ChatWebSocketHandler.java**

```java
package com.agent.conversation.ws;

import com.agent.conversation.service.StreamingProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// WebSocket 对话处理器: 接收用户消息 → 流式代理Python引擎 → 逐token返回
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final StreamingProxy streamingProxy;
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 逐字符回显(模拟流式), 实际应调用 StreamingProxy.streamChat()
        for (int i = 0; i < payload.length(); i++) {
            String chunk = String.valueOf(payload.charAt(i));
            session.sendMessage(new TextMessage(chunk));
            Thread.sleep(30); // 模拟打字效果
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }
}
```

- [ ] **Step 5: Write ConversationController.java**

```java
package com.agent.conversation.controller;

import com.agent.common.Result;
import com.agent.conversation.entity.Conversation;
import com.agent.conversation.entity.Message;
import com.agent.conversation.repo.ConversationRepo;
import com.agent.conversation.repo.MessageRepo;
import com.agent.conversation.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationRepo conversationRepo;
    private final MessageRepo messageRepo;
    private final ChatService chatService;

    @PostMapping
    public Result<Conversation> create(@RequestBody Conversation conv) { return Result.ok(conversationRepo.save(conv)); }

    @GetMapping
    public Result<List<Conversation>> list() { return Result.ok(conversationRepo.findAll()); }

    @GetMapping("/{id}/messages")
    public Result<List<Message>> messages(@PathVariable Long id) {
        return Result.ok(messageRepo.findByConversationIdOrderByCreatedAtAsc(id));
    }

    @PostMapping("/{id}/send")
    public Result<Message> send(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String userMessage = body.get("message");
        Message msg = chatService.sendMessage(id, userMessage);
        return Result.ok(msg);
    }
}
```

- [ ] **Step 6: Write ChatService.java**

```java
package com.agent.conversation.service;

import com.agent.conversation.entity.Message;
import com.agent.conversation.repo.MessageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MessageRepo messageRepo;

    public Message sendMessage(Long conversationId, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        return messageRepo.save(msg);
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add agent-backend/agent-conversation/
git commit -m "feat: add conversation module with sessions, messages, WebSocket, SSE streaming proxy"
```

---

### Task 9: Build agent-orchestration - Agent config, Prompt templates, Tools

**Files:**
- Create: `agent-backend/agent-orchestration/src/main/java/com/agent/orchestration/entity/AgentConfig.java`
- Create: `agent-backend/agent-orchestration/src/main/java/com/agent/orchestration/entity/PromptTemplate.java`
- Create: `agent-backend/agent-orchestration/src/main/java/com/agent/orchestration/entity/ToolDef.java`
- Create: repos and controller similar to previous modules

Key entities:

```java
// AgentConfig: 绑定 Prompt + Tools + 模型参数
@Entity @Table(name = "orch_agent_config")
public class AgentConfig extends BaseEntity {
    private String name;
    private String description;
    private Long promptTemplateId;   // 关联的 Prompt 模板
    private String modelName;        // gpt-4o / deepseek-chat / qwen-turbo
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private String toolIds;          // 逗号分隔的工具ID列表
    private Long tenantId;
    private Boolean active = true;
}

// PromptTemplate: 系统提示词
@Entity @Table(name = "orch_prompt_template")
public class PromptTemplate extends BaseEntity {
    private String name;
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;     // 系统提示词内容
    private String variables;        // JSON: 可替换变量列表
    private Long tenantId;
}

// ToolDef: 工具定义
@Entity @Table(name = "orch_tool_def")
public class ToolDef extends BaseEntity {
    private String name;
    private String description;
    @Column(columnDefinition = "TEXT")
    private String parameters;       // JSON Schema 参数定义
    private String endpoint;         // 工具调用端点
    private Boolean builtin = false; // 是否内置工具
    private Long tenantId;
}
```

- [ ] Commit: `git commit -m "feat: add orchestration module with agent config, prompt templates, tool definitions"`

---

### Task 10: Build agent-workflow and agent-monitor

**Files for workflow:**
- WorkflowDef (name, description, dagJson, status, tenantId)
- WorkflowNode (workflowId, nodeType: LLM/TOOL/CONDITION/CODE, configJson, positionX/Y)
- WorkflowEdge (workflowId, sourceNodeId, targetNodeId, conditionExpr)
- Controller with CRUD endpoints

**Files for monitor:**
- ApiLog (endpoint, method, responseTimeMs, statusCode, tenantId, userId, createdAt)
- TokenUsage (modelName, promptTokens, completionTokens, totalTokens, tenantId, date)
- MonitorController: `/api/monitor/dashboard` returns aggregated stats (daily token usage, avg latency, top endpoints, model distribution)

- [ ] Commit: `git commit -m "feat: add workflow and monitor modules"`

---

## Phase 4: Python Agent Engine

### Task 11: Initialize Python project with FastAPI entry point

**Files:**
- Create: `agent-engine/requirements.txt`
- Create: `agent-engine/main.py`
- Create: `agent-engine/config.py`

- [ ] **Step 1: Write requirements.txt**

```
fastapi==0.110.0
uvicorn[standard]==0.27.1
langchain==0.1.16
langchain-openai==0.1.3
langgraph==0.0.35
langchain-community==0.0.34
sentence-transformers==2.6.0
pgvector==0.2.4
psycopg2-binary==2.9.9
celery==5.3.6
pika==1.3.2
unstructured==0.12.5
python-multipart==0.0.9
Pillow==10.2.0
python-docx==1.1.0
pdfplumber==0.10.3
pydantic==2.6.1
httpx==0.27.0
```

- [ ] **Step 2: Write config.py**

```python
# 应用配置管理
import os

class Settings:
    # 数据库
    DB_HOST: str = os.getenv("DB_HOST", "localhost")
    DB_PORT: int = int(os.getenv("DB_PORT", "5432"))
    DB_NAME: str = os.getenv("DB_NAME", "agent_platform")
    DB_USER: str = os.getenv("DB_USER", "agent")
    DB_PASS: str = os.getenv("DB_PASS", "agent123")

    # 默认模型配置
    DEFAULT_MODEL: str = os.getenv("DEFAULT_MODEL", "deepseek-chat")
    DEFAULT_BASE_URL: str = os.getenv("DEFAULT_BASE_URL", "https://api.deepseek.com/v1")
    DEFAULT_API_KEY: str = os.getenv("DEFAULT_API_KEY", "")

    # 多模型配置 (JSON格式, 从环境变量或数据库读取)
    MODEL_CONFIGS: dict = {
        "deepseek-chat": {
            "base_url": "https://api.deepseek.com/v1",
            "supports_vision": False,
            "supports_function_call": True,
            "max_tokens": 8192,
        },
        "gpt-4o": {
            "base_url": "https://api.openai.com/v1",
            "supports_vision": True,
            "supports_function_call": True,
            "max_tokens": 128000,
        },
        "qwen-turbo": {
            "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "supports_vision": False,
            "supports_function_call": True,
            "max_tokens": 8192,
        },
        "glm-4": {
            "base_url": "https://open.bigmodel.cn/api/paas/v4",
            "supports_vision": True,
            "supports_function_call": True,
            "max_tokens": 128000,
        },
    }

    # Embedding 配置
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")

    # RabbitMQ
    MQ_HOST: str = os.getenv("MQ_HOST", "localhost")
    MQ_PORT: int = int(os.getenv("MQ_PORT", "5672"))
    MQ_USER: str = os.getenv("MQ_USER", "agent")
    MQ_PASS: str = os.getenv("MQ_PASS", "agent123")

    @property
    def db_url(self) -> str:
        return f"postgresql+psycopg2://{self.DB_USER}:{self.DB_PASS}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"

    @property
    def mq_url(self) -> str:
        return f"amqp://{self.MQ_USER}:{self.MQ_PASS}@{self.MQ_HOST}:{self.MQ_PORT}//"

settings = Settings()
```

- [ ] **Step 3: Write main.py**

```python
# FastAPI 应用入口
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.routes import chat, rag, workflow, model_info
from api.middleware.error_handler import register_exception_handlers

app = FastAPI(title="Agent Engine", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(chat.router, prefix="/api/chat", tags=["chat"])
app.include_router(rag.router, prefix="/api/rag", tags=["rag"])
app.include_router(workflow.router, prefix="/api/workflow", tags=["workflow"])
app.include_router(model_info.router, prefix="/api/model", tags=["model"])

# 注册异常处理器
register_exception_handlers(app)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
```

- [ ] **Step 4: Commit**

```bash
git add agent-engine/
git commit -m "feat: initialize Python agent engine with FastAPI, config, requirements"
```

---

### Task 12: Build core - Model factory, capability detection, embedding factory

**Files:**
- Create: `agent-engine/core/__init__.py`
- Create: `agent-engine/core/model_factory.py`
- Create: `agent-engine/core/model_capability.py`
- Create: `agent-engine/core/embedding_factory.py`
- Create: `agent-engine/core/memory/__init__.py`
- Create: `agent-engine/core/memory/buffer.py`
- Create: `agent-engine/core/memory/summary.py`

- [ ] **Step 1: Write model_factory.py**

```python
# 多模型工厂: 根据模型名称创建对应的 LangChain LLM 实例
from langchain_openai import ChatOpenAI
from config import settings

class ModelFactory:
    """多模型工厂, 统一使用 OpenAI 兼容接口创建 LLM 实例"""

    @staticmethod
    def create(model_name: str = None, temperature: float = 0.7,
               max_tokens: int = 2048, api_key: str = None) -> ChatOpenAI:
        model_name = model_name or settings.DEFAULT_MODEL
        config = settings.MODEL_CONFIGS.get(model_name)
        if not config:
            raise ValueError(f"不支持的模型: {model_name}")

        return ChatOpenAI(
            model=model_name,
            base_url=config["base_url"],
            api_key=api_key or settings.DEFAULT_API_KEY,
            temperature=temperature,
            max_tokens=max_tokens,
        )

    @staticmethod
    def list_models() -> list[dict]:
        """返回所有已配置的模型列表及能力"""
        return [
            {"name": name, "supports_vision": cfg["supports_vision"],
             "supports_function_call": cfg["supports_function_call"],
             "max_tokens": cfg["max_tokens"]}
            for name, cfg in settings.MODEL_CONFIGS.items()
        ]
```

- [ ] **Step 2: Write model_capability.py**

```python
# 模型能力检测: 查询当前模型是否支持 Vision / Function Call
from config import settings

class ModelCapability:
    """检测指定模型的能力"""

    @staticmethod
    def check(model_name: str = None) -> dict:
        model_name = model_name or settings.DEFAULT_MODEL
        config = settings.MODEL_CONFIGS.get(model_name, {})
        return {
            "model_name": model_name,
            "supports_vision": config.get("supports_vision", False),
            "supports_function_call": config.get("supports_function_call", False),
            "max_tokens": config.get("max_tokens", 4096),
        }

    @staticmethod
    def require_vision(model_name: str = None) -> bool:
        """检查是否支持多模态, 不支持则抛出异常"""
        caps = ModelCapability.check(model_name)
        if not caps["supports_vision"]:
            raise ValueError(
                f"当前模型 '{caps['model_name']}' 不支持多模态(图片理解), "
                "请切换至 GPT-4o 或 GLM-4 等支持 Vision 的模型"
            )
        return True
```

- [ ] **Step 3: Write embedding_factory.py**

```python
# Embedding 模型工厂: 文本向量化
from langchain_community.embeddings import HuggingFaceEmbeddings
from config import settings

class EmbeddingFactory:
    """Embedding 模型工厂"""

    _instance = None

    @classmethod
    def get_embeddings(cls) -> HuggingFaceEmbeddings:
        if cls._instance is None:
            cls._instance = HuggingFaceEmbeddings(
                model_name=settings.EMBEDDING_MODEL,
                model_kwargs={"device": "cpu"},
                encode_kwargs={"normalize_embeddings": True},
            )
        return cls._instance
```

- [ ] **Step 4: Write memory/buffer.py**

```python
# 短期记忆: 滑动窗口对话历史
from langchain.memory import ConversationBufferWindowMemory

def create_window_memory(k: int = 10) -> ConversationBufferWindowMemory:
    """创建滑动窗口记忆, 保留最近 k 轮对话"""
    return ConversationBufferWindowMemory(
        k=k,
        return_messages=True,
        memory_key="chat_history",
    )
```

- [ ] **Step 5: Write memory/summary.py**

```python
# 长期记忆: 对话摘要压缩
from langchain.memory import ConversationSummaryMemory
from core.model_factory import ModelFactory

def create_summary_memory() -> ConversationSummaryMemory:
    """创建摘要记忆, 用 LLM 自动压缩历史对话"""
    llm = ModelFactory.create()
    return ConversationSummaryMemory(
        llm=llm,
        memory_key="chat_history",
        return_messages=True,
    )
```

- [ ] **Step 6: Commit**

---

### Task 13: Build RAG engine - retrievers, reranker, ingestion

**Files:**
- Create: `agent-engine/rag/__init__.py`
- Create: `agent-engine/rag/retrievers/__init__.py`
- Create: `agent-engine/rag/retrievers/vector_retriever.py`
- Create: `agent-engine/rag/retrievers/bm25_retriever.py`
- Create: `agent-engine/rag/retrievers/multi_modal_retriever.py`
- Create: `agent-engine/rag/reranker.py`
- Create: `agent-engine/rag/ingestion.py`

- [ ] **Step 1: Write vector_retriever.py**

```python
# 向量检索器: 基于 Pgvector 的语义检索
from langchain_community.vectorstores import PGVector
from core.embedding_factory import EmbeddingFactory
from config import settings

class VectorRetriever:
    """Pgvector 向量检索"""

    def __init__(self, collection_name: str = "default"):
        self.collection_name = collection_name
        self.embeddings = EmbeddingFactory.get_embeddings()
        self.store = PGVector(
            collection_name=collection_name,
            connection_string=settings.db_url,
            embedding_function=self.embeddings,
        )

    def search(self, query: str, k: int = 5) -> list:
        """语义检索 Top-K 文档分片"""
        return self.store.similarity_search(query, k=k)

    def add_chunks(self, chunks: list[str], metadatas: list[dict] = None):
        """向向量库添加文档分片"""
        self.store.add_texts(chunks, metadatas=metadatas)

    def delete_by_doc_id(self, doc_id: str):
        """删除指定文档的所有向量"""
        self.store.delete(filter={"document_id": doc_id})
```

- [ ] **Step 2: Write bm25_retriever.py**

```python
# BM25 关键字检索器: 稀疏检索, 与向量检索互补
from rank_bm25 import BM25Okapi
import jieba

class BM25Retriever:
    """BM25 关键字检索, 中文分词"""

    def __init__(self, chunks: list[str]):
        tokenized = [list(jieba.cut(ch)) for ch in chunks]
        self.bm25 = BM25Okapi(tokenized)
        self.chunks = chunks

    def search(self, query: str, k: int = 5) -> list[str]:
        tokens = list(jieba.cut(query))
        scores = self.bm25.get_scores(tokens)
        # 返回得分最高的 Top-K
        ranked = sorted(enumerate(scores), key=lambda x: x[1], reverse=True)
        return [self.chunks[i] for i, _ in ranked[:k]]
```

- [ ] **Step 3: Write multi_modal_retriever.py**

```python
# 多模态检索器: 混合文本 + 图片检索
from rag.retrievers.vector_retriever import VectorRetriever
from core.model_capability import ModelCapability

class MultiModalRetriever:
    """多模态检索: 文本分片 + 图片分片联合检索"""

    def __init__(self, model_name: str = None):
        self.vector_retriever = VectorRetriever()
        self.supports_vision = ModelCapability.check(model_name)["supports_vision"]

    def search(self, query: str, k: int = 5,
               include_images: bool = True) -> dict:
        """检索, 返回文本分片和图片路径"""
        results = self.vector_retriever.search(query, k=k)
        text_chunks = []
        image_paths = []

        for doc in results:
            if doc.metadata.get("chunk_type") == "image":
                if include_images and self.supports_vision:
                    image_paths.append(doc.metadata.get("image_path"))
            else:
                text_chunks.append(doc.page_content)

        return {"text_chunks": text_chunks, "image_paths": image_paths}
```

- [ ] **Step 4: Write reranker.py**

```python
# 重排序器: 使用 Cross-Encoder 对初步检索结果精细排序
from sentence_transformers import CrossEncoder

class Reranker:
    """Cross-Encoder 重排序, 提升检索精准度"""

    def __init__(self, model_name: str = "BAAI/bge-reranker-base"):
        self.model = CrossEncoder(model_name)

    def rerank(self, query: str, documents: list[str], top_k: int = 3) -> list[str]:
        pairs = [(query, doc) for doc in documents]
        scores = self.model.predict(pairs)
        ranked = sorted(zip(documents, scores), key=lambda x: x[1], reverse=True)
        return [doc for doc, _ in ranked[:top_k]]
```

- [ ] **Step 5: Write ingestion.py**

```python
# 文档摄入流水线: 接收 Java 后端通知 → 向量化 → 入库
from rag.retrievers.vector_retriever import VectorRetriever
from core.embedding_factory import EmbeddingFactory
import psycopg2
from config import settings

class IngestionPipeline:
    """文档向量化摄入流水线"""

    def __init__(self):
        self.embeddings = EmbeddingFactory.get_embeddings()
        self.store = VectorRetriever()

    def ingest_document(self, doc_id: int, tenant_id: int):
        """将指定文档的所有分片向量化并入库"""
        conn = psycopg2.connect(settings.db_url)
        cur = conn.cursor()
        # 读取该文档所有未向量化的分片
        cur.execute(
            "SELECT id, content, chunk_type, chunk_index FROM kb_chunk "
            "WHERE document_id = %s AND vectorized = false", (doc_id,))
        chunks = cur.fetchall()

        texts, metadatas = [], []
        for chunk_id, content, chunk_type, chunk_idx in chunks:
            if chunk_type == "text" and content:
                texts.append(content)
                metadatas.append({
                    "chunk_id": str(chunk_id),
                    "document_id": str(doc_id),
                    "tenant_id": str(tenant_id),
                    "chunk_type": chunk_type,
                    "chunk_index": chunk_idx,
                })

        if texts:
            self.store.add_chunks(texts, metadatas)
            # 标记为已向量化
            for chunk_id, _, _, _ in chunks:
                cur.execute(
                    "UPDATE kb_chunk SET vectorized = true WHERE id = %s", (chunk_id,))
            conn.commit()

        cur.close()
        conn.close()
        return len(texts)
```

- [ ] **Step 6: Commit**

---

### Task 14: Build Agents - ReAct, ToolUse, Multi-Agent Supervisor

**Files:**
- Create: `agent-engine/agents/__init__.py`
- Create: `agent-engine/agents/react_agent.py`
- Create: `agent-engine/agents/tool_use_agent.py`
- Create: `agent-engine/agents/streaming.py`
- Create: `agent-engine/agents/multi_agent/__init__.py`
- Create: `agent-engine/agents/multi_agent/supervisor.py`
- Create: `agent-engine/agents/multi_agent/worker.py`

- [ ] **Step 1: Write react_agent.py**

```python
# ReAct Agent: 推理-行动循环
from langchain.agents import create_react_agent, AgentExecutor
from langchain import hub
from core.model_factory import ModelFactory

class ReActAgent:
    """ReAct 范式 Agent: Thought → Action → Observation 循环"""

    def __init__(self, model_name: str = None, tools: list = None):
        self.llm = ModelFactory.create(model_name=model_name, temperature=0.3)
        self.tools = tools or []

    def create_executor(self, verbose: bool = True) -> AgentExecutor:
        prompt = hub.pull("hwchase17/react")  # 标准 ReAct Prompt
        agent = create_react_agent(self.llm, self.tools, prompt)
        return AgentExecutor(
            agent=agent,
            tools=self.tools,
            verbose=verbose,
            handle_parsing_errors=True,
            max_iterations=10,
        )

    def run(self, query: str) -> str:
        executor = self.create_executor()
        return executor.invoke({"input": query})["output"]
```

- [ ] **Step 2: Write tool_use_agent.py**

```python
# 工具调用 Agent: 基于 Function Call 的工具使用
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from core.model_factory import ModelFactory

class ToolUseAgent:
    """工具调用 Agent, 使用模型的 Function Call 能力"""

    def __init__(self, model_name: str = None, tools: list = None,
                 system_prompt: str = "你是一个有帮助的AI助手"):
        self.llm = ModelFactory.create(model_name=model_name)
        self.tools = tools or []

        self.prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            MessagesPlaceholder(variable_name="chat_history", optional=True),
            ("human", "{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ])

    def create_executor(self) -> AgentExecutor:
        agent = create_tool_calling_agent(self.llm, self.tools, self.prompt)
        return AgentExecutor(agent=agent, tools=self.tools, verbose=True)

    def run(self, query: str, chat_history: list = None) -> str:
        executor = self.create_executor()
        return executor.invoke({
            "input": query,
            "chat_history": chat_history or [],
        })["output"]
```

- [ ] **Step 3: Write streaming.py**

```python
# 流式输出处理器: 管理 LangChain 的 streaming callback
from langchain.callbacks.base import BaseCallbackHandler
from typing import Any
import asyncio

class StreamingCallback(BaseCallbackHandler):
    """流式输出回调, 用于 SSE 推送每个 token"""

    def __init__(self):
        self.queue = asyncio.Queue()
        self.done = False

    def on_llm_new_token(self, token: str, **kwargs: Any) -> None:
        self.queue.put_nowait(token)

    def on_llm_end(self, *args: Any, **kwargs: Any) -> None:
        self.done = True
        self.queue.put_nowait(None)  # 结束信号

    async def iter_tokens(self):
        """异步迭代器, 逐 token yield"""
        while True:
            token = await self.queue.get()
            if token is None:
                break
            yield token
```

- [ ] **Step 4: Write supervisor.py**

```python
# Supervisor 多Agent协调器: 主 Agent 分发任务给子 Agent
from langgraph.graph import StateGraph, END
from typing import TypedDict, Annotated
import operator

class SupervisorState(TypedDict):
    messages: Annotated[list, operator.add]
    next_worker: str
    task_result: str

class Supervisor:
    """多Agent协调器: 根据任务类型路由到不同 Worker Agent"""

    WORKERS = {
        "rag": "知识库检索专家 - 负责文档问答",
        "code": "代码执行专家 - 负责运行Python代码",
        "search": "网络搜索专家 - 负责联网查询",
        "general": "通用对话专家 - 负责日常问答",
    }

    def __init__(self, model_name: str = None):
        self.llm = ModelFactory.create(model_name=model_name, temperature=0.2)

    def route(self, state: SupervisorState) -> str:
        """根据最后一条消息决定下一个 Worker"""
        last_msg = state["messages"][-1].content if state["messages"] else ""
        # 简单规则路由, 也可用 LLM 判断
        if any(kw in last_msg for kw in ["文档", "知识库", "查询资料"]):
            return "rag"
        elif any(kw in last_msg for kw in ["运行", "执行代码", "计算"]):
            return "code"
        elif any(kw in last_msg for kw in ["搜索", "最新", "网上"]):
            return "search"
        return "general"

    def build_graph(self, workers: dict) -> StateGraph:
        """构建 Supervisor 工作图"""
        graph = StateGraph(SupervisorState)
        for name, worker in workers.items():
            graph.add_node(name, worker)
        graph.add_conditional_edges("supervisor", self.route, self.WORKERS.keys())
        graph.set_entry_point("supervisor")
        return graph.compile()
```

- [ ] **Step 5: Write worker.py**

```python
# Worker 子Agent: 执行具体任务
from agents.tool_use_agent import ToolUseAgent

class WorkerAgent:
    """子Agent Worker, 每个Worker有专属工具和系统提示"""

    def __init__(self, name: str, role: str, tools: list):
        self.name = name
        self.role = role
        self.agent = ToolUseAgent(
            tools=tools,
            system_prompt=f"你是{role}, 专注于完成分配给你的任务。"
        )

    def execute(self, task: str) -> str:
        return self.agent.run(task)
```

- [ ] **Step 6: Commit**

---

### Task 15: Build Tools system - base class, builtin tools, custom registry

**Files:**
- Create: `agent-engine/tools/__init__.py`
- Create: `agent-engine/tools/base.py`
- Create: `agent-engine/tools/builtin/__init__.py`
- Create: `agent-engine/tools/builtin/search.py`
- Create: `agent-engine/tools/builtin/calculator.py`
- Create: `agent-engine/tools/builtin/database.py`
- Create: `agent-engine/tools/builtin/file_ops.py`
- Create: `agent-engine/tools/custom/__init__.py`
- Create: `agent-engine/tools/custom/registry.py`

- [ ] **Step 1: Write base.py**

```python
# 工具基类 + 注册器
from langchain.tools import BaseTool
from pydantic import BaseModel, Field
from typing import Type

class ToolRegistry:
    """工具注册中心, 管理所有可用工具"""
    _tools: dict[str, BaseTool] = {}

    @classmethod
    def register(cls, tool: BaseTool):
        cls._tools[tool.name] = tool

    @classmethod
    def get(cls, name: str) -> BaseTool:
        return cls._tools.get(name)

    @classmethod
    def list_all(cls) -> list[BaseTool]:
        return list(cls._tools.values())

    @classmethod
    def get_by_names(cls, names: list[str]) -> list[BaseTool]:
        return [cls._tools[n] for n in names if n in cls._tools]

# 内置工具示例: 计算器
class CalculatorInput(BaseModel):
    expression: str = Field(description="数学表达式, 如 '2+3*4'")

class CalculatorTool(BaseTool):
    name: str = "calculator"
    description: str = "执行数学计算, 输入数学表达式返回计算结果"
    args_schema: Type[BaseModel] = CalculatorInput

    def _run(self, expression: str) -> str:
        try:
            # 安全的数学计算 (仅允许数字和基本运算符)
            allowed = set("0123456789+-*/().% ")
            if not all(c in allowed for c in expression):
                return "错误: 表达式包含不允许的字符"
            result = eval(expression, {"__builtins__": {}}, {})
            return str(result)
        except Exception as e:
            return f"计算错误: {e}"

ToolRegistry.register(CalculatorTool())
```

- [ ] **Step 2: Write search.py, database.py, file_ops.py**

Same pattern: each tool extends BaseTool, defines name/description/args_schema, implements _run(), registers to ToolRegistry.

- [ ] **Step 3: Write custom/registry.py**

```python
# 自定义工具注册: 从 Java 后端同步工具定义
import httpx
from tools.base import ToolRegistry
from langchain.tools import StructuredTool

class CustomToolRegistry:
    """从 Java 后端同步自定义工具"""

    JAVA_BACKEND = "http://localhost:8080"

    @classmethod
    async def sync(cls):
        """从后端拉取所有启用的工具定义并注册"""
        async with httpx.AsyncClient() as client:
            resp = await client.get(f"{cls.JAVA_BACKEND}/api/tools")
            if resp.status_code != 200:
                return
            tools = resp.json().get("data", [])
            for tool_def in tools:
                tool = StructuredTool.from_function(
                    func=lambda **kwargs: f"工具调用结果: {kwargs}",
                    name=tool_def["name"],
                    description=tool_def["description"],
                )
                ToolRegistry.register(tool)
```

- [ ] **Step 4: Commit**

---

### Task 16: Build Workflow engine, API routes, Celery worker

**Files:**
- Create: `agent-engine/workflows/__init__.py`
- Create: `agent-engine/workflows/engine.py`
- Create: `agent-engine/workflows/state.py`
- Create: `agent-engine/workflows/nodes/__init__.py`
- Create: `agent-engine/workflows/nodes/llm_node.py`
- Create: `agent-engine/workflows/nodes/tool_node.py`
- Create: `agent-engine/workflows/nodes/condition_node.py`
- Create: `agent-engine/workflows/nodes/code_node.py`
- Create: API routes: `chat.py`, `rag.py`, `workflow.py`, `model_info.py`
- Create: Worker: `celery_app.py`, `tasks.py`, `callbacks.py`

- [ ] **Step 1: Write workflows/engine.py**

```python
# 工作流执行引擎: 基于 LangGraph 执行 DAG 工作流
from langgraph.graph import StateGraph, END
from workflows.state import WorkflowState
from workflows.nodes.llm_node import LLMNode
from workflows.nodes.tool_node import ToolNode
from workflows.nodes.condition_node import ConditionNode
from workflows.nodes.code_node import CodeNode

class WorkflowEngine:
    """工作流执行引擎, 根据 DAG 定义构建并执行 LangGraph"""

    NODE_TYPES = {
        "LLM": LLMNode,
        "TOOL": ToolNode,
        "CONDITION": ConditionNode,
        "CODE": CodeNode,
    }

    def __init__(self, model_name: str = None):
        self.model_name = model_name

    def execute(self, dag: dict, inputs: dict) -> dict:
        """执行工作流 DAG, dag 包含 nodes 和 edges"""
        graph = StateGraph(WorkflowState)

        # 添加节点
        node_instances = {}
        for node_def in dag["nodes"]:
            node_type = node_def["type"]
            node_cls = self.NODE_TYPES.get(node_type)
            if not node_cls:
                raise ValueError(f"未知节点类型: {node_type}")
            instance = node_cls(node_def.get("config", {}), self.model_name)
            node_instances[node_def["id"]] = instance
            graph.add_node(node_def["id"], instance.execute)

        # 添加边
        for edge in dag["edges"]:
            source = edge["source"]
            target = edge["target"]
            if source == "START":
                graph.set_entry_point(target)
            elif target == "END":
                graph.add_edge(source, END)
            elif edge.get("condition"):
                graph.add_conditional_edges(
                    source,
                    node_instances[source].route,
                    {k: k for k in node_instances},
                )
            else:
                graph.add_edge(source, target)

        app = graph.compile()
        return app.invoke(inputs)
```

- [ ] **Step 2: Write node implementations**

LLMNode: calls ModelFactory.create() with node config, returns llm.invoke()
ToolNode: looks up tool from ToolRegistry, executes it
ConditionNode: evaluates condition expression, returns branch key
CodeNode: executes Python code in restricted sandbox (exec() with limited globals)

- [ ] **Step 3: Write API routes**

`chat.py`:
```python
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from agents.tool_use_agent import ToolUseAgent
from agents.streaming import StreamingCallback
from core.model_factory import ModelFactory
from rag.retrievers.vector_retriever import VectorRetriever
from tools.base import ToolRegistry
from pydantic import BaseModel

router = APIRouter()

class ChatRequest(BaseModel):
    message: str
    conversation_id: int = None
    agent_config_id: str = None
    model_name: str = None

@router.post("/stream")
async def chat_stream(req: ChatRequest):
    """流式对话接口: SSE 格式返回"""
    callback = StreamingCallback()
    llm = ModelFactory.create(
        model_name=req.model_name,
        streaming=True,
        callbacks=[callback]
    )
    # 构建 Agent 并流式返回
    async def generate():
        # 检索相关文档 (如果有知识库)
        retriever = VectorRetriever()
        docs = retriever.search(req.message, k=3)
        context = "\n".join([d.page_content for d in docs])

        # 构建 Prompt
        prompt = f"参考以下资料回答问题:\n{context}\n\n用户: {req.message}\n助手:"

        # 流式调用 LLM
        await llm.ainvoke(prompt)
        async for token in callback.iter_tokens():
            yield f"data: {token}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")
```

`model_info.py`:
```python
from fastapi import APIRouter
from core.model_capability import ModelCapability
from core.model_factory import ModelFactory

router = APIRouter()

@router.get("/capability")
async def capability(model_name: str = None):
    """查询模型能力(含多模态支持)"""
    return ModelCapability.check(model_name)

@router.get("/list")
async def list_models():
    """列出所有可用模型"""
    return {"models": ModelFactory.list_models()}
```

- [ ] **Step 4: Write Celery worker**

`celery_app.py`:
```python
from celery import Celery
from config import settings

app = Celery(
    "agent_engine",
    broker=settings.mq_url,
    backend="rpc://",
)

app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_routes={
        "worker.tasks.ingest_document": {"queue": "doc.ingest.queue"},
        "worker.tasks.execute_workflow": {"queue": "workflow.queue"},
    },
)
```

`tasks.py`:
```python
from worker.celery_app import app
from rag.ingestion import IngestionPipeline
from workflows.engine import WorkflowEngine

@app.task(bind=True, max_retries=3)
def ingest_document(self, doc_id: int, tenant_id: int):
    """异步文档向量化任务"""
    pipeline = IngestionPipeline()
    count = pipeline.ingest_document(doc_id, tenant_id)
    return {"doc_id": doc_id, "chunks_ingested": count}

@app.task(bind=True, max_retries=2)
def execute_workflow(self, workflow_id: int, inputs: dict):
    """异步工作流执行任务"""
    engine = WorkflowEngine()
    # 从数据库加载 DAG 定义
    result = engine.execute(inputs["dag"], inputs)
    return result
```

- [ ] **Step 5: Commit**

```bash
git add agent-engine/
git commit -m "feat: add workflow engine, API routes, Celery worker tasks"
```

---

## Phase 5: Frontend (React + Vite + TypeScript + Shadcn/ui)

### Task 17: Initialize frontend project

**Files:**
- Create: `agent-frontend/package.json`
- Create: `agent-frontend/vite.config.ts`
- Create: `agent-frontend/tsconfig.json`
- Create: `agent-frontend/tailwind.config.js`
- Create: `agent-frontend/postcss.config.js`
- Create: `agent-frontend/index.html`
- Create: `agent-frontend/src/main.tsx`
- Create: `agent-frontend/src/App.tsx`
- Create: `agent-frontend/src/index.css`
- Create: `agent-frontend/src/lib/utils.ts`

- [ ] **Step 1: Initialize with Vite**

```bash
cd D:/LearnJava/Agent
npm create vite@latest agent-frontend -- --template react-ts
cd agent-frontend
```

- [ ] **Step 2: Install dependencies**

```bash
npm install react-router-dom @tanstack/react-query zustand axios recharts reactflow lucide-react react-markdown react-dropzone @monaco-editor/react
npm install -D tailwindcss @tailwindcss/typography postcss autoprefixer
npx tailwindcss init -p
```

- [ ] **Step 3: Initialize Shadcn/ui**

```bash
npx shadcn-ui@latest init
# Select: TypeScript, Default style, Slate base color, CSS variables
npx shadcn-ui@latest add button input card table dialog dropdown-menu sheet tabs separator scroll-area avatar badge tooltip toast
```

- [ ] **Step 4: Write index.css (Tailwind + Shadcn)**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 222.2 84% 4.9%;
    --foreground: 210 40% 98%;
    --card: 222.2 84% 6%;
    --card-foreground: 210 40% 98%;
    --primary: 217.2 91.2% 59.8%;
    --primary-foreground: 222.2 47.4% 11.2%;
    --secondary: 217.2 32.6% 17.5%;
    --secondary-foreground: 210 40% 98%;
    --muted: 217.2 32.6% 17.5%;
    --muted-foreground: 215 20.2% 65.1%;
    --accent: 160 84% 39%;
    --accent-foreground: 210 40% 98%;
    --border: 217.2 32.6% 17.5%;
    --ring: 224.3 76.3% 48%;
    --radius: 0.5rem;
  }
}

@layer base {
  * { @apply border-border; }
  body {
    @apply bg-background text-foreground;
    font-family: 'Inter', system-ui, sans-serif;
  }
}

/* 自定义滚动条 */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { @apply bg-muted rounded-full; }

/* 流式打字光标动画 */
@keyframes blink { 0%, 50% { opacity: 1; } 51%, 100% { opacity: 0; } }
.typing-cursor::after {
  content: '▊';
  animation: blink 1s infinite;
  @apply text-primary;
}
```

- [ ] **Step 5: Write App.tsx with routing**

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppLayout } from '@/components/layout/AppLayout';
import { DashboardPage } from '@/pages/Dashboard/DashboardPage';
import { ChatPage } from '@/pages/Chat/ChatPage';
import { KnowledgePage } from '@/pages/Knowledge/KnowledgePage';
import { AgentOrchPage } from '@/pages/AgentOrch/AgentOrchPage';
import { WorkflowPage } from '@/pages/Workflow/WorkflowPage';
import { TenantPage } from '@/pages/Tenant/TenantPage';
import { LoginPage } from '@/pages/Auth/LoginPage';
import { UserManagementPage } from '@/pages/Auth/UserManagementPage';
import { SettingsPage } from '@/pages/Settings/SettingsPage';

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<AppLayout />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/knowledge" element={<KnowledgePage />} />
            <Route path="/agents" element={<AgentOrchPage />} />
            <Route path="/workflows" element={<WorkflowPage />} />
            <Route path="/tenants" element={<TenantPage />} />
            <Route path="/users" element={<UserManagementPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
```

- [ ] **Step 6: Commit**

```bash
git add agent-frontend/
git commit -m "feat: initialize React frontend with Vite, Tailwind, Shadcn/ui, routing"
```

---

### Task 18: Build layout and navigation

**Files:**
- Create: `agent-frontend/src/components/layout/Sidebar.tsx`
- Create: `agent-frontend/src/components/layout/Topbar.tsx`
- Create: `agent-frontend/src/components/layout/AppLayout.tsx`

- [ ] **Step 1: Write Sidebar.tsx**

```tsx
import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';
import {
  LayoutDashboard, MessageSquare, Database, Bot,
  GitBranch, Users, Shield, Settings, ChevronLeft
} from 'lucide-react';
import { useState } from 'react';

const navItems = [
  { to: '/', icon: LayoutDashboard, label: '监控大盘' },
  { to: '/chat', icon: MessageSquare, label: '对话交互' },
  { to: '/knowledge', icon: Database, label: '知识库' },
  { to: '/agents', icon: Bot, label: 'Agent编排' },
  { to: '/workflows', icon: GitBranch, label: '工作流' },
  { to: '/tenants', icon: Users, label: '租户管理' },
  { to: '/users', icon: Shield, label: '用户权限' },
  { to: '/settings', icon: Settings, label: '系统设置' },
];

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside className={cn(
      'flex flex-col h-screen bg-card border-r border-border transition-all duration-300',
      collapsed ? 'w-16' : 'w-60'
    )}>
      {/* Logo */}
      <div className="flex items-center gap-3 p-4 border-b border-border">
        <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
          <Bot className="w-5 h-5 text-primary-foreground" />
        </div>
        {!collapsed && <span className="font-bold text-lg">Agent Platform</span>}
      </div>

      {/* Nav items */}
      <nav className="flex-1 p-2 space-y-1">
        {navItems.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => cn(
              'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors',
              'hover:bg-secondary',
              isActive && 'bg-primary/10 text-primary border-l-2 border-primary',
            )}
          >
            <item.icon className="w-5 h-5 flex-shrink-0" />
            {!collapsed && <span className="text-sm">{item.label}</span>}
          </NavLink>
        ))}
      </nav>

      {/* Collapse toggle */}
      <button
        onClick={() => setCollapsed(!collapsed)}
        className="p-3 border-t border-border hover:bg-secondary transition-colors"
      >
        <ChevronLeft className={cn(
          'w-5 h-5 transition-transform mx-auto',
          collapsed && 'rotate-180'
        )} />
      </button>
    </aside>
  );
}
```

- [ ] **Step 2: Write Topbar.tsx**

```tsx
import { Bell, Moon, Sun, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuTrigger, DropdownMenuSeparator
} from '@/components/ui/dropdown-menu';
import { useAuthStore } from '@/stores/authStore';

export function Topbar() {
  const { user, logout } = useAuthStore();

  return (
    <header className="h-14 border-b border-border bg-card flex items-center justify-between px-6">
      <div>
        <span className="text-sm text-muted-foreground">
          欢迎, {user?.username || '管理员'}
        </span>
      </div>

      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon">
          <Bell className="w-5 h-5" />
        </Button>
        <Button variant="ghost" size="icon">
          <Sun className="w-5 h-5" />
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon">
              <Avatar className="w-8 h-8">
                <AvatarFallback className="bg-primary/20 text-primary text-xs">
                  {user?.username?.[0]?.toUpperCase() || 'A'}
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuItem><User className="w-4 h-4 mr-2" />个人设置</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout}>退出登录</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
```

- [ ] **Step 3: Write AppLayout.tsx**

```tsx
import { Outlet, Navigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { useAuthStore } from '@/stores/authStore';

export function AppLayout() {
  const { token } = useAuthStore();
  if (!token) return <Navigate to="/login" replace />;

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Topbar />
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Commit**

---

### Task 19: Build stores, API client, and types

**Files:**
- Create: `agent-frontend/src/stores/authStore.ts`
- Create: `agent-frontend/src/stores/chatStore.ts`
- Create: `agent-frontend/src/stores/appStore.ts`
- Create: `agent-frontend/src/api/client.ts`
- Create: `agent-frontend/src/types/index.ts`
- Create: all API modules

- [ ] **Step 1: Write types/index.ts**

```ts
// 通用 API 返回体
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

// 用户
export interface User { id: number; username: string; email: string; avatar?: string; enabled: boolean; roles: Role[]; }
export interface Role { id: number; name: string; description: string; permissions: Permission[]; }
export interface Permission { id: number; code: string; description: string; parentCode?: string; }

// 租户
export interface Tenant { id: number; name: string; description: string; enabled: boolean; apiKey: string; }
export interface TenantQuota { id: number; tenantId: number; maxDocuments: number; maxTokensPerDay: number; maxConversations: number; maxAgents: number; }

// 知识库
export interface Document { id: number; fileName: string; fileType: string; fileSize: number; status: string; chunkCount: number; errorMsg?: string; }
export interface Chunk { id: number; documentId: number; content: string; chunkType: 'text' | 'image'; imagePath?: string; chunkIndex: number; vectorized: boolean; }

// 对话
export interface Conversation { id: number; title: string; userId: number; agentConfigId?: number; messageCount: number; }
export interface Message { id: number; conversationId: number; role: 'user' | 'assistant' | 'system'; content: string; tokenCount?: number; }

// Agent编排
export interface AgentConfig { id: number; name: string; description: string; modelName: string; temperature: number; maxTokens: number; active: boolean; }
export interface PromptTemplate { id: number; name: string; systemPrompt: string; variables: string; }
export interface ToolDef { id: number; name: string; description: string; parameters: string; endpoint: string; builtin: boolean; }

// 工作流
export interface WorkflowDef { id: number; name: string; description: string; dagJson: string; status: string; }
export interface WorkflowNode { id: string; type: 'LLM' | 'TOOL' | 'CONDITION' | 'CODE'; config: Record<string, any>; position: { x: number; y: number }; }
export interface WorkflowEdge { id: string; source: string; target: string; conditionExpr?: string; }

// 监控
export interface DashboardStats { dailyTokens: number; avgLatency: number; totalCalls: number; activeConversations: number; }
```

- [ ] **Step 2: Write api/client.ts**

```ts
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore';

const client = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
});

// 请求拦截器: 自动注入 JWT Token
client.interceptors.request.use(config => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器: 统一错误处理
client.interceptors.response.use(
  res => res.data,
  error => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  }
);

export default client;
```

- [ ] **Step 3: Write authStore.ts**

```ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  username: string | null;
  roles: string[];
  setAuth: (token: string, username: string, roles: string[]) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      username: null,
      roles: [],
      setAuth: (token, username, roles) => set({ token, username, roles }),
      logout: () => { set({ token: null, username: null, roles: [] }); window.location.href = '/login'; },
    }),
    { name: 'agent-auth' }
  )
);
```

- [ ] **Step 4: Write chatStore.ts**

```ts
import { create } from 'zustand';
import { Message, Conversation } from '@/types';

interface ChatState {
  conversations: Conversation[];
  currentConvId: number | null;
  messages: Message[];
  isStreaming: boolean;
  setConversations: (convs: Conversation[]) => void;
  setCurrentConv: (id: number) => void;
  addMessage: (msg: Message) => void;
  appendStreamChunk: (chunk: string) => void;
  setStreaming: (v: boolean) => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  currentConvId: null,
  messages: [],
  isStreaming: false,
  setConversations: (convs) => set({ conversations: convs }),
  setCurrentConv: (id) => set({ currentConvId: id, messages: [] }),
  addMessage: (msg) => set(s => ({ messages: [...s.messages, msg] })),
  appendStreamChunk: (chunk) => {
    const msgs = get().messages;
    const last = msgs[msgs.length - 1];
    if (last && last.role === 'assistant') {
      set({ messages: [...msgs.slice(0, -1), { ...last, content: last.content + chunk }] });
    } else {
      set({ messages: [...msgs, { id: Date.now(), conversationId: get().currentConvId!, role: 'assistant', content: chunk }] });
    }
  },
  setStreaming: (v) => set({ isStreaming: v }),
}));
```

- [ ] **Step 5: Commit**

---

### Task 20: Build pages - Dashboard, Chat, Knowledge

- [ ] **Step 1: Write DashboardPage.tsx**

```tsx
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line } from 'recharts';
import { MessageSquare, Database, Zap, Clock } from 'lucide-react';
import client from '@/api/client';

export function DashboardPage() {
  const { data } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => client.get('/monitor/dashboard') as Promise<any>,
    refetchInterval: 10000,
  });

  const stats = data?.data || { dailyTokens: 0, avgLatency: 0, totalCalls: 0, activeConversations: 0 };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">监控大盘</h1>

      {/* 统计卡片 */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard icon={Zap} title="今日Token消耗" value={stats.dailyTokens.toLocaleString()} color="text-blue-400" />
        <StatCard icon={Clock} title="平均延迟" value={`${stats.avgLatency}ms`} color="text-emerald-400" />
        <StatCard icon={MessageSquare} title="总调用量" value={stats.totalCalls.toLocaleString()} color="text-violet-400" />
        <StatCard icon={Database} title="活跃会话" value={stats.activeConversations.toString()} color="text-amber-400" />
      </div>

      {/* 图表 */}
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader><CardTitle>Token消耗趋势</CardTitle></CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={[]}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" /><YAxis /><Tooltip /><Line type="monotone" dataKey="tokens" stroke="#3b82f6" /></LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>调用量排行</CardTitle></CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={[]}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="model" /><YAxis /><Tooltip /><Bar dataKey="calls" fill="#8b5cf6" /></BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function StatCard({ icon: Icon, title, value, color }: any) {
  return (
    <Card>
      <CardContent className="flex items-center gap-4 py-4">
        <div className={`p-3 rounded-lg bg-secondary ${color}`}><Icon className="w-6 h-6" /></div>
        <div>
          <p className="text-sm text-muted-foreground">{title}</p>
          <p className="text-2xl font-bold">{value}</p>
        </div>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 2: Write Chat components**

ChatPage: left panel = ConversationList, right panel = ChatWindow (MessageBubble list + ChatInput)
MessageBubble: renders user/assistant messages, supports Markdown via react-markdown, code highlighting
ChatInput: textarea + send button, supports Shift+Enter newline
ChatWindow: uses useSSE hook for streaming, renders MessageBubble list, auto-scrolls to bottom

- [ ] **Step 3: Write useSSE hook**

```ts
import { useState, useCallback, useRef } from 'react';

export function useSSE() {
  const [isStreaming, setIsStreaming] = useState(false);
  const readerRef = useRef<ReadableStreamDefaultReader | null>(null);

  const startStream = useCallback(async (url: string, body: any, onChunk: (text: string) => void) => {
    setIsStreaming(true);
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const reader = response.body!.getReader();
    readerRef.current = reader;
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const text = decoder.decode(value);
      const lines = text.split('\n');
      for (const line of lines) {
        if (line.startsWith('data: ') && line !== 'data: [DONE]') {
          onChunk(line.slice(6));
        }
      }
    }
    setIsStreaming(false);
  }, []);

  const stopStream = useCallback(() => {
    readerRef.current?.cancel();
    setIsStreaming(false);
  }, []);

  return { isStreaming, startStream, stopStream };
}
```

- [ ] **Step 4: Write KnowledgePage.tsx**

```tsx
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Upload, FileText, Search, Trash2 } from 'lucide-react';
import { useDropzone } from 'react-dropzone';
import { useState } from 'react';
import client from '@/api/client';
import { Document } from '@/types';

export function KnowledgePage() {
  const queryClient = useQueryClient();
  const [testQuery, setTestQuery] = useState('');
  const [testResults, setTestResults] = useState<string[]>([]);

  const { data: docs } = useQuery({
    queryKey: ['documents'],
    queryFn: () => client.get('/knowledge/docs') as Promise<any>,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return client.post('/knowledge/docs/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }) as Promise<any>;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  });

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: files => files.forEach(f => uploadMutation.mutate(f)),
    accept: { 'application/pdf': ['.pdf'], 'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'], 'text/markdown': ['.md'], 'text/plain': ['.txt'], 'text/html': ['.html'], 'image/png': ['.png'], 'image/jpeg': ['.jpg', '.jpeg'] },
  });

  const statusBadge = (status: string) => {
    const map: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
      DONE: 'default', PARSING: 'secondary', FAILED: 'destructive', PENDING: 'outline',
    };
    return <Badge variant={map[status] || 'outline'}>{status}</Badge>;
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold">知识库管理</h1>
      </div>

      {/* 上传区域 */}
      <div {...getRootProps()} className={`
        border-2 border-dashed rounded-xl p-12 text-center cursor-pointer transition-colors
        ${isDragActive ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}
      `}>
        <input {...getInputProps()} />
        <Upload className="w-10 h-10 mx-auto mb-3 text-muted-foreground" />
        <p className="text-lg font-medium">拖拽文件到此处上传</p>
        <p className="text-sm text-muted-foreground mt-1">支持 PDF、Word、Markdown、HTML、TXT、PNG、JPG</p>
      </div>

      {/* 文档列表 */}
      <Card>
        <CardHeader><CardTitle>文档列表</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>文件名</TableHead><TableHead>类型</TableHead><TableHead>大小</TableHead><TableHead>分片数</TableHead><TableHead>状态</TableHead><TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {docs?.data?.map((doc: Document) => (
                <TableRow key={doc.id}>
                  <TableCell className="flex items-center gap-2"><FileText className="w-4 h-4" />{doc.fileName}</TableCell>
                  <TableCell><Badge variant="outline">{doc.fileType}</Badge></TableCell>
                  <TableCell>{(doc.fileSize / 1024).toFixed(1)} KB</TableCell>
                  <TableCell>{doc.chunkCount}</TableCell>
                  <TableCell>{statusBadge(doc.status)}</TableCell>
                  <TableCell><Button variant="ghost" size="icon"><Trash2 className="w-4 h-4 text-red-400" /></Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 检索测试 */}
      <Card>
        <CardHeader><CardTitle>检索测试</CardTitle></CardHeader>
        <CardContent>
          <div className="flex gap-2">
            <Input value={testQuery} onChange={e => setTestQuery(e.target.value)} placeholder="输入测试查询语句..." />
            <Button><Search className="w-4 h-4 mr-1" />检索</Button>
          </div>
          {testResults.length > 0 && (
            <div className="mt-4 space-y-2">
              {testResults.map((r, i) => (
                <div key={i} className="p-3 rounded-lg bg-secondary text-sm">{r}</div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
```

- [ ] **Step 5: Commit**

---

### Task 21: Build AgentOrch, Workflow, Auth, Tenant, Settings pages

- [ ] **Step 1: AgentOrchPage.tsx** — 三个 Tab: Agent 配置列表 / Prompt 模板编辑器(Monaco) / 工具绑定面板
- [ ] **Step 2: WorkflowPage.tsx** — 左侧节点面板(draggable) + 中央 React Flow 画布 + 右侧节点配置抽屉
- [ ] **Step 3: LoginPage.tsx** — 居中卡片, 渐变背景, 用户名密码表单
- [ ] **Step 4: UserManagementPage.tsx** — 用户表格 + 角色树 + 权限矩阵, 使用 Shadcn Table + Dialog
- [ ] **Step 5: TenantPage.tsx** — 租户表格 + 配额编辑对话框
- [ ] **Step 6: SettingsPage.tsx** — 模型配置表单(base_url/api_key/模型名), 系统参数

- [ ] Commit: `git commit -m "feat: complete all frontend pages"`

---

## Phase 6: Integration & Documentation

### Task 22: Write comprehensive README.md

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README.md** (comprehensive, with architecture diagram, setup guide, API docs, dev guide)

### Task 23: Final integration verification

- [ ] Verify Java backend starts: `mvn spring-boot:run` in agent-backend
- [ ] Verify Python engine starts: `uvicorn main:app --port 8000` in agent-engine
- [ ] Verify frontend starts: `npm run dev` in agent-frontend
- [ ] Test end-to-end: login → create tenant → upload document → chat with RAG → check monitor
- [ ] Final commit

---

*Plan complete*
