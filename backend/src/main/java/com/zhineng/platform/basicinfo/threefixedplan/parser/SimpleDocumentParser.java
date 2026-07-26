package com.zhineng.platform.basicinfo.threefixedplan.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class SimpleDocumentParser {
    public ParsedDocument parse(Path path, String fileType, List<Mapping> mappings) throws IOException {
        TextData data = switch (fileType) {
            case "XLSX" -> readXlsx(path);
            case "DOCX" -> readDocx(path);
            case "PDF" -> readPdf(path);
            default -> throw new IOException("不支持的文件类型");
        };
        Map<String, ParsedField> fields = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            if (!"ALL".equals(mapping.fileType()) && !fileType.equals(mapping.fileType())) continue;
            String value = data.pairs.get(normalize(mapping.sourceLabel()));
            String method = "TABLE";
            if (value == null) {
                value = findInText(data.text, mapping.sourceLabel());
                method = "LABEL";
            }
            if (value != null && !value.isBlank() && !fields.containsKey(mapping.targetField())) {
                fields.put(mapping.targetField(), new ParsedField(
                        mapping.sourceLabel(), value.trim(), value.trim(), method,
                        "TABLE".equals(method) ? "HIGH" : "MEDIUM"));
            }
        }
        String status = fields.isEmpty() ? "FAILED" : fields.size() >= 4 ? "SUCCESS" : "PARTIAL";
        return new ParsedDocument(data.text, fields, status);
    }

    private TextData readXlsx(Path path) throws IOException {
        Map<String, String> pairs = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (var input = Files.newInputStream(path);
             var workbook = WorkbookFactory.create(input)) {
            var sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                row.forEach(cell -> values.add(formatter.formatCellValue(cell).trim()));
                values.removeIf(String::isBlank);
                if (values.isEmpty()) continue;
                text.append(String.join(" ", values)).append('\n');
                if (values.size() >= 2) pairs.putIfAbsent(normalize(values.get(0)), values.get(1));
            }
        }
        return new TextData(text.toString(), pairs);
    }

    private TextData readDocx(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    text.append(row.getTableCells().stream()
                            .map(cell -> cell.getText().trim()).reduce((a, b) -> a + " " + b)
                            .orElse("")).append('\n')));
        }
        return new TextData(text.toString(), pairsFromLines(text.toString()));
    }

    private TextData readPdf(Path path) throws IOException {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return new TextData(text, pairsFromLines(text));
        }
    }

    private Map<String, String> pairsFromLines(String text) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("[：:]\\s*", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                pairs.putIfAbsent(normalize(parts[0]), parts[1].trim());
            }
        }
        return pairs;
    }

    private String findInText(String text, String label) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (normalize(trimmed).startsWith(normalize(label))) {
                String value = trimmed.substring(Math.min(trimmed.length(), label.length()))
                        .replaceFirst("^[：:\\s]+", "").trim();
                if (!value.isBlank()) return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s：:]", "").toUpperCase(Locale.ROOT);
    }

    public record Mapping(String fileType, String sourceLabel, String targetField) {
    }
    public record ParsedField(
            String sourceLabel, String value, String snippet, String method, String confidence
    ) {
    }
    public record ParsedDocument(String text, Map<String, ParsedField> fields, String status) {
    }
    private record TextData(String text, Map<String, String> pairs) {
    }
}
