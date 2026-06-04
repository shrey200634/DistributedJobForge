package com.distributedjobforge.worker_service.registration;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerRegistrationService {

    private final RedissonClient redissonClient ;

    @Getter
    private String workerId ;

    @PostConstruct
    public  void  register (){
        workerId = computeWorkerId();

        String metadata = String.format(
                "{\"workerId\":\"%s\",\"hostname\":\"%s\",\"registeredAt\":\"%s\"}",
                workerId , hostname() , Instant.now());

        redissonClient.getBucket("workers:" + workerId)
                .set(metadata , 30 , TimeUnit.SECONDS);
        redissonClient.getSet("workers:active").add(workerId );
        log.info("Worker registered: id={}", workerId);

    }
    public  void refreshHeartbeat(){
        redissonClient.getBucket("workers:" + workerId)
                .expire(30 , TimeUnit.SECONDS);
    }
    // call the consumer before calling the job
    public  void markInProgress (UUID jobId ){
        redissonClient.<String>getSet("jobs:in-progress:" + workerId)
                .add(jobId.toString());
    }
    //call the Consumer after publishing the result
    public  void  markDone ( UUID jobId ){
        redissonClient.<String>getSet("jobs:in-progress:" + workerId)
                .remove(jobId.toString());
    }

    @PreDestroy
    public  void deregister(){
        redissonClient.getSet("workers:active").remove(workerId);
        redissonClient.getBucket("workers:" + workerId).delete();
        log.info("Worker deregistered: id={}", workerId);
    }

    private  String computeWorkerId(){
        try {
            String raw = hostname() + ManagementFactory.getRuntimeMXBean().getName();
            MessageDigest digest= MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.substring(0, 16);
        } catch (Exception e) {
            return hostname().substring(0,Math.min(16, hostname().length()));

        }
    }


    private  String hostname(){
        try {
            return InetAddress.getLocalHost().getHostName();
        }catch (Exception e ){
            return System.getenv().getOrDefault("HOSTNAME","unknown-worker");
        }
    }
}
