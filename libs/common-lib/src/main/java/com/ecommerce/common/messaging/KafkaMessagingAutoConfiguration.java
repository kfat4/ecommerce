package com.ecommerce.common.messaging;

import com.ecommerce.common.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka conventions shared by every service:
 * <ul>
 *   <li>Topics are created up-front with several partitions so consumers can scale horizontally.</li>
 *   <li>Failed records are retried with exponential back-off, then parked on {@code <topic>.DLT}.</li>
 *   <li>Poison messages ({@link NonRetryableMessageException}) skip the retries.</li>
 * </ul>
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaMessagingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    MessageReader messageReader(ObjectMapper objectMapper) {
        return new MessageReader(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CorrelationIdRecordInterceptor correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, -1));
        var backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);
        var handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Message exhausted retries, sending to DLT. topic={} key={} error={}",
                    record.topic(), record.key(), ex.getMessage());
            recoverer.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(NonRetryableMessageException.class);
        return handler;
    }

    @Bean
    KafkaAdmin.NewTopics ecommerceTopics(@Value("${ecommerce.kafka.partitions:3}") int partitions,
                                        @Value("${ecommerce.kafka.replicas:1}") int replicas) {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : Topics.ALL) {
            TopicBuilder builder = TopicBuilder.name(topic).partitions(partitions).replicas(replicas);
            if (Topics.COMPACTED.contains(topic)) {
                builder.compact();
            }
            topics.add(builder.build());
            topics.add(TopicBuilder.name(topic + Topics.DLT_SUFFIX).partitions(1).replicas(replicas).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
