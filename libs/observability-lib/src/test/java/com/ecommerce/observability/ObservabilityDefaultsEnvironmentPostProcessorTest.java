package com.ecommerce.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityDefaultsEnvironmentPostProcessorTest {

    private final ObservabilityDefaultsEnvironmentPostProcessor processor =
            new ObservabilityDefaultsEnvironmentPostProcessor();

    @Test
    void addsDefaultsWithLowestPrecedence() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("defaultProperties", Map.of()));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/traces");
        var names = environment.getPropertySources().stream().map(p -> p.getName()).toList();
        assertThat(names.indexOf(ObservabilityDefaultsEnvironmentPostProcessor.SOURCE_NAME))
                .isEqualTo(names.indexOf("defaultProperties") - 1);
    }

    @Test
    void serviceConfigurationWinsOverDefaults() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("application.yml",
                Map.of("management.endpoints.web.exposure.include", "health,gateway")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,gateway");
    }

    @Test
    void otlpEndpointFollowsEnvironmentVariable() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("env",
                Map.of("OTLP_ENDPOINT", "http://otel-collector:4318")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.opentelemetry.logging.export.otlp.endpoint"))
                .isEqualTo("http://otel-collector:4318/v1/logs");
    }
}
