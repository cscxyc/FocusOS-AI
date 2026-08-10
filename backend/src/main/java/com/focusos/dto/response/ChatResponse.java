package com.focusos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String response;
    private Integer tokensUsed;
    private String agentType;
    private List<Map<String, Object>> sources;
}
