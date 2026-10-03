package dev.varshit.proctor.faceservice.dto;

import java.util.List;

public record RecognizeResponse(String image, int width, int height, List<FaceMatchDTO> faces) {
}
