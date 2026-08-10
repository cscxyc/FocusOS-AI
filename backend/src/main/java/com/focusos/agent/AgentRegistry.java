package com.focusos.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 注册表 — Spring 自动注入所有 FocusAgent 实现
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, FocusAgent> agents = new HashMap<>();

    public AgentRegistry(List<FocusAgent> agentList) {
        for (FocusAgent agent : agentList) {
            agents.put(agent.type(), agent);
            log.info("Registered agent: type={}", agent.type());
        }
    }

    public Optional<FocusAgent> getAgent(String type) {
        return Optional.ofNullable(agents.get(type));
    }

    public Map<String, FocusAgent> getAllAgents() {
        return agents;
    }
}
