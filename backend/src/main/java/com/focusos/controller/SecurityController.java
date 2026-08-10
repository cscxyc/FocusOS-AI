package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.service.PromptSecurityFilter;
import com.focusos.service.PromptSecurityFilter.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 8-E: 安全增强 Controller (Task 13)
 * <p>
 * 提供 Prompt Injection 扫描接口：
 * <ul>
 *   <li>POST /security/scan-prompt — 扫描用户输入，返回是否拦截 + 命中规则</li>
 * </ul>
 * <p>
 * 用于前端在提交 LLM 调用前进行客户端预校验，以及 QA 测试验证拦截规则覆盖度。
 */
@Slf4j
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecurityController {

    private final PromptSecurityFilter promptSecurityFilter;

    /**
     * 扫描用户输入，检测是否包含 Prompt Injection 攻击模式。
     * <p>
     * 请求体：
     * <pre>{ "input": "用户输入文本" }</pre>
     * 响应：
     * <pre>{ "blocked": true/false, "reason": "命中规则说明" }</pre>
     */
    @PostMapping("/scan-prompt")
    public ApiResponse<Map<String, Object>> scanPrompt(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User ignored,
            @RequestBody Map<String, String> request) {
        String input = request.get("input");
        if (input == null) {
            input = "";
        }

        ScanResult result = promptSecurityFilter.scan(input);
        Map<String, Object> data = new HashMap<>();
        data.put("blocked", result.isBlocked());
        data.put("reason", result.reason());
        data.put("inputLength", input.length());

        if (result.isBlocked()) {
            log.warn("PromptSecurityFilter 拦截用户输入: length={}, reason={}", input.length(), result.reason());
        }

        return ApiResponse.success(result.isBlocked() ? "输入被拦截" : "输入通过安全检查", data);
    }
}
