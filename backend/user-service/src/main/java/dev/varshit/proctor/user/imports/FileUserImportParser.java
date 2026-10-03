package dev.varshit.proctor.user.imports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import dev.varshit.proctor.common.enums.CustomFieldType;
import dev.varshit.proctor.common.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FileUserImportParser implements UserImportParser {

    private static final Set<String> REQUIRED_COLUMNS = Set.of("name", "email", "password");
    private static final Set<String> FIXED_COLUMNS = Set.of("name", "email", "password", "roles", "departments", "enabled", "verified");

    private final ObjectMapper mapper;
    private final int maxRows;

    public FileUserImportParser(ObjectMapper mapper, @Value("${app.import.max-rows:100000}") int maxRows) {
        this.mapper = mapper;
        this.maxRows = maxRows;
    }

    @Override
    public Mono<List<ImportRow>> parse(Path file, ImportReferenceData reference) {
        return Mono.fromCallable(() -> parseBlocking(file, reference)).subscribeOn(Schedulers.boundedElastic());
    }

    private List<ImportRow> parseBlocking(Path file, ImportReferenceData reference) throws Exception {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<ImportRow> rows;
        if (name.endsWith(".json")) {
            rows = parseJson(file, reference);
        } else if (name.endsWith(".csv")) {
            rows = parseCsv(file, reference);
        } else if (name.endsWith(".xlsx")) {
            rows = parseExcel(file, reference);
        } else {
            throw new BadRequestException("Unsupported file type. Use .csv, .json or .xlsx");
        }
        if (rows.isEmpty()) {
            throw new BadRequestException("The file has no rows to import");
        }
        if (rows.size() > maxRows) {
            throw new BadRequestException("Import is limited to " + maxRows + " users, this file has " + rows.size());
        }
        return rows;
    }

    private void validateHeader(Collection<String> header, String fileKind, ImportReferenceData reference) {
        Set<String> present = header.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !present.contains(column)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new BadRequestException(fileKind + " is missing required column(s): " + String.join(", ", missing)
                    + ". Expected at least: " + String.join(", ", REQUIRED_COLUMNS));
        }
        Set<String> unknown = present.stream()
                .filter(column -> !FIXED_COLUMNS.contains(column))
                .filter(column -> !reference.customFieldsByKey().containsKey(column))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            String known = reference.customFieldsByKey().isEmpty()
                    ? "(no custom fields are defined for users)"
                    : String.join(", ", reference.customFieldsByKey().keySet());
            throw new BadRequestException(fileKind + " has unknown column(s): " + String.join(", ", unknown)
                    + ". Known custom field columns: " + known);
        }
    }

    private List<ImportRow> parseJson(Path file, ImportReferenceData reference) throws Exception {
        List<Map<String, Object>> raw;
        try (InputStream in = Files.newInputStream(file)) {
            raw = mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new BadRequestException("Invalid JSON: expected an array of user objects");
        }
        if (raw.isEmpty()) {
            return List.of();
        }
        validateHeader(raw.get(0).keySet(), "JSON", reference);
        return raw.stream().map(row -> build(row, reference)).collect(Collectors.toList());
    }

    private List<ImportRow> parseCsv(Path file, ImportReferenceData reference) throws Exception {
        List<ImportRow> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new BadRequestException("CSV file is empty");
            }
            validateHeader(Arrays.asList(header), "CSV", reference);
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i].trim(), i < line.length ? line[i] : null);
                }
                rows.add(build(row, reference));
            }
        }
        return rows;
    }

    private List<ImportRow> parseExcel(Path file, ImportReferenceData reference) throws Exception {
        List<ImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = Files.newInputStream(file); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("XLSX file is empty");
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            validateHeader(headers, "XLSX", reference);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Map<String, Object> values = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    values.put(headers.get(j), formatter.formatCellValue(row.getCell(j)));
                }
                rows.add(build(values, reference));
            }
        }
        return rows;
    }

    private ImportRow build(Map<String, Object> row, ImportReferenceData reference) {
        Map<String, Object> customFields = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String key = rawKey.trim().toLowerCase(Locale.ROOT);
            if (FIXED_COLUMNS.contains(key) || isBlank(entry.getValue())) {
                continue;
            }
            ImportReferenceData.CustomFieldMeta meta = reference.customFieldsByKey().get(key);
            CustomFieldType type = meta == null ? null : meta.dataType();
            if (type == CustomFieldType.multi_select && entry.getValue() instanceof String text) {
                customFields.put(key, splitList(text));
            } else {
                customFields.put(key, entry.getValue());
            }
        }
        return new ImportRow(
                str(row.get("name")),
                str(row.get("email")),
                str(row.get("password")),
                toNameSet(row.get("roles")),
                toNameSet(row.get("departments")),
                customFields,
                flag(row.get("enabled")),
                flag(row.get("verified")));
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> splitList(String value) {
        return Arrays.stream(value.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private Set<String> toNameSet(Object value) {
        if (value == null) {
            return new HashSet<>();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        }
        String text = String.valueOf(value);
        return text.isBlank() ? new HashSet<>() : new HashSet<>(splitList(text));
    }

    private Boolean flag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Boolean.parseBoolean(text);
    }
}
