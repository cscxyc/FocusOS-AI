"use client";

import { create } from "zustand";
import { ScheduleEvent } from "@/lib/types";

interface ScheduleState {
  events: ScheduleEvent[];
  isLoading: boolean;
  setEvents: (events: ScheduleEvent[]) => void;
  addEvent: (event: ScheduleEvent) => void;
  updateEvent: (id: string, updates: Partial<ScheduleEvent>) => void;
  deleteEvent: (id: string) => void;
  toggleEventComplete: (id: string) => void;
  setIsLoading: (value: boolean) => void;
}

export const useScheduleStore = create<ScheduleState>((set) => ({
  events: [],
  isLoading: false,

  setEvents: (events) => set({ events }),
  addEvent: (event) => set((state) => ({ events: [...state.events, event] })),
  updateEvent: (id, updates) =>
    set((state) => ({
      events: state.events.map((e) => (e.id === id ? { ...e, ...updates } : e)),
    })),
  deleteEvent: (id) =>
    set((state) => ({ events: state.events.filter((e) => e.id !== id) })),
  toggleEventComplete: (id) =>
    set((state) => ({
      events: state.events.map((e) =>
        e.id === id ? { ...e, completed: !e.completed } : e
      ),
    })),
  setIsLoading: (value) => set({ isLoading: value }),
}));
