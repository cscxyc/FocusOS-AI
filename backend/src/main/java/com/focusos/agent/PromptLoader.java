package com.focusos.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7-C-B: Prompt 配置化加载器
 * <p>
 * 从 resources/prompts/*.yaml 加载 Agent Prompt 配置，
 * 替代原来硬编码在 AgentPromptProvider 中的字符串。
 * <p>
 * 设计原则：
 * 1. @PostConstruct 启动时一次性加载并缓存（避免每次调用读文件）
 * 2. YAML 文件缺失时降级为空字符串（不阻塞启动）
 * 3. 渲染方法将 YAML 结构（role/rules/examples）拼接为完整 prompt 字符串
 * 4. 保持 AgentPromptProvider 方法签名不变（向后兼容）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptLoader {

    private static final String PROMPT_DIR = "classpath:prompts/*.yaml";

    /** 缓存：fileName → YAML 解析后的 Map */
    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    @PostConstruct
    public void loadAll() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PROMPT_DIR);
            Yaml yaml = new Yaml();

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null) {
                        cache.put(fileName, data);
                        log.info("PromptLoader: loaded {}", fileName);
                    }
                }
            }
            log.info("PromptLoader: total {} prompt files loaded", cache.size());
        } catch (Exception e) {
            log.warn("PromptLoader: failed to load prompt files, will fall back to inline prompts: {}", e.getMessage());
        }
    }

    /**
     * 获取指定 YAML 文件的顶层 section 数据
     */
    public Map<String, Object> getSection(String fileName, String sectionKey) {
        Map<String, Object> fileData = cache.get(fileName);
        if (fileData == null) return Map.of();
        Object section = fileData.get(sectionKey);
        if (section instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) m;
            return result;
        }
        return Map.of();
    }

    /**
     * 渲染 prompt：将 YAML 结构拼接为完整 prompt 字符串
     * <p>
     * 格式：
     * {role}
     *
     * 【{quality_constraints.title}】
     * 1. {rule1}
     * 2. {rule2}
     * ...
     *
     * 【输出要求】
     * 1. {req1}
     * ...
     */
    @SuppressWarnings("unchecked")
    public String renderPrompt(String fileName, String sectionKey) {
        Map<String, Object> section = getSection(fileName, sectionKey);
        if (section.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // role
        Object role = section.get("role");
        if (role != null) {
            sb.append(role).append("\n\n");
        }

        // quality_constraints / evaluation_constraints
        Object constraints = section.get("quality_constraints");
        if (constraints == null) constraints = section.get("evaluation_constraints");
        if (constraints instanceof Map<?, ?> cm) {
            Object title = cm.get("title");
            if (title != null) {
                sb.append("【").append(title).append("】\n");
            }
            Object rules = cm.get("rules");
            if (rules instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    sb.append(i + 1).append(". ").append(list.get(i)).append("\n");
                }
                sb.append("\n");
            }
        }

        // output_requirements
        Object outputReqs = section.get("output_requirements");
        if (outputReqs instanceof List<?> list) {
            sb.append("【输出要求】\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(i + 1).append(". ").append(list.get(i)).append("\n");
            }
            sb.append("\n");
        }

        // question_principles
        Object principles = section.get("question_principles");
        if (principles instanceof List<?> list) {
            sb.append("【出题原则】\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(i + 1).append(". ").append(list.get(i)).append("\n");
            }
            sb.append("\n");
        }

        // focusos_examples
        Object examples = section.get("focusos_examples");
        if (examples instanceof List<?> list && !list.isEmpty()) {
            sb.append("【FocusOS AI 项目深挖示例】\n");
            sb.append("基于用户的 FocusOS AI 项目（Multi-Agent + RAG 架构），可以问：\n");
            for (Object ex : list) {
                sb.append("- ").append(ex).append("\n");
            }
            sb.append("\n");
        }

        // star_example
        Object starExample = section.get("star_example");
        if (starExample instanceof Map<?, ?> sem) {
            sb.append("【STAR 原则应用示例】\n");
            sb.append("原描述：").append(sem.get("original")).append("\n");
            sb.append("优化后：").append(sem.get("optimized")).append("\n\n");
        }

        // scoring_examples (Sprint 8-A: Resume Evaluator)
        Object scoringExamples = section.get("scoring_examples");
        if (scoringExamples instanceof List<?> list && !list.isEmpty()) {
            sb.append("【评分示例】\n");
            for (Object ex : list) {
                sb.append("- ").append(ex).append("\n");
            }
            sb.append("\n");
        }

        // growth_examples (Sprint 8-B: Career Growth)
        Object growthExamples = section.get("growth_examples");
        if (growthExamples instanceof List<?> list && !list.isEmpty()) {
            sb.append("【成长规划示例 — 错误做法 vs 正确做法】\n");
            for (Object ex : list) {
                sb.append("- ").append(ex).append("\n");
            }
            sb.append("\n");
        }

        // rules (simple list, for rag/router)
        Object rules = section.get("rules");
        if (rules instanceof List<?> list) {
            sb.append("【规则】\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(i + 1).append(". ").append(list.get(i)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 检查 prompt 文件是否已加载
     */
    public boolean isLoaded(String fileName) {
        return cache.containsKey(fileName);
    }
}
