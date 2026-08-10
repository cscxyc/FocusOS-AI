package com.focusos.repository;

import com.focusos.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    List<UserSettings> findByUserId(Long userId);

    Optional<UserSettings> findByUserIdAndSettingKey(Long userId, String settingKey);

    void deleteByUserIdAndSettingKey(Long userId, String settingKey);
}
