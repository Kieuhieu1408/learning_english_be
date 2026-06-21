package com.learning.english.service;

import com.learning.english.dto.AiSettingsDto;
import com.learning.english.model.User;
import com.learning.english.model.UserAiSettings;
import com.learning.english.repository.UserAiSettingsRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AiSettingsService {

    @Autowired
    private UserAiSettingsRepository userAiSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Value("${app.encryption.secret-key}")
    private String secretKey;

    public AiSettingsDto getSettings(UUID userId) {
        return userAiSettingsRepository.findByUserId(userId)
                .map(settings -> new AiSettingsDto(
                        settings.getProvider(),
                        encryptionUtil.decrypt(settings.getEncryptedApiKey(), secretKey),
                        settings.getTranslationModel(),
                        settings.getQuizModel()
                ))
                .orElse(null);
    }

    @Transactional
    public AiSettingsDto saveSettings(UUID userId, AiSettingsDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserAiSettings settings = userAiSettingsRepository.findByUserId(userId)
                .orElseGet(() -> UserAiSettings.builder()
                        .user(user)
                        .build());

        settings.setProvider(dto.provider());
        settings.setEncryptedApiKey(encryptionUtil.encrypt(dto.apiKey(), secretKey));
        settings.setTranslationModel(dto.translationModel());
        settings.setQuizModel(dto.quizModel());

        userAiSettingsRepository.save(settings);

        return new AiSettingsDto(
                settings.getProvider(),
                dto.apiKey(),
                settings.getTranslationModel(),
                settings.getQuizModel()
        );
    }
}
