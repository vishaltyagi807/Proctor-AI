package dev.varshit.proctor.user.imports;

import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

public interface UserImportParser {

    Mono<List<ImportRow>> parse(Path file, ImportReferenceData reference);
}
