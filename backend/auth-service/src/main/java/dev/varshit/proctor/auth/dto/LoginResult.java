package dev.varshit.proctor.auth.dto;

import dev.varshit.proctor.common.dto.UserDTO;
import dev.varshit.proctor.security.jwt.IssuedToken;

public record LoginResult(UserDTO user, IssuedToken access, IssuedRefreshToken refresh) {
}
