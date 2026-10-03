package dev.varshit.proctor.faceservice.dto;

public record FaceMatchDTO(BoundingBoxDTO box, Float confidence, boolean matched, MatchedUserDTO user) {
}
