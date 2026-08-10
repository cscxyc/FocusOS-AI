"use client";

import { create } from "zustand";
import { LearningPlan, PomodoroSession, DailyReview } from "@/lib/types";

interface LearningState {
  plans: LearningPlan[];
  currentPlanId: string | null;
  pomodoroSessions: PomodoroSession[];
  dailyReviews: DailyReview[];
  activePomodoro: { planId: string; remainingTime: number; isRunning: boolean } | null;
  setPlans: (plans: LearningPlan[]) => void;
  setCurrentPlan: (planId: string | null) => void;
  addPomodoroSession: (session: PomodoroSession) => void;
  startPomodoro: (planId: string, duration?: number) => void;
  pausePomodoro: () => void;
  resumePomodoro: () => void;
  stopPomodoro: () => void;
  tickPomodoro: () => void;
  setDailyReviews: (reviews: DailyReview[]) => void;
  addDailyReview: (review: DailyReview) => void;
}

export const useLearningStore = create<LearningState>((set) => ({
  plans: [],
  currentPlanId: null,
  pomodoroSessions: [],
  dailyReviews: [],
  activePomodoro: null,

  setPlans: (plans) => set({ plans }),
  setCurrentPlan: (planId) => set({ currentPlanId: planId }),

  addPomodoroSession: (session) =>
    set((state) => ({
      pomodoroSessions: [...state.pomodoroSessions, session],
    })),

  startPomodoro: (planId, duration = 25 * 60) =>
    set({
      activePomodoro: { planId, remainingTime: duration, isRunning: true },
    }),

  pausePomodoro: () =>
    set((state) => ({
      activePomodoro: state.activePomodoro
        ? { ...state.activePomodoro, isRunning: false }
        : null,
    })),

  resumePomodoro: () =>
    set((state) => ({
      activePomodoro: state.activePomodoro
        ? { ...state.activePomodoro, isRunning: true }
        : null,
    })),

  stopPomodoro: () => set({ activePomodoro: null }),

  tickPomodoro: () =>
    set((state) => {
      if (!state.activePomodoro || !state.activePomodoro.isRunning)
        return state;
      const newTime = state.activePomodoro.remainingTime - 1;
      if (newTime <= 0) {
        return { activePomodoro: null };
      }
      return {
        activePomodoro: { ...state.activePomodoro, remainingTime: newTime },
      };
    }),

  setDailyReviews: (reviews) => set({ dailyReviews: reviews }),
  addDailyReview: (review) =>
    set((state) => ({ dailyReviews: [review, ...state.dailyReviews] })),
}));
