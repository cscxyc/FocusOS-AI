"use client";

import * as React from "react";
import { useState } from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
  TrendingUp,
  Target,
  Zap,
  Calendar,
  Rocket,
  BookOpen,
  Clock,
  ArrowRight,
  Sparkles,
  Loader2,
  ChevronRight,
  AlertCircle,
  CheckCircle2,
  Star,
  Layers,
} from "lucide-react";
import {
  useResumeVersions,
  useResumeEvaluations,
  useActiveResumeVersion,
} from "@/hooks/useResume";
import {
  useGenerateCareerGrowth,
  useCareerGrowthPlans,
} from "@/hooks/useCareer";
import type {
  CareerGrowthPlanData,
  SkillGap,
  LearningStage,
  WeeklyTask,
  ProjectRecommendation,
  ResumeVersion,
  ResumeEvaluationReport,
} from "@/lib/types";

/**
 * Sprint 8-B: 职业成长仪表盘
 * <p>
 * 功能：
 * 1. 选择简历版本 + 评估报告（可选）+ JD（可选）
 * 2. 点击生成职业成长规划
 * 3. 展示规划结果：
 *    - 当前等级 / 职业目标
 *    - 能力 Gap 分析（SkillGap 列表）
 *    - 三个月成长路线（LearningStage 时间线）
 *    - 周任务计划（WeeklyTask 网格）
 *    - 推荐项目实践（ProjectRecommendation 卡片）
 *    - 总结
 * 4. 历史规划列表
 */
export function CareerGrowthDashboard() {
  const { data: versions } = useResumeVersions();
  const { data: activeVersion } = useActiveResumeVersion();
  const { data: evaluations } = useResumeEvaluations();
  const { data: planHistory } = useCareerGrowthPlans();
  const generateMutation = useGenerateCareerGrowth();

  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(
    activeVersion?.id ?? null
  );
  const [selectedEvaluationId, setSelectedEvaluationId] = useState<number | null>(null);
  const [jobDescription, setJobDescription] = useState<string>("");
  const [result, setResult] = useState<CareerGrowthPlanData | null>(null);

  const selectedVersion: ResumeVersion | undefined = versions?.find(
    (v) => v.id === selectedVersionId
  );
  const selectedEvaluation: ResumeEvaluationReport | undefined = evaluations?.find(
    (e) => e.id === selectedEvaluationId
  );

  const handleGenerate = () => {
    if (!selectedVersionId) return;
    generateMutation.mutate(
      {
        resumeVersionId: selectedVersionId,
        evaluationId: selectedEvaluationId ?? undefined,
        jobDescription: jobDescription.trim() || undefined,
      },
      {
        onSuccess: (resp) => {
          setResult(resp.data?.plan ?? null);
        },
      }
    );
  };

  const isGenerating = generateMutation.isPending;

  return (
    <div className="space-y-5">
      {/* 配置区 */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center gap-2 mb-4">
            <div className="h-8 w-8 rounded-xl bg-gradient-to-br from-brand-500 to-purple-500 flex items-center justify-center">
              <Sparkles className="h-4 w-4 text-white" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-white">
                生成职业成长规划
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                基于目标 JD + 简历评分 + Personal RAG，生成三个月可执行成长路线
              </p>
            </div>
          </div>

          <div className="grid md:grid-cols-2 gap-4">
            {/* 选择简历版本 */}
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                简历版本 <span className="text-red-500">*</span>
              </label>
              <select
                value={selectedVersionId ?? ""}
                onChange={(e) => {
                  const id = e.target.value ? Number(e.target.value) : null;
                  setSelectedVersionId(id);
                  setSelectedEvaluationId(null);
                }}
                className="w-full h-9 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 text-sm text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                <option value="">请选择简历版本</option>
                {versions?.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.versionName || `版本 ${v.id}`}
                    {v.targetPosition ? ` · ${v.targetPosition}` : ""}
                    {v.isActive ? " ✅" : ""}
                  </option>
                ))}
              </select>
            </div>

            {/* 选择评估报告（可选） */}
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                简历评分报告（可选）
              </label>
              <select
                value={selectedEvaluationId ?? ""}
                onChange={(e) =>
                  setSelectedEvaluationId(e.target.value ? Number(e.target.value) : null)
                }
                className="w-full h-9 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 text-sm text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                <option value="">不使用评分（通用规划）</option>
                {evaluations
                  ?.filter((e) => selectedVersionId == null || e.resumeVersionId === selectedVersionId)
                  .map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.jobTitle || `评估 #${e.id}`} · {e.score}分
                      {e.company ? ` · ${e.company}` : ""}
                    </option>
                  ))}
              </select>
            </div>

            {/* JD 输入（可选） */}
            <div className="md:col-span-2">
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                目标岗位 JD（可选，未选择 Career 报告时填写）
              </label>
              <textarea
                value={jobDescription}
                onChange={(e) => setJobDescription(e.target.value)}
                rows={3}
                placeholder="粘贴目标岗位 JD，规划将基于 JD 要求生成针对性 Gap 分析..."
                className="w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-brand-500 resize-none"
              />
            </div>
          </div>

          <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
            <div className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1.5">
              <AlertCircle className="h-3.5 w-3.5" />
              规划基于 AI 生成，建议结合个人实际情况调整
            </div>
            <Button
              onClick={handleGenerate}
              disabled={!selectedVersionId || isGenerating}
              className="gap-2"
            >
              {isGenerating ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Sparkles className="h-4 w-4" />
              )}
              {isGenerating ? "AI 正在生成规划..." : "生成职业成长规划"}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 规划结果展示 */}
      {(result || generateMutation.data?.data?.plan) && (
        <GrowthPlanView plan={result ?? (generateMutation.data?.data?.plan as CareerGrowthPlanData)} />
      )}

      {/* 历史规划列表 */}
      {planHistory && planHistory.length > 0 && !result && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <BookOpen className="h-4 w-4 text-brand-500" />
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                历史成长规划
                <span className="ml-1.5 text-xs text-gray-400">({planHistory.length})</span>
              </h3>
            </div>
            <div className="space-y-2">
              {planHistory.slice(0, 5).map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors cursor-pointer"
                  onClick={() => {
                    // 直接跳转：这里简单做 state 设置，实际可做详情页
                    setSelectedVersionId(p.resumeVersionId);
                    setSelectedEvaluationId(p.evaluationId ?? null);
                  }}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="h-9 w-9 rounded-lg bg-brand-100 dark:bg-brand-900/30 flex items-center justify-center flex-shrink-0">
                      <TrendingUp className="h-4 w-4 text-brand-600 dark:text-brand-400" />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-gray-900 dark:text-white truncate">
                        {p.targetPosition || `规划 #${p.id}`}
                        {p.company && (
                          <Badge variant="secondary" className="ml-1.5 text-[10px]">
                            {p.company}
                          </Badge>
                        )}
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                        {p.currentLevel || "未评级"} · {new Date(p.createdAt).toLocaleDateString("zh-CN")}
                      </div>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-gray-400 flex-shrink-0" />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ============================================================
// 规划结果展示子组件
// ============================================================

function GrowthPlanView({ plan }: { plan: CareerGrowthPlanData }) {
  const {
    currentLevel,
    careerGoal,
    skillGaps = [],
    roadmap = [],
    weeklyTasks = [],
    projects = [],
    summary,
  } = plan;

  return (
    <div className="space-y-4">
      {/* 等级 + 目标 */}
      <Card>
        <CardContent className="p-5">
          <div className="grid md:grid-cols-2 gap-5">
            <div className="flex items-start gap-3">
              <div className="h-11 w-11 rounded-xl bg-gradient-to-br from-brand-500 to-indigo-500 flex items-center justify-center flex-shrink-0">
                <Star className="h-5 w-5 text-white" />
              </div>
              <div>
                <div className="text-xs text-gray-500 dark:text-gray-400 mb-1">当前能力等级</div>
                <div className="text-lg font-bold text-gray-900 dark:text-white">
                  {currentLevel || "未评级"}
                </div>
                <Badge variant="secondary" className="mt-2 text-[10px]">
                  基于评分定位
                </Badge>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="h-11 w-11 rounded-xl bg-gradient-to-br from-green-500 to-emerald-500 flex items-center justify-center flex-shrink-0">
                <Target className="h-5 w-5 text-white" />
              </div>
              <div>
                <div className="text-xs text-gray-500 dark:text-gray-400 mb-1">3 个月职业目标</div>
                <div className="text-base font-semibold text-gray-900 dark:text-white leading-snug">
                  {careerGoal || "提升简历竞争力"}
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 能力 Gap 分析 */}
      {skillGaps.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-7 w-7 rounded-lg bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center">
                <Zap className="h-4 w-4 text-orange-600 dark:text-orange-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                能力 Gap 分析
                <span className="ml-1.5 text-xs text-gray-400">({skillGaps.length})</span>
              </h3>
            </div>
            <div className="space-y-3">
              {skillGaps.map((gap, i) => (
                <SkillGapRow key={i} gap={gap} index={i} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 三个月成长路线 */}
      {roadmap.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-7 w-7 rounded-lg bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                <Layers className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                三个月成长路线
              </h3>
            </div>
            <div className="grid md:grid-cols-3 gap-4">
              {roadmap.map((stage, i) => (
                <RoadmapStage key={i} stage={stage} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 周任务计划 */}
      {weeklyTasks.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-7 w-7 rounded-lg bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
                <Calendar className="h-4 w-4 text-purple-600 dark:text-purple-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                周任务计划
                <span className="ml-1.5 text-xs text-gray-400">
                  (共 {weeklyTasks.reduce((s, t) => s + (t.estimatedHours || 0), 0)} 小时)
                </span>
              </h3>
            </div>
            <div className="grid md:grid-cols-2 gap-3 max-h-[500px] overflow-y-auto pr-1">
              {weeklyTasks.map((task, i) => (
                <WeeklyTaskCard key={i} task={task} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 推荐项目实践 */}
      {projects.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-7 w-7 rounded-lg bg-pink-100 dark:bg-pink-900/30 flex items-center justify-center">
                <Rocket className="h-4 w-4 text-pink-600 dark:text-pink-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                推荐项目实践
                <span className="ml-1.5 text-xs text-gray-400">({projects.length})</span>
              </h3>
            </div>
            <div className="space-y-3">
              {projects.map((p, i) => (
                <ProjectCard key={i} project={p} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 总结 */}
      {summary && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-start gap-3">
              <div className="h-7 w-7 rounded-lg bg-green-100 dark:bg-green-900/30 flex items-center justify-center flex-shrink-0">
                <CheckCircle2 className="h-4 w-4 text-green-600 dark:text-green-400" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
                  规划总结
                </h3>
                <p className="text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-line">
                  {summary}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function SkillGapRow({ gap, index }: { gap: SkillGap; index: number }) {
  const impColor =
    gap.importance === "HIGH"
      ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
      : gap.importance === "MEDIUM"
      ? "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400"
      : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400";
  const impLabel =
    gap.importance === "HIGH" ? "高优先" : gap.importance === "MEDIUM" ? "中优先" : "低优先";

  return (
    <div className="p-3 rounded-xl bg-gray-50 dark:bg-gray-800/50 border border-gray-100 dark:border-gray-700/50">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex-shrink-0 w-5 h-5 rounded-full bg-brand-100 dark:bg-brand-900/30 text-brand-600 dark:text-brand-400 text-[10px] font-bold flex items-center justify-center">
            {index + 1}
          </span>
          <span className="text-sm font-semibold text-gray-900 dark:text-white">
            {gap.skill}
          </span>
          <Badge className={`text-[10px] ${impColor}`} variant="secondary">
            {impLabel}
          </Badge>
        </div>
      </div>
      <div className="grid sm:grid-cols-2 gap-2 text-xs">
        <div>
          <span className="text-gray-500 dark:text-gray-400">当前：</span>
          <span className="text-gray-700 dark:text-gray-300">{gap.currentStatus || "-"}</span>
        </div>
        <div>
          <span className="text-gray-500 dark:text-gray-400">目标：</span>
          <span className="text-gray-700 dark:text-gray-300">{gap.targetStatus || "-"}</span>
        </div>
      </div>
      {gap.reason && (
        <div className="mt-2 pt-2 border-t border-gray-200/60 dark:border-gray-700/50 text-xs text-gray-600 dark:text-gray-400 flex items-start gap-1.5">
          <ArrowRight className="h-3 w-3 text-gray-400 mt-0.5 flex-shrink-0" />
          <span>{gap.reason}</span>
        </div>
      )}
    </div>
  );
}

function RoadmapStage({ stage }: { stage: LearningStage }) {
  const monthLabels = ["第 1 月 · 基础补齐", "第 2 月 · 进阶实践", "第 3 月 · 项目落地"];
  const label = monthLabels[(stage.month || 1) - 1] || `第 ${stage.month} 月`;
  const gradients = [
    "from-brand-500 to-indigo-500",
    "from-indigo-500 to-purple-500",
    "from-purple-500 to-pink-500",
  ];
  const gradient = gradients[(stage.month || 1) - 1] || gradients[0];

  return (
    <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-700/50 bg-white dark:bg-gray-800/30">
      <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-gradient-to-r ${gradient} text-white text-xs font-medium mb-3`}>
        {label}
      </div>
      <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-3 leading-snug">
        {stage.goal}
      </h4>
      {stage.skills.length > 0 && (
        <div className="mb-3">
          <div className="text-[10px] text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-1.5">
            技能列表
          </div>
          <div className="flex flex-wrap gap-1.5">
            {stage.skills.map((s, i) => (
              <Badge key={i} variant="secondary" className="text-[10px]">
                {s}
              </Badge>
            ))}
          </div>
        </div>
      )}
      {stage.tasks.length > 0 && (
        <div>
          <div className="text-[10px] text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-1.5">
            关键任务
          </div>
          <ul className="space-y-1">
            {stage.tasks.map((t, i) => (
              <li key={i} className="text-xs text-gray-700 dark:text-gray-300 flex items-start gap-1.5">
                <ChevronRight className="h-3 w-3 text-gray-400 mt-0.5 flex-shrink-0" />
                <span>{t}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function WeeklyTaskCard({ task }: { task: WeeklyTask }) {
  const prColor =
    task.priority === "HIGH"
      ? "border-l-red-500"
      : task.priority === "MEDIUM"
      ? "border-l-orange-500"
      : "border-l-gray-400";
  const prBadge =
    task.priority === "HIGH"
      ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
      : task.priority === "MEDIUM"
      ? "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400"
      : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400";
  const prLabel = task.priority === "HIGH" ? "高" : task.priority === "MEDIUM" ? "中" : "低";

  return (
    <div className={`p-3 rounded-xl bg-gray-50 dark:bg-gray-800/50 border-l-4 ${prColor} border border-gray-100 dark:border-gray-700/50`}>
      <div className="flex items-start justify-between gap-2 mb-1.5">
        <div className="flex items-center gap-1.5 min-w-0">
          <Badge variant="secondary" className="text-[10px] flex-shrink-0">
            Week {task.week}
          </Badge>
          <span className="text-sm font-medium text-gray-900 dark:text-white truncate">
            {task.title}
          </span>
        </div>
        <Badge className={`text-[10px] ${prBadge}`} variant="secondary">
          {prLabel}优先
        </Badge>
      </div>
      {task.description && (
        <p className="text-xs text-gray-600 dark:text-gray-400 mb-2 line-clamp-2">
          {task.description}
        </p>
      )}
      <div className="flex items-center gap-1.5 text-[10px] text-gray-500 dark:text-gray-400">
        <Clock className="h-3 w-3" />
        <span>预计 {task.estimatedHours || 0} 小时</span>
      </div>
    </div>
  );
}

function ProjectCard({ project }: { project: ProjectRecommendation }) {
  return (
    <div className="p-4 rounded-xl bg-gradient-to-br from-gray-50 to-transparent dark:from-gray-800/50 dark:to-transparent border border-gray-100 dark:border-gray-700/50">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className="h-8 w-8 rounded-lg bg-pink-100 dark:bg-pink-900/30 flex items-center justify-center flex-shrink-0">
            <Rocket className="h-4 w-4 text-pink-600 dark:text-pink-400" />
          </div>
          <div className="min-w-0">
            <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
              {project.name}
            </h4>
          </div>
        </div>
      </div>
      <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">
        {project.purpose}
      </p>
      {project.technologies.length > 0 && (
        <div className="mb-3">
          <div className="text-[10px] text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-1.5">
            技术栈
          </div>
          <div className="flex flex-wrap gap-1.5">
            {project.technologies.map((t, i) => (
              <Badge key={i} variant="default" className="text-[10px]">
                {t}
              </Badge>
            ))}
          </div>
        </div>
      )}
      {project.whyRecommended && (
        <div className="pt-3 border-t border-gray-200/60 dark:border-gray-700/50 text-xs text-gray-600 dark:text-gray-400 flex items-start gap-1.5">
          <CheckCircle2 className="h-3.5 w-3.5 text-green-500 mt-0.5 flex-shrink-0" />
          <span>{project.whyRecommended}</span>
        </div>
      )}
    </div>
  );
}
