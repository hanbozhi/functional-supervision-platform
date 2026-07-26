package com.zhineng.platform.basicinfo.staffing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.staffing.controller.StaffingController;
import com.zhineng.platform.basicinfo.staffing.controller.StaffingExceptionHandler;
import com.zhineng.platform.basicinfo.staffing.dto.StaffingDtos;
import com.zhineng.platform.basicinfo.staffing.excel.StaffingExcelService;
import com.zhineng.platform.basicinfo.staffing.repository.StaffingRepository;
import com.zhineng.platform.basicinfo.staffing.service.StaffingException;
import com.zhineng.platform.basicinfo.staffing.service.StaffingService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaffingManagementTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesCreateUpdateBatchHistoryAndMigrationFlow() throws Exception {
        Fixture f = fixture();
        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(5, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations", Integer.class));
        assertEquals(1, f.jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));

        long orgId = f.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-JY'", Long.class);
        f.jdbc.update("""
                UPDATE org_units SET verification_status='VERIFIED', verified_at='2026-01-01'
                WHERE id=?
                """, orgId);
        var created = f.service.create(request(orgId, 56, 52, 8, 7, 3, null));
        assertEquals(56, created.approvedStaffing());
        assertEquals("PENDING", f.jdbc.queryForObject(
                "SELECT verification_status FROM org_units WHERE id=?", String.class, orgId));

        var updated = f.service.update(created.id(),
                request(orgId, 60, 62, 8, 9, 4, created.versionNo()));
        assertEquals(true, updated.overstaffed());
        assertEquals(true, updated.leadershipOverOccupied());

        var batched = f.service.batch(new StaffingDtos.BatchRequest(
                List.of(new StaffingDtos.BatchItem(
                        updated.id(), 61, 59, 9, 8, 2, updated.versionNo(), "批量备注")),
                LocalDate.now().toString(), "批量核对"));
        assertEquals(61, batched.get(0).approvedStaffing());
        assertEquals(3, f.jdbc.queryForObject(
                "SELECT count(*) FROM staffing_change_logs", Integer.class));
        assertEquals(3, f.jdbc.queryForObject(
                "SELECT count(*) FROM sys_operation_logs WHERE module_code='M1-4'",
                Integer.class));

        var stale = request(orgId, 61, 59, 9, 8, 2, updated.versionNo());
        StaffingException exception = assertThrows(
                StaffingException.class, () -> f.service.update(created.id(), stale));
        assertEquals("STALE_VERSION", exception.code());
    }

    @Test
    void importsValidRowsAndKeepsInvalidRowsAsErrors() throws Exception {
        Fixture f = fixture();
        byte[] content = workbook(new Object[][]{
                {"KLMY-WS", "克拉玛依市卫生健康委员会", 40, 41, 7, 8, 2,
                        "2026-07-26", "Excel导入", ""},
                {"NOT-FOUND", "不存在机构", 10, 8, 2, 1, 0,
                        "2026-07-26", "Excel导入", ""},
                {"KLMY-CZ", "克拉玛依市财政局", "错误数字", 8, 2, 1, 0,
                        "2026-07-26", "Excel导入", ""}
        });
        var file = new MockMultipartFile(
                "file", "台账.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        var result = f.service.importFile(file);
        assertEquals(3, result.totalRows());
        assertEquals(1, result.successRows());
        assertEquals(2, result.failedRows());
        assertEquals("PARTIAL", result.status());
        assertEquals(2, f.jdbc.queryForObject(
                "SELECT count(*) FROM staffing_import_errors", Integer.class));

        MockMultipartFile wrong = new MockMultipartFile(
                "file", "台账.csv", "text/csv", new byte[]{1});
        assertEquals("INVALID_FILE_TYPE", assertThrows(
                StaffingException.class, () -> f.service.importFile(wrong)).code());
    }

    @Test
    void controllerReturnsPageStatsAndTemplate() throws Exception {
        Fixture f = fixture();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StaffingController(f.service))
                .setControllerAdvice(new StaffingExceptionHandler()).build();
        mvc.perform(get("/api/basic-info/staffing-ledgers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());
        mvc.perform(get("/api/basic-info/staffing-ledgers/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").isNumber());
        mvc.perform(get("/api/basic-info/staffing-ledgers/import-template"))
                .andExpect(status().isOk());
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("staffing.sqlite");
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        StaffingService service = new StaffingService(
                new StaffingRepository(jdbc), new StaffingExcelService(),
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), mapper,
                new DataSourceTransactionManager(dataSource));
        return new Fixture(dataSource, jdbc, service);
    }

    private StaffingDtos.SaveRequest request(
            long orgId, int approved, int actual, int leadApproved,
            int leadOccupied, int external, Integer version
    ) {
        return new StaffingDtos.SaveRequest(
                orgId, approved, actual, leadApproved, leadOccupied, external,
                "2026-07-26", "测试变更", "测试备注", version);
    }

    private byte[] workbook(Object[][] data) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("编制人员台账");
            String[] headers = {
                    "机构编码", "机构名称", "核定编制", "实有在编", "领导职数核定",
                    "领导职数占用", "编外人员", "数据日期", "变更原因", "备注"
            };
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < data[rowIndex].length; column++) {
                    Object value = data[rowIndex][column];
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(column).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private record Fixture(DataSource dataSource, JdbcTemplate jdbc, StaffingService service) {
    }
}
