package com.focusos.repository;

import com.focusos.entity.ScheduleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, Long> {

    List<ScheduleEvent> findByUserId(Long userId);

    List<ScheduleEvent> findByUserIdAndEventDate(Long userId, LocalDate eventDate);

    List<ScheduleEvent> findByUserIdAndEventDateBetween(Long userId, LocalDate start, LocalDate end);

    List<ScheduleEvent> findByUserIdAndIsCompleted(Long userId, Boolean isCompleted);

    long countByUserIdAndIsCompleted(Long userId, Boolean isCompleted);
}
