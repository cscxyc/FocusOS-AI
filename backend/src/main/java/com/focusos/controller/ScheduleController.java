package com.focusos.controller;

import com.focusos.dto.request.CreateScheduleEventRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.ScheduleEventResponse;
import com.focusos.entity.User;
import com.focusos.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/events")
    public ApiResponse<ScheduleEventResponse> createEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateScheduleEventRequest request) {
        return ApiResponse.success("日程创建成功", scheduleService.createEvent(user.getId(), request));
    }

    @GetMapping("/events")
    public ApiResponse<List<ScheduleEventResponse>> getEvents(@AuthenticationPrincipal User user) {
        return ApiResponse.success(scheduleService.getEvents(user.getId()));
    }

    @GetMapping("/events/today")
    public ApiResponse<List<ScheduleEventResponse>> getTodayEvents(@AuthenticationPrincipal User user) {
        return ApiResponse.success(scheduleService.getTodayEvents(user.getId()));
    }

    @GetMapping("/events/upcoming")
    public ApiResponse<List<ScheduleEventResponse>> getUpcomingEvents(@AuthenticationPrincipal User user) {
        return ApiResponse.success(scheduleService.getUpcomingEvents(user.getId()));
    }

    @PutMapping("/events/{eventId}")
    public ApiResponse<ScheduleEventResponse> updateEvent(
            @AuthenticationPrincipal User user,
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> updates) {
        return ApiResponse.success("事件更新成功", scheduleService.updateEvent(user.getId(), eventId, updates));
    }

    @PutMapping("/events/{eventId}/complete")
    public ApiResponse<ScheduleEventResponse> completeEvent(
            @AuthenticationPrincipal User user,
            @PathVariable Long eventId) {
        return ApiResponse.success("事件已完成", scheduleService.completeEvent(user.getId(), eventId));
    }

    @DeleteMapping("/events/{eventId}")
    public ApiResponse<Void> deleteEvent(
            @AuthenticationPrincipal User user,
            @PathVariable Long eventId) {
        scheduleService.deleteEvent(user.getId(), eventId);
        return ApiResponse.success("事件删除成功", null);
    }
}
