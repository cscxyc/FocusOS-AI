-- FocusOS AI Initial Data

-- Default admin user (password: admin123)
INSERT INTO users (username, email, password_hash, is_active) VALUES
    ('admin', 'admin@focusos.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1)
ON DUPLICATE KEY UPDATE username = username;

-- Sample user settings
INSERT INTO user_settings (user_id, setting_key, setting_value) VALUES
    (1, 'language', 'zh-CN'),
    (1, 'theme', 'light'),
    (1, 'notifications_enabled', 'true')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- Sample learning plan
INSERT INTO learning_plans (user_id, title, goal, start_date, end_date, daily_target_minutes, status) VALUES
    (1, 'Spring Boot Mastery', 'Master Spring Boot 3.x and build production-ready applications',
     '2024-01-01', '2024-03-31', 120, 'ACTIVE')
ON DUPLICATE KEY UPDATE title = title;

-- Sample learning sessions
INSERT INTO learning_sessions (user_id, plan_id, subject, duration_minutes, session_date, focus_level, notes) VALUES
    (1, 1, 'Spring Security', 90, CURDATE(), 5, 'Completed JWT authentication implementation'),
    (1, 1, 'Spring Data JPA', 60, CURDATE(), 4, 'Reviewed entity relationships and repositories')
ON DUPLICATE KEY UPDATE subject = subject;

-- Sample schedule events
INSERT INTO schedule_events (user_id, title, description, event_date, start_time, end_time, event_type, priority) VALUES
    (1, 'Morning Study', 'Deep focus learning session', CURDATE(), '09:00:00', '11:00:00', 'STUDY', 'HIGH'),
    (1, 'Career Planning', 'Review career goals', CURDATE(), '14:00:00', '15:00:00', 'CAREER', 'MEDIUM'),
    (1, 'Daily Review', 'Review today progress', CURDATE(), '21:00:00', '21:30:00', 'REVIEW', 'MEDIUM')
ON DUPLICATE KEY UPDATE title = title;

-- Sample career profile
INSERT INTO career_profiles (user_id, title, summary, skills, experience, education) VALUES
    (1, 'Full-Stack Developer',
     'Passionate full-stack developer with 5+ years of experience in building scalable web applications.',
     '["Java", "Spring Boot", "React", "TypeScript", "MySQL", "Redis", "Docker", "Kubernetes"]',
     '[{"company": "Tech Corp", "role": "Senior Developer", "period": "2021-Present"}]',
     '[{"school": "State University", "degree": "B.S. Computer Science", "period": "2015-2019"}]')
ON DUPLICATE KEY UPDATE title = title;

-- Sample conversation messages
INSERT INTO conversation_messages (user_id, agent_type, role, content, tokens_used) VALUES
    (1, 'LEARNING', 'user', 'Help me create a Spring Boot learning plan', 15),
    (1, 'LEARNING', 'assistant', 'I will create a comprehensive Spring Boot learning plan for you...', 150),
    (1, 'CAREER', 'user', 'Analyze this job description for me', 20),
    (1, 'CAREER', 'assistant', 'Based on the job description analysis...', 200)
ON DUPLICATE KEY UPDATE content = content;
