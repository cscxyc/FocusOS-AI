package com.focusos.repository;

import com.focusos.entity.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 7-C-A: Resume Version Repository
 */
@Repository
public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

    /** 查询用户全部简历版本（最新优先） */
    List<ResumeVersion> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 按目标岗位查询（用于版本对比） */
    List<ResumeVersion> findByUserIdAndTargetPositionOrderByCreatedAtDesc(Long userId, String targetPosition);

    /** 查询用户的当前激活版本 */
    Optional<ResumeVersion> findByUserIdAndIsActiveTrue(Long userId);

    /** 按来源 reportId 查询（避免重复创建） */
    Optional<ResumeVersion> findByUserIdAndSourceReportId(Long userId, Long sourceReportId);

    /** 将用户的所有版本设为非激活（用于切换激活版本） */
    @Modifying
    @Query("UPDATE ResumeVersion r SET r.isActive = false WHERE r.userId = :userId")
    void deactivateAllByUserId(@Param("userId") Long userId);
}
