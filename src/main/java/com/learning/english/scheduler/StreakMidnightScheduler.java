package com.learning.english.scheduler;

import com.learning.english.model.UserAppConfig;
import com.learning.english.model.UserSyncMetadata;
import com.learning.english.repository.UserAppConfigRepository;
import com.learning.english.repository.UserSyncMetadataRepository;
import com.learning.english.util.TimezoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class StreakMidnightScheduler {

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    /**
     * Runs every hour to reconcile user streaks across timezones.
     * If a user's lastActiveDate is strictly before yesterday in their local timezone,
     * currentStreakDays is reset to 0.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void reconcileStreaks() {
        List<UserSyncMetadata> allMetadata = metadataRepository.findAll();
        if (allMetadata.isEmpty()) {
            return;
        }

        Map<UUID, String> userTimezones = userAppConfigRepository.findAll().stream()
                .filter(c -> c.getUser() != null)
                .collect(Collectors.toMap(
                        c -> c.getUser().getId(),
                        UserAppConfig::getTimezone,
                        (a, b) -> a
                ));

        for (UserSyncMetadata metadata : allMetadata) {
            if (metadata.getCurrentStreakDays() == null || metadata.getCurrentStreakDays() <= 0) {
                continue;
            }
            UUID userId = metadata.getUser().getId();
            ZoneId zoneId = TimezoneUtil.parseZoneId(userTimezones.get(userId));
            LocalDate today = TimezoneUtil.today(zoneId);
            LocalDate yesterday = today.minusDays(1);

            LocalDate lastActive = metadata.getLastActiveDate();
            if (lastActive == null || lastActive.isBefore(yesterday)) {
                metadata.setCurrentStreakDays(0);
                metadataRepository.save(metadata);
            }
        }
    }
}
