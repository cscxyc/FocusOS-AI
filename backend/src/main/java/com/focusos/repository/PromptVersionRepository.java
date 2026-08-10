package com.focusos.repository;

import com.focusos.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8-D Task6: Prompt 版本管理 Repository
 * <p>
 * Prompt 版本为全局共享（非用户隔离），因为 Prompt 模板对所有用户通用。
 * 仅管理员可通过 API 管理。
 */
@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    /** 按 agentType 查询所有版本 */
    List<PromptVersion> findByAgentTypeOrderByCreatedAtDesc(@Param("agentType") String agentType);

    /** 查询当前启用的版本 */
    @Query("SELECT p FROM PromptVersion p WHERE p.agentType = :agentType AND p.enabled = true")
    Optional<PromptVersion> findEnabledByAgentType(@Param("agentType") String agentType);

    /** 禁用指定 agentType 的所有版本（切换版本前调用） */
    @Modifying
    @Transactional
    @Query("UPDATE PromptVersion p SET p.enabled = false WHERE p.agentType = :agentType")
    int disableAllByAgentType(@Param("agentType") String agentType);

    /** 按 agentType + version 精确查询 */
    Optional<PromptVersion> findByAgentTypeAndVersion(@Param("agentType") String agentType, @Param("version") String version);
}
