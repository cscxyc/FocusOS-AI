"use client";

import * as React from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { JDAnalyzer } from "@/components/career/JDAnalyzer";
import { ResumeOptimizer } from "@/components/career/ResumeOptimizer";
import { CareerWorkflow } from "@/components/career/CareerWorkflow";
import { MockInterview } from "@/components/career/MockInterview";
import { CareerGrowthDashboard } from "@/components/career/CareerGrowthDashboard";
import { ResumeWorkspace } from "@/components/resume/ResumeWorkspace";
import { LLMMonitor } from "@/components/dashboard/LLMMonitor";
// Sprint 8-C: Personal Memory System
import { MemoryDashboard } from "@/components/memory/MemoryDashboard";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useCareerStore } from "@/store/careerStore";
import { useCareer } from "@/hooks/useCareer";
import { FileText, Target, Wand2, Briefcase, Loader2, Sparkles, MessageSquare, Activity, TrendingUp, Brain } from "lucide-react";
import { useState } from "react";

export default function CareerPage() {
  const resumes = useCareerStore((s) => s.resumes);
  const { isLoading: isProfileLoading } = useCareer();
  const [activeTab, setActiveTab] = useState<"resume" | "jd" | "optimize" | "workflow" | "growth" | "interview" | "memory" | "monitor">("workflow");

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">职业中心</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                AI Career Assistant · JD 分析 → 简历优化 → 学习计划 → 面试准备
              </p>
            </div>
            <Badge variant="default">
              <Briefcase className="h-3 w-3 mr-1" />
              {resumes.length} 份简历
            </Badge>
          </div>

          <div className="flex gap-2 mb-6 flex-wrap">
            <button
              onClick={() => setActiveTab("workflow")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "workflow"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <Sparkles className="h-4 w-4" />AI Career 分析
            </button>
            <button
              onClick={() => setActiveTab("growth")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "growth"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <TrendingUp className="h-4 w-4" />职业成长
            </button>
            <button
              onClick={() => setActiveTab("memory")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "memory"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <Brain className="h-4 w-4" />成长档案
            </button>
            <button
              onClick={() => setActiveTab("interview")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "interview"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <MessageSquare className="h-4 w-4" />模拟面试
            </button>
            <button
              onClick={() => setActiveTab("resume")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "resume"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <FileText className="h-4 w-4" />简历管理
            </button>
            <button
              onClick={() => setActiveTab("jd")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "jd"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <Target className="h-4 w-4" />JD 分析
            </button>
            <button
              onClick={() => setActiveTab("optimize")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "optimize"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <Wand2 className="h-4 w-4" />简历优化
            </button>
            <button
              onClick={() => setActiveTab("monitor")}
              className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                activeTab === "monitor"
                  ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                  : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
              }`}
            >
              <Activity className="h-4 w-4" />LLM 监控
            </button>
          </div>

          {activeTab === "workflow" && <CareerWorkflow />}

          {activeTab === "growth" && <CareerGrowthDashboard />}

          {activeTab === "memory" && <MemoryDashboard />}

          {activeTab === "interview" && <MockInterview />}

          {activeTab === "resume" && <ResumeWorkspace />}

          {activeTab === "jd" && <JDAnalyzer />}
          {activeTab === "optimize" && <ResumeOptimizer />}

          {activeTab === "monitor" && <LLMMonitor />}
        </main>
      </div>
    </div>
  );
}
