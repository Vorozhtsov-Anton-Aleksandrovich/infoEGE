package com.github.anton.infoege.attempt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Закрывает попытки, у которых вышло время, даже если ученик ушёл со страницы.
 * Дополнительно каждый запрос к попытке проверяет истечение сам (AttemptService).
 */
@Component
public class AttemptExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryScheduler.class);

    private final AttemptService attemptService;

    public AttemptExpiryScheduler(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @Scheduled(fixedDelayString = "${app.attempt-expiry-check-ms:30000}")
    public void expireOverdue() {
        int expired = attemptService.expireOverdue();
        if (expired > 0) {
            log.info("Expired {} overdue attempt(s)", expired);
        }
    }
}
