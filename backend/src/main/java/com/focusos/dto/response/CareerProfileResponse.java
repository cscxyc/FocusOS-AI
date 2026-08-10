package com.focusos.dto.response;

import com.focusos.entity.CareerProfile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerProfileResponse {

    private Long id;
    private String title;
    private String summary;
    private String skills;
    private String experience;
    private String education;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CareerProfileResponse fromEntity(CareerProfile profile) {
        return new CareerProfileResponse(
            profile.getId(),
            profile.getTitle(),
            profile.getSummary(),
            profile.getSkills(),
            profile.getExperience(),
            profile.getEducation(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}
