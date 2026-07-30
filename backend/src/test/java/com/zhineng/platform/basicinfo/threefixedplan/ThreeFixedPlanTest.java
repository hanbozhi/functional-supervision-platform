package com.zhineng.platform.basicinfo.threefixedplan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.threefixedplan.controller.ThreeFixedExceptionHandler;
import com.zhineng.platform.basicinfo.threefixedplan.controller.ThreeFixedPlanController;
import com.zhineng.platform.basicinfo.threefixedplan.dto.ThreeFixedDtos;
import com.zhineng.platform.basicinfo.threefixedplan.parser.SimpleDocumentParser;
import com.zhineng.platform.basicinfo.threefixedplan.repository.ThreeFixedPlanRepository;
import com.zhineng.platform.basicinfo.threefixedplan.service.ThreeFixedException;
import com.zhineng.platform.basicinfo.threefixedplan.service.ThreeFixedPlanService;
import com.zhineng.platform.basicinfo.threefixedplan.storage.ThreeFixedStorageService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import javax.sql.DataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ThreeFixedPlanTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesManualReviewUploadReparseAndDownloadFlow() throws Exception {
        Fixture f = fixture();
        long orgId = f.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-JY'", Long.class);
        ThreeFixedDtos.Fields fields = new ThreeFixedDtos.Fields(
                "教育局三定方案", "教发〔2026〕1号", "2026-01-01",
                "克拉玛依市教育局", "政府机关", "行政编制", 56,
                "负责全市教育工作", "办公室、基础教育科", "首次录入");

        Map<String, Object> manual = f.service.createManual(
                new ThreeFixedDtos.ManualRequest(orgId, fields));
        long manualId = number(manual.get("id"));
        assertEquals("PENDING_REVIEW", manual.get("workflow_status"));

        fields = new ThreeFixedDtos.Fields(
                "教育局三定方案（修订）", fields.documentNo(), fields.effectiveDate(),
                fields.organizationName(), fields.organizationNature(), fields.staffingType(),
                fields.approvedStaffing(), fields.mainResponsibilities(),
                fields.internalDepartments(), fields.remarks());
        Map<String, Object> updated = f.service.update(manualId,
                new ThreeFixedDtos.UpdateRequest(fields, integer(manual.get("row_version"))));
        Map<String, Object> returned = f.service.review(manualId,
                new ThreeFixedDtos.ReviewRequest("RETURNED", "请补充职责",
                        integer(updated.get("row_version"))));
        Map<String, Object> submitted = f.service.submit(manualId,
                new ThreeFixedDtos.SubmitRequest(integer(returned.get("row_version"))));
        Map<String, Object> confirmed = f.service.review(manualId,
                new ThreeFixedDtos.ReviewRequest("CONFIRMED", "确认入库",
                        integer(submitted.get("row_version"))));
        assertEquals("CONFIRMED", confirmed.get("workflow_status"));

        byte[] workbook = xlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "教育局三定方案.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
        Map<String, Object> uploaded = f.service.upload(orgId, null, file, "SINGLE_UPLOAD");
        assertEquals("SUCCESS", uploaded.get("parse_status"));
        assertEquals(56, integer(uploaded.get("approved_staffing")));
        assertNotNull(uploaded.get("parseResults"));

        Map<String, Object> reparsed = f.service.reparse(number(uploaded.get("id")),
                new ThreeFixedDtos.SubmitRequest(integer(uploaded.get("row_version"))));
        assertEquals("SUCCESS", reparsed.get("parse_status"));
        @SuppressWarnings("unchecked")
        var attachments = (java.util.List<Map<String, Object>>) reparsed.get("attachments");
        var download = f.service.download(number(attachments.get(0).get("id")));
        assertArrayEquals(workbook, Files.readAllBytes(download.path()));

        MockMultipartFile tooLarge = new MockMultipartFile(
                "file", "large.pdf", "application/pdf",
                new byte[(int) ThreeFixedPlanService.MAX_FILE_SIZE + 1]);
        ThreeFixedException exception = assertThrows(
                ThreeFixedException.class,
                () -> f.service.upload(orgId, null, tooLarge, "SINGLE_UPLOAD"));
        assertEquals("FILE_TOO_LARGE", exception.code());
        assertEquals(7, f.jdbc.queryForObject(
                "SELECT count(*) FROM sys_operation_logs WHERE module_code='M1-2'",
                Integer.class));
    }

    @Test
    void controllerListsPlansAndMigrationIsIdempotent() throws Exception {
        Fixture f = fixture();
        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE version='4'", Integer.class));
        assertEquals(1, f.jdbc.queryForObject(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='three_fixed_plans'",
                Integer.class));
        assertEquals(1, f.jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThreeFixedPlanController(f.service, f.objectMapper))
                .setControllerAdvice(new ThreeFixedExceptionHandler()).build();
        mvc.perform(get("/api/basic-info/three-fixed-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void parsesDocxAndPdfAndRejectsOversizedBatch() throws Exception {
        SimpleDocumentParser parser = new SimpleDocumentParser();
        Path docx = tempDirectory.resolve("sample.docx");
        try (XWPFDocument document = new XWPFDocument();
             var output = Files.newOutputStream(docx)) {
            document.createParagraph().createRun().setText("方案名称：DOCX测试方案");
            document.write(output);
        }
        var docxResult = parser.parse(docx, "DOCX",
                List.of(new SimpleDocumentParser.Mapping("ALL", "方案名称", "PLAN_NAME")));
        assertEquals("DOCX测试方案", docxResult.fields().get("PLAN_NAME").value());

        Path pdf = tempDirectory.resolve("sample.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText("PLAN_NAME: PDF Plan");
                content.endText();
            }
            document.save(pdf.toFile());
        }
        var pdfResult = parser.parse(pdf, "PDF",
                List.of(new SimpleDocumentParser.Mapping("PDF", "PLAN_NAME", "PLAN_NAME")));
        assertEquals("PDF Plan", pdfResult.fields().get("PLAN_NAME").value());

        Fixture f = fixture();
        ThreeFixedPlanController controller = new ThreeFixedPlanController(f.service, f.objectMapper);
        MultipartFile fake = mock(MultipartFile.class);
        when(fake.getSize()).thenReturn(3L * 1024 * 1024);
        ThreeFixedException batchError = assertThrows(
                ThreeFixedException.class,
                () -> controller.batchUpload(java.util.Collections.nCopies(20, fake), "[]"));
        assertEquals("BATCH_TOO_LARGE", batchError.code());
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("three-fixed.sqlite");
        DataSource ds = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(ds);
        runner.run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        ObjectMapper mapper = new ObjectMapper();
        ThreeFixedPlanRepository repository = new ThreeFixedPlanRepository(jdbc);
        ThreeFixedPlanService service = new ThreeFixedPlanService(
                repository,
                new ThreeFixedStorageService(tempDirectory.resolve("storage").toString()),
                new SimpleDocumentParser(),
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), mapper);
        return new Fixture(ds, jdbc, service, mapper);
    }

    private byte[] xlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("三定");
            String[][] rows = {
                    {"方案名称", "教育局三定方案2026"},
                    {"机构名称", "克拉玛依市教育局"},
                    {"机构性质", "政府机关"},
                    {"编制类型", "行政编制"},
                    {"核定编制", "56"},
                    {"主要职责", "负责全市教育工作"}
            };
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private int integer(Object value) { return ((Number) value).intValue(); }

    private record Fixture(
            DataSource dataSource, JdbcTemplate jdbc,
            ThreeFixedPlanService service, ObjectMapper objectMapper
    ) {
    }
}
