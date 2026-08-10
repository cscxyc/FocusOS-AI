#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 6-A
Multi-Agent Workflow QA 测试

测试场景：
1. "根据我的经历，帮我规划AI应用开发转型路线"
   验证：自动调用 CareerAgent、LearningAgent、Personal RAG
2. "我想准备秋招"
   验证：生成 岗位分析、学习计划、任务列表
"""

import os
import sys
import json
import time
import requests

BASE_URL = "http://localhost:8080/api"
LOGIN_PAYLOAD = {
    "username": "zhoujiayi",
    "password": "FocusOS@2026"
}

# 测试结果保存目录
RESULT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sprint6a_results")


def _extract_token(data):
    if not isinstance(data, dict):
        return None
    inner = data.get("data", data)
    if not isinstance(inner, dict):
        return None
    return inner.get("accessToken") or inner.get("token")


def login():
    print("\n[LOGIN] 登录...")
    r = requests.post(f"{BASE_URL}/auth/login", json=LOGIN_PAYLOAD, timeout=10)
    if r.status_code != 200:
        print(f"  登录失败: {r.status_code} {r.text}")
        sys.exit(1)
    token = _extract_token(r.json())
    if not token:
        print(f"  未获取到 token: {r.json()}")
        sys.exit(1)
    print(f"  登录成功")
    return token


def execute_workflow(token, goal, scenario_name):
    """执行 Multi-Agent Workflow"""
    print("\n" + "#" * 70)
    print(f"# 场景: {scenario_name}")
    print(f"# 目标: {goal}")
    print("#" * 70)

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {"goal": goal}

    print(f"\n[API] POST /workflow/execute")
    print(f"  请求体: {json.dumps(payload, ensure_ascii=False)}")
    print(f"  正在执行（Multi-Agent 协作可能需要 1-3 分钟）...")

    start_time = time.time()
    try:
        r = requests.post(f"{BASE_URL}/workflow/execute",
                          headers=headers,
                          json=payload,
                          timeout=300)
    except requests.exceptions.Timeout:
        print(f"  [ERROR] 请求超时（5分钟）")
        return None
    elapsed = time.time() - start_time

    print(f"  耗时: {elapsed:.1f}s | HTTP状态: {r.status_code}")

    if r.status_code != 200:
        print(f"  请求失败: {r.text[:500]}")
        return None

    resp = r.json()
    data = resp.get("data", resp)
    return data


def verify_workflow_result(result, scenario_name):
    """验证工作流执行结果"""
    if not result:
        print("\n[验证] 无结果可验证")
        return False

    print("\n" + "=" * 70)
    print(f"[工作流结果概览]")
    print("=" * 70)
    print(f"  WorkflowId : {result.get('workflowId')}")
    print(f"  用户目标   : {result.get('userGoal')}")
    print(f"  状态       : {result.get('status')}")
    print(f"  总任务数   : {result.get('totalTasks')}")
    print(f"  成功数     : {result.get('successTasks')}")
    print(f"  失败数     : {result.get('failedTasks')}")

    tasks = result.get("tasks", [])
    print(f"\n[任务明细] 共 {len(tasks)} 个任务")
    print("-" * 70)
    for i, task in enumerate(tasks):
        print(f"\n  任务 #{i + 1}")
        print(f"    ID         : {task.get('id')}")
        print(f"    Goal       : {task.get('goal')}")
        print(f"    TaskType   : {task.get('taskType')}")
        print(f"    AgentType  : {task.get('agentType')}")
        print(f"    Status     : {task.get('status')}")
        print(f"    DependsOn  : {task.get('dependsOn')}")
        print(f"    InputParams: {task.get('inputParams')}")
        if task.get('errorMessage'):
            print(f"    Error      : {task.get('errorMessage')[:200]}")
        if task.get('result'):
            preview = task.get('result')
            if len(preview) > 800:
                preview = preview[:800] + " ... [截断]"
            print(f"    Result     :")
            for line in preview.split("\n"):
                print(f"      {line}")

    summary = result.get("summary", "")
    if summary:
        print(f"\n[MasterAgent 汇总总结]")
        print("-" * 70)
        print(summary)

    # 场景化验证
    print("\n" + "=" * 70)
    print(f"[场景验证] {scenario_name}")
    print("=" * 70)

    agent_types = [t.get('agentType', '') for t in tasks]
    task_types = [t.get('taskType', '') for t in tasks]
    statuses = [t.get('status', '') for t in tasks]
    success_count = sum(1 for s in statuses if s == 'SUCCESS')

    checks = {
        "工作流状态非 FAILED": result.get('status') != 'FAILED',
        "至少2个任务": len(tasks) >= 2,
        "至少1个任务成功": success_count >= 1,
        "调用 CareerAgent": 'career' in agent_types,
        "调用 LearningAgent": 'learning' in agent_types,
        "调用 RAGAgent": 'rag' in agent_types,
        "包含任务类型 CAREER_ANALYSIS": 'CAREER_ANALYSIS' in task_types,
        "包含任务类型 LEARNING_PLAN": 'LEARNING_PLAN' in task_types,
        "MasterAgent生成汇总总结": bool(summary and len(summary) > 50),
    }

    all_passed = True
    for label, ok in checks.items():
        mark = "[OK]  " if ok else "[MISS]"
        if not ok:
            all_passed = False
        print(f"  {mark} {label}")

    # 场景1特别检查
    if "转型" in scenario_name or "AI应用开发" in scenario_name:
        specific_checks = {
            "结合个人经历(简历/项目)": any(k in (summary or '') + str(tasks)
                                        for k in ["FocusOS", "RAG", "Milvus", "测试用户", "用友", "简历", "Spring", "Java"]),
            "提到岗位分析": any(k in (summary or '') + str(tasks)
                               for k in ["岗位", "AI应用开发", "匹配度", "要求"]),
            "提到学习路线": any(k in (summary or '') + str(tasks)
                               for k in ["学习", "路线", "计划", "Agent", "RAG", "LangChain"]),
        }
        print("\n  [场景1专项检查]")
        for label, ok in specific_checks.items():
            mark = "[OK]  " if ok else "[MISS]"
            if not ok:
                all_passed = False
            print(f"    {mark} {label}")

    # 场景2特别检查
    if "秋招" in scenario_name:
        specific_checks = {
            "生成岗位分析": any(k in (summary or '') + str(tasks)
                               for k in ["岗位", "秋招", "校招", "JD", "要求"]),
            "生成学习计划": any(k in (summary or '') + str(tasks)
                               for k in ["学习", "计划", "路线", "复习"]),
            "生成任务列表": any(k in (summary or '') + str(tasks)
                               for k in ["任务", "每日", "daily", "task", "DAILY_TASK", "行动"]),
        }
        print("\n  [场景2专项检查]")
        for label, ok in specific_checks.items():
            mark = "[OK]  " if ok else "[MISS]"
            if not ok:
                all_passed = False
            print(f"    {mark} {label}")

    print("\n" + "=" * 70)
    print(f"  验证结论: {'全部通过' if all_passed else '存在未通过项'}")
    print("=" * 70)

    return all_passed


def test_dashboard_ai_plan(token):
    """测试 Dashboard AI 计划接口"""
    print("\n" + "#" * 70)
    print("# 测试 Dashboard AI 计划 (/dashboard/ai-plan)")
    print("#" * 70)

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    print("\n[API] GET /dashboard/ai-plan")
    try:
        r = requests.get(f"{BASE_URL}/dashboard/ai-plan", headers=headers, timeout=120)
    except requests.exceptions.Timeout:
        print("  [ERROR] 请求超时")
        return False

    print(f"  HTTP状态: {r.status_code}")
    if r.status_code != 200:
        print(f"  请求失败: {r.text[:500]}")
        return False

    resp = r.json()
    data = resp.get("data", resp)

    print("\n[AI 计划面板内容]")
    print("-" * 70)
    print(f"  今日AI建议: {(data.get('dailyAdvice') or '')[:200]}...")
    print(f"  本周目标  : {data.get('weeklyGoal', '无')}")
    print(f"  学习任务数: {len(data.get('learningTasks', []))}")
    print(f"  职业进度  : {json.dumps(data.get('careerProgress', {}), ensure_ascii=False)}")

    ai_stats = data.get('aiTaskStats', {})
    print(f"  AI工作流统计:")
    print(f"    总工作流数: {ai_stats.get('totalWorkflows', 0)}")
    print(f"    总任务数  : {ai_stats.get('totalTasks', 0)}")
    print(f"    成功任务数: {ai_stats.get('successTasks', 0)}")
    recent = ai_stats.get('recentTasks', [])
    print(f"    最近任务数: {len(recent)}")

    # 验证字段
    checks = {
        "dailyAdvice 存在": 'dailyAdvice' in data,
        "learningTasks 存在": 'learningTasks' in data,
        "careerProgress 存在": 'careerProgress' in data,
        "aiTaskStats 存在": 'aiTaskStats' in data,
    }
    print("\n[验证] Dashboard AI 计划字段:")
    all_passed = True
    for label, ok in checks.items():
        mark = "[OK]  " if ok else "[MISS]"
        if not ok:
            all_passed = False
        print(f"  {mark} {label}")

    return all_passed


def save_result(scenario_name, result):
    """保存测试结果到文件"""
    os.makedirs(RESULT_DIR, exist_ok=True)
    safe_name = scenario_name.replace(" ", "_").replace("/", "_")
    filename = os.path.join(RESULT_DIR, f"{safe_name}.json")
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n[保存] 结果已保存到: {filename}")


def main():
    print("=" * 70)
    print("FocusOS AI Sprint 6-A")
    print("Multi-Agent Workflow QA 测试")
    print("=" * 70)

    token = login()

    overall_results = {}

    # 测试1: AI应用开发转型路线
    goal1 = "根据我的经历，帮我规划AI应用开发转型路线"
    result1 = execute_workflow(token, goal1, "AI应用开发转型路线")
    if result1:
        save_result("AI应用开发转型路线", result1)
        overall_results["测试1_AI应用开发转型路线"] = verify_workflow_result(result1, "AI应用开发转型路线")
    else:
        overall_results["测试1_AI应用开发转型路线"] = False

    # 测试2: 准备秋招
    goal2 = "我想准备秋招"
    result2 = execute_workflow(token, goal2, "秋招准备")
    if result2:
        save_result("秋招准备", result2)
        overall_results["测试2_秋招准备"] = verify_workflow_result(result2, "秋招准备")
    else:
        overall_results["测试2_秋招准备"] = False

    # 测试3: Dashboard AI 计划
    overall_results["测试3_Dashboard_AI计划"] = test_dashboard_ai_plan(token)

    # 总结
    print("\n" + "=" * 70)
    print("[Sprint 6-A QA 测试总结]")
    print("=" * 70)
    for name, passed in overall_results.items():
        mark = "[PASS]" if passed else "[FAIL]"
        print(f"  {mark} {name}")

    pass_count = sum(1 for v in overall_results.values() if v)
    total = len(overall_results)
    print(f"\n  通过: {pass_count}/{total}")
    print("=" * 70)

    return 0 if pass_count == total else 1


if __name__ == "__main__":
    sys.exit(main())
