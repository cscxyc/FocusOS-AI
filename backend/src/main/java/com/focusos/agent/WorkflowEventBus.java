package com.focusos.agent;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sprint 6-B: Workflow 事件总线
 * <p>
 * 职责：
 * 1. 维护 workflowId → SseEmitter 订阅列表
 * 2. WorkflowExecutor 异步线程推送事件 → 所有订阅者实时收到
 * 3. Workflow 完成后自动清理订阅
 * <p>
 * 线程安全：使用 ConcurrentHashMap + CopyOnWriteArrayList
 */
@Slf4j
@Component
public class WorkflowEventBus {

    /** 每个 workflowId 的订阅者列表（一个 workflow 可被多个客户端订阅，如多标签页） */
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** 每个 workflowId 的历史事件缓存（用于客户端后订阅时补发已发生事件） */
    private final Map<String, List<WorkflowEvent>> eventHistory = new ConcurrentHashMap<>();

    /** 订阅超时时间（30分钟，足够 LLM 长任务） */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * 订阅指定 workflow 的事件流
     */
    public SseEmitter subscribe(String workflowId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        subscribers.computeIfAbsent(workflowId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeSubscriber(workflowId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for workflowId={}", workflowId);
            removeSubscriber(workflowId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE error for workflowId={}: {}", workflowId, e.getMessage());
            removeSubscriber(workflowId, emitter);
        });

        // 补发历史事件（支持刷新页面恢复状态）
        List<WorkflowEvent> history = eventHistory.get(workflowId);
        if (history != null) {
            for (WorkflowEvent event : history) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.getEvent())
                            .data(event));
                } catch (IOException e) {
                    log.debug("Failed to replay history event: {}", e.getMessage());
                    break;
                }
            }
        }

        log.info("SSE subscriber added: workflowId={}, total={}", workflowId,
                subscribers.getOrDefault(workflowId, List.of()).size());
        return emitter;
    }

    /**
     * 推送事件（由 WorkflowExecutor 在异步线程调用）
     */
    public void publish(String workflowId, WorkflowEvent event) {
        // 缓存历史
        eventHistory.computeIfAbsent(workflowId, k -> new CopyOnWriteArrayList<>()).add(event);

        List<SseEmitter> emitters = subscribers.get(workflowId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No subscriber for workflowId={}, event cached only", workflowId);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getEvent())
                        .data(event));
            } catch (IOException e) {
                log.debug("Failed to push SSE event: {}", e.getMessage());
                removeSubscriber(workflowId, emitter);
            }
        }
        log.info("Event pushed: workflowId={}, event={}, subscribers={}",
                workflowId, event.getEvent(), subscribers.getOrDefault(workflowId, List.of()).size());
    }

    /**
     * 完成 workflow 后关闭所有订阅者
     */
    public void complete(String workflowId) {
        List<SseEmitter> emitters = subscribers.remove(workflowId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Failed to complete emitter: {}", e.getMessage());
                }
            }
        }
        // 保留 eventHistory 供后续查询，定时清理由外部任务负责（此处不清理以支持刷新页面恢复）
        log.info("Workflow {} completed, {} subscribers closed", workflowId, emitters != null ? emitters.size() : 0);
    }

    /**
     * 获取 workflow 的历史事件（用于非 SSE 的 REST 查询）
     */
    public List<WorkflowEvent> getHistory(String workflowId) {
        return eventHistory.getOrDefault(workflowId, List.of());
    }

    private void removeSubscriber(String workflowId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(workflowId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(workflowId);
            }
        }
    }

    /**
     * Workflow 事件载体
     */
    @Data
    @Builder
    public static class WorkflowEvent {
        /** 事件类型：workflow_started / task_started / task_completed / workflow_completed / workflow_failed */
        private String event;

        /** 工作流 ID */
        private String workflowId;

        /** 任务 ID（task_* 事件） */
        private Long taskId;

        /** 任务类型 */
        private String taskType;

        /** Agent 类型 */
        private String agentType;

        /** 任务目标描述 */
        private String task;

        /** 状态：STARTED / RUNNING / SUCCESS / FAILED */
        private String status;

        /** 进度百分比 0-100 */
        private Integer progress;

        /** 人类可读消息 */
        private String message;

        /** 任务耗时（毫秒，task_completed 时填充） */
        private Long durationMs;

        /** 错误信息（FAILED 事件） */
        private String errorMessage;

        /** 时间戳 */
        private String timestamp;

        /** 任务总结（workflow_completed 时填充） */
        private String summary;

        /** 当前已完成任务数 */
        private Integer completedTasks;

        /** 总任务数 */
        private Integer totalTasks;
    }
}
