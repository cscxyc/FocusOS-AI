package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.User;
import com.focusos.service.CacheService;
import com.focusos.service.LLMGateway;
import com.focusos.service.LLMGateway.ChatResponse;
import com.focusos.service.RetryManager;
import com.focusos.service.RetryManager.RetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Sprint 8-E: QA 测试专用 Controller (Task 14 辅助)
 * <p>
 * 暴露 CacheService / LLMGateway / RetryManager 的内部状态与方法，便于 QA 脚本直接验证：
 * <ul>
 *   <li>POST /sprint8e/cache/put    — 写入缓存</li>
 *   <li>GET  /sprint8e/cache/get    — 读取缓存（验证 hit/miss）</li>
 *   <li>POST /sprint8e/cache/evict  — 清除缓存</li>
 *   <li>POST /sprint8e/llm/call     — 直接调用 LLM Gateway</li>
 *   <li>POST /sprint8e/llm/fallback — 调用 LLM Gateway（带 fallback）</li>
 *   <li>POST /sprint8e/retry/success — 重试框架（任务第 2 次成功）</li>
 *   <li>POST /sprint8e/retry/failed  — 重试框架（任务持续失败，验证 maxRetry）</li>
 * </ul>
 * <p>
 * 注意：本 Controller 仅用于 QA 验证，生产环境可通过 Spring Profile 隔离禁用。
 */
@Slf4j
@RestController
@RequestMapping("/sprint8e")
@RequiredArgsConstructor
public class Sprint8ETestController {

    private final CacheService cacheService;
    private final LLMGateway llmGateway;
    private final RetryManager retryManager;

    // ============================================================
    // Cache 测试端点
    // ============================================================

    @PostMapping("/cache/put")
    public ApiResponse<Map<String, Object>> cachePut(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String key = request.get("key");
        String value = request.get("value");
        if (key == null || value == null) {
            return ApiResponse.error("key/value 不能为空");
        }
        cacheService.put(key, value);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("written", true);
        data.put("cacheAvailable", cacheService.isAvailable());
        return ApiResponse.success("缓存写入成功", data);
    }

    @GetMapping("/cache/get")
    public ApiResponse<Map<String, Object>> cacheGet(
            @AuthenticationPrincipal User user,
            @RequestParam String key) {
        java.util.Optional<String> value = cacheService.get(key, String.class);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("hit", value.isPresent());
        data.put("value", value.orElse(null));
        return ApiResponse.success(value.isPresent() ? "Cache HIT" : "Cache MISS", data);
    }

    @PostMapping("/cache/evict")
    public ApiResponse<Map<String, Object>> cacheEvict(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String key = request.get("key");
        cacheService.evict(key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("evicted", true);
        return ApiResponse.success("缓存已清除", data);
    }

    // ============================================================
    // LLM Gateway 测试端点
    // ============================================================

    @PostMapping("/llm/call")
    public ApiResponse<ChatResponse> llmCall(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ApiResponse.error("prompt 不能为空");
        }
        String agentType = request.getOrDefault("agentType", "test");
        ChatResponse response = llmGateway.call(agentType, prompt, user.getId());
        return ApiResponse.success("LLM 调用完成", response);
    }

    @PostMapping("/llm/fallback")
    public ApiResponse<ChatResponse> llmCallWithFallback(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ApiResponse.error("prompt 不能为空");
        }
        String agentType = request.getOrDefault("agentType", "test");
        ChatResponse response = llmGateway.callWithFallback(agentType, prompt, user.getId());
        return ApiResponse.success("LLM 调用完成（带 Fallback）", response);
    }

    // ============================================================
    // Retry 测试端点
    // ============================================================

    /** 模拟任务第 N 次才成功（验证重试机制生效） */
    @PostMapping("/retry/success")
    public ApiResponse<Map<String, Object>> retrySuccess(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {
        int succeedOnAttempt = ((Number) request.getOrDefault("succeedOnAttempt", 2)).intValue();
        int maxRetry = ((Number) request.getOrDefault("maxRetry", 3)).intValue();

        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> task = () -> {
            int n = attempts.incrementAndGet();
            if (n < succeedOnAttempt) {
                throw new RuntimeException("模拟第 " + n + " 次失败");
            }
            return "第 " + n + " 次成功";
        };

        RetryPolicy policy = RetryPolicy.builder()
                .maxRetry(maxRetry)
                .backoffMs(new long[]{100L, 200L, 500L})
                .build();

        Map<String, Object> data = new HashMap<>();
        try {
            String result = retryManager.executeWithRetry(task, policy);
            data.put("success", true);
            data.put("result", result);
            data.put("attempts", attempts.get());
            data.put("succeedOnAttempt", succeedOnAttempt);
            return ApiResponse.success("重试任务成功", data);
        } catch (Exception e) {
            data.put("success", false);
            data.put("error", e.getMessage());
            data.put("attempts", attempts.get());
            return ApiResponse.success("重试任务失败", data);
        }
    }

    /** 模拟任务持续失败（验证 maxRetry 限制生效，不无限重试） */
    @PostMapping("/retry/failed")
    public ApiResponse<Map<String, Object>> retryFailed(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {
        int maxRetry = ((Number) request.getOrDefault("maxRetry", 3)).intValue();

        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> task = () -> {
            int n = attempts.incrementAndGet();
            throw new RuntimeException("模拟第 " + n + " 次失败（持续失败）");
        };

        RetryPolicy policy = RetryPolicy.builder()
                .maxRetry(maxRetry)
                .backoffMs(new long[]{100L, 200L, 500L})
                .build();

        Map<String, Object> data = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            String result = retryManager.executeWithRetry(task, policy);
            data.put("success", true);
            data.put("result", result);
            data.put("attempts", attempts.get());
        } catch (Exception e) {
            data.put("success", false);
            data.put("error", e.getMessage());
            data.put("attempts", attempts.get());
            data.put("maxRetryExceeded", attempts.get() == maxRetry);
        }
        data.put("elapsedMs", System.currentTimeMillis() - start);
        return ApiResponse.success("重试任务已结束", data);
    }
}
