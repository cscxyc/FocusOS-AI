# FocusOS AI — Architecture

> 本文档描述 FocusOS AI 的系统架构、Agent Workflow、RAG 流程、Memory 机制、LLM Observability 与 Production 部署方案。

---

## 1. 系统架构

### 1.1 分层视图

```
┌─────────────────────────────────────────────────────────────┐
│                      用户浏览器                              │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP / SSE
┌────────────────────────▼────────────────────────────────────┐
│                  Frontend (Next.js 14)                       │
│  React Query · Zustand · TailwindCSS · SSE EventSource      │
└────────────────────────┬────────────────────────────────────┘
                         │ REST API
┌────────────────────────▼────────────────────────────────────┐
│              Backend (Spring Boot 3.2)                       │
│  ┌──────────┐  ┌────────────┐  ┌────────────────────────┐   │
│  │ JWT Auth │→ │ Controller │→ │     Service Layer      │   │
│  │  Filter  │  │   (REST)   │  │  (Business Logic)      │   │
│  └──────────┘  └────────────┘  └──────────┬─────────────┘   │
│                                            │                 │
│                ┌───────────────────────────▼──────────┐      │
│                │       Agent Workflow Engine          │      │
│                │  MasterAgent → AgentRouter → Agents  │      │
│                │       ↓                              │      │
│                │  WorkflowEventBus (SSE Push)         │      │
│                └──────┬──────────────┬────────────────┘      │
│                       │              │                       │
│           ┌───────────▼──┐   ┌───────▼────────┐              │
│           │  LLM Gateway │   │  RAG Service   │              │
│           │  (qwen-plus) │   │  (Milvus)      │              │
│           └──────────────┘   └────────────────┘              │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Infrastructure                            │
│  MySQL 8.0  │  Redis 7  │  Milvus 2.4  │  Prometheus        │
│  (业务数据)  │ (队列/缓存) │  (向量存储)   │  + Grafana         │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 关键设计原则

1. **分层清晰**：Controller → Service → Repository → Entity，DTO 严格隔离请求 / 响应
2. **用户隔离**：所有数据查询按 `userId` 过滤，RAG 向量通过 Metadata 字段隔离
3. **降级策略**：Redis / Milvus / LLM 不可用时均有降级方案，保证核心可用
4. **可观测性**：每次 LLM 调用记录 `LLMCallLog`，Prometheus 暴露 5 个核心指标

---

## 2. Agent Workflow

### 2.1 工作流 DAG

Career Workflow 采用 DAG（有向无环图）拓扑，6 个 Agent 按依赖关系并行 / 串行执行：

```
                  ┌─────────────────────┐
                  │   CAREER_ANALYSIS    │  CareerAgent
                  │   (JD 分析 + 匹配)    │
                  └──────────┬──────────┘
                             │
                  ┌──────────▼──────────┐
                  │ RESUME_OPTIMIZATION  │  ResumeOptimizationAgent
                  │   (STAR 简历优化)     │
                  └──────────┬──────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
┌──────────▼─────────┐ ┌─────▼──────────┐ ┌────▼─────────────────┐
│ SKILL_GAP_ANALYSIS │ │ LEARNING_PLAN  │ │ INTERVIEW_PREPARATION│
│   (技能缺口分析)    │ │  (学习计划)     │ │   (面试准备)          │
└──────────┬─────────┘ └────────────────┘ └────┬─────────────────┘
           │                                     │
           └─────────────────┬───────────────────┘
                             │
                  ┌──────────▼──────────┐
                  │   MOCK_INTERVIEW     │  InterviewAgent
                  │   (模拟面试 + 评分)   │
                  └─────────────────────┘
```

### 2.2 执行引擎

| 组件 | 职责 |
|------|------|
| `MasterAgent` | 工作流入口，解析目标 → 分发任务 |
| `AgentRouter` | DAG 路由，决定下一个执行的 Agent |
| `AgentWorker` | 线程池（core=5 / max=20 / queue=100）消费任务 |
| `WorkflowScheduler` | 工作流状态持久化 + 入队 |
| `WorkflowEventBus` | SSE 事件推送（`task_started` / `task_completed` / `workflow_completed`） |
| `RetryManager` | 失败重试（2s / 5s / 10s 退避，maxRetry 限制） |

### 2.3 WorkflowInstance 状态机

```
PENDING ──→ RUNNING ──→ SUCCESS
   │           │
   │           ├──→ FAILED ──→ RETRYING ──→ RUNNING
   │           │
   │           └──→ PAUSED ──→ (resume) ──→ PENDING
   │
   └──→ CANCELLED
```

### 2.4 SSE 实时事件

前端通过 `EventSource` 订阅 `/api/workflow/{id}/events`，事件类型：

| 事件 | 触发时机 | Payload |
|------|----------|---------|
| `workflow_started` | 工作流启动 | `{workflowId, totalTasks}` |
| `task_started` | 单个 Agent 开始 | `{taskId, agentType, startedAt}` |
| `task_completed` | 单个 Agent 完成 | `{taskId, status, durationMs, result}` |
| `workflow_completed` | 全部完成 | `{workflowId, successTasks, failedTasks}` |
| `workflow_failed` | 工作流失败 | `{workflowId, errorMessage}` |

---

## 3. RAG 流程

### 3.1 文档入库流程

```
用户上传文档 (MD / PDF / TXT)
        │
        ▼
┌───────────────┐
│  Document     │  存储元数据到 MySQL: knowledge_documents
│  Ingestion    │
└───────┬───────┘
        │
        ▼
┌───────────────┐
│  Chunking     │  按字符长度切块（≤ 2048 chars，DashScope Embedding 限制）
└───────┬───────┘
        │
        ▼
┌───────────────┐
│  Embedding    │  调用 text-embedding-v2 生成 1536 维向量
└───────┬───────┘
        │
        ▼
┌───────────────┐
│  Milvus Store │  写入向量 + Metadata (userId, documentId, fileName)
└───────────────┘
```

### 3.2 检索流程

```
用户 Query
    │
    ▼
┌──────────────────┐
│ ProfileQueryBuilder│  按查询类别构建短查询（避免 Embedding 长度限制）
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│   Embedding      │  Query → 1536 维向量
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Milvus Search    │  按 userId 过滤 + 余弦相似度检索 (minScore=0.25)
│  + Metadata Filter│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│   Rerank / Top-K │  返回 Top-K 相关 Chunk
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Agent Generate  │  将检索结果作为 context 注入 Prompt
└──────────────────┘
```

### 3.3 用户隔离

每条向量在 Milvus 中携带 Metadata：

```json
{
  "userId": 1,
  "documentId": 42,
  "fileName": "resume.pdf",
  "category": "CAREER"
}
```

检索时强制按 `userId` 过滤，确保用户间数据严格隔离。

### 3.4 类别路由

Career 相关 RAG 查询优先检索 `CAREER / EXPERIENCE / PROJECT` 类别文档；`LEARNING` 类别仅在生成学习计划时检索。

---

## 4. Memory 机制

### 4.1 Memory 类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `MEMORY` | 通用记忆 | "用户上周完成了 Spring Security 学习" |
| `PREFERENCE` | 用户偏好 | "用户偏好使用 TypeScript" |
| `FACT` | 客观事实 | "用户有 3 年 Java 开发经验" |
| `SKILL` | 技能 | "用户熟悉 Spring Boot / MySQL / Docker" |
| `EXPERIENCE` | 经历 | "用户在 XX 公司担任后端开发" |

### 4.2 Memory 工作流

```
Agent 交互
    │
    ▼
┌──────────────────┐
│ MemoryExtractor  │  从对话中提取潜在 Memory
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ MemoryMergeStrategy│  与现有 Memory 去重 / 合并
│  (相似度匹配)      │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  UserMemory 持久化│  存入 MySQL: user_memories
└──────────────────┘

         ↓ Agent 调用前

┌──────────────────┐
│ Memory Injection │  按 userId + 类型查询相关 Memory
└────────┬─────────┘
         │
         ▼
   注入 Agent Prompt → 提升回答个性化程度
```

### 4.3 Memory 衰减与合并

- **合并策略**：新 Memory 与已有 Memory 相似度 > 阈值时合并，保留最新信息
- **权重**：`EXPERIENCE` / `SKILL` 权重高于 `MEMORY`，优先注入

---

## 5. LLM Observability

### 5.1 LLM Gateway

统一 LLM 调用入口，提供：

| 能力 | 实现 |
|------|------|
| 模型路由 | 不同 Agent 使用不同模型（Career → qwen-plus，Memory → qwen-turbo） |
| 超时控制 | `LLM_TIMEOUT=60s`，避免长尾请求阻塞 |
| Fallback 链 | `qwen-plus → qwen-turbo → 启发式降级` |
| 调用日志 | 每次调用记录 `LLMCallLog`（模型 / Token / 延迟 / 成本） |

### 5.2 LLMCallLog

每次 LLM 调用记录以下字段：

```java
class LLMCallLog {
    Long userId;
    String workflowId;
    String agentType;
    String model;
    Integer inputTokens;
    Integer outputTokens;
    Long latencyMs;
    BigDecimal estimatedCost;  // 基于模型单价估算
    Boolean success;
    String errorMessage;
    LocalDateTime createdAt;
}
```

### 5.3 用户配额

`UserQuota` 实体按用户级别限制每日 Token 消耗：

| 级别 | 每日 Token 上限 |
|------|-----------------|
| DEFAULT | 10,000 |
| PREMIUM | 100,000 |

超额时抛出 `QuotaExceededException`，阻断 LLM 调用。

### 5.4 Prometheus 指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `llm.latency` | Histogram | LLM 调用延迟分布 |
| `llm.tokens.total` | Counter | Token 消耗总量 |
| `llm.cost.total` | Counter | 估算成本累计 |
| `workflow.duration` | Histogram | 工作流总耗时 |
| `workflow.tasks.total` | Counter | 任务执行总数 |

Grafana 仪表盘配置见 `monitor/grafana-dashboard.json`。

---

## 6. Production 部署

### 6.1 Docker Compose 拓扑

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network                            │
│                                                              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────────┐       │
│  │  MySQL  │  │  Redis  │  │  etcd   │  │  MinIO   │       │
│  └────┬────┘  └────┬────┘  └────┬────┘  └─────┬────┘       │
│       │            │            │             │             │
│       │            │            └──────┬──────┘             │
│       │            │                   │                    │
│       │            │            ┌──────▼──────┐             │
│       │            │            │   Milvus    │             │
│       │            │            └──────┬──────┘             │
│       │            │                   │                    │
│       └────────────┴───────────────────┤                    │
│                                        │                    │
│                                 ┌──────▼──────┐             │
│                                 │  Backend    │             │
│                                 │ (Spring Boot)│             │
│                                 └──────┬──────┘             │
│                                        │                    │
│                          ┌─────────────┼─────────────┐      │
│                          │             │             │      │
│                   ┌──────▼─────┐ ┌─────▼─────┐ ┌─────▼────┐ │
│                   │   Nginx    │ │Prometheus │ │ Grafana  │ │
│                   │ (Frontend) │ │           │ │          │ │
│                   └──────┬─────┘ └───────────┘ └──────────┘ │
│                          │                                   │
└──────────────────────────┼───────────────────────────────────┘
                           │
                    :80 (HTTP)
```

### 6.2 健康检查

每个服务均配置 `healthcheck`，Backend 启动依赖 MySQL / Redis / Milvus 健康检查通过。

### 6.3 数据持久化

| Volume | 说明 |
|--------|------|
| `mysql-data` | MySQL 数据文件 |
| `redis-data` | Redis AOF 持久化 |
| `milvus-data` | Milvus 向量数据 |
| `etcd-data` | Milvus 元数据 |
| `minio-data` | Milvus 对象存储 |
| `prometheus-data` | 监控指标（15 天保留） |
| `grafana-data` | 仪表盘配置 |

### 6.4 降级策略

| 组件 | 不可用时行为 |
|------|-------------|
| Redis | 降级到 `ConcurrentHashMap` 本地缓存；任务队列降级为同步执行 |
| Milvus | 降级到 `InMemoryEmbeddingStore`（需设置 `MILVUS_ENABLED=false`） |
| LLM (qwen-plus) | Fallback 到 qwen-turbo；再失败则 `RAGEvaluator` 启发式降级 |
| QuotaService | 跳过配额检查（`ObjectProvider` 可选注入） |

### 6.5 CI/CD

GitHub Actions 流水线（`.github/workflows/build.yml`）：

```
push to main / PR
    │
    ▼
┌──────────────────┐
│  Build & Test    │  Maven 编译 + 单元测试
└────────┬─────────┘
         │ (仅 main 分支)
┌────────▼─────────┐
│  Docker Build    │  构建 Backend 镜像并推送到 Docker Hub
└────────┬─────────┘
         │
┌────────▼─────────┐
│  Deploy (手动)    │  SSH 到生产服务器，docker compose pull + up
└──────────────────┘
```
