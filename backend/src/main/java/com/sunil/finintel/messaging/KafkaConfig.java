package com.sunil.finintel.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(Topics.ORDERS_CREATED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic ordersCreatedDltTopic() {
        return TopicBuilder.name(Topics.ORDERS_CREATED_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic ordersValidatedTopic() {
        return TopicBuilder.name(Topics.ORDERS_VALIDATED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic ordersRejectedTopic() {
        return TopicBuilder.name(Topics.ORDERS_REJECTED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic ordersValidatedDltTopic() {
        return TopicBuilder.name(Topics.ORDERS_VALIDATED_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic ordersExecutedTopic() {
        return TopicBuilder.name(Topics.ORDERS_EXECUTED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic portfolioUpdatedTopic() {
        return TopicBuilder.name(Topics.PORTFOLIO_UPDATED).partitions(PARTITIONS).replicas(1).build();
    }

    // Failed record: retried 2 more times, 1 second apart (3 attempts total), then sent to "<topic>.DLT".
    // Malformed messages can never succeed, so they skip the retries.
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                // partition -1: let Kafka choose the DLT partition
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }
}
