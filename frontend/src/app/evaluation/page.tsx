"use client";

import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { Badge } from "@/components/ui/Badge";
import { Gauge } from "lucide-react";
import { EvaluationDashboard } from "@/components/evaluation/EvaluationDashboard";

export default function EvaluationPage() {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">评估中心</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                Agent Evaluation · 质量排行 → 评分趋势 → 问题分析 → Grounding/RAG 评估 → Prompt A/B
              </p>
            </div>
            <Badge variant="default">
              <Gauge className="h-3 w-3 mr-1" />
              Evaluation
            </Badge>
          </div>

          <EvaluationDashboard />
        </main>
      </div>
    </div>
  );
}
