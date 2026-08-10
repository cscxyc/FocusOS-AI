package com.focusos.dto.response;

import com.focusos.entity.LearningSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningSessionResponse {

    private Long id;
    private Long planId;
    private String subject;
    private Integer durationMinutes;
    private LocalDate sessionDate;
    private String notes;
    private Integer focusLevel;
    private LocalDateTime createdAt;

    public static LearningSessionResponse fromEntity(LearningSession session) {
        return new LearningSessionResponse(
            session.getId(),
            session.getPlanId(),
            session.getSubject(),
            session.getDurationMinutes(),
            session.getSessionDate(),
            session.getNotes(),
            session.getFocusLevel(),
            session.getCreatedAt()
        );
    }
}
