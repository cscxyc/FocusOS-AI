"use client";

import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface ResumePreviewProps {
  content: string;
  className?: string;
}

/**
 * Sprint 7-C-B: Markdown 简历预览组件
 * 使用 react-markdown + remark-gfm 渲染 GFM 语法的 Markdown 简历
 */
export function ResumePreview({ content, className }: ResumePreviewProps) {
  if (!content || content.trim() === "") {
    return (
      <div className="flex items-center justify-center py-16 text-gray-400 dark:text-gray-500">
        <p className="text-sm">暂无简历内容</p>
      </div>
    );
  }

  return (
    <div
      className={`prose prose-sm dark:prose-invert max-w-none
        prose-headings:font-semibold prose-headings:text-gray-900 dark:prose-headings:text-white
        prose-h1:text-2xl prose-h1:border-b prose-h1:border-gray-200 dark:prose-h1:border-gray-700 prose-h1:pb-2 prose-h1:mb-4
        prose-h2:text-lg prose-h2:text-brand-600 dark:prose-h2:text-brand-400 prose-h2:mt-6 prose-h2:mb-3
        prose-h3:text-base prose-h3:mt-4 prose-h3:mb-2
        prose-p:text-gray-700 dark:prose-p:text-gray-300 prose-p:leading-relaxed prose-p:my-2
        prose-li:text-gray-700 dark:prose-li:text-gray-300 prose-li:my-1
        prose-ul:my-2 prose-ol:my-2
        prose-strong:text-gray-900 dark:prose-strong:text-white
        prose-code:text-brand-600 dark:prose-code:text-brand-400 prose-code:bg-gray-100 dark:prose-code:bg-gray-800 prose-code:px-1 prose-code:py-0.5 prose-code:rounded prose-code:before:content-none prose-code:after:content-none
        prose-code:text-sm
        prose-blockquote:border-l-brand-400 prose-blockquote:bg-gray-50 dark:prose-blockquote:bg-gray-800/50
        ${className || ""}`}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}
