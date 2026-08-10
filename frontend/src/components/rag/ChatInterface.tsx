"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Send, Bot, User, Paperclip, Sparkles, Search, AlertCircle } from "lucide-react";
import { useRAGStore } from "@/store/ragStore";
import { useRAG } from "@/hooks/useRAG";
import { AnimatePresence, motion } from "framer-motion";

export function ChatInterface() {
  const [input, setInput] = React.useState("");
  const [searchQuery, setSearchQuery] = React.useState("");
  const chatHistory = useRAGStore((s) => s.chatHistory);
  const addMessage = useRAGStore((s) => s.addMessage);
  const clearChatHistory = useRAGStore((s) => s.clearChatHistory);
  const { chatMutation } = useRAG();
  const messagesEndRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatHistory, chatMutation.isPending]);

  const handleSend = () => {
    if (!input.trim() || chatMutation.isPending) return;
    const message = input;
    addMessage({
      id: Date.now().toString(),
      role: "user",
      content: message,
      timestamp: new Date().toISOString(),
    });
    setInput("");
    chatMutation.mutate(message);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Card className="flex flex-col h-[600px]">
      <div className="flex items-center justify-between border-b border-gray-200 dark:border-gray-800 px-6 py-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
            <Bot className="h-5 w-5 text-white" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white">知识库对话</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">基于你的文档进行问答</p>
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={clearChatHistory}>清空对话</Button>
      </div>

      <div className="px-6 py-3 border-b border-gray-200 dark:border-gray-800">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="语义搜索知识库..."
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 pl-10 pr-4 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
          />
        </div>
      </div>

      <CardContent className="flex-1 overflow-y-auto p-4 space-y-4">
        <AnimatePresence>
          {chatHistory.map((message) => (
            <motion.div
              key={message.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className={`flex gap-3 ${message.role === "user" ? "flex-row-reverse" : ""}`}
            >
              <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${message.role === "user" ? "bg-brand-500 text-white" : "bg-gray-100 dark:bg-gray-800"}`}>
                {message.role === "user" ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
              </div>
              <div className={`flex flex-col gap-1 max-w-[75%] ${message.role === "user" ? "items-end" : "items-start"}`}>
                <div className={`rounded-2xl px-4 py-2.5 text-sm ${message.role === "user" ? "bg-brand-500 text-white" : "bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100"}`}>
                  {message.content}
                </div>
                {message.sources && message.sources.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {message.sources.map((source, idx) => (
                      <span key={idx} className="inline-flex items-center gap-1 rounded-md bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[10px] text-gray-500">
                        <Paperclip className="h-2.5 w-2.5" />{source.documentName}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </motion.div>
          ))}
        </AnimatePresence>

        {chatMutation.isPending && (
          <div className="flex gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-800">
              <Bot className="h-4 w-4" />
            </div>
            <div className="flex items-center gap-1 rounded-2xl bg-gray-100 dark:bg-gray-800 px-4 py-3">
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: "0ms" }} />
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: "150ms" }} />
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: "300ms" }} />
            </div>
          </div>
        )}

        {chatMutation.isError && (
          <div className="flex gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-red-100 dark:bg-red-900/30">
              <AlertCircle className="h-4 w-4 text-red-500" />
            </div>
            <div className="rounded-2xl bg-red-50 dark:bg-red-900/20 px-4 py-2.5 text-sm text-red-600 dark:text-red-400">
              回答失败：{(chatMutation.error as Error)?.message || "请稍后重试"}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </CardContent>

      <div className="border-t border-gray-200 dark:border-gray-800 p-4">
        <div className="flex items-end gap-2">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入你的问题..."
            rows={1}
            className="flex-1 resize-none rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
          />
          <Button onClick={handleSend} disabled={!input.trim() || chatMutation.isPending} className="shrink-0">
            <Send className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <Sparkles className="h-3 w-3 text-brand-500" />
          <span className="text-[10px] text-gray-500">AI 回答可能包含错误信息，请核对重要信息</span>
        </div>
      </div>
    </Card>
  );
}
