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
  Brain,
  Sparkles,
  Loader2,
  Star,
  Plus,
  Trash2,
  Wand2,
  FolderKanban,
  Trophy,
  Target,
  GraduationCap,
  UserCircle2,
  Clock3,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  CheckCircle2,
} from "lucide-react";
import {
  listMemories,
  createMemory,
  deleteMemory,
  extractMemory,
} from "@/lib/api";
import type { UserMemory, MemoryType } from "@/lib/types";

// ------------------------------------------------------------------
// Helper: confidence 0.0~1.0 → 1~5 星（向上取整，保留 1 星）
// ------------------------------------------------------------------
function confidenceToStars(confidence: number): number {
  if (!confidence || confidence <= 0) return 1;
  if (confidence >= 1) return 5;
  const raw = Math.ceil(confidence * 5);
  return Math.max(1, Math.min(5, raw));
}

const MEMORY_TYPE_META: Record<
  MemoryType | string,
  { label: string; color: string; icon: React.ComponentType<any> }
> = {
  SKILL: { label: "技能", color: "bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-200", icon: Brain },
  PROJECT: { label: "项目", color: "bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-200", icon: FolderKanban },
  EXPERIENCE: { label: "经验", color: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-200", icon: GraduationCap },
  GOAL: { label: "目标", color: "bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-200", icon: Target },
  LEARNING_PROGRESS: { label: "学习进度", color: "bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-200", icon: Sparkles },
  PREFERENCE: { label: "偏好", color: "bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-200", icon: UserCircle2 },
  ACHIEVEMENT: { label: "成就", color: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-200", icon: Trophy },
};

const DEFAULT_UID = 1; // QA / 未登录态默认用户 ID（测试脚本使用 7/8/1 等，可从 UI 修改）

// ------------------------------------------------------------------
// MemoryDashboard 主组件
// ------------------------------------------------------------------
export function MemoryDashboard() {
  const [memories, setMemories] = useState<UserMemory[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [debugUserId, setDebugUserId] = useState<number>(DEFAULT_UID);

  // 手动提取表单
  const [extractEventType, setExtractEventType] = useState("LEARNING_COMPLETED");
  const [extractContent, setExtractContent] = useState("");
  const [extracting, setExtracting] = useState(false);

  // 手动添加单条
  const [showManualAdd, setShowManualAdd] = useState(false);
  const [mType, setMType] = useState<MemoryType | string>("SKILL");
  const [mKey, setMKey] = useState("");
  const [mValue, setMValue] = useState("");
  const [mConfidence, setMConfidence] = useState<number>(0.8);
  const [mSource, setMSource] = useState("MANUAL");
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    setErrorMsg(null);
    listMemories({ userId: debugUserId, limit: 500 })
      .then((data) => setMemories(Array.isArray(data) ? data : []))
      .catch((e: Error) => setErrorMsg(e.message || "加载失败"))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debugUserId]);

  // 按类型分组 + 技能树 + 时间线（计算值）
  const computed = useMemo(() => {
    const byType: Record<string, UserMemory[]> = {};
    for (const m of memories) {
      (byType[m.memoryType] ||= []).push(m);
    }
    // 技能树：SKILL 按 confidence 降序
    const skills = [...(byType.SKILL || [])].sort(
      (a, b) => (b.confidence || 0) - (a.confidence || 0),
    );
    // 时间线：所有 memory 按 updatedAt 倒序
    const timeline = [...memories].sort((a, b) => {
      const ta = a.updatedAt || a.createdAt || "";
      const tb = b.updatedAt || b.createdAt || "";
      return tb.localeCompare(ta);
    });
    // 统计
    const stats: Record<string, number> = { TOTAL: memories.length };
    Object.keys(MEMORY_TYPE_META).forEach((k) => (stats[k] = byType[k]?.length || 0));
    return { byType, skills, timeline, stats };
  }, [memories]);

  const flashSuccess = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 2500);
  };

  // ----------------------------------------------------------
  // Action: 提取记忆（MemoryAgent）
  // ----------------------------------------------------------
  const handleExtract = async () => {
    if (!extractContent.trim()) {
      setErrorMsg("请填写事件内容");
      return;
    }
    setExtracting(true);
    setErrorMsg(null);
    try {
      const saved = await extractMemory({
        userId: debugUserId,
        eventType: extractEventType.trim(),
        content: extractContent.trim(),
        source: "MANUAL_EXTRACT",
      });
      flashSuccess(`提取完成，入库 ${saved?.length || 0} 条记忆`);
      setExtractContent("");
      load();
    } catch (e: any) {
      setErrorMsg(e?.message || "提取失败");
    } finally {
      setExtracting(false);
    }
  };

  // ----------------------------------------------------------
  // Action: 手动添加一条记忆
  // ----------------------------------------------------------
  const handleManualAdd = async () => {
    if (!mKey.trim() || !mValue.trim()) {
      setErrorMsg("Key / Value 必填");
      return;
    }
    setSaving(true);
    setErrorMsg(null);
    try {
      await createMemory({
        userId: debugUserId,
        memoryType: mType,
        memoryKey: mKey.trim(),
        memoryValue: mValue.trim(),
        source: mSource.trim() || "MANUAL",
        confidence: Number(mConfidence) || 0.8,
      });
      flashSuccess("记忆添加成功（同 key 已自动合并）");
      setMKey("");
      setMValue("");
      load();
    } catch (e: any) {
      setErrorMsg(e?.message || "保存失败");
    } finally {
      setSaving(false);
    }
  };

  // ----------------------------------------------------------
  // Action: 删除一条记忆
  // ----------------------------------------------------------
  const handleDelete = async (id: number) => {
    if (!window.confirm("确认删除这条记忆？此操作不可恢复。")) return;
    try {
      await deleteMemory(id, debugUserId);
      flashSuccess("删除成功");
      load();
    } catch (e: any) {
      setErrorMsg(e?.message || "删除失败");
    }
  };

  return (
    <div className="space-y-6">
      {/* Header + Status */}
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-xl">
                <Brain className="h-6 w-6 text-brand-500" />
                个人成长档案 · Personal Memory Dashboard
              </CardTitle>
              <CardDescription className="mt-1">
                长期记忆会沉淀用户的技能、项目、经验和成就，供 CareerGrowth / Interview / ResumeEvaluator
                三大 Agent 动态调整输出。
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
              <Button size="sm" variant="outline" onClick={load}>
                {loading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Sparkles className="h-4 w-4 mr-1" />}
                刷新
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-8 gap-3">
            <StatCard label="总记忆数" value={computed.stats.TOTAL} accent="bg-gray-100 dark:bg-gray-800" />
            {(Object.keys(MEMORY_TYPE_META) as MemoryType[]).map((t) => {
              const meta = MEMORY_TYPE_META[t];
              const Icon = meta.icon;
              return (
                <StatCard
                  key={t}
                  label={meta.label}
                  value={computed.stats[t] || 0}
                  accent={meta.color}
                  icon={<Icon className="h-4 w-4" />}
                />
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

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 技能树（左 2/3） */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <Star className="h-5 w-5 text-yellow-500" /> 技能树
              </CardTitle>
              <CardDescription>
                按可信度 confidence 映射 5 星；仅用于示意，真实项目可结合 Radar 图组件。
              </CardDescription>
            </CardHeader>
            <CardContent>
              {computed.skills.length === 0 ? (
                <EmptyHint text="暂无技能数据。用下方「AI 记忆提取」或「手动添加」录入你的第一条技能。" />
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {computed.skills.map((s) => (
                    <SkillRow key={s.id} memory={s} onDelete={() => handleDelete(s.id)} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* 成长时间线 */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <Clock3 className="h-5 w-5 text-brand-500" /> 成长时间线
              </CardTitle>
              <CardDescription>所有长期记忆按更新时间倒序排列。</CardDescription>
            </CardHeader>
            <CardContent>
              {computed.timeline.length === 0 ? (
                <EmptyHint text="暂无时间线记录。" />
              ) : (
                <ol className="relative border-l border-gray-200 dark:border-gray-700 ml-2 space-y-6">
                  {computed.timeline.slice(0, 50).map((m) => (
                    <TimelineItem key={m.id} memory={m} onDelete={() => handleDelete(m.id)} />
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>
        </div>

        {/* 工具面板（右 1/3） */}
        <div className="space-y-6">
          {/* AI 记忆提取 */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <Wand2 className="h-5 w-5 text-brand-500" /> AI 记忆提取
              </CardTitle>
              <CardDescription>把行为事件丢给 MemoryAgent，自动提取结构化长期记忆。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  事件类型 eventType
                </label>
                <Input
                  value={extractEventType}
                  onChange={(e) => setExtractEventType(e.target.value)}
                  placeholder="LEARNING_COMPLETED / PROJECT_SUBMISSION / ..."
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  行为内容 content
                </label>
                <textarea
                  className="w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                  rows={5}
                  value={extractContent}
                  onChange={(e) => setExtractContent(e.target.value)}
                  placeholder="例：完成 Milvus 向量检索性能优化实验，HNSW 索引 m=32 efSearch=128，10w 向量 P99 从 102ms 降到 12ms。"
                />
              </div>
              <Button
                className="w-full"
                disabled={extracting || loading}
                onClick={handleExtract}
              >
                {extracting ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Sparkles className="h-4 w-4 mr-2" />}
                {extracting ? "MemoryAgent 提取中..." : "提取并沉淀为长期记忆"}
              </Button>
            </CardContent>
          </Card>

          {/* 手动添加 */}
          <Card>
            <CardHeader className="cursor-pointer select-none" onClick={() => setShowManualAdd((v) => !v)}>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2 text-lg">
                  <Plus className="h-5 w-5 text-brand-500" /> 手动添加记忆
                </CardTitle>
                {showManualAdd ? <ChevronUp className="h-5 w-5 text-gray-400" /> : <ChevronDown className="h-5 w-5 text-gray-400" />}
              </div>
            </CardHeader>
            {showManualAdd && (
              <CardContent className="space-y-3 pt-0">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">记忆类型</label>
                    <select
                      className="w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                      value={mType}
                      onChange={(e) => setMType(e.target.value)}
                    >
                      {(Object.keys(MEMORY_TYPE_META) as MemoryType[]).map((t) => (
                        <option key={t} value={t}>
                          {MEMORY_TYPE_META[t].label} ({t})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">置信度 (0~1)</label>
                    <Input
                      type="number"
                      step={0.05}
                      min={0}
                      max={1}
                      value={mConfidence}
                      onChange={(e) => setMConfidence(Number(e.target.value) || 0.8)}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                    Key（技能名 / 项目名）
                  </label>
                  <Input
                    value={mKey}
                    onChange={(e) => setMKey(e.target.value)}
                    placeholder="例：Milvus / FocusOS AI / 跳槽大厂"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                    Value（具体描述）
                  </label>
                  <textarea
                    className="w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                    rows={3}
                    value={mValue}
                    onChange={(e) => setMValue(e.target.value)}
                    placeholder="例：完成 Milvus HNSW 索引优化实验，P99 从 102ms 降到 12ms"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">来源 source</label>
                  <Input
                    value={mSource}
                    onChange={(e) => setMSource(e.target.value)}
                    placeholder="MANUAL / LEARNING_COMPLETED / RESUME / ..."
                  />
                </div>
                <Button className="w-full" disabled={saving} onClick={handleManualAdd}>
                  {saving ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Plus className="h-4 w-4 mr-2" />}
                  {saving ? "保存中..." : "保存记忆（同 key 自动合并）"}
                </Button>
              </CardContent>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------
// 子组件
// ------------------------------------------------------------------

function StatCard(props: {
  label: string;
  value: number;
  accent?: string;
  icon?: React.ReactNode;
}) {
  return (
    <div
      className={`rounded-xl border border-gray-100 dark:border-gray-800 p-3 ${
        props.accent || "bg-white dark:bg-gray-900"
      }`}
    >
      <div className="flex items-center justify-between text-xs font-medium text-gray-600 dark:text-gray-300">
        <span>{props.label}</span>
        {props.icon}
      </div>
      <div className="mt-1 text-2xl font-bold text-gray-900 dark:text-white">{props.value}</div>
    </div>
  );
}

function SkillRow({
  memory,
  onDelete,
}: {
  memory: UserMemory;
  onDelete?: () => void;
}) {
  const stars = confidenceToStars(memory.confidence || 0.8);
  return (
    <div className="group rounded-xl border border-gray-100 dark:border-gray-800 p-4 bg-white dark:bg-gray-900 hover:shadow-sm transition-shadow">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="text-base font-semibold text-gray-900 dark:text-white">
              {memory.memoryKey}
            </div>
            <Badge variant="outline" className="text-[10px]">
              conf {(memory.confidence ?? 0).toFixed(2)}
            </Badge>
          </div>
          <div className="mt-1 flex items-center gap-0.5">
            {Array.from({ length: 5 }).map((_, i) => (
              <Star
                key={i}
                className={`h-4 w-4 ${
                  i < stars
                    ? "fill-yellow-400 text-yellow-400"
                    : "text-gray-200 dark:text-gray-700"
                }`}
              />
            ))}
            <span className="ml-2 text-[11px] text-gray-500 dark:text-gray-400">
              {stars}/5
            </span>
          </div>
          <div className="mt-2 text-sm text-gray-600 dark:text-gray-300 line-clamp-3">
            {memory.memoryValue}
          </div>
          {memory.source && (
            <div className="mt-2 text-[11px] text-gray-400">来源：{memory.source}</div>
          )}
        </div>
        <Button
          size="icon"
          variant="ghost"
          className="opacity-0 group-hover:opacity-100 transition-opacity text-gray-400 hover:text-red-500 shrink-0"
          onClick={onDelete}
          title="删除"
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

function TimelineItem({
  memory,
  onDelete,
}: {
  memory: UserMemory;
  onDelete?: () => void;
}) {
  const meta = MEMORY_TYPE_META[memory.memoryType] || {
    label: memory.memoryType,
    color: "bg-gray-100",
    icon: Brain,
  };
  const Icon = meta.icon;
  const dateStr = memory.updatedAt || memory.createdAt || "";
  const dateLabel = dateStr ? new Date(dateStr).toLocaleString() : "-";
  return (
    <li className="ml-4 group">
      <span
        className={`absolute -left-[9px] flex items-center justify-center w-4 h-4 rounded-full ring-4 ring-white dark:ring-gray-950 ${meta.color}`}
        aria-hidden
      >
        <Icon className="w-2.5 h-2.5" />
      </span>
      <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 p-4 hover:shadow-sm transition-shadow">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <Badge className={meta.color}>{meta.label}</Badge>
              <span className="text-sm font-semibold text-gray-900 dark:text-white">
                {memory.memoryKey}
              </span>
              {(memory.confidence != null) && (
                <Badge variant="outline" className="text-[10px]">
                  conf {memory.confidence.toFixed(2)}
                </Badge>
              )}
            </div>
            <div className="mt-1 text-[11px] text-gray-500 dark:text-gray-400">{dateLabel}</div>
            <div className="mt-2 text-sm text-gray-600 dark:text-gray-300 whitespace-pre-wrap break-words">
              {memory.memoryValue}
            </div>
            {memory.source && (
              <div className="mt-2 text-[11px] text-gray-400">来源：{memory.source}</div>
            )}
          </div>
          <Button
            size="icon"
            variant="ghost"
            className="opacity-0 group-hover:opacity-100 transition-opacity text-gray-400 hover:text-red-500 shrink-0"
            onClick={onDelete}
            title="删除"
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </li>
  );
}

function EmptyHint({ text }: { text: string }) {
  return (
    <div className="rounded-xl border border-dashed border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-8 text-center">
      <Brain className="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600 mb-2" />
      <p className="text-sm text-gray-500 dark:text-gray-400">{text}</p>
    </div>
  );
}
