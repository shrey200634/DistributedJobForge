package com.distributedjobforge.worker_service.executor.javatask;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sleeps for a given number of seconds, then returns.
 * Payload args: { "seconds": <int> }   e.g. {"seconds": 2}
 *
 * Deliberately slow — used to prove the JAVA_CLASS timeout path works
 * (submit a sleep longer than the job's timeoutS).
 */
@Component
public class SleepTask implements JavaTask {

    @Override
    public String name() {
        return "sleep";
    }

    @Override
    public String run(Map<String, Object> args) throws InterruptedException {
        int seconds = ((Number) args.getOrDefault("seconds", 1)).intValue();
        Thread.sleep(seconds * 1000L);
        return "slept for " + seconds + "s";
    }
}
