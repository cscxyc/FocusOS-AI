package com.focusos.service;

import com.focusos.dto.response.ResumeDiffResponse;
import com.focusos.dto.response.ResumeVersionResponse;
import com.focusos.entity.ResumeVersion;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sprint 7-C-B: 简历版本 Diff 对比服务
 * <p>
 * 对比两个 ResumeVersion 的技能关键词和段落内容差异，用于：
 * 1. 面试展示（不同岗位简历的差异化策略）
 * 2. 版本演进追踪（编辑前后的内容变化）
 * <p>
 * Diff 策略：
 * - 技能关键词：从 content 中提取技术名词（Spring/MySQL/RAG/Milvus 等），对比集合差异
 * - 段落级：按 Markdown ## 标题分 section，对比每个 section 的文本内容
 * - 相似度：基于共有技能数 + section 文本相似度（Jaccard 系数）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeDiffService {

    private final ResumeVersionRepository resumeVersionRepository;

    /**
     * 对比两个简历版本
     *
     * @param userId   用户 ID（所有权校验）
     * @param versionA 版本 A ID
     * @param versionB 版本 B ID
     * @return Diff 结果
     */
    public ResumeDiffResponse diff(Long userId, Long versionA, Long versionB) {
        if (versionA == null || versionB == null) {
            throw new BusinessException("必须提供两个版本 ID");
        }
        if (versionA.equals(versionB)) {
            throw new BusinessException("不能对比同一版本");
        }

        ResumeVersion a = getOwnedVersion(userId, versionA);
        ResumeVersion b = getOwnedVersion(userId, versionB);

        String contentA = a.getContent() != null ? a.getContent() : "";
        String contentB = b.getContent() != null ? b.getContent() : "";

        // 1. 提取技能关键词
        Set<String> skillsA = extractSkills(contentA);
        Set<String> skillsB = extractSkills(contentB);

        // 2. 集合差异
        Set<String> added = new LinkedHashSet<>(skillsA);  // A 有 B 没有
        added.removeAll(skillsB);

        Set<String> removed = new LinkedHashSet<>(skillsB); // B 有 A 没有
        removed.removeAll(skillsA);

        Set<String> common = new LinkedHashSet<>(skillsA);
        common.retainAll(skillsB);

        // 3. 段落级 diff（按 ## section）
        List<ResumeDiffResponse.SectionDiff> sectionDiffs = diffSections(contentA, contentB);

        // 4. 相似度（Jaccard 系数，基于技能集合）
        double similarity = computeSimilarity(skillsA, skillsB, sectionDiffs);

        ResumeDiffResponse.DiffSummary summary = ResumeDiffResponse.DiffSummary.builder()
                .addedCount(added.size())
                .removedCount(removed.size())
                .commonCount(common.size())
                .changedCount(sectionDiffs.size())
                .similarityScore(Math.round(similarity * 10) / 10.0)
                .build();

        log.info("ResumeDiff: A={} B={}, added={} removed={} common={} sections={} similarity={}",
                versionA, versionB, added.size(), removed.size(), common.size(),
                sectionDiffs.size(), summary.getSimilarityScore());

        return ResumeDiffResponse.builder()
                .versionAId(versionA)
                .versionBId(versionB)
                .versionAPosition(a.getTargetPosition())
                .versionBPosition(b.getTargetPosition())
                .added(new ArrayList<>(added))
                .removed(new ArrayList<>(removed))
                .common(new ArrayList<>(common))
                .changed(sectionDiffs)
                .summary(summary)
                .build();
    }

    /**
     * 从 Markdown 简历内容中提取技能关键词
     * <p>
     * 策略：匹配常见技术名词（中英文），去重，归一化大小写
     */
    private Set<String> extractSkills(String content) {
        if (content == null || content.isBlank()) return Collections.emptySet();

        // 常见技术栈关键词（覆盖 Java/前端/AI/数据库/中间件/工具）
        // 使用单词边界匹配，避免 Java 匹配 JavaScript 的子串
        String skillPattern = String.join("|",
                // Java 生态
                "\\bJava\\b", "\\bSpring\\b", "Spring\\s*Boot", "Spring\\s*Cloud", "Spring\\s*MVC",
                "MyBatis", "MyBatis-Plus", "JPA", "Hibernate", "Maven", "Gradle", "JVM",
                "JDK", "Lambda", "Stream\\s*API", "Lombok",
                // 并发
                "多线程", "并发编程", "JUC", "ThreadLocal", "ThreadPool", "CompletableFuture",
                // 数据库
                "MySQL", "PostgreSQL", "Redis", "MongoDB", "Elasticsearch", "SQL", "索引优化",
                "分库分表", "事务", "MVCC",
                // 中间件
                "Kafka", "RabbitMQ", "RocketMQ", "Nacos", "Zookeeper", "Eureka", "Feign",
                "Gateway", "Sentinel",
                // 微服务
                "微服务", "分布式", "服务治理", "限流", "熔断", "降级",
                // 前端
                "Vue", "React", "TypeScript", "JavaScript", "HTML", "CSS", "TailwindCSS",
                "Next\\.js", "Node\\.js", "Axios",
                // AI / 大模型
                "RAG", "Embedding", "Milvus", "Pinecone", "Weaviate", "Chroma",
                "LangChain4j", "LangChain", "向量数据库", "向量检索", "语义检索",
                "Prompt\\s*Engineering", "Function\\s*Calling", "Fine-tuning", "微调",
                "大模型", "LLM", "ChatGPT", "通义千问", "qwen", "DashScope", "百炼",
                "Agent", "Multi-Agent", "Workflow", "SSE",
                // DevOps
                "Docker", "Kubernetes", "K8s", "Jenkins", "Git", "CI/CD", "Linux", "Nginx",
                // 测试
                "JUnit", "Mockito", "TestNG", "压力测试",
                // 算法
                "数据结构", "算法", "LeetCode", "动态规划"
        );

        Pattern p = Pattern.compile(skillPattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);

        Set<String> skills = new LinkedHashSet<>();
        while (m.find()) {
            String skill = m.group().replaceAll("\\s+", " ").trim();
            // 归一化：首字母大写（对英文）
            if (skill.matches("[a-zA-Z].*")) {
                skill = normalizeCase(skill);
            }
            skills.add(skill);
        }
        return skills;
    }

    /**
     * 归一化英文技能名大小写
     * Spring Boot, MySQL, RAG, Java 等使用标准大小写
     */
    private String normalizeCase(String skill) {
        String lower = skill.toLowerCase();
        return switch (lower) {
            case "java" -> "Java";
            case "spring" -> "Spring";
            case "spring boot" -> "Spring Boot";
            case "spring cloud" -> "Spring Cloud";
            case "spring mvc" -> "Spring MVC";
            case "mybatis" -> "MyBatis";
            case "mybatis-plus" -> "MyBatis-Plus";
            case "mysql" -> "MySQL";
            case "postgresql" -> "PostgreSQL";
            case "redis" -> "Redis";
            case "mongodb" -> "MongoDB";
            case "elasticsearch" -> "Elasticsearch";
            case "kafka" -> "Kafka";
            case "rabbitmq" -> "RabbitMQ";
            case "rocketmq" -> "RocketMQ";
            case "nacos" -> "Nacos";
            case "zookeeper" -> "Zookeeper";
            case "eureka" -> "Eureka";
            case "feign" -> "Feign";
            case "gateway" -> "Gateway";
            case "sentinel" -> "Sentinel";
            case "vue" -> "Vue";
            case "react" -> "React";
            case "typescript" -> "TypeScript";
            case "javascript" -> "JavaScript";
            case "html" -> "HTML";
            case "css" -> "CSS";
            case "tailwindcss" -> "TailwindCSS";
            case "next.js" -> "Next.js";
            case "node.js" -> "Node.js";
            case "axios" -> "Axios";
            case "rag" -> "RAG";
            case "embedding" -> "Embedding";
            case "milvus" -> "Milvus";
            case "pinecone" -> "Pinecone";
            case "weaviate" -> "Weaviate";
            case "chroma" -> "Chroma";
            case "langchain4j" -> "LangChain4j";
            case "langchain" -> "LangChain";
            case "prompt engineering" -> "Prompt Engineering";
            case "function calling" -> "Function Calling";
            case "fine-tuning" -> "Fine-tuning";
            case "llm" -> "LLM";
            case "chatgpt" -> "ChatGPT";
            case "qwen" -> "Qwen";
            case "dashscope" -> "DashScope";
            case "agent" -> "Agent";
            case "multi-agent" -> "Multi-Agent";
            case "workflow" -> "Workflow";
            case "sse" -> "SSE";
            case "docker" -> "Docker";
            case "kubernetes", "k8s" -> "Kubernetes";
            case "jenkins" -> "Jenkins";
            case "git" -> "Git";
            case "ci/cd" -> "CI/CD";
            case "linux" -> "Linux";
            case "nginx" -> "Nginx";
            case "junit" -> "JUnit";
            case "mockito" -> "Mockito";
            case "testng" -> "TestNG";
            case "maven" -> "Maven";
            case "gradle" -> "Gradle";
            case "jvm" -> "JVM";
            case "jdk" -> "JDK";
            case "lambda" -> "Lambda";
            case "stream api" -> "Stream API";
            case "lombok" -> "Lombok";
            case "juc" -> "JUC";
            case "threadlocal" -> "ThreadLocal";
            case "threadpool" -> "ThreadPool";
            case "completablefuture" -> "CompletableFuture";
            case "sql" -> "SQL";
            case "mvcc" -> "MVCC";
            case "api" -> "API";
            default -> skill;
        };
    }

    /**
     * 按 Markdown ## 标题分 section，对比每个 section 内容
     */
    private List<ResumeDiffResponse.SectionDiff> diffSections(String contentA, String contentB) {
        Map<String, String> sectionsA = splitBySection(contentA);
        Map<String, String> sectionsB = splitBySection(contentB);

        Set<String> allSections = new LinkedHashSet<>();
        allSections.addAll(sectionsA.keySet());
        allSections.addAll(sectionsB.keySet());

        List<ResumeDiffResponse.SectionDiff> diffs = new ArrayList<>();
        for (String section : allSections) {
            String textA = sectionsA.getOrDefault(section, "");
            String textB = sectionsB.getOrDefault(section, "");

            if (textA.isEmpty() && !textB.isEmpty()) {
                diffs.add(ResumeDiffResponse.SectionDiff.builder()
                        .section(section).before("").after(textB).changeType("added").build());
            } else if (!textA.isEmpty() && textB.isEmpty()) {
                diffs.add(ResumeDiffResponse.SectionDiff.builder()
                        .section(section).before(textA).after("").changeType("removed").build());
            } else if (!textA.equals(textB)) {
                diffs.add(ResumeDiffResponse.SectionDiff.builder()
                        .section(section).before(textA).after(textB).changeType("changed").build());
            }
            // 完全相同的 section 不加入 diff 列表
        }
        return diffs;
    }

    /**
     * 按 ## 二级标题切分 Markdown 内容为 section map
     */
    private Map<String, String> splitBySection(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (content == null || content.isBlank()) return sections;

        String[] lines = content.split("\n");
        String currentSection = "（顶部摘要）";
        StringBuilder currentText = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("## ")) {
                // 保存上一个 section
                if (currentText.length() > 0) {
                    sections.put(currentSection, currentText.toString().trim());
                }
                currentSection = line.trim().substring(3).trim();
                currentText = new StringBuilder();
            } else {
                currentText.append(line).append("\n");
            }
        }
        // 保存最后一个 section
        if (currentText.length() > 0) {
            sections.put(currentSection, currentText.toString().trim());
        }
        return sections;
    }

    /**
     * 计算相似度（综合技能集合 Jaccard + section 文本相似度）
     */
    private double computeSimilarity(Set<String> skillsA, Set<String> skillsB,
                                      List<ResumeDiffResponse.SectionDiff> sectionDiffs) {
        // 1. 技能集合 Jaccard 系数
        double skillSim = 0;
        if (!skillsA.isEmpty() || !skillsB.isEmpty()) {
            Set<String> intersection = new HashSet<>(skillsA);
            intersection.retainAll(skillsB);
            Set<String> union = new HashSet<>(skillsA);
            union.addAll(skillsB);
            skillSim = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        }

        // 2. section 变化比例（变化越少越相似）
        double sectionSim = 1.0;
        if (!sectionDiffs.isEmpty()) {
            // 假设典型简历 6 个 section，变化的 section 越多相似度越低
            sectionSim = Math.max(0, 1.0 - sectionDiffs.size() * 0.15);
        }

        // 综合：技能权重 0.6，section 权重 0.4
        return (skillSim * 0.6 + sectionSim * 0.4) * 100;
    }

    /**
     * 获取用户拥有的版本（所有权校验）
     */
    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        ResumeVersion v = resumeVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("简历版本", versionId));
        if (!v.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该简历版本");
        }
        return v;
    }
}
