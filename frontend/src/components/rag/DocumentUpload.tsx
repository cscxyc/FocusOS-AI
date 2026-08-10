"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Upload, FileText, X, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { useRAG } from "@/hooks/useRAG";

export function DocumentUpload() {
  const [isDragging, setIsDragging] = React.useState(false);
  const [uploadedFiles, setUploadedFiles] = React.useState<File[]>([]);
  const { uploadMutation } = useRAG();
  const isUploading = uploadMutation.isPending;

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => setIsDragging(false);

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const files = Array.from(e.dataTransfer.files);
    setUploadedFiles((prev) => [...prev, ...files]);
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    setUploadedFiles((prev) => [...prev, ...files]);
  };

  const handleRemoveFile = (index: number) => {
    setUploadedFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpload = () => {
    if (uploadedFiles.length === 0) return;
    uploadedFiles.forEach((file) => {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("title", file.name);
      formData.append("category", "general");
      formData.append("tags", "");
      uploadMutation.mutate(formData);
    });
    setUploadedFiles([]);
  };

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  return (
    <Card>
      <CardContent className="p-6">
        <div
          className={cn(
            "relative border-2 border-dashed rounded-2xl p-8 text-center transition-all duration-300",
            isDragging
              ? "border-brand-500 bg-brand-50 dark:bg-brand-900/20"
              : "border-gray-300 dark:border-gray-700 hover:border-brand-400 hover:bg-gray-50 dark:hover:bg-gray-800/50"
          )}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          <div className="flex flex-col items-center gap-4">
            <div className={cn(
              "flex h-16 w-16 items-center justify-center rounded-2xl transition-all",
              isDragging
                ? "bg-brand-500 text-white"
                : "bg-gray-100 dark:bg-gray-800 text-gray-500"
            )}>
              <Upload className="h-8 w-8" />
            </div>
            <div>
              <p className="text-lg font-semibold text-gray-900 dark:text-white">
                拖拽文件到此处上传
              </p>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                支持 PDF、Markdown、Word、TXT 等格式
              </p>
            </div>
            <label className="cursor-pointer">
              <input
                type="file"
                multiple
                className="hidden"
                onChange={handleFileSelect}
                accept=".pdf,.md,.doc,.docx,.txt,.csv,.json"
              />
              <span className="inline-flex items-center gap-2 rounded-xl bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 transition-colors">
                选择文件
              </span>
            </label>
          </div>
        </div>

        {uploadMutation.isError && (
          <div className="mt-4 p-3 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-sm text-red-600 dark:text-red-400">
            上传失败：{(uploadMutation.error as Error)?.message || "请稍后重试"}
          </div>
        )}

        {uploadMutation.isSuccess && uploadedFiles.length === 0 && (
          <div className="mt-4 p-3 rounded-xl bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 text-sm text-green-600 dark:text-green-400 flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4" />
            文件上传成功
          </div>
        )}

        {uploadedFiles.length > 0 && (
          <div className="mt-4 space-y--2">
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              待上传文件 ({uploadedFiles.length})
            </p>
            <div className="space-y-2 max-h-40 overflow-y-auto">
              {uploadedFiles.map((file, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-3 rounded-xl bg-gray-50 dark:bg-gray-800"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <FileText className="h-4 w-4 text-brand-500 shrink-0" />
                    <span className="text-sm truncate">{file.name}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-xs text-gray-500">{formatSize(file.size)}</span>
                    <button
                      onClick={() => handleRemoveFile(index)}
                      className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-700"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <button
              onClick={handleUpload}
              disabled={isUploading}
              className="w-full mt-3 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-600 to-accent-600 px-4 py-2 text-sm font-medium text-white hover:shadow-lg transition-all disabled:opacity-50"
            >
              {isUploading ? "上传中..." : `上传 ${uploadedFiles.length} 个文件`}
            </button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
