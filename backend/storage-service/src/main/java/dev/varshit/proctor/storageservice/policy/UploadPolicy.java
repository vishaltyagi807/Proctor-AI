package dev.varshit.proctor.storageservice.policy;

public interface UploadPolicy {

    ApprovedUpload approve(String fileName, String contentType, long sizeBytes);

    record ApprovedUpload(String contentType, String extension, String displayName, boolean inlineViewable) {
    }
}
