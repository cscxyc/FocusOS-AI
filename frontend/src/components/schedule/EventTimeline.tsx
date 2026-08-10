"use client";

import * as React from "react";
import { ScheduleEvent } from "@/lib/types";
import { useScheduleStore } from "@/store/scheduleStore";
import { Check, Clock, MapPin, GripVertical } from "lucide-react";
import { cn } from "@/lib/utils";
import { format } from "date-fns";
import { zhCN } from "date-fns/locale";

const priorityColors = {
  low: "bg-gray-400",
  medium: "bg-amber-400",
  high: "bg-red-500",
};

const priorityLabels = {
  low: "低",
  medium: "中",
  high: "高",
};

export function EventTimeline() {
  const events = useScheduleStore((s) => s.events);
  const toggleEventComplete = useScheduleStore((s) => s.toggleEventComplete);
  const deleteEvent = useScheduleStore((s) => s.deleteEvent);

  const sortedEvents = [...events].sort(
    (a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  );

  return (
    <div className="relative">
      <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-gradient-to-b from-brand-500 via-accent-500 to-transparent" />
      <div className="space-y-4">
        {sortedEvents.map((event) => (
          <div key={event.id} className="relative pl-10">
            <div
              className={cn(
                "absolute left-2.5 top-4 h-3 w-3 rounded-full border-2 border-white dark:border-gray-900",
                priorityColors[event.priority],
                event.completed && "opacity-50"
              )}
            />
            <div
              className={cn(
                "group rounded-xl border p-4 transition-all hover:shadow-md",
                event.completed
                  ? "bg-gray-50 dark:bg-gray-800/50 border-gray-200 dark:border-gray-800"
                  : "bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-800"
              )}
            >
              <div className="flex items-start gap-3">
                <button
                  onClick={() => toggleEventComplete(event.id)}
                  className={cn(
                    "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2 transition-all",
                    event.completed
                      ? "bg-brand-500 border-brand-500 text-white"
                      : "border-gray-300 dark:border-gray-600 hover:border-brand-500"
                  )}
                >
                  {event.completed && <Check className="h-3 w-3" />}
                </button>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <h4 className={cn("font-medium", event.completed && "line-through text-gray-500")}>
                      {event.title}
                    </h4>
                    <span className={cn("text-[10px] px-1.5 py-0.5 rounded-full font-medium", priorityColors[event.priority] + " text-white")}>
                      {priorityLabels[event.priority]}
                    </span>
                  </div>
                  {event.description && (
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-2">
                      {event.description}
                    </p>
                  )}
                  <div className="flex items-center gap-3 mt-1 text-xs text-gray-500 dark:text-gray-400">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {format(new Date(event.startTime), "HH:mm")} - {format(new Date(event.endTime), "HH:mm")}
                    </span>
                    {event.location && (
                      <span className="flex items-center gap-1">
                        <MapPin className="h-3 w-3" />
                        {event.location}
                      </span>
                    )}
                    <span className="px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-800">
                      {event.category}
                    </span>
                  </div>
                </div>
                <button
                  onClick={() => deleteEvent(event.id)}
                  className="p-1.5 rounded-lg opacity-0 group-hover:opacity-100 hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500 transition-all"
                >
                  <GripVertical className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
