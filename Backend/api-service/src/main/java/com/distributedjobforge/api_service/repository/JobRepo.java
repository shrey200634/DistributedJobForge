package com.distributedjobforge.api_service.repository;

import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;


import java.util.UUID;


@Repository
public interface  JobRepo   extends JpaRepository<Job , UUID> {

    List<Job> findByStatus(JobStatus status);

    Page<Job> findByStatus (JobStatus status , Pageable pageable);

    List<Job> findByWorkerId(String workerId );

    @Query("SELECT j FROM Job j JOIN j.dependsOn dep WHERE dep = :parentId")
    List<Job> findChildrenOf(@Param("parentId") UUID parentId);

    @Query("SELECT COUNT(p) FROM Job p WHERE p.id IN :parentIds AND p.status <> :succeeded")
    long countUnfinishedParents(@Param("parentIds") List<UUID> parentIds,
                                @Param("succeeded") JobStatus succeeded);


}
