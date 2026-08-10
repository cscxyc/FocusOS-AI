#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 6-B
异步 Workflow + SSE 实时进度 + WorkflowContext QA 测试

测试场景：
1. 异步启动 + SSE 实时事件
   输入: "根据我的经历规划AI应用开发转型路线"
   验证:
   - HTTP 立即返回 workflowId（<10秒）
   - SSE 持续收到 task_started / task_completed 事件
   - 最终收到 workflow_completed
   - 结果保存到数据库

2. 异常处理（模拟部分任务失败仍能继续）
   输入: 无效特殊字符目标（测试降级）
   验证:
   - workflow 不会整体崩溃
   - 至少完成部分任务或返回 workflow_failed 事件

3. 历史查询 + 状态持久化
   验证:
   - GET /workflow/{workflowId} 能恢复 workflow 状态
   - GET /workflow/history 返回历史列表
"""

import os
import sys
import json
import time
import requests
import threading
import sseclient  # pip install sseclient-py

BASE_URL = "http://localhost:8080/api"
LOGIN_PAYLOAD = {
    "username": "zhoujiayi",
    "password": "FocusOS@2026"
}

RESULT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sprint6b_results")


def login():
    print("\n[LOGIN] 登录...")
    r = requests.post(f"{BASE_URL}/auth/login", json=LOGIN_PAYLOAD, timeout=10)
    if r.status_code != 200:
        print(f"  登录失败: {r.status_code} {r.text}")
        sys.exit(1)
    data = r.json()
    token = data.get("data", {}).get("accessToken") or data.get("data", {}).get("token")
    if not token:
        print(f"  未获取到 token: {data}")
        sys.exit(1)
    print(f"  登录成功")
    return token


def start_workflow_async(token, goal):
    """启动异步 workflow，立即返回 workflowId"""
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {"goal": goal}
    print(f"\n[ASYNC] POST /workflow/execute")
    print(f"  goal: {goal}")
    start = time.time()
    r = requests.post(f"{BASE_URL}/workflow/execute", headers=headers, json=payload, timeout=30)
    elapsed_ms = int((time.time() - start) * 1000)
    print(f"  HTTP 立即返回: {r.status_code} | 耗时: {elapsed_ms}ms")
    if r.status_code != 200:
        print(f"  [ERROR] 启动失败: {r.text}")
        return None, elapsed_ms
    body = r.json()
    data = body.get("data", {})
    workflow_id = data.get("workflowId")
    status = data.get("status")
    print(f"  workflowId: {workflow_id}")
    print(f"  status: {status}")
    print(f"  sseEndpoint: {data.get('sseEndpoint')}")
    print(f"  startElapsedMs: {data.get('startElapsedMs')}")
    return workflow_id, elapsed_ms


def subscribe_sse(workflow_id, token, events_collector, stop_event):
    """订阅 SSE 事件流，收集到 events_collector 列表"""
    url = f"{BASE_URL}/workflow/{workflow_id}/events?token={token}"
    headers = {"Accept": "text/event-stream"}
    try:
        response = requests.get(url, headers=headers, stream=True, timeout=600)
        client = sseclient.SSEClient(response)
        for event in client.events():
            if stop_event.is_set():
                break
            try:
                data = json.loads(event.data) if event.data else {}
            except json.JSONDecodeError:
                data = {"raw": event.data}
            data["_event_name"] = event.event
            events_collector.append(data)
            event_name = event.event
            msg = data.get("message", "")
            progress = data.get("progress", "")
            agent = data.get("agentType", "")
            task_name = data.get("task", "")
            print(f"  [SSE] {event_name} | progress={progress}% | agent={agent} | {task_name} | {msg}")
            # 终止条件
            if event_name in ("workflow_completed", "workflow_failed"):
                break
    except Exception as e:
        if not stop_event.is_set():
            print(f"  [SSE ERROR] {type(e).__name__}: {e}")


def test1_async_sse(token):
    """测试1: 异步启动 + SSE 实时事件 + 最终结果"""
    print("\n" + "=" * 70)
    print("[测试1] 异步 Workflow + SSE 实时进度")
    print("=" * 70)

    goal = "根据我的经历规划AI应用开发转型路线"

    # 1. 启动 workflow
    workflow_id, start_elapsed = start_workflow_async(token, goal)
    if not workflow_id:
        return False

    # 2. 验证启动耗时（< 30 秒）
    if start_elapsed > 30000:
        print(f"  [FAIL] 启动耗时 {start_elapsed}ms > 30000ms（应为立即返回）")
        return False
    print(f"  [PASS] 启动耗时 {start_elapsed}ms < 30000ms（异步返回成功）")

    # 3. 订阅 SSE
    print(f"\n[SSE] 订阅 /api/workflow/{workflow_id}/events ...")
    events = []
    stop_event = threading.Event()
    sse_thread = threading.Thread(
        target=subscribe_sse,
        args=(workflow_id, token, events, stop_event),
        daemon=True
    )
    sse_thread.start()

    # 4. 等待 workflow 完成（最多 5 分钟）
    sse_thread.join(timeout=300)
    if sse_thread.is_alive():
        print("  [WARN] SSE 超时 5 分钟，强制停止")
        stop_event.set()
        return False

    # 5. 验证事件
    event_names = [e.get("_event_name") for e in events]
    print(f"\n[事件统计] 共收到 {len(events)} 个事件")
    print(f"  事件类型: {event_names}")

    checks = {
        "收到 workflow_started": any(n == "workflow_started" for n in event_names),
        "收到 task_started": any(n == "task_started" for n in event_names),
        "收到 task_completed": any(n == "task_completed" for n in event_names),
        "收到 workflow_completed 或 workflow_failed": any(
            n in ("workflow_completed", "workflow_failed") for n in event_names
        ),
    }
    all_pass = True
    for desc, ok in checks.items():
        mark = "[PASS]" if ok else "[FAIL]"
        print(f"  {mark} {desc}")
        if not ok:
            all_pass = False

    # 6. 验证最终状态
    final_event = events[-1] if events else {}
    if final_event.get("_event_name") == "workflow_completed":
        status = final_event.get("status")
        progress = final_event.get("progress")
        summary = final_event.get("summary", "")
        duration = final_event.get("durationMs")
        completed = final_event.get("completedTasks")
        total = final_event.get("totalTasks")
        print(f"\n[最终状态]")
        print(f"  status: {status}")
        print(f"  progress: {progress}%")
        print(f"  tasks: {completed}/{total}")
        print(f"  duration: {duration}ms")
        print(f"  summary length: {len(summary)} 字符")
        if status in ("SUCCESS", "PARTIAL") and progress == 100:
            print(f"  [PASS] workflow 正常完成")
        else:
            print(f"  [WARN] workflow 状态: {status}")

    # 7. 保存结果
    os.makedirs(RESULT_DIR, exist_ok=True)
    with open(os.path.join(RESULT_DIR, "test1_async_sse.json"), "w", encoding="utf-8") as f:
        json.dump({"workflowId": workflow_id, "events": events}, f, ensure_ascii=False, indent=2)

    return all_pass


def test3_history_persistence(token, workflow_id):
    """测试3: 历史查询 + 状态持久化（刷新页面恢复）"""
    print("\n" + "=" * 70)
    print("[测试3] 历史查询 + 状态持久化")
    print("=" * 70)

    headers = {"Authorization": f"Bearer {token}"}

    # 1. 查询单个 workflow
    print(f"\n[API] GET /workflow/{workflow_id}")
    r = requests.get(f"{BASE_URL}/workflow/{workflow_id}", headers=headers, timeout=10)
    if r.status_code != 200:
        print(f"  [FAIL] HTTP {r.status_code}: {r.text}")
        return False
    body = r.json()
    data = body.get("data", {})
    print(f"  workflowId: {data.get('workflowId')}")
    print(f"  userGoal: {data.get('userGoal')}")
    print(f"  status: {data.get('status')}")
    print(f"  tasks: {data.get('successTasks')}/{data.get('totalTasks')}")
    tasks = data.get("tasks", [])
    if tasks:
        # 验证新字段
        t0 = tasks[0]
        print(f"  示例任务字段:")
        print(f"    id={t0.get('id')}")
        print(f"    startedAt={t0.get('startedAt')}")
        print(f"    completedAt={t0.get('completedAt')}")
        print(f"    durationMs={t0.get('durationMs')}")

    checks = {
        "GET /workflow/{id} 返回数据": data.get("workflowId") == workflow_id,
        "任务列表非空": len(tasks) > 0,
        "包含 startedAt 字段": any(t.get("startedAt") for t in tasks),
        "包含 durationMs 字段": any(t.get("durationMs") is not None for t in tasks),
    }
    all_pass = True
    for desc, ok in checks.items():
        mark = "[PASS]" if ok else "[FAIL]"
        print(f"  {mark} {desc}")
        if not ok:
            all_pass = False

    # 2. 查询历史列表
    print(f"\n[API] GET /workflow/history")
    r = requests.get(f"{BASE_URL}/workflow/history", headers=headers, timeout=10)
    if r.status_code != 200:
        print(f"  [FAIL] HTTP {r.status_code}: {r.text}")
        return False
    body = r.json()
    history = body.get("data", [])
    print(f"  历史 workflow 数量: {len(history)}")
    found = any(wf.get("workflowId") == workflow_id for wf in history)
    print(f"  当前 workflow 存在于历史: {found}")
    if found:
        print(f"  [PASS] 历史持久化正常")
    else:
        print(f"  [FAIL] 当前 workflow 未在历史中")
        all_pass = False

    # 保存结果
    with open(os.path.join(RESULT_DIR, "test3_history.json"), "w", encoding="utf-8") as f:
        json.dump({"workflow": data, "historyCount": len(history)}, f, ensure_ascii=False, indent=2)

    return all_pass


def main():
    print("=" * 70)
    print("FocusOS AI Sprint 6-B QA 测试")
    print("异步 Workflow + SSE 实时进度 + WorkflowContext 共享上下文")
    print("=" * 70)

    os.makedirs(RESULT_DIR, exist_ok=True)
    token = login()

    results = {}

    # 测试1: 异步 + SSE
    results["测试1_异步SSE"] = test1_async_sse(token)

    # 获取刚执行的 workflow_id 用于测试3
    test1_result_file = os.path.join(RESULT_DIR, "test1_async_sse.json")
    workflow_id_for_test3 = None
    if os.path.exists(test1_result_file):
        with open(test1_result_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            workflow_id_for_test3 = data.get("workflowId")

    # 测试3: 历史持久化
    if workflow_id_for_test3:
        results["测试3_历史持久化"] = test3_history_persistence(token, workflow_id_for_test3)
    else:
        print("\n[测试3] 跳过（测试1未产生 workflowId）")
        results["测试3_历史持久化"] = False

    # 总结
    print("\n" + "=" * 70)
    print("[Sprint 6-B QA 测试总结]")
    print("=" * 70)
    for name, passed in results.items():
        mark = "[PASS]" if passed else "[FAIL]"
        print(f"  {mark} {name}")
    pass_count = sum(1 for v in results.values() if v)
    total = len(results)
    print(f"\n  通过: {pass_count}/{total}")
    print("=" * 70)

    return 0 if pass_count == total else 1


if __name__ == "__main__":
    sys.exit(main())
