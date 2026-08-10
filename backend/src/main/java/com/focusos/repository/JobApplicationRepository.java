package com.focusos.repository;

import com.focusos.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByUserId(Long userId);

    List<JobApplication> findByUserIdAndStatus(Long userId, String status);

    long countByUserIdAndStatus(Long userId, String status);
}
