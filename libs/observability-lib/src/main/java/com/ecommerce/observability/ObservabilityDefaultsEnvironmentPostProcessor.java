package com.ecommerce.observability;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Adds {@code ecommerce-observability-defaults.yml} with the <b>lowest</b> precedence (just above Spring's
 * {@code defaultProperties}). Unlike {@code spring.config.import} – where the imported file wins over the file
 * that imports it – every service, profile or environment variable can still override these defaults.
 * <p>
 * Runs after {@link ConfigDataEnvironmentPostProcessor} so that application.yml is already in place and the
 * defaults really end up behind it.
 */
public class ObservabilityDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String RESOURCE = "ecommerce-observability-defaults.yml";
    static final String SOURCE_NAME = "ecommerceObservabilityDefaults";
    private static final String DEFAULT_PROPERTIES = "defaultProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(SOURCE_NAME)) {
            return;
        }
        for (PropertySource<?> source : load()) {
            if (sources.contains(DEFAULT_PROPERTIES)) {
                sources.addBefore(DEFAULT_PROPERTIES, source);
            } else {
                sources.addLast(source);
            }
        }
    }

    private static List<PropertySource<?>> load() {
        try {
            return new YamlPropertySourceLoader().load(SOURCE_NAME, new ClassPathResource(RESOURCE));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + RESOURCE, e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
