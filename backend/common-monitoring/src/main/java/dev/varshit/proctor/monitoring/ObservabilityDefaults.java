package dev.varshit.proctor.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class ObservabilityDefaults implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE = "proctorObservabilityDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "spring.reactor.context-propagation", "auto",
            "management.metrics.tags.application", "${spring.application.name:unknown}",
            "management.tracing.sampling.probability", "0.0");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(SOURCE)) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE, DEFAULTS));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
