package com.ecommerce.common.outbox;

import com.ecommerce.common.observability.OutboxMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Opt-in with {@code ecommerce.outbox.enabled=true}. Requires the outbox_events table (see Flyway scripts). */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"})
@ConditionalOnClass({JdbcTemplate.class, KafkaTemplate.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "ecommerce.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new OutboxPublisher(jdbcTemplate, objectMapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate,
                            PlatformTransactionManager transactionManager, OutboxProperties properties,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        var metrics = new OutboxMetrics(meterRegistry.getIfAvailable(SimpleMeterRegistry::new), "postgres");
        return new OutboxRelay(jdbcTemplate, kafkaTemplate, new TransactionTemplate(transactionManager), properties,
                metrics);
    }
}
