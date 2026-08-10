package com.focusos.service;

import com.focusos.config.JwtTokenProvider;
import com.focusos.dto.request.LoginRequest;
import com.focusos.dto.request.RegisterRequest;
import com.focusos.dto.response.UserResponse;
import com.focusos.entity.User;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.exception.UnauthorizedException;
import com.focusos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已被注册");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);

        User savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getUsername());

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", UserResponse.fromEntity(savedUser));
        return result;
    }

    public Map<String, Object> login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseGet(() -> userRepository.findByEmail(request.getUsername())
                        .orElseThrow(() -> new UnauthorizedException("用户名或密码错误")));

        if (!user.getIsActive()) {
            throw new BusinessException("账号已被禁用", 403);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("User logged in: {}", user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", UserResponse.fromEntity(user));
        return result;
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("无效的刷新令牌");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("无效的刷新令牌");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("无效的刷新令牌"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", UserResponse.fromEntity(user));
        return result;
    }

    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UserResponse updateData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));

        if (updateData.getUsername() != null && !updateData.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(updateData.getUsername())) {
                throw new BusinessException("用户名已被使用");
            }
            user.setUsername(updateData.getUsername());
        }

        if (updateData.getEmail() != null && !updateData.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateData.getEmail())) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(updateData.getEmail());
        }

        if (updateData.getAvatar() != null) {
            user.setAvatar(updateData.getAvatar());
        }

        User savedUser = userRepository.save(user);
        log.info("User profile updated: {}", savedUser.getUsername());
        return UserResponse.fromEntity(savedUser);
    }
}
