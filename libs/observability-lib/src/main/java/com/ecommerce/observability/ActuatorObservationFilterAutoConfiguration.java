package com.ecommerce.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus scrapes {@code /actuator/prometheus} every 15 s on every replica. Without this filter each scrape
 * becomes a trace (and an http.server.requests sample), burying real user traffic in Kibana APM.
 * Actuator traffic is still visible as the Prometheus {@code up} / scrape metrics.
 */
@AutoConfiguration
public class ActuatorObservationFilterAutoConfiguration {

    static final String ACTUATOR_PREFIX = "/actuator";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    @ConditionalOnClass(org.springframework.http.server.observation.ServerRequestObservationContext.class)
    static class Servlet {

        @Bean
        ObservationPredicate ignoreActuatorServletRequests() {
            return (name, context) -> !(context
                    instanceof org.springframework.http.server.observation.ServerRequestObservationContext request
                    && request.getCarrier().getRequestURI().startsWith(ACTUATOR_PREFIX));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.REACTIVE)
    @ConditionalOnClass(org.springframework.http.server.reactive.observation.ServerRequestObservationContext.class)
    static class Reactive {

        @Bean
        ObservationPredicate ignoreActuatorReactiveRequests() {
            return (name, context) -> !(context
                    instanceof org.springframework.http.server.reactive.observation.ServerRequestObservationContext request
                    && request.getCarrier().getPath().value().startsWith(ACTUATOR_PREFIX));
        }
    }
}
