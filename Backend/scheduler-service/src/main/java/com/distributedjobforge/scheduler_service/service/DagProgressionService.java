package com.distributedjobforge.scheduler_service.service;


import com.distributedjobforge.scheduler_service.domain.Job;
import com.distributedjobforge.scheduler_service.domain.JobStatus;
import com.distributedjobforge.scheduler_service.kafka.JobEventPublisher;
import com.distributedjobforge.scheduler_service.repository.JobRepo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DagProgressionService {
    private final JobRepo jobRepo;
    private  final JobEventPublisher jobEventPublisher ;
    private   final MeterRegistry registry ;

    @Transactional
    public  void onJobSucceeded(UUID completedJobId ){
        List<Job> children = jobRepo.findChildrenOf(completedJobId);
        if (children.isEmpty()){
            log.debug("Job {} has no downstream dependents", completedJobId);
            return ;
        }
        log.info("Job {} succeeded — checking {} downstream job(s)",
                completedJobId , children.size());

        for (Job child : children){
            if ( child.getStatus()!= JobStatus.BLOCKED){
                continue;
            }
            long stillWaiting = jobRepo.countUnfinishedParents(
                    child.getDependsOn(),JobStatus.SUCCEEDED
            );
            if (stillWaiting ==0 ){
                child.setStatus(JobStatus.PENDING);
                jobRepo.save(child);
                jobEventPublisher.publishJobPending(child);
                registry.counter("djf.dag.unblocked").increment();

                log.info("Unblocked job {} — all parents done, published to job.pending",
                        child.getId());

            }
            else {
                log.debug("job {} still missing on {} parent(s)",
                        child.getId() , stillWaiting);
            }
        }

    }
}
