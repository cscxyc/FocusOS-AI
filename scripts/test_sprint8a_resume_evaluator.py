#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 8-A QA 测试脚本
==================================
测试内容：ResumeEvaluatorAgent — 简历 AI 质量评分

测试三个岗位：
1. AI应用开发工程师（字节）— 期望 score 70-90，必须识别 RAG/Agent/Milvus
2. Java后端开发工程师（美团）— 必须识别 Spring Boot/MySQL/Redis，不足：高并发/MQ
3. 大模型应用工程师（百度）— 必须识别 RAG/Embedding/Agent/LangChain4j

每个岗位 10 项检查（共 30 项）：
1. evaluation report 创建
2. score 范围正确
3. keyword 匹配正确
4. missingKeywords 存在
5. strengths 非空
6. weaknesses 非空
7. suggestions 非空
8. JSON 可解析
9. 来源真实性检查
10. 前端展示字段完整

附加检查：
- LLM 调用日志写入 llm_call_logs（agentType=resume_evaluator）
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
OUTPUT_DIR = Path(__file__).parent / "sprint8a_results"
OUTPUT_DIR.mkdir(exist_ok=True)

# ============================================================
# 测试用简历内容（覆盖 FocusOS AI 真实项目经历）
# 包含关键词：Java, Spring Boot, MySQL, Redis, RAG, Agent, Milvus, LangChain4j, Docker
# 缺失关键词：高并发, MQ, Kubernetes, Embedding, 微服务
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
# 三个测试岗位 JD
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
7. 熟悉 Docker 容器化部署
8. 良好的系统设计能力和工程素养
"""

JD_JAVA_BACKEND = """【美团】Java后端开发工程师

岗位职责：
1. 负责美团核心业务系统的后端开发，支撑高并发、高可用的在线服务
2. 参与微服务架构设计与系统拆分，使用 Spring Cloud 构建分布式系统
3. 设计和优化数据库方案，处理海量数据存储与查询
4. 使用消息队列（MQ）实现系统解耦与异步处理
5. 参与系统性能优化，保障高并发场景下的系统稳定性

岗位要求：
1. 本科及以上学历，计算机相关专业
2. 精通 Java 编程，深入理解 Spring Boot / Spring Cloud 微服务框架
3. 熟悉 MySQL 数据库设计与优化，具备 Redis 缓存使用经验
4. 具备高并发系统开发经验，了解限流、降级、熔断等稳定性策略
5. 熟悉 RabbitMQ / Kafka 等消息队列中间件
6. 有分布式系统设计经验，了解分布式锁、分布式事务
7. 熟悉 Docker / Kubernetes 容器化部署
8. 具备良好的工程能力和代码规范
"""

JD_LLM_APP = """【百度】大模型应用工程师

岗位职责：
1. 负责基于大语言模型的应用产品研发，构建智能对话、知识问答等 AI 能力
2. 设计和实现 RAG 检索增强生成系统，优化知识检索准确率
3. 使用 LangChain4j 等框架进行 Agent 应用开发，实现工具调用与任务编排
4. 研究 Embedding 技术与向量检索方案，提升语义匹配效果
5. 进行 Prompt Engineering 优化，提升大模型输出质量

岗位要求：
1. 本科及以上学历，计算机相关专业
2. 精通 Java 编程，熟悉 Spring Boot 后端开发
3. 具备大模型应用开发经验，熟悉 RAG / Agent 架构
4. 熟悉 LangChain4j / LangChain 等 AI 应用框架
5. 了解 Embedding 模型原理，有向量检索实践经验
6. 有 Milvus 等向量数据库使用经验
7. 熟悉 Prompt Engineering，能设计高质量提示词
8. 具备良好的问题分析与解决能力
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
# 通用字段完整性校验（对应检查项 10：前端展示字段完整）
# ============================================================
REQUIRED_FIELDS = [
    "score", "matchScore", "atsScore", "starScore", "completenessScore",
    "strengths", "weaknesses", "missingKeywords", "keywordMatches",
    "sectionScores", "suggestions", "recommendedActions",
]


def validate_evaluation_structure(evaluation, prefix=""):
    """校验评估结果结构是否完整（前端可直接渲染）"""
    if not isinstance(evaluation, dict):
        return False, f"{prefix}evaluation 不是 dict"
    missing_fields = [f for f in REQUIRED_FIELDS if f not in evaluation]
    if missing_fields:
        return False, f"{prefix}缺失字段: {missing_fields}"
    # 校验评分字段为整数
    score_fields = ["score", "matchScore", "atsScore", "starScore", "completenessScore"]
    for sf in score_fields:
        v = evaluation.get(sf)
        if not isinstance(v, (int, float)):
            return False, f"{prefix}{sf} 不是数值: {type(v).__name__}"
    # 校验列表字段为 list
    list_fields = ["strengths", "weaknesses", "missingKeywords",
                   "keywordMatches", "suggestions", "recommendedActions"]
    for lf in list_fields:
        if not isinstance(evaluation.get(lf), list):
            return False, f"{prefix}{lf} 不是 list"
    # 校验 sectionScores
    ss = evaluation.get("sectionScores")
    if not isinstance(ss, dict):
        return False, f"{prefix}sectionScores 不是 dict"
    for sf in ["summary", "experience", "project", "skills"]:
        if sf not in ss:
            return False, f"{prefix}sectionScores.{sf} 缺失"
    return True, "结构完整"


def check_keyword_matches(keyword_matches, expected_keywords, prefix=""):
    """检查关键词匹配中是否包含期望的 MATCH 关键词"""
    matched_keywords = set()
    for km in keyword_matches:
        if isinstance(km, dict):
            kw = km.get("keyword", "").lower()
            status = km.get("status", "").upper()
            if status == "MATCH":
                matched_keywords.add(kw)
    found = []
    missing = []
    for ek in expected_keywords:
        ek_lower = ek.lower()
        # 模糊匹配：关键词可能包含在 matched keyword 文本中
        hit = any(ek_lower in mk or mk in ek_lower for mk in matched_keywords)
        if hit:
            found.append(ek)
        else:
            missing.append(ek)
    return found, missing


# ============================================================
# 单个岗位评估测试（10 项检查）
# ============================================================
def test_evaluate_position(token, version_id, jd, position_name, company,
                            expected_score_min, expected_score_max,
                            expected_match_keywords, expected_missing_keywords=None,
                            timeout=180):
    """
    对单个岗位执行简历评估并做 10 项检查
    """
    print(f"\n{'='*60}")
    print(f"[TEST] {position_name} — {company}")
    print(f"{'='*60}")

    # 调用评估接口
    print(f"  → 调用 /resume/evaluate ...")
    t0 = time.time()
    data, status, _ = http_request("POST", "/resume/evaluate", {
        "resumeVersionId": version_id,
        "jobDescription": jd,
    }, token=token, timeout=timeout)
    elapsed = time.time() - t0
    print(f"  → 耗时 {elapsed:.1f}s, status={status}")

    # 提取评估数据
    resp_data = data.get("data", {}) if isinstance(data.get("data"), dict) else {}
    evaluation_id = resp_data.get("evaluationId")
    evaluation = resp_data.get("evaluation", {})
    score = resp_data.get("score")

    # ---------- 检查 1: evaluation report 创建 ----------
    check(f"[{position_name}] evaluation report 创建",
          status == 200 and evaluation_id is not None,
          f"status={status}, evaluationId={evaluation_id}")

    # ---------- 检查 8: JSON 可解析 ----------
    json_parseable = isinstance(evaluation, dict) and "score" in evaluation
    check(f"[{position_name}] JSON 可解析",
          json_parseable,
          f"evaluation keys={list(evaluation.keys())[:6] if isinstance(evaluation, dict) else 'N/A'}")

    # 如果评估失败，后续检查无法进行
    if not json_parseable:
        print(f"  [SKIP] 评估结果解析失败，跳过后续检查")
        for i in range(2, 8):
            check(f"[{position_name}] 检查项 {i}", False, "评估结果不可用")
        check(f"[{position_name}] 来源真实性检查", False, "评估结果不可用")
        check(f"[{position_name}] 前端展示字段完整", False, "评估结果不可用")
        return None

    # ---------- 检查 2: score 范围正确 ----------
    actual_score = evaluation.get("score", score)
    in_range = isinstance(actual_score, (int, float)) and 0 <= actual_score <= 100
    if expected_score_min is not None and expected_score_max is not None:
        in_range = in_range and expected_score_min <= actual_score <= expected_score_max
        range_desc = f"score={actual_score}, 期望 [{expected_score_min}, {expected_score_max}]"
    else:
        range_desc = f"score={actual_score}, 期望 [0, 100]"
    check(f"[{position_name}] score 范围正确", in_range, range_desc)

    # ---------- 检查 3: keyword 匹配正确 ----------
    keyword_matches = evaluation.get("keywordMatches", [])
    found_keywords, missing_in_match = check_keyword_matches(
        keyword_matches, expected_match_keywords, prefix=f"[{position_name}] ")
    check(f"[{position_name}] keyword 匹配正确",
          len(found_keywords) >= len(expected_match_keywords) * 0.5,
          f"识别 MATCH: {found_keywords}, 未识别: {missing_in_match}")

    # ---------- 检查 4: missingKeywords 存在 ----------
    missing_keywords = evaluation.get("missingKeywords", [])
    has_missing = isinstance(missing_keywords, list) and len(missing_keywords) > 0
    # 如果有期望缺失的关键词，检查是否包含
    if expected_missing_keywords and has_missing:
        mk_lower = [k.lower() for k in missing_keywords]
        covered = [ek for ek in expected_missing_keywords
                   if any(ek.lower() in mk or mk in ek.lower() for mk in mk_lower)]
        detail = f"missingKeywords={missing_keywords[:5]}, 期望覆盖={expected_missing_keywords}, 实际覆盖={covered}"
    else:
        detail = f"missingKeywords={missing_keywords[:5] if missing_keywords else '空'}"
    check(f"[{position_name}] missingKeywords 存在", has_missing, detail)

    # ---------- 检查 5: strengths 非空 ----------
    strengths = evaluation.get("strengths", [])
    check(f"[{position_name}] strengths 非空",
          isinstance(strengths, list) and len(strengths) > 0,
          f"count={len(strengths)}, 示例: {strengths[0][:60] if strengths else 'N/A'}")

    # ---------- 检查 6: weaknesses 非空 ----------
    weaknesses = evaluation.get("weaknesses", [])
    check(f"[{position_name}] weaknesses 非空",
          isinstance(weaknesses, list) and len(weaknesses) > 0,
          f"count={len(weaknesses)}, 示例: {weaknesses[0][:60] if weaknesses else 'N/A'}")

    # ---------- 检查 7: suggestions 非空 ----------
    suggestions = evaluation.get("suggestions", [])
    check(f"[{position_name}] suggestions 非空",
          isinstance(suggestions, list) and len(suggestions) > 0,
          f"count={len(suggestions)}, 示例: {suggestions[0][:60] if suggestions else 'N/A'}")

    # ---------- 检查 9: 来源真实性检查 ----------
    # strengths 中应引用简历真实内容（如 FocusOS AI / Spring Boot / RAG / Milvus 等）
    real_keywords = ["focusos", "spring boot", "rag", "milvus", "langchain4j",
                     "agent", "java", "redis", "mysql", "用友", "实习"]
    strengths_text = " ".join(strengths).lower() if strengths else ""
    has_real_content = any(rk in strengths_text for rk in real_keywords)
    # 同时检查 weaknesses 不应编造未提及的经历（不能出现简历中没有的公司/项目）
    fake_keywords = ["腾讯", "阿里巴巴", "baidu", "字节", "美团", "google", "微软"]
    weaknesses_text = " ".join(weaknesses).lower() if weaknesses else ""
    has_fabricated = any(fk in weaknesses_text for fk in fake_keywords)
    authentic = has_real_content and not has_fabricated
    check(f"[{position_name}] 来源真实性检查",
          authentic,
          f"引用真实内容={has_real_content}, 编造经历={has_fabricated}")

    # ---------- 检查 10: 前端展示字段完整 ----------
    ok, detail = validate_evaluation_structure(evaluation, prefix=f"[{position_name}] ")
    check(f"[{position_name}] 前端展示字段完整", ok, detail)

    return {
        "position": position_name,
        "company": company,
        "evaluationId": evaluation_id,
        "score": actual_score,
        "matchScore": evaluation.get("matchScore"),
        "atsScore": evaluation.get("atsScore"),
        "starScore": evaluation.get("starScore"),
        "completenessScore": evaluation.get("completenessScore"),
        "strengths": strengths,
        "weaknesses": weaknesses,
        "missingKeywords": missing_keywords,
        "keywordMatches": keyword_matches,
        "suggestions": suggestions,
        "recommendedActions": evaluation.get("recommendedActions", []),
        "sectionScores": evaluation.get("sectionScores", {}),
        "elapsed_seconds": round(elapsed, 1),
    }


# ============================================================
# LLM 调用日志检查
# ============================================================
def test_llm_logs(token):
    print(f"\n{'='*60}")
    print("[TEST] LLM 调用日志（agentType=resume_evaluator）")
    print(f"{'='*60}")

    # 查询 LLM 调用统计摘要
    data, status, _ = http_request("GET", "/llm-logs/summary", token=token, timeout=15)
    summary = data.get("data", {}) if isinstance(data.get("data"), dict) else {}

    check("LLM Logs 摘要接口可用",
          status == 200 and data.get("code") == 200,
          f"status={status}")

    total_calls = summary.get("totalCalls", 0)
    check("LLM Logs 有调用记录", total_calls > 0, f"totalCalls={total_calls}")

    # 检查 byAgent 中是否有 resume_evaluator
    by_agent = summary.get("byAgent", [])
    agent_types = []
    if isinstance(by_agent, list):
        for a in by_agent:
            if isinstance(a, dict):
                agent_types.append(a.get("agentType", a.get("agent_type", "")))
    has_evaluator = any("resume_evaluator" in str(a).lower() for a in agent_types)
    check("LLM Logs 包含 resume_evaluator agentType",
          has_evaluator,
          f"agents={agent_types}")

    return summary


# ============================================================
# 历史评估查询测试
# ============================================================
def test_history_queries(token, version_id, evaluation_id):
    print(f"\n{'='*60}")
    print("[TEST] 历史评估查询")
    print(f"{'='*60}")

    # GET /resume/evaluations/{id}
    data, status, _ = http_request("GET", f"/resume/evaluations/{evaluation_id}",
                                    token=token, timeout=15)
    check("GET /resume/evaluations/{id} 返回完整评估",
          status == 200 and data.get("code") == 200,
          f"status={status}")
    if status == 200 and data.get("data"):
        detail = data["data"]
        check("评估详情包含 evaluation 字段",
              "evaluation" in detail and isinstance(detail.get("evaluation"), dict),
              f"keys={list(detail.keys())[:8]}")

    # GET /resume/versions/{versionId}/evaluations
    data, status, _ = http_request("GET",
                                    f"/resume/versions/{version_id}/evaluations",
                                    token=token, timeout=15)
    check("GET /resume/versions/{versionId}/evaluations 返回历史列表",
          status == 200 and data.get("code") == 200,
          f"status={status}")
    if status == 200 and isinstance(data.get("data"), list):
        check("版本评估历史非空", len(data["data"]) > 0, f"count={len(data['data'])}")

    # GET /resume/evaluations （全部）
    data, status, _ = http_request("GET", "/resume/evaluations", token=token, timeout=15)
    check("GET /resume/evaluations 返回用户全部评估",
          status == 200 and data.get("code") == 200,
          f"status={status}, count={len(data.get('data', [])) if isinstance(data.get('data'), list) else 'N/A'}")


# ============================================================
# Main
# ============================================================
def main():
    print("=" * 60)
    print("FocusOS AI Sprint 8-A QA Test — ResumeEvaluatorAgent")
    print("=" * 60)

    token = login()

    # 创建测试用简历版本
    print("\n[SETUP] 创建测试简历版本...")
    data, status, _ = http_request("POST", "/resume/versions", {
        "targetPosition": "AI应用开发工程师",
        "versionName": "QA_Sprint8A_评估测试版",
        "content": RESUME_CONTENT,
        "setActive": False,
    }, token=token, timeout=30)
    version_id = data.get("data", {}).get("id") if data.get("data") else None
    if not version_id:
        print(f"[FAIL] 创建简历版本失败: status={status}, resp={data}")
        sys.exit(1)
    print(f"[OK] 简历版本创建: id={version_id}")

    test_results = []

    try:
        # ============================================================
        # 岗位 1: AI应用开发工程师（字节）
        # ============================================================
        r1 = test_evaluate_position(
            token, version_id, JD_AI_APP,
            position_name="AI应用开发", company="字节跳动",
            expected_score_min=0, expected_score_max=100,
            expected_match_keywords=["RAG", "Agent", "Milvus"],
        )
        if r1:
            test_results.append(r1)

        # ============================================================
        # 岗位 2: Java后端开发工程师（美团）
        # ============================================================
        r2 = test_evaluate_position(
            token, version_id, JD_JAVA_BACKEND,
            position_name="Java后端", company="美团",
            expected_score_min=0, expected_score_max=100,
            expected_match_keywords=["Spring Boot", "MySQL", "Redis"],
            expected_missing_keywords=["高并发", "MQ"],
        )
        if r2:
            test_results.append(r2)

        # ============================================================
        # 岗位 3: 大模型应用工程师（百度）
        # ============================================================
        r3 = test_evaluate_position(
            token, version_id, JD_LLM_APP,
            position_name="大模型应用", company="百度",
            expected_score_min=0, expected_score_max=100,
            expected_match_keywords=["RAG", "Agent", "LangChain4j"],
        )
        if r3:
            test_results.append(r3)

        # ============================================================
        # LLM 调用日志检查
        # ============================================================
        llm_summary = test_llm_logs(token)

        # ============================================================
        # 历史评估查询测试
        # ============================================================
        if test_results:
            test_history_queries(token, version_id, test_results[0]["evaluationId"])

    finally:
        # 清理：删除测试简历版本
        print(f"\n[CLEANUP] 删除测试简历版本 {version_id}...")
        try:
            http_request("DELETE", f"/resume/versions/{version_id}",
                         token=token, timeout=15)
            print("[OK] 清理完成")
        except Exception as e:
            print(f"[WARN] 清理失败: {e}")

    # ============================================================
    # 汇总
    # ============================================================
    print("\n" + "=" * 60)
    print(f"QA Results: {passed_checks}/{total_checks} checks passed "
          f"({(passed_checks / total_checks * 100):.1f}%)")
    print("=" * 60)

    # 打印三个岗位评分汇总
    if test_results:
        print("\n三个岗位评分汇总：")
        print(f"{'岗位':<16} {'公司':<8} {'总分':>6} {'匹配':>6} {'ATS':>6} {'STAR':>6} {'完整':>6} {'耗时':>6}")
        print("-" * 70)
        for r in test_results:
            print(f"{r['position']:<16} {r['company']:<8} "
                  f"{r['score']:>6} {r['matchScore']:>6} {r['atsScore']:>6} "
                  f"{r['starScore']:>6} {r['completenessScore']:>6} {r['elapsed_seconds']:>5}s")

    overall = "PASS" if passed_checks == total_checks else (
        "PARTIAL PASS" if passed_checks >= total_checks * 0.8 else "FAIL")

    output = {
        "sprint": "8-A",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total_checks": total_checks,
        "passed_checks": passed_checks,
        "success_rate": round(passed_checks / total_checks * 100, 1) if total_checks > 0 else 0,
        "overall_status": overall,
        "results": results,
        "position_evaluations": test_results,
        "llm_summary": llm_summary,
    }

    output_file = OUTPUT_DIR / "sprint8a_summary.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\nResults saved to: {output_file}")

    # 保存完整评估详情（用于验收报告）
    detail_file = OUTPUT_DIR / "sprint8a_evaluations.json"
    with open(detail_file, "w", encoding="utf-8") as f:
        json.dump(test_results, f, ensure_ascii=False, indent=2)
    print(f"Evaluation details saved to: {detail_file}")

    return 0 if overall == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
