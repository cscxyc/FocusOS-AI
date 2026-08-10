package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 7-C-B: 简历版本 Diff 对比响应 DTO
 * <p>
 * 用于面试展示：对比两个 ResumeVersion 的技能/内容差异
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeDiffResponse {

    /** 版本 A 的 ID */
    private Long versionAId;

    /** 版本 B 的 ID */
    private Long versionBId;

    /** 版本 A 的岗位 */
    private String versionAPosition;

    /** 版本 B 的岗位 */
    private String versionBPosition;

    /** 版本 A 独有的技能/关键词（版本 B 没有） */
    @Builder.Default
    private List<String> added = new ArrayList<>();

    /** 版本 B 独有的技能/关键词（版本 A 没有） */
    @Builder.Default
    private List<String> removed = new ArrayList<>();

    /** 两个版本共有的技能/关键词 */
    @Builder.Default
    private List<String> common = new ArrayList<>();

    /** 段落级差异（按 section 比对） */
    @Builder.Default
    private List<SectionDiff> changed = new ArrayList<>();

    /** 汇总统计 */
    private DiffSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectionDiff {
        @lombok.experimental.Accessors(chain = true)
        private String section;
        private String before;
        private String after;
        /** added / removed / changed */
        private String changeType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiffSummary {
        private int addedCount;
        private int removedCount;
        private int commonCount;
        private int changedCount;
        /** 0-100，相似度分数，越高两版本越相似 */
        private double similarityScore;
    }
}
