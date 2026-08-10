package com.focusos.controller;

import com.focusos.agent.WorkflowEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Sprint 6-B: Workflow SSE 事件推送控制器
 * <p>
 * 实际访问路径（context-path=/api）：
 * 1. GET /api/workflow/{workflowId}/events — SSE 实时事件流（含历史补发，支持刷新恢复）
 * 2. GET /api/workflow/{workflowId}/events/history — 仅查询历史事件（非流式）
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowEventController {

    private final WorkflowEventBus eventBus;

    /**
     * SSE 订阅 Workflow 实时事件
     * <p>
     * 客户端使用 EventSource 监听：
     * <pre>
     * const es = new EventSource('/api/workflow/abc12345/events');
     * es.addEventListener('workflow_started', e => console.log(JSON.parse(e.data)));
     * es.addEventListener('task_started', e => ...);
     * es.addEventListener('task_completed', e => ...);
     * es.addEventListener('workflow_completed', e => { es.close(); });
     * es.addEventListener('workflow_failed', e => { es.close(); });
     * </pre>
     */
    @GetMapping(value = "/{workflowId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEvents(@PathVariable String workflowId) {
        log.info("SSE subscription: workflowId={}", workflowId);
        return eventBus.subscribe(workflowId);
    }

    /**
     * 查询历史事件（非流式，用于断线后补全）
     */
    @GetMapping("/{workflowId}/events/history")
    public Map<String, Object> getEventHistory(@PathVariable String workflowId) {
        List<WorkflowEventBus.WorkflowEvent> history = eventBus.getHistory(workflowId);
        return Map.of(
                "workflowId", workflowId,
                "events", history,
                "count", history.size()
        );
    }
}
