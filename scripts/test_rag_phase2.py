#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sprint 5-A 端到端测试脚本（重启后第二次查询）"""
import requests, json, sys, os

BASE = "http://localhost:8080/api"
SCRIPT_DIR = os.path.dirname(__file__)

print("=" * 60)
print("===== Sprint 5-A 测试：重启后端后第二次查询 =====")
print("=" * 60)

# 读取第一次查询的结果
with open(os.path.join(SCRIPT_DIR, "phase1_result.json"), "r", encoding="utf-8") as f:
    phase1 = json.load(f)
print(f"第一次查询结果摘要: {phase1['results']}")
print(f"第一次文档ID列表: {phase1['doc_ids']}")

# 1. 登录获取 token
print("\n[1/4] 重新登录获取 Token...")
login_body = {"username": "test5a", "password": "123456"}
r = requests.post(f"{BASE}/auth/login", json=login_body, timeout=10)
login_data = r.json().get("data", {})
token = login_data.get("token") or login_data.get("accessToken")
if not token:
    print(f"  登录失败: {r.text[:200]}")
    sys.exit(1)
print(f"  Token 获取成功: {token[:20]}...")
headers = {"Authorization": f"Bearer {token}"}

# 2. 检查文档列表（重启后应仍存在）
print("\n[2/4] 检查文档列表（重启后验证）...")
r = requests.get(f"{BASE}/rag/documents", headers=headers, timeout=10)
docs = r.json().get("data", [])
for d in docs:
    print(f"  docId={d.get('id')} title={d.get('title')} vectorized={d.get('isVectorized')} type={d.get('fileType')}")

# 3. 第二次搜索查询（关键验证：Milvus 数据持久化）
print("\n[3/4] 第二次搜索查询（验证 Milvus 持久化）...")
queries = ["实习经历", "FocusOS 项目技术栈", "简历技能"]
phase2_results = {}
all_pass = True
for q in queries:
    r = requests.get(f"{BASE}/rag/search", headers=headers, params={"query": q}, timeout=30)
    resp = r.json()
    data = resp.get("data", "")
    result_len = len(data) if data else 0
    preview = data[:100] if data else "(空)"
    phase2_results[q] = result_len
    p1_len = phase1["results"].get(q, 0)
    match = "匹配" if result_len == p1_len and result_len > 0 else "不匹配"
    if result_len == 0:
        match = "失败（数据丢失！）"
        all_pass = False
    print(f"  查询「{q}」→ 命中字符数={result_len} (第一次={p1_len}) → {match}")
    print(f"    预览: {preview}...")

# 4. 验证用户隔离（重启后仍生效）
print("\n[4/4] 验证用户隔离（重启后）...")
login_body2 = {"username": "test5a_other", "password": "123456"}
r2 = requests.post(f"{BASE}/auth/login", json=login_body2, timeout=10)
token2 = r2.json().get("data", {}).get("token") or r2.json().get("data", {}).get("accessToken")
headers2 = {"Authorization": f"Bearer {token2}"}
r2 = requests.get(f"{BASE}/rag/search", headers=headers2, params={"query": "实习经历"}, timeout=30)
data2 = r2.json().get("data", "")
isolated = "通过（无数据泄漏）" if (not data2 or len(data2) == 0) else "失败（数据泄漏！）"
print(f"  其他用户查询「实习经历」→ {isolated}")

# 总结
print("\n" + "=" * 60)
if all_pass:
    print("===== 验证结论：全部通过 =====")
    print("  Milvus 向量持久化成功！重启后端后数据未丢失。")
else:
    print("===== 验证结论：存在失败项 =====")
print(f"  第一次查询结果: {phase1['results']}")
print(f"  第二次查询结果: {phase2_results}")
print("=" * 60)
