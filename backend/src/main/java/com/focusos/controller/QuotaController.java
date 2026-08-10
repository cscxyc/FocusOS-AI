package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.User;
import com.focusos.entity.UserQuota;
import com.focusos.service.QuotaServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 8-E: 用户配额 Controller (Task 8)
 * <p>
 * 提供用户配额信息查询与管理接口：
 * <ul>
 *   <li>GET  /quota          — 查询当前用户配额概览</li>
 *   <li>POST /quota/reset    — 重置当日配额（测试/管理员）</li>
 *   <li>POST /quota/upgrade  — 升级为 PREMIUM 等级（提升配额上限）</li>
 * </ul>
 * <p>
 * 配额检查本身由 {@link com.focusos.service.LLMGateway#call} 自动调用，无需前端显式触发。
 */
@Slf4j
@RestController
@RequestMapping("/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaServiceImpl quotaService;

    /**
     * 查询当前用户配额概览。
     * <p>
     * 响应字段：
     * <ul>
     *   <li>tier: 用户等级（DEFAULT / PREMIUM）</li>
     *   <li>dailyTokenLimit: 每日 Token 上限</li>
     *   <li>usedTokens: 当日已使用 Token</li>
     *   <li>remainingTokens: 当日剩余 Token</li>
     *   <li>resetDate: 配额重置日期</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getQuota(@AuthenticationPrincipal User user) {
        Long userId = user.getId();
        // 调用 ensureQuotaExists 风格的方法：首次查询时自动创建配额记录
        UserQuota quota = quotaService.getQuota(userId)
                .orElseGet(() -> quotaService.resetQuota(userId));

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("tier", quota.getTier().name());
        data.put("dailyTokenLimit", quota.getDailyTokenLimit());
        data.put("usedTokens", quota.getUsedTokens());
        long remaining = Math.max(0, quota.getDailyTokenLimit() - quota.getUsedTokens());
        data.put("remainingTokens", remaining);
        data.put("resetDate", quota.getResetDate() != null ? quota.getResetDate().toString() : LocalDate.now().toString());
        data.put("usagePercent", quota.getDailyTokenLimit() > 0
                ? Math.round(100.0 * quota.getUsedTokens() / quota.getDailyTokenLimit())
                : 0);

        return ApiResponse.success("配额查询成功", data);
    }

    /**
     * 重置当前用户当日配额（测试用 / 管理员手动重置）。
     */
    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> resetQuota(@AuthenticationPrincipal User user) {
        UserQuota quota = quotaService.resetQuota(user.getId());
        log.info("用户 {} 配额已手动重置", user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", quota.getUserId());
        data.put("usedTokens", quota.getUsedTokens());
        data.put("dailyTokenLimit", quota.getDailyTokenLimit());
        data.put("resetDate", quota.getResetDate().toString());

        return ApiResponse.success("配额已重置", data);
    }

    /**
     * 升级当前用户为 PREMIUM 等级（提升配额上限至 premium-daily-token-limit）。
     */
    @PostMapping("/upgrade")
    public ApiResponse<Map<String, Object>> upgradeToPremium(@AuthenticationPrincipal User user) {
        UserQuota quota = quotaService.upgradeToPremium(user.getId());
        log.info("用户 {} 已升级为 PREMIUM，新配额上限={}", user.getId(), quota.getDailyTokenLimit());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", quota.getUserId());
        data.put("tier", quota.getTier().name());
        data.put("dailyTokenLimit", quota.getDailyTokenLimit());
        data.put("usedTokens", quota.getUsedTokens());

        return ApiResponse.success("已升级为 PREMIUM 用户", data);
    }
}
