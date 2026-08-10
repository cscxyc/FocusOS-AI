"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Progress } from "@/components/ui/Progress";
import type { ResumeEvaluation } from "@/lib/types";

interface ResumeScoreCardProps {
  evaluation: ResumeEvaluation;
}

/**
 * Sprint 8-A: 简历评分卡
 * <p>
 * 展示：
 * 1. 综合总分（大数字 0-100）
 * 2. 五维雷达图（JD匹配 / ATS / STAR / 项目 / 完整度）
 * 3. 各维度评分进度条
 */
export function ResumeScoreCard({ evaluation }: ResumeScoreCardProps) {
  const {
    score,
    matchScore,
    atsScore,
    starScore,
    completenessScore,
    sectionScores,
  } = evaluation;

  // 五维数据（项目深度取 sectionScores.project）
  const projectScore = sectionScores?.project ?? 0;
  const radarData = [
    { label: "JD匹配", value: matchScore ?? 0 },
    { label: "ATS", value: atsScore ?? 0 },
    { label: "STAR", value: starScore ?? 0 },
    { label: "项目", value: projectScore },
    { label: "完整度", value: completenessScore ?? 0 },
  ];

  const scoreColor = getScoreColor(score ?? 0);
  const scoreLabel = getScoreLabel(score ?? 0);

  return (
    <Card>
      <CardContent className="p-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-center">
          {/* 左侧：综合总分 */}
          <div className="flex flex-col items-center justify-center text-center">
            <div className="relative h-36 w-36 flex items-center justify-center">
              <svg className="absolute inset-0" viewBox="0 0 100 100">
                <circle
                  cx="50"
                  cy="50"
                  r="44"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="6"
                  className="text-gray-100 dark:text-gray-800"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="44"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="6"
                  strokeLinecap="round"
                  className={scoreColor}
                  strokeDasharray={`${(score ?? 0) * 2.76} 999`}
                  transform="rotate(-90 50 50)"
                  style={{ transition: "stroke-dasharray 0.8s ease-out" }}
                />
              </svg>
              <div className="relative z-10">
                <div className={`text-4xl font-bold ${scoreColor}`}>
                  {score ?? 0}
                </div>
                <div className="text-xs text-gray-400 mt-0.5">/ 100</div>
              </div>
            </div>
            <div className={`mt-3 text-sm font-semibold ${scoreColor}`}>
              {scoreLabel}
            </div>
          </div>

          {/* 右侧：五维雷达图 */}
          <div className="flex justify-center">
            <RadarChart data={radarData} />
          </div>
        </div>

        {/* 各维度评分进度条 */}
        <div className="mt-6 pt-6 border-t border-gray-100 dark:border-gray-800 space-y-3">
          {radarData.map((dim) => (
            <div key={dim.label} className="flex items-center gap-3">
              <span className="text-xs text-gray-500 dark:text-gray-400 w-16 flex-shrink-0">
                {dim.label}
              </span>
              <Progress
                value={dim.value}
                className="flex-1"
                indicatorClassName={getProgressColor(dim.value)}
              />
              <span className={`text-xs font-semibold w-8 text-right ${getScoreColor(dim.value)}`}>
                {dim.value}
              </span>
            </div>
          ))}
        </div>

        {/* Section 评分 */}
        {sectionScores && (
          <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
            <div className="text-xs text-gray-400 mb-2">Section 评分</div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <SectionBadge label="摘要" value={sectionScores.summary ?? 0} />
              <SectionBadge label="经历" value={sectionScores.experience ?? 0} />
              <SectionBadge label="项目" value={sectionScores.project ?? 0} />
              <SectionBadge label="技能" value={sectionScores.skills ?? 0} />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================
// 雷达图组件（纯 SVG 实现，无外部依赖）
// ============================================================

interface RadarChartProps {
  data: Array<{ label: string; value: number }>;
  size?: number;
}

function RadarChart({ data, size = 220 }: RadarChartProps) {
  const center = size / 2;
  const maxRadius = size / 2 - 36; // 留出标签空间
  const angleStep = (Math.PI * 2) / data.length;

  // 计算每个维度的坐标点
  const points = data.map((d, i) => {
    const angle = -Math.PI / 2 + i * angleStep; // 从顶部开始
    const radius = (d.value / 100) * maxRadius;
    return {
      x: center + radius * Math.cos(angle),
      y: center + radius * Math.sin(angle),
      labelX: center + (maxRadius + 18) * Math.cos(angle),
      labelY: center + (maxRadius + 18) * Math.sin(angle),
      label: d.label,
      value: d.value,
    };
  });

  const polygonPoints = points.map((p) => `${p.x},${p.y}`).join(" ");

  // 网格圆（20/40/60/80/100）
  const gridLevels = [20, 40, 60, 80, 100];

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      {/* 网格 */}
      {gridLevels.map((level) => {
        const r = (level / 100) * maxRadius;
        const gridPoints = data
          .map((_, i) => {
            const angle = -Math.PI / 2 + i * angleStep;
            return `${center + r * Math.cos(angle)},${center + r * Math.sin(angle)}`;
          })
          .join(" ");
        return (
          <polygon
            key={level}
            points={gridPoints}
            fill="none"
            stroke="currentColor"
            strokeWidth="0.5"
            className="text-gray-200 dark:text-gray-700"
          />
        );
      })}

      {/* 轴线 */}
      {data.map((_, i) => {
        const angle = -Math.PI / 2 + i * angleStep;
        return (
          <line
            key={i}
            x1={center}
            y1={center}
            x2={center + maxRadius * Math.cos(angle)}
            y2={center + maxRadius * Math.sin(angle)}
            stroke="currentColor"
            strokeWidth="0.5"
            className="text-gray-200 dark:text-gray-700"
          />
        );
      })}

      {/* 数据多边形 */}
      <polygon
        points={polygonPoints}
        fill="currentColor"
        fillOpacity="0.15"
        stroke="currentColor"
        strokeWidth="2"
        className="text-brand-500"
        style={{ transition: "all 0.6s ease-out" }}
      />

      {/* 数据点 */}
      {points.map((p, i) => (
        <circle
          key={i}
          cx={p.x}
          cy={p.y}
          r="3"
          className="fill-brand-500"
          style={{ transition: "all 0.6s ease-out" }}
        />
      ))}

      {/* 标签 */}
      {points.map((p, i) => (
        <text
          key={i}
          x={p.labelX}
          y={p.labelY}
          textAnchor="middle"
          dominantBaseline="middle"
          className="fill-gray-500 dark:fill-gray-400 text-[10px] font-medium"
        >
          {p.label}
          <tspan x={p.labelX} y={p.labelY + 12} className="fill-brand-500 font-bold">
            {p.value}
          </tspan>
        </text>
      ))}
    </svg>
  );
}

// ============================================================
// 工具函数
// ============================================================

function SectionBadge({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col items-center justify-center py-2 rounded-lg bg-gray-50 dark:bg-gray-800/50">
      <span className="text-[10px] text-gray-400">{label}</span>
      <span className={`text-sm font-bold ${getScoreColor(value)}`}>{value}</span>
    </div>
  );
}

function getScoreColor(score: number): string {
  if (score >= 85) return "text-green-500";
  if (score >= 70) return "text-blue-500";
  if (score >= 55) return "text-yellow-500";
  if (score >= 40) return "text-orange-500";
  return "text-red-500";
}

function getProgressColor(score: number): string {
  if (score >= 85) return "from-green-400 to-green-500";
  if (score >= 70) return "from-blue-400 to-blue-500";
  if (score >= 55) return "from-yellow-400 to-yellow-500";
  if (score >= 40) return "from-orange-400 to-orange-500";
  return "from-red-400 to-red-500";
}

function getScoreLabel(score: number): string {
  if (score >= 85) return "优秀";
  if (score >= 70) return "良好";
  if (score >= 55) return "合格";
  if (score >= 40) return "较弱";
  return "不合格";
}
