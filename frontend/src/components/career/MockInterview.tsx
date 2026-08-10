"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  MessageSquare,
  Sparkles,
  Send,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  ListChecks,
  Trophy,
  TrendingUp,
  RotateCcw,
} from "lucide-react";
import api from "@/lib/api";

// ============ 类型定义 ============
interface InterviewQuestion {
  type: string;
  category?: string;
  question: string;
  difficulty?: string;
  expectedAnswer?: string;
  userProjectReference?: string;
  followUpQuestions?: string[];
}

interface ConversationEntry {
  question: string;
  expectedAnswer?: string;
  userProjectReference?: string;
  userAnswer: string;
  evaluation: string;
}

interface Evaluation {
  score: number;
  strengths: string[];
  weaknesses: string[];
  improvement: string[];
  factCheck?: {
    fabricated: boolean;
    fabricationDetails?: string;
  };
}

interface InterviewSession {
  id: number;
  userId: number;
  workflowId?: string | null;
  jobTitle?: string;
  company?: string;
  jobDescription?: string;
  questionsJson: string;
  conversationJson: string;
  score?: number | null;
  strengths?: string;
  weaknesses?: string;
  improvement?: string;
  status: "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
  answeredCount?: number;
  profileSufficient: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// ============ 辅助函数 ============
function safeParse<T>(json: string | null | undefined, fallback: T): T {
  if (!json) return fallback;
  try {
    return JSON.parse(json) as T;
  } catch {
    return fallback;
  }
}

function scoreColor(score: number): string {
  if (score >= 85) return "text-emerald-600";
  if (score >= 70) return "text-blue-600";
  if (score >= 55) return "text-amber-600";
  if (score >= 40) return "text-orange-600";
  return "text-red-600";
}

function difficultyVariant(diff?: string): "default" | "secondary" | "destructive" {
  if (diff === "困难") return "destructive";
  if (diff === "中等") return "default";
  return "secondary";
}

// ============ 主组件 ============
export function MockInterview() {
  const [sessions, setSessions] = React.useState<InterviewSession[]>([]);
  const [currentSession, setCurrentSession] = React.useState<InterviewSession | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  // 新建面试题生成表单
  const [jdText, setJdText] = React.useState("");
  const [jobTitle, setJobTitle] = React.useState("");
  const [company, setCompany] = React.useState("");

  // 当前题目索引 + 用户回答
  const [currentQuestionIdx, setCurrentQuestionIdx] = React.useState(0);
  const [userAnswer, setUserAnswer] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const [currentEvaluation, setCurrentEvaluation] = React.useState<Evaluation | null>(null);
  const [finalEval, setFinalEval] = React.useState<any>(null);
  const [completing, setCompleting] = React.useState(false);

  React.useEffect(() => {
    fetchSessions();
  }, []);

  const fetchSessions = async () => {
    try {
      const res: any = await api.get("/api/interview/sessions");
      setSessions(Array.isArray(res) ? res : []);
    } catch (e: any) {
      console.warn("Failed to fetch sessions:", e);
    }
  };

  const generateQuestions = async () => {
    if (!jdText.trim()) {
      setError("请粘贴职位描述");
      return;
    }
    setLoading(true);
    setError(null);
    setFinalEval(null);
    setCurrentEvaluation(null);
    try {
      const res: any = await api.post("/api/interview/generate-questions", {
        jobDescription: jdText,
        jobTitle: jobTitle || undefined,
        company: company || undefined,
      });
      setCurrentSession(res as InterviewSession);
      setCurrentQuestionIdx(0);
      setUserAnswer("");
      fetchSessions();
    } catch (e: any) {
      setError(e.message || "生成面试题失败");
    } finally {
      setLoading(false);
    }
  };

  const selectSession = (session: InterviewSession) => {
    setCurrentSession(session);
    setCurrentQuestionIdx(0);
    setUserAnswer("");
    setCurrentEvaluation(null);
    setFinalEval(null);
    if (session.status === "COMPLETED") {
      // 已完成会话：解析 strengths/weaknesses/improvement
      try {
        setFinalEval({
          score: session.score,
          strengths: safeParse<string[]>(session.strengths, []),
          weaknesses: safeParse<string[]>(session.weaknesses, []),
          improvement: safeParse<string[]>(session.improvement, []),
        });
      } catch {
        // ignore
      }
    }
  };

  const submitAnswer = async () => {
    if (!currentSession || !userAnswer.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const res: any = await api.post(
        `/api/interview/sessions/${currentSession.id}/answer`,
        {
          questionIndex: currentQuestionIdx,
          userAnswer,
        }
      );
      const updatedSession = res.session as InterviewSession;
      const evaluation = safeParse<Evaluation>(res.evaluation, {
        score: 0,
        strengths: [],
        weaknesses: [],
        improvement: [],
      });
      setCurrentSession(updatedSession);
      setCurrentEvaluation(evaluation);
      setUserAnswer("");
      fetchSessions();
    } catch (e: any) {
      setError(e.message || "提交回答失败");
    } finally {
      setSubmitting(false);
    }
  };

  const nextQuestion = () => {
    setCurrentQuestionIdx((idx) => idx + 1);
    setCurrentEvaluation(null);
    setUserAnswer("");
  };

  const completeInterview = async () => {
    if (!currentSession) return;
    setCompleting(true);
    setError(null);
    try {
      const res: any = await api.post(
        `/api/interview/sessions/${currentSession.id}/complete`
      );
      const updatedSession = res.session as InterviewSession;
      const finalEvaluation = safeParse<any>(res.finalEvaluation, {});
      setCurrentSession(updatedSession);
      setFinalEval(finalEvaluation);
      fetchSessions();
    } catch (e: any) {
      setError(e.message || "完成面试失败");
    } finally {
      setCompleting(false);
    }
  };

  const startNewInterview = () => {
    setCurrentSession(null);
    setCurrentEvaluation(null);
    setFinalEval(null);
    setUserAnswer("");
    setCurrentQuestionIdx(0);
    setJdText("");
    setJobTitle("");
    setCompany("");
  };

  // ============ 渲染 ============
  const questions: InterviewQuestion[] = currentSession
    ? safeParse<{ interviewQuestions: InterviewQuestion[] }>(currentSession.questionsJson, {
        interviewQuestions: [],
      }).interviewQuestions
    : [];

  const conversation: ConversationEntry[] = currentSession
    ? safeParse<ConversationEntry[]>(currentSession.conversationJson, [])
    : [];

  const currentQuestion = questions[currentQuestionIdx];
  const totalQuestions = questions.length;
  const isCompleted = currentSession?.status === "COMPLETED";

  return (
    <div className="space-y-6">
      {/* 顶部：标题 + 操作 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
            <MessageSquare className="h-5 w-5 text-brand-500" />
            模拟面试 (Sprint 7-B)
          </h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            基于 Personal RAG 的真实经历生成定制化面试题 + AI 评分 + 改进建议
          </p>
        </div>
        {currentSession && (
          <Button variant="outline" onClick={startNewInterview}>
            <RotateCcw className="h-4 w-4 mr-1" /> 新建面试
          </Button>
        )}
      </div>

      {error && (
        <div className="rounded-xl border border-red-300 bg-red-50 p-3 text-sm text-red-700 flex items-start gap-2">
          <AlertTriangle className="h-4 w-4 mt-0.5 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* 阶段1：无会话时显示 JD 输入 */}
      {!currentSession && (
        <Card>
          <CardContent className="p-6 space-y-4">
            <h3 className="font-semibold text-gray-900 dark:text-white">生成面试题</h3>
            <div className="grid grid-cols-2 gap-3">
              <input
                value={jobTitle}
                onChange={(e) => setJobTitle(e.target.value)}
                placeholder="岗位名称（如：AI应用开发工程师）"
                className="rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
              />
              <input
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                placeholder="公司（可选）"
                className="rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
              />
            </div>
            <textarea
              value={jdText}
              onChange={(e) => setJdText(e.target.value)}
              placeholder="粘贴完整的职位描述到此..."
              rows={8}
              className="w-full rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
            />
            <Button onClick={generateQuestions} disabled={loading} className="w-full">
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 mr-1 animate-spin" /> 正在生成面试题...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4 mr-1" /> 生成定制化面试题
                </>
              )}
            </Button>

            {/* 历史会话 */}
            {sessions.length > 0 && (
              <div className="mt-6 border-t pt-4">
                <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                  历史面试会话 ({sessions.length})
                </h4>
                <div className="space-y-2 max-h-64 overflow-y-auto">
                  {sessions.map((s) => (
                    <div
                      key={s.id}
                      onClick={() => selectSession(s)}
                      className="cursor-pointer rounded-lg border border-gray-200 dark:border-gray-700 p-3 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">
                            {s.jobTitle || "未命名岗位"} {s.company && `@ ${s.company}`}
                          </p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            ID: {s.id} · {s.answeredCount || 0} 题已答 ·{" "}
                            {s.status === "COMPLETED"
                              ? "已完成"
                              : s.status === "ABANDONED"
                              ? "已放弃"
                              : "进行中"}
                          </p>
                        </div>
                        {s.score != null && (
                          <Badge variant="secondary" className={scoreColor(s.score)}>
                            {s.score}
                          </Badge>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 阶段2：面试题列表 + 模拟面试对话 */}
      {currentSession && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 左侧：面试题列表 */}
          <Card className="lg:col-span-1">
            <CardContent className="p-4">
              <h3 className="font-semibold text-gray-900 dark:text-white mb-3 flex items-center gap-2">
                <ListChecks className="h-4 w-4" />
                面试题列表 ({totalQuestions})
              </h3>
              <div className="space-y-2 max-h-[600px] overflow-y-auto">
                {questions.map((q, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      setCurrentQuestionIdx(idx);
                      setCurrentEvaluation(null);
                      setUserAnswer("");
                    }}
                    className={`cursor-pointer rounded-lg border p-3 text-xs transition-colors ${
                      idx === currentQuestionIdx
                        ? "border-brand-500 bg-brand-50 dark:bg-brand-950/30"
                        : "border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <Badge variant="secondary" className="text-[10px]">
                        {q.category || q.type}
                      </Badge>
                      {q.difficulty && (
                        <Badge variant={difficultyVariant(q.difficulty)} className="text-[10px]">
                          {q.difficulty}
                        </Badge>
                      )}
                    </div>
                    <p className="line-clamp-2 text-gray-700 dark:text-gray-300">
                      {idx + 1}. {q.question}
                    </p>
                    {idx < currentQuestionIdx ||
                      (idx === currentQuestionIdx &&
                        conversation.length > currentQuestionIdx && (
                          <CheckCircle2 className="h-3 w-3 text-emerald-500 mt-1" />
                        ))}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* 右侧：当前问题 + 回答 + 评价 */}
          <div className="lg:col-span-2 space-y-4">
            {/* 当前题目卡片 */}
            {currentQuestion && !isCompleted && (
              <Card>
                <CardContent className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary">
                        {currentQuestion.category || currentQuestion.type}
                      </Badge>
                      {currentQuestion.difficulty && (
                        <Badge variant={difficultyVariant(currentQuestion.difficulty)}>
                          {currentQuestion.difficulty}
                        </Badge>
                      )}
                      <span className="text-xs text-gray-500">
                        第 {currentQuestionIdx + 1} / {totalQuestions} 题
                      </span>
                    </div>
                  </div>
                  <div>
                    <p className="font-medium text-gray-900 dark:text-white">
                      {currentQuestion.question}
                    </p>
                    {currentQuestion.userProjectReference && (
                      <p className="text-xs text-blue-600 mt-2 bg-blue-50 dark:bg-blue-950/30 rounded p-2">
                        💡 可引用经历：{currentQuestion.userProjectReference}
                      </p>
                    )}
                    {currentQuestion.followUpQuestions &&
                      currentQuestion.followUpQuestions.length > 0 && (
                        <details className="mt-2">
                          <summary className="text-xs text-gray-500 cursor-pointer">
                            可能的追问 ({currentQuestion.followUpQuestions.length})
                          </summary>
                          <ul className="text-xs text-gray-600 mt-1 ml-4 list-disc">
                            {currentQuestion.followUpQuestions.map((f, i) => (
                              <li key={i}>{f}</li>
                            ))}
                          </ul>
                        </details>
                      )}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* 用户回答输入 */}
            {currentQuestion && !isCompleted && (
              <Card>
                <CardContent className="p-5 space-y-3">
                  <h4 className="font-medium text-gray-900 dark:text-white">你的回答</h4>
                  <textarea
                    value={userAnswer}
                    onChange={(e) => setUserAnswer(e.target.value)}
                    placeholder="请输入你的回答...（建议使用 STAR 原则：情境-任务-行动-结果）"
                    rows={6}
                    className="w-full rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
                  />
                  <div className="flex gap-2">
                    <Button
                      onClick={submitAnswer}
                      disabled={!userAnswer.trim() || submitting}
                      className="flex-1"
                    >
                      {submitting ? (
                        <>
                          <Loader2 className="h-4 w-4 mr-1 animate-spin" /> AI 评价中...
                        </>
                      ) : (
                        <>
                          <Send className="h-4 w-4 mr-1" /> 提交回答获取 AI 评价
                        </>
                      )}
                    </Button>
                    {currentEvaluation && currentQuestionIdx < totalQuestions - 1 && (
                      <Button variant="outline" onClick={nextQuestion}>
                        下一题 →
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* AI 评价结果 */}
            {currentEvaluation && (
              <Card>
                <CardContent className="p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <h4 className="font-medium text-gray-900 dark:text-white flex items-center gap-2">
                      <Trophy className="h-4 w-4 text-amber-500" />
                      AI 评价
                    </h4>
                    <div className={`text-2xl font-bold ${scoreColor(currentEvaluation.score)}`}>
                      {currentEvaluation.score}
                    </div>
                  </div>

                  {currentEvaluation.factCheck?.fabricated && (
                    <div className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-700">
                      <AlertTriangle className="h-4 w-4 inline mr-1" />
                      ⚠️ 检测到编造经历：{currentEvaluation.factCheck.fabricationDetails}
                    </div>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div>
                      <p className="text-xs font-medium text-emerald-600 mb-1">✓ 亮点</p>
                      <ul className="text-xs space-y-1">
                        {currentEvaluation.strengths.map((s, i) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {s}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-red-600 mb-1">✗ 弱点</p>
                      <ul className="text-xs space-y-1">
                        {currentEvaluation.weaknesses.map((w, i) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {w}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-blue-600 mb-1">💡 改进</p>
                      <ul className="text-xs space-y-1">
                        {currentEvaluation.improvement.map((imp, i) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {imp}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}

            {/* 完成面试按钮 */}
            {currentSession &&
              !isCompleted &&
              conversation.length > 0 &&
              currentQuestionIdx >= totalQuestions - 1 &&
              currentEvaluation && (
                <Card>
                  <CardContent className="p-5 text-center">
                    <p className="text-sm text-gray-600 mb-3">
                      已完成全部 {totalQuestions} 道题，点击下方按钮生成最终面试评价
                    </p>
                    <Button onClick={completeInterview} disabled={completing}>
                      {completing ? (
                        <>
                          <Loader2 className="h-4 w-4 mr-1 animate-spin" /> 生成最终评价...
                        </>
                      ) : (
                        <>
                          <Trophy className="h-4 w-4 mr-1" /> 生成最终面试评价
                        </>
                      )}
                    </Button>
                  </CardContent>
                </Card>
              )}

            {/* 最终评价 */}
            {finalEval && isCompleted && (
              <Card>
                <CardContent className="p-5 space-y-4">
                  <div className="flex items-center justify-between border-b pb-3">
                    <h4 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                      <TrendingUp className="h-5 w-5 text-brand-500" />
                      最终面试评价
                    </h4>
                    {finalEval.score != null && (
                      <div className={`text-3xl font-bold ${scoreColor(finalEval.score)}`}>
                        {finalEval.score}
                      </div>
                    )}
                  </div>

                  {finalEval.jobReadiness && (
                    <div className="text-sm">
                      <span className="text-gray-500">求职准备度：</span>
                      <Badge variant="default" className="ml-2">
                        {finalEval.jobReadiness}
                      </Badge>
                    </div>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <p className="text-sm font-medium text-emerald-600 mb-2">✓ 综合优势</p>
                      <ul className="text-sm space-y-1">
                        {(finalEval.strengths || []).map((s: string, i: number) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {s}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-red-600 mb-2">✗ 综合弱点</p>
                      <ul className="text-sm space-y-1">
                        {(finalEval.weaknesses || []).map((w: string, i: number) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {w}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-blue-600 mb-2">💡 改进建议</p>
                      <ul className="text-sm space-y-1">
                        {(finalEval.improvement || []).map((imp: string, i: number) => (
                          <li key={i} className="text-gray-700 dark:text-gray-300">
                            • {imp}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>

                  {finalEval.focusAreas && finalEval.focusAreas.length > 0 && (
                    <div className="border-t pt-3">
                      <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        🎯 下一步重点练习方向
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {finalEval.focusAreas.map((area: string, i: number) => (
                          <Badge key={i} variant="secondary">
                            {area}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            )}

            {/* 对话历史 */}
            {conversation.length > 0 && (
              <Card>
                <CardContent className="p-4">
                  <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                    对话历史 ({conversation.length})
                  </h4>
                  <div className="space-y-3 max-h-96 overflow-y-auto">
                    {conversation.map((entry, i) => {
                      const eval_ = safeParse<Evaluation>(entry.evaluation, {
                        score: 0,
                        strengths: [],
                        weaknesses: [],
                        improvement: [],
                      });
                      return (
                        <div
                          key={i}
                          className="border-l-2 border-brand-400 pl-3 py-2"
                        >
                          <p className="text-xs text-gray-500 mb-1">Q{i + 1}: {entry.question}</p>
                          <p className="text-sm text-gray-700 dark:text-gray-300 mb-1">
                            <span className="font-medium">你：</span>
                            {entry.userAnswer}
                          </p>
                          <div className="flex items-center gap-2 text-xs">
                            <Badge variant="secondary" className={scoreColor(eval_.score)}>
                              {eval_.score}
                            </Badge>
                            <span className="text-gray-500">
                              {eval_.strengths.length} 亮点 · {eval_.weaknesses.length} 弱点
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* 资料不足提示 */}
            {currentSession && !currentSession.profileSufficient && (
              <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 flex items-start gap-2">
                <AlertTriangle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                <div>
                  <p className="font-medium">资料不足提示</p>
                  <p className="mt-1">
                    Personal RAG 检索资料不足，面试题的项目深挖部分可能不够精准。
                    建议上传更多项目文档、实习证明、简历等资料以获得定制化面试体验。
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
