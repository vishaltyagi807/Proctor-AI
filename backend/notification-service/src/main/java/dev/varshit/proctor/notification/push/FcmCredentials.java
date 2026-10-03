package dev.varshit.proctor.notification.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.common.exception.BadRequestException;

public record FcmCredentials(String projectId, String clientEmail, String privateKeyPem) {

    public static FcmCredentials parse(ObjectMapper mapper, String serviceAccountJson) {
        try {
            JsonNode node = mapper.readTree(serviceAccountJson);
            if (!"service_account".equals(text(node, "type"))) {
                throw new BadRequestException("The JSON is not a Google service account key (type must be service_account)");
            }
            String project = text(node, "project_id");
            String email = text(node, "client_email");
            String key = text(node, "private_key");
            if (project == null || email == null || key == null || !key.contains("BEGIN PRIVATE KEY")) {
                throw new BadRequestException("Service account JSON must contain project_id, client_email and private_key");
            }
            return new FcmCredentials(project, email, key);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Service account JSON is not valid JSON");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
