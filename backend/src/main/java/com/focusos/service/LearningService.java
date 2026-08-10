package com.focusos.service;

import com.focusos.agent.LearningAgent;
import com.focusos.dto.request.CreateLearningPlanRequest;
import com.focusos.dto.request.CreateLearningSessionRequest;
import com.focusos.dto.response.LearningPlanResponse;
import com.focusos.dto.response.LearningSessionResponse;
import com.focusos.entity.LearningPlan;
import com.focusos.entity.LearningSession;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.LearningPlanRepository;
import com.focusos.repository.LearningSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningService {

    private final LearningPlanRepository learningPlanRepository;
    private final LearningSessionRepository learningSessionRepository;
    private final LearningAgent learningAgent;

    @Transactional
    public LearningPlanResponse createPlan(Long userId, CreateLearningPlanRequest request) {
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setTitle(request.getTitle());
        plan.setGoal(request.getGoal());
        plan.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        plan.setEndDate(request.getEndDate());
        plan.setDailyTargetMinutes(request.getDailyTargetMinutes());
        plan.setStatus("ACTIVE");

        LearningPlan savedPlan = learningPlanRepository.save(plan);
        log.info("Learning plan created: {} for user: {}", savedPlan.getTitle(), userId);
        return LearningPlanResponse.fromEntity(savedPlan);
    }

    public List<LearningPlanResponse> getPlans(Long userId) {
        return learningPlanRepository.findByUserId(userId).stream()
                .map(LearningPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LearningPlanResponse> getPlansByStatus(Long userId, String status) {
        return learningPlanRepository.findByUserIdAndStatus(userId, status).stream()
                .map(LearningPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public LearningSessionResponse addSession(Long userId, CreateLearningSessionRequest request) {
        if (request.getPlanId() != null) {
            LearningPlan plan = learningPlanRepository.findById(request.getPlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("学习计划", request.getPlanId()));
            if (!plan.getUserId().equals(userId)) {
                throw new BusinessException("无权操作此学习计划");
            }
        }

        LearningSession session = new LearningSession();
        session.setUserId(userId);
        session.setPlanId(request.getPlanId());
        session.setSubject(request.getSubject());
        session.setDurationMinutes(request.getDurationMinutes());
        session.setSessionDate(request.getSessionDate() != null ? request.getSessionDate() : LocalDate.now());
        session.setNotes(request.getNotes());
        session.setFocusLevel(request.getFocusLevel());

        LearningSession savedSession = learningSessionRepository.save(session);
        log.info("Learning session added for user: {}", userId);
        return LearningSessionResponse.fromEntity(savedSession);
    }

    public List<LearningSessionResponse> getSessions(Long userId) {
        return learningSessionRepository.findByUserId(userId).stream()
                .map(LearningSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LearningSessionResponse> getSessionsByPlan(Long userId, Long planId) {
        return learningSessionRepository.findByUserIdAndPlanId(userId, planId).stream()
                .map(LearningSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LearningSessionResponse> getTodaySessions(Long userId) {
        return learningSessionRepository.findByUserIdAndSessionDate(userId, LocalDate.now()).stream()
                .map(LearningSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public String generatePlanWithAI(Long userId, String goal, int durationWeeks, int dailyMinutes) {
        String plan = learningAgent.generateLearningPlan(goal, durationWeeks, dailyMinutes);
        log.info("AI-generated learning plan for user: {}", userId);
        return plan;
    }

    public String dailyReviewWithAI(Long userId) {
        List<LearningSession> todaySessions =
                learningSessionRepository.findByUserIdAndSessionDate(userId, LocalDate.now());

        StringBuilder summary = new StringBuilder("今日学习总结：\n");
        int totalMinutes = 0;
        for (LearningSession session : todaySessions) {
            summary.append(String.format("- %s: %d分钟, 专注度%d%n",
                    session.getSubject(),
                    session.getDurationMinutes(),
                    session.getFocusLevel() != null ? session.getFocusLevel() : 0));
            totalMinutes += session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
        }
        summary.append(String.format("\n总学习时长: %d分钟", totalMinutes));

        return learningAgent.dailyReview(summary.toString());
    }

    public int getTodayTotalMinutes(Long userId) {
        return learningSessionRepository.sumDurationByUserIdAndDate(userId, LocalDate.now());
    }

    public double getWeeklyAvgFocus(Long userId) {
        LocalDate start = LocalDate.now().minusWeeks(1);
        LocalDate end = LocalDate.now();
        return learningSessionRepository.avgFocusLevelByUserIdAndDateBetween(userId, start, end);
    }
}
