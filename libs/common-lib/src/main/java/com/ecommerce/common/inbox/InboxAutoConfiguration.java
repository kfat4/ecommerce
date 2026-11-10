package com.ecommerce.common.inbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in with {@code ecommerce.inbox.enabled=true}. Requires the processed_messages table. */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass({JdbcTemplate.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "ecommerce.inbox", name = "enabled", havingValue = "true")
public class InboxAutoConfiguration {

    @Bean
    ProcessedMessageStore processedMessageStore(JdbcTemplate jdbcTemplate, ObjectProvider<MeterRegistry> meterRegistry) {
        return new ProcessedMessageStore(jdbcTemplate, meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }
}
