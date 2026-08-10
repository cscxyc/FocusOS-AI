"use client";

import { create } from "zustand";
import { Target } from "@/lib/types";

interface DashboardState {
  greeting: string;
  todayTargets: Target[];
  completionRate: number;
  aiSuggestion: string | null;
  streakDays: number;
  setGreeting: (greeting: string) => void;
  setTodayTargets: (targets: Target[]) => void;
  updateTargetProgress: (targetId: string, progress: number) => void;
  setCompletionRate: (rate: number) => void;
  setAISuggestion: (suggestion: string) => void;
  setStreakDays: (days: number) => void;
}

export const useDashboardStore = create<DashboardState>((set) => ({
  greeting: "",
  todayTargets: [],
  completionRate: 0,
  aiSuggestion: null,
  streakDays: 0,

  setGreeting: (greeting) => set({ greeting }),
  setTodayTargets: (targets) => set({ todayTargets: targets }),
  updateTargetProgress: (targetId, progress) =>
    set((state) => ({
      todayTargets: state.todayTargets.map((t) =>
        t.id === targetId ? { ...t, progress } : t
      ),
    })),
  setCompletionRate: (rate) => set({ completionRate: rate }),
  setAISuggestion: (suggestion) => set({ aiSuggestion: suggestion }),
  setStreakDays: (days) => set({ streakDays: days }),
}));
