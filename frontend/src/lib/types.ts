export interface User {
  id: string;
  username: string;
  email: string;
  avatar?: string;
  bio?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface Target {
  id: string;
  name: string;
  category: "study" | "fitness" | "reading" | "career" | "investment" | "other";
  progress: number;
  goal: number;
  unit: string;
  color: string;
}

export interface DailyProgress {
  date: string;
  completionRate: number;
  targets: Target[];
  aiSuggestion?: string;
}

export interface LearningPlan {
  id: string;
  title: string;
  description: string;
  progress: number;
  totalSessions: number;
  completedSessions: number;
  deadline: string;
  tags: string[];
}

export interface PomodoroSession {
  id: string;
  planId: string;
  startTime: string;
  endTime?: string;
  duration: number;
  completed: boolean;
}

export interface DailyReview {
  id: string;
  date: string;
  achievements: string[];
  challenges: string[];
  aiSummary: string;
  mood: "great" | "good" | "neutral" | "bad";
}

export interface Document {
  id: string;
  name: string;
  type: string;
  size: number;
  uploadedAt: string;
  status: "pending" | "vectorizing" | "ready" | "error";
  chunks?: number;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  sources?: { documentId: string; documentName: string; chunkIndex: number }[];
}

export interface Resume {
  id: string;
  name: string;
  fileUrl: string;
  uploadedAt: string;
  parsedData?: {
    skills: string[];
    experience: string[];
    education: string[];
  };
}

export interface JDAnalysis {
  id: string;
  jdText: string;
  analyzedAt: string;
  matchScore: number;
  matchingSkills: string[];
  missingSkills: string[];
  suggestions: string[];
}

export interface ScheduleEvent {
  id: string;
  title: string;
  description?: string;
  startTime: string;
  endTime: string;
  completed: boolean;
  priority: "low" | "medium" | "high";
  category: string;
  location?: string;
}

export interface ApiError {
  message: string;
  code?: string;
  details?: unknown;
}

// ===== Dashboard =====

/**
 * Backend GET /api/dashboard response.
 * learningStats / careerStats are returned as JSON objects (Map serialized);
 * recentSessions / todayEvents are returned as JSON arrays (List serialized).
 */
export interface DashboardResponse {
  userId: string;
  username: string;
  learningStats: Record<string, unknown>;
  recentSessions: LearningSession[];
  todayEvents: ScheduleEvent[];
  careerStats: Record<string, unknown>;
}

export interface LearningSession {
  id: string;
  planId?: string;
  startTime: string;
  endTime?: string;
  duration: number;
  completed: boolean;
  [key: string]: unknown;
}

/** GET /api/dashboard/ai-recommendation response. */
export interface AIRecommendation {
  suggestion: string;
  agentType?: string;
  [key: string]: unknown;
}

// ===== Learning =====

export interface CreatePlanRequest {
  title: string;
  description?: string;
  deadline?: string;
  tags?: string[];
  totalSessions?: number;
}

export interface GeneratePlanRequest {
  goal: string;
  durationWeeks?: number;
  dailyMinutes?: number;
}

export interface GeneratePlanResponse {
  [key: string]: unknown;
}

export interface DailyReviewResponse {
  id?: string;
  date: string;
  achievements: string[];
  challenges: string[];
  aiSummary: string;
  mood: "great" | "good" | "neutral" | "bad";
  [key: string]: unknown;
}

// ===== Career =====

export interface CareerProfile {
  id?: string;
  username?: string;
  [key: string]: unknown;
}

export interface JDAnalysisRequest {
  jobDescription: string;
  resumeId?: string;
}

export interface JDAnalysisResponse {
  matchScore: number;
  matchingSkills: string[];
  missingSkills: string[];
  suggestions: string[];
  [key: string]: unknown;
}

export interface ResumeOptimizeRequest {
  jobDescription: string;
  resumeId?: string;
}

export interface ResumeOptimizeResponse {
  optimizedResume?: string;
  suggestions?: string[];
  [key: string]: unknown;
}

export interface JobApplication {
  id: string;
  company?: string;
  position?: string;
  status?: string;
  appliedAt?: string;
  [key: string]: unknown;
}

// ===== RAG =====

export interface ChatRequest {
  message: string;
}

export interface ChatResponse {
  response: string;
  sources?: { documentId: string; documentName: string; chunkIndex: number }[];
  [key: string]: unknown;
}

// ===== Schedule =====

export type CreateEventRequest = Omit<ScheduleEvent, "id">;

// ===== Agent =====

export interface AgentChatRequest {
  message: string;
  agentType?: string;
}

export interface AgentChatResponse {
  response: string;
  agentType: string;
}

export interface SmartChatRequest {
  message: string;
}

export interface SmartChatResponse {
  response: string;
}

// ============ Sprint 7-C-B: Resume Workspace ============

export interface ResumeVersion {
  id: number;
  userId: number;
  targetPosition: string;
  versionName: string;
  content?: string;
  sourceReportId?: number | null;
  isActive: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateResumeVersionRequest {
  targetPosition?: string;
  versionName?: string;
  content?: string;
  reportId?: number;
  setActive?: boolean;
}

export interface UpdateResumeVersionRequest {
  content?: string;
  versionName?: string;
}

export interface ResumeDiffResponse {
  added: string[];
  removed: string[];
  changed: Array<{
    section: string;
    before: string;
    after: string;
  }>;
  summary: {
    addedCount: number;
    removedCount: number;
    changedCount: number;
    similarityScore: number;
  };
}

export type ExportFormat = "pdf" | "md" | "docx";

// ============ Sprint 8-A: Resume Evaluation ============

export interface KeywordMatch {
  keyword: string;
  status: "MATCH" | "MISSING";
  evidence: string;
}

export interface SectionScores {
  summary: number;
  experience: number;
  project: number;
  skills: number;
}

export interface ResumeEvaluation {
  score: number;
  matchScore: number;
  atsScore: number;
  starScore: number;
  completenessScore: number;
  strengths: string[];
  weaknesses: string[];
  missingKeywords: string[];
  keywordMatches: KeywordMatch[];
  sectionScores: SectionScores;
  suggestions: string[];
  recommendedActions: string[];
}

export interface ResumeEvaluationReport {
  id: number;
  userId: number;
  resumeVersionId: number;
  careerReportId?: number | null;
  jobTitle?: string;
  company?: string;
  score: number;
  matchScore: number;
  atsScore: number;
  starScore: number;
  completenessScore: number;
  createdAt: string;
  updatedAt?: string;
}

/** POST /api/resume/evaluate 返回结构 */
export interface EvaluateResumeResponse {
  evaluationId: number;
  score: number;
  matchScore: number;
  atsScore: number;
  starScore: number;
  completenessScore: number;
  jobTitle?: string;
  company?: string;
  evaluation: ResumeEvaluation;
}

export interface EvaluateResumeRequest {
  resumeVersionId: number;
  careerReportId?: number;
  jobDescription?: string;
}

// ============ Sprint 8-B: Career Growth ============

export interface SkillGap {
  skill: string;
  importance: "HIGH" | "MEDIUM" | "LOW";
  currentStatus: string;
  targetStatus: string;
  reason: string;
}

export interface LearningStage {
  month: number;
  goal: string;
  skills: string[];
  tasks: string[];
}

export interface WeeklyTask {
  week: number;
  title: string;
  description: string;
  estimatedHours: number;
  priority: "HIGH" | "MEDIUM" | "LOW";
}

export interface ProjectRecommendation {
  name: string;
  purpose: string;
  technologies: string[];
  whyRecommended: string;
}

export interface CareerGrowthPlanData {
  currentLevel: string;
  careerGoal: string;
  skillGaps: SkillGap[];
  roadmap: LearningStage[];
  weeklyTasks: WeeklyTask[];
  projects: ProjectRecommendation[];
  summary: string;
}

export interface CareerGrowthPlan {
  id: number;
  userId: number;
  resumeVersionId: number;
  evaluationId?: number | null;
  targetPosition?: string;
  company?: string;
  currentLevel?: string;
  status?: string;
  createdAt: string;
  updatedAt?: string;
}

/** POST /api/career/growth 返回结构 */
export interface GenerateCareerGrowthResponse {
  planId: number;
  currentLevel?: string;
  targetPosition?: string;
  company?: string;
  status?: string;
  createdAt?: string;
  plan: CareerGrowthPlanData;
}

export interface GenerateCareerGrowthRequest {
  resumeVersionId: number;
  evaluationId?: number;
  careerReportId?: number;
  jobDescription?: string;
}

// ============ Sprint 7-C-B: LLM Call Logs ============

export interface LLMCallLog {
  id: number;
  userId: number;
  workflowId?: string | null;
  agentType: string;
  model: string;
  inputTokens?: number | null;
  outputTokens?: number | null;
  latencyMs?: number | null;
  success: boolean;
  errorMessage?: string | null;
  createdAt: string;
}

export interface LLMCallLogSummary {
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalLatencyMs: number;
  byAgent: Array<{
    agentType: string;
    callCount: number;
    totalTokens: number;
    avgLatencyMs: number;
  }>;
  recentLogs: LLMCallLog[];
}

// ============ Sprint 8-C: Personal Memory System ============

export type MemoryType =
  | "SKILL"
  | "PROJECT"
  | "EXPERIENCE"
  | "GOAL"
  | "LEARNING_PROGRESS"
  | "PREFERENCE"
  | "ACHIEVEMENT";

export interface UserMemory {
  id: number;
  userId: number;
  memoryType: MemoryType;
  memoryKey: string;
  memoryValue: string;
  source?: string | null;
  /** 0.0 ~ 1.0 可信度 */
  confidence: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateMemoryRequest {
  userId: number;
  memoryType: MemoryType | string;
  memoryKey: string;
  memoryValue: string;
  source?: string;
  confidence?: number;
}

export interface ExtractMemoryRequest {
  userId: number;
  eventType: string;
  content: string;
  source?: string;
}

// ============ Sprint 8-D: Agent Evaluation System ============

/** Agent 评估记录（GET /api/evaluation 列表 / POST /api/evaluation 响应） */
export interface EvaluationRecord {
  id: number;
  userId?: number;
  workflowId?: string | null;
  agentType: string;
  evaluationType?: string;
  input?: string;
  output?: string;
  score?: number | null;
  metricsJson?: string | null;
  feedback?: string | null;
  promptVersion?: string | null;
  createdAt?: string | null;
}

/** POST /api/evaluation 请求体 */
export interface CreateEvaluationRequest {
  userId?: number;
  agentType: string;
  evaluationType?: string;
  workflowId?: string;
  input?: string;
  output: string;
  promptVersion?: string;
}

/** GET /api/evaluation/ranking 返回元素：各 Agent 平均得分排行 */
export interface AgentScoreRanking {
  agentType: string;
  avgScore: number;
  count: number;
}

/** GET /api/evaluation/trend 返回元素：评分趋势点 */
export interface ScoreTrendPoint {
  date: string | null;
  score: number | null;
  agentType: string;
}

/** GET /api/evaluation/issues/{agentType} 返回元素：问题分析 */
export interface EvaluationIssue {
  id: number;
  score: number | null;
  feedback: string | null;
  createdAt: string | null;
}

/** POST /api/evaluation/grounding 请求体 */
export interface GroundingCheckRequest {
  userId?: number;
  answer: string;
  memoryContext?: string;
  ragContext?: string;
}

/** POST /api/evaluation/grounding 响应：事实依据核查结果 */
export interface GroundingResult {
  grounded: boolean;
  unsupportedClaims: string[];
  confidence: number;
}

/** POST /api/evaluation/rag-eval 请求体 */
export interface RAGEvalRequest {
  userId?: number;
  question: string;
  retrievedContext: string;
  answer: string;
}

/** POST /api/evaluation/rag-eval 响应：RAG 质量评估指标 */
export interface RAGMetrics {
  contextRecall: number;
  contextPrecision: number;
  faithfulness: number;
  overallScore: number;
  issues: string[];
}

/** Prompt 版本（GET /api/evaluation/prompt-version/{agentType} 响应） */
export interface PromptVersion {
  id: number;
  agentType: string;
  version: string;
  promptContent: string;
  enabled: boolean;
  description?: string | null;
  avgScore?: number | null;
  evalCount?: number | null;
  createdAt?: string | null;
}

/** POST /api/evaluation/prompt-version 请求体 */
export interface CreatePromptVersionRequest {
  agentType: string;
  version: string;
  promptContent: string;
  description?: string;
  enabled?: boolean;
}

/** GET /api/evaluation/prompt-version/compare/{agentType} 返回元素：A/B 对比 */
export interface PromptVersionComparison {
  version: string;
  avgScore: number;
  count: number;
}

// ============ Sprint 9-A: Dashboard Overview + Timeline ============

/** GET /api/dashboard/overview 响应：用户 AI 职业状态聚合 */
export interface DashboardOverview {
  userId: number;
  username: string;
  resumeScore: ResumeSummary;
  highestMatchScore: CareerSummary;
  interviewScore: InterviewSummary;
  growthProgress: GrowthSummary;
  memoryCount: number;
  recentActivities: ActivityItem[];
  generatedAt: string;
}

export interface ResumeSummary {
  resumeId: number | null;
  versionName: string | null;
  targetPosition: string | null;
  score: number | null;
  totalVersions: number;
  hasActiveVersion: boolean;
}

export interface CareerSummary {
  matchScore: number | null;
  jobTitle: string | null;
  company: string | null;
  workflowId: string | null;
  totalReports: number;
  latestAnalysisAt: string | null;
}

export interface InterviewSummary {
  averageScore: number | null;
  highestScore: number | null;
  latestScore: number | null;
  totalSessions: number;
  completedSessions: number;
  latestJobTitle: string | null;
}

export interface GrowthSummary {
  totalWeeks: number;
  completedWeeks: number;
  progressPercent: number;
  targetPosition: string | null;
  currentLevel: string | null;
  activePlans: number;
  totalPlans: number;
}

export interface ActivityItem {
  type: string;
  title: string;
  description: string;
  workflowId?: string | null;
  status: string;
  createdAt: string;
}

/** GET /api/dashboard/timeline 响应：Career Journey Timeline */
export interface TimelineResponse {
  workflowId: string | null;
  stages: TimelineStage[];
  generatedAt: string;
}

export interface TimelineStage {
  stage: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "SKIPPED";
  title: string;
  description: string;
  workflowId?: string | null;
  entityId?: number | null;
  createdAt?: string | null;
  completedAt?: string | null;
  durationMs?: number | null;
}

/** SSE 工作流事件（复用 Sprint 6-B SSE） */
export interface WorkflowStreamEvent {
  event: string;
  workflowId: string;
  taskId?: number;
  taskType?: string;
  agentType?: string;
  task?: string;
  status?: string;
  progress?: number;
  message?: string;
  durationMs?: number;
  summary?: string;
  totalTasks?: number;
  completedTasks?: number;
  errorMessage?: string;
  timestamp?: string;
}

/** Agent 执行图节点状态 */
export type AgentNodeStatus = "WAITING" | "RUNNING" | "SUCCESS" | "FAILED";

export interface AgentNode {
  id: string;
  label: string;
  agentType: string;
  taskType: string;
  status: AgentNodeStatus;
  message?: string;
  durationMs?: number;
  summary?: string;
}

export interface AgentEdge {
  from: string;
  to: string;
}

/** 工作流详情页 Task 项 */
export interface WorkflowTaskDetail {
  taskType: string;
  agentType: string;
  status: string;
  durationMs?: number;
  model?: string;
  inputTokens?: number;
  outputTokens?: number;
  estimatedCost?: number;
}

/** GET /api/workflow/{id} 响应：工作流详情 */
export interface WorkflowDetailResponse {
  workflowId: string;
  userGoal: string;
  status: string;
  totalTasks: number;
  successTasks: number;
  failedTasks: number;
  createdAt: string;
  summary?: string;
  tasks: WorkflowTaskResponse[];
}

export interface WorkflowTaskResponse {
  id: number;
  goal?: string;
  taskType: string;
  agentType: string;
  status: string;
  dependsOn?: number | null;
  result?: string;
  errorMessage?: string;
  workflowId?: string;
  createdAt?: string;
  completedAt?: string;
  startedAt?: string;
  durationMs?: number;
}

/** GET /api/workflow/{id}/instance 响应：持久化的 WorkflowInstance */
export interface WorkflowInstanceResponse {
  id: number;
  workflowId: string;
  userId: number;
  workflowType: string;
  status: string;
  currentTask?: string | null;
  progress?: number | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
}

/** LLMCallLog 扩展（含 estimatedCost） */
export interface LLMCallLogWithCost extends LLMCallLog {
  estimatedCost?: number | null;
}


