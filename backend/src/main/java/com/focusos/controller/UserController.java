package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.UserResponse;
import com.focusos.entity.User;
import com.focusos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        return ApiResponse.success(userService.getProfile(user.getId()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UserResponse updateData) {
        return ApiResponse.success("更新成功", userService.updateProfile(user.getId(), updateData));
    }
}
