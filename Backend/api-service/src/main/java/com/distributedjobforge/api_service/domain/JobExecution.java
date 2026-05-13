package com.distributedjobforge.api_service.domain;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor

@Table(name = "job_execution")

public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id ;

    private int attempt ;

    @Column(name = "worker_id")
    private String workerId ;

    @Column(name = "started_at")
    private Instant startedAt ;

    @Column(name = "end_at")
    private Instant endAt ;

    @Column(name = "exit_code")
    private Integer exitCode ;

    @Column(columnDefinition = "TEXT")
    private String stdout ;

    @Column (columnDefinition = "TEXT")
    private String stderr;

    @Column (name = "duration_ms ")
    private Long durationMs ;

    @Enumerated(EnumType.STRING)
    private JobStatus status ;

}
