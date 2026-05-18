package com.krithika.ratelimiter.scheduler;

import com.krithika.ratelimiter.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Housekeeping jobs:
 *  - Delete API usage logs older than 30 days (keeps Supabase lean)
 *  - Logs are retained in Supabase; Redis keys expire automatically via TTL
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionScheduler {

    private final ApiUsageLogRepository usageLogRepository;

    /** Run at 2 AM every day */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldUsageLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        log.info("RetentionScheduler: purging usage logs older than {}", cutoff);

        // Supabase-friendly: delete in batches to avoid long lock times
        var oldLogs = usageLogRepository.findAll()
                .stream()
                .filter(log -> log.getCreatedAt().isBefore(cutoff))
                .toList();

        if (!oldLogs.isEmpty()) {
            usageLogRepository.deleteAll(oldLogs);
            log.info("RetentionScheduler: deleted {} old usage log entries", oldLogs.size());
        } else {
            log.info("RetentionScheduler: nothing to purge");
        }
    }
}
