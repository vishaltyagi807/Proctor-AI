package dev.varshit.proctor.user.imports;

import dev.varshit.proctor.common.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ImportTemplateGenerator {

    public record Template(byte[] bytes, String contentType, String fileName) {
    }

    private static final List<String> FIXED_HEADER = List.of("name", "email", "password", "roles", "departments", "enabled", "verified");

    private final ImportLookupRepository lookup;

    public ImportTemplateGenerator(ImportLookupRepository lookup) {
        this.lookup = lookup;
    }

    public Mono<Template> generate(String format) {
        return lookup.load().map(reference -> build(format, reference));
    }

    private Template build(String format, ImportReferenceData reference) {
        List<String> header = new ArrayList<>(FIXED_HEADER);
        // TreeMap for a stable, alphabetical column order regardless of definition creation order.
        Map<String, ImportReferenceData.CustomFieldMeta> customFields = new TreeMap<>(reference.customFieldsByKey());
        header.addAll(customFields.keySet());

        List<String> example = new ArrayList<>(List.of(
                "Jane Doe",
                "jane.doe@college.com",
                "Passw0rd!",
                reference.roleIdsByName().keySet().stream().sorted().findFirst().orElse("<role-name>"),
                reference.departmentIdsByCode().keySet().stream().sorted().findFirst().orElse("<department-code>"),
                "true",
                "true"));
        customFields.values().forEach(meta -> example.add(exampleValue(meta)));

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new Template(json(header, example), "application/json", "users-import-template.json");
            case "xlsx" -> new Template(xlsx(header, example), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "users-import-template.xlsx");
            case "csv" -> new Template(csv(header, example), "text/csv", "users-import-template.csv");
            default -> throw new BadRequestException("Unsupported template format: " + format);
        };
    }

    private String exampleValue(ImportReferenceData.CustomFieldMeta meta) {
        List<String> options = meta.options();
        return switch (meta.dataType()) {
            case text -> "Sample text";
            case number -> "123";
            case bool -> "true";
            case date -> "2026-01-01";
            case select -> options != null && !options.isEmpty() ? options.get(0) : "option1";
            case multi_select -> options != null && !options.isEmpty()
                    ? String.join("|", options.subList(0, Math.min(2, options.size())))
                    : "option1|option2";
        };
    }

    private byte[] csv(List<String> header, List<String> example) {
        String content = String.join(",", header) + "\n" + String.join(",", quoteAll(example)) + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private List<String> quoteAll(List<String> values) {
        return values.stream().map(v -> v.contains(",") ? "\"" + v + "\"" : v).toList();
    }

    private byte[] json(List<String> header, List<String> example) {
        StringBuilder sb = new StringBuilder("[\n  {\n");
        for (int i = 0; i < header.size(); i++) {
            sb.append("    \"").append(header.get(i)).append("\": ");
            String value = example.get(i);
            boolean bare = header.get(i).equals("enabled") || header.get(i).equals("verified");
            sb.append(bare ? value : "\"" + value + "\"");
            sb.append(i < header.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  }\n]\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<String> header, List<String> example) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("users");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                headerRow.createCell(i).setCellValue(header.get(i));
            }
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < example.size(); i++) {
                exampleRow.createCell(i).setCellValue(example.get(i));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate xlsx template", e);
        }
    }
}
