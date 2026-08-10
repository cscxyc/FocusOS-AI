package com.focusos.repository;

import com.focusos.entity.UserQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Sprint 8-E: 用户配额 Repository (Task 8)
 */
@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, Long> {

    /** 按用户 ID 查询配额 */
    Optional<UserQuota> findByUserId(Long userId);

    /**
     * Sprint 8-E: 原子累加用户 Token 使用量（避免并发竞争）。
     * <p>
     * 不存在或受影响行数为 0 时由调用方按需创建配额记录。
     *
     * @param userId 用户 ID
     * @param tokens 本次调用消耗的 Token 数
     * @return 受影响行数（0 表示配额记录不存在，需先创建）
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserQuota q SET q.usedTokens = q.usedTokens + :tokens WHERE q.userId = :userId")
    int incrementUsedTokens(@Param("userId") Long userId, @Param("tokens") long tokens);

    /**
     * Sprint 8-E: 重置用户当日已使用 Token 数（跨日重置）。
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserQuota q SET q.usedTokens = 0, q.resetDate = :today WHERE q.userId = :userId")
    int resetDailyQuota(@Param("userId") Long userId, @Param("today") java.time.LocalDate today);
}
