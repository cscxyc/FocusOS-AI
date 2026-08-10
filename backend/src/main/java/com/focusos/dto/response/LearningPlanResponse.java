package com.focusos.dto.response;

import com.focusos.entity.LearningPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlanResponse {

    private Long id;
    private String title;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dailyTargetMinutes;
    private String status;
    private LocalDateTime createdAt;

    public static LearningPlanResponse fromEntity(LearningPlan plan) {
        return new LearningPlanResponse(
            plan.getId(),
            plan.getTitle(),
            plan.getGoal(),
            plan.getStartDate(),
            plan.getEndDate(),
            plan.getDailyTargetMinutes(),
            plan.getStatus(),
            plan.getCreatedAt()
        );
    }
}
