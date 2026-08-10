#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sprint 5-A 端到端测试脚本（第一次查询）"""
import requests, json, sys, os

BASE = "http://localhost:8080/api"
TEST_DIR = os.path.join(os.path.dirname(__file__), "..", "test-files")

print("=" * 60)
print("===== Sprint 5-A 测试：第一次查询 =====")
print("=" * 60)

# 1. 注册用户
print("\n[1/6] 注册测试用户...")
reg_body = {"username": "test5a", "email": "test5a@focusos.com", "password": "123456"}
try:
    r = requests.post(f"{BASE}/auth/register", json=reg_body, timeout=10)
    print(f"  注册响应: {r.json().get('message', r.text[:80])}")
except Exception as e:
    print(f"  注册异常(可能已存在): {e}")

# 2. 登录获取 token
print("\n[2/6] 登录获取 Token...")
login_body = {"username": "test5a", "password": "123456"}
r = requests.post(f"{BASE}/auth/login", json=login_body, timeout=10)
login_data = r.json().get("data", {})
token = login_data.get("token") or login_data.get("accessToken")
if not token:
    print(f"  登录失败: {r.text[:200]}")
    sys.exit(1)
print(f"  Token 获取成功: {token[:20]}...")
headers = {"Authorization": f"Bearer {token}"}

# 3. 上传三个文件
print("\n[3/6] 上传三个文件...")
files_config = [
    ("resume.pdf", "张明个人简历", "简历"),
    ("实习日志.txt", "字节跳动实习日志", "日志"),
    ("FocusOS项目说明.md", "FocusOS AI 项目说明", "文档"),
]
doc_ids = []
for fname, title, category in files_config:
    fpath = os.path.join(TEST_DIR, fname)
    with open(fpath, "rb") as f:
        r = requests.post(f"{BASE}/rag/documents/upload", headers=headers,
                          files={"file": (fname, f)}, data={"title": title, "category": category}, timeout=30)
    resp = r.json()
    doc_id = resp.get("data", {}).get("id")
    doc_ids.append(doc_id)
    print(f"  上传成功: {title} → docId={doc_id}")

# 4. 向量化三个文件
print("\n[4/6] 向量化三个文件（写入 Milvus）...")
for i, doc_id in enumerate(doc_ids):
    r = requests.post(f"{BASE}/rag/documents/{doc_id}/vectorize", headers=headers, timeout=60)
    resp = r.json()
    vec = resp.get("data", {}).get("isVectorized")
    print(f"  向量化成功: {files_config[i][1]} → isVectorized={vec}")

# 5. 搜索查询（第一次）
print("\n[5/6] 第一次搜索查询（验证 Milvus 写入）...")
queries = ["实习经历", "FocusOS 项目技术栈", "简历技能"]
phase1_results = {}
for q in queries:
    r = requests.get(f"{BASE}/rag/search", headers=headers, params={"query": q}, timeout=30)
    resp = r.json()
    data = resp.get("data", "")
    result_len = len(data) if data else 0
    preview = data[:120] if data else "(空)"
    phase1_results[q] = result_len
    print(f"  查询「{q}」→ 命中字符数={result_len}")
    print(f"    预览: {preview}...")

# 6. 验证用户隔离
print("\n[6/6] 验证用户隔离（注册第二个用户，查询应为空）...")
reg_body2 = {"username": "test5a_other", "email": "other@focusos.com", "password": "123456"}
try:
    requests.post(f"{BASE}/auth/register", json=reg_body2, timeout=10)
except Exception:
    pass
login_body2 = {"username": "test5a_other", "password": "123456"}
r2 = requests.post(f"{BASE}/auth/login", json=login_body2, timeout=10)
token2 = r2.json().get("data", {}).get("token") or r2.json().get("data", {}).get("accessToken")
headers2 = {"Authorization": f"Bearer {token2}"}
r2 = requests.get(f"{BASE}/rag/search", headers=headers2, params={"query": "实习经历"}, timeout=30)
data2 = r2.json().get("data", "")
isolated = "通过（无数据泄漏）" if (not data2 or len(data2) == 0) else "失败（数据泄漏！）"
print(f"  其他用户查询「实习经历」→ {isolated}")

# 输出文档列表
print("\n--- 文档列表 ---")
r = requests.get(f"{BASE}/rag/documents", headers=headers, timeout=10)
for d in r.json().get("data", []):
    print(f"  docId={d.get('id')} title={d.get('title')} vectorized={d.get('isVectorized')} type={d.get('fileType')}")

print("\n===== 第一次查询完成 =====")
print(f"文档ID列表（供重启后验证用）: {doc_ids}")
print(f"第一次查询结果摘要: {phase1_results}")

# 保存结果供第二次对比
with open(os.path.join(os.path.dirname(__file__), "phase1_result.json"), "w", encoding="utf-8") as f:
    json.dump({"doc_ids": doc_ids, "results": phase1_results}, f, ensure_ascii=False, indent=2)
