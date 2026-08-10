"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import { Document, ChatRequest, ChatResponse } from "@/lib/types";
import { useRAGStore } from "@/store/ragStore";

/** GET /api/rag/documents — list all documents in the knowledge base. */
export function useDocuments() {
  return useQuery({
    queryKey: ["rag-documents"],
    queryFn: () => api.get<Document[]>("/api/rag/documents"),
    staleTime: 5 * 60 * 1000,
  });
}

/** POST /api/rag/documents/upload — upload a new document (multipart/form-data). */
export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (formData: FormData) =>
      api.post<Document>("/api/rag/documents/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["rag-documents"] }),
  });
}

/** POST /api/rag/documents/{id}/vectorize — (re)vectorize an uploaded document. */
export function useVectorize() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (documentId: string) =>
      api.post<Document>(`/api/rag/documents/${documentId}/vectorize`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["rag-documents"] }),
  });
}

/** POST /api/rag/chat — ask a question against the knowledge base. */
export function useChat() {
  return useMutation({
    mutationFn: (data: ChatRequest) =>
      api.post<ChatResponse>("/api/rag/chat", data),
  });
}

/** DELETE /api/rag/documents/{id} — remove a document from the knowledge base. */
export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (documentId: string) =>
      api.delete<void>(`/api/rag/documents/${documentId}`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["rag-documents"] }),
  });
}

// ===== Backward-compatible aggregate hook =====
// Kept so existing components (ChatInterface, DocumentList, DocumentUpload)
// keep working while new code migrates to the granular hooks above.

export function useRAG() {
  const queryClient = useQueryClient();
  const chatHistory = useRAGStore((s) => s.chatHistory);
  const addMessage = useRAGStore((s) => s.addMessage);
  const clearChatHistory = useRAGStore((s) => s.clearChatHistory);
  const setIsUploading = useRAGStore((s) => s.setIsUploading);
  const setIsProcessing = useRAGStore((s) => s.setIsProcessing);

  const documentsQuery = useDocuments();

  const uploadMutation = useMutation({
    mutationFn: async (formData: FormData) => {
      setIsUploading(true);
      const data = await api.post<Document>("/api/rag/documents/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      return data;
    },
    onSuccess: () => {
      setIsUploading(false);
      queryClient.invalidateQueries({ queryKey: ["rag-documents"] });
    },
    onError: () => setIsUploading(false),
  });

  const chatMutation = useMutation({
    mutationFn: async (message: string) => {
      setIsProcessing(true);
      const data = await api.post<ChatResponse>("/api/rag/chat", { message });
      return data;
    },
    onSuccess: (data: ChatResponse) => {
      setIsProcessing(false);
      if (data?.response) {
        addMessage({
          id: Date.now().toString(),
          role: "assistant",
          content: data.response,
          timestamp: new Date().toISOString(),
          sources: data.sources,
        });
      }
    },
    onError: () => setIsProcessing(false),
  });

  const deleteDocumentMutation = useDeleteDocument();

  return {
    documents: documentsQuery.data ?? [],
    isDocumentsLoading: documentsQuery.isLoading,
    chatHistory,
    clearChatHistory,
    uploadMutation,
    chatMutation,
    vectorizeDocument: useVectorize().mutate,
    deleteDocument: deleteDocumentMutation.mutate,
    isUploading: uploadMutation.isPending,
    isProcessing: chatMutation.isPending,
  };
}
