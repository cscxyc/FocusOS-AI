package com.focusos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.LLMJsonSanitizer;
import com.focusos.dto.response.ResumeVersionResponse;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.ResumeVersion;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.CareerAnalysisReportRepository;
import com.focusos.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sprint 7-C-A: Resume Service
 * <p>
 * 职责：
 * 1. 创建简历版本（手动 / 基于 CareerAnalysisReport）
 * 2. 查看历史版本（列表 + 详情）
 * 3. 更新版本内容
 * 4. 删除版本
 * 5. 切换激活版本
 * 6. 基于 ResumeOptimizationAgent 输出生成简历 Markdown 内容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeVersionRepository resumeVersionRepository;
    private final CareerAnalysisReportRepository careerReportRepository;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 创建简历版本（手动）
     */
    @Transactional
    public ResumeVersionResponse createVersion(Long userId, String targetPosition,
                                                String versionName, String content,
                                                Long sourceReportId, boolean setActive) {
        log.info("Sprint 7-C-A: createVersion for userId={}, targetPosition={}, sourceReportId={}",
                userId, targetPosition, sourceReportId);

        ResumeVersion version = new ResumeVersion();
        version.setUserId(userId);
        version.setTargetPosition(targetPosition);
        version.setVersionName(versionName != null ? versionName : generateDefaultVersionName(targetPosition));
        version.setContent(content != null ? content : "");
        version.setSourceReportId(sourceReportId);
        version.setIsActive(false);

        // 若设置为激活，先取消其他激活版本
        if (setActive) {
            resumeVersionRepository.deactivateAllByUserId(userId);
            version.setIsActive(true);
        }

        ResumeVersion saved = resumeVersionRepository.save(version);
        log.info("ResumeVersion created: id={}, userId={}, isActive={}",
                saved.getId(), userId, saved.getIsActive());
        return ResumeVersionResponse.fromEntity(saved);
    }

    /**
     * 基于 CareerAnalysisReport 创建简历版本
     * <p>
     * 将 ResumeOptimizationAgent 的 JSON 输出转换为 Markdown 简历内容
     *
     * @param userId     用户 ID
     * @param reportId   CareerAnalysisReport ID
     * @param versionName 版本名称（可选）
     * @param setActive  是否设为激活版本
     */
    @Transactional
    public ResumeVersionResponse createVersionFromReport(Long userId, Long reportId,
                                                          String versionName, boolean setActive) {
        log.info("Sprint 7-C-A: createVersionFromReport for userId={}, reportId={}", userId, reportId);

        CareerAnalysisReport report = careerReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Career报告", reportId));

        if (!report.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该报告");
        }

        // 避免重复创建（同一 report 只创建一次）
        ResumeVersion existing = resumeVersionRepository
                .findByUserIdAndSourceReportId(userId, reportId)
                .orElse(null);
        if (existing != null) {
            log.info("ResumeVersion already exists for report {}, return existing id={}", reportId, existing.getId());
            return ResumeVersionResponse.fromEntity(existing);
        }

        // 将 ResumeOptimization JSON 转换为 Markdown 简历
        String resumeContent = convertResumeOptimizationToMarkdown(
                report.getResumeSuggestions(), report.getJobTitle(), report.getCompany());

        String targetPosition = report.getJobTitle() != null ? report.getJobTitle() : "目标岗位";
        String name = versionName != null ? versionName
                : generateDefaultVersionName(targetPosition);

        return createVersion(userId, targetPosition, name, resumeContent, reportId, setActive);
    }

    /**
     * 查询用户全部简历版本（列表视图，不含 content）
     */
    public List<ResumeVersionResponse> getVersions(Long userId) {
        return resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ResumeVersionResponse::fromEntitySummary)
                .collect(Collectors.toList());
    }

    /**
     * 按目标岗位查询版本
     */
    public List<ResumeVersionResponse> getVersionsByPosition(Long userId, String targetPosition) {
        return resumeVersionRepository
                .findByUserIdAndTargetPositionOrderByCreatedAtDesc(userId, targetPosition).stream()
                .map(ResumeVersionResponse::fromEntitySummary)
                .collect(Collectors.toList());
    }

    /**
     * 获取版本详情（含 content）
     */
    public ResumeVersionResponse getVersion(Long userId, Long versionId) {
        ResumeVersion version = getOwnedVersion(userId, versionId);
        return ResumeVersionResponse.fromEntity(version);
    }

    /**
     * 获取当前激活版本
     */
    public ResumeVersionResponse getActiveVersion(Long userId) {
        return resumeVersionRepository.findByUserIdAndIsActiveTrue(userId)
                .map(ResumeVersionResponse::fromEntity)
                .orElse(null);
    }

    /**
     * 更新版本内容
     */
    @Transactional
    public ResumeVersionResponse updateVersion(Long userId, Long versionId,
                                                String content, String versionName) {
        ResumeVersion version = getOwnedVersion(userId, versionId);

        if (content != null) {
            version.setContent(content);
        }
        if (versionName != null && !versionName.isBlank()) {
            version.setVersionName(versionName);
        }

        ResumeVersion saved = resumeVersionRepository.save(version);
        log.info("ResumeVersion updated: id={}, userId={}", versionId, userId);
        return ResumeVersionResponse.fromEntity(saved);
    }

    /**
     * 删除版本
     */
    @Transactional
    public void deleteVersion(Long userId, Long versionId) {
        ResumeVersion version = getOwnedVersion(userId, versionId);
        resumeVersionRepository.delete(version);
        log.info("ResumeVersion deleted: id={}, userId={}", versionId, userId);
    }

    /**
     * 切换激活版本
     */
    @Transactional
    public ResumeVersionResponse activateVersion(Long userId, Long versionId) {
        ResumeVersion version = getOwnedVersion(userId, versionId);
        // 先取消所有激活
        resumeVersionRepository.deactivateAllByUserId(userId);
        version.setIsActive(true);
        ResumeVersion saved = resumeVersionRepository.save(version);
        log.info("ResumeVersion activated: id={}, userId={}", versionId, userId);
        return ResumeVersionResponse.fromEntity(saved);
    }

    // ============================================================
    // 简历内容生成：将 ResumeOptimization JSON 转换为 Markdown
    // ============================================================

    /**
     * 将 ResumeOptimizationAgent 的 JSON 输出转换为 Markdown 简历
     * <p>
     * 输入 JSON 格式：
     * {
     *   "summaryOptimization": "...",
     *   "experienceOptimization": [{"original":"", "optimized":"", "reason":""}],
     *   "projectOptimization": [{"original":"", "optimized":"", "reason":""}],
     *   "keywordsToAdd": []
     * }
     */
    public String convertResumeOptimizationToMarkdown(String resumeOptimizationJson,
                                                       String jobTitle, String company) {
        if (resumeOptimizationJson == null || resumeOptimizationJson.isBlank()) {
            return generateEmptyResumeMarkdown(jobTitle, company);
        }

        try {
            // 使用 sanitizer 清洗后再解析
            String sanitized = jsonSanitizer.sanitize(resumeOptimizationJson);
            Map<String, Object> data = objectMapper.readValue(sanitized,
                    new TypeReference<Map<String, Object>>() {});

            StringBuilder md = new StringBuilder();
            md.append("# 简历\n\n");
            if (jobTitle != null) {
                md.append("**目标岗位**: ").append(jobTitle);
                if (company != null) md.append(" @ ").append(company);
                md.append("\n\n");
            }

            // 个人摘要
            String summary = getAsString(data, "summaryOptimization");
            if (summary != null && !summary.isBlank()) {
                md.append("## 个人摘要\n\n");
                md.append(summary).append("\n\n");
            }

            // 经历优化
            Object expObj = data.get("experienceOptimization");
            if (expObj instanceof List<?> expList && !expList.isEmpty()) {
                md.append("## 工作/实习经历\n\n");
                for (Object item : expList) {
                    if (item instanceof Map<?, ?> m) {
                        String optimized = getAsString(m, "optimized");
                        if (optimized != null && !optimized.isBlank()) {
                            md.append("- ").append(optimized).append("\n\n");
                        }
                    }
                }
            }

            // 项目优化
            Object projObj = data.get("projectOptimization");
            if (projObj instanceof List<?> projList && !projList.isEmpty()) {
                md.append("## 项目经历\n\n");
                for (Object item : projList) {
                    if (item instanceof Map<?, ?> m) {
                        String optimized = getAsString(m, "optimized");
                        if (optimized != null && !optimized.isBlank()) {
                            md.append("### ").append(getAsString(m, "original", "项目")).append("\n\n");
                            md.append(optimized).append("\n\n");
                        }
                    }
                }
            }

            // 技能关键词
            Object kwObj = data.get("keywordsToAdd");
            if (kwObj instanceof List<?> kwList && !kwList.isEmpty()) {
                md.append("## 技能关键词\n\n");
                md.append(String.join(" · ", kwList.stream()
                        .filter(Object -> Object != null)
                        .map(Object::toString)
                        .toList())).append("\n\n");
            }

            md.append("---\n");
            md.append("*由 FocusOS AI Career Assistant 基于 STAR 原则生成*\n");

            return md.toString();
        } catch (Exception e) {
            log.warn("Failed to convert resume optimization to markdown: {}", e.getMessage());
            return generateEmptyResumeMarkdown(jobTitle, company);
        }
    }

    private String generateEmptyResumeMarkdown(String jobTitle, String company) {
        StringBuilder md = new StringBuilder();
        md.append("# 简历\n\n");
        if (jobTitle != null) {
            md.append("**目标岗位**: ").append(jobTitle);
            if (company != null) md.append(" @ ").append(company);
            md.append("\n\n");
        }
        md.append("## 个人摘要\n\n（请补充个人摘要）\n\n");
        md.append("## 工作/实习经历\n\n（请补充经历）\n\n");
        md.append("## 项目经历\n\n（请补充项目）\n\n");
        md.append("## 技能\n\n（请补充技能）\n\n");
        return md.toString();
    }

    private String generateDefaultVersionName(String targetPosition) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String pos = targetPosition != null ? targetPosition.substring(0, Math.min(20, targetPosition.length())) : "通用";
        return "v_" + pos + "_" + timestamp;
    }

    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("简历版本", versionId));
        if (!version.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该简历版本");
        }
        return version;
    }

    private String getAsString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private String getAsString(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        if (v == null || v.toString().isBlank()) return defaultValue;
        return v.toString();
    }

    // ============================================================
    // Sprint 7-C-A Task4: 简历导出（PDF / Word / Markdown）
    // ============================================================

    /**
     * 导出为 PDF（使用 PDFBox）
     */
    public byte[] exportToPdf(ResumeVersionResponse version) {
        return ResumeExportUtil.markdownToPdf(version.getContent(), version.getVersionName());
    }

    /**
     * 导出为 Word docx
     */
    public byte[] exportToDocx(ResumeVersionResponse version) {
        return ResumeExportUtil.markdownToDocx(version.getContent(), version.getVersionName());
    }
}
