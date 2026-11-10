package com.ecommerce.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    @Test
    void installsLogbackAppenderWhenOpenTelemetryIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenTelemetryLogbackAutoConfiguration.class))
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .run(context -> assertThat(context).hasSingleBean(InitializingBean.class));
    }

    @Test
    void doesNothingWithoutOpenTelemetry() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenTelemetryLogbackAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(InitializingBean.class));
    }

    @Test
    void servletActuatorRequestsAreNotObserved() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActuatorObservationFilterAutoConfiguration.class))
                .run(context -> {
                    ObservationPredicate predicate = context.getBean(ObservationPredicate.class);
                    assertThat(predicate.test("http.server.requests", request("/actuator/prometheus"))).isFalse();
                    assertThat(predicate.test("http.server.requests", request("/api/v1/orders"))).isTrue();
                    assertThat(predicate.test("spring.kafka.listener", new Observation.Context())).isTrue();
                });
    }

    private static ServerRequestObservationContext request(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
    }
}
