package dev.varshit.proctor.faceservice.vision;

import java.util.ArrayList;
import java.util.List;

public final class NonMaxSuppression {

    private NonMaxSuppression() {
    }

    public static List<FaceBox> apply(List<FaceBox> boxes, float iouThreshold) {
        List<FaceBox> sorted = new ArrayList<>(boxes);
        sorted.sort((a, b) -> Float.compare(b.score(), a.score()));
        boolean[] suppressed = new boolean[sorted.size()];
        List<FaceBox> kept = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (suppressed[i]) {
                continue;
            }
            FaceBox current = sorted.get(i);
            kept.add(current);
            for (int j = i + 1; j < sorted.size(); j++) {
                if (!suppressed[j] && iou(current, sorted.get(j)) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private static float iou(FaceBox a, FaceBox b) {
        float ax2 = a.x() + a.width();
        float ay2 = a.y() + a.height();
        float bx2 = b.x() + b.width();
        float by2 = b.y() + b.height();
        float ix1 = Math.max(a.x(), b.x());
        float iy1 = Math.max(a.y(), b.y());
        float ix2 = Math.min(ax2, bx2);
        float iy2 = Math.min(ay2, by2);
        float iw = Math.max(0, ix2 - ix1);
        float ih = Math.max(0, iy2 - iy1);
        float intersection = iw * ih;
        float union = a.area() + b.area() - intersection;
        return union <= 0 ? 0 : intersection / union;
    }
}
