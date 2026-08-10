package com.focusos.service;

import com.focusos.agent.MemoryAgent;
import com.focusos.agent.UserMemoryContext;
import com.focusos.entity.UserMemory;
import com.focusos.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Sprint 8-C: 用户长期记忆服务
 * <p>
 * 职责：
 * 1. 记忆 CRUD（严格用户隔离：所有查询/删除/更新都携带 userId 双重校验）
 * 2. 记忆合并（同 userId + type + key 的记忆走 MemoryMergeStrategy，避免重复）
 * 3. 记忆提取（调用 MemoryAgent 从用户行为事件中抽取结构化记忆并持久化）
 * 4. 构建 Agent 上下文（UserMemoryContext，供 WorkflowContext 注入到 CareerGrowth/Interview/Evaluator）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalMemoryService {

    public static final double DEFAULT_AGENT_MIN_CONFIDENCE = 0.5;

    private final UserMemoryRepository memoryRepository;
    private final MemoryMergeStrategy mergeStrategy;
    private final MemoryAgent memoryAgent;

    // ============================================================
    // 写入路径：save / saveAll / extractAndPersist
    // ============================================================

    /**
     * 保存单条记忆：
     * - 若已存在同 userId + type + key 的记忆，则执行合并（更新实体）
     * - 否则新增
     *
     * @return 保存/合并后的实体（含 id）
     */
    @Transactional
    public UserMemory saveMemory(Long userId, UserMemory input) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");
        if (input == null) throw new IllegalArgumentException("记忆不能为空");
        if (input.getMemoryKey() == null || input.getMemoryKey().isBlank()) {
            throw new IllegalArgumentException("memoryKey 不能为空");
        }
        if (input.getMemoryValue() == null || input.getMemoryValue().isBlank()) {
            throw new IllegalArgumentException("memoryValue 不能为空");
        }

        // 归一化：类型大写，key trim
        input.setUserId(userId);
        if (input.getMemoryType() == null) input.setMemoryType("SKILL");
        input.setMemoryType(input.getMemoryType().toUpperCase());
        input.setMemoryKey(input.getMemoryKey().trim());

        Optional<UserMemory> existing = memoryRepository.findByUserIdAndMemoryTypeAndMemoryKey(
                userId, input.getMemoryType(), input.getMemoryKey());

        UserMemory toSave;
        if (existing.isPresent() && mergeStrategy.canMerge(existing.get(), input)) {
            toSave = mergeStrategy.merge(existing.get(), input);
            log.debug("Memory merge userId={} type={} key={}, new confidence={}",
                    userId, toSave.getMemoryType(), toSave.getMemoryKey(), toSave.getConfidence());
        } else if (existing.isPresent()) {
            // 存在但不能合并（极少见，因为同 type+key）—— 仍合并
            toSave = mergeStrategy.merge(existing.get(), input);
        } else {
            toSave = input;
            log.debug("Memory create userId={} type={} key={}, confidence={}",
                    userId, toSave.getMemoryType(), toSave.getMemoryKey(), toSave.getConfidence());
        }

        return memoryRepository.save(toSave);
    }

    /**
     * Task9 Controller 调用：简单参数化保存（6 参数）
     */
    @Transactional
    public UserMemory saveMemory(Long userId,
                                 String memoryType,
                                 String memoryKey,
                                 String memoryValue,
                                 String source,
                                 Double confidence) {
        UserMemory m = new UserMemory();
        m.setUserId(userId);
        m.setMemoryType(memoryType);
        m.setMemoryKey(memoryKey);
        m.setMemoryValue(memoryValue);
        m.setSource(source);
        m.setConfidence(confidence == null ? 0.8 : confidence);
        return saveMemory(userId, m);
    }

    /**
     * 批量保存记忆（逐条 merge 处理）
     *
     * @return 已保存的记忆列表（含 id）
     */
    @Transactional
    public List<UserMemory> saveAll(Long userId, List<UserMemory> list) {
        List<UserMemory> saved = new ArrayList<>();
        if (list == null) return saved;
        int skipped = 0;
        for (UserMemory m : list) {
            try {
                saved.add(saveMemory(userId, m));
            } catch (IllegalArgumentException e) {
                skipped++;
                log.debug("Memory save skipped userId={} reason={}", userId, e.getMessage());
            }
        }
        if (skipped > 0) {
            log.info("PersonalMemoryService.saveAll userId={} total={} saved={} skipped={}",
                    userId, list.size(), saved.size(), skipped);
        }
        return saved;
    }

    /**
     * 手动触发记忆提取 + 持久化（3 参数）
     */
    @Transactional
    public List<UserMemory> extractAndPersist(Long userId, String eventType, String content) {
        return extractAndSave(userId, eventType, content, null);
    }

    /**
     * Task9 Controller 调用：手动触发记忆提取 + 持久化（4 参数，带 source）
     *
     * @return 提取后已持久化的记忆列表
     */
    @Transactional
    public List<UserMemory> extractAndSave(Long userId, String eventType, String content, String source) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");
        // 1. 调用 MemoryAgent 提取
        List<UserMemory> extracted = memoryAgent.extractMemories(eventType, content, userId);
        if (extracted == null || extracted.isEmpty()) {
            log.info("MemoryAgent extracted 0 memories for userId={} eventType={}", userId, eventType);
            return new ArrayList<>();
        }
        // 1.5 如果提供 source，覆盖 MemoryAgent 返回的每条记忆的 source
        if (source != null && !source.isBlank()) {
            for (UserMemory m : extracted) {
                if (m.getSource() == null || m.getSource().isBlank()) {
                    m.setSource(source);
                }
            }
        }
        // 2. 合并 + 持久化
        return saveAll(userId, extracted);
    }

    // ============================================================
    // 读取路径：retrieve all / by type / by id + context
    // ============================================================

    public List<UserMemory> retrieveAll(Long userId) {
        if (userId == null) return new ArrayList<>();
        return memoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<UserMemory> retrieveByType(Long userId, String memoryType) {
        if (userId == null) return new ArrayList<>();
        if (memoryType == null || memoryType.isBlank()) return retrieveAll(userId);
        return memoryRepository.findByUserIdAndMemoryTypeOrderByUpdatedAtDesc(userId, memoryType.toUpperCase());
    }

    /**
     * Task9 Controller 调用：按最小 confidence 过滤
     */
    public List<UserMemory> retrieveByMinConfidence(Long userId, double minConfidence) {
        if (userId == null) return new ArrayList<>();
        List<UserMemory> all = memoryRepository.findByUserIdAndMinConfidence(userId, minConfidence);
        return all != null ? all : new ArrayList<>();
    }

    /**
     * 按 id 读取（带 userId 校验，避免越权）
     */
    public Optional<UserMemory> retrieveById(Long userId, Long id) {
        if (userId == null || id == null) return Optional.empty();
        return memoryRepository.findByIdAndUserId(id, userId);
    }

    /**
     * 用于 Agent 注入：仅取高置信（≥minConfidence）记忆，构建结构化 UserMemoryContext。
     * 供 WorkflowContext.renderAsPromptContext() 注入到所有 Agent prompt。
     */
    public UserMemoryContext buildAgentContext(Long userId) {
        return buildAgentContext(userId, DEFAULT_AGENT_MIN_CONFIDENCE);
    }

    public UserMemoryContext buildAgentContext(Long userId, double minConfidence) {
        if (userId == null) {
            return UserMemoryContext.empty("userId 为空");
        }
        try {
            List<UserMemory> memories = memoryRepository.findByUserIdAndMinConfidence(userId, minConfidence);
            return UserMemoryContext.fromMemories(userId, memories, minConfidence);
        } catch (Exception e) {
            log.error("buildAgentContext failed userId={}", userId, e);
            return UserMemoryContext.empty("构建失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 删除：单条 / 清空（都带 userId 双重校验，禁止越权）
    // ============================================================

    /**
     * 单条删除（双重校验：id 必须属于该 userId）
     *
     * @return true 删除成功；false 记忆不存在或不属于该用户
     */
    @Transactional
    public boolean deleteMemory(Long userId, Long id) {
        if (userId == null || id == null) return false;
        int affected = memoryRepository.deleteByIdAndUserId(id, userId);
        boolean ok = affected > 0;
        log.info("deleteMemory userId={} id={} affected={}", userId, id, affected);
        return ok;
    }

    /**
     * 清空用户所有记忆（高破坏性：仅用户自己的「重置成长档案」按钮应调用此方法）
     *
     * @return 删除条数
     */
    @Transactional
    public int clearAllMemories(Long userId) {
        if (userId == null) return 0;
        int n = memoryRepository.deleteByUserId(userId);
        log.warn("clearAllMemories userId={} deletedRows={}", userId, n);
        return n;
    }

    // ============================================================
    // 统计：用于 Dashboard 展示技能数 / 项目数 / 学习进度数
    // ============================================================

    public Map<String, Integer> countStats(Long userId) {
        Map<String, Integer> stats = new HashMap<>();
        Set<String> allTypes = Set.of("SKILL","PROJECT","EXPERIENCE","GOAL","LEARNING_PROGRESS","PREFERENCE","ACHIEVEMENT");
        List<UserMemory> all = memoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        stats.put("TOTAL", all.size());
        for (String t : allTypes) stats.put(t, 0);
        for (UserMemory m : all) {
            String t = m.getMemoryType();
            if (t != null && allTypes.contains(t)) {
                stats.put(t, stats.get(t) + 1);
            }
        }
        return stats;
    }
}
