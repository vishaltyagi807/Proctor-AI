package dev.varshit.proctor.faceservice.vision;

import java.awt.image.BufferedImage;
import java.util.List;

public interface FaceDetector {

    List<FaceBox> detect(BufferedImage image);
}
