package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GroundingCheckRequest {
    private Long userId;
    @NotBlank(message = "answer不能为空")
    private String answer;
    private String memoryContext;
    private String ragContext;
}
