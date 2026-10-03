package dev.varshit.proctor.faceservice.vision;

public record FaceBox(float x, float y, float width, float height, float score, float[] landmarks) {

    public float area() {
        return width * height;
    }
}
