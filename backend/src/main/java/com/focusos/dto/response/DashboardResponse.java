package com.focusos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private Long userId;
    private String username;
    private Map<String, Object> learningStats;
    private List<Map<String, Object>> recentSessions;
    private List<Map<String, Object>> todayEvents;
    private Map<String, Object> careerStats;
    private Map<String, Object> aiRecommendation;
}
