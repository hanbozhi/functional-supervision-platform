package com.zhineng.platform.basicinfo.staffing.excel;

import com.zhineng.platform.basicinfo.staffing.service.StaffingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class StaffingExcelService {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final String[] HEADERS = {
            "机构编码", "机构名称", "核定编制", "实有在编", "领导职数核定",
            "领导职数占用", "编外人员", "数据日期", "变更原因", "备注"
    };

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("编制人员台账");
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 9 ? 6000 : 4200);
            }
            Row sample = sheet.createRow(1);
            Object[] values = {
                    "KLMY-JY", "克拉玛依市教育局", 56, 52, 8, 7, 3,
                    LocalDate.now().toString(), "开发演示数据", "请删除示例行后导入"
            };
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Number number) {
                    sample.createCell(i).setCellValue(number.doubleValue());
                } else {
                    sample.createCell(i).setCellValue(String.valueOf(values[i]));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("生成Excel模板失败", exception);
        }
    }

    public List<ImportRow> read(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw bad("EMPTY_WORKBOOK", "Excel中没有可导入数据");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = new HashMap<>();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            for (Cell cell : header) {
                columns.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
            }
            for (String required : HEADERS) {
                if (!columns.containsKey(required)) {
                    throw bad("INVALID_TEMPLATE", "Excel缺少列：" + required);
                }
            }
            List<ImportRow> rows = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || empty(row, formatter)) {
                    continue;
                }
                try {
                    rows.add(new ImportRow(
                            index + 1,
                            text(row, columns.get("机构编码"), formatter),
                            text(row, columns.get("机构名称"), formatter),
                            integer(row, columns.get("核定编制"), formatter, "核定编制"),
                            integer(row, columns.get("实有在编"), formatter, "实有在编"),
                            integer(row, columns.get("领导职数核定"), formatter, "领导职数核定"),
                            integer(row, columns.get("领导职数占用"), formatter, "领导职数占用"),
                            integer(row, columns.get("编外人员"), formatter, "编外人员"),
                            date(row, columns.get("数据日期"), formatter),
                            text(row, columns.get("变更原因"), formatter),
                            text(row, columns.get("备注"), formatter),
                            null
                    ));
                } catch (StaffingException exception) {
                    rows.add(new ImportRow(
                            index + 1,
                            text(row, columns.get("机构编码"), formatter),
                            text(row, columns.get("机构名称"), formatter),
                            0, 0, 0, 0, 0, LocalDate.now().toString(),
                            null, null, exception.getMessage()));
                }
            }
            if (rows.isEmpty()) {
                throw bad("EMPTY_WORKBOOK", "Excel中没有可导入数据");
            }
            return rows;
        } catch (StaffingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw bad("INVALID_XLSX", "无法读取Excel文件：" + exception.getMessage());
        }
    }

    private int integer(Row row, int column, DataFormatter formatter, String label) {
        String value = text(row, column, formatter);
        if (value == null) {
            throw bad("INVALID_ROW", "第" + (row.getRowNum() + 1) + "行" + label + "不能为空");
        }
        try {
            double number = Double.parseDouble(value);
            if (number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return (int) number;
        } catch (NumberFormatException exception) {
            throw bad("INVALID_ROW", "第" + (row.getRowNum() + 1) + "行" + label + "必须是非负整数");
        }
    }

    private String date(Row row, int column, DataFormatter formatter) {
        String value = text(row, column, formatter);
        try {
            return LocalDate.parse(value).toString();
        } catch (NullPointerException | DateTimeParseException exception) {
            throw bad("INVALID_ROW",
                    "第" + (row.getRowNum() + 1) + "行数据日期必须为YYYY-MM-DD");
        }
    }

    private String text(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean empty(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private StaffingException bad(String code, String message) {
        return new StaffingException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record ImportRow(
            int rowNumber, String orgUnitCode, String orgUnitName, int approvedStaffing,
            int actualStaffing, int leadershipApproved, int leadershipOccupied,
            int externalStaff, String dataDate, String changeReason, String remarks,
            String errorMessage
    ) {
    }
}
