"use client";

import { create } from "zustand";
import { Resume, JDAnalysis } from "@/lib/types";

interface CareerState {
  resumes: Resume[];
  currentResumeId: string | null;
  jdAnalyses: JDAnalysis[];
  isAnalyzing: boolean;
  isOptimizing: boolean;
  setResumes: (resumes: Resume[]) => void;
  addResume: (resume: Resume) => void;
  removeResume: (id: string) => void;
  setCurrentResume: (id: string | null) => void;
  addJDAnalysis: (analysis: JDAnalysis) => void;
  setIsAnalyzing: (value: boolean) => void;
  setIsOptimizing: (value: boolean) => void;
}

export const useCareerStore = create<CareerState>((set) => ({
  resumes: [],
  currentResumeId: null,
  jdAnalyses: [],
  isAnalyzing: false,
  isOptimizing: false,

  setResumes: (resumes) => set({ resumes }),
  addResume: (resume) =>
    set((state) => ({ resumes: [resume, ...state.resumes] })),
  removeResume: (id) =>
    set((state) => ({ resumes: state.resumes.filter((r) => r.id !== id) })),
  setCurrentResume: (id) => set({ currentResumeId: id }),
  addJDAnalysis: (analysis) =>
    set((state) => ({ jdAnalyses: [analysis, ...state.jdAnalyses] })),
  setIsAnalyzing: (value) => set({ isAnalyzing: value }),
  setIsOptimizing: (value) => set({ isOptimizing: value }),
}));
