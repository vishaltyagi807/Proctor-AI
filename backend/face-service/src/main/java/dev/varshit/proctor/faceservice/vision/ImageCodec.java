package dev.varshit.proctor.faceservice.vision;

import dev.varshit.proctor.common.exception.BadRequestException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ImageCodec {

    public record Letterboxed(BufferedImage image, float scale) {
    }

    private ImageCodec() {
    }

    public static byte[] decodeBase64(String base64) {
        String cleaned = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        try {
            return Base64.getDecoder().decode(cleaned.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Image is not valid base64");
        }
    }

    public static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) {
                throw new BadRequestException("Unable to decode image");
            }
            BufferedImage rgb = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(decoded, 0, 0, Color.BLACK, null);
            g.dispose();
            return rgb;
        } catch (IOException e) {
            throw new BadRequestException("Unable to decode image");
        }
    }

    public static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode image", e);
        }
    }

    public static Letterboxed letterbox(BufferedImage source, int targetSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min((float) targetSize / width, (float) targetSize / height);
        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        BufferedImage canvas = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, targetSize, targetSize);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return new Letterboxed(canvas, scale);
    }
}
