package com.distributedjobforge.api_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic jobPendingTopic (){
        return TopicBuilder.name("job.pending")
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public  NewTopic jobResultTopic(){
        return TopicBuilder.name("job.result")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public  NewTopic jobDlqTopic(){
        return TopicBuilder.name("job.dlq")
                .partitions(3)
                .replicas(1)
                .build();

    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return TopicBuilder.name("job.completed")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
