package dev.varshit.proctor.faceservice.vision;

import java.awt.image.BufferedImage;

public interface FaceEmbedder {

    float[] embed(BufferedImage alignedFace112);

    int dimensions();
}
