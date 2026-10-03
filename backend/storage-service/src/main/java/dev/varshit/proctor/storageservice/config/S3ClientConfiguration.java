package dev.varshit.proctor.storageservice.config;

import dev.varshit.proctor.storageservice.objectstore.ObjectStore;
import dev.varshit.proctor.storageservice.objectstore.S3ObjectStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.util.retry.Retry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3ClientConfiguration {

    @Bean(destroyMethod = "close")
    public S3AsyncClient s3AsyncClient(S3Properties properties) {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                .build();
    }

    @Bean
    public ObjectStore objectStore(S3AsyncClient client, S3Presigner presigner, S3Properties properties) {
        return new S3ObjectStore(client, presigner, properties);
    }

    @Bean
    public ApplicationRunner bucketInitializer(ObjectStore store) {
        return args -> store.ensureBucket()
                .retryWhen(Retry.backoff(10, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(15)))
                .block();
    }

    private StaticCredentialsProvider credentials(S3Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private software.amazon.awssdk.services.s3.S3Configuration pathStyle() {
        return software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
