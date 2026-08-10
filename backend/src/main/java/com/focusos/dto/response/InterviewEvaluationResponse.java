package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 7-C-A: 面试评价响应 DTO
 * <p>
 * 用于 InterviewAgent 的模拟面试评价输出，通过 ObjectMapper 序列化保证 JSON 稳定性。
 * 替代原先手动拼接 JSON 字符串的方式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterviewEvaluationResponse {

    /** 评分 0-100 */
    @JsonProperty("score")
    private Integer score;

    /** 优势列表 */
    @JsonProperty("strengths")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    /** 弱点列表 */
    @JsonProperty("weaknesses")
    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    /** 改进建议列表 */
    @JsonProperty("improvement")
    @Builder.Default
    private List<String> improvement = new ArrayList<>();

    /** 事实核查结果（检测用户是否编造经历） */
    @JsonProperty("factCheck")
    private FactCheck factCheck;

    /** 求职准备度（仅最终评价包含） */
    @JsonProperty("jobReadiness")
    private String jobReadiness;

    /** 重点练习方向（仅最终评价包含） */
    @JsonProperty("focusAreas")
    @Builder.Default
    private List<String> focusAreas = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FactCheck {
        /** 是否检测到编造 */
        @JsonProperty("fabricated")
        private Boolean fabricated;

        /** 编造详情 */
        @JsonProperty("fabricationDetails")
        private String fabricationDetails;
    }
}
