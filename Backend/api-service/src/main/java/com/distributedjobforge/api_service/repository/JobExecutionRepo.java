package com.distributedjobforge.api_service.repository;

import com.distributedjobforge.api_service.domain.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.*;



@Repository
public interface  JobExecutionRepo extends JpaRepository<JobExecution , UUID> {

    List<JobExecution>  findByJobIdOrderByAttemptAsc(UUID jobId );

}
