package dev.varshit.proctor.faceservice.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScrfdFaceDetector implements FaceDetector, AutoCloseable {

    private static final int[] STRIDES = {8, 16, 32};

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final int inputSize;
    private final float scoreThreshold;
    private final float nmsThreshold;
    private final String inputName;

    public ScrfdFaceDetector(OrtEnvironment environment, OrtSession session, int inputSize, float scoreThreshold, float nmsThreshold) {
        this.environment = environment;
        this.session = session;
        this.inputSize = inputSize;
        this.scoreThreshold = scoreThreshold;
        this.nmsThreshold = nmsThreshold;
        this.inputName = session.getInputNames().iterator().next();
    }

    @Override
    public List<FaceBox> detect(BufferedImage image) {
        ImageCodec.Letterboxed letterboxed = ImageCodec.letterbox(image, inputSize);
        float[] chw = toChwNormalized(letterboxed.image());
        try {
            OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(chw), new long[]{1, 3, inputSize, inputSize});
            try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
                List<FaceBox> boxes = decode(result, letterboxed.scale());
                return NonMaxSuppression.apply(boxes, nmsThreshold);
            } finally {
                input.close();
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Face detection inference failed", e);
        }
    }

    private float[] toChwNormalized(BufferedImage image) {
        int size = image.getWidth();
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
                data[idx] = (r - 127.5f) / 128f;
                data[plane + idx] = (g - 127.5f) / 128f;
                data[2 * plane + idx] = (b - 127.5f) / 128f;
            }
        }
        return data;
    }

    private List<FaceBox> decode(OrtSession.Result result, float scale) throws OrtException {
        List<float[]> flattened = new ArrayList<>();
        for (Map.Entry<String, OnnxValue> entry : result) {
            flattened.add(flatten((OnnxTensor) entry.getValue()));
        }
        int fmc = STRIDES.length;
        if (flattened.size() < fmc * 2) {
            throw new IllegalStateException("Unexpected SCRFD output count: " + flattened.size());
        }
        boolean hasKeypoints = flattened.size() >= fmc * 3;
        List<FaceBox> boxes = new ArrayList<>();
        for (int i = 0; i < fmc; i++) {
            int stride = STRIDES[i];
            float[] scores = flattened.get(i);
            float[] bboxPreds = flattened.get(i + fmc);
            float[] kpsPreds = hasKeypoints ? flattened.get(i + fmc * 2) : null;
            int height = inputSize / stride;
            int width = inputSize / stride;
            int locations = height * width;
            int numAnchors = Math.max(1, scores.length / Math.max(1, locations));
            for (int idx = 0; idx < scores.length; idx++) {
                float score = scores[idx];
                if (score < scoreThreshold) {
                    continue;
                }
                int location = idx / numAnchors;
                int row = location / width;
                int col = location % width;
                float centerX = col * stride;
                float centerY = row * stride;
                float left = bboxPreds[idx * 4] * stride;
                float top = bboxPreds[idx * 4 + 1] * stride;
                float right = bboxPreds[idx * 4 + 2] * stride;
                float bottom = bboxPreds[idx * 4 + 3] * stride;
                float x1 = (centerX - left) / scale;
                float y1 = (centerY - top) / scale;
                float x2 = (centerX + right) / scale;
                float y2 = (centerY + bottom) / scale;
                float[] landmarks = null;
                if (kpsPreds != null) {
                    landmarks = new float[10];
                    for (int k = 0; k < 5; k++) {
                        float px = centerX + kpsPreds[idx * 10 + k * 2] * stride;
                        float py = centerY + kpsPreds[idx * 10 + k * 2 + 1] * stride;
                        landmarks[k * 2] = px / scale;
                        landmarks[k * 2 + 1] = py / scale;
                    }
                }
                boxes.add(new FaceBox(x1, y1, x2 - x1, y2 - y1, score, landmarks));
            }
        }
        return boxes;
    }

    private float[] flatten(OnnxTensor tensor) throws OrtException {
        FloatBuffer buffer = tensor.getFloatBuffer();
        float[] data = new float[buffer.remaining()];
        buffer.get(data);
        return data;
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
