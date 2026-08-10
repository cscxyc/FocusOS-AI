package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RAGEvalRequest {
    private Long userId;
    @NotBlank(message = "question不能为空")
    private String question;
    private String retrievedContext;
    private String answer;
}
