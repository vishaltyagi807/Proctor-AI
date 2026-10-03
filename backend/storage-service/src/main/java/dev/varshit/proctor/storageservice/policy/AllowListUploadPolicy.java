package dev.varshit.proctor.storageservice.policy;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.storageservice.config.StorageLimits;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AllowListUploadPolicy implements UploadPolicy {

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp")),
            Map.entry("image/heic", Set.of("heic")),
            Map.entry("video/mp4", Set.of("mp4", "m4v")),
            Map.entry("video/webm", Set.of("webm")),
            Map.entry("video/quicktime", Set.of("mov")),
            Map.entry("audio/mpeg", Set.of("mp3")),
            Map.entry("audio/mp4", Set.of("m4a")),
            Map.entry("audio/wav", Set.of("wav")),
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("text/plain", Set.of("txt")),
            Map.entry("text/csv", Set.of("csv")),
            Map.entry("application/msword", Set.of("doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.ms-excel", Set.of("xls")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx"))
    );

    private static final Set<String> INLINE_PREFIXES = Set.of("image/", "video/", "audio/");

    private final StorageLimits limits;

    public AllowListUploadPolicy(StorageLimits limits) {
        this.limits = limits;
    }

    @Override
    public ApprovedUpload approve(String fileName, String contentType, long sizeBytes) {
        String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        Set<String> extensions = ALLOWED.get(type);
        if (extensions == null) {
            throw new BadRequestException("Content type '" + contentType + "' is not allowed");
        }
        if (sizeBytes <= 0 || sizeBytes > limits.maxFileBytes()) {
            throw new BadRequestException("File size must be between 1 byte and " + limits.maxFileBytes() + " bytes");
        }
        String name = sanitize(fileName);
        String extension = extensionOf(name);
        if (!extensions.contains(extension)) {
            throw new BadRequestException("File extension does not match content type " + type);
        }
        boolean inline = INLINE_PREFIXES.stream().anyMatch(type::startsWith);
        return new ApprovedUpload(type, extension, name, inline);
    }

    private String sanitize(String fileName) {
        String base = fileName == null ? "" : fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty() || base.length() > 200) {
            throw new BadRequestException("Invalid file name");
        }
        return base;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
