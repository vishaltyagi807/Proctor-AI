package dev.varshit.proctor.faceservice.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Map;

public class ArcFaceEmbedder implements FaceEmbedder, AutoCloseable {

    private static final int INPUT_SIZE = 112;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final int dimensions;

    public ArcFaceEmbedder(OrtEnvironment environment, OrtSession session, int dimensions) {
        this.environment = environment;
        this.session = session;
        this.inputName = session.getInputNames().iterator().next();
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(BufferedImage alignedFace112) {
        float[] chw = toChwNormalized(alignedFace112);
        try {
            OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(chw), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
            try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                OnnxValue output = result.get(0);
                FloatBuffer buffer = ((OnnxTensor) output).getFloatBuffer();
                float[] raw = new float[buffer.remaining()];
                buffer.get(raw);
                return l2Normalize(raw);
            } finally {
                input.close();
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Face embedding inference failed", e);
        }
    }

    private float[] toChwNormalized(BufferedImage image) {
        int size = INPUT_SIZE;
        float[] data = new float[3 * size * size];
        int plane = size * size;
        int[] pixels = image.getRGB(0, 0, size, size, null, 0, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = pixels[y * size + x];
                float r = (pixel >> 16) & 0xFF;
                float g = (pixel >> 8) & 0xFF;
                float b = pixel & 0xFF;
                int idx = y * size + x;
                data[idx] = (r - 127.5f) / 127.5f;
                data[plane + idx] = (g - 127.5f) / 127.5f;
                data[2 * plane + idx] = (b - 127.5f) / 127.5f;
            }
        }
        return data;
    }

    private float[] l2Normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException(e);
        }
    }
}
