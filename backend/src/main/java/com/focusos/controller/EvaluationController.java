package com.focusos.controller;

import com.focusos.agent.GroundingChecker.GroundingResult;
import com.focusos.agent.RAGEvaluator.RAGMetrics;
import com.focusos.dto.request.CreateEvaluationRequest;
import com.focusos.dto.request.GroundingCheckRequest;
import com.focusos.dto.request.PromptVersionRequest;
import com.focusos.dto.request.RAGEvalRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.EvaluationRecordResponse;
import com.focusos.dto.response.PromptVersionResponse;
import com.focusos.entity.AgentEvaluationRecord;
import com.focusos.entity.PromptVersion;
import com.focusos.entity.User;
import com.focusos.service.AgentEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 8-D: Agent 评估系统 API
 * <p>
 * 前缀: /api/evaluation（application.yml context-path=/api，此处 @RequestMapping("/evaluation")）
 * <p>
 * 安全：所有用户数据相关接口强制用户隔离。@AuthenticationPrincipal 注入当前登录用户，
 * 通过 resolveUserId 解析真实 userId；QA 模式（未登录）允许通过参数指定 userId。
 */
@Slf4j
@RestController
@RequestMapping("/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final AgentEvaluationService evaluationService;

    // ============================================================
    // 1. POST 创建评估
    // ============================================================

    @PostMapping
    public ApiResponse<EvaluationRecordResponse> createEvaluation(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateEvaluationRequest request) {

        Long userId = resolveUserId(user, request.getUserId());
        if (userId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        try {
            AgentEvaluationRecord record = evaluationService.evaluateAndSave(
                    userId,
                    request.getAgentType(),
                    request.getEvaluationType(),
                    request.getInput(),
                    request.getOutput(),
                    request.getWorkflowId(),
                    request.getPromptVersion()
            );
            return ApiResponse.success("评估完成", EvaluationRecordResponse.from(record));
        } catch (Exception e) {
            log.error("createEvaluation failed userId={} agentType={}", userId, request.getAgentType(), e);
            return ApiResponse.error("评估失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 2. GET 列表查询（支持 agentType / evaluationType 过滤）
    // ============================================================

    @GetMapping
    public ApiResponse<List<EvaluationRecordResponse>> listEvaluations(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String evaluationType,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<AgentEvaluationRecord> list;
        if (agentType != null && !agentType.isBlank()) {
            list = evaluationService.findByUserIdAndAgentType(resolvedUserId, agentType);
        } else if (evaluationType != null && !evaluationType.isBlank()) {
            list = evaluationService.findByUserIdAndEvaluationType(resolvedUserId, evaluationType);
        } else {
            list = evaluationService.findByUserId(resolvedUserId);
        }
        return ApiResponse.success("查询成功", EvaluationRecordResponse.fromList(list));
    }

    // ============================================================
    // 3. GET 按 id 查询
    // ============================================================

    @GetMapping("/{id}")
    public ApiResponse<EvaluationRecordResponse> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        Optional<AgentEvaluationRecord> opt = evaluationService.findByIdAndUserId(id, resolvedUserId);
        if (opt.isEmpty()) {
            return ApiResponse.error(404, "评估记录不存在或无权访问");
        }
        return ApiResponse.success("查询成功", EvaluationRecordResponse.from(opt.get()));
    }

    // ============================================================
    // 4. GET 各 Agent 平均得分排行
    // ============================================================

    @GetMapping("/ranking")
    public ApiResponse<List<Map<String, Object>>> getAgentScoreRanking(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<Object[]> rows = evaluationService.getAgentScoreRanking(resolvedUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("agentType", row[0]);
                m.put("avgScore", row[1]);
                m.put("count", row[2]);
                result.add(m);
            }
        }
        return ApiResponse.success("查询成功", result);
    }

    // ============================================================
    // 5. GET 评分趋势
    // ============================================================

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> getScoreTrend(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<AgentEvaluationRecord> list = evaluationService.getRecentScored(resolvedUserId, 50);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentEvaluationRecord e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            m.put("score", e.getScore());
            m.put("agentType", e.getAgentType());
            result.add(m);
        }
        return ApiResponse.success("查询成功", result);
    }

    // ============================================================
    // 6. GET 问题分析（按 agentType）
    // ============================================================

    @GetMapping("/issues/{agentType}")
    public ApiResponse<List<Map<String, Object>>> getIssueAnalysis(
            @AuthenticationPrincipal User user,
            @PathVariable String agentType,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<AgentEvaluationRecord> list = evaluationService.getRecentByAgent(resolvedUserId, agentType, 20);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentEvaluationRecord e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("score", e.getScore());
            m.put("feedback", e.getFeedback());
            m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            result.add(m);
        }
        return ApiResponse.success("查询成功", result);
    }

    // ============================================================
    // 7. DELETE 删除评估记录
    // ============================================================

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEvaluation(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        boolean deleted = evaluationService.deleteByIdAndUserId(id, resolvedUserId);
        if (deleted) {
            return ApiResponse.success("删除成功", null);
        }
        return ApiResponse.error(403, "无权删除该评估记录（非本人所有或不存在）");
    }

    // ============================================================
    // 8. POST Grounding 事实依据核查
    // ============================================================

    @PostMapping("/grounding")
    public ApiResponse<GroundingResult> groundingCheck(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody GroundingCheckRequest request) {

        Long userId = resolveUserId(user, request.getUserId());
        if (userId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        GroundingResult result = evaluationService.checkGrounding(
                userId, request.getAnswer(), request.getMemoryContext(), request.getRagContext());
        return ApiResponse.success("事实核查完成", result);
    }

    // ============================================================
    // 9. POST RAG 评估
    // ============================================================

    @PostMapping("/rag-eval")
    public ApiResponse<RAGMetrics> ragEval(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RAGEvalRequest request) {

        Long userId = resolveUserId(user, request.getUserId());
        if (userId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        RAGMetrics metrics = evaluationService.evaluateRAG(
                userId, request.getQuestion(), request.getRetrievedContext(), request.getAnswer());
        return ApiResponse.success("RAG 评估完成", metrics);
    }

    // ============================================================
    // 10. POST 创建 Prompt 版本
    // ============================================================

    @PostMapping("/prompt-version")
    public ApiResponse<PromptVersionResponse> createPromptVersion(
            @Valid @RequestBody PromptVersionRequest request) {

        PromptVersion pv = evaluationService.createPromptVersion(
                request.getAgentType(),
                request.getVersion(),
                request.getPromptContent(),
                request.getDescription(),
                request.getEnabled()
        );
        return ApiResponse.success("Prompt 版本创建成功", PromptVersionResponse.from(pv));
    }

    // ============================================================
    // 11. GET 按 agent 查询 Prompt 版本列表
    // ============================================================

    @GetMapping("/prompt-version/{agentType}")
    public ApiResponse<List<PromptVersionResponse>> listPromptVersions(
            @PathVariable String agentType) {

        List<PromptVersion> list = evaluationService.findPromptVersionsByAgent(agentType);
        return ApiResponse.success("查询成功", PromptVersionResponse.fromList(list));
    }

    // ============================================================
    // 12. PUT 启用指定 Prompt 版本
    // ============================================================

    @PutMapping("/prompt-version/{id}/enable")
    public ApiResponse<PromptVersionResponse> enablePromptVersion(@PathVariable Long id) {

        Optional<PromptVersion> opt = evaluationService.enablePromptVersion(id);
        if (opt.isEmpty()) {
            return ApiResponse.error(404, "Prompt 版本不存在");
        }
        return ApiResponse.success("启用成功", PromptVersionResponse.from(opt.get()));
    }

    // ============================================================
    // 13. GET Prompt 版本 A/B 对比
    // ============================================================

    @GetMapping("/prompt-version/compare/{agentType}")
    public ApiResponse<List<Map<String, Object>>> comparePromptVersions(
            @AuthenticationPrincipal User user,
            @PathVariable String agentType,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<Object[]> rows = evaluationService.getPromptVersionComparison(resolvedUserId, agentType);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("version", row[0]);
                m.put("avgScore", row[1]);
                m.put("count", row[2]);
                result.add(m);
            }
        }
        return ApiResponse.success("查询成功", result);
    }

    // ============================================================
    // 安全：用户隔离校验（与 MemoryController 一致）
    // ============================================================

    /**
     * 解析真实 userId，保证用户隔离：
     * - 已登录时优先使用 @AuthenticationPrincipal user.getId()；
     * - 如果请求参数中也带了 userId，必须与登录用户一致，否则 null（上层返回 403）。
     * <p>
     * 开发/QA 模式（security.disabled=true 或 user==null）时，允许使用参数 userId。
     */
    private Long resolveUserId(User user, Long paramUserId) {
        if (user != null && user.getId() != null) {
            // 已登录：如果参数也带 userId，强制要求一致
            if (paramUserId != null && !paramUserId.equals(user.getId())) {
                log.warn("跨用户访问被拒绝: loginUser={}, paramUserId={}", user.getId(), paramUserId);
                return null;
            }
            return user.getId();
        }
        // 未登录（QA 模式 / Security 关闭）：允许通过参数指定 userId
        return paramUserId;
    }
}
