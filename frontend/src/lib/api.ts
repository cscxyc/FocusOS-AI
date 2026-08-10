import axios, { AxiosRequestConfig, AxiosError, InternalAxiosRequestConfig } from "axios";
import { getToken, setToken, clearToken } from "./auth";

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

const REFRESH_TOKEN_KEY = "focusos_refresh_token";

function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function setRefreshToken(token: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

function clearRefreshToken(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

const instance = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

instance.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string) => void; reject: (error: any) => void }> = [];

function processQueue(error: any, token: string | null = null) {
  failedQueue.forEach(p => {
    if (error) p.reject(error);
    else if (token) p.resolve(token);
  });
  failedQueue = [];
}

function logoutAndClear() {
  clearToken();
  clearRefreshToken();
  clearAuthCookies();
  if (typeof window !== "undefined") {
    window.location.href = "/login";
  }
}

instance.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body === "object" && "code" in body && "data" in body) {
      return body.data;
    }
    return body;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401) {
      const errorData = error.response?.data as any;
      if (originalRequest.url?.includes("/auth/refresh")) {
        logoutAndClear();
        const message =
          errorData?.message ||
          errorData?.error ||
          "认证已过期，请重新登录";
        return Promise.reject(new Error(message));
      }

      if (!originalRequest._retry) {
        if (!isRefreshing) {
          isRefreshing = true;
          originalRequest._retry = true;

          try {
            const refreshToken = getRefreshToken();
            if (!refreshToken) {
              logoutAndClear();
              processQueue(new Error("无刷新令牌"), null);
              const message =
                errorData?.message ||
                errorData?.error ||
                "认证失败，请重新登录";
              return Promise.reject(new Error(message));
            }

            const refreshResponse = await axios.post<{ accessToken: string; refreshToken?: string }>(
              (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080") + "/api/auth/refresh",
              { refreshToken }
            );

            const responseData = refreshResponse.data;
            const data =
              responseData && typeof responseData === "object" && "data" in responseData
                ? (responseData as any).data
                : responseData;

            const newAccessToken = data.accessToken;
            const newRefreshToken = data.refreshToken || refreshToken;

            setToken(newAccessToken);
            setRefreshToken(newRefreshToken);
            setAuthCookies(newAccessToken, newRefreshToken);
            processQueue(null, newAccessToken);

            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            }
            return instance(originalRequest);
          } catch (refreshError) {
            logoutAndClear();
            processQueue(refreshError, null);
            const message =
              (refreshError as any).response?.data?.message ||
              (refreshError as any).response?.data?.error ||
              "认证刷新失败，请重新登录";
            return Promise.reject(new Error(message));
          } finally {
            isRefreshing = false;
          }
        } else {
          return new Promise<string>((resolve, reject) => {
            failedQueue.push({
              resolve: (token: string) => {
                if (originalRequest.headers) {
                  originalRequest.headers.Authorization = `Bearer ${token}`;
                }
                resolve(instance(originalRequest) as any);
              },
              reject: (err: any) => reject(err),
            });
          }) as any;
        }
      }

      logoutAndClear();
    }

    const respData = error.response?.data as any;
    const message =
      respData?.message ||
      respData?.error ||
      "请求失败，请稍后重试";
    return Promise.reject(new Error(message));
  }
);

interface ApiClient {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
}

const api = instance as unknown as ApiClient;

// ============ Sprint 8-C: Personal Memory System ============
import type {
  UserMemory,
  CreateMemoryRequest,
  ExtractMemoryRequest,
} from "./types";

/**
 * 查询当前用户全部记忆
 * @param params.userId       用户 ID（QA 模式下使用，登录态可省略）
 * @param params.minConfidence 最小可信度 0.0~1.0，可选
 * @param params.limit        最大条数，默认 200
 */
export async function listMemories(params?: {
  userId?: number;
  minConfidence?: number;
  limit?: number;
}): Promise<UserMemory[]> {
  const url = new URL("/api/memory", "http://localhost");
  if (params?.userId != null) url.searchParams.set("userId", String(params.userId));
  if (params?.minConfidence != null)
    url.searchParams.set("minConfidence", String(params.minConfidence));
  if (params?.limit != null) url.searchParams.set("limit", String(params.limit));
  // 使用 pathname + search（origin 不影响，axios 会拼 baseURL）
  const path = url.pathname + url.search;
  return api.get<UserMemory[]>(path);
}

/** 按记忆类型分类查询 */
export async function listMemoriesByType(
  type: string,
  userId?: number,
): Promise<UserMemory[]> {
  const url = new URL(`/api/memory/type/${encodeURIComponent(type)}`, "http://localhost");
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.get<UserMemory[]>(url.pathname + url.search);
}

/** 创建 / 合并一条记忆 */
export async function createMemory(req: CreateMemoryRequest): Promise<UserMemory> {
  return api.post<UserMemory>("/api/memory", req);
}

/** 删除一条记忆（仅允许本人） */
export async function deleteMemory(
  id: number,
  userId?: number,
): Promise<void> {
  const url = new URL(`/api/memory/${encodeURIComponent(String(id))}`, "http://localhost");
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.delete<void>(url.pathname + url.search);
}

/** 手动触发 MemoryAgent 提取结构化记忆并入库 */
export async function extractMemory(
  req: ExtractMemoryRequest,
): Promise<UserMemory[]> {
  return api.post<UserMemory[]>("/api/memory/extract", req);
}

// ============ Sprint 8-D: Agent Evaluation System ============
import type {
  EvaluationRecord,
  CreateEvaluationRequest,
  AgentScoreRanking,
  ScoreTrendPoint,
  EvaluationIssue,
  GroundingCheckRequest,
  GroundingResult,
  RAGEvalRequest,
  RAGMetrics,
  PromptVersion,
  CreatePromptVersionRequest,
  PromptVersionComparison,
} from "./types";

/** 创建评估记录 POST /api/evaluation */
export async function createEvaluation(
  req: CreateEvaluationRequest,
): Promise<EvaluationRecord> {
  return api.post<EvaluationRecord>("/api/evaluation", req);
}

/** 查询评估列表 GET /api/evaluation（可选 agentType / evaluationType 过滤） */
export async function listEvaluations(params?: {
  userId?: number;
  agentType?: string;
  evaluationType?: string;
}): Promise<EvaluationRecord[]> {
  const url = new URL("/api/evaluation", "http://localhost");
  if (params?.userId != null) url.searchParams.set("userId", String(params.userId));
  if (params?.agentType) url.searchParams.set("agentType", params.agentType);
  if (params?.evaluationType) url.searchParams.set("evaluationType", params.evaluationType);
  return api.get<EvaluationRecord[]>(url.pathname + url.search);
}

/** 各 Agent 平均得分排行 GET /api/evaluation/ranking */
export async function getAgentScoreRanking(
  userId?: number,
): Promise<AgentScoreRanking[]> {
  const url = new URL("/api/evaluation/ranking", "http://localhost");
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.get<AgentScoreRanking[]>(url.pathname + url.search);
}

/** 评分趋势 GET /api/evaluation/trend */
export async function getScoreTrend(
  userId?: number,
): Promise<ScoreTrendPoint[]> {
  const url = new URL("/api/evaluation/trend", "http://localhost");
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.get<ScoreTrendPoint[]>(url.pathname + url.search);
}

/** 问题分析（按 agentType） GET /api/evaluation/issues/{agentType} */
export async function getEvaluationIssues(
  agentType: string,
  userId?: number,
): Promise<EvaluationIssue[]> {
  const url = new URL(
    `/api/evaluation/issues/${encodeURIComponent(agentType)}`,
    "http://localhost",
  );
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.get<EvaluationIssue[]>(url.pathname + url.search);
}

/** 删除评估记录 DELETE /api/evaluation/{id} */
export async function deleteEvaluation(
  id: number,
  userId?: number,
): Promise<void> {
  const url = new URL(
    `/api/evaluation/${encodeURIComponent(String(id))}`,
    "http://localhost",
  );
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.delete<void>(url.pathname + url.search);
}

/** Grounding 事实依据核查 POST /api/evaluation/grounding */
export async function checkGrounding(
  req: GroundingCheckRequest,
): Promise<GroundingResult> {
  return api.post<GroundingResult>("/api/evaluation/grounding", req);
}

/** RAG 评估 POST /api/evaluation/rag-eval */
export async function evaluateRAG(req: RAGEvalRequest): Promise<RAGMetrics> {
  return api.post<RAGMetrics>("/api/evaluation/rag-eval", req);
}

/** 创建 Prompt 版本 POST /api/evaluation/prompt-version */
export async function createPromptVersion(
  req: CreatePromptVersionRequest,
): Promise<PromptVersion> {
  return api.post<PromptVersion>("/api/evaluation/prompt-version", req);
}

/** 按 agent 查询 Prompt 版本列表 GET /api/evaluation/prompt-version/{agentType} */
export async function listPromptVersions(
  agentType: string,
): Promise<PromptVersion[]> {
  return api.get<PromptVersion[]>(
    `/api/evaluation/prompt-version/${encodeURIComponent(agentType)}`,
  );
}

/** 启用指定 Prompt 版本 PUT /api/evaluation/prompt-version/{id}/enable */
export async function enablePromptVersion(
  id: number,
): Promise<PromptVersion> {
  return api.put<PromptVersion>(
    `/api/evaluation/prompt-version/${encodeURIComponent(String(id))}/enable`,
  );
}

/** Prompt 版本 A/B 对比 GET /api/evaluation/prompt-version/compare/{agentType} */
export async function comparePromptVersions(
  agentType: string,
  userId?: number,
): Promise<PromptVersionComparison[]> {
  const url = new URL(
    `/api/evaluation/prompt-version/compare/${encodeURIComponent(agentType)}`,
    "http://localhost",
  );
  if (userId != null) url.searchParams.set("userId", String(userId));
  return api.get<PromptVersionComparison[]>(url.pathname + url.search);
}

export { api };
export default api;

