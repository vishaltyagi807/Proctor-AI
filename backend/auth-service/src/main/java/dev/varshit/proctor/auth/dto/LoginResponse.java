package dev.varshit.proctor.auth.dto;

import dev.varshit.proctor.common.dto.AccessTokenDTO;
import dev.varshit.proctor.common.dto.UserDTO;

public record LoginResponse(UserDTO user, AccessTokenDTO tokenInfo) {
}
