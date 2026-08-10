"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import { ScheduleEvent, CreateEventRequest } from "@/lib/types";

/** GET /api/schedule/events — list all schedule events. */
export function useEvents() {
  return useQuery({
    queryKey: ["schedule-events"],
    queryFn: () => api.get<ScheduleEvent[]>("/api/schedule/events"),
    staleTime: 60 * 1000,
  });
}

/** POST /api/schedule/events — create a new schedule event. */
export function useCreateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (event: CreateEventRequest) =>
      api.post<ScheduleEvent>("/api/schedule/events", event),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["schedule-events"] }),
  });
}

/** PUT /api/schedule/events/{id} — update an existing schedule event. */
export function useUpdateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, updates }: { id: string; updates: Partial<ScheduleEvent> }) =>
      api.put<ScheduleEvent>(`/api/schedule/events/${id}`, updates),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["schedule-events"] }),
  });
}

/** PUT /api/schedule/events/{id}/complete — set completed status for schedule event. */
export function useSetEventCompleted() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, completed }: { id: string; completed: boolean }) =>
      api.put<ScheduleEvent>(`/api/schedule/events/${id}/complete`, { completed }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["schedule-events"] }),
  });
}

/** DELETE /api/schedule/events/{id} — delete a schedule event. */
export function useDeleteEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/schedule/events/${id}`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["schedule-events"] }),
  });
}
