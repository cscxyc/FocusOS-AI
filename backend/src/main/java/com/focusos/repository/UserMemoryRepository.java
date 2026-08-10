package com.focusos.repository;

import com.focusos.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8-C: 用户长期记忆 Repository
 * <p>
 * 用户隔离规则：
 * 所有查询方法必须携带 userId 参数。
 * 禁止跨 userId 读取/删除。
 */
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    // ============================================================
    // 用户隔离查询 — 全部携带 userId
    // ============================================================

    /**
     * 按用户查询全部记忆（按更新时间倒序）
     */
    List<UserMemory> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);

    /**
     * 按用户 + 类型查询（如仅查询 SKILL 类）
     */
    List<UserMemory> findByUserIdAndMemoryTypeOrderByUpdatedAtDesc(
            @Param("userId") Long userId,
            @Param("memoryType") String memoryType);

    /**
     * 按用户 + key 查询（用于 MemoryMergeStrategy 判断是否已存在同 key 记忆，
     * 注意 memoryType 也需一致，因为同 key 可能既是 SKILL 又是 PROJECT）
     */
    Optional<UserMemory> findByUserIdAndMemoryTypeAndMemoryKey(
            @Param("userId") Long userId,
            @Param("memoryType") String memoryType,
            @Param("memoryKey") String memoryKey);

    /**
     * 按用户 + 模糊 key 匹配（用于 CareerGrowthAgent 检查 "Milvus基础" 是否已掌握
     * —— 用 LIKE "%Milvus%" 避免精确匹配大小写与前后缀差异）
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId " +
            "AND m.memoryType = 'SKILL' " +
            "AND LOWER(m.memoryKey) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<UserMemory> findSkillsByKeywordFuzzy(
            @Param("userId") Long userId,
            @Param("keyword") String keyword);

    // ============================================================
    // 高置信记忆（confidence >= 阈值）—— 用于 Agent 注入，避免低置信噪声
    // ============================================================

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.confidence >= :threshold " +
            "ORDER BY m.updatedAt DESC")
    List<UserMemory> findByUserIdAndMinConfidence(
            @Param("userId") Long userId,
            @Param("threshold") Double threshold);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType AND m.confidence >= :threshold " +
            "ORDER BY m.updatedAt DESC")
    List<UserMemory> findByUserIdAndTypeAndMinConfidence(
            @Param("userId") Long userId,
            @Param("memoryType") String memoryType,
            @Param("threshold") Double threshold);

    // ============================================================
    // 用户级删除 —— 必须带 userId，防止跨用户删数据
    // ============================================================

    @Modifying
    @Transactional
    @Query("DELETE FROM UserMemory m WHERE m.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 按 id + userId 删除（双重校验，避免 A 用户删 B 用户的记忆）
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserMemory m WHERE m.id = :id AND m.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // ============================================================
    // 单条按 id + userId 查询（详情/更新/删除前校验）
    // ============================================================

    @Query("SELECT m FROM UserMemory m WHERE m.id = :id AND m.userId = :userId")
    Optional<UserMemory> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
