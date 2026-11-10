package com.ecommerce.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Connects the {@code OTEL} appender declared in {@code logback-spring.xml} to the OpenTelemetry SDK that Spring
 * Boot builds. Logback starts long before the application context, so the appender can only be wired once the
 * {@link OpenTelemetry} bean exists. Until then the appender drops events (startup logs still reach the console).
 * <p>
 * Every log record exported from here carries the current trace_id/span_id, which is what makes
 * "show me the logs of this trace" possible in Kibana.
 */
@AutoConfiguration(afterName = "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnClass({OpenTelemetry.class, OpenTelemetryAppender.class})
public class OpenTelemetryLogbackAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    InitializingBean openTelemetryLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
