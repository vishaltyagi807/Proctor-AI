package dev.varshit.proctor.storage;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.common.exception.NotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class LocalFileStorage implements FileStorage {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9-]{1,40}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,40}/[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?");

    private final Path root;
    private final long maxBytes;

    public LocalFileStorage(StorageProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
        this.maxBytes = properties.maxBytes();
    }

    @Override
    public Mono<StoredFile> store(FilePart part, String namespace, Set<String> allowedExtensions) {
        if (!NAMESPACE.matcher(namespace).matches()) {
            return Mono.error(new IllegalArgumentException("Invalid storage namespace"));
        }
        String originalName = sanitizeName(part.filename());
        String extension = extensionOf(originalName);
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
            return Mono.error(new BadRequestException("File type '" + extension + "' is not allowed"));
        }
        String key = namespace + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            return Mono.error(new BadRequestException("Invalid file name"));
        }
        String contentType = part.headers().getContentType() == null
                ? "application/octet-stream" : part.headers().getContentType().toString();

        return Mono.fromCallable(() -> Files.createDirectories(target.getParent()))
                .subscribeOn(Schedulers.boundedElastic())
                .then(part.transferTo(target))
                .then(Mono.fromCallable(() -> Files.size(target)).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(size -> size > maxBytes
                        ? delete(key).then(Mono.error(new BadRequestException("File exceeds the maximum allowed size")))
                        : Mono.just(new StoredFile(key, originalName, contentType, size)));
    }

    @Override
    public Mono<Resource> load(String key) {
        return resolve(key).map(FileSystemResource::new);
    }

    @Override
    public Mono<Path> resolve(String key) {
        return Mono.fromCallable(() -> {
            if (!KEY.matcher(key).matches()) {
                throw new NotFoundException("File not found");
            }
            Path path = root.resolve(key).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new NotFoundException("File not found");
            }
            return path;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String key) {
        return Mono.fromRunnable(() -> {
            if (!KEY.matcher(key).matches()) {
                return;
            }
            try {
                Path path = root.resolve(key).normalize();
                if (path.startsWith(root)) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException ignored) {
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String sanitizeName(String name) {
        String base = name == null ? "file" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.isBlank() ? "file" : base;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
