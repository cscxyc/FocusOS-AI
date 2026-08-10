"use client";

import { create } from "zustand";
import { User, LoginRequest, RegisterRequest } from "@/lib/types";
import { getToken, setToken, clearToken, getUser, setUser } from "@/lib/auth";
import api from "@/lib/api";

const REFRESH_TOKEN_KEY = "focusos_refresh_token";

function setRefreshToken(token: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

function clearRefreshToken(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function setAuthCookies(accessToken: string, refreshToken: string) {
  if (typeof document === "undefined") return;
  document.cookie = 'accessToken=' + accessToken + '; path=/; SameSite=Lax; max-age=86400';
  document.cookie = 'refreshToken=' + refreshToken + '; path=/; SameSite=Lax; max-age=604800';
}

function clearAuthCookies() {
  if (typeof document === "undefined") return;
  document.cookie = 'accessToken=; path=/; SameSite=Lax; max-age=0';
  document.cookie = 'refreshToken=; path=/; SameSite=Lax; max-age=0';
}

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  clearError: () => void;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,

  initialize: () => {
    const token = getToken();
    const user = getUser();
    if (token && user) {
      set({ token, user, isAuthenticated: true });
    }
  },

  login: async (data) => {
    set({ isLoading: true, error: null });
    try {
      const response = await api.post<{ accessToken: string; refreshToken: string; user: User }>(
        "/api/auth/login",
        data
      );
      setToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setUser(response.user);
      setAuthCookies(response.accessToken, response.refreshToken);
      set({
        user: response.user,
        token: response.accessToken,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
      set({
        isLoading: false,
        error: error instanceof Error ? error.message : "登录失败",
      });
      throw error;
    }
  },

  register: async (data) => {
    set({ isLoading: true, error: null });
    try {
      const response = await api.post<{ accessToken: string; refreshToken: string; user: User }>(
        "/api/auth/register",
        data
      );
      setToken(response.accessToken);
      setRefreshToken(response.refreshToken);
      setUser(response.user);
      setAuthCookies(response.accessToken, response.refreshToken);
      set({
        user: response.user,
        token: response.accessToken,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
      set({
        isLoading: false,
        error: error instanceof Error ? error.message : "注册失败",
      });
      throw error;
    }
  },

  logout: () => {
    clearToken();
    clearRefreshToken();
    clearAuthCookies();
    set({ user: null, token: null, isAuthenticated: false, error: null });
  },

  clearError: () => set({ error: null }),
}));
