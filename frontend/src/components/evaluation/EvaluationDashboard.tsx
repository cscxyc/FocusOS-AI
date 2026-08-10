"use client";

import * as React from "react";
import { useEffect, useMemo, useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import {
  Gauge,
  TrendingUp,
  AlertTriangle,
  ClipboardCheck,
  ShieldCheck,
  Database,
  GitCompare,
  Loader2,
  Sparkles,
  RefreshCw,
  Plus,
  CheckCircle2,
  AlertCircle,
  XCircle,
  Trophy,
  ChevronRight,
} from "lucide-react";
import {
  createEvaluation,
  getAgentScoreRanking,
  getScoreTrend,
  getEvaluationIssues,
  checkGrounding,
  evaluateRAG,
  createPromptVersion,
  listPromptVersions,
  enablePromptVersion,
  comparePromptVersions,
} from "@/lib/api";
import type {
  AgentScoreRanking,
  ScoreTrendPoint,
  EvaluationIssue,
  GroundingResult,
  RAGMetrics,
  PromptVersion,
  PromptVersionComparison,
} from "@/lib/types";

// ------------------------------------------------------------------
// 常量与工具
// ------------------------------------------------------------------

const DEFAULT_UID = 1; // QA / 未登录态默认用户 ID

const EVAL_TYPES = [
  "AUTO_LLM",
  "MANUAL",
  "GROUNDING",
  "RAG",
  "PROMPT_AB",
];

const COMMON_AGENTS = [
  "CareerAgent",
  "InterviewAgent",
  "ResumeEvaluator",
  "RAGAgent",
  "CareerGrowthAgent",
  "MemoryAgent",
];

type TabKey = "overview" | "manual" | "grounding" | "rag" | "prompt";

const TABS: { key: TabKey; label: string; icon: React.ComponentType<any> }[] = [
  { key: "overview", label: "评估总览", icon: Gauge },
  { key: "manual", label: "手动评估", icon: ClipboardCheck },
  { key: "grounding", label: "Grounding 核查", icon: ShieldCheck },
  { key: "rag", label: "RAG 评估", icon: Database },
  { key: "prompt", label: "Prompt A/B", icon: GitCompare },
];

/** 分数 → 颜色档位（绿 >= 85, 黄 >= 70, 红 < 70） */
function scoreLevel(score: number | null | undefined): "green" | "yellow" | "red" | "gray" {
  if (score == null) return "gray";
  if (score >= 85) return "green";
  if (score >= 70) return "yellow";
  return "red";
}

function scoreBarColor(level: ReturnType<typeof scoreLevel>): string {
  switch (level) {
    case "green":
      return "bg-green-500";
    case "yellow":
      return "bg-amber-500";
    case "red":
      return "bg-red-500";
    default:
      return "bg-gray-300 dark:bg-gray-600";
  }
}

function scoreBadgeClass(level: ReturnType<typeof scoreLevel>): string {
  switch (level) {
    case "green":
      return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300";
    case "yellow":
      return "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300";
    case "red":
      return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300";
    default:
      return "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400";
  }
}

function fmtDate(s: string | null | undefined): string {
  if (!s) return "-";
  const d = new Date(s);
  if (isNaN(d.getTime())) return s;
  return d.toLocaleString("zh-CN", { hour12: false });
}

// ------------------------------------------------------------------
// 主组件
// ------------------------------------------------------------------
export function EvaluationDashboard() {
  const [debugUserId, setDebugUserId] = useState<number>(DEFAULT_UID);
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const flashSuccess = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 2500);
  };
  const flashError = (msg: string) => {
    setErrorMsg(msg);
    setTimeout(() => setErrorMsg(null), 4000);
  };

  return (
    <div className="space-y-5">
      {/* 顶部控制条 */}
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-xl">
                <Gauge className="h-6 w-6 text-brand-500" />
                Agent 评估中心 · Evaluation Dashboard
              </CardTitle>
              <CardDescription className="mt-1">
                多 Agent 输出质量评估、事实依据核查、RAG 质量度量与 Prompt 版本 A/B 对比。
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <label className="text-sm text-gray-500 dark:text-gray-400">调试用户ID:</label>
              <Input
                type="number"
                className="w-28"
                value={debugUserId}
                onChange={(e) => setDebugUserId(Number(e.target.value) || DEFAULT_UID)}
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex gap-2 flex-wrap">
            {TABS.map((t) => {
              const Icon = t.icon;
              const active = activeTab === t.key;
              return (
                <button
                  key={t.key}
                  onClick={() => setActiveTab(t.key)}
                  className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                    active
                      ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                      : "bg-gray-50 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {t.label}
                </button>
              );
            })}
          </div>

          {errorMsg && (
            <div className="mt-4 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-3 flex items-start gap-2">
              <AlertCircle className="h-5 w-5 text-red-600 dark:text-red-300 mt-0.5 shrink-0" />
              <div className="text-sm text-red-700 dark:text-red-200">{errorMsg}</div>
            </div>
          )}
          {successMsg && (
            <div className="mt-4 rounded-lg bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 p-3 flex items-start gap-2">
              <CheckCircle2 className="h-5 w-5 text-green-600 dark:text-green-300 mt-0.5 shrink-0" />
              <div className="text-sm text-green-700 dark:text-green-200">{successMsg}</div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Tab 内容 */}
      {activeTab === "overview" && (
        <OverviewTab userId={debugUserId} onError={flashError} />
      )}
      {activeTab === "manual" && (
        <ManualEvalTab
          userId={debugUserId}
          onSuccess={flashSuccess}
          onError={flashError}
        />
      )}
      {activeTab === "grounding" && (
        <GroundingTab
          userId={debugUserId}
          onSuccess={flashSuccess}
          onError={flashError}
        />
      )}
      {activeTab === "rag" && (
        <RagEvalTab
          userId={debugUserId}
          onSuccess={flashSuccess}
          onError={flashError}
        />
      )}
      {activeTab === "prompt" && (
        <PromptABTab
          userId={debugUserId}
          onSuccess={flashSuccess}
          onError={flashError}
        />
      )}
    </div>
  );
}

// ============================================================
// 1. 评估总览：排行 + 趋势 + 问题分析
// ============================================================
function OverviewTab({
  userId,
  onError,
}: {
  userId: number;
  onError: (msg: string) => void;
}) {
  const [ranking, setRanking] = useState<AgentScoreRanking[]>([]);
  const [trend, setTrend] = useState<ScoreTrendPoint[]>([]);
  const [issuesByAgent, setIssuesByAgent] = useState<Record<string, EvaluationIssue[]>>({});
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([
      getAgentScoreRanking(userId).catch((e) => {
        onError(e?.message || "排行加载失败");
        return [] as AgentScoreRanking[];
      }),
      getScoreTrend(userId).catch((e) => {
        onError(e?.message || "趋势加载失败");
        return [] as ScoreTrendPoint[];
      }),
    ])
      .then(async ([rank, tr]) => {
        setRanking(Array.isArray(rank) ? rank : []);
        setTrend(Array.isArray(tr) ? tr : []);
        // 基于排行中的 agentType 拉取每个 agent 的问题分析
        const agents = (Array.isArray(rank) ? rank : []).map((r) => r.agentType);
        const uniqueAgents = Array.from(new Set(agents));
        const issueEntries = await Promise.all(
          uniqueAgents.map((a) =>
            getEvaluationIssues(a, userId)
              .then(
                (issues): [string, EvaluationIssue[]] => [
                  a,
                  Array.isArray(issues) ? issues : [],
                ],
              )
              .catch((): [string, EvaluationIssue[]] => [a, []]),
          ),
        );
        const map: Record<string, EvaluationIssue[]> = {};
        issueEntries.forEach(([a, list]) => (map[a] = list));
        setIssuesByAgent(map);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const maxAvg = useMemo(() => {
    if (ranking.length === 0) return 100;
    return Math.max(100, ...ranking.map((r) => Number(r.avgScore) || 0));
  }, [ranking]);

  return (
    <div className="space-y-5">
      <div className="flex justify-end">
        <Button size="sm" variant="outline" onClick={load} disabled={loading}>
          {loading ? (
            <Loader2 className="h-4 w-4 animate-spin mr-1" />
          ) : (
            <RefreshCw className="h-4 w-4 mr-1" />
          )}
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Agent 质量排行 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Trophy className="h-5 w-5 text-yellow-500" />
              Agent 质量排行
            </CardTitle>
            <CardDescription>各 Agent 平均得分（绿 &gt;= 85, 黄 &gt;= 70, 红 &lt; 70）</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading && ranking.length === 0 ? (
              <LoadingHint />
            ) : ranking.length === 0 ? (
              <EmptyHint text="暂无评估数据，先去「手动评估」生成几条记录吧。" />
            ) : (
              ranking.map((r) => {
                const avg = Number(r.avgScore) || 0;
                const level = scoreLevel(avg);
                const widthPct = Math.min(100, (avg / maxAvg) * 100);
                return (
                  <div key={r.agentType} className="space-y-1">
                    <div className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="font-medium text-gray-900 dark:text-white truncate">
                          {r.agentType}
                        </span>
                        <Badge variant="outline" className="text-[10px]">
                          {r.count} 次
                        </Badge>
                      </div>
                      <Badge className={`text-[10px] ${scoreBadgeClass(level)}`}>
                        {avg.toFixed(1)}
                      </Badge>
                    </div>
                    <div className="h-2.5 w-full rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${scoreBarColor(level)}`}
                        style={{ width: `${widthPct}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>

        {/* 评分趋势 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <TrendingUp className="h-5 w-5 text-brand-500" />
              评分趋势
            </CardTitle>
            <CardDescription>最近 50 条评分记录的时间线</CardDescription>
          </CardHeader>
          <CardContent>
            {loading && trend.length === 0 ? (
              <LoadingHint />
            ) : trend.length === 0 ? (
              <EmptyHint text="暂无评分趋势数据。" />
            ) : (
              <ol className="relative border-l border-gray-200 dark:border-gray-700 ml-2 space-y-3 max-h-[360px] overflow-y-auto pr-1">
                {trend.slice(0, 50).map((p, i) => {
                  const level = scoreLevel(p.score);
                  return (
                    <li key={i} className="ml-4">
                      <span
                        className={`absolute -left-[9px] w-4 h-4 rounded-full ring-4 ring-white dark:ring-gray-950 ${scoreBarColor(level)}`}
                        aria-hidden
                      />
                      <div className="rounded-lg border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 p-2.5">
                        <div className="flex items-center justify-between gap-2">
                          <Badge variant="secondary" className="text-[10px]">
                            {p.agentType}
                          </Badge>
                          <Badge className={`text-[10px] ${scoreBadgeClass(level)}`}>
                            {p.score ?? "-"}
                          </Badge>
                        </div>
                        <div className="mt-1 text-[11px] text-gray-500 dark:text-gray-400">
                          {fmtDate(p.date)}
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 问题分析（按 agent 分组） */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <AlertTriangle className="h-5 w-5 text-amber-500" />
            问题分析（按 Agent 分组）
          </CardTitle>
          <CardDescription>每个 Agent 最近 20 条评估的反馈与扣分项</CardDescription>
        </CardHeader>
        <CardContent>
          {Object.keys(issuesByAgent).length === 0 ? (
            <EmptyHint text="暂无问题分析数据。" />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {Object.entries(issuesByAgent).map(([agent, issues]) => (
                <div
                  key={agent}
                  className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30 p-4"
                >
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <Badge variant="default">{agent}</Badge>
                      <span className="text-xs text-gray-500 dark:text-gray-400">
                        {issues.length} 条记录
                      </span>
                    </div>
                  </div>
                  {issues.length === 0 ? (
                    <p className="text-xs text-gray-400">暂无问题记录</p>
                  ) : (
                    <ul className="space-y-2 max-h-[260px] overflow-y-auto pr-1">
                      {issues.slice(0, 20).map((it) => {
                        const level = scoreLevel(it.score);
                        return (
                          <li
                            key={it.id}
                            className="rounded-lg bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 p-2.5"
                          >
                            <div className="flex items-center justify-between gap-2 mb-1">
                              <Badge className={`text-[10px] ${scoreBadgeClass(level)}`}>
                                {it.score ?? "-"}
                              </Badge>
                              <span className="text-[10px] text-gray-400">
                                {fmtDate(it.createdAt)}
                              </span>
                            </div>
                            <p className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words line-clamp-4">
                              {it.feedback || "（无反馈）"}
                            </p>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// ============================================================
// 2. 手动评估
// ============================================================
function ManualEvalTab({
  userId,
  onSuccess,
  onError,
}: {
  userId: number;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [agentType, setAgentType] = useState<string>(COMMON_AGENTS[0]);
  const [evaluationType, setEvaluationType] = useState<string>("MANUAL");
  const [input, setInput] = useState<string>("");
  const [output, setOutput] = useState<string>("");
  const [promptVersion, setPromptVersion] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [lastRecord, setLastRecord] = useState<{
    id: number;
    score: number | null;
    feedback: string | null;
  } | null>(null);

  const handleSubmit = async () => {
    if (!agentType.trim() || !output.trim()) {
      onError("agentType 与 output 必填");
      return;
    }
    setSubmitting(true);
    try {
      const rec = await createEvaluation({
        userId,
        agentType: agentType.trim(),
        evaluationType: evaluationType.trim() || undefined,
        input: input.trim() || undefined,
        output: output.trim(),
        promptVersion: promptVersion.trim() || undefined,
      });
      setLastRecord({
        id: rec?.id,
        score: rec?.score ?? null,
        feedback: rec?.feedback ?? null,
      });
      onSuccess(`评估完成，记录 ID=${rec?.id ?? "-"}`);
    } catch (e: any) {
      onError(e?.message || "评估失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <ClipboardCheck className="h-5 w-5 text-brand-500" />
          手动评估 Agent 输出
        </CardTitle>
        <CardDescription>
          提交一组 agentType / evaluationType / input / output，由 EvaluationAgent 打分并写入记录。
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
              Agent 类型 <span className="text-red-500">*</span>
            </label>
            <input
              list="eval-agent-types"
              className="w-full h-10 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
              value={agentType}
              onChange={(e) => setAgentType(e.target.value)}
              placeholder="如 CareerAgent"
            />
            <datalist id="eval-agent-types">
              {COMMON_AGENTS.map((a) => (
                <option key={a} value={a} />
              ))}
            </datalist>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
              评估类型 evaluationType
            </label>
            <select
              className="w-full h-10 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
              value={evaluationType}
              onChange={(e) => setEvaluationType(e.target.value)}
            >
              {EVAL_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            输入 input（可选）
          </label>
          <textarea
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            rows={3}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Agent 的原始输入内容..."
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            输出 output <span className="text-red-500">*</span>
          </label>
          <textarea
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            rows={5}
            value={output}
            onChange={(e) => setOutput(e.target.value)}
            placeholder="Agent 生成的输出内容..."
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            Prompt 版本（可选）
          </label>
          <Input
            value={promptVersion}
            onChange={(e) => setPromptVersion(e.target.value)}
            placeholder="如 v1.2"
          />
        </div>

        <div className="flex justify-end">
          <Button onClick={handleSubmit} disabled={submitting} className="gap-2">
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4" />
            )}
            {submitting ? "评估中..." : "提交评估"}
          </Button>
        </div>

        {lastRecord && (
          <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-800/40 p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-semibold text-gray-900 dark:text-white">
                最近一次评估结果 · #{lastRecord.id}
              </div>
              {lastRecord.score != null && (
                <Badge className={`text-[10px] ${scoreBadgeClass(scoreLevel(lastRecord.score))}`}>
                  {lastRecord.score}
                </Badge>
              )}
            </div>
            <p className="text-xs text-gray-600 dark:text-gray-300 whitespace-pre-wrap break-words">
              {lastRecord.feedback || "（无反馈）"}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================
// 3. Grounding 事实依据核查
// ============================================================
function GroundingTab({
  userId,
  onSuccess,
  onError,
}: {
  userId: number;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [answer, setAnswer] = useState<string>("");
  const [memoryContext, setMemoryContext] = useState<string>("");
  const [ragContext, setRagContext] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<GroundingResult | null>(null);

  const handleSubmit = async () => {
    if (!answer.trim()) {
      onError("answer 必填");
      return;
    }
    setSubmitting(true);
    try {
      const res = await checkGrounding({
        userId,
        answer: answer.trim(),
        memoryContext: memoryContext.trim() || undefined,
        ragContext: ragContext.trim() || undefined,
      });
      setResult(res);
      onSuccess(
        res?.grounded
          ? `核查通过，置信度 ${(res?.confidence ?? 0).toFixed(2)}`
          : "核查未通过，发现无据断言",
      );
    } catch (e: any) {
      onError(e?.message || "核查失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <ShieldCheck className="h-5 w-5 text-brand-500" />
          Grounding 事实依据核查
        </CardTitle>
        <CardDescription>
          检查 Agent 输出中的事实性陈述是否在 Memory / RAG 上下文中有据可查（幻觉检测）。
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            待核查回答 answer <span className="text-red-500">*</span>
          </label>
          <textarea
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            rows={4}
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="Agent 生成的回答..."
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
              Memory 上下文（可选）
            </label>
            <textarea
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
              rows={5}
              value={memoryContext}
              onChange={(e) => setMemoryContext(e.target.value)}
              placeholder="用户长期记忆上下文..."
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
              RAG 上下文（可选）
            </label>
            <textarea
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
              rows={5}
              value={ragContext}
              onChange={(e) => setRagContext(e.target.value)}
              placeholder="RAG 检索到的上下文..."
            />
          </div>
        </div>

        <div className="flex justify-end">
          <Button onClick={handleSubmit} disabled={submitting} className="gap-2">
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ShieldCheck className="h-4 w-4" />
            )}
            {submitting ? "核查中..." : "执行核查"}
          </Button>
        </div>

        {result && <GroundingResultView result={result} />}
      </CardContent>
    </Card>
  );
}

function GroundingResultView({ result }: { result: GroundingResult }) {
  const passed = result.grounded;
  return (
    <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-800/40 p-4 space-y-3">
      <div className="flex items-center gap-2">
        {passed ? (
          <CheckCircle2 className="h-5 w-5 text-green-500" />
        ) : (
          <XCircle className="h-5 w-5 text-red-500" />
        )}
        <span className="text-sm font-semibold text-gray-900 dark:text-white">
          {passed ? "全部事实有据可查" : "存在无据断言（幻觉）"}
        </span>
        <Badge variant="outline" className="text-[10px] ml-auto">
          confidence {(result.confidence ?? 0).toFixed(2)}
        </Badge>
      </div>
      <div>
        <div className="text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
          无据断言列表
        </div>
        {result.unsupportedClaims && result.unsupportedClaims.length > 0 ? (
          <ul className="space-y-1">
            {result.unsupportedClaims.map((c, i) => (
              <li
                key={i}
                className="text-xs text-gray-700 dark:text-gray-300 flex items-start gap-1.5"
              >
                <ChevronRight className="h-3 w-3 text-gray-400 mt-0.5 shrink-0" />
                <span>{c}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-gray-400">无</p>
        )}
      </div>
    </div>
  );
}

// ============================================================
// 4. RAG 评估
// ============================================================
function RagEvalTab({
  userId,
  onSuccess,
  onError,
}: {
  userId: number;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [question, setQuestion] = useState<string>("");
  const [retrievedContext, setRetrievedContext] = useState<string>("");
  const [answer, setAnswer] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<RAGMetrics | null>(null);

  const handleSubmit = async () => {
    if (!question.trim() || !retrievedContext.trim() || !answer.trim()) {
      onError("question / retrievedContext / answer 均必填");
      return;
    }
    setSubmitting(true);
    try {
      const res = await evaluateRAG({
        userId,
        question: question.trim(),
        retrievedContext: retrievedContext.trim(),
        answer: answer.trim(),
      });
      setResult(res);
      onSuccess(`RAG 评估完成，综合分 ${((res?.overallScore ?? 0)).toFixed(1)}`);
    } catch (e: any) {
      onError(e?.message || "RAG 评估失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Database className="h-5 w-5 text-brand-500" />
          RAG 质量评估
        </CardTitle>
        <CardDescription>
          端到端评估 RAG 流水线：上下文召回率 / 精确率 / 忠实度，综合分 = 三者均值。
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            用户问题 question <span className="text-red-500">*</span>
          </label>
          <Input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="用户原始问题"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            检索上下文 retrievedContext <span className="text-red-500">*</span>
          </label>
          <textarea
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            rows={5}
            value={retrievedContext}
            onChange={(e) => setRetrievedContext(e.target.value)}
            placeholder="RAG 检索到的文档片段..."
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            生成回答 answer <span className="text-red-500">*</span>
          </label>
          <textarea
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            rows={5}
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="RAGAgent 生成的回答..."
          />
        </div>

        <div className="flex justify-end">
          <Button onClick={handleSubmit} disabled={submitting} className="gap-2">
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Database className="h-4 w-4" />
            )}
            {submitting ? "评估中..." : "执行评估"}
          </Button>
        </div>

        {result && <RagResultView result={result} />}
      </CardContent>
    </Card>
  );
}

function RagResultView({ result }: { result: RAGMetrics }) {
  const metrics: { label: string; value: number | null }[] = [
    { label: "上下文召回", value: result.contextRecall ?? null },
    { label: "上下文精确", value: result.contextPrecision ?? null },
    { label: "忠实度", value: result.faithfulness ?? null },
  ];
  const overall = result.overallScore ?? 0;
  return (
    <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-800/40 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div className="text-sm font-semibold text-gray-900 dark:text-white">RAG 评估指标</div>
        <Badge className={`text-[10px] ${scoreBadgeClass(scoreLevel(overall))}`}>
          综合 {overall.toFixed(1)}
        </Badge>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {metrics.map((m) => {
          const level = scoreLevel(m.value);
          const v = m.value ?? 0;
          return (
            <div
              key={m.label}
              className="rounded-lg bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 p-3"
            >
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs text-gray-600 dark:text-gray-300">{m.label}</span>
                <Badge className={`text-[10px] ${scoreBadgeClass(level)}`}>{m.value ?? "-"}</Badge>
              </div>
              <div className="h-2 w-full rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                <div
                  className={`h-full rounded-full ${scoreBarColor(level)}`}
                  style={{ width: `${Math.min(100, v)}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
      {result.issues && result.issues.length > 0 && (
        <div>
          <div className="text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
            发现的问题
          </div>
          <ul className="space-y-1">
            {result.issues.map((c, i) => (
              <li
                key={i}
                className="text-xs text-gray-700 dark:text-gray-300 flex items-start gap-1.5"
              >
                <AlertTriangle className="h-3 w-3 text-amber-500 mt-0.5 shrink-0" />
                <span>{c}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

// ============================================================
// 5. Prompt A/B 测试
// ============================================================
function PromptABTab({
  userId,
  onSuccess,
  onError,
}: {
  userId: number;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [agentType, setAgentType] = useState<string>(COMMON_AGENTS[0]);
  const [versions, setVersions] = useState<PromptVersion[]>([]);
  const [comparison, setComparison] = useState<PromptVersionComparison[]>([]);
  const [loading, setLoading] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  // 新建版本表单
  const [showCreate, setShowCreate] = useState(false);
  const [newVersion, setNewVersion] = useState<string>("");
  const [newPrompt, setNewPrompt] = useState<string>("");
  const [newDesc, setNewDesc] = useState<string>("");
  const [creating, setCreating] = useState(false);

  const load = () => {
    if (!agentType.trim()) return;
    setLoading(true);
    Promise.all([
      listPromptVersions(agentType.trim()).catch((e) => {
        onError(e?.message || "版本列表加载失败");
        return [] as PromptVersion[];
      }),
      comparePromptVersions(agentType.trim(), userId).catch((e) => {
        onError(e?.message || "对比数据加载失败");
        return [] as PromptVersionComparison[];
      }),
    ])
      .then(([v, c]) => {
        setVersions(Array.isArray(v) ? v : []);
        setComparison(Array.isArray(c) ? c : []);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentType, userId]);

  const handleCreate = async () => {
    if (!agentType.trim() || !newVersion.trim() || !newPrompt.trim()) {
      onError("agentType / version / promptContent 必填");
      return;
    }
    setCreating(true);
    try {
      await createPromptVersion({
        agentType: agentType.trim(),
        version: newVersion.trim(),
        promptContent: newPrompt.trim(),
        description: newDesc.trim() || undefined,
        enabled: false,
      });
      onSuccess(`Prompt 版本 ${newVersion.trim()} 创建成功`);
      setNewVersion("");
      setNewPrompt("");
      setNewDesc("");
      setShowCreate(false);
      load();
    } catch (e: any) {
      onError(e?.message || "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const handleEnable = async (id: number) => {
    setTogglingId(id);
    try {
      await enablePromptVersion(id);
      onSuccess("已切换启用版本");
      load();
    } catch (e: any) {
      onError(e?.message || "启用失败");
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <div className="space-y-5">
      {/* 顶部：选择 agent + 刷新 */}
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-lg">
                <GitCompare className="h-5 w-5 text-brand-500" />
                Prompt 版本 A/B 测试
              </CardTitle>
              <CardDescription>
                管理每个 Agent 的 Prompt 版本，启用/停用版本，并对比各版本平均得分。
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <input
                list="prompt-agent-types"
                className="h-10 w-48 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                value={agentType}
                onChange={(e) => setAgentType(e.target.value)}
                placeholder="选择 Agent"
              />
              <datalist id="prompt-agent-types">
                {COMMON_AGENTS.map((a) => (
                  <option key={a} value={a} />
                ))}
              </datalist>
              <Button size="sm" variant="outline" onClick={load} disabled={loading}>
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin mr-1" />
                ) : (
                  <RefreshCw className="h-4 w-4 mr-1" />
                )}
                刷新
              </Button>
              <Button size="sm" variant="outline" onClick={() => setShowCreate((v) => !v)}>
                <Plus className="h-4 w-4 mr-1" />
                新建版本
              </Button>
            </div>
          </div>
        </CardHeader>
      </Card>

      {/* 新建版本表单 */}
      {showCreate && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">创建新 Prompt 版本</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  版本号 version <span className="text-red-500">*</span>
                </label>
                <Input
                  value={newVersion}
                  onChange={(e) => setNewVersion(e.target.value)}
                  placeholder="如 v1.3"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  描述 description
                </label>
                <Input
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="本版本改动说明"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                Prompt 内容 promptContent <span className="text-red-500">*</span>
              </label>
              <textarea
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                rows={6}
                value={newPrompt}
                onChange={(e) => setNewPrompt(e.target.value)}
                placeholder="完整 Prompt 文本..."
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setShowCreate(false)}>
                取消
              </Button>
              <Button onClick={handleCreate} disabled={creating} className="gap-2">
                {creating ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Plus className="h-4 w-4" />
                )}
                {creating ? "创建中..." : "创建版本"}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* 版本列表 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">版本列表（{agentType}）</CardTitle>
            <CardDescription>同一 Agent 同一时刻仅一个版本被启用</CardDescription>
          </CardHeader>
          <CardContent>
            {loading && versions.length === 0 ? (
              <LoadingHint />
            ) : versions.length === 0 ? (
              <EmptyHint text="暂无 Prompt 版本，点击「新建版本」创建。" />
            ) : (
              <ul className="space-y-2">
                {versions.map((v) => (
                  <li
                    key={v.id}
                    className="rounded-xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 p-3"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-semibold text-gray-900 dark:text-white">
                            {v.version}
                          </span>
                          {v.enabled ? (
                            <Badge variant="success" className="text-[10px]">
                              启用中
                            </Badge>
                          ) : (
                            <Badge variant="secondary" className="text-[10px]">
                              未启用
                            </Badge>
                          )}
                          {v.avgScore != null && (
                            <Badge
                              className={`text-[10px] ${scoreBadgeClass(scoreLevel(v.avgScore))}`}
                            >
                              均分 {v.avgScore.toFixed(1)}
                            </Badge>
                          )}
                          {v.evalCount != null && (
                            <Badge variant="outline" className="text-[10px]">
                              {v.evalCount} 次
                            </Badge>
                          )}
                        </div>
                        {v.description && (
                          <p className="mt-1 text-xs text-gray-600 dark:text-gray-400 line-clamp-2">
                            {v.description}
                          </p>
                        )}
                        <p className="mt-1 text-[10px] text-gray-400">
                          {fmtDate(v.createdAt)}
                        </p>
                      </div>
                      <Button
                        size="sm"
                        variant={v.enabled ? "outline" : "default"}
                        disabled={v.enabled || togglingId === v.id}
                        onClick={() => handleEnable(v.id)}
                        className="shrink-0"
                      >
                        {togglingId === v.id && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />}
                        {v.enabled ? "已启用" : "启用"}
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* A/B 对比 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">A/B 得分对比</CardTitle>
            <CardDescription>各 Prompt 版本的平均评估得分</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading && comparison.length === 0 ? (
              <LoadingHint />
            ) : comparison.length === 0 ? (
              <EmptyHint text="暂无对比数据，需对相应版本产生评估记录。" />
            ) : (
              comparison.map((c) => {
                const avg = Number(c.avgScore) || 0;
                const level = scoreLevel(avg);
                return (
                  <div key={c.version} className="space-y-1">
                    <div className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-gray-900 dark:text-white">
                          {c.version}
                        </span>
                        <Badge variant="outline" className="text-[10px]">
                          {c.count} 次
                        </Badge>
                      </div>
                      <Badge className={`text-[10px] ${scoreBadgeClass(level)}`}>
                        {avg.toFixed(1)}
                      </Badge>
                    </div>
                    <div className="h-2.5 w-full rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${scoreBarColor(level)}`}
                        style={{ width: `${Math.min(100, avg)}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>

      {/* 版本内容预览 + 删除评估记录入口 */}
      {versions.length > 0 && <PromptContentPreview versions={versions} />}
    </div>
  );
}

function PromptContentPreview({ versions }: { versions: PromptVersion[] }) {
  const [openId, setOpenId] = useState<number | null>(null);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Prompt 内容预览</CardTitle>
        <CardDescription>点击展开查看各版本完整 Prompt 文本</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {versions.map((v) => (
          <div
            key={v.id}
            className="rounded-xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 overflow-hidden"
          >
            <button
              className="w-full flex items-center justify-between px-3 py-2 text-left hover:bg-gray-50 dark:hover:bg-gray-800/50"
              onClick={() => setOpenId(openId === v.id ? null : v.id)}
            >
              <span className="text-sm font-medium text-gray-900 dark:text-white">
                {v.version}
                {v.enabled && (
                  <Badge variant="success" className="ml-2 text-[10px]">
                    启用中
                  </Badge>
                )}
              </span>
              <ChevronRight
                className={`h-4 w-4 text-gray-400 transition-transform ${
                  openId === v.id ? "rotate-90" : ""
                }`}
              />
            </button>
            {openId === v.id && (
              <pre className="px-3 pb-3 text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words bg-gray-50 dark:bg-gray-800/40 rounded-b-xl">
                {v.promptContent || "（空）"}
              </pre>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

// ============================================================
// 通用子组件
// ============================================================
function LoadingHint() {
  return (
    <div className="flex items-center justify-center py-8 text-sm text-gray-400">
      <Loader2 className="h-4 w-4 animate-spin mr-2" />
      加载中...
    </div>
  );
}

function EmptyHint({ text }: { text: string }) {
  return (
    <div className="rounded-xl border border-dashed border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-8 text-center">
      <Gauge className="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600 mb-2" />
      <p className="text-sm text-gray-500 dark:text-gray-400">{text}</p>
    </div>
  );
}
