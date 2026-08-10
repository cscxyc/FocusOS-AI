package com.focusos.repository;

import com.focusos.entity.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

    List<LearningSession> findByUserId(Long userId);

    List<LearningSession> findByUserIdAndPlanId(Long userId, Long planId);

    List<LearningSession> findByUserIdAndSessionDate(Long userId, LocalDate sessionDate);

    List<LearningSession> findByUserIdAndSessionDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM LearningSession s WHERE s.userId = :userId AND s.sessionDate = :date")
    int sumDurationByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(AVG(s.focusLevel), 0) FROM LearningSession s WHERE s.userId = :userId AND s.sessionDate BETWEEN :start AND :end")
    double avgFocusLevelByUserIdAndDateBetween(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM LearningSession s WHERE s.userId = :userId AND s.sessionDate BETWEEN :start AND :end")
    int sumDurationByUserIdAndDateBetween(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
