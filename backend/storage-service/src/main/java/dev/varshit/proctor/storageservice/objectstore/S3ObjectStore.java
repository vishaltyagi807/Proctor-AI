package dev.varshit.proctor.storageservice.objectstore;

import dev.varshit.proctor.storageservice.config.S3Properties;
import org.springframework.http.ContentDisposition;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class S3ObjectStore implements ObjectStore {

    private final S3AsyncClient client;
    private final S3Presigner presigner;
    private final S3Properties properties;

    public S3ObjectStore(S3AsyncClient client, S3Presigner presigner, S3Properties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedRequest presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(put)
                .build());
        return new PresignedRequest(
                presigned.url().toString(),
                "PUT",
                headers(presigned.signedHeaders()),
                OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC));
    }

    @Override
    public PresignedRequest presignDownload(String key, String fileName, String contentType, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition(disposition.toString())
                .build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(get)
                .build());
        return new PresignedRequest(
                presigned.url().toString(),
                "GET",
                headers(presigned.signedHeaders()),
                OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC));
    }

    @Override
    public Mono<ObjectMetadata> stat(String key) {
        return Mono.fromFuture(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(properties.bucket()).key(key).build()))
                .map(head -> new ObjectMetadata(head.contentLength(), head.contentType()))
                .onErrorResume(NoSuchKeyException.class, e -> Mono.empty())
                .onErrorResume(S3Exception.class, e -> e.statusCode() == 404 ? Mono.empty() : Mono.error(e));
    }

    @Override
    public Mono<Void> delete(String key) {
        return Mono.fromFuture(() -> client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.bucket()).key(key).build()))
                .then();
    }

    @Override
    public Mono<Void> ensureBucket() {
        return Mono.fromFuture(() -> client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build()))
                .then()
                .onErrorResume(error -> isMissingBucket(error), error -> Mono.fromFuture(() -> client.createBucket(
                        CreateBucketRequest.builder().bucket(properties.bucket()).build())).then());
    }

    private boolean isMissingBucket(Throwable error) {
        return error instanceof NoSuchBucketException
                || (error instanceof S3Exception s3 && s3.statusCode() == 404);
    }

    private Map<String, String> headers(Map<String, List<String>> signed) {
        Map<String, String> result = new LinkedHashMap<>();
        signed.forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host")) {
                result.put(name, String.join(",", values));
            }
        });
        return result;
    }
}
