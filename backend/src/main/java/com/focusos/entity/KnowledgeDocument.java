package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "knowledge_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 50)
    private String fileType;

    @Column(length = 500)
    private String filePath;

    @Column
    private Long fileSize;

    @Column(length = 100)
    private String category;

    /** 文档类型：resume/internship/project/notes/goal/jd/other */
    @Column(length = 50)
    private String documentType;

    /** 优先级：1-5，数字越大越重要 */
    @Column
    private Integer priority = 3;

    /** 来源：upload/manual_import/system_generated */
    @Column(length = 50)
    private String source = "upload";

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column
    private Boolean isVectorized = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
