package com.distributedjobforge.scheduler_service.scheduler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class LeaderElectionService {

    private final RedissonClient redissonClient;

    @Getter
    private volatile boolean leader = false;

    public LeaderElectionService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Scheduled(fixedDelay = 10_000)
    public void tryBecomeLeader() {
        RLock lock = redissonClient.getLock("scheduler:leader");
        try {
            boolean acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (acquired && !leader) {
                leader = true;
                log.info("This instance is now the LEADER");
            } else if (!acquired && leader) {
                leader = false;
                log.info("Lost leadership — now STANDBY");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            leader = false;
            log.warn("Leader election interrupted");
        }
    }
}