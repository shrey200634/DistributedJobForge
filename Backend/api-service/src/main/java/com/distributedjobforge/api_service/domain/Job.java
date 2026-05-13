package com.distributedjobforge.api_service.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_priority", columnList = "priority DESC, created_at ASC"),
        @Index(name = "idx_jobs_worker", columnList = "worker_id")
})
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id ;

    @Column(name = "idempotency_key" , unique = true , nullable = false)
    private  String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;


    @Column(nullable = false)
    @Builder.Default
    private int priority =5 ;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, Object> payload;

    @ElementCollection
    @CollectionTable(name = "job_dependencies",
            joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "depends_on_job_id")
    @Builder.Default
    private List<UUID> dependsOn = new ArrayList<>();

    @Builder.Default
    private int maxRetries = 5;

    @Builder.Default
    private int timeoutS = 300;

    @Builder.Default
    private int retryCount = 0;

    @Column(name = "worker_id")
    private String workerId;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> result;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
    }

}
