package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptVersionRequest {
    @NotBlank(message = "agentType不能为空")
    private String agentType;
    @NotBlank(message = "version不能为空")
    private String version;
    @NotBlank(message = "promptContent不能为空")
    private String promptContent;
    private String description;
    private Boolean enabled;
}
