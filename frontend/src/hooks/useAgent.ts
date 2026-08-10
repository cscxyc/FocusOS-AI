"use client";

import { api } from "@/lib/api";
import { useMutation } from "@tanstack/react-query";
import { AgentChatRequest, AgentChatResponse, SmartChatRequest, SmartChatResponse } from "@/lib/types";

export function useAgentChat() {
  return useMutation({
    mutationFn: async (data: AgentChatRequest) => {
      return api.post<AgentChatResponse>("/agent/chat", data);
    },
  });
}

export function useSmartChat() {
  return useMutation({
    mutationFn: async (data: SmartChatRequest) => {
      return api.post<SmartChatResponse>("/agent/smart-chat", data);
    },
  });
}
