package com.distributedjobforge.worker_service.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartBeatScheduler {
    private  final WorkerRegistrationService workerRegistrationService ;

    @Scheduled(fixedDelay = 10_000)
    public  void beat (){
        workerRegistrationService.refreshHeartbeat();
        log.debug("Heartbeat refreshed for worker : {}" , workerRegistrationService.getWorkerId());
    }
}
