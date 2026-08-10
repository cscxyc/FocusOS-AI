package com.focusos.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Sprint 6-C: 用户画像上下文（带来源信息）
 * <p>
 * 与 WorkflowContext 的区别：
 * - WorkflowContext 是 Workflow 级别的共享上下文（含 workflowId、userGoal 等）
 * - UserProfileContext 是用户资料的结构化表示（含 skills/projects/experiences + sourceDocuments）
 * <p>
 * 每个 skill/project/experience 都标注来源文档（fileName），供 Agent 引用来源
 */
@Data
@Builder
public class UserProfileContext {

    private Long userId;
    private String userGoal;

    /** 带来源的技能列表 */
    private List<SourcedSkill> skills;

    /** 带来源的项目经历 */
    private List<SourcedProject> projects;

    /** 带来源的实习/工作经历 */
    private List<SourcedExperience> experiences;

    /** 检索到的源文档列表（去重） */
    private List<SourcedDocument> sourceDocuments;

    /** 完整画像文本（用于注入 Agent prompt） */
    private String profileText;

    /** RAG 检索是否成功 */
    private boolean retrievalSuccess;

    /** RAG 检索失败原因 */
    private String retrievalError;

    /**
     * 渲染为 Agent 可直接使用的带来源标注的 prompt 文本
     */
    public String renderWithSources() {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户目标】").append(userGoal != null ? userGoal : "未提供").append("\n\n");

        if (!retrievalSuccess) {
            sb.append("【用户画像】（RAG 检索失败：").append(retrievalError).append("）\n");
            return sb.toString();
        }

        if (skills != null && !skills.isEmpty()) {
            sb.append("【技能列表（带来源）】\n");
            for (SourcedSkill s : skills) {
                sb.append("- ").append(s.getValue()).append("（来源: ").append(s.getSource()).append("）\n");
            }
            sb.append("\n");
        }

        if (projects != null && !projects.isEmpty()) {
            sb.append("【项目经历（带来源）】\n");
            for (SourcedProject p : projects) {
                sb.append("- ").append(p.getName()).append("（来源: ").append(p.getSource()).append("）\n");
                sb.append("  ").append(p.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (experiences != null && !experiences.isEmpty()) {
            sb.append("【实习/工作经历（带来源）】\n");
            for (SourcedExperience e : experiences) {
                sb.append("- ").append(e.getCompany()).append("（来源: ").append(e.getSource()).append("）\n");
                sb.append("  ").append(e.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (sourceDocuments != null && !sourceDocuments.isEmpty()) {
            sb.append("【参考文档列表】\n");
            for (SourcedDocument d : sourceDocuments) {
                sb.append("- ").append(d.getTitle() != null ? d.getTitle() : d.getFileName())
                        .append("（category=").append(d.getCategory())
                        .append(", type=").append(d.getDocumentType()).append("）\n");
            }
        }

        return sb.toString();
    }

    @Data
    @Builder
    public static class SourcedSkill {
        private String value;
        private String source;
    }

    @Data
    @Builder
    public static class SourcedProject {
        private String name;
        private String description;
        private String source;
    }

    @Data
    @Builder
    public static class SourcedExperience {
        private String company;
        private String description;
        private String source;
    }

    @Data
    @Builder
    public static class SourcedDocument {
        private String documentId;
        private String title;
        private String fileName;
        private String category;
        private String documentType;
    }
}
