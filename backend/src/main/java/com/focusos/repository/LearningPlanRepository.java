package com.focusos.repository;

import com.focusos.entity.LearningPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {

    List<LearningPlan> findByUserId(Long userId);

    List<LearningPlan> findByUserIdAndStatus(Long userId, String status);

    long countByUserIdAndStatus(Long userId, String status);
}
