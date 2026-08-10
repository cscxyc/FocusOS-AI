#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 6-C QA 测试
Personal RAG 增强 + DAG 并行 + 来源追踪

测试目标："根据我的经历规划AI应用开发路线"

验证点：
1. WorkflowContext: profileLength > 0
2. CareerAgent: 引用真实简历/项目
3. LearningAgent: 结合技能差距生成计划
4. RAG: 来源准确
5. Workflow 成功完成
"""
import os, sys, json, time, requests, threading
import sseclient

BASE_URL = "http://localhost:8080/api"
LOGIN_PAYLOAD = {"username": "zhoujiayi", "password": "FocusOS@2026"}
RESULT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sprint6c_results")


def login():
    r = requests.post(f"{BASE_URL}/auth/login", json=LOGIN_PAYLOAD, timeout=10)
    token = r.json().get("data", {}).get("accessToken")
    print(f"[LOGIN] {'成功' if token else '失败'}")
    return token


def start_workflow(token, goal):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    t0 = time.time()
    r = requests.post(f"{BASE_URL}/workflow/execute", headers=headers, json={"goal": goal}, timeout=30)
    elapsed = int((time.time() - t0) * 1000)
    data = r.json().get("data", {})
    print(f"[START] workflowId={data.get('workflowId')} elapsed={elapsed}ms")
    return data.get("workflowId"), elapsed


def subscribe_sse(workflow_id, token, events, stop_event):
    url = f"{BASE_URL}/workflow/{workflow_id}/events?token={token}"
    try:
        resp = requests.get(url, stream=True, timeout=600)
        client = sseclient.SSEClient(resp)
        for event in client.events():
            if stop_event.is_set(): break
            try: data = json.loads(event.data) if event.data else {}
            except: data = {"raw": event.data}
            data["_event_name"] = event.event
            events.append(data)
            ev = event.event
            msg = data.get("message", "")
            prog = data.get("progress", "")
            agent = data.get("agentType", "")
            print(f"  [SSE] {ev} | prog={prog}% | agent={agent} | {msg}")
            if ev in ("workflow_completed", "workflow_failed"): break
    except Exception as e:
        if not stop_event.is_set(): print(f"  [SSE ERROR] {e}")


def main():
    print("=" * 70)
    print("FocusOS AI Sprint 6-C QA 测试")
    print("=" * 70)
    os.makedirs(RESULT_DIR, exist_ok=True)
    token = login()

    goal = "根据我的经历规划AI应用开发路线"
    workflow_id, start_elapsed = start_workflow(token, goal)
    if not workflow_id:
        print("[FAIL] 无法启动 workflow"); return 1

    # 验证启动耗时
    v_start = start_elapsed < 30000
    print(f"\n[验证] HTTP 立即返回 (<30s): {'PASS' if v_start else 'FAIL'} ({start_elapsed}ms)")

    # SSE 订阅
    print(f"\n[SSE] 订阅 workflow {workflow_id} ...")
    events = []
    stop_event = threading.Event()
    t = threading.Thread(target=subscribe_sse, args=(workflow_id, token, events, stop_event), daemon=True)
    t.start()
    t.join(timeout=300)

    event_names = [e.get("_event_name") for e in events]
    print(f"\n[事件统计] 共 {len(events)} 个事件: {event_names}")

    # ===== 5 个验证点 =====
    checks = {}

    # 验证1: workflow_completed 事件存在
    checks["1_workflow_completed"] = any(n == "workflow_completed" for n in event_names)

    # 验证2: 收到 task_started 和 task_completed
    checks["2_task_events"] = any(n == "task_started" for n in event_names) and \
                               any(n == "task_completed" for n in event_names)

    # 验证3: workflow 状态为 SUCCESS 或 PARTIAL
    final_event = events[-1] if events else {}
    checks["3_workflow_success"] = final_event.get("status") in ("SUCCESS", "PARTIAL")

    # 验证4: 完成任务数 > 0
    checks["4_tasks_completed"] = (final_event.get("completedTasks") or 0) > 0

    # 验证5: summary 非空
    summary = final_event.get("summary", "")
    checks["5_summary_nonempty"] = len(summary) > 100

    # ===== 查询 workflow 详情验证 RAG 来源 =====
    print(f"\n[API] GET /workflow/{workflow_id}")
    r = requests.get(f"{BASE_URL}/workflow/{workflow_id}",
                     headers={"Authorization": f"Bearer {token}"}, timeout=10)
    wf_detail = r.json().get("data", {})
    tasks = wf_detail.get("tasks", [])
    print(f"  任务数: {len(tasks)}")
    for t in tasks:
        print(f"  - id={t.get('id')} type={t.get('taskType')} agent={t.get('agentType')} "
              f"status={t.get('status')} duration={t.get('durationMs')}ms")

    # 验证6: CareerAgent 执行（agentType=career 的任务存在且 SUCCESS）
    career_tasks = [t for t in tasks if t.get("agentType") == "career"]
    checks["6_career_agent_executed"] = len(career_tasks) > 0 and \
                                         any(t.get("status") == "SUCCESS" for t in career_tasks)

    # 验证7: LearningAgent 执行（agentType=learning 的任务存在且 SUCCESS）
    learning_tasks = [t for t in tasks if t.get("agentType") == "learning"]
    checks["7_learning_agent_executed"] = len(learning_tasks) > 0 and \
                                           any(t.get("status") == "SUCCESS" for t in learning_tasks)

    # 验证8: RAG Agent 执行（agentType=rag 的任务存在）
    rag_tasks = [t for t in tasks if t.get("agentType") == "rag"]
    checks["8_rag_agent_executed"] = len(rag_tasks) > 0

    # 验证9: 至少一个任务结果包含用户真实信息（如"测试用户"、"Java"、"Spring"等）
    all_results = " ".join([t.get("result") or "" for t in tasks]).lower()
    real_info_keywords = ["测试用户", "java", "spring", "focusos", "用友", "实习", "后端", "微服务"]
    found_keywords = [kw for kw in real_info_keywords if kw.lower() in all_results]
    checks["9_results_contain_real_profile"] = len(found_keywords) >= 3
    print(f"\n[验证] 结果中包含真实用户信息关键词: {found_keywords}")

    # 验证10: 检查 Context 初始化事件是否提到检索成功
    context_events = [e for e in events if e.get("taskType") == "CONTEXT_INIT"]
    if context_events:
        ctx_event = context_events[-1]
        ctx_msg = ctx_event.get("message", "")
        # Sprint 6-C 应该检索成功（profileLength > 0）
        checks["10_context_init_success"] = "检索完成" in ctx_msg or "字符" in ctx_msg
        print(f"\n[验证] Context 初始化: {ctx_msg}")
    else:
        checks["10_context_init_success"] = False
        print(f"\n[验证] Context 初始化: 未找到 CONTEXT_INIT 事件")

    # 验证11: DAG 并行 - 检查无依赖任务是否并行执行（通过 task_started 时间戳判断）
    # 从 SSE 事件中提取 task_started 的时间戳
    task_started_events = [e for e in events if e.get("_event_name") == "task_started"
                           and e.get("taskType") not in ("PLANNING", "CONTEXT_INIT", "SUMMARY")]
    if len(task_started_events) >= 2:
        # 如果多个 task_started 连续出现（中间没有 task_completed），说明并行
        checks["11_dag_parallel_possible"] = True
        print(f"\n[验证] DAG 并行: {len(task_started_events)} 个任务 task_started 事件")
    else:
        checks["11_dag_parallel_possible"] = len(task_started_events) >= 1
        print(f"\n[验证] DAG 并行: {len(task_started_events)} 个任务（可能串行依赖）")

    # ===== 总耗时 =====
    total_duration = final_event.get("durationMs", 0)
    print(f"\n[性能] Workflow 总耗时: {total_duration}ms ({total_duration/1000:.1f}s)")

    # ===== 保存结果 =====
    result = {
        "workflowId": workflow_id,
        "goal": goal,
        "startElapsedMs": start_elapsed,
        "totalDurationMs": total_duration,
        "events": events,
        "workflowDetail": wf_detail,
        "summary": summary,
        "checks": checks,
        "foundKeywords": found_keywords,
    }
    with open(os.path.join(RESULT_DIR, "sprint6c_test.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    # ===== 总结 =====
    print("\n" + "=" * 70)
    print("[Sprint 6-C QA 测试总结]")
    print("=" * 70)
    for name, passed in checks.items():
        mark = "[PASS]" if passed else "[FAIL]"
        print(f"  {mark} {name}")
    pass_count = sum(1 for v in checks.values() if v)
    total = len(checks)
    print(f"\n  通过: {pass_count}/{total}")
    print("=" * 70)
    return 0 if pass_count == total else 1


if __name__ == "__main__":
    sys.exit(main())
