package dev.varshit.proctor.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Set;

public interface FileStorage {

    Mono<StoredFile> store(FilePart part, String namespace, Set<String> allowedExtensions);

    Mono<Resource> load(String key);

    Mono<Path> resolve(String key);

    Mono<Void> delete(String key);
}
