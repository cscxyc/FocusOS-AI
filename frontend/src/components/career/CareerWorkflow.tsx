"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  Target,
  Sparkles,
  Loader2,
  CheckCircle2,
  XCircle,
  AlertCircle,
  TrendingUp,
  Wand2,
  GraduationCap,
  MessageSquare,
  FileText,
  ChevronDown,
  ChevronRight,
  Clock,
} from "lucide-react";
import api from "@/lib/api";
import { getToken } from "@/lib/auth";

// ============ Types ============
interface WorkflowEvent {
  event: string;
  workflowId: string;
  taskId?: number;
  taskType?: string;
  agentType?: string;
  task?: string;
  status?: string;
  progress?: number;
  message?: string;
  durationMs?: number;
  summary?: string;
  totalTasks?: number;
  completedTasks?: number;
}

interface CareerReport {
  id: number;
  userId: number;
  workflowId: string;
  jobTitle: string;
  company: string;
  jobDescription: string;
  matchScore: number;
  candidateProfile: string;
  advantages: string; // JSON array string
  gaps: string; // JSON array string
  resumeSuggestions: string; // JSON string
  learningPlan: string;
  interviewQuestions: string; // JSON string
  profileSufficient: boolean;
  overallRecommendation: string;
  createdAt: string;
}

interface TaskProgress {
  taskType?: string;
  task?: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
  message?: string;
}

const TASK_LABELS: Record<string, string> = {
  CONTEXT_INIT: "检索个人知识库",
  CAREER_ANALYSIS: "岗位匹配分析",
  RESUME_OPTIMIZATION: "简历优化（STAR）",
  SKILL_GAP_ANALYSIS: "技能差距分析",
  LEARNING_PLAN: "学习计划生成",
  INTERVIEW_PREPARATION: "面试题生成",
  MOCK_INTERVIEW: "模拟面试会话初始化",
};

// ============ Main Component ============
export function CareerWorkflow() {
  const [jdText, setJdText] = React.useState("");
  const [jobTitle, setJobTitle] = React.useState("");
  const [company, setCompany] = React.useState("");
  const [isStarting, setIsStarting] = React.useState(false);
  const [workflowId, setWorkflowId] = React.useState<string | null>(null);
  const [progress, setProgress] = React.useState(0);
  const [status, setStatus] = React.useState<"IDLE" | "RUNNING" | "SUCCESS" | "FAILED">("IDLE");
  const [tasks, setTasks] = React.useState<TaskProgress[]>([]);
  const [report, setReport] = React.useState<CareerReport | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const eventSourceRef = React.useRef<EventSource | null>(null);

  React.useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  const startAnalysis = async () => {
    if (!jdText.trim()) return;
    setIsStarting(true);
    setError(null);
    setReport(null);
    setTasks([]);
    setProgress(0);
    setStatus("IDLE");

    try {
      const res: any = await api.post("/api/career/analyze-workflow", {
        jobDescription: jdText,
        jobTitle: jobTitle || undefined,
        company: company || undefined,
      });

      const wfId = res.workflowId;
      setWorkflowId(wfId);
      setStatus("RUNNING");
      setIsStarting(false);
      subscribeSSE(wfId);
    } catch (e: any) {
      setError(e.message || "启动分析失败");
      setIsStarting(false);
    }
  };

  const subscribeSSE = (wfId: string) => {
    eventSourceRef.current?.close();
    const token = getToken();
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    const url = `${baseUrl}/api/workflow/${wfId}/events?token=${encodeURIComponent(token || "")}`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    const handleEvent = (e: MessageEvent) => {
      try {
        const data: WorkflowEvent = JSON.parse(e.data);
        handleWorkflowEvent(data, wfId);
      } catch (err) {
        console.error("Parse SSE event failed:", err);
      }
    };

    es.addEventListener("workflow_started", handleEvent);
    es.addEventListener("task_started", handleEvent);
    es.addEventListener("task_completed", handleEvent);
    es.addEventListener("workflow_completed", handleEvent);
    es.addEventListener("workflow_failed", handleEvent);
    es.onerror = () => {
      es.close();
    };
  };

  const handleWorkflowEvent = (event: WorkflowEvent, wfId: string) => {
    if (event.progress) setProgress(event.progress);

    if (event.event === "task_started" || event.event === "task_completed") {
      const taskProgress: TaskProgress = {
        taskType: event.taskType,
        task: event.task,
        status: event.event === "task_started" ? "RUNNING" : (event.status as any) || "SUCCESS",
        message: event.message,
      };
      setTasks((prev) => {
        const idx = prev.findIndex((t) => t.taskType === event.taskType);
        if (idx >= 0) {
          const next = [...prev];
          next[idx] = taskProgress;
          return next;
        }
        return [...prev, taskProgress];
      });
    }

    if (event.event === "workflow_completed") {
      setStatus(event.status === "PARTIAL" ? "SUCCESS" : (event.status as any) || "SUCCESS");
      setProgress(100);
      eventSourceRef.current?.close();
      // Fetch the report
      setTimeout(() => fetchReport(wfId), 1000);
    }

    if (event.event === "workflow_failed") {
      setStatus("FAILED");
      setError(event.message || "工作流执行失败");
      eventSourceRef.current?.close();
    }
  };

  const fetchReport = async (wfId: string) => {
    try {
      const res: any = await api.get(`/api/career/reports/by-workflow/${wfId}`);
      setReport(res);
    } catch (e: any) {
      setError("获取报告失败: " + (e.message || "未知错误"));
    }
  };

  const reset = () => {
    setReport(null);
    setWorkflowId(null);
    setProgress(0);
    setStatus("IDLE");
    setTasks([]);
    setError(null);
  };

  // ============ Render ============
  if (report) {
    return <ReportView report={report} onReset={reset} />;
  }

  return (
    <div className="space-y-4">
      {/* JD Input */}
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
              <Target className="h-5 w-5 text-white" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">AI Career Assistant</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Sprint 7-A · JD → 匹配分析 → 简历优化 → 学习计划 → 面试准备
              </p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 mb-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">岗位名称</label>
              <input
                type="text"
                value={jobTitle}
                onChange={(e) => setJobTitle(e.target.value)}
                placeholder="如：AI应用开发工程师"
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
                disabled={isStarting || status === "RUNNING"}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">公司（可选）</label>
              <input
                type="text"
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                placeholder="如：字节跳动"
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
                disabled={isStarting || status === "RUNNING"}
              />
            </div>
          </div>

          <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">职位描述（JD）</label>
          <textarea
            value={jdText}
            onChange={(e) => setJdText(e.target.value)}
            placeholder="粘贴完整的职位描述到此..."
            rows={8}
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-brand-500"
            disabled={isStarting || status === "RUNNING"}
          />

          <Button
            onClick={startAnalysis}
            disabled={isStarting || !jdText.trim() || status === "RUNNING"}
            className="w-full mt-4"
          >
            {isStarting || status === "RUNNING" ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="mr-2 h-4 w-4" />
            )}
            {isStarting ? "启动中..." : status === "RUNNING" ? "分析中..." : "开始 Career 分析"}
          </Button>

          {error && (
            <div className="mt-4 p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
              <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Progress */}
      {status === "RUNNING" && (
        <Card className="border-brand-200 dark:border-brand-800">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Loader2 className="h-5 w-5 text-brand-500 animate-spin" />
                <span className="text-sm font-medium text-gray-900 dark:text-white">
                  Career Workflow 执行中
                </span>
              </div>
              <Badge variant="secondary">{progress}%</Badge>
            </div>

            <div className="w-full h-2.5 bg-gray-200 dark:bg-gray-800 rounded-full overflow-hidden mb-4">
              <div
                className="h-full bg-gradient-to-r from-brand-500 to-accent-500 transition-all duration-500"
                style={{ width: `${progress}%` }}
              />
            </div>

            <div className="space-y-2">
              {tasks.map((task, i) => (
                <div
                  key={i}
                  className={`flex items-center gap-3 p-2.5 rounded-lg border ${
                    task.status === "SUCCESS"
                      ? "border-green-200 bg-green-50/50 dark:border-green-900 dark:bg-green-950/20"
                      : task.status === "FAILED"
                      ? "border-red-200 bg-red-50/50 dark:border-red-900 dark:bg-red-950/20"
                      : "border-brand-300 bg-brand-50/50 dark:border-brand-800 dark:bg-brand-950/20"
                  }`}
                >
                  {task.status === "SUCCESS" ? (
                    <CheckCircle2 className="h-4 w-4 text-green-500 shrink-0" />
                  ) : task.status === "FAILED" ? (
                    <XCircle className="h-4 w-4 text-red-500 shrink-0" />
                  ) : (
                    <Loader2 className="h-4 w-4 text-brand-500 animate-spin shrink-0" />
                  )}
                  <span className="text-sm text-gray-700 dark:text-gray-300 flex-1">
                    {TASK_LABELS[task.taskType || ""] || task.task || task.taskType}
                  </span>
                  {task.message && (
                    <span className="text-xs text-gray-400 truncate max-w-xs">{task.message}</span>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ============ Report View ============
function ReportView({ report, onReset }: { report: CareerReport; onReset: () => void }) {
  const [expandedSections, setExpandedSections] = React.useState<Set<string>>(
    new Set(["overview", "resume"])
  );

  const toggle = (section: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });
  };

  const advantages: string[] = safeParseArray(report.advantages);
  const gaps: string[] = safeParseArray(report.gaps);
  const resumeSuggestions = safeParse(report.resumeSuggestions);
  const interviewQuestions = safeParse(report.interviewQuestions);

  const scoreColor =
    report.matchScore >= 80
      ? "from-green-500 to-emerald-500"
      : report.matchScore >= 60
      ? "from-brand-500 to-accent-500"
      : report.matchScore >= 40
      ? "from-yellow-500 to-orange-500"
      : "from-red-500 to-rose-500";

  const scoreLabel =
    report.matchScore >= 80
      ? "高度匹配"
      : report.matchScore >= 60
      ? "良好匹配"
      : report.matchScore >= 40
      ? "部分匹配"
      : "需要提升";

  return (
    <div className="space-y-4">
      {/* Header */}
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-lg font-bold text-gray-900 dark:text-white">
                Career 分析报告
              </h3>
              <p className="text-sm text-gray-500 mt-1">
                {report.jobTitle || "目标岗位"}
                {report.company ? ` · ${report.company}` : ""}
                {" · "}
                {new Date(report.createdAt).toLocaleString("zh-CN")}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={onReset}>
              新分析
            </Button>
          </div>

          {!report.profileSufficient && (
            <div className="mb-4 p-3 rounded-xl bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-yellow-500 shrink-0 mt-0.5" />
              <p className="text-sm text-yellow-700 dark:text-yellow-300">
                个人知识库资料不足，建议补充简历、项目文档、实习证明等资料以获得更精准的分析。
              </p>
            </div>
          )}

          {/* Match Score */}
          <div className={`flex items-center justify-center p-6 rounded-xl bg-gradient-to-br ${scoreColor} bg-opacity-10`}>
            <div className="text-center text-white">
              <span className="text-6xl font-bold">{report.matchScore ?? 0}%</span>
              <p className="text-sm mt-1 opacity-90">{scoreLabel}</p>
            </div>
          </div>

          {report.candidateProfile && (
            <div className="mt-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-800">
              <p className="text-sm text-gray-700 dark:text-gray-300">
                <span className="font-medium">候选人画像：</span>
                {report.candidateProfile}
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Advantages & Gaps */}
      <Card>
        <CardContent className="p-6">
          <SectionHeader
            title="匹配分析"
            icon={<TrendingUp className="h-4 w-4" />}
            expanded={expandedSections.has("overview")}
            onClick={() => toggle("overview")}
          />
          {expandedSections.has("overview") && (
            <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <h4 className="text-sm font-semibold text-green-600 dark:text-green-400 mb-2 flex items-center gap-1">
                  <CheckCircle2 className="h-4 w-4" /> 优势
                </h4>
                {advantages.length > 0 ? (
                  <ul className="space-y-2">
                    {advantages.map((a, i) => (
                      <li key={i} className="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                        <span className="text-green-500 shrink-0">•</span>
                        {a}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-gray-400">暂无优势数据</p>
                )}
              </div>
              <div>
                <h4 className="text-sm font-semibold text-red-600 dark:text-red-400 mb-2 flex items-center gap-1">
                  <XCircle className="h-4 w-4" /> 不足
                </h4>
                {gaps.length > 0 ? (
                  <ul className="space-y-2">
                    {gaps.map((g, i) => (
                      <li key={i} className="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                        <span className="text-red-500 shrink-0">•</span>
                        {g}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-gray-400">暂无差距数据</p>
                )}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Resume Optimization */}
      {resumeSuggestions && (
        <Card>
          <CardContent className="p-6">
            <SectionHeader
              title="简历优化（STAR 原则）"
              icon={<Wand2 className="h-4 w-4" />}
              expanded={expandedSections.has("resume")}
              onClick={() => toggle("resume")}
            />
            {expandedSections.has("resume") && (
              <div className="mt-4 space-y-4">
                {resumeSuggestions.summaryOptimization && (
                  <div className="p-3 rounded-xl bg-brand-50 dark:bg-brand-900/20 border border-brand-200 dark:border-brand-800">
                    <p className="text-xs font-medium text-brand-600 dark:text-brand-400 mb-1">摘要优化</p>
                    <p className="text-sm text-gray-700 dark:text-gray-300">{resumeSuggestions.summaryOptimization}</p>
                  </div>
                )}

                {resumeSuggestions.experienceOptimization?.map((item: any, i: number) => (
                  <OptimizationItem key={`exp-${i}`} item={item} label="经历优化" />
                ))}

                {resumeSuggestions.projectOptimization?.map((item: any, i: number) => (
                  <OptimizationItem key={`proj-${i}`} item={item} label="项目优化" />
                ))}

                {resumeSuggestions.keywordsToAdd?.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-gray-600 dark:text-gray-400 mb-2">建议添加关键词</p>
                    <div className="flex flex-wrap gap-2">
                      {resumeSuggestions.keywordsToAdd.map((kw: string, i: number) => (
                        <Badge key={i} variant="secondary" className="text-xs">{kw}</Badge>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Learning Plan */}
      {report.learningPlan && (
        <Card>
          <CardContent className="p-6">
            <SectionHeader
              title="学习计划（12 周提升路线）"
              icon={<GraduationCap className="h-4 w-4" />}
              expanded={expandedSections.has("learning")}
              onClick={() => toggle("learning")}
            />
            {expandedSections.has("learning") && (
              <div className="mt-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-800">
                <pre className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-sans max-h-96 overflow-y-auto">
                  {report.learningPlan}
                </pre>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Interview Preparation */}
      {interviewQuestions && (
        <Card>
          <CardContent className="p-6">
            <SectionHeader
              title="面试准备"
              icon={<MessageSquare className="h-4 w-4" />}
              expanded={expandedSections.has("interview")}
              onClick={() => toggle("interview")}
            />
            {expandedSections.has("interview") && (
              <div className="mt-4 space-y-4">
                {interviewQuestions.selfIntroduction && (
                  <div className="p-3 rounded-xl bg-brand-50 dark:bg-brand-900/20 border border-brand-200 dark:border-brand-800">
                    <p className="text-xs font-medium text-brand-600 dark:text-brand-400 mb-1">自我介绍模板</p>
                    <p className="text-sm text-gray-700 dark:text-gray-300">{interviewQuestions.selfIntroduction}</p>
                  </div>
                )}

                {interviewQuestions.technicalQuestions?.length > 0 && (
                  <QuestionList
                    title="技术面试题"
                    questions={interviewQuestions.technicalQuestions.map((q: any) => ({
                      question: q.question,
                      detail: q.keyPoints?.join("；"),
                      tag: q.relatedExperience,
                    }))}
                  />
                )}

                {interviewQuestions.behavioralQuestions?.length > 0 && (
                  <QuestionList
                    title="行为面试题"
                    questions={interviewQuestions.behavioralQuestions.map((q: any) => ({
                      question: q.question,
                      detail: q.starFramework,
                    }))}
                  />
                )}

                {interviewQuestions.projectDeepDive?.length > 0 && (
                  <QuestionList
                    title="项目深挖"
                    questions={interviewQuestions.projectDeepDive.map((q: any) => ({
                      question: q.project,
                      detail: q.preparationTips,
                      tag: q.likelyQuestions?.join("；"),
                    }))}
                  />
                )}

                {interviewQuestions.weaknessResponses?.length > 0 && (
                  <div>
                    <p className="text-sm font-semibold text-gray-900 dark:text-white mb-2">弱点应对</p>
                    {interviewQuestions.weaknessResponses.map((w: any, i: number) => (
                      <div key={i} className="p-3 rounded-lg border border-gray-200 dark:border-gray-700 mb-2">
                        <p className="text-sm font-medium text-red-600 dark:text-red-400">{w.weakness}</p>
                        <p className="text-sm text-gray-700 dark:text-gray-300 mt-1">{w.response}</p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Overall Recommendation */}
      {report.overallRecommendation && (
        <Card>
          <CardContent className="p-6">
            <div className="p-4 rounded-xl bg-gradient-to-br from-brand-50 to-accent-50 dark:from-brand-950/30 dark:to-accent-950/30 border border-brand-200 dark:border-brand-800">
              <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-2 flex items-center gap-2">
                <Sparkles className="h-4 w-4" />
                整体建议
              </h4>
              <div className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">
                {report.overallRecommendation}
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ============ Sub Components ============
function SectionHeader({
  title,
  icon,
  expanded,
  onClick,
}: {
  title: string;
  icon: React.ReactNode;
  expanded: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="flex items-center gap-2 w-full text-left group"
    >
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-100 dark:bg-brand-900/30 text-brand-600 dark:text-brand-400">
        {icon}
      </div>
      <h3 className="font-semibold text-gray-900 dark:text-white flex-1">{title}</h3>
      {expanded ? (
        <ChevronDown className="h-4 w-4 text-gray-400" />
      ) : (
        <ChevronRight className="h-4 w-4 text-gray-400" />
      )}
    </button>
  );
}

function OptimizationItem({ item, label }: { item: any; label: string }) {
  return (
    <div className="p-3 rounded-xl border border-gray-200 dark:border-gray-700">
      <div className="flex items-center gap-2 mb-2">
        <Badge variant="secondary" className="text-[10px]">{label}</Badge>
      </div>
      <div className="space-y-2">
        <div>
          <p className="text-xs text-gray-400 mb-0.5">原描述</p>
          <p className="text-sm text-gray-600 dark:text-gray-400 line-through opacity-70">{item.original}</p>
        </div>
        <div>
          <p className="text-xs text-brand-500 mb-0.5">AI 优化</p>
          <p className="text-sm text-gray-900 dark:text-gray-100">{item.optimized}</p>
        </div>
        {item.reason && (
          <div>
            <p className="text-xs text-gray-400 mb-0.5">修改原因</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 italic">{item.reason}</p>
          </div>
        )}
      </div>
    </div>
  );
}

function QuestionList({
  title,
  questions,
}: {
  title: string;
  questions: { question: string; detail?: string; tag?: string }[];
}) {
  return (
    <div>
      <p className="text-sm font-semibold text-gray-900 dark:text-white mb-2">{title}</p>
      <div className="space-y-2">
        {questions.map((q, i) => (
          <div key={i} className="p-3 rounded-lg border border-gray-200 dark:border-gray-700">
            <p className="text-sm font-medium text-gray-900 dark:text-white">{q.question}</p>
            {q.detail && <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{q.detail}</p>}
            {q.tag && (
              <p className="text-xs text-brand-500 mt-1">📎 {q.tag}</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ============ Utils ============
function safeParseArray(str: string): string[] {
  try {
    const parsed = JSON.parse(str);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function safeParse(str: string): any {
  if (!str) return null;
  try {
    return JSON.parse(str);
  } catch {
    return null;
  }
}
