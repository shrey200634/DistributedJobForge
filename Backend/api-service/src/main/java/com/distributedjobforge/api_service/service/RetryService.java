package com.distributedjobforge.api_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    public static final String RETRY_SCHEDULE_KEY = "jobs:retry_schedule";

    private final RedissonClient redissonClient;


    public boolean scheduleRetryIfPossible(String jobId, int currentAttempt, int maxRetries) {
        RAtomicLong retryCount = redissonClient.getAtomicLong("jobs:retry_count:" + jobId);

        // increment attempt counter (durable in Redis, 7d TTL per doc §4.3)
        long attempt = retryCount.incrementAndGet();
        retryCount.expire(7, TimeUnit.DAYS);

        if (attempt >= maxRetries) {
            log.warn("Job {} exhausted retries (attempt={}, maxRetries={}) -> DLQ",
                    jobId, attempt, maxRetries);
            retryCount.delete();
            return false;
        }

        // Exponential backoff with jitter
        long base = (long) (5 * Math.pow(2, attempt));
        long jitter = ThreadLocalRandom.current().nextLong(0, 3); // 0..2 inclusive
        long delaySeconds = Math.min(base + jitter, 300);

        long readyAtEpochMs = System.currentTimeMillis() + (delaySeconds * 1000);

        // Schedule the retry: ZSET scored by ready-time
        RScoredSortedSet<String> schedule = redissonClient.getScoredSortedSet(RETRY_SCHEDULE_KEY);
        schedule.add(readyAtEpochMs, jobId);

        log.info("Job {} scheduled for retry: attempt={}, delay={}s, readyAt={}",
                jobId, attempt, delaySeconds, readyAtEpochMs);
        return true;
    }

    /** Current attempt number for a job (0 if none recorded). */
    public long getAttempt(String jobId) {
        return redissonClient.getAtomicLong("jobs:retry_count:" + jobId).get();
    }
}
