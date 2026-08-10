package com.focusos.controller;

import com.focusos.dto.request.CreateUserMemoryRequest;
import com.focusos.dto.request.ExtractMemoryRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.UserMemoryResponse;
import com.focusos.entity.User;
import com.focusos.entity.UserMemory;
import com.focusos.service.PersonalMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Sprint 8-C: Personal Memory System 记忆管理 API
 * <p>
 * 前缀: /api/memory
 * 5 个接口：
 * 1. POST   /api/memory              — 创建/合并一条长期记忆（MemoryMergeStrategy 自动合并同 key）
 * 2. GET    /api/memory              — 查询当前用户全部长期记忆（支持 minConfidence 过滤）
 * 3. GET    /api/memory/type/{type}  — 按记忆类型分类查询（SKILL/PROJECT/...）
 * 4. DELETE /api/memory/{id}         — 删除指定记忆（仅允许删除自己的）
 * 5. POST   /api/memory/extract      — 手动触发 MemoryAgent 从行为事件中提取结构化记忆
 * <p>
 * 安全：所有接口强制用户隔离。@AuthenticationPrincipal 注入当前登录用户，
 *      任何跨用户操作（body.userId != user.id）直接 403。
 */
@Slf4j
@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final PersonalMemoryService personalMemoryService;

    /** 允许的 memoryType 枚举值 */
    private static final Set<String> VALID_TYPES = Set.of(
            "SKILL", "PROJECT", "EXPERIENCE", "GOAL",
            "LEARNING_PROGRESS", "PREFERENCE", "ACHIEVEMENT"
    );

    // ============================================================
    // Task9-1: POST 创建记忆（带同 key 自动合并）
    // ============================================================

    /**
     * 创建/合并一条长期记忆
     * <p>
     * curl 示例：
     * <pre>
     * POST /api/memory
     * {
     *   "userId": 1,
     *   "memoryType": "SKILL",
     *   "memoryKey": "Milvus",
     *   "memoryValue": "完成向量检索优化实验，了解HNSW索引优化",
     *   "source": "LEARNING_COMPLETED",
     *   "confidence": 0.85
     * }
     * </pre>
     */
    @PostMapping
    public ApiResponse<UserMemoryResponse> createMemory(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateUserMemoryRequest request) {

        Long userId = resolveUserId(user, request.getUserId());
        if (userId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        if (!VALID_TYPES.contains(request.getMemoryType())) {
            return ApiResponse.error("非法 memoryType，允许值: " + VALID_TYPES);
        }

        UserMemory saved = personalMemoryService.saveMemory(
                userId,
                request.getMemoryType(),
                request.getMemoryKey(),
                request.getMemoryValue(),
                request.getSource() == null ? "MANUAL" : request.getSource(),
                request.getConfidence() == null ? 0.8 : request.getConfidence()
        );
        return ApiResponse.success("记忆保存成功", UserMemoryResponse.from(saved));
    }

    // ============================================================
    // Task9-2: GET 查询当前用户全部记忆
    // ============================================================

    /**
     * 查询当前用户全部记忆
     * <p>
     * 参数：
     * - userId:        建议不传，使用 @AuthenticationPrincipal 自动注入（传了也必须 == 当前登录用户）
     * - minConfidence: 可选，0.0~1.0 过滤（例如 0.7 只返回置信度 >=0.7 的记忆）
     * - limit:         可选，最大返回条数（默认 200）
     */
    @GetMapping
    public ApiResponse<List<UserMemoryResponse>> listMemories(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false, defaultValue = "200") Integer limit) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        List<UserMemory> memories;
        if (minConfidence != null) {
            memories = personalMemoryService.retrieveByMinConfidence(resolvedUserId, minConfidence);
        } else {
            memories = personalMemoryService.retrieveAll(resolvedUserId);
        }
        if (limit != null && memories.size() > limit) {
            memories = memories.subList(0, limit);
        }
        return ApiResponse.success("查询成功", UserMemoryResponse.fromList(memories));
    }

    // ============================================================
    // Task9-3: GET 分类查询
    // ============================================================

    /**
     * 按记忆类型分类查询
     * <p>
     * 示例: GET /api/memory/type/SKILL?userId=1
     */
    @GetMapping("/type/{type}")
    public ApiResponse<List<UserMemoryResponse>> listByType(
            @AuthenticationPrincipal User user,
            @PathVariable String type,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        if (!VALID_TYPES.contains(type)) {
            return ApiResponse.error("非法 memoryType，允许值: " + VALID_TYPES);
        }

        List<UserMemory> memories = personalMemoryService.retrieveByType(resolvedUserId, type);
        return ApiResponse.success("分类查询成功: " + type, UserMemoryResponse.fromList(memories));
    }

    // ============================================================
    // Task9-4: DELETE 删除（仅自己的）
    // ============================================================

    /**
     * 删除指定记忆
     * <p>
     * 安全：先查所属用户再删除，不是自己的返回 403
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(required = false) Long userId) {

        Long resolvedUserId = resolveUserId(user, userId);
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        boolean deleted = personalMemoryService.deleteMemory(resolvedUserId, id);
        if (deleted) {
            return ApiResponse.success("删除成功", null);
        } else {
            return ApiResponse.error(403, "无权删除该记忆（非本人所有或不存在）");
        }
    }

    // ============================================================
    // Task9-5: POST 手动触发 MemoryAgent 提取结构化记忆
    // ============================================================

    /**
     * 手动触发 MemoryAgent 从行为事件提取结构化记忆，提取后直接自动入库（MemoryMergeStrategy 同 key 合并）
     * <p>
     * 输入示例：
     * <pre>
     * {
     *   "userId": 1,
     *   "eventType": "LEARNING_COMPLETED",
     *   "content": "完成Milvus向量检索优化实验，HNSW索引 m=32 efSearch=128，10w 向量检索 P99 从 102ms 降到 12ms"
     * }
     * </pre>
     * 返回：提取并保存的记忆数组（0~多条）
     */
    @PostMapping("/extract")
    public ApiResponse<List<UserMemoryResponse>> extractMemories(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ExtractMemoryRequest request) {

        Long resolvedUserId = resolveUserId(user, request.getUserId());
        if (resolvedUserId == null) return ApiResponse.error(403, "用户未登录或 userId 不匹配");

        try {
            List<UserMemory> saved = personalMemoryService.extractAndSave(
                    resolvedUserId,
                    request.getEventType(),
                    request.getContent(),
                    request.getSource() == null ? "MANUAL_EXTRACT" : request.getSource()
            );
            return ApiResponse.success(
                    "记忆提取完成，共 " + saved.size() + " 条",
                    UserMemoryResponse.fromList(saved)
            );
        } catch (Exception e) {
            log.error("MemoryAgent 提取失败", e);
            return ApiResponse.error("记忆提取失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 安全：用户隔离校验
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
