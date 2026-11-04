package dev.varshit.proctor.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestRedis {

    private static final int PORT = 6379;

    private static GenericContainer<?> container;

    private TestRedis() {
    }

    public static synchronized GenericContainer<?> container() {
        if (container == null) {
            GenericContainer<?> started = new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine"))
                    .withExposedPorts(PORT);
            started.start();
            container = started;
        }
        return container;
    }

    public static String host() {
        return container().getHost();
    }

    public static int port() {
        return container().getMappedPort(PORT);
    }
}
