package dev.varshit.proctor.user.imports;

import dev.varshit.proctor.security.UserPrincipal;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface UserImportService {

    record Started(String processId, int total) {
    }

    /**
     * Validates the file's schema and parses it synchronously (fast - a header/shape check plus
     * row count), then kicks off the actual per-row import in the background. The returned Mono
     * only completes once the file is known to be well-formed and importable; a schema problem
     * surfaces as an error here, before anything is reported as "started".
     */
    Mono<Started> start(FilePart file, UserPrincipal actor);
}
