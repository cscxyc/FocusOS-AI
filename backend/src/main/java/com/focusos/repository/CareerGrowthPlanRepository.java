package com.focusos.repository;

import com.focusos.entity.CareerGrowthPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Sprint 8-B: Career Growth Plan Repository
 */
@Repository
public interface CareerGrowthPlanRepository extends JpaRepository<CareerGrowthPlan, Long> {

    /** 查询用户全部成长规划（最新优先） */
    List<CareerGrowthPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查询指定简历版本的成长规划历史 */
    List<CareerGrowthPlan> findByUserIdAndResumeVersionIdOrderByCreatedAtDesc(Long userId, Long resumeVersionId);
}
