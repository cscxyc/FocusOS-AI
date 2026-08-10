package com.focusos.repository;

import com.focusos.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {

    List<AgentTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AgentTask> findByUserIdAndWorkflowIdOrderByCreatedAtAsc(Long userId, String workflowId);

    List<AgentTask> findByUserIdAndStatus(Long userId, String status);

    List<AgentTask> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
