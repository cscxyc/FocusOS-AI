package com.focusos.dto.response;

import com.focusos.entity.ScheduleEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEventResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean completed;
    private String eventType;
    private String priority;
    private LocalDateTime createdAt;

    public static ScheduleEventResponse fromEntity(ScheduleEvent event) {
        return new ScheduleEventResponse(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getEventDate(),
            event.getStartTime(),
            event.getEndTime(),
            event.getIsCompleted(),
            event.getEventType(),
            event.getPriority(),
            event.getCreatedAt()
        );
    }
}
