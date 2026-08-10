package com.focusos.repository;

import com.focusos.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByUserId(Long userId);

    List<KnowledgeDocument> findByUserIdAndCategory(Long userId, String category);

    List<KnowledgeDocument> findByUserIdAndIsVectorized(Long userId, Boolean isVectorized);
}
