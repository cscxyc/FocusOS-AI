"use client";

import * as React from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { WorkflowMonitor } from "@/components/dashboard/WorkflowMonitor";

export default function WorkflowPage() {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
              AI Workflow Monitor
            </h1>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Sprint 6-B · 异步多 Agent 协作 · SSE 实时进度推送
            </p>
          </div>
          <WorkflowMonitor />
        </main>
      </div>
    </div>
  );
}
