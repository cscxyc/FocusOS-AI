package com.focusos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLearningSessionRequest {

    private Long planId;

    private String subject;

    @NotNull(message = "学习时长不能为空")
    private Integer durationMinutes;

    private LocalDate sessionDate;

    private String notes;

    private Integer focusLevel;
}
