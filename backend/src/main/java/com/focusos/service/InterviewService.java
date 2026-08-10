package com.focusos.service;

import com.focusos.agent.InterviewAgent;
import com.focusos.agent.LLMCallContext;
import com.focusos.agent.PersonalProfileService;
import com.focusos.agent.UserProfileContext;
import com.focusos.agent.WorkflowContext;
import com.focusos.entity.InterviewSession;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7-B: 模拟面试服务
 * <p>
 * 职责：
 * 1. 独立生成面试题（不依赖 Career Workflow，可在 Interview Tab 单独触发）
 * 2. 创建 InterviewSession
 * 3. 处理用户回答 + AI 评价（多轮对话）
 * 4. 生成最终评价
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewAgent interviewAgent;
    private final InterviewSessionRepository sessionRepository;
    private final PersonalProfileService personalProfileService;

    /**
     * 独立生成面试题并创建 InterviewSession
     * <p>
     * 不依赖 Career Workflow，用户可在 Interview Tab 直接生成面试题。
     *
     * @param userId         用户 ID
     * @param jobDescription 目标 JD
     * @param jobTitle       岗位名称
     * @param company        公司
     * @return 创建的 InterviewSession
     */
    @Transactional
    public InterviewSession generateInterviewQuestions(Long userId, String jobDescription,
                                                        String jobTitle, String company) {
        log.info("Sprint 7-B: generateInterviewQuestions for userId={}, jobTitle={}", userId, jobTitle);

        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, null, "interview");
        try {
            // 检索用户画像（Personal RAG）
            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(userId, jobDescription);
            String workflowContextText = profileContext.isRetrievalSuccess()
                    ? profileContext.renderWithSources()
                    : "（Personal RAG 检索失败，降级为通用上下文）";

            // 调用 InterviewAgent 生成面试题
            String questionsJson = interviewAgent.generateInterviewQuestions(
                    jobDescription, workflowContextText, null, null);

            // 创建 InterviewSession
            InterviewSession session = new InterviewSession();
            session.setUserId(userId);
            session.setJobTitle(jobTitle);
            session.setCompany(company);
            session.setJobDescription(jobDescription);
            session.setQuestionsJson(questionsJson);
            session.setConversationJson("[]");
            session.setStatus("IN_PROGRESS");
            session.setAnsweredCount(0);
            session.setProfileSufficient(profileContext.isRetrievalSuccess()
                    && profileContext.getProfileText() != null
                    && profileContext.getProfileText().length() > 50);
            session.setWorkflowId(null);  // 独立生成，不关联 Workflow

            InterviewSession saved = sessionRepository.save(session);
            log.info("InterviewSession created: id={}, userId={}, questionsLen={}",
                    saved.getId(), userId, questionsJson.length());
            return saved;
        } finally {
            LLMCallContext.clear();
        }
    }

    /**
     * 提交用户回答，获取 AI 评价
     * <p>
     * 流程：
     * 1. 从 session 中取出当前问题
     * 2. 调用 InterviewAgent.evaluateAnswer 评价
     * 3. 更新 conversationJson（追加问答记录）
     * 4. 更新 answeredCount
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param questionIndex 问题索引（从 0 开始）
     * @param userAnswer 用户回答
     * @return 评价结果 JSON + 更新后的 session
     */
    @Transactional
    public Map<String, Object> submitAnswer(Long userId, Long sessionId, int questionIndex, String userAnswer) {
        InterviewSession session = getOwnedSession(userId, sessionId);

        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, session.getWorkflowId(), "mock_interview");
        try {
            // 解析面试题列表
            List<Map<String, Object>> questions = interviewAgent.parseInterviewQuestions(session.getQuestionsJson());
            if (questionIndex < 0 || questionIndex >= questions.size()) {
                throw new BusinessException("问题索引越界: " + questionIndex + ", 总题数: " + questions.size());
            }
            Map<String, Object> currentQuestion = questions.get(questionIndex);
            String question = getAsString(currentQuestion, "question");
            String expectedAnswer = getAsString(currentQuestion, "expectedAnswer");
            String userProjectReference = getAsString(currentQuestion, "userProjectReference");

            // 检索用户画像（用于事实核查）
            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(
                    userId, session.getJobDescription());
            String workflowContextText = profileContext.isRetrievalSuccess()
                    ? profileContext.renderWithSources()
                    : "";

            // 调用 InterviewAgent 评价
            String evaluationJson = interviewAgent.evaluateAnswer(
                    question, expectedAnswer, userAnswer, userProjectReference, workflowContextText);

            // 更新对话历史
            List<Map<String, Object>> conversation = interviewAgent.parseConversation(session.getConversationJson());
            Map<String, Object> entry = interviewAgent.createConversationEntry(
                    question, expectedAnswer, userProjectReference, userAnswer, evaluationJson);
            conversation.add(entry);
            session.setConversationJson(interviewAgent.serializeConversation(conversation));
            session.setAnsweredCount((session.getAnsweredCount() != null ? session.getAnsweredCount() : 0) + 1);

            // 解析评价分数，更新 session.score（累加平均）
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> eval = om.readValue(evaluationJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Integer score = getAsInt(eval, "score");
                if (score != null) {
                    // 简单平均：当前总分 + 新分数 / 已回答数
                    int answered = session.getAnsweredCount();
                    Integer prev = session.getScore();
                    if (prev == null || prev == 0) {
                        session.setScore(score);
                    } else {
                        session.setScore((prev * (answered - 1) + score) / answered);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse evaluation score", e);
            }

            sessionRepository.save(session);
            log.info("Answer submitted: sessionId={}, questionIndex={}, answeredCount={}, score={}",
                    sessionId, questionIndex, session.getAnsweredCount(), session.getScore());

            Map<String, Object> result = new HashMap<>();
            result.put("session", session);
            result.put("evaluation", evaluationJson);
            return result;
        } finally {
            LLMCallContext.clear();
        }
    }

    /**
     * 生成最终面试评价（多轮问答后）
     * <p>
     * 将会话标记为 COMPLETED，并生成综合评价。
     */
    @Transactional
    public Map<String, Object> completeSession(Long userId, Long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);

        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, session.getWorkflowId(), "mock_interview");
        try {
            // 生成最终评价
            String finalEvalJson = interviewAgent.generateFinalEvaluation(
                    session.getConversationJson(), session.getJobDescription());

            // 解析并更新 session 字段
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> finalEval = om.readValue(finalEvalJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Integer finalScore = getAsInt(finalEval, "score");
                if (finalScore != null) session.setScore(finalScore);
                Object strengths = finalEval.get("strengths");
                Object weaknesses = finalEval.get("weaknesses");
                Object improvement = finalEval.get("improvement");
                session.setStrengths(strengths != null ? om.writeValueAsString(strengths) : "[]");
                session.setWeaknesses(weaknesses != null ? om.writeValueAsString(weaknesses) : "[]");
                session.setImprovement(improvement != null ? om.writeValueAsString(improvement) : "[]");
            } catch (Exception e) {
                log.warn("Failed to parse final evaluation", e);
            }

            session.setStatus("COMPLETED");
            sessionRepository.save(session);
            log.info("InterviewSession completed: id={}, score={}", sessionId, session.getScore());

            Map<String, Object> result = new HashMap<>();
            result.put("session", session);
            result.put("finalEvaluation", finalEvalJson);
            return result;
        } finally {
            LLMCallContext.clear();
        }
    }

    /**
     * 获取用户全部面试会话
     */
    public List<InterviewSession> getSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取指定会话
     */
    public InterviewSession getSession(Long userId, Long sessionId) {
        return getOwnedSession(userId, sessionId);
    }

    /**
     * 通过 workflowId 获取会话（Career Workflow 完成后联动查询）
     */
    public InterviewSession getSessionByWorkflowId(String workflowId) {
        return sessionRepository.findByWorkflowId(workflowId).orElse(null);
    }

    /**
     * 放弃会话
     */
    @Transactional
    public void abandonSession(Long userId, Long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        session.setStatus("ABANDONED");
        sessionRepository.save(session);
    }

    /**
     * Sprint 7-C-B: 修复历史损坏的 InterviewSession.questionsJson
     * <p>
     * 背景：Sprint 7-B 中部分 session 的 questionsJson 因 LLM 输出正则反斜杠未转义而损坏，
     * Sprint 7-C-A 已通过 LLMJsonSanitizer + DTO 解决新生成 JSON 的稳定性，
     * 本方法用于修复历史损坏数据。
     * <p>
     * 流程：
     * 1. 读取 session.jobDescription（历史 JD）
     * 2. 通过 PersonalProfileService 重新检索用户画像
     * 3. 调用 InterviewAgent.generateInterviewQuestions 重新生成（走 LLMJsonSanitizer）
     * 4. 替换 session.questionsJson
     * 5. 重置 conversationJson / answeredCount / score（因为题目变了，历史对话失效）
     *
     * @param userId    用户 ID（所有权校验）
     * @param sessionId 会话 ID
     * @return 修复后的 session
     */
    @Transactional
    public InterviewSession repairSession(Long userId, Long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        log.info("Sprint 7-C-B: repairSession sessionId={}, userId={}, jobTitle={}",
                sessionId, userId, session.getJobTitle());

        if (session.getJobDescription() == null || session.getJobDescription().isBlank()) {
            throw new BusinessException("该会话缺少 jobDescription，无法修复（历史数据不完整）");
        }

        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, session.getWorkflowId(), "interview_repair");
        try {
            // 重新检索用户画像
            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(
                    userId, session.getJobDescription());
            String workflowContextText = profileContext.isRetrievalSuccess()
                    ? profileContext.renderWithSources()
                    : "（Personal RAG 检索失败，降级为通用上下文）";

            // 调用 InterviewAgent 重新生成面试题（走 LLMJsonSanitizer + DTO 序列化）
            // Sprint 7-C-B: 增加重试机制，LLM 输出可能因特殊字符导致首次解析失败
            String newQuestionsJson = null;
            List<Map<String, Object>> parsed = List.of();
            for (int attempt = 1; attempt <= 2; attempt++) {
                newQuestionsJson = interviewAgent.generateInterviewQuestions(
                        session.getJobDescription(), workflowContextText, null, null);
                parsed = interviewAgent.parseInterviewQuestions(newQuestionsJson);
                if (!parsed.isEmpty()) {
                    break;
                }
                log.warn("Sprint 7-C-B: repairSession attempt {} produced empty questions, retrying...", attempt);
            }

            if (parsed.isEmpty()) {
                throw new BusinessException("修复失败：LLM 输出经 2 次尝试仍无法解析为有效面试题（原始长度="
                        + (newQuestionsJson != null ? newQuestionsJson.length() : 0) + "）");
            }

            // 替换字段
            session.setQuestionsJson(newQuestionsJson);
            session.setConversationJson("[]");  // 重置对话（题目变了，历史对话失效）
            session.setAnsweredCount(0);
            session.setScore(null);
            session.setProfileSufficient(profileContext.isRetrievalSuccess()
                    && profileContext.getProfileText() != null
                    && profileContext.getProfileText().length() > 50);
            // 状态重置为 IN_PROGRESS（允许重新作答）
            session.setStatus("IN_PROGRESS");

            InterviewSession saved = sessionRepository.save(session);
            log.info("InterviewSession repaired: id={}, newQuestionsLen={}, questionCount={}",
                    sessionId, newQuestionsJson.length(), parsed.size());
            return saved;
        } finally {
            LLMCallContext.clear();
        }
    }

    private InterviewSession getOwnedSession(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("面试会话", sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该面试会话");
        }
        return session;
    }

    private String getAsString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private Integer getAsInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
