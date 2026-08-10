package com.focusos.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCareerProfileRequest {

    private String title;

    private String summary;

    private String skills;

    private String experience;

    private String education;
}
