# FocusOS AI — API Reference

> 主要 REST API 列表。所有 API 前缀为 `/api`，需在 Header 携带 `Authorization: Bearer <JWT>`（除 `/auth/*` 外）。
> 完整交互式文档：启动后端后访问 `http://localhost:8080/api/swagger-ui.html`。

---

## Auth

| Method | Path | 说明 |
|--------|------|------|
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 用户登录，返回 JWT |
| POST | `/auth/refresh` | 刷新 JWT Token |
| GET | `/auth/me` | 获取当前用户信息 |

---

## Dashboard

| Method | Path | 说明 |
|--------|------|------|
| GET | `/dashboard` | 原有 Dashboard（学习统计 + 今日事件 + AI 建议） |
| GET | `/dashboard/overview` | **Sprint 9-A**：5 维职业状态聚合（简历评分 / JD 匹配 / 面试成绩 / 成长进度 / Memory 数量） |
| GET | `/dashboard/timeline` | **Sprint 9-A**：Career Journey Timeline（6 阶段） |
| GET | `/dashboard/ai-recommendation` | AI 建议生成 |
| GET | `/dashboard/ai-plan` | AI 计划生成 |

### DashboardOverviewResponse 结构

```json
{
  "userId": 1,
  "username": "admin",
  "resumeScore": { "score": 86, "version": "v2", "targetPosition": "AI应用开发工程师" },
  "highestMatchScore": { "matchScore": 78, "jobTitle": "AI应用开发工程师", "company": "..." },
  "interviewScore": { "averageScore": 75, "highestScore": 88, "totalSessions": 3 },
  "growthProgress": { "totalWeeks": 12, "completedWeeks": 4, "progressPercent": 33 },
  "memoryCount": 15,
  "recentActivities": [ { "type": "RESUME", "title": "...", "createdAt": "..." } ],
  "generatedAt": "2026-08-10T12:00:00"
}
```

### TimelineResponse 结构

```json
[
  {
    "stage": "CAREER_ANALYSIS",
    "status": "SUCCESS",
    "title": "JD Analysis",
    "description": "AI应用开发工程师 - matchScore: 78",
    "workflowId": "wf-xxx",
    "createdAt": "2026-08-10T10:00:00"
  }
]
```

---

## Career

| Method | Path | 说明 |
|--------|------|------|
| POST | `/career/analyze-jd` | 分析 JD，抽取岗位要求 |
| POST | `/career/workflow` | 启动 Career Workflow（6 Agent 协同） |
| GET | `/career/reports` | 获取用户的 CareerAnalysisReport 列表 |
| GET | `/career/reports/{id}` | 获取单个分析报告详情 |
| POST | `/career/profiles` | 创建 Career Profile |
| GET | `/career/profiles` | 获取用户的 Career Profile |

---

## Resume

| Method | Path | 说明 |
|--------|------|------|
| POST | `/resume/versions` | 创建简历版本 |
| GET | `/resume/versions` | 获取用户的简历版本列表 |
| GET | `/resume/versions/{id}` | 获取单个简历版本 |
| PUT | `/resume/versions/{id}` | 更新简历内容 |
| POST | `/resume/versions/{id}/activate` | 激活指定版本 |
| GET | `/resume/versions/{id}/diff` | 获取简历 Diff（与前一版本对比） |
| GET | `/resume/versions/{id}/export-pdf` | 导出 PDF |
| POST | `/resume/evaluate` | 简历质量评估（ResumeEvaluatorAgent） |

---

## Interview

| Method | Path | 说明 |
|--------|------|------|
| POST | `/interview/sessions` | 创建面试会话 |
| GET | `/interview/sessions` | 获取用户的面试会话列表 |
| GET | `/interview/sessions/{id}` | 获取单个面试会话详情 |
| POST | `/interview/sessions/{id}/questions` | 生成面试题 |
| POST | `/interview/sessions/{id}/answers` | 提交答案并评分 |
| GET | `/interview/sessions/{id}/evaluation` | 获取面试评价报告 |

---

## Memory

| Method | Path | 说明 |
|--------|------|------|
| GET | `/memory` | 获取用户全部 Memory |
| GET | `/memory/type/{type}` | 按 type 获取 Memory（MEMORY/PREFERENCE/FACT/SKILL/EXPERIENCE） |
| POST | `/memory` | 创建 Memory |
| PUT | `/memory/{id}` | 更新 Memory |
| DELETE | `/memory/{id}` | 删除 Memory |
| POST | `/memory/extract` | 从对话中自动提取 Memory |

---

## Workflow

| Method | Path | 说明 |
|--------|------|------|
| POST | `/workflow/execute` | 异步启动 Workflow，立即返回 `workflowId` |
| GET | `/workflow/{workflowId}` | 查询 Workflow 详情（含 Task 列表） |
| GET | `/workflow/history` | 获取用户 Workflow 历史 |
| POST | `/workflow/submit` | **Sprint 8-E**：通过 Scheduler 提交（持久化 + 入队） |
| POST | `/workflow/{id}/pause` | 暂停 Workflow |
| POST | `/workflow/{id}/resume` | 恢复 Workflow |
| POST | `/workflow/{id}/retry` | 重试失败的 Workflow |
| GET | `/workflow/{id}/instance` | 获取持久化的 WorkflowInstance |
| GET | `/workflow/instances` | 获取用户的 WorkflowInstance 列表 |

### SSE 事件流

| Method | Path | 说明 |
|--------|------|------|
| GET | `/workflow/{workflowId}/events` | SSE 订阅实时事件流（长连接） |
| GET | `/workflow/{workflowId}/events/history` | 获取历史事件（用于页面刷新恢复） |

**SSE 事件类型**：`workflow_started` / `task_started` / `task_completed` / `workflow_completed` / `workflow_failed`

---

## RAG

| Method | Path | 说明 |
|--------|------|------|
| POST | `/rag/upload` | 上传文档（MD / PDF / TXT），自动向量化入库 |
| GET | `/rag/documents` | 获取用户的文档列表 |
| DELETE | `/rag/documents/{id}` | 删除文档（同时删除向量） |
| POST | `/rag/search` | 知识库检索（返回 Top-K 相关 Chunk） |
| POST | `/rag/chat` | 基于知识库的对话（RAG Agent） |

---

## Agent Evaluation

| Method | Path | 说明 |
|--------|------|------|
| POST | `/evaluation` | 创建 Agent 评估记录 |
| GET | `/evaluation` | 获取评估记录列表 |
| GET | `/evaluation/{id}` | 获取单个评估记录 |
| GET | `/evaluation/ranking` | Agent 质量排名 |
| GET | `/evaluation/trend` | Agent 质量趋势 |
| POST | `/evaluation/rag-eval` | RAG 评估（contextRecall / contextPrecision / faithfulness） |
| POST | `/evaluation/grounding-check` | Grounding 检测（检测无据陈述） |

---

## Career Growth

| Method | Path | 说明 |
|--------|------|------|
| POST | `/career-growth/plans` | 生成成长计划（CareerGrowthAgent） |
| GET | `/career-growth/plans` | 获取用户的成长计划列表 |
| GET | `/career-growth/plans/{id}` | 获取单个成长计划详情 |
| PUT | `/career-growth/plans/{id}/progress` | 更新周进度 |

---

## LLM Call Logs

| Method | Path | 说明 |
|--------|------|------|
| GET | `/llm-logs` | 获取 LLM 调用日志列表（支持分页 + 过滤） |
| GET | `/llm-logs/workflow/{workflowId}` | 按 Workflow 获取 LLM 调用日志 |
| GET | `/llm-logs/stats` | LLM 调用统计（Token / 成本 / 延迟） |

---

## Quota

| Method | Path | 说明 |
|--------|------|------|
| GET | `/quota` | 获取当前用户配额 |
| GET | `/quota/usage` | 获取今日 Token 使用量 |
| POST | `/quota/upgrade` | 升级配额级别（DEFAULT → PREMIUM） |

---

## Learning

| Method | Path | 说明 |
|--------|------|------|
| POST | `/learning/plans` | 创建学习计划 |
| GET | `/learning/plans` | 获取学习计划列表 |
| POST | `/learning/sessions` | 记录学习会话 |
| GET | `/learning/sessions` | 获取学习会话历史 |
| GET | `/learning/stats` | 学习统计 |

---

## Schedule

| Method | Path | 说明 |
|--------|------|------|
| POST | `/schedule/events` | 创建日程事件 |
| GET | `/schedule/events` | 获取日程事件列表 |
| PUT | `/schedule/events/{id}` | 更新日程事件 |
| DELETE | `/schedule/events/{id}` | 删除日程事件 |

---

## Actuator / Monitoring

| Method | Path | 说明 |
|--------|------|------|
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/info` | 应用信息 |
| GET | `/actuator/metrics` | Micrometer 指标 |
| GET | `/actuator/prometheus` | Prometheus 抓取端点 |

---

## 通用响应格式

所有 API 返回统一 `ApiResponse` 包装：

```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... },
  "timestamp": "2026-08-10T12:00:00"
}
```

错误响应：

```json
{
  "success": false,
  "message": "错误描述",
  "data": null,
  "timestamp": "2026-08-10T12:00:00"
}
```
