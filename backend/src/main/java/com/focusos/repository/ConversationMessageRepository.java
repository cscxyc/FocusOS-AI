package com.focusos.repository;

import com.focusos.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<ConversationMessage> findByUserIdAndAgentTypeOrderByCreatedAtAsc(Long userId, String agentType);

    void deleteByUserId(Long userId);
}
