package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEvaluationRequest {
    private Long userId;
    @NotBlank(message = "agentType不能为空")
    private String agentType;
    private String evaluationType;
    private String workflowId;
    private String input;
    @NotBlank(message = "output不能为空")
    private String output;
    private String promptVersion;
}
