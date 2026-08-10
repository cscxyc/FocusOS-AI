package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.ResumeDiffResponse;
import com.focusos.dto.response.ResumeVersionResponse;
import com.focusos.entity.User;
import com.focusos.service.ResumeDiffService;
import com.focusos.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7-C-A: Resume Controller
 * <p>
 * 简历版本管理 + 导出 API：
 * 1. POST   /resume/versions              — 创建版本（手动 / 基于 reportId）
 * 2. GET    /resume/versions              — 查询版本列表
 * 3. GET    /resume/versions/{versionId}  — 获取版本详情
 * 4. PUT    /resume/versions/{versionId}  — 更新版本内容
 * 5. DELETE /resume/versions/{versionId}  — 删除版本
 * 6. POST   /resume/versions/{versionId}/activate — 切换激活版本
 * 7. GET    /resume/active                — 获取当前激活版本
 * 8. GET    /resume/versions/{versionId}/export?format=pdf|md|docx — 导出简历
 */
@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeDiffService resumeDiffService;

    /**
     * 创建简历版本
     * <p>
     * 请求体：
     * - 手动创建：{ "targetPosition": "...", "versionName": "...", "content": "...", "setActive": true }
     * - 基于 report：{ "reportId": 123, "versionName": "可选", "setActive": true }
     */
    @PostMapping("/versions")
    public ApiResponse<ResumeVersionResponse> createVersion(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {

        Long reportId = getAsLong(request.get("reportId"));
        String targetPosition = getAsString(request.get("targetPosition"));
        String versionName = getAsString(request.get("versionName"));
        String content = getAsString(request.get("content"));
        boolean setActive = getAsBool(request.get("setActive"), false);

        ResumeVersionResponse response;
        if (reportId != null) {
            response = resumeService.createVersionFromReport(user.getId(), reportId, versionName, setActive);
        } else {
            if (targetPosition == null || targetPosition.isBlank()) {
                return ApiResponse.error("targetPosition 不能为空（手动创建时必填）");
            }
            response = resumeService.createVersion(user.getId(), targetPosition, versionName,
                    content, null, setActive);
        }
        return ApiResponse.success("简历版本创建成功", response);
    }

    /**
     * 查询用户全部简历版本（列表视图）
     */
    @GetMapping("/versions")
    public ApiResponse<List<ResumeVersionResponse>> getVersions(@AuthenticationPrincipal User user) {
        return ApiResponse.success(resumeService.getVersions(user.getId()));
    }

    /**
     * 按目标岗位查询版本
     */
    @GetMapping("/versions/by-position")
    public ApiResponse<List<ResumeVersionResponse>> getVersionsByPosition(
            @AuthenticationPrincipal User user,
            @RequestParam String targetPosition) {
        return ApiResponse.success(resumeService.getVersionsByPosition(user.getId(), targetPosition));
    }

    /**
     * 获取版本详情（含 content）
     */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<ResumeVersionResponse> getVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId) {
        return ApiResponse.success(resumeService.getVersion(user.getId(), versionId));
    }

    /**
     * 更新版本内容
     * 请求体：{ "content": "...", "versionName": "..." }
     */
    @PutMapping("/versions/{versionId}")
    public ApiResponse<ResumeVersionResponse> updateVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId,
            @RequestBody Map<String, Object> request) {
        String content = getAsString(request.get("content"));
        String versionName = getAsString(request.get("versionName"));
        return ApiResponse.success("更新成功",
                resumeService.updateVersion(user.getId(), versionId, content, versionName));
    }

    /**
     * 删除版本
     */
    @DeleteMapping("/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId) {
        resumeService.deleteVersion(user.getId(), versionId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 切换激活版本
     */
    @PostMapping("/versions/{versionId}/activate")
    public ApiResponse<ResumeVersionResponse> activateVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId) {
        return ApiResponse.success("激活成功",
                resumeService.activateVersion(user.getId(), versionId));
    }

    /**
     * 获取当前激活版本
     */
    @GetMapping("/active")
    public ApiResponse<ResumeVersionResponse> getActiveVersion(@AuthenticationPrincipal User user) {
        ResumeVersionResponse active = resumeService.getActiveVersion(user.getId());
        if (active == null) {
            return ApiResponse.error("当前无激活的简历版本");
        }
        return ApiResponse.success(active);
    }

    /**
     * 导出简历
     * <p>
     * 支持 format: pdf | md | docx
     * 优先实现 pdf
     */
    @GetMapping("/versions/{versionId}/export")
    public ResponseEntity<Resource> exportResume(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "pdf") String format) {

        ResumeVersionResponse version = resumeService.getVersion(user.getId(), versionId);
        if (version.getContent() == null || version.getContent().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] data;
        String contentType;
        String fileExtension;
        switch (format.toLowerCase()) {
            case "pdf" -> {
                data = resumeService.exportToPdf(version);
                contentType = "application/pdf";
                fileExtension = "pdf";
            }
            case "md" -> {
                data = version.getContent().getBytes(StandardCharsets.UTF_8);
                contentType = "text/markdown; charset=utf-8";
                fileExtension = "md";
            }
            case "docx" -> {
                data = resumeService.exportToDocx(version);
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                fileExtension = "docx";
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }

        String fileName = URLEncoder.encode(
                (version.getVersionName() != null ? version.getVersionName() : "resume") + "." + fileExtension,
                StandardCharsets.UTF_8);

        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(resource);
    }

    /**
     * Sprint 7-C-B: 对比两个简历版本
     * <p>
     * 用法：GET /resume/diff?versionA=1&versionB=2
     * 用于面试展示：不同岗位简历的差异化策略
     */
    @GetMapping("/diff")
    public ApiResponse<ResumeDiffResponse> diffVersions(
            @AuthenticationPrincipal User user,
            @RequestParam Long versionA,
            @RequestParam Long versionB) {
        return ApiResponse.success(
                resumeDiffService.diff(user.getId(), versionA, versionB));
    }

    private String getAsString(Object v) {
        return v != null ? v.toString() : null;
    }

    private Long getAsLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean getAsBool(Object v, boolean defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Boolean) return (Boolean) v;
        return defaultValue;
    }
}
