package com.focusos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 6-A: Multi-Agent Workflow 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {
    private String workflowId;
    private String userGoal;
    private String status;
    private Integer totalTasks;
    private Integer successTasks;
    private Integer failedTasks;
    private LocalDateTime createdAt;
    private List<AgentTaskResponse> tasks;
    /** MasterAgent 的综合总结（汇总各 Agent 输出） */
    private String summary;
}
