package dev.varshit.proctor.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String email) {
}
