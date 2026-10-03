package dev.varshit.proctor.monitoring;

import java.time.Duration;

public final class MonitoringKeys {

    public static final String INSTANCE_PREFIX = "monitor:instance:";
    public static final Duration REPORT_INTERVAL = Duration.ofSeconds(2);
    public static final Duration REPORT_TTL = Duration.ofSeconds(10);

    private MonitoringKeys() {
    }

    public static String instanceKey(String instanceId) {
        return INSTANCE_PREFIX + instanceId;
    }
}
