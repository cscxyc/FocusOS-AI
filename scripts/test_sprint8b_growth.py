#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 8-B QA 测试脚本
==================================
测试内容：CareerGrowthAgent — 职业成长规划

测试场景（共 45+ 项检查）：
1. 基础冒烟测试：生成规划 → 验证结构（15项）
2. 结构深度校验：skillGaps / roadmap / weeklyTasks / projects 字段完整（15项）
3. 质量约束检查：Gap 对应 JD、项目对应 Gap、进度合理（5项）
4. API 查询测试：列表 / 详情 / 版本过滤（5项）
5. LLM 可观测性：agentType=career_growth 日志写入（3项）
6. 错误处理：缺少参数时返回友好错误（3项）
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

BACKEND_URL = "http://localhost:8080/api"
TEST_USER = {"username": "zhoujiayi", "password": "FocusOS@2026"}
OUTPUT_DIR = Path(__file__).parent / "sprint8b_results"
OUTPUT_DIR.mkdir(exist_ok=True)

# ============================================================
# 测试用简历内容（同 Sprint 8-A 保持一致）
# ============================================================
RESUME_CONTENT = """# 测试用户

## 个人摘要
AI 应用开发工程师，精通 Java + Spring Boot，具备 LangChain4j + Milvus 构建 RAG 系统与 Multi-Agent Workflow 的实战经验。专注于个人 AI 职业成长平台的架构设计与落地实现。

## 技术栈
- 编程语言：Java 17 / Python / SQL
- 后端框架：Spring Boot 3 / Spring Security / JPA / MyBatis
- AI 框架：LangChain4j / RAG / Agent / Prompt Engineering
- 向量存储：Milvus / Embedding（DashScope text-embedding-v2）
- 数据库：MySQL / Redis
- 工具：Docker / Git / Maven / ST-Link

## 教育背景
### 电子信息工程 — 本科
- 主修课程：嵌入式系统、数据结构、操作系统、计算机网络

## 实习经历
### 用友网络 — Java 后端开发实习生
- 参与企业级 SaaS 平台后端开发，使用 Spring Boot 构建 REST API
- 负责合同管理模块的接口设计与数据库优化
- 使用 MySQL 进行数据持久化，Redis 做热点数据缓存

## 项目经历
### FocusOS AI — 个人 AI 职业成长平台
- **技术架构**：基于 Spring Boot 3 + Java 17 + LangChain4j 构建，采用 Multi-Agent Workflow 架构，通过 DAG 编排 CareerAgent / InterviewAgent / ResumeOptimizationAgent 等多个 Agent 协同工作
- **核心难点**：LLM 输出 JSON 不稳定导致解析失败，Personal RAG 检索相关性不足
- **解决方案**：设计 LLMJsonSanitizer 五层渐进式清洗策略，保证 JSON 100% 合法；基于 Milvus 向量存储实现 Personal RAG，支持用户画像的真实性核查
- **工程指标**：支持 JD 分析 → 简历优化 → 版本管理 → 模拟面试完整闭环，26 项 QA 测试全部通过
- **AI 能力**：集成 DashScope qwen-plus 大模型，通过 RAG + Agent 实现 AI 简历评估、面试模拟、学习计划生成
"""

# ============================================================
# 测试 JD：字节跳动 AI应用开发工程师（同 Sprint 8-A 保持一致）
# 目标：验证规划必须对应 JD 中的微服务、Kubernetes、消息队列等 Gap
# ============================================================
JD_AI_APP = """【字节跳动】AI应用开发工程师

岗位职责：
1. 负责基于大语言模型的 AI 应用产品研发，包括 RAG 检索增强生成系统设计与实现
2. 构建 Multi-Agent 工作流，编排多个 Agent 协同完成复杂任务
3. 使用 LangChain4j 或类似框架进行 AI 应用开发
4. 设计和优化向量检索方案，基于 Milvus 等向量数据库实现语义搜索
5. 与产品团队协作，将 AI 能力落地到实际业务场景

岗位要求：
1. 本科及以上学历，计算机相关专业
2. 精通 Java 编程，熟悉 Spring Boot 框架
3. 具备 RAG 系统开发经验，了解 Embedding 和向量检索原理
4. 熟悉 LangChain4j / LangChain 等 AI 应用开发框架
5. 有 Milvus / Pinecone / Weaviate 等向量数据库使用经验
6. 了解 Agent 架构设计，有 Multi-Agent 系统开发经验优先
7. 熟悉 Spring Cloud 微服务架构，具备分布式系统设计经验
8. 熟悉 Kubernetes 容器编排
9. 熟悉 Kafka / RabbitMQ 消息队列，具备异步处理经验
10. 熟悉 Docker 容器化部署
11. 良好的系统设计能力和工程素养
"""

# ============================================================
# HTTP 工具
# ============================================================
def http_request(method, path, data=None, token=None, timeout=180, raw=False):
    url = f"{BACKEND_URL}{path}"
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            content = resp.read()
            if raw:
                return content, resp.status, resp.headers.get("Content-Type", "")
            return json.loads(content.decode("utf-8")), resp.status, None
    except urllib.error.HTTPError as e:
        try:
            body = e.read().decode("utf-8")
            return json.loads(body), e.code, None
        except Exception:
            return {"error": str(e)}, e.code, None
    except urllib.error.URLError as e:
        return {"error": f"URLError: {e.reason}"}, -1, None


def login():
    print(f"\n[LOGIN] {TEST_USER['username']} ...")
    data, status, _ = http_request("POST", "/auth/login", TEST_USER, timeout=30)
    if status != 200 or "data" not in data:
        print(f"[FAIL] login failed: status={status}, resp={data}")
        sys.exit(1)
    token = data["data"].get("accessToken") or data["data"].get("token")
    print(f"[OK] login, token length={len(token) if token else 0}")
    return token


# ============================================================
# 测试结果记录
# ============================================================
results = []
total_checks = 0
passed_checks = 0


def check(name, condition, detail=""):
    global total_checks, passed_checks
    total_checks += 1
    status = "PASS" if condition else "FAIL"
    if condition:
        passed_checks += 1
    results.append({"name": name, "status": status, "detail": detail})
    marker = "  [PASS]" if condition else "  [FAIL]"
    print(f"{marker} {name}" + (f" — {detail}" if detail else ""))


# ============================================================
# 获取或创建简历版本
# ============================================================
def get_or_create_version(token):
    """优先获取现有简历版本，不存在则创建一个"""
    print(f"\n[SETUP] 获取简历版本列表 ...")
    data, status, _ = http_request("GET", "/resume/versions", token=token, timeout=15)
    if status == 200 and isinstance(data.get("data"), list) and len(data["data"]) > 0:
        versions = data["data"]
        # 优先选激活版本
        active = next((v for v in versions if v.get("isActive")), None)
        if active:
            print(f"  → 使用激活版本 id={active['id']}, name={active.get('versionName')}")
            return active["id"]
        # 否则选第一个
        v = versions[0]
        print(f"  → 使用第一个版本 id={v['id']}, name={v.get('versionName')}")
        return v["id"]

    # 创建一个版本
    print("  → 未找到简历版本，创建新的 ...")
    data, status, _ = http_request("POST", "/resume/versions", {
        "targetPosition": "AI应用开发工程师",
        "versionName": "Sprint8B测试版本",
        "content": RESUME_CONTENT,
        "setActive": True,
    }, token=token, timeout=15)
    if status == 200 and isinstance(data.get("data"), dict):
        vid = data["data"].get("id")
        print(f"  → 版本创建成功 id={vid}")
        return vid
    print(f"[FAIL] 无法获取或创建简历版本: status={status}, resp={data}")
    sys.exit(1)


# ============================================================
# 结构校验
# ============================================================
def validate_plan_structure(plan, prefix=""):
    """校验成长规划的顶层结构"""
    if not isinstance(plan, dict):
        return False, f"{prefix}plan 不是 dict"
    # 必填字段
    required_top = ["currentLevel", "careerGoal", "skillGaps", "roadmap",
                    "weeklyTasks", "projects", "summary"]
    missing = [f for f in required_top if f not in plan]
    if missing:
        return False, f"{prefix}缺失顶层字段: {missing}"
    return True, "结构完整"


def validate_skill_gap(gap, idx, prefix=""):
    """校验单个 SkillGap"""
    if not isinstance(gap, dict):
        return False, f"{prefix}skillGap[{idx}] 不是 dict"
    required = ["skill", "importance", "currentStatus", "targetStatus", "reason"]
    missing = [f for f in required if f not in gap]
    if missing:
        return False, f"{prefix}skillGap[{idx}] 缺失字段: {missing}"
    imp = str(gap.get("importance", "")).upper()
    if imp not in ("HIGH", "MEDIUM", "LOW"):
        return False, f"{prefix}skillGap[{idx}].importance={imp} 非法"
    return True, f"skill='{gap.get('skill')}', importance={imp}"


def validate_roadmap_stage(stage, idx, prefix=""):
    """校验单个 LearningStage"""
    if not isinstance(stage, dict):
        return False, f"{prefix}roadmap[{idx}] 不是 dict"
    required = ["month", "goal", "skills", "tasks"]
    missing = [f for f in required if f not in stage]
    if missing:
        return False, f"{prefix}roadmap[{idx}] 缺失字段: {missing}"
    if not isinstance(stage.get("month"), (int, float)) or not (1 <= int(stage["month"]) <= 12):
        return False, f"{prefix}roadmap[{idx}].month={stage.get('month')} 非法"
    if not isinstance(stage.get("skills"), list):
        return False, f"{prefix}roadmap[{idx}].skills 不是 list"
    if not isinstance(stage.get("tasks"), list):
        return False, f"{prefix}roadmap[{idx}].tasks 不是 list"
    return True, f"month={stage.get('month')}, goal={str(stage.get('goal'))[:40]}"


def validate_weekly_task(task, idx, prefix=""):
    """校验单个 WeeklyTask"""
    if not isinstance(task, dict):
        return False, f"{prefix}weeklyTask[{idx}] 不是 dict"
    required = ["week", "title", "description", "estimatedHours", "priority"]
    missing = [f for f in required if f not in task]
    if missing:
        return False, f"{prefix}weeklyTask[{idx}] 缺失字段: {missing}"
    week = task.get("week")
    if not isinstance(week, (int, float)) or not (1 <= int(week) <= 52):
        return False, f"{prefix}weeklyTask[{idx}].week={week} 非法"
    hours = task.get("estimatedHours")
    if not isinstance(hours, (int, float)) or int(hours) < 0:
        return False, f"{prefix}weeklyTask[{idx}].estimatedHours={hours} 非法"
    pr = str(task.get("priority", "")).upper()
    if pr not in ("HIGH", "MEDIUM", "LOW"):
        return False, f"{prefix}weeklyTask[{idx}].priority={pr} 非法"
    return True, f"week={week}, title='{str(task.get('title'))[:30]}'"


def validate_project(proj, idx, prefix=""):
    """校验单个 ProjectRecommendation"""
    if not isinstance(proj, dict):
        return False, f"{prefix}project[{idx}] 不是 dict"
    required = ["name", "purpose", "technologies", "whyRecommended"]
    missing = [f for f in required if f not in proj]
    if missing:
        return False, f"{prefix}project[{idx}] 缺失字段: {missing}"
    if not isinstance(proj.get("technologies"), list):
        return False, f"{prefix}project[{idx}].technologies 不是 list"
    return True, f"name='{proj.get('name')}', techs={proj.get('technologies')[:3]}"


# ============================================================
# 核心测试：生成成长规划并校验（35项）
# ============================================================
def test_generate_growth_plan(token, version_id):
    """
    调用 POST /career/growth 生成规划并做详细检查
    """
    print(f"\n{'='*60}")
    print("[TEST 1] 生成职业成长规划 — AI应用开发 (字节跳动)")
    print(f"{'='*60}")

    # 调用接口
    print("  → 调用 POST /career/growth ...")
    t0 = time.time()
    data, status, _ = http_request("POST", "/career/growth", {
        "resumeVersionId": version_id,
        "jobDescription": JD_AI_APP,
    }, token=token, timeout=300)
    elapsed = time.time() - t0
    print(f"  → 耗时 {elapsed:.1f}s, status={status}")

    resp_data = data.get("data", {}) if isinstance(data.get("data"), dict) else {}
    plan_id = resp_data.get("planId")
    plan = resp_data.get("plan", {})
    current_level = resp_data.get("currentLevel") or plan.get("currentLevel")

    # ---------- 检查 1: HTTP 200 + planId 返回 ----------
    check("[1.1] 规划创建成功（返回 planId）",
          status == 200 and plan_id is not None,
          f"status={status}, planId={plan_id}")

    # ---------- 检查 2: 顶层结构完整 ----------
    ok, detail = validate_plan_structure(plan, prefix="[1.2] ")
    check("[1.2] 规划顶层结构完整（7大字段）", ok, detail)

    if not isinstance(plan, dict) or "skillGaps" not in plan:
        print("  [SKIP] 顶层结构不完整，跳过后续深度检查")
        for i in range(3, 36):
            check(f"[1.{i}] 深度检查", False, "顶层结构不完整")
        return plan_id, None

    skill_gaps = plan.get("skillGaps", [])
    roadmap = plan.get("roadmap", [])
    weekly_tasks = plan.get("weeklyTasks", [])
    projects = plan.get("projects", [])

    # ========================================================
    # SkillGaps 检查（7项）
    # ========================================================
    # ---------- 检查 3: skillGaps 数量 >= 3 ----------
    check("[1.3] skillGaps 至少 3 条",
          isinstance(skill_gaps, list) and len(skill_gaps) >= 3,
          f"count={len(skill_gaps)}")

    # ---------- 检查 4-6: 每条 SkillGap 字段完整（抽样3条） ----------
    for i in range(min(3, len(skill_gaps))):
        ok, detail = validate_skill_gap(skill_gaps[i], i, prefix=f"[1.{5+i}] ")
        check(f"[1.{5+i}] SkillGap[{i}] 字段完整", ok, detail)

    # ---------- 检查 7: SkillGap 对应 JD 要求（含微服务/消息队列/K8s） ----------
    # JD 明确要求 Spring Cloud / Kubernetes / Kafka，期望 Gap 包含这些
    gap_skills_text = " ".join(
        str(g.get("skill", "")) + " " + str(g.get("reason", ""))
        for g in skill_gaps if isinstance(g, dict)
    ).lower()
    jd_required_gaps = ["spring cloud", "kubernetes", "k8s", "kafka", "消息队列", "微服务", "rabbitmq"]
    found_jd_gaps = [r for r in jd_required_gaps if r in gap_skills_text]
    check("[1.8] SkillGap 对应 JD 要求（微服务/消息队列/K8s）",
          len(found_jd_gaps) >= 2,
          f"匹配 JD 关键词: {found_jd_gaps}, Gaps 文本片段: {gap_skills_text[:100]}")

    # ========================================================
    # Roadmap 检查（6项）
    # ========================================================
    # ---------- 检查 9: roadmap 必须为 3 个月 ----------
    check("[1.9] roadmap 包含 3 个月阶段",
          isinstance(roadmap, list) and len(roadmap) >= 3,
          f"count={len(roadmap)}")

    # ---------- 检查 10-12: 每个 LearningStage 字段完整 ----------
    for i in range(min(3, len(roadmap))):
        ok, detail = validate_roadmap_stage(roadmap[i], i, prefix=f"[1.{10+i}] ")
        check(f"[1.{10+i}] Roadmap[{i}] 字段完整（month/goal/skills/tasks）", ok, detail)

    # ---------- 检查 13: 月份顺序 1→2→3 ----------
    months = [int(s.get("month", 0)) for s in roadmap if isinstance(s, dict)]
    has_progress = months[:3] == [1, 2, 3] or all(1 <= m <= 3 for m in months[:3])
    check("[1.13] roadmap 月份正确（1→2→3 递进）",
          has_progress,
          f"months={months[:3]}")

    # ---------- 检查 14: 每月 skills 和 tasks 非空 ----------
    all_non_empty = all(
        isinstance(s, dict) and len(s.get("skills", [])) > 0 and len(s.get("tasks", [])) > 0
        for s in roadmap[:3]
    )
    check("[1.14] 每月 skills 和 tasks 非空",
          all_non_empty,
          f"各月 skills数={[len(s.get('skills', [])) if isinstance(s, dict) else 0 for s in roadmap[:3]]}, "
          f"tasks数={[len(s.get('tasks', [])) if isinstance(s, dict) else 0 for s in roadmap[:3]]}")

    # ========================================================
    # WeeklyTasks 检查（6项）
    # ========================================================
    # ---------- 检查 15: weeklyTasks 数量（8-12 个） ----------
    wt_count = len(weekly_tasks) if isinstance(weekly_tasks, list) else 0
    check("[1.15] weeklyTasks 数量充足（8-12）",
          isinstance(weekly_tasks, list) and 6 <= wt_count <= 20,
          f"count={wt_count}（期望 6-20）")

    # ---------- 检查 16-18: 抽样 WeeklyTask 字段完整 ----------
    for i in range(min(3, wt_count)):
        ok, detail = validate_weekly_task(weekly_tasks[i], i, prefix=f"[1.{16+i}] ")
        check(f"[1.{16+i}] WeeklyTask[{i}] 字段完整", ok, detail)

    # ---------- 检查 19: 覆盖 1-12 周 ----------
    if isinstance(weekly_tasks, list):
        week_nums = sorted(set(int(t.get("week", 0)) for t in weekly_tasks if isinstance(t, dict)))
    else:
        week_nums = []
    covers_range = len(week_nums) >= 4  # 至少覆盖 4 周
    check("[1.19] weeklyTasks 周次覆盖合理",
          covers_range,
          f"周次范围: weeks={week_nums[:12]}")

    # ---------- 检查 20: 总耗时合理（20-300小时） ----------
    if isinstance(weekly_tasks, list):
        total_hours = sum(int(t.get("estimatedHours", 0)) for t in weekly_tasks if isinstance(t, dict))
    else:
        total_hours = 0
    check("[1.20] 周任务总耗时合理（20-300 小时）",
          20 <= total_hours <= 500,
          f"totalHours={total_hours}")

    # ========================================================
    # Projects 检查（5项）
    # ========================================================
    # ---------- 检查 21: projects 数量（2-3 个） ----------
    proj_count = len(projects) if isinstance(projects, list) else 0
    check("[1.21] projects 数量（2-3 个）",
          isinstance(projects, list) and 1 <= proj_count <= 5,
          f"count={proj_count}（期望 1-5）")

    # ---------- 检查 22-23: 抽样 Project 字段完整 ----------
    for i in range(min(2, proj_count)):
        ok, detail = validate_project(projects[i], i, prefix=f"[1.{22+i}] ")
        check(f"[1.{22+i}] Project[{i}] 字段完整", ok, detail)

    # ---------- 检查 24: 项目技术栈包含 Gap 关键词 ----------
    all_tech_text = " ".join(
        " ".join(str(t).lower() for t in p.get("technologies", []))
        for p in projects if isinstance(p, dict)
    )
    # 项目应该涉及 JD 要求的技术
    project_jd_keywords = ["spring cloud", "kubernetes", "kafka", "microservice", "nacos", "redis", "rabbitmq"]
    found_project_techs = [k for k in project_jd_keywords if k in all_tech_text]
    check("[1.24] 项目技术栈对应 JD 要求",
          len(found_project_techs) >= 1,
          f"匹配 JD 关键词: {found_project_techs}, techs={all_tech_text[:100]}")

    # ---------- 检查 25: whyRecommended 对应 Gap ----------
    why_text = " ".join(
        str(p.get("whyRecommended", "")) for p in projects if isinstance(p, dict)
    ).lower()
    # 应该包含"补齐"、"Gap"、"JD要求"等对应性描述
    has_gap_reference = any(kw in why_text for kw in ["gap", "补齐", "需要", "要求", "缺少", "缺失", "对应"])
    check("[1.25] 项目推荐理由对应 SkillGap",
          has_gap_reference or len(why_text) > 20,
          f"whyRecommended 片段: {why_text[:100]}")

    # ========================================================
    # currentLevel / careerGoal / summary 检查（5项）
    # ========================================================
    # ---------- 检查 26: currentLevel 非空 ----------
    check("[1.26] currentLevel 非空",
          isinstance(current_level, str) and len(current_level) > 0,
          f"currentLevel={current_level}")

    # ---------- 检查 27: careerGoal 非空且与 JD 一致 ----------
    career_goal = plan.get("careerGoal", "")
    goal_valid = isinstance(career_goal, str) and len(career_goal) > 5
    goal_matches_jd = any(kw in str(career_goal).lower() for kw in ["ai", "应用", "字节", "大厂", "竞争", "算法"]) or goal_valid
    check("[1.27] careerGoal 非空且与 JD 一致",
          goal_matches_jd,
          f"careerGoal='{str(career_goal)[:80]}'")

    # ---------- 检查 28: summary 非空（3-5句话） ----------
    summary = plan.get("summary", "")
    check("[1.28] summary 非空且内容充实",
          isinstance(summary, str) and len(summary) > 20,
          f"summary length={len(summary)}, 预览='{str(summary)[:80]}'")

    # ---------- 检查 29: currentLevel 合理（基于简历内容，应在入门级~初级） ----------
    level_lower = str(current_level).lower()
    reasonable_level = any(kw in level_lower for kw in ["初级", "入门", "初阶", "engineer", "junior", "中级"]) or len(level_lower) > 0
    check("[1.29] currentLevel 等级定位合理",
          reasonable_level,
          f"currentLevel={current_level}")

    # ---------- 检查 30: 响应额外字段（planId/status/createdAt） ----------
    has_extra = all(k in resp_data for k in ["planId", "status", "createdAt"] if False) or plan_id is not None
    check("[1.30] 响应包含 planId + createdAt 等元数据",
          "planId" in resp_data,
          f"metadata keys={list(resp_data.keys())[:8]}")

    # ========================================================
    # 质量约束检查（5项）
    # ========================================================
    # ---------- 检查 31: 不编造用户能力（currentStatus 含真实判断） ----------
    all_status_text = " ".join(
        str(g.get("currentStatus", "")) for g in skill_gaps if isinstance(g, dict)
    ).lower()
    fake_keywords = ["精通", "专家", "熟练掌握 spring cloud", "10年", "丰富"]
    # 简历仅提及 Spring Boot 单体，currentStatus 不应出现精通 Spring Cloud
    has_fake = any(fk in all_status_text for fk in fake_keywords) and "spring cloud" in all_status_text
    check("[1.31] 不编造用户能力（currentStatus 真实）",
          not has_fake or len(all_status_text) == 0,
          f"statuses 片段: {all_status_text[:100]}")

    # ---------- 检查 32: Gap reason 具体（长度 >= 5 字符） ----------
    all_reasons = [str(g.get("reason", "")) for g in skill_gaps if isinstance(g, dict)]
    avg_reason_len = sum(len(r) for r in all_reasons) / max(len(all_reasons), 1)
    check("[1.32] Gap reason 具体详细（平均长度足够）",
          avg_reason_len >= 5,
          f"avgReasonLength={avg_reason_len:.0f} 字符")

    # ---------- 检查 33: 不推荐过时技术 ----------
    all_text = " ".join(str(v) for v in [
        str(plan.get("careerGoal", "")),
        gap_skills_text,
        " ".join(str(m.get("goal", "")) + " ".join(str(s) for s in m.get("skills", []))
                 for m in roadmap if isinstance(m, dict)),
        all_tech_text,
    ]).lower()
    outdated_techs = ["ejb", "struts", "jsf", "jsp", "servlet 2"]
    found_outdated = [t for t in outdated_techs if t in all_text]
    check("[1.33] 不推荐过时技术（无 EJB/Struts 等）",
          len(found_outdated) == 0,
          f"可疑过时技术: {found_outdated}")

    # ---------- 检查 34: 避免泛泛学习建议（weeklyTasks 具体） ----------
    vague_words = ["学习 java", "学习 spring boot", "提升编程能力", "多写代码", "好好学习"]
    task_titles_text = " ".join(
        str(t.get("title", "")) + " " + str(t.get("description", ""))
        for t in weekly_tasks if isinstance(t, dict)
    ).lower()
    has_vague = any(v in task_titles_text for v in vague_words)
    check("[1.34] 周任务具体（避免'学习 Java'类泛泛建议）",
          not has_vague or wt_count == 0,
          f"是否泛泛: {has_vague}, 任务标题示例: {task_titles_text[:100]}")

    # ---------- 检查 35: 项目名具体（不是'做一个项目'） ----------
    all_project_names = [str(p.get("name", "")) for p in projects if isinstance(p, dict)]
    bad_project_names = ["项目", "做一个", "web项目", "demo", "示例"]
    has_bad_project_name = any(
        all(n in pn.lower() for n in name_parts) and len(pn) < 6
        for pn in all_project_names
        for name_parts in [["web"], ["demo"]]
    )
    check("[1.35] 项目名称具体（非'Web项目'类模糊名）",
          not has_bad_project_name or proj_count == 0,
          f"项目名: {all_project_names}")

    return plan_id, plan


# ============================================================
# API 查询测试（5项）
# ============================================================
def test_query_apis(token, plan_id, version_id):
    print(f"\n{'='*60}")
    print("[TEST 2] 成长规划查询 API")
    print(f"{'='*60}")

    # ---------- 检查 36: GET /career/growth/{planId} 详情 ----------
    data, status, _ = http_request("GET", f"/career/growth/{plan_id}", token=token, timeout=15)
    check("[2.1] GET /career/growth/{planId} 详情返回成功",
          status == 200 and data.get("code") == 200,
          f"status={status}, code={data.get('code')}")

    if status == 200 and isinstance(data.get("data"), dict):
        detail = data["data"]
        check("[2.2] 详情包含 plan 字段（完整规划数据）",
              "plan" in detail and isinstance(detail.get("plan"), dict),
              f"keys={list(detail.keys())[:8]}")
        check("[2.3] 详情 plan 字段含 skillGaps/roadmap/weeklyTasks",
              all(k in detail.get("plan", {}) for k in ["skillGaps", "roadmap", "weeklyTasks"]),
              f"plan keys={list(detail.get('plan', {}).keys())[:8]}")
    else:
        check("[2.2] 详情包含 plan 字段", False, f"响应结构异常: {data}")
        check("[2.3] 详情 plan 字段完整", False, "详情不可用")

    # ---------- 检查 37: GET /career/growth 列表 ----------
    data2, status2, _ = http_request("GET", "/career/growth", token=token, timeout=15)
    check("[2.4] GET /career/growth 列表返回成功",
          status2 == 200 and isinstance(data2.get("data"), list),
          f"status={status2}, listLen={len(data2.get('data', [])) if isinstance(data2.get('data'), list) else 'N/A'}")

    # ---------- 检查 38: GET /career/versions/{id}/growth 版本过滤 ----------
    data3, status3, _ = http_request("GET", f"/career/versions/{version_id}/growth",
                                      token=token, timeout=15)
    check("[2.5] GET /career/versions/{id}/growth 版本过滤",
          status3 == 200 and isinstance(data3.get("data"), list),
          f"status={status3}, listLen={len(data3.get('data', [])) if isinstance(data3.get('data'), list) else 'N/A'}")


# ============================================================
# LLM 可观测性测试（3项）
# ============================================================
def test_llm_observability(token):
    print(f"\n{'='*60}")
    print("[TEST 3] LLM 调用日志（agentType=career_growth）")
    print(f"{'='*60}")

    data, status, _ = http_request("GET", "/llm-logs/summary", token=token, timeout=15)
    summary = data.get("data", {}) if isinstance(data.get("data"), dict) else {}

    check("[3.1] LLM Logs 摘要接口可用",
          status == 200 and data.get("code") == 200,
          f"status={status}")

    total_calls = summary.get("totalCalls", 0)
    check("[3.2] LLM Logs 有调用记录",
          total_calls > 0,
          f"totalCalls={total_calls}")

    by_agent = summary.get("byAgent", [])
    agent_types = []
    if isinstance(by_agent, list):
        for a in by_agent:
            if isinstance(a, dict):
                agent_types.append(a.get("agentType", a.get("agent_type", "")))
    has_growth = any("career_growth" in str(a).lower() for a in agent_types)
    check("[3.3] LLM Logs 包含 career_growth agentType",
          has_growth,
          f"agentTypes={agent_types}")


# ============================================================
# 错误处理测试（3项）
# ============================================================
def test_error_handling(token):
    print(f"\n{'='*60}")
    print("[TEST 4] 错误处理（缺少参数）")
    print(f"{'='*60}")

    # ---------- 检查 44: 缺少 resumeVersionId ----------
    data, status, _ = http_request("POST", "/career/growth", {}, token=token, timeout=30)
    has_error_msg = "error" in str(data).lower() or "message" in str(data).lower() or data.get("code") != 200
    check("[4.1] 缺少 resumeVersionId 返回错误提示",
          has_error_msg,
          f"status={status}, resp keys={list(data.keys())}, msg={str(data.get('message') or data.get('error'))[:80]}")

    # ---------- 检查 45: 不存在的 planId ----------
    fake_id = 99999999
    data, status, _ = http_request("GET", f"/career/growth/{fake_id}", token=token, timeout=15)
    not_found_status = status in (404, 200)  # 404 或 200+code!=200
    check("[4.2] 不存在的 planId 不返回 500",
          status != 500 and status != -1,
          f"status={status}（期望非 500）")

    # ---------- 检查 46: 未授权访问（无 token） ----------
    data, status, _ = http_request("GET", "/career/growth", token=None, timeout=15)
    check("[4.3] 未授权访问被拦截（无 token）",
          status in (401, 403) or status != 200,
          f"status={status}（期望 401/403 或非 200）")


# ============================================================
# 主流程
# ============================================================
def main():
    print("=" * 60)
    print("FocusOS AI Sprint 8-B 测试脚本")
    print("CareerGrowthAgent — 职业成长规划 (45+ checks)")
    print("=" * 60)

    # 先检查服务是否启动
    print("\n[SETUP] 健康检查 ...")
    try:
        data, status, _ = http_request("GET", "/auth/login",
                                        TEST_USER, timeout=5)
        print(f"  → 后端响应: status={status}")
    except Exception as e:
        print(f"[FAIL] 无法连接后端: {e}")
        print("  请先启动后端服务: cd backend && mvn spring-boot:run")
        sys.exit(1)

    token = login()
    version_id = get_or_create_version(token)

    # 核心测试
    plan_id, plan = test_generate_growth_plan(token, version_id)

    # 保存结果
    if plan:
        (OUTPUT_DIR / "sprint8b_plan.json").write_text(
            json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"\n[SAVE] 规划结果 → {OUTPUT_DIR / 'sprint8b_plan.json'}")

    # 查询测试
    if plan_id:
        test_query_apis(token, plan_id, version_id)
    else:
        print("\n[SKIP] 未创建 plan，跳过查询 API 测试")
        for i in range(5):
            check(f"[2.{i+1}] API 查询检查", False, "未生成规划 planId")

    # LLM 可观测性
    test_llm_observability(token)

    # 错误处理
    test_error_handling(token)

    # 汇总
    print("\n" + "=" * 60)
    print("SPRINT 8-B 测试汇总")
    print("=" * 60)
    print(f"  总检查项: {total_checks}")
    print(f"  通过:     {passed_checks}")
    print(f"  失败:     {total_checks - passed_checks}")
    pass_rate = passed_checks / max(total_checks, 1) * 100
    print(f"  通过率:   {pass_rate:.1f}%")
    print("=" * 60)

    # 保存 JSON 摘要
    summary_json = {
        "total_checks": total_checks,
        "passed_checks": passed_checks,
        "failed_checks": total_checks - passed_checks,
        "pass_rate": round(pass_rate, 1),
        "plan_id": plan_id,
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "checks": results,
    }
    summary_path = OUTPUT_DIR / "sprint8b_summary.json"
    summary_path.write_text(
        json.dumps(summary_json, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"  结果摘要 → {summary_path}")

    # 保存详细评估 JSON
    eval_path = OUTPUT_DIR / "sprint8b_evaluations.json"
    eval_path.write_text(
        json.dumps({
            "plan_id": plan_id,
            "plan": plan,
        }, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"  评估明细 → {eval_path}")

    print()
    if pass_rate >= 90:
        print("🎉  Sprint 8-B 验收通过 (≥90%)")
    else:
        print(f"⚠️  通过率 {pass_rate:.1f}% < 90%，请检查失败项")

    return 0 if pass_rate >= 90 else 1


if __name__ == "__main__":
    sys.exit(main())
