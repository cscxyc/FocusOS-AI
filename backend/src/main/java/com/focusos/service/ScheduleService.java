package com.focusos.service;

import com.focusos.dto.request.CreateScheduleEventRequest;
import com.focusos.dto.response.ScheduleEventResponse;
import com.focusos.entity.ScheduleEvent;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.ScheduleEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleEventRepository scheduleEventRepository;

    @Transactional
    public ScheduleEventResponse createEvent(Long userId, CreateScheduleEventRequest request) {
        ScheduleEvent event = new ScheduleEvent();
        event.setUserId(userId);
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setEventDate(request.getEventDate() != null ? request.getEventDate() : LocalDate.now());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setEventType(request.getEventType());
        event.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        event.setIsCompleted(false);

        ScheduleEvent savedEvent = scheduleEventRepository.save(event);
        log.info("Schedule event created: {} for user: {}", savedEvent.getTitle(), userId);
        return ScheduleEventResponse.fromEntity(savedEvent);
    }

    public List<ScheduleEventResponse> getEvents(Long userId) {
        return scheduleEventRepository.findByUserId(userId).stream()
                .map(ScheduleEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleEventResponse> getTodayEvents(Long userId) {
        return scheduleEventRepository.findByUserIdAndEventDate(userId, LocalDate.now()).stream()
                .map(ScheduleEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleEventResponse> getUpcomingEvents(Long userId) {
        return scheduleEventRepository.findByUserIdAndEventDateBetween(
                        userId, LocalDate.now(), LocalDate.now().plusDays(7)).stream()
                .filter(e -> !e.getIsCompleted())
                .map(ScheduleEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleEventResponse updateEvent(Long userId, Long eventId, Map<String, Object> updates) {
        ScheduleEvent event = scheduleEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("日程事件", eventId));

        if (!event.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("日程事件", eventId);
        }

        if (updates.containsKey("title")) {
            event.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("description")) {
            event.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("eventDate")) {
            event.setEventDate(LocalDate.parse((String) updates.get("eventDate")));
        }
        if (updates.containsKey("startTime")) {
            event.setStartTime(java.time.LocalTime.parse((String) updates.get("startTime")));
        }
        if (updates.containsKey("endTime")) {
            event.setEndTime(java.time.LocalTime.parse((String) updates.get("endTime")));
        }
        if (updates.containsKey("eventType")) {
            event.setEventType((String) updates.get("eventType"));
        }
        if (updates.containsKey("priority")) {
            event.setPriority((String) updates.get("priority"));
        }
        if (updates.containsKey("isCompleted")) {
            event.setIsCompleted((Boolean) updates.get("isCompleted"));
        }

        ScheduleEvent savedEvent = scheduleEventRepository.save(event);
        log.info("Event updated: {}", savedEvent.getTitle());
        return ScheduleEventResponse.fromEntity(savedEvent);
    }

    @Transactional
    public ScheduleEventResponse completeEvent(Long userId, Long eventId) {
        ScheduleEvent event = scheduleEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("日程事件", eventId));

        if (!event.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("日程事件", eventId);
        }

        event.setIsCompleted(true);
        ScheduleEvent savedEvent = scheduleEventRepository.save(event);
        log.info("Event completed: {}", savedEvent.getTitle());
        return ScheduleEventResponse.fromEntity(savedEvent);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        ScheduleEvent event = scheduleEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("日程事件", eventId));

        if (!event.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("日程事件", eventId);
        }

        scheduleEventRepository.delete(event);
        log.info("Event deleted: {}", event.getTitle());
    }
}
