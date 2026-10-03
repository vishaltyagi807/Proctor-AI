package dev.varshit.proctor.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 200) String password,
        @JsonSetter(nulls = Nulls.SKIP) Set<UUID> roles,
        @JsonSetter(nulls = Nulls.SKIP) Set<UUID> departments,
        @JsonSetter(nulls = Nulls.SKIP) Map<String, Object> customFields,
        Boolean enabled,
        Boolean verified
) {
    public CreateUserRequest {
        roles = roles == null ? new HashSet<>() : roles;
        departments = departments == null ? new HashSet<>() : departments;
        customFields = customFields == null ? new HashMap<>() : customFields;
        enabled = enabled == null || enabled;
        verified = verified == null || verified;
    }
}
