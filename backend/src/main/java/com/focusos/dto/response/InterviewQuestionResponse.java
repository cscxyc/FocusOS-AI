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
 * Sprint 7-C-A: 面试题响应 DTO
 * <p>
 * 用于 InterviewAgent 输出的结构化面试题，通过 ObjectMapper 序列化保证 JSON 稳定性。
 * 替代原先手动拼接 JSON 字符串的方式，避免特殊字符（如正则表达式反斜杠）导致解析失败。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterviewQuestionResponse {

    @JsonProperty("interviewQuestions")
    private List<Question> interviewQuestions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Question {
        /** 问题类型：技术问题 / 行为问题 / 项目深挖 */
        @JsonProperty("type")
        private String type;

        /** 类别：Java基础 / Spring Boot / AI应用开发 / RAG / Agent / 项目深挖 */
        @JsonProperty("category")
        private String category;

        /** 面试题目 */
        @JsonProperty("question")
        private String question;

        /** 难度：简单 / 中等 / 困难 */
        @JsonProperty("difficulty")
        private String difficulty;

        /** 参考答案要点 */
        @JsonProperty("expectedAnswer")
        private String expectedAnswer;

        /** 用户真实项目/经历引用（含来源文档名） */
        @JsonProperty("userProjectReference")
        private String userProjectReference;

        /** 追问列表 */
        @JsonProperty("followUpQuestions")
        @Builder.Default
        private List<String> followUpQuestions = new ArrayList<>();
    }
}
