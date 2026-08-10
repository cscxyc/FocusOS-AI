"use client";

import { create } from "zustand";
import { Document, ChatMessage } from "@/lib/types";

interface RAGState {
  documents: Document[];
  chatHistory: ChatMessage[];
  isUploading: boolean;
  isProcessing: boolean;
  isSearching: boolean;
  uploadProgress: number;
  setDocuments: (documents: Document[]) => void;
  addDocument: (doc: Document) => void;
  removeDocument: (id: string) => void;
  updateDocumentStatus: (id: string, status: Document["status"]) => void;
  setChatHistory: (messages: ChatMessage[]) => void;
  addMessage: (message: ChatMessage) => void;
  clearChatHistory: () => void;
  setIsUploading: (value: boolean) => void;
  setIsProcessing: (value: boolean) => void;
  setUploadProgress: (progress: number) => void;
}

export const useRAGStore = create<RAGState>((set) => ({
  documents: [],
  chatHistory: [],
  isUploading: false,
  isProcessing: false,
  isSearching: false,
  uploadProgress: 0,

  setDocuments: (documents) => set({ documents }),
  addDocument: (doc) =>
    set((state) => ({ documents: [doc, ...state.documents] })),
  removeDocument: (id) =>
    set((state) => ({
      documents: state.documents.filter((d) => d.id !== id),
    })),
  updateDocumentStatus: (id, status) =>
    set((state) => ({
      documents: state.documents.map((d) =>
        d.id === id ? { ...d, status } : d
      ),
    })),
  setChatHistory: (messages) => set({ chatHistory: messages }),
  addMessage: (message) =>
    set((state) => ({ chatHistory: [...state.chatHistory, message] })),
  clearChatHistory: () => set({ chatHistory: [] }),
  setIsUploading: (value) => set({ isUploading: value }),
  setIsProcessing: (value) => set({ isProcessing: value }),
  setUploadProgress: (progress) => set({ uploadProgress: progress }),
}));
