package dev.varshit.proctor.faceservice.imports;

import dev.varshit.proctor.common.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * There's no real "schema" for a zip of photos - this generates a small reference file
 * explaining the one rule that matters (filename, minus extension, must equal the enrolled
 * user's email), in the same csv/json/xlsx choice offered for the user import template.
 */
@Component
public class FaceZipTemplateGenerator {

    public record Template(byte[] bytes, String contentType, String fileName) {
    }

    private static final String[] HEADER = {"filename", "note"};
    private static final String[][] EXAMPLES = {
            {"student0001@college.com.jpg", "Filename (minus extension) must exactly match the enrolled user's email"},
            {"teacher0002@college.com.png", "Supported extensions: jpg, jpeg, png, webp, bmp"},
    };

    public Template generate(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new Template(json(), "application/json", "faces-import-template.json");
            case "xlsx" -> new Template(xlsx(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "faces-import-template.xlsx");
            case "csv" -> new Template(csv(), "text/csv", "faces-import-template.csv");
            default -> throw new BadRequestException("Unsupported template format: " + format);
        };
    }

    private byte[] csv() {
        StringBuilder sb = new StringBuilder(String.join(",", HEADER)).append('\n');
        for (String[] row : EXAMPLES) {
            sb.append('"').append(row[0]).append("\",\"").append(row[1]).append("\"\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] json() {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < EXAMPLES.length; i++) {
            sb.append("  { \"filename\": \"").append(EXAMPLES[i][0]).append("\", \"note\": \"")
                    .append(EXAMPLES[i][1]).append("\" }");
            sb.append(i < EXAMPLES.length - 1 ? ",\n" : "\n");
        }
        sb.append("]\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("faces");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            for (int r = 0; r < EXAMPLES.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(EXAMPLES[r][0]);
                row.createCell(1).setCellValue(EXAMPLES[r][1]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate xlsx template", e);
        }
    }
}
