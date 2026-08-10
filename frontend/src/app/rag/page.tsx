"use client";

import * as React from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { DocumentUpload } from "@/components/rag/DocumentUpload";
import { DocumentList } from "@/components/rag/DocumentList";
import { ChatInterface } from "@/components/rag/ChatInterface";
import { Database, MessageSquare } from "lucide-react";
import { useState } from "react";

export default function RAGPage() {
  const [activeTab, setActiveTab] = useState<"chat" | "documents">("chat");

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">个人知识库</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                上传文档，AI 智能检索与问答
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setActiveTab("chat")}
                className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                  activeTab === "chat"
                    ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                    : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
                }`}
              >
                <MessageSquare className="h-4 w-4" />
                AI 对话
              </button>
              <button
                onClick={() => setActiveTab("documents")}
                className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all ${
                  activeTab === "documents"
                    ? "bg-brand-500 text-white shadow-lg shadow-brand-500/25"
                    : "bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50"
                }`}
              >
                <Database className="h-4 w-4" />
                文档管理
              </button>
            </div>
          </div>

          {activeTab === "chat" ? (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2">
                <ChatInterface />
              </div>
              <div>
                <DocumentList />
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              <DocumentUpload />
              <DocumentList />
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
