package com.focusos.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Sprint 6-B: Workflow 共享上下文
 * <p>
 * 生命周期：Workflow 开始 → Personal RAG 检索用户资料 → 生成 Context → 传递给所有 Agent
 * <p>
 * 解决问题：
 * 1. 避免 Agent 重复查询 RAG（每个 Agent 自己查询导致 embedding 调用增加）
 * 2. 保证上下文一致（所有 Agent 看到同一份用户资料）
 * 3. 保留用户隔离（userId 强制过滤）
 */
@Data
@Builder
public class WorkflowContext {

    /** 用户 ID（用户隔离核心字段） */
    private Long userId;

    /** 工作流批次 ID */
    private String workflowId;

    /** 用户原始目标 */
    private String userGoal;

    /** Personal RAG 检索的用户画像摘要（基于简历/项目/实习） */
    private String userProfile;

    /** 用户技能列表（从 RAG 提取） */
    private List<String> skills;

    /** 用户项目经历（从 RAG 提取） */
    private List<Map<String, Object>> projects;

    /** 用户实习/工作经历（从 RAG 提取） */
    private List<Map<String, Object>> experiences;

    /** 用户职业目标（从 RAG 提取，可能与 userGoal 不同） */
    private String careerGoal;

    /** 检索到的原始文档片段（供 Agent 引用具体内容） */
    private List<Map<String, Object>> retrievedDocuments;

    /** RAG 检索是否成功（失败时降级为空上下文，不阻塞 Workflow） */
    private boolean ragRetrievalSuccess;

    /** RAG 检索失败原因（用于日志与降级追踪） */
    private String ragRetrievalError;

    /** Sprint 6-C: 结构化用户画像（带来源信息） */
    private UserProfileContext userProfileContext;

    /** Sprint 8-C: 结构化用户长期成长记忆（动态沉淀，注入所有 Agent prompt） */
    private UserMemoryContext memoryContext;

    /** Context 创建时间（用于性能分析） */
    private long createdAtMillis;

    /**
     * 将 Context 渲染为 Agent 可直接使用的文本块
     * <p>
     * Sprint 6-C: 优先使用 UserProfileContext.renderWithSources()（带来源标注）
     * Sprint 8-C: 在 profile 之后追加 memoryContext（长期记忆优先级 Memory > Profile > Resume）
     */
    public String renderAsPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户原始目标】").append(userGoal != null ? userGoal : "未提供").append("\n");

        // Sprint 6-C: 优先使用结构化 UserProfileContext（带来源信息）
        if (userProfileContext != null && userProfileContext.isRetrievalSuccess()) {
            sb.append(userProfileContext.renderWithSources());
        } else {
            // 降级：使用旧的字符串型 userProfile
            appendLegacyProfile(sb);
        }

        // Sprint 8-C: 长期成长记忆（所有 Agent 都注入；优先级最高）
        if (memoryContext != null) {
            sb.append("\n").append(memoryContext.renderAsPromptSection());
        } else {
            sb.append("\n【长期成长记忆】（未加载，将仅基于静态资料处理）\n");
        }
        return sb.toString();
    }

    private void appendLegacyProfile(StringBuilder sb) {
        // 降级：使用旧的字符串型 userProfile
        if (ragRetrievalSuccess && userProfile != null && !userProfile.isBlank()) {
            sb.append("【用户画像（来自个人知识库 RAG 检索）】\n").append(userProfile).append("\n");
            if (skills != null && !skills.isEmpty()) {
                sb.append("【技能列表】").append(String.join("、", skills)).append("\n");
            }
            if (projects != null && !projects.isEmpty()) {
                sb.append("【项目经历】\n");
                for (Map<String, Object> p : projects) {
                    sb.append("- ").append(p.getOrDefault("name", "")).append(": ")
                            .append(p.getOrDefault("description", "")).append("\n");
                }
            }
            if (experiences != null && !experiences.isEmpty()) {
                sb.append("【工作/实习经历】\n");
                for (Map<String, Object> e : experiences) {
                    sb.append("- ").append(e.getOrDefault("company", "")).append(" - ")
                            .append(e.getOrDefault("role", "")).append("\n");
                }
            }
            if (careerGoal != null && !careerGoal.isBlank()) {
                sb.append("【职业目标】").append(careerGoal).append("\n");
            }
        } else {
            sb.append("【用户画像】（RAG 检索失败或为空，降级为通用上下文）");
            if (ragRetrievalError != null) {
                sb.append(" 原因: ").append(ragRetrievalError);
            }
            if (userProfileContext != null && userProfileContext.getRetrievalError() != null) {
                sb.append(" / ").append(userProfileContext.getRetrievalError());
            }
            sb.append("\n");
        }
        // void：直接修改 StringBuilder 引用即可，无需返回
    }

    /**
     * 构造一个降级 Context（RAG 失败时使用）
     */
    public static WorkflowContext degraded(Long userId, String workflowId, String userGoal, String errorReason) {
        return WorkflowContext.builder()
                .userId(userId)
                .workflowId(workflowId)
                .userGoal(userGoal)
                .ragRetrievalSuccess(false)
                .ragRetrievalError(errorReason)
                .userProfile("")
                .skills(List.of())
                .projects(List.of())
                .experiences(List.of())
                .createdAtMillis(System.currentTimeMillis())
                .build();
    }
}
