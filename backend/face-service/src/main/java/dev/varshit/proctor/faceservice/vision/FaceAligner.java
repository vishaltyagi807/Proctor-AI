package dev.varshit.proctor.faceservice.vision;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public final class FaceAligner {

    private static final float[][] REFERENCE_LANDMARKS_112 = {
            {38.2946f, 51.6963f},
            {73.5318f, 51.5014f},
            {56.0252f, 71.7366f},
            {41.5493f, 92.3655f},
            {70.7299f, 92.2041f}
    };

    private FaceAligner() {
    }

    public static BufferedImage align(BufferedImage source, float[] landmarks, int outputSize) {
        if (landmarks == null || landmarks.length < 10) {
            return null;
        }
        float scale = outputSize / 112f;
        float meanX = 0;
        float meanY = 0;
        float meanU = 0;
        float meanV = 0;
        float[] xs = new float[5];
        float[] ys = new float[5];
        float[] us = new float[5];
        float[] vs = new float[5];
        for (int i = 0; i < 5; i++) {
            xs[i] = landmarks[i * 2];
            ys[i] = landmarks[i * 2 + 1];
            us[i] = REFERENCE_LANDMARKS_112[i][0] * scale;
            vs[i] = REFERENCE_LANDMARKS_112[i][1] * scale;
            meanX += xs[i];
            meanY += ys[i];
            meanU += us[i];
            meanV += vs[i];
        }
        meanX /= 5f;
        meanY /= 5f;
        meanU /= 5f;
        meanV /= 5f;

        float numeratorA = 0;
        float numeratorB = 0;
        float denominator = 0;
        for (int i = 0; i < 5; i++) {
            float cx = xs[i] - meanX;
            float cy = ys[i] - meanY;
            float cu = us[i] - meanU;
            float cv = vs[i] - meanV;
            numeratorA += cx * cu + cy * cv;
            numeratorB += cx * cv - cy * cu;
            denominator += cx * cx + cy * cy;
        }
        if (denominator <= 0f) {
            return null;
        }
        float a = numeratorA / denominator;
        float b = numeratorB / denominator;
        float tx = meanU - (a * meanX - b * meanY);
        float ty = meanV - (b * meanX + a * meanY);

        AffineTransform transform = new AffineTransform(a, b, -b, a, tx, ty);
        BufferedImage output = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, outputSize, outputSize);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, transform, null);
        g.dispose();
        return output;
    }
}
