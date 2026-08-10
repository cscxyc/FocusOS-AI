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
@Table(name = "job_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 200)
    private String company;

    @Column(length = 200)
    private String position;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column(length = 30)
    private String status = "PENDING";

    @Column
    private Double matchScore;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private LocalDate appliedDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
