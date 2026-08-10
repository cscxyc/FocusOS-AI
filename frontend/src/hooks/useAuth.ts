"use client";

import { useMutation } from "@tanstack/react-query";
import { useAuthStore } from "@/store/authStore";
import { LoginRequest, RegisterRequest } from "@/lib/types";

export function useAuth() {
  const { login, register, logout, isAuthenticated, user, isLoading } = useAuthStore();

  const loginMutation = useMutation({
    mutationFn: (data: LoginRequest) => login(data),
  });

  const registerMutation = useMutation({
    mutationFn: (data: RegisterRequest) => register(data),
  });

  const logoutMutation = useMutation({
    mutationFn: () => Promise.resolve(logout()),
  });

  return {
    login: loginMutation.mutate,
    register: registerMutation.mutate,
    logout: logoutMutation.mutate,
    isAuthenticated,
    user,
    isLoading: isLoading || loginMutation.isPending || registerMutation.isPending,
    error: loginMutation.error || registerMutation.error,
  };
}
