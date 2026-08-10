package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "learning_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LearningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column
    private Long planId;

    @Column(length = 100)
    private String subject;

    @Column
    private Integer durationMinutes;

    @Column
    private LocalDate sessionDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private Integer focusLevel;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
