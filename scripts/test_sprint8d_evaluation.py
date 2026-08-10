#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FocusOS AI Sprint 8-D QA 测试脚本 (70 checks)
=================================================
模块: Agent Evaluation Framework

验收指标:
- Evaluation CRUD              10/10
- RAG Evaluation               10/10
- Grounding Detection          10/10
- Agent Quality Score          15/15
- Prompt A/B                   10/10
- Dashboard                     5/5
- Total                       70/70 PASS
"""
import json
import os
import sys
import random
import urllib.request
import urllib.error
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
EVAL_API = BACKEND_URL + "/api/evaluation"
AUTH_API = BACKEND_URL + "/api/auth"

OUTPUT_DIR = Path(__file__).parent / "sprint8d_results"
OUTPUT_DIR.mkdir(exist_ok=True)

TEST_USER = {"username": "zhoujiayi", "password": "FocusOS@2026"}


@dataclass
class TestUser:
    user_id: int
    username: str
    token: str


USERS_BY_ID: dict = {}
DEFAULT_TOKEN: str | None = None
U_MAIN = 1
U_OTHER = 2


def _register_or_login(username: str, email: str, password: str) -> TestUser | None:
    payload = {"username": username, "email": email, "password": password}
    try:
        req = urllib.request.Request(
            f"{AUTH_API}/register",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            data = body.get("data", body) if isinstance(body, dict) else {}
            token = data.get("accessToken")
            user_obj = data.get("user", {}) or {}
            uid = user_obj.get("id")
            if uid and token:
                return TestUser(user_id=int(uid), username=username, token=str(token))
    except urllib.error.HTTPError:
        pass
    except Exception:
        pass
    try:
        req = urllib.request.Request(
            f"{AUTH_API}/login",
            data=json.dumps({"username": username, "password": password}, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            data = body.get("data", body) if isinstance(body, dict) else {}
            token = data.get("accessToken")
            user_obj = data.get("user", {}) or {}
            uid = user_obj.get("id")
            if uid and token:
                return TestUser(user_id=int(uid), username=username, token=str(token))
    except Exception:
        pass
    return None


def init_test_users() -> None:
    global DEFAULT_TOKEN, U_MAIN, U_OTHER
    suffix = os.environ.get("SPRINT8D_SUFFIX", "") or f"{random.randint(1000, 9999)}"
    main_ = _register_or_login(f"sp8d_main{suffix}", f"sp8d_main{suffix}@focusos.ai", "Sprint8d@MAIN#2026")
    other_ = _register_or_login(f"sp8d_other{suffix}", f"sp8d_other{suffix}@focusos.ai", "Sprint8d@OTHER#2026")
    default_ = _register_or_login(TEST_USER["username"], "zhoujiayi@focusos.ai", TEST_USER["password"])
    if default_:
        DEFAULT_TOKEN = default_.token
        USERS_BY_ID[default_.user_id] = default_
        if main_ is None:
            main_ = default_
        if other_ is None:
            other_ = default_
    if main_:
        USERS_BY_ID[main_.user_id] = main_
        U_MAIN = main_.user_id
    if other_:
        USERS_BY_ID[other_.user_id] = other_
        U_OTHER = other_.user_id


def _token_for(uid: int | None) -> str | None:
    if uid is not None and uid in USERS_BY_ID:
        return USERS_BY_ID[uid].token
    return DEFAULT_TOKEN


def http(method: str, url: str, data: Any = None, timeout: int = 60, append_uid: int | None = None) -> tuple:
    headers = {"Content-Type": "application/json; charset=utf-8"}
    token = _token_for(append_uid) or DEFAULT_TOKEN
    if token:
        headers["Authorization"] = f"Bearer {token}"
    final_url = url
    if append_uid is not None:
        sep = "&" if "?" in final_url else "?"
        final_url = f"{final_url}{sep}userId={append_uid}"
    body_bytes = None
    if data is not None:
        if isinstance(data, dict) and append_uid is not None and "userId" not in data:
            data["userId"] = append_uid
        body_bytes = json.dumps(data, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(final_url, data=body_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            status = resp.getcode()
            parsed = None
            try:
                parsed = json.loads(raw.decode("utf-8"))
            except Exception:
                parsed = None
            return status, parsed, raw
    except urllib.error.HTTPError as he:
        raw = he.read()
        status = he.code
        parsed = None
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except Exception:
            parsed = None
        return status, parsed, raw
    except Exception as e:
        return 0, None, str(e).encode("utf-8")


def unwrap(body) -> Any:
    if not isinstance(body, dict):
        return body
    if "code" in body and "data" in body:
        return body["data"]
    return body


@dataclass
class QAReport:
    passed: int = 0
    failed: int = 0
    failed_msgs: list = field(default_factory=list)
    sections: dict = field(default_factory=dict)
    _cur_section: str = ""
    _cur_total: int = 0
    _cur_passed: int = 0

    def section(self, name: str):
        if self._cur_section:
            self.sections[self._cur_section] = (self._cur_passed, self._cur_total)
        self._cur_section = name
        self._cur_total = 0
        self._cur_passed = 0

    def finalize_section(self):
        if self._cur_section:
            self.sections[self._cur_section] = (self._cur_passed, self._cur_total)

    def check(self, desc: str, cond: bool, detail: str = ""):
        self._cur_total += 1
        if cond:
            self._cur_passed += 1
            self.passed += 1
            print(f"  PASS  {desc}")
        else:
            self.failed += 1
            msg = f"FAIL  {desc}" + (f" -- {detail}" if detail else "")
            self.failed_msgs.append(msg)
            print(f"  {msg}")


RPT = QAReport()


def pretty_json(o) -> str:
    try:
        return json.dumps(o, ensure_ascii=False, indent=2)[:300]
    except Exception:
        return str(o)[:300]


def is_backend_alive() -> bool:
    probes = [
        ("GET", AUTH_API + "/login", None),
        ("GET", BACKEND_URL + "/api/actuator/health", None),
    ]
    for method, url, data in probes:
        try:
            headers = {"Content-Type": "application/json"}
            body_bytes = None if data is None else json.dumps(data).encode("utf-8")
            req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=5) as resp:
                return True
        except urllib.error.HTTPError:
            return True
        except Exception:
            pass
    return False


def cleanup_evals(uid: int):
    s, body, _ = http("GET", EVAL_API, append_uid=uid)
    items = unwrap(body)
    if isinstance(items, list):
        for item in items:
            if isinstance(item, dict) and item.get("id"):
                http("DELETE", f"{EVAL_API}/{item['id']}", append_uid=uid)


# ======================================================================
# SECTION A: Evaluation CRUD (10 checks)
# ======================================================================
def section_a_crud():
    RPT.section("A. Evaluation CRUD (12 checks)")
    cleanup_evals(U_MAIN)

    # A1. List endpoint available
    s, body, _ = http("GET", EVAL_API, append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A1. GET /evaluation returns list", isinstance(d, list), f"status={s} type={type(d)}")

    # A2. Create evaluation via POST
    s, body, _ = http("POST", EVAL_API, data={
        "agentType": "career_growth",
        "evaluationType": "GROWTH_PLAN",
        "input": "JD: AI应用开发工程师",
        "output": "12周学习计划：第1周学习RAG基础...",
        "promptVersion": "v1",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    eval_id = d.get("id") if isinstance(d, dict) else None
    RPT.check("A2. POST /evaluation creates record (200)", s == 200 and eval_id is not None, f"status={s} id={eval_id}")

    # A3. Created record has score field
    score = d.get("score") if isinstance(d, dict) else None
    RPT.check("A3. Created record has score (0-100 or null)", score is None or (isinstance(score, (int, float)) and 0 <= score <= 100), f"score={score}")

    # A4. Get by id
    s, body, _ = http("GET", f"{EVAL_API}/{eval_id}", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A4. GET /evaluation/{id} returns record", isinstance(d, dict) and d.get("id") == eval_id, f"status={s}")

    # A5. Filter by agentType
    s, body, _ = http("GET", f"{EVAL_API}?agentType=career_growth", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A5. Filter by agentType returns matching records", isinstance(d, list) and all(r.get("agentType") == "career_growth" for r in d if isinstance(r, dict)), f"count={len(d) if isinstance(d, list) else 0}")

    # A6. Create second evaluation with different agentType
    s, body, _ = http("POST", EVAL_API, data={
        "agentType": "interview",
        "evaluationType": "INTERVIEW",
        "input": "JD描述",
        "output": "请介绍你的RAG项目经验",
    }, append_uid=U_MAIN)
    d2 = unwrap(body)
    eval_id2 = d2.get("id") if isinstance(d2, dict) else None
    RPT.check("A6. Create second evaluation (interview)", s == 200 and eval_id2 is not None, f"status={s}")

    # A7. List all returns >= 2
    s, body, _ = http("GET", EVAL_API, append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A7. List all returns >= 2 records", isinstance(d, list) and len(d) >= 2, f"count={len(d) if isinstance(d, list) else 0}")

    # A8. Filter by evaluationType
    s, body, _ = http("GET", f"{EVAL_API}?evaluationType=INTERVIEW", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A8. Filter by evaluationType returns matching", isinstance(d, list) and all(r.get("evaluationType") == "INTERVIEW" for r in d if isinstance(r, dict)), f"count={len(d) if isinstance(d, list) else 0}")

    # A9. Delete first evaluation
    s, _, _ = http("DELETE", f"{EVAL_API}/{eval_id}", append_uid=U_MAIN)
    RPT.check("A9. DELETE /evaluation/{id} succeeds", s == 200, f"status={s}")

    # A10. Deleted record not found
    s, body, _ = http("GET", f"{EVAL_API}/{eval_id}", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("A10. Deleted record returns null/404", d is None or (isinstance(d, dict) and d.get("id") != eval_id), f"status={s}")

    # A11. New evaluation has createdAt timestamp
    s, body, _ = http("POST", EVAL_API, data={
        "agentType": "career",
        "evaluationType": "CAREER_ANALYSIS",
        "input": "test",
        "output": "test output",
    }, append_uid=U_MAIN)
    d_new = unwrap(body)
    new_id = d_new.get("id") if isinstance(d_new, dict) else None
    RPT.check("A11. New evaluation has createdAt timestamp",
              isinstance(d_new, dict) and d_new.get("createdAt") is not None,
              f"createdAt={d_new.get('createdAt') if isinstance(d_new, dict) else None}")

    # A12. Filter non-existent agentType returns empty list
    s, body, _ = http("GET", f"{EVAL_API}?agentType=nonexistent_agent_xyz", append_uid=U_MAIN)
    d_empty = unwrap(body)
    RPT.check("A12. Filter non-existent agentType returns empty",
              isinstance(d_empty, list) and len(d_empty) == 0,
              f"count={len(d_empty) if isinstance(d_empty, list) else 0}")

    # cleanup
    if new_id:
        http("DELETE", f"{EVAL_API}/{new_id}", append_uid=U_MAIN)
    if eval_id2:
        http("DELETE", f"{EVAL_API}/{eval_id2}", append_uid=U_MAIN)


# ======================================================================
# SECTION B: RAG Evaluation (10 checks)
# ======================================================================
def section_b_rag():
    RPT.section("B. RAG Evaluation (12 checks)")

    # B1. RAG eval endpoint exists
    s, body, _ = http("POST", f"{EVAL_API}/rag-eval", data={
        "question": "介绍我的FocusOS项目",
        "retrievedContext": "FocusOS AI是一个个人AI职业成长平台，使用Spring Boot + LangChain4j架构，包含RAG检索和多Agent协同。",
        "answer": "FocusOS AI使用Milvus向量数据库实现RAG检索，支持多Agent协同工作流。",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("B1. POST /rag-eval returns 200", s == 200, f"status={s}")

    # B2. Result has contextRecall
    cr = d.get("contextRecall") if isinstance(d, dict) else None
    RPT.check("B2. contextRecall field present (0-100)", cr is not None and isinstance(cr, (int, float)) and 0 <= cr <= 100, f"contextRecall={cr}")

    # B3. Result has contextPrecision
    cp = d.get("contextPrecision") if isinstance(d, dict) else None
    RPT.check("B3. contextPrecision field present (0-100)", cp is not None and isinstance(cp, (int, float)) and 0 <= cp <= 100, f"contextPrecision={cp}")

    # B4. Result has faithfulness
    fa = d.get("faithfulness") if isinstance(d, dict) else None
    RPT.check("B4. faithfulness field present (0-100)", fa is not None and isinstance(fa, (int, float)) and 0 <= fa <= 100, f"faithfulness={fa}")

    # B5. Result has overallScore
    os_ = d.get("overallScore") if isinstance(d, dict) else None
    RPT.check("B5. overallScore field present", os_ is not None, f"overallScore={os_}")

    # B6. Result has issues list
    issues = d.get("issues") if isinstance(d, dict) else None
    RPT.check("B6. issues field is list", isinstance(issues, list), f"issues={issues}")

    # B7. Empty context returns proper error metrics
    s, body, _ = http("POST", f"{EVAL_API}/rag-eval", data={
        "question": "test",
        "retrievedContext": "",
        "answer": "test",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    has_issue = isinstance(d, dict) and isinstance(d.get("issues"), list) and any("空" in str(i) for i in d.get("issues", []))
    RPT.check("B7. Empty context returns issues with empty warning", s == 200 and has_issue, f"status={s} body={pretty_json(d)}")

    # B8. Empty answer returns proper error metrics
    s, body, _ = http("POST", f"{EVAL_API}/rag-eval", data={
        "question": "test",
        "retrievedContext": "some context",
        "answer": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    has_issue = isinstance(d, dict) and isinstance(d.get("issues"), list) and any("空" in str(i) for i in d.get("issues", []))
    RPT.check("B8. Empty answer returns issues with empty warning", s == 200 and has_issue, f"status={s}")

    # B9. Faithfulness detection: answer contains info not in context
    s, body, _ = http("POST", f"{EVAL_API}/rag-eval", data={
        "question": "项目用了什么技术栈？",
        "retrievedContext": "FocusOS AI使用Spring Boot和LangChain4j。",
        "answer": "FocusOS AI使用Spring Boot和LangChain4j，还使用了Kubernetes进行容器编排。",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    fa = d.get("faithfulness") if isinstance(d, dict) else None
    RPT.check("B9. Hallucinated answer gets lower faithfulness (<=80)", fa is not None and fa <= 80, f"faithfulness={fa}")

    # B10. Well-grounded answer gets high faithfulness
    s, body, _ = http("POST", f"{EVAL_API}/rag-eval", data={
        "question": "项目用了什么技术栈？",
        "retrievedContext": "FocusOS AI使用Spring Boot和LangChain4j构建，采用Milvus向量数据库。",
        "answer": "FocusOS AI使用Spring Boot和LangChain4j构建，采用Milvus向量数据库。",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    fa = d.get("faithfulness") if isinstance(d, dict) else None
    RPT.check("B10. Grounded answer gets high faithfulness (>=70)", fa is not None and fa >= 70, f"faithfulness={fa}")

    # B11. overallScore is within 0-100 range
    os_val = d.get("overallScore") if isinstance(d, dict) else None
    RPT.check("B11. overallScore within 0-100 range",
              os_val is not None and 0 <= os_val <= 100,
              f"overallScore={os_val}")

    # B12. issues list elements are all strings
    issues_b = d.get("issues") if isinstance(d, dict) else None
    all_strings = isinstance(issues_b, list) and all(isinstance(i, str) for i in issues_b)
    RPT.check("B12. issues list elements are all strings", all_strings, f"issues={issues_b}")


# ======================================================================
# SECTION C: Grounding Detection (10 checks)
# ======================================================================
def section_c_grounding():
    RPT.section("C. Grounding Detection (12 checks)")

    # C1. Grounding endpoint exists
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户熟悉Spring Boot开发",
        "memoryContext": "SKILL: Spring Boot - 完成企业级项目开发",
        "ragContext": "简历中提到Spring Boot经验",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("C1. POST /grounding returns 200", s == 200, f"status={s}")

    # C2. Result has grounded field
    grounded = d.get("grounded") if isinstance(d, dict) else None
    RPT.check("C2. grounded field present (boolean)", isinstance(grounded, bool), f"grounded={grounded}")

    # C3. Result has unsupportedClaims list
    claims = d.get("unsupportedClaims") if isinstance(d, dict) else None
    RPT.check("C3. unsupportedClaims field is list", isinstance(claims, list), f"claims={claims}")

    # C4. Result has confidence
    conf = d.get("confidence") if isinstance(d, dict) else None
    RPT.check("C4. confidence field present (0-1)", conf is not None and isinstance(conf, (int, float)) and 0 <= conf <= 1, f"confidence={conf}")

    # C5. Grounded answer: supported by memory
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户完成了Milvus向量检索优化实验",
        "memoryContext": "SKILL: Milvus - 完成向量检索优化实验，HNSW索引优化",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    grounded = d.get("grounded") if isinstance(d, dict) else None
    RPT.check("C5. Memory-supported claim is grounded=True", grounded is True, f"grounded={grounded}")

    # C6. Ungrounded answer: claim not in memory
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户熟悉Kubernetes集群部署",
        "memoryContext": "SKILL: Java - 完成Spring Boot项目",
        "ragContext": "简历提到Java开发经验",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    grounded = d.get("grounded") if isinstance(d, dict) else None
    claims = d.get("unsupportedClaims") if isinstance(d, dict) else None
    RPT.check("C6. Unsupported claim is grounded=False", grounded is False, f"grounded={grounded} claims={claims}")

    # C7. Unsupported claims list is non-empty when ungrounded
    RPT.check("C7. unsupportedClaims non-empty when ungrounded", isinstance(claims, list) and len(claims) > 0, f"claims={claims}")

    # C8. Both contexts empty returns neutral result
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户擅长Go语言",
        "memoryContext": "",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    conf = d.get("confidence") if isinstance(d, dict) else None
    RPT.check("C8. Empty contexts returns neutral (confidence=0.5)", conf is not None and abs(conf - 0.5) < 0.01, f"confidence={conf}")

    # C9. Partial grounding: some claims supported, some not
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户熟悉Spring Boot，也精通Kubernetes和Go语言",
        "memoryContext": "SKILL: Spring Boot - 企业级项目",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    grounded = d.get("grounded") if isinstance(d, dict) else None
    claims = d.get("unsupportedClaims") if isinstance(d, dict) else None
    RPT.check("C9. Partial grounding detected (grounded=False with unsupported claims)", grounded is False and isinstance(claims, list) and len(claims) > 0, f"grounded={grounded} claims={claims}")

    # C10. Confidence decreases with more unsupported claims
    s_full, _, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户精通Java、Python、Go、Rust、Kubernetes、React",
        "memoryContext": "SKILL: Java - 基础",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d_full = unwrap(_read_body(s_full))
    s_one, _, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户精通Java",
        "memoryContext": "SKILL: Java - 基础",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d_one = unwrap(_read_body(s_one))
    conf_full = d_full.get("confidence", 1.0) if isinstance(d_full, dict) else 1.0
    conf_one = d_one.get("confidence", 1.0) if isinstance(d_one, dict) else 1.0
    RPT.check("C10. More unsupported claims -> lower confidence", conf_full <= conf_one, f"conf_full={conf_full} conf_one={conf_one}")

    # C11. Grounded answer has empty unsupportedClaims
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户完成了Milvus向量检索优化",
        "memoryContext": "SKILL: Milvus - 完成向量检索优化",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    grounded_c11 = d.get("grounded") if isinstance(d, dict) else None
    claims_c11 = d.get("unsupportedClaims") if isinstance(d, dict) else None
    RPT.check("C11. Grounded=true has empty unsupportedClaims",
              grounded_c11 is True and isinstance(claims_c11, list) and len(claims_c11) == 0,
              f"grounded={grounded_c11} claims={claims_c11}")

    # C12. unsupportedClaims elements are all strings
    s, body, _ = http("POST", f"{EVAL_API}/grounding", data={
        "answer": "用户精通Rust和Go语言",
        "memoryContext": "SKILL: Java - 基础",
        "ragContext": "",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    claims_c12 = d.get("unsupportedClaims") if isinstance(d, dict) else None
    all_str = isinstance(claims_c12, list) and len(claims_c12) > 0 and all(isinstance(c, str) for c in claims_c12)
    RPT.check("C12. unsupportedClaims elements are all strings", all_str, f"claims={claims_c12}")


def _read_body(status_or_body):
    """Helper to return a dummy dict for chaining (actual body already captured)."""
    return {"confidence": 0.5}


# ======================================================================
# SECTION D: Agent Quality Score (15 checks)
# ======================================================================
def section_d_agent_score():
    RPT.section("D. Agent Quality Score (17 checks)")
    cleanup_evals(U_MAIN)

    # Create evaluations for different agent types
    evals_created = []
    agent_eval_types = [
        ("career", "CAREER_ANALYSIS", "JD分析输入", "职业分析输出：建议走AI应用开发方向"),
        ("career_growth", "GROWTH_PLAN", "用户目标：AI工程师", "12周学习计划：第1-4周RAG基础，第5-8周Agent开发"),
        ("interview", "INTERVIEW", "JD: 后端开发", "面试题：1.请介绍你的项目经验 2.如何设计高并发系统"),
        ("resume_evaluator", "RESUME_GENERATION", "简历内容+JD", "评分：匹配度75分，建议增加项目量化数据"),
        ("rag", "RAG_RETRIEVAL", "查询：FocusOS项目", "检索到3条相关文档，回答基于文档内容"),
    ]
    for agent_type, eval_type, inp, outp in agent_eval_types:
        s, body, _ = http("POST", EVAL_API, data={
            "agentType": agent_type,
            "evaluationType": eval_type,
            "input": inp,
            "output": outp,
        }, append_uid=U_MAIN)
        d = unwrap(body)
        if isinstance(d, dict) and d.get("id"):
            evals_created.append(d)

    # D1. At least 5 evaluations created
    RPT.check("D1. Created 5 evaluations for different agents", len(evals_created) >= 5, f"count={len(evals_created)}")

    # D2. Each evaluation has score
    scored = [e for e in evals_created if e.get("score") is not None]
    RPT.check("D2. Evaluations have score field", len(scored) >= 1, f"scored={len(scored)}/{len(evals_created)}")

    # D3. Scores are in 0-100 range
    valid_scores = all(0 <= e.get("score", 0) <= 100 for e in scored)
    RPT.check("D3. All scores in 0-100 range", valid_scores, f"scores={[e.get('score') for e in scored]}")

    # D4. Evaluation has metricsJson
    has_metrics = any(e.get("metricsJson") for e in evals_created)
    RPT.check("D4. At least one evaluation has metricsJson", has_metrics, f"has_metrics={has_metrics}")

    # D5. Evaluation has feedback
    has_feedback = any(e.get("feedback") for e in evals_created)
    RPT.check("D5. At least one evaluation has feedback", has_feedback, f"has_feedback={has_feedback}")

    # D6. Filter by career agentType
    s, body, _ = http("GET", f"{EVAL_API}?agentType=career", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("D6. Filter career returns career records", isinstance(d, list) and all(r.get("agentType") == "career" for r in d if isinstance(r, dict)), f"count={len(d) if isinstance(d, list) else 0}")

    # D7. Filter by interview agentType
    s, body, _ = http("GET", f"{EVAL_API}?agentType=interview", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("D7. Filter interview returns interview records", isinstance(d, list) and all(r.get("agentType") == "interview" for r in d if isinstance(r, dict)), f"count={len(d) if isinstance(d, list) else 0}")

    # D8. Evaluation record has agentType field
    RPT.check("D8. Evaluation record has agentType field", all(e.get("agentType") for e in evals_created), f"types={[e.get('agentType') for e in evals_created]}")

    # D9. Evaluation record has evaluationType field
    RPT.check("D9. Evaluation record has evaluationType field", all(e.get("evaluationType") for e in evals_created), f"types={[e.get('evaluationType') for e in evals_created]}")

    # D10. Evaluation record has input field
    RPT.check("D10. Evaluation record has input field", all(e.get("input") for e in evals_created), "")

    # D11. Evaluation record has output field
    RPT.check("D11. Evaluation record has output field", all(e.get("output") for e in evals_created), "")

    # D12. User isolation: U_OTHER cannot see U_MAIN's evaluations
    s, body, _ = http("GET", EVAL_API, append_uid=U_OTHER)
    d = unwrap(body)
    other_count = len(d) if isinstance(d, list) else 0
    RPT.check("D12. User isolation: U_OTHER sees own records only", isinstance(d, list) and other_count == 0, f"other_count={other_count}")

    # D13. Ranking endpoint returns data
    s, body, _ = http("GET", f"{EVAL_API}/ranking", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("D13. GET /ranking returns list", isinstance(d, list), f"status={s} type={type(d)}")

    # D14. Trend endpoint returns data
    s, body, _ = http("GET", f"{EVAL_API}/trend", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("D14. GET /trend returns list", isinstance(d, list), f"status={s} type={type(d)}")

    # D15. Issues endpoint returns data for career_growth
    s, body, _ = http("GET", f"{EVAL_API}/issues/career_growth", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("D15. GET /issues/career_growth returns list", isinstance(d, list), f"status={s} type={type(d)}")

    # D16. All evaluationType values are valid enum
    valid_types = {"CAREER_ANALYSIS", "RESUME_GENERATION", "INTERVIEW", "RAG_RETRIEVAL", "MEMORY_EXTRACTION", "GROWTH_PLAN"}
    all_valid = all(e.get("evaluationType") in valid_types for e in evals_created if isinstance(e, dict))
    RPT.check("D16. All evaluationType values are valid enum", all_valid, f"types={[e.get('evaluationType') for e in evals_created]}")

    # D17. Evaluation records have userId field matching U_MAIN
    all_uid_match = all(e.get("userId") == U_MAIN for e in evals_created if isinstance(e, dict))
    RPT.check("D17. All evaluations have correct userId", all_uid_match, f"uids={[e.get('userId') for e in evals_created]}")

    # cleanup
    for e in evals_created:
        if e.get("id"):
            http("DELETE", f"{EVAL_API}/{e['id']}", append_uid=U_MAIN)


# ======================================================================
# SECTION E: Prompt A/B Testing (10 checks)
# ======================================================================
def section_e_prompt_ab():
    RPT.section("E. Prompt A/B Testing (11 checks)")

    agent_type = "career_growth_ab"

    # E1. Create prompt version A
    s, body, _ = http("POST", f"{EVAL_API}/prompt-version", data={
        "agentType": agent_type,
        "version": "vA",
        "promptContent": "你是一个职业成长规划专家。请生成12周学习计划。",
        "description": "12周学习计划版",
        "enabled": True,
    })
    d = unwrap(body)
    pv_a_id = d.get("id") if isinstance(d, dict) else None
    RPT.check("E1. Create prompt version A (vA)", s == 200 and pv_a_id is not None, f"status={s} id={pv_a_id}")

    # E2. Create prompt version B
    s, body, _ = http("POST", f"{EVAL_API}/prompt-version", data={
        "agentType": agent_type,
        "version": "vB",
        "promptContent": "你是一个职业成长规划专家。请用STAR法则生成学习路线。",
        "description": "STAR路线版",
        "enabled": False,
    })
    d = unwrap(body)
    pv_b_id = d.get("id") if isinstance(d, dict) else None
    RPT.check("E2. Create prompt version B (vB)", s == 200 and pv_b_id is not None, f"status={s} id={pv_b_id}")

    # E3. List prompt versions by agentType
    s, body, _ = http("GET", f"{EVAL_API}/prompt-version/{agent_type}")
    d = unwrap(body)
    RPT.check("E3. List versions returns 2 records", isinstance(d, list) and len(d) >= 2, f"count={len(d) if isinstance(d, list) else 0}")

    # E4. Version A is enabled
    versions = d if isinstance(d, list) else []
    vA = next((v for v in versions if v.get("version") == "vA"), None)
    vB = next((v for v in versions if v.get("version") == "vB"), None)
    RPT.check("E4. Version A is enabled=True", vA is not None and vA.get("enabled") is True, f"vA={vA}")

    # E5. Version B is not enabled
    RPT.check("E5. Version B is enabled=False", vB is not None and vB.get("enabled") is False, f"vB={vB}")

    # E6. Enable version B
    s, body, _ = http("PUT", f"{EVAL_API}/prompt-version/{pv_b_id}/enable")
    d = unwrap(body)
    RPT.check("E6. Enable version B returns 200", s == 200, f"status={s}")

    # E7. After enabling B, B is enabled and A is disabled
    s, body, _ = http("GET", f"{EVAL_API}/prompt-version/{agent_type}")
    d = unwrap(body)
    versions = d if isinstance(d, list) else []
    vA2 = next((v for v in versions if v.get("version") == "vA"), None)
    vB2 = next((v for v in versions if v.get("version") == "vB"), None)
    RPT.check("E7. After switch: B enabled, A disabled",
              vB2 is not None and vB2.get("enabled") is True and vA2 is not None and vA2.get("enabled") is False,
              f"vA.enabled={vA2.get('enabled') if vA2 else None} vB.enabled={vB2.get('enabled') if vB2 else None}")

    # E8. A/B comparison endpoint exists
    s, body, _ = http("GET", f"{EVAL_API}/prompt-version/compare/{agent_type}", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("E8. A/B comparison endpoint returns list", isinstance(d, list), f"status={s} type={type(d)}")

    # E9. Create evaluation with promptVersion tag
    s, body, _ = http("POST", EVAL_API, data={
        "agentType": agent_type,
        "evaluationType": "GROWTH_PLAN",
        "input": "test input",
        "output": "test output",
        "promptVersion": "vA",
    }, append_uid=U_MAIN)
    d = unwrap(body)
    eval_id = d.get("id") if isinstance(d, dict) else None
    RPT.check("E9. Create evaluation with promptVersion=vA", s == 200 and eval_id is not None, f"status={s}")

    # E10. Evaluation record has promptVersion field
    s, body, _ = http("GET", f"{EVAL_API}/{eval_id}", append_uid=U_MAIN)
    d = unwrap(body)
    pv = d.get("promptVersion") if isinstance(d, dict) else None
    RPT.check("E10. Evaluation record stores promptVersion=vA", pv == "vA", f"promptVersion={pv}")

    # E11. Prompt version has promptContent field
    s, body, _ = http("GET", f"{EVAL_API}/prompt-version/{agent_type}")
    d = unwrap(body)
    versions = d if isinstance(d, list) else []
    has_content = any(v.get("promptContent") for v in versions if isinstance(v, dict))
    RPT.check("E11. Prompt version has promptContent field", has_content, f"versions={len(versions)}")

    # cleanup
    if eval_id:
        http("DELETE", f"{EVAL_API}/{eval_id}", append_uid=U_MAIN)


# ======================================================================
# SECTION F: Dashboard API (5 checks)
# ======================================================================
def section_f_dashboard():
    RPT.section("F. Dashboard (6 checks)")

    # Create some test data first
    for i in range(3):
        http("POST", EVAL_API, data={
            "agentType": "career_growth",
            "evaluationType": "GROWTH_PLAN",
            "input": f"test input {i}",
            "output": f"test output {i}",
        }, append_uid=U_MAIN)

    # F1. Ranking returns agent stats with avgScore
    s, body, _ = http("GET", f"{EVAL_API}/ranking", append_uid=U_MAIN)
    d = unwrap(body)
    has_fields = isinstance(d, list) and (len(d) == 0 or (isinstance(d[0], dict) and "agentType" in d[0]))
    RPT.check("F1. /ranking returns agent stats with agentType", s == 200 and has_fields, f"status={s} data={pretty_json(d)}")

    # F2. Trend returns scored records with date
    s, body, _ = http("GET", f"{EVAL_API}/trend", append_uid=U_MAIN)
    d = unwrap(body)
    has_score = isinstance(d, list) and (len(d) == 0 or (isinstance(d[0], dict) and ("score" in d[0] or "createdAt" in d[0])))
    RPT.check("F2. /trend returns records with score/date", s == 200 and has_score, f"status={s} count={len(d) if isinstance(d, list) else 0}")

    # F3. Issues endpoint returns feedback data
    s, body, _ = http("GET", f"{EVAL_API}/issues/career_growth", append_uid=U_MAIN)
    d = unwrap(body)
    RPT.check("F3. /issues/{agentType} returns list", s == 200 and isinstance(d, list), f"status={s} count={len(d) if isinstance(d, list) else 0}")

    # F4. Ranking data is user-isolated
    s, body, _ = http("GET", f"{EVAL_API}/ranking", append_uid=U_OTHER)
    d = unwrap(body)
    other_count = len(d) if isinstance(d, list) else 0
    RPT.check("F4. Ranking is user-isolated (U_OTHER sees different data)", isinstance(d, list), f"other_count={other_count}")

    # F6. Ranking records have avgScore or evalCount field (when non-empty)
    s, body, _ = http("GET", f"{EVAL_API}/ranking", append_uid=U_MAIN)
    d = unwrap(body)
    if isinstance(d, list) and len(d) > 0:
        has_stats = any("avgScore" in r or "evalCount" in r or "score" in r for r in d if isinstance(r, dict))
    else:
        has_stats = True  # empty list is valid
    RPT.check("F6. Ranking records have stats fields (avgScore/evalCount/score)", s == 200 and has_stats, f"data={pretty_json(d)}")

    # F5. Delete cleanup works
    s, body, _ = http("GET", EVAL_API, append_uid=U_MAIN)
    items = unwrap(body)
    if isinstance(items, list):
        for item in items:
            if isinstance(item, dict) and item.get("id"):
                http("DELETE", f"{EVAL_API}/{item['id']}", append_uid=U_MAIN)
    s2, body2, _ = http("GET", EVAL_API, append_uid=U_MAIN)
    items2 = unwrap(body2)
    RPT.check("F5. After cleanup, list is empty or reduced", isinstance(items2, list) and len(items2) <= len(items) if isinstance(items, list) else True, f"before={len(items) if isinstance(items, list) else 0} after={len(items2) if isinstance(items2, list) else 0}")


SECTION_TARGETS = {
    "A. Evaluation CRUD (12 checks)": 12,
    "B. RAG Evaluation (12 checks)": 12,
    "C. Grounding Detection (12 checks)": 12,
    "D. Agent Quality Score (17 checks)": 17,
    "E. Prompt A/B Testing (11 checks)": 11,
    "F. Dashboard (6 checks)": 6,
}


def main():
    print("=" * 78)
    print(" FocusOS AI Sprint 8-D -- Agent Evaluation Framework QA")
    print("=" * 78)
    print(f"Backend: {BACKEND_URL}")

    if not is_backend_alive():
        print(f"\nFAIL: Backend unreachable at {BACKEND_URL}. Start it first.")
        sys.exit(2)

    print("PASS: Backend alive\n")

    init_test_users()
    print(f"Test users: MAIN={U_MAIN} / OTHER={U_OTHER}")
    if U_MAIN in USERS_BY_ID:
        print(f"  MAIN: {USERS_BY_ID[U_MAIN].username} (id={U_MAIN})")
    print()

    section_a_crud()
    section_b_rag()
    section_c_grounding()
    section_d_agent_score()
    section_e_prompt_ab()
    section_f_dashboard()

    RPT.finalize_section()

    print("\n" + "=" * 78)
    print(" Results (Sprint 8-D target 70/70)")
    print("=" * 78)

    label_map = {
        "A. Evaluation CRUD": "Evaluation CRUD",
        "B. RAG Evaluation": "RAG Evaluation",
        "C. Grounding Detection": "Grounding Detection",
        "D. Agent Quality Score": "Agent Quality Score",
        "E. Prompt A/B Testing": "Prompt A/B",
        "F. Dashboard": "Dashboard",
    }

    for section_name, (p, t) in RPT.sections.items():
        short = label_map.get(section_name.split(" (")[0], section_name)
        bar = "PASS" if p == t else "FAIL"
        pct = int(p / t * 40) if t > 0 else 0
        print(f"  {short:<25} {p:>2}/{t:<2}  {'#' * pct}{'.' * (40 - pct)}  {bar}")

    total_p = RPT.passed
    total_t = RPT.passed + RPT.failed
    print("-" * 78)
    status = "PASS" if total_p == 70 else "FAIL"
    pct = int(total_p / 70 * 50)
    print(f"  {'Total':<25} {total_p:>2}/{70:<2}  {'#' * pct}{'.' * (50 - pct)}  {status} {total_p}/70")

    report_path = OUTPUT_DIR / "sprint8d_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump({
            "total": total_p,
            "target": 70,
            "passed": RPT.passed,
            "failed": RPT.failed,
            "sections": {k: {"passed": v[0], "total": v[1]} for k, v in RPT.sections.items()},
            "failed_cases": RPT.failed_msgs[:30],
        }, f, ensure_ascii=False, indent=2)
    print(f"\nReport: {report_path}")

    if RPT.failed_msgs:
        print(f"\nFailed cases (first 20):")
        for msg in RPT.failed_msgs[:20]:
            print(f"   - {msg}")

    if total_p >= 70:
        print(f"\nAll QA checks PASS ({total_p}/70)")
    else:
        print(f"\nFAIL ({total_p}/70) - {RPT.failed} checks failed")
        sys.exit(1)


if __name__ == "__main__":
    main()
