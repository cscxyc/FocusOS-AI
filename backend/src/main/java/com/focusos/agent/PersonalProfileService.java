package com.focusos.agent;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sprint 6-C: Personal Profile 专用检索服务
 * <p>
 * 职责：
 * 1. 根据 userId 检索简历/实习/项目/目标等个人资料
 * 2. 返回带来源信息的 UserProfileContext（每个 skill/project/experience 都标注来源文档）
 * 3. 使用 ProfileQueryBuilder 生成短而精准的检索 query，避免 embedding 完整用户目标
 * <p>
 * 与 RAGAgent.searchUserProfile 的区别：
 * - RAGAgent 返回纯文本拼接（无来源信息）
 * - PersonalProfileService 返回结构化 UserProfileContext（含 sourceDocuments）
 * - 支持多 query 检索提高召回率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalProfileService {

    private final RAGAgent ragAgent;
    private final ProfileQueryBuilder queryBuilder;

    /**
     * 检索用户完整画像（带来源信息）
     * <p>
     * 调用链：ProfileQueryBuilder 生成 query → RAGAgent.searchWithMetadata → 组装 UserProfileContext
     */
    public UserProfileContext retrieveProfile(Long userId, String userGoal) {
        long start = System.currentTimeMillis();
        log.info("[PersonalProfile] Start retrieving profile for userId={}, goal={}", userId, userGoal);

        List<String> categories = List.of("career", "project", "experience", "goal", "learning");
        return retrieveProfileInternal(userId, userGoal, categories, start);
    }

    /**
     * Sprint 7-A: Career 专用检索 — 仅检索 CAREER/EXPERIENCE/PROJECT，排除 LEARNING
     * <p>
     * 用于 Career Workflow 的 Context 初始化，确保职业分析基于真实简历/项目/实习资料。
     * 学习计划生成时由 LearningAgent 自行检索 LEARNING 类别。
     */
    public UserProfileContext retrieveCareerProfile(Long userId, String jobDescription) {
        long start = System.currentTimeMillis();
        log.info("[PersonalProfile] Sprint 7-A Career retrieval for userId={}, jdLength={}",
                userId, jobDescription != null ? jobDescription.length() : 0);

        // Career 专用：仅检索 CAREER/EXPERIENCE/PROJECT，不检索 LEARNING
        List<String> categories = List.of("career", "project", "experience", "goal");
        return retrieveProfileInternal(userId, jobDescription, categories, start);
    }

    /**
     * 内部检索逻辑（支持自定义 categories）
     */
    private UserProfileContext retrieveProfileInternal(Long userId, String userGoal,
                                                        List<String> categories, long start) {
        Map<String, List<EmbeddingMatch<TextSegment>>> rawMatches = new LinkedHashMap<>();

        // 1. 按分类多 query 检索（提高召回率）
        Map<String, List<String>> allQueries = queryBuilder.buildProfileQueries(categories);
        for (String cat : categories) {
            List<EmbeddingMatch<TextSegment>> catMatches = new ArrayList<>();
            List<String> queries = allQueries.getOrDefault(cat, List.of(queryBuilder.buildQueryForCategory(cat)));
            for (String q : queries) {
                List<EmbeddingMatch<TextSegment>> matches = ragAgent.searchWithMetadata(q, userId, cat, 5, 0.20);
                catMatches.addAll(matches);
            }
            // 去重（按 text 内容）
            catMatches = dedupByContent(catMatches);
            rawMatches.put(cat, catMatches);
            log.info("[PersonalProfile] category={}: {} matches (after dedup)", cat, catMatches.size());
        }

        // 2. 附加目标导向检索（基于用户目标提取关键词）
        if (userGoal != null && !userGoal.isBlank()) {
            String goalQuery = queryBuilder.buildGoalOrientedQuery(userGoal);
            List<EmbeddingMatch<TextSegment>> goalMatches = ragAgent.searchWithMetadata(goalQuery, userId, null, 8, 0.20);
            goalMatches = dedupByContent(goalMatches);
            // 合并到对应分类
            for (EmbeddingMatch<TextSegment> m : goalMatches) {
                String cat = m.embedded().metadata().getString("category");
                if (cat != null && !cat.isBlank()) {
                    rawMatches.computeIfAbsent(cat, k -> new ArrayList<>()).add(m);
                }
            }
            log.info("[PersonalProfile] goal-oriented query '{}': {} matches", goalQuery, goalMatches.size());
        }

        // 3. 组装结构化 UserProfileContext
        UserProfileContext context = assembleContext(userId, userGoal, rawMatches);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[PersonalProfile] Done: userId={}, profileLength={}, sourceDocs={}, elapsed={}ms",
                userId, context.getProfileText().length(), context.getSourceDocuments().size(), elapsed);
        return context;
    }

    /**
     * 组装带来源信息的 UserProfileContext
     */
    private UserProfileContext assembleContext(Long userId, String userGoal,
                                                Map<String, List<EmbeddingMatch<TextSegment>>> rawMatches) {
        List<UserProfileContext.SourcedSkill> skills = new ArrayList<>();
        List<UserProfileContext.SourcedProject> projects = new ArrayList<>();
        List<UserProfileContext.SourcedExperience> experiences = new ArrayList<>();
        List<UserProfileContext.SourcedDocument> sourceDocs = new ArrayList<>();
        StringBuilder profileText = new StringBuilder();
        Set<String> seenDocIds = new HashSet<>();

        // career 分类 → skills
        for (EmbeddingMatch<TextSegment> m : rawMatches.getOrDefault("career", List.of())) {
            String text = m.embedded().text();
            String source = m.embedded().metadata().getString("fileName");
            String docTitle = m.embedded().metadata().getString("documentTitle");
            String docId = m.embedded().metadata().getString("documentId");

            profileText.append("【简历/技能】").append(text).append("\n\n");

            // 提取技能关键词（简单实现：从文本中识别常见技术词）
            List<String> extractedSkills = extractSkills(text);
            for (String skill : extractedSkills) {
                skills.add(UserProfileContext.SourcedSkill.builder()
                        .value(skill)
                        .source(source != null ? source : (docTitle != null ? docTitle : "unknown"))
                        .build());
            }

            if (docId != null && seenDocIds.add(docId)) {
                sourceDocs.add(UserProfileContext.SourcedDocument.builder()
                        .documentId(docId)
                        .title(docTitle)
                        .fileName(source)
                        .category("career")
                        .documentType(m.embedded().metadata().getString("documentType"))
                        .build());
            }
        }

        // project 分类 → projects
        for (EmbeddingMatch<TextSegment> m : rawMatches.getOrDefault("project", List.of())) {
            String text = m.embedded().text();
            String source = m.embedded().metadata().getString("fileName");
            String docTitle = m.embedded().metadata().getString("documentTitle");
            String docId = m.embedded().metadata().getString("documentId");

            profileText.append("【项目经历】").append(text).append("\n\n");

            projects.add(UserProfileContext.SourcedProject.builder()
                    .name(extractProjectName(text, docTitle))
                    .description(text.length() > 300 ? text.substring(0, 300) + "..." : text)
                    .source(source != null ? source : (docTitle != null ? docTitle : "unknown"))
                    .build());

            if (docId != null && seenDocIds.add(docId)) {
                sourceDocs.add(UserProfileContext.SourcedDocument.builder()
                        .documentId(docId)
                        .title(docTitle)
                        .fileName(source)
                        .category("project")
                        .documentType(m.embedded().metadata().getString("documentType"))
                        .build());
            }
        }

        // experience 分类 → experiences
        for (EmbeddingMatch<TextSegment> m : rawMatches.getOrDefault("experience", List.of())) {
            String text = m.embedded().text();
            String source = m.embedded().metadata().getString("fileName");
            String docTitle = m.embedded().metadata().getString("documentTitle");
            String docId = m.embedded().metadata().getString("documentId");

            profileText.append("【实习/工作经历】").append(text).append("\n\n");

            experiences.add(UserProfileContext.SourcedExperience.builder()
                    .company(extractCompanyName(text))
                    .description(text.length() > 300 ? text.substring(0, 300) + "..." : text)
                    .source(source != null ? source : (docTitle != null ? docTitle : "unknown"))
                    .build());

            if (docId != null && seenDocIds.add(docId)) {
                sourceDocs.add(UserProfileContext.SourcedDocument.builder()
                        .documentId(docId)
                        .title(docTitle)
                        .fileName(source)
                        .category("experience")
                        .documentType(m.embedded().metadata().getString("documentType"))
                        .build());
            }
        }

        // goal + learning 分类 → careerGoal + profileText
        for (EmbeddingMatch<TextSegment> m : rawMatches.getOrDefault("goal", List.of())) {
            profileText.append("【职业目标】").append(m.embedded().text()).append("\n\n");
        }
        for (EmbeddingMatch<TextSegment> m : rawMatches.getOrDefault("learning", List.of())) {
            profileText.append("【学习笔记】").append(m.embedded().text()).append("\n\n");
        }

        boolean success = profileText.length() > 0;
        return UserProfileContext.builder()
                .userId(userId)
                .userGoal(userGoal)
                .skills(dedupSkills(skills))
                .projects(projects)
                .experiences(experiences)
                .sourceDocuments(sourceDocs)
                .profileText(profileText.toString().trim())
                .retrievalSuccess(success)
                .retrievalError(success ? null : "未检索到任何用户资料（Milvus 可能为空或用户无文档）")
                .build();
    }

    /**
     * 从文本中提取技能关键词
     */
    private List<String> extractSkills(String text) {
        String[] knownSkills = {
                "Java", "Spring Boot", "Spring", "MyBatis", "MySQL", "Redis", "Python",
                "AI", "LLM", "RAG", "Agent", "LangChain4j", "Milvus", "向量数据库",
                "STM32", "Keil", "嵌入式", "C语言", "C++",
                "React", "Vue", "Next.js", "TypeScript", "JavaScript",
                "Docker", "Git", "Maven", "Gradle",
                "FastAPI", "Flask", "Django",
                "HTTP", "RESTful", "微服务", "Spring Cloud",
                "Linux", "Shell"
        };
        List<String> found = new ArrayList<>();
        for (String skill : knownSkills) {
            if (text.toLowerCase().contains(skill.toLowerCase())) {
                found.add(skill);
            }
        }
        return found;
    }

    private String extractProjectName(String text, String docTitle) {
        // 优先使用文档标题作为项目名
        if (docTitle != null && !docTitle.isBlank()) return docTitle;
        // 从文本第一行提取
        String firstLine = text.split("\n")[0];
        return firstLine.length() > 50 ? firstLine.substring(0, 50) + "..." : firstLine;
    }

    private String extractCompanyName(String text) {
        // 简单实现：查找"公司"关键词附近的内容
        int idx = text.indexOf("公司");
        if (idx > 0) {
            int start = Math.max(0, idx - 10);
            return text.substring(start, idx + 2).trim();
        }
        return text.split("\n")[0].length() > 30 ? text.substring(0, 30) : text.split("\n")[0];
    }

    private List<EmbeddingMatch<TextSegment>> dedupByContent(List<EmbeddingMatch<TextSegment>> matches) {
        Map<String, EmbeddingMatch<TextSegment>> seen = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> m : matches) {
            String key = m.embedded().text();
            if (!seen.containsKey(key)) {
                seen.put(key, m);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private List<UserProfileContext.SourcedSkill> dedupSkills(List<UserProfileContext.SourcedSkill> skills) {
        Map<String, UserProfileContext.SourcedSkill> seen = new LinkedHashMap<>();
        for (UserProfileContext.SourcedSkill s : skills) {
            seen.putIfAbsent(s.getValue().toLowerCase(), s);
        }
        return new ArrayList<>(seen.values());
    }
}
