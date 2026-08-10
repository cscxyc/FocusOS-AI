package com.focusos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleEventRequest {

    @NotBlank(message = "事件标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "事件日期不能为空")
    private LocalDate eventDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String eventType;

    private String priority;
}
