package dev.varshit.proctor.storage;

public record StoredFile(String key, String originalName, String contentType, long sizeBytes) {
}
