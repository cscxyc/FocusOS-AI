#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 7-A QA 测试
Career Agent 2.0 — AI Career Assistant 闭环验收

测试 3 个真实 AI 岗位 JD：
  1. AI应用开发工程师  → 验证 matchScore / 优势 / 不足 / 建议
  2. Java后端开发工程师 → 验证 Java 优势识别
  3. 大模型应用工程师   → 验证 RAG / Agent / LangChain4j 项目经验识别

每个 JD 执行完整 Career Workflow（5步 DAG）：
  CAREER_ANALYSIS → RESUME_OPTIMIZATION → SKILL_GAP_ANALYSIS → LEARNING_PLAN → INTERVIEW_PREPARATION
"""
import os, sys, json, time, requests, threading

BASE_URL = "http://localhost:8080/api"
LOGIN_PAYLOAD = {"username": "zhoujiayi", "password": "FocusOS@2026"}
RESULT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sprint7a_results")

# ===== 3 个真实 AI 岗位 JD =====
JD_LIST = [
    {
        "name": "test1_ai_app_dev",
        "jobTitle": "AI应用开发工程师",
        "company": "字节跳动",
        "jobDescription": """
【岗位职责】
1. 负责AI应用产品的后端架构设计与开发，包括LLM应用、RAG知识库、Agent工作流等；
2. 参与大模型应用落地方案设计，完成Prompt工程、Function Calling、多轮对话等核心功能开发；
3. 基于Spring Boot构建高并发AI服务后端，支持SSE流式输出、向量检索、多Agent协作；
4. 负责Milvus/Chroma等向量数据库的集成与优化，实现高效的语义检索；
5. 与算法团队协作，将LLM能力封装为可复用的API服务；
6. 编写技术文档，参与代码评审，保证工程质量。

【任职要求】
1. 本科及以上学历，计算机相关专业，1-3年后端开发经验；
2. 精通Java，熟练掌握Spring Boot、Spring Security、MyBatis等框架；
3. 熟悉LangChain4j或LangChain（Python），了解LLM应用开发流程；
4. 熟悉向量数据库（Milvus、Pinecone、Chroma），理解Embedding原理；
5. 了解RAG、Agent、Function Calling等大模型应用架构；
6. 熟悉MySQL、Redis，了解消息队列（Kafka/RabbitMQ）；
7. 有AI应用项目经验者优先，有开源项目贡献者优先；
8. 良好的沟通能力和团队协作精神。
""".strip(),
        "verifyFocus": ["matchScore", "Java", "Spring", "RAG", "Agent", "建议"],
    },
    {
        "name": "test2_java_backend",
        "jobTitle": "Java后端开发工程师",
        "company": "美团",
        "jobDescription": """
【岗位职责】
1. 负责公司核心业务系统的后端开发，支撑高并发、高可用业务场景；
2. 参与微服务架构设计与演进，使用Spring Boot/Spring Cloud构建分布式系统；
3. 负责数据库设计与优化，包括MySQL分库分表、索引优化、慢查询排查；
4. 参与缓存架构设计（Redis），提升系统性能与稳定性；
5. 编写单元测试与集成测试，参与代码评审；
6. 排查线上问题，持续优化系统性能。

【任职要求】
1. 本科及以上学历，计算机相关专业，1-3年Java后端开发经验；
2. 精通Java核心知识（集合、并发、JVM原理），熟悉常用设计模式；
3. 熟练掌握Spring Boot、Spring MVC、MyBatis等主流框架；
4. 熟悉MySQL数据库，了解索引优化、事务隔离级别、分库分表；
5. 熟悉Redis缓存，了解缓存穿透、雪崩、击穿解决方案；
6. 了解微服务架构（Spring Cloud、Nacos、Feign、Gateway）；
7. 熟悉消息队列（Kafka、RabbitMQ），了解异步解耦思想；
8. 有实习经验或开源项目经验者优先。
""".strip(),
        "verifyFocus": ["Java", "Spring", "MySQL", "优势", "实习", "微服务"],
    },
    {
        "name": "test3_llm_app",
        "jobTitle": "大模型应用工程师",
        "company": "百度",
        "jobDescription": """
【岗位职责】
1. 负责大模型应用产品的研发，包括RAG知识库问答、Multi-Agent工作流、智能对话系统；
2. 设计并实现基于LangChain/LangChain4j的LLM应用架构，支持多轮对话、工具调用、记忆管理；
3. 负责向量检索系统的建设，使用Milvus等向量数据库实现高效语义检索；
4. 参与Prompt工程优化，提升LLM输出质量与稳定性；
5. 构建Agent协作工作流，实现任务拆解、多Agent通信、结果聚合；
6. 负责SSE流式输出、异步任务编排等技术实现；
7. 与产品、算法团队协作，推动大模型应用落地。

【任职要求】
1. 本科及以上学历，计算机相关专业；
2. 熟悉Java或Python，有后端开发经验；
3. 深入理解RAG架构（文档解析、分块、Embedding、向量检索、重排序）；
4. 熟悉LangChain、LangChain4j、LlamaIndex等LLM应用框架；
5. 熟悉Milvus、Pinecone、Weaviate等向量数据库；
6. 了解Agent架构（ReAct、Plan-and-Execute、Multi-Agent协作）；
7. 有大模型应用项目经验，有RAG/Agent相关开源贡献者优先；
8. 熟悉Prompt Engineering，了解Few-shot、Chain-of-Thought等技术。
""".strip(),
        "verifyFocus": ["RAG", "Agent", "LangChain", "Milvus", "项目经验", "匹配"],
    },
]


def login():
    r = requests.post(f"{BASE_URL}/auth/login", json=LOGIN_PAYLOAD, timeout=10)
    token = r.json().get("data", {}).get("accessToken")
    print(f"[LOGIN] {'成功' if token else '失败'}")
    return token


def start_career_workflow(token, jd_item):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {
        "jobDescription": jd_item["jobDescription"],
        "jobTitle": jd_item["jobTitle"],
        "company": jd_item["company"],
    }
    t0 = time.time()
    r = requests.post(f"{BASE_URL}/career/analyze-workflow", headers=headers, json=payload, timeout=30)
    elapsed = int((time.time() - t0) * 1000)
    data = r.json().get("data", {})
    wf_id = data.get("workflowId")
    print(f"  [START] workflowId={wf_id} elapsed={elapsed}ms")
    return wf_id, elapsed


def subscribe_sse(workflow_id, token, events, stop_event):
    url = f"{BASE_URL}/workflow/{workflow_id}/events?token={token}"
    try:
        resp = requests.get(url, stream=True, timeout=600)
        for line in resp.iter_lines(decode_unicode=True):
            if stop_event.is_set():
                break
            if not line:
                continue
            # SSE format: event: xxx \n data: {...}
            if line.startswith("event:"):
                ev_name = line[6:].strip()
            elif line.startswith("data:"):
                ev_data = line[5:].strip()
                try:
                    data = json.loads(ev_data) if ev_data else {}
                except Exception:
                    data = {"raw": ev_data}
                data["_event_name"] = ev_name
                events.append(data)
                msg = data.get("message", "")
                prog = data.get("progress", "")
                agent = data.get("agentType", "")
                task_type = data.get("taskType", "")
                print(f"    [SSE] {ev_name} | prog={prog}% | agent={agent} | task={task_type} | {msg}")
                if ev_name in ("workflow_completed", "workflow_failed"):
                    break
    except Exception as e:
        if not stop_event.is_set():
            print(f"    [SSE ERROR] {e}")


def fetch_report(token, workflow_id):
    headers = {"Authorization": f"Bearer {token}"}
    for attempt in range(10):
        time.sleep(2)
        try:
            r = requests.get(f"{BASE_URL}/career/reports/by-workflow/{workflow_id}",
                             headers=headers, timeout=10)
            j = r.json()
            if j.get("code") == 200 and j.get("data"):
                return j["data"]
        except Exception:
            pass
    return None


def validate_report(jd_item, report, events, start_elapsed):
    checks = {}
    report = report or {}

    # 1. workflow_completed 事件存在
    event_names = [e.get("_event_name") for e in events]
    checks["1_workflow_completed"] = any(n == "workflow_completed" for n in event_names)

    # 2. 收到 task_started 和 task_completed
    checks["2_task_events"] = (any(n == "task_started" for n in event_names)
                                and any(n == "task_completed" for n in event_names))

    # 3. workflow 状态为 SUCCESS 或 PARTIAL
    final_event = events[-1] if events else {}
    checks["3_workflow_success"] = final_event.get("status") in ("SUCCESS", "PARTIAL")

    # 4. 完成任务数 >= 3（至少 3 个任务成功）
    completed = final_event.get("completedTasks") or 0
    checks["4_tasks_completed_3plus"] = completed >= 3

    # 5. Report 持久化成功
    checks["5_report_persisted"] = report.get("id") is not None

    # 6. matchScore 存在且在合理范围 (0-100)
    score = report.get("matchScore")
    checks["6_matchscore_valid"] = score is not None and 0 <= score <= 100

    # 7. advantages 非空（JSON 数组）
    adv_raw = report.get("advantages", "[]")
    try:
        adv = json.loads(adv_raw) if isinstance(adv_raw, str) else adv_raw
    except Exception:
        adv = []
    checks["7_advantages_nonempty"] = len(adv) >= 1

    # 8. gaps 非空
    gaps_raw = report.get("gaps", "[]")
    try:
        gaps = json.loads(gaps_raw) if isinstance(gaps_raw, str) else gaps_raw
    except Exception:
        gaps = []
    checks["8_gaps_nonempty"] = len(gaps) >= 1

    # 9. resumeSuggestions 非空（简历优化建议）
    resume_sug = report.get("resumeSuggestions", "")
    checks["9_resume_suggestions"] = len(str(resume_sug)) > 50

    # 10. learningPlan 非空
    learning = report.get("learningPlan", "")
    checks["10_learning_plan"] = len(str(learning)) > 50

    # 11. interviewQuestions 非空
    interview = report.get("interviewQuestions", "")
    checks["11_interview_questions"] = len(str(interview)) > 50

    # 12. profileSufficient 字段存在
    checks["12_profile_sufficient_flag"] = report.get("profileSufficient") is not None

    # 13. 验证 JD 特定关注点（输出中包含关键词）
    all_text = json.dumps(report, ensure_ascii=False).lower()
    focus_keywords = jd_item["verifyFocus"]
    found = [kw for kw in focus_keywords if kw.lower() in all_text]
    checks["13_jd_focus_keywords"] = len(found) >= len(focus_keywords) // 2

    # 14. HTTP 立即返回 (<30s)
    checks["14_http_immediate"] = start_elapsed < 30000

    # 15. 个人 RAG 来源引用（检查是否包含用户真实经历关键词）
    real_keywords = ["测试用户", "focusos", "java", "spring", "rag", "milvus", "实习", "项目", "agent", "用友"]
    found_real = [kw for kw in real_keywords if kw.lower() in all_text]
    checks["15_real_profile_referenced"] = len(found_real) >= 2

    return checks, {
        "matchScore": score,
        "advantagesCount": len(adv),
        "gapsCount": len(gaps),
        "resumeSuggestionsLen": len(str(resume_sug)),
        "learningPlanLen": len(str(learning)),
        "interviewLen": len(str(interview)),
        "profileSufficient": report.get("profileSufficient"),
        "completedTasks": completed,
        "foundFocusKeywords": found,
        "foundRealKeywords": found_real,
        "totalDurationMs": final_event.get("durationMs", 0),
    }


def main():
    print("=" * 70)
    print("FocusOS AI Sprint 7-A QA 测试")
    print("Career Agent 2.0 — AI Career Assistant 闭环验收")
    print("=" * 70)
    os.makedirs(RESULT_DIR, exist_ok=True)

    token = login()
    if not token:
        print("[FATAL] 登录失败，无法继续测试"); return 1

    all_results = {}
    all_checks_summary = {}

    for i, jd_item in enumerate(JD_LIST, 1):
        print(f"\n{'='*70}")
        print(f"[测试 {i}] {jd_item['jobTitle']} @ {jd_item['company']}")
        print(f"{'='*70}")

        # 启动 Career Workflow
        wf_id, start_elapsed = start_career_workflow(token, jd_item)
        if not wf_id:
            print("  [FAIL] 无法启动 Career Workflow"); continue

        # SSE 订阅
        print(f"  [SSE] 订阅 workflow {wf_id} ...")
        events = []
        stop_event = threading.Event()
        t = threading.Thread(target=subscribe_sse, args=(wf_id, token, events, stop_event), daemon=True)
        t.start()
        t.join(timeout=300)
        stop_event.set()

        print(f"\n  [事件统计] 共 {len(events)} 个事件")

        # 获取 Report
        print(f"  [FETCH] 获取 Career Report ...")
        report = fetch_report(token, wf_id)
        if report:
            print(f"  [REPORT] id={report.get('id')} matchScore={report.get('matchScore')}")
        else:
            print(f"  [REPORT] 获取失败")

        # 验证
        checks, metrics = validate_report(jd_item, report, events, start_elapsed)
        all_checks_summary[jd_item["name"]] = checks
        all_results[jd_item["name"]] = {
            "jobTitle": jd_item["jobTitle"],
            "company": jd_item["company"],
            "workflowId": wf_id,
            "startElapsedMs": start_elapsed,
            "events": events,
            "report": report,
            "checks": checks,
            "metrics": metrics,
        }

        # 保存单个结果
        result_file = os.path.join(RESULT_DIR, f"{jd_item['name']}.json")
        with open(result_file, "w", encoding="utf-8") as f:
            json.dump(all_results[jd_item["name"]], f, ensure_ascii=False, indent=2)
        print(f"  [SAVE] {result_file}")

        # 打印验证结果
        print(f"\n  [验证结果] {jd_item['jobTitle']}")
        for name, passed in checks.items():
            mark = "[PASS]" if passed else "[FAIL]"
            print(f"    {mark} {name}")
        pass_count = sum(1 for v in checks.values() if v)
        print(f"\n    通过: {pass_count}/{len(checks)}")

    # ===== 总结 =====
    print("\n" + "=" * 70)
    print("[Sprint 7-A QA 测试总结]")
    print("=" * 70)

    total_pass = 0
    total_checks = 0
    for name, checks in all_checks_summary.items():
        pass_count = sum(1 for v in checks.values() if v)
        total = len(checks)
        total_pass += pass_count
        total_checks += total
        status = "PASS" if pass_count == total else ("PARTIAL" if pass_count >= total * 0.8 else "FAIL")
        print(f"  [{status}] {name}: {pass_count}/{total}")

    print(f"\n  总通过: {total_pass}/{total_checks}")
    overall = "PASS" if total_pass == total_checks else ("PARTIAL" if total_pass >= total_checks * 0.85 else "FAIL")
    print(f"  总体结论: {overall}")
    print("=" * 70)

    # 保存汇总
    summary_file = os.path.join(RESULT_DIR, "sprint7a_summary.json")
    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump({
            "overall": overall,
            "totalPass": total_pass,
            "totalChecks": total_checks,
            "perTest": {name: {"pass": sum(1 for v in c.values() if v), "total": len(c)}
                        for name, c in all_checks_summary.items()},
            "metrics": {name: all_results.get(name, {}).get("metrics", {}) for name in all_checks_summary},
        }, f, ensure_ascii=False, indent=2)

    return 0 if overall in ("PASS", "PARTIAL") else 1


if __name__ == "__main__":
    sys.exit(main())
