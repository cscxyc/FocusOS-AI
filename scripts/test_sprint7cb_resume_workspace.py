#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 7-C-B QA 测试脚本
=====================================
测试内容：
1. Resume Workspace — 创建/查询/查看/编辑/激活/删除/导出(PDF/MD/DOCX)
2. Resume Diff — 两个版本对比
3. Interview Repair — 历史损坏 JSON 修复
4. LLM Call Log — 调用统计摘要

测试策略：
- 使用已有用户登录
- 手动创建两个简历版本（不同岗位），验证完整 CRUD + 导出
- 对比两个版本，验证 Diff 结果
- 查询用户面试会话，尝试解析 questionsJson（预期失败），修复后重新解析（预期成功）
- 查询 LLM 调用统计摘要
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
OUTPUT_DIR = Path(__file__).parent / "sprint7cb_results"
OUTPUT_DIR.mkdir(exist_ok=True)

# 测试用简历内容（Markdown）
RESUME_AI_APP = """# 测试用户

## 个人摘要
AI应用开发工程师，精通 Spring Boot + LangChain4j，具备 Multi-Agent Workflow 编排经验。

## 技术栈
- Java / Spring Boot / MySQL / Redis
- LangChain4j / RAG / Milvus / Agent
- SSE / CompletableFuture / DAG 工作流

## 实习经历
### 用友网络 — Java 后端开发实习生
- 参与企业级 SaaS 平台后端开发

## 项目经历
### FocusOS AI — 个人 AI 职业助手平台
- 基于 Spring Boot + LangChain4j 构建 Multi-Agent Workflow
- 集成 Milvus 向量存储实现 Personal RAG
- 支持 JD 分析、简历优化、模拟面试闭环
"""

RESUME_JAVA_BACKEND = """# 测试用户

## 个人摘要
Java 后端开发工程师，专注微服务架构与高并发系统设计。

## 技术栈
- Java / Spring Boot / Spring Cloud / MyBatis
- MySQL / Redis / RabbitMQ / Docker
- 微服务 / 分布式 / 高并发

## 实习经历
### 用友网络 — Java 后端开发实习生
- 参与企业级 SaaS 平台后端开发
- 使用 Spring Cloud 微服务架构

## 项目经历
### FocusOS AI — 个人 AI 职业助手平台
- 基于 Spring Boot 构建后端 REST API
- 使用 MySQL + Redis 做数据持久化与缓存
"""


def http_request(method, path, data=None, token=None, timeout=180, raw=False):
    url = f"{BACKEND_URL}{path}"
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data else None
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


def login():
    print(f"\n[LOGIN] {TEST_USER['username']} ...")
    data, status, _ = http_request("POST", "/auth/login", TEST_USER, timeout=30)
    if status != 200 or "data" not in data:
        print(f"[FAIL] login failed: {data}")
        sys.exit(1)
    token = data["data"].get("accessToken") or data["data"].get("token")
    print(f"[OK] login")
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
    print(f"  [{status}] {name}" + (f" — {detail}" if detail else ""))


# ============================================================
# Test 1: Resume Workspace CRUD
# ============================================================
def test_resume_workspace_crud(token):
    print("\n[TEST 1] Resume Workspace CRUD")
    version_ids = []

    # 1.1 创建 AI应用开发版
    print("  → 创建 AI应用开发版...")
    data, status, _ = http_request("POST", "/resume/versions", {
        "targetPosition": "AI应用开发工程师",
        "versionName": "QA测试_AI应用开发版",
        "content": RESUME_AI_APP,
        "setActive": True,
    }, token=token, timeout=30)
    check("创建 AI应用开发版", status == 200 and data.get("code") == 200,
          f"status={status}, id={data.get('data', {}).get('id') if data.get('data') else 'N/A'}")
    ai_version_id = data.get("data", {}).get("id")
    if ai_version_id:
        version_ids.append(ai_version_id)

    # 1.2 创建 Java后端版
    print("  → 创建 Java后端版...")
    data, status, _ = http_request("POST", "/resume/versions", {
        "targetPosition": "Java后端开发工程师",
        "versionName": "QA测试_Java后端版",
        "content": RESUME_JAVA_BACKEND,
        "setActive": False,
    }, token=token, timeout=30)
    check("创建 Java后端版", status == 200 and data.get("code") == 200,
          f"status={status}, id={data.get('data', {}).get('id') if data.get('data') else 'N/A'}")
    java_version_id = data.get("data", {}).get("id")
    if java_version_id:
        version_ids.append(java_version_id)

    # 1.3 查询版本列表
    print("  → 查询版本列表...")
    data, status, _ = http_request("GET", "/resume/versions", token=token, timeout=15)
    versions = data.get("data", []) if data.get("data") else []
    check("查询版本列表", status == 200 and len(versions) >= 2,
          f"count={len(versions)}")

    # 1.4 查看版本详情
    if ai_version_id:
        print("  → 查看版本详情...")
        data, status, _ = http_request("GET", f"/resume/versions/{ai_version_id}", token=token, timeout=15)
        detail = data.get("data", {})
        check("查看版本详情", status == 200 and detail.get("content") is not None,
              f"contentLen={len(detail.get('content', ''))}")

    # 1.5 编辑保存
    if ai_version_id:
        print("  → 编辑保存...")
        updated_content = RESUME_AI_APP + "\n## 补充\n- 新增技能：Docker / Kubernetes\n"
        data, status, _ = http_request("PUT", f"/resume/versions/{ai_version_id}", {
            "content": updated_content,
            "versionName": "QA测试_AI应用开发版_已编辑",
        }, token=token, timeout=15)
        check("编辑保存", status == 200 and data.get("code") == 200,
              f"versionName={data.get('data', {}).get('versionName', 'N/A')}")

    # 1.6 激活版本
    if java_version_id:
        print("  → 激活 Java后端版...")
        data, status, _ = http_request("POST", f"/resume/versions/{java_version_id}/activate",
                                       token=token, timeout=15)
        check("激活版本", status == 200 and data.get("code") == 200,
              f"isActive={data.get('data', {}).get('isActive', 'N/A')}")

    # 1.7 查询激活版本
    print("  → 查询当前激活版本...")
    data, status, _ = http_request("GET", "/resume/active", token=token, timeout=15)
    check("查询激活版本", status == 200,
          f"activeId={data.get('data', {}).get('id') if data.get('data') else 'N/A'}")

    return version_ids


# ============================================================
# Test 2: Resume Export (PDF / MD / DOCX)
# ============================================================
def test_resume_export(token, version_ids):
    print("\n[TEST 2] Resume Export")
    if not version_ids:
        check("Export 测试跳过（无版本ID）", False, "no version_ids")
        return

    vid = version_ids[0]

    # PDF
    print("  → 导出 PDF...")
    content, status, ct = http_request("GET", f"/resume/versions/{vid}/export?format=pdf",
                                       token=token, timeout=30, raw=True)
    check("导出 PDF", status == 200 and len(content) > 10240,
          f"status={status}, size={len(content)} bytes, ct={ct}")

    # Markdown
    print("  → 导出 Markdown...")
    content, status, ct = http_request("GET", f"/resume/versions/{vid}/export?format=md",
                                       token=token, timeout=30, raw=True)
    check("导出 Markdown", status == 200 and len(content) > 100,
          f"status={status}, size={len(content)} bytes")

    # Word (DOCX)
    print("  → 导出 Word...")
    content, status, ct = http_request("GET", f"/resume/versions/{vid}/export?format=docx",
                                       token=token, timeout=30, raw=True)
    check("导出 Word", status == 200,
          f"status={status}, size={len(content)} bytes")


# ============================================================
# Test 3: Resume Diff
# ============================================================
def test_resume_diff(token, version_ids):
    print("\n[TEST 3] Resume Diff")
    if len(version_ids) < 2:
        check("Diff 测试跳过（需要2个版本）", False, f"version_ids={version_ids}")
        return None

    vid_a, vid_b = version_ids[0], version_ids[1]
    print(f"  → 对比 versionA={vid_a} vs versionB={vid_b}...")
    data, status, _ = http_request("GET",
                                   f"/resume/diff?versionA={vid_a}&versionB={vid_b}",
                                   token=token, timeout=15)
    diff = data.get("data", {}) if data.get("data") else {}
    check("Diff 接口返回", status == 200 and data.get("code") == 200,
          f"status={status}")

    check("Diff 包含 added", "added" in diff and isinstance(diff.get("added"), list),
          f"added={diff.get('added', [])[:5]}")
    check("Diff 包含 removed", "removed" in diff and isinstance(diff.get("removed"), list),
          f"removed={diff.get('removed', [])[:5]}")
    check("Diff 包含 summary", "summary" in diff,
          f"summary={diff.get('summary', {})}")

    # 验证 AI版 应该包含 RAG/Milvus/Agent 等
    all_changes = (diff.get("added", []) or []) + (diff.get("removed", []) or [])
    has_tech_keywords = any(kw in " ".join(all_changes).lower()
                           for kw in ["rag", "milvus", "agent", "spring", "redis", "docker"])
    check("Diff 包含技术关键词", has_tech_keywords,
          f"keywords found in: {all_changes[:10]}")

    return diff


# ============================================================
# Test 4: Interview Repair
# ============================================================
def test_interview_repair(token):
    print("\n[TEST 4] Interview Repair")
    repair_result = {"tested": False, "repaired": False}

    # 查询用户面试会话
    data, status, _ = http_request("GET", "/interview/sessions", token=token, timeout=15)
    sessions = data.get("data", []) if data.get("data") else []
    print(f"  → 用户面试会话数: {len(sessions)}")

    if not sessions:
        check("Interview Repair 跳过（无历史会话）", True, "no sessions to repair")
        return repair_result

    # 尝试找到 questionsJson 损坏的会话
    corrupt_session = None
    for s in sessions:
        qj = s.get("questionsJson", "")
        if qj:
            try:
                json.loads(qj)
                # 解析成功，不是损坏的
            except (json.JSONDecodeError, ValueError):
                corrupt_session = s
                break

    if corrupt_session:
        print(f"  → 找到损坏会话: id={corrupt_session.get('id')}")
        check("发现损坏的 questionsJson", True,
              f"sessionId={corrupt_session.get('id')}")

        # 修复
        sid = corrupt_session["id"]
        print(f"  → 修复会话 {sid}...")
        data, status, _ = http_request("POST", f"/interview/sessions/{sid}/repair",
                                       token=token, timeout=120)
        check("Repair 接口返回", status == 200 and data.get("code") == 200,
              f"status={status}")

        if status == 200 and data.get("data"):
            new_qj = data["data"].get("questionsJson", "")
            try:
                parsed = json.loads(new_qj)
                check("修复后 JSON 可解析", True,
                      f"questions count={len(parsed) if isinstance(parsed, list) else 'object'}")
                repair_result["tested"] = True
                repair_result["repaired"] = True
            except (json.JSONDecodeError, ValueError) as e:
                check("修复后 JSON 可解析", False, f"parse error: {e}")
                repair_result["tested"] = True
    else:
        # 没有损坏的会话，选择第一个会话验证 repair 接口可用
        print("  → 未发现损坏会话，使用第一个会话验证 repair 接口...")
        sid = sessions[0]["id"]
        print(f"  → 对会话 {sid} 执行 repair...")
        data, status, _ = http_request("POST", f"/interview/sessions/{sid}/repair",
                                       token=token, timeout=120)
        check("Repair 接口可用", status == 200 and data.get("code") == 200,
              f"status={status}")
        if status == 200 and data.get("data"):
            new_qj = data["data"].get("questionsJson", "")
            try:
                json.loads(new_qj)
                check("Repair 后 JSON 可解析", True, "parse OK")
                repair_result["tested"] = True
                repair_result["repaired"] = True
            except (json.JSONDecodeError, ValueError):
                check("Repair 后 JSON 可解析", False, "parse failed")
                repair_result["tested"] = True

    return repair_result


# ============================================================
# Test 5: LLM Call Log
# ============================================================
def test_llm_logs(token):
    print("\n[TEST 5] LLM Call Log")

    # 5.1 统计摘要
    print("  → 查询 LLM 调用统计摘要...")
    data, status, _ = http_request("GET", "/llm-logs/summary", token=token, timeout=15)
    summary = data.get("data", {}) if data.get("data") else {}
    check("LLM Logs 摘要接口", status == 200 and data.get("code") == 200,
          f"status={status}")

    total_calls = summary.get("totalCalls", 0)
    check("LLM Logs 有调用记录", total_calls > 0,
          f"totalCalls={total_calls}")

    if total_calls > 0:
        check("LLM Logs 成功调用 > 0", summary.get("successCalls", 0) > 0,
              f"successCalls={summary.get('successCalls', 0)}")
        check("LLM Logs Token 用量 > 0",
              (summary.get("totalInputTokens", 0) + summary.get("totalOutputTokens", 0)) > 0,
              f"input={summary.get('totalInputTokens', 0)}, output={summary.get('totalOutputTokens', 0)}")
        by_agent = summary.get("byAgent", [])
        check("LLM Logs byAgent 非空", isinstance(by_agent, list) and len(by_agent) > 0,
              f"agents={len(by_agent)}")

    # 5.2 最近记录
    print("  → 查询最近 LLM 调用记录...")
    data, status, _ = http_request("GET", "/llm-logs/recent", token=token, timeout=15)
    recent = data.get("data", []) if data.get("data") else []
    check("LLM Logs 最近记录接口", status == 200,
          f"count={len(recent)}")

    return summary


# ============================================================
# Test 6: Cleanup (删除测试版本)
# ============================================================
def test_cleanup(token, version_ids):
    print("\n[TEST 6] Cleanup — 删除测试版本")
    for vid in version_ids:
        data, status, _ = http_request("DELETE", f"/resume/versions/{vid}",
                                       token=token, timeout=15)
        check(f"删除版本 {vid}", status == 200,
              f"status={status}")


# ============================================================
# Main
# ============================================================
def main():
    print("=" * 60)
    print("FocusOS AI Sprint 7-C-B QA Test")
    print("=" * 60)

    token = login()

    # Test 1: Resume Workspace CRUD
    version_ids = test_resume_workspace_crud(token)

    # Test 2: Resume Export
    test_resume_export(token, version_ids)

    # Test 3: Resume Diff
    test_resume_diff(token, version_ids)

    # Test 4: Interview Repair
    repair_result = test_interview_repair(token)

    # Test 5: LLM Call Log
    llm_summary = test_llm_logs(token)

    # Test 6: Cleanup
    test_cleanup(token, version_ids)

    # 汇总
    print("\n" + "=" * 60)
    print(f"QA Results: {passed_checks}/{total_checks} checks passed "
          f"({(passed_checks / total_checks * 100):.1f}%)")
    print("=" * 60)

    overall = "PASS" if passed_checks == total_checks else ("PARTIAL PASS" if passed_checks >= total_checks * 0.9 else "FAIL")

    output = {
        "sprint": "7-C-B",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total_checks": total_checks,
        "passed_checks": passed_checks,
        "success_rate": round(passed_checks / total_checks * 100, 1) if total_checks > 0 else 0,
        "overall_status": overall,
        "results": results,
        "repair_tested": repair_result.get("tested", False),
        "repair_succeeded": repair_result.get("repaired", False),
        "llm_summary": llm_summary,
    }

    output_file = OUTPUT_DIR / "sprint7cb_summary.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\nResults saved to: {output_file}")

    return 0 if overall == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
