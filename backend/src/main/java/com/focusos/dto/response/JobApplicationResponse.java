package com.focusos.dto.response;

import com.focusos.entity.JobApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationResponse {

    private Long id;
    private String company;
    private String position;
    private String jobDescription;
    private String status;
    private Double matchScore;
    private String notes;
    private LocalDate appliedDate;
    private LocalDateTime createdAt;

    public static JobApplicationResponse fromEntity(JobApplication app) {
        return new JobApplicationResponse(
            app.getId(),
            app.getCompany(),
            app.getPosition(),
            app.getJobDescription(),
            app.getStatus(),
            app.getMatchScore(),
            app.getNotes(),
            app.getAppliedDate(),
            app.getCreatedAt()
        );
    }
}
