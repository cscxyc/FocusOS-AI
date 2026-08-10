package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeJDRequest {

    @NotBlank(message = "职位描述不能为空")
    private String jobDescription;

    private String company;

    private String position;
}
