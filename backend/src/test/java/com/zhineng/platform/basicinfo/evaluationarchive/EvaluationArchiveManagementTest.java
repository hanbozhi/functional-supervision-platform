package com.zhineng.platform.basicinfo.evaluationarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.evaluationarchive.controller.EvaluationArchiveController;
import com.zhineng.platform.basicinfo.evaluationarchive.controller.EvaluationArchiveExceptionHandler;
import com.zhineng.platform.basicinfo.evaluationarchive.dto.EvaluationArchiveDtos;
import com.zhineng.platform.basicinfo.evaluationarchive.repository.EvaluationArchiveRepository;
import com.zhineng.platform.basicinfo.evaluationarchive.service.EvaluationArchiveException;
import com.zhineng.platform.basicinfo.evaluationarchive.service.EvaluationArchiveService;
import com.zhineng.platform.basicinfo.evaluationarchive.storage.EvaluationArchiveStorageService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvaluationArchiveManagementTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesDraftAttachmentArchiveWithdrawAndReplacementFlow() throws Exception {
        Fixture f = fixture();
        long orgId = businessOrg(f.jdbc);
        Map<String, Object> archive = f.service.create(request(orgId));
        long id = number(archive.get("id"));
        assertEquals("DA-2026-0001", archive.get("archive_no"));

        EvaluationArchiveException noReport = assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.archive(id, new EvaluationArchiveDtos.VersionRequest(0)));
        assertEquals("REPORT_REQUIRED", noReport.code());

        var report = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-demo".getBytes());
        Map<String, Object> first = f.service.upload(id, "REPORT", "初稿", report);
        assertTrue(Files.exists(f.storage.resolve(String.valueOf(first.get("storage_path")))));

        Map<String, Object> archived = f.service.archive(
                id, new EvaluationArchiveDtos.VersionRequest(0));
        assertEquals("ARCHIVED", archived.get("status"));
        assertEquals(25L, number(archived.get("completenessPercent")));

        EvaluationArchiveException readOnly = assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.upload(id, "OTHER", null, report));
        assertEquals("ARCHIVE_READ_ONLY", readOnly.code());

        Map<String, Object> draft = f.service.withdraw(
                id, new EvaluationArchiveDtos.WithdrawRequest(1, "补充材料"));
        assertEquals("DRAFT", draft.get("status"));

        var replacement = new MockMultipartFile(
                "file", "report-v2.pdf", "application/pdf", "%PDF-v2".getBytes());
        Map<String, Object> second = f.service.replace(
                id, number(first.get("id")), "修订版", replacement);
        assertEquals(2L, number(second.get("version_no")));
        assertEquals(1, f.jdbc.queryForObject("""
                SELECT count(*) FROM evaluation_archive_attachments
                WHERE version_group=? AND is_current=1
                """, Integer.class, second.get("version_group")));
        assertEquals(2, f.service.attachments(id, true).size());
        assertTrue(Files.exists(f.storage.resolve(String.valueOf(first.get("storage_path")))));
        f.service.upload(id, "SELF_ASSESSMENT", null, report);
        f.service.upload(id, "RECTIFICATION_LEDGER", null, report);
        f.service.upload(id, "REVIEW_RECORD", null, report);
        assertEquals(100L, number(f.service.detail(id).get("completenessPercent")));
        assertEquals(1L, f.service.page(
                null, null, null, null, null, null,
                "report-v2.pdf", 1, 10).total());
        assertTrue(number(f.jdbc.queryForObject("""
                SELECT count(*) FROM sys_operation_logs WHERE module_code='M1-6'
                """, Integer.class)) >= 8);
    }

    @Test
    void validatesFileTypeAndRejectsStorageTraversal() throws Exception {
        Fixture f = fixture();
        long id = number(f.service.create(request(businessOrg(f.jdbc))).get("id"));
        var illegal = new MockMultipartFile(
                "file", "report.exe", "application/octet-stream", new byte[]{1});
        assertEquals("INVALID_FILE_TYPE", assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.upload(id, "REPORT", null, illegal)).code());

        var report = new MockMultipartFile(
                "file", "report.pdf", "text/html", "%PDF-demo".getBytes());
        Map<String, Object> attachment = f.service.upload(id, "REPORT", null, report);
        assertEquals("application/pdf", attachment.get("content_type"));
        f.jdbc.update("UPDATE sys_attachments SET storage_path='../outside.pdf' WHERE id=?",
                attachment.get("attachment_id"));
        assertEquals("INVALID_STORAGE_PATH", assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.download(number(attachment.get("id")), false)).code());

        var office = new MockMultipartFile(
                "file", "notes.doc", "text/html", "office-demo".getBytes());
        Map<String, Object> officeAttachment = f.service.upload(id, "OTHER", null, office);
        assertEquals("application/msword", f.service.download(
                number(officeAttachment.get("id")), false).contentType());
        assertEquals("PREVIEW_NOT_SUPPORTED", assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.download(number(officeAttachment.get("id")), true)).code());
    }

    @Test
    void cleansNewFileWhenDatabaseTransactionFailsAndRejectsDuplicateArchive() throws Exception {
        Fixture f = fixture();
        long orgId = businessOrg(f.jdbc);
        f.service.create(request(orgId));
        assertEquals("DUPLICATE_ARCHIVE", assertThrows(
                EvaluationArchiveException.class,
                () -> f.service.create(request(orgId))).code());

        f.jdbc.execute("DROP TABLE sys_operation_logs");
        var report = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-demo".getBytes());
        assertThrows(RuntimeException.class,
                () -> f.service.upload(1, "REPORT", null, report));
        try (var files = Files.list(tempDirectory.resolve("storage"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void migrationIsRepeatableAndControllerReturnsRealPageAndStats() throws Exception {
        Fixture f = fixture();
        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        f.service.create(request(businessOrg(f.jdbc)));
        assertEquals(1, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE version='7'", Integer.class));
        assertEquals(1, f.jdbc.queryForObject(
                "PRAGMA foreign_keys", Integer.class));

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new EvaluationArchiveController(f.service))
                .setControllerAdvice(new EvaluationArchiveExceptionHandler()).build();
        mvc.perform(get("/api/basic-info/evaluation-archives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/basic-info/evaluation-archives/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts").value(1));
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("archive.sqlite");
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        EvaluationArchiveStorageService storage =
                new EvaluationArchiveStorageService(tempDirectory.resolve("storage").toString());
        EvaluationArchiveService service = new EvaluationArchiveService(
                new EvaluationArchiveRepository(jdbc), storage,
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), new ObjectMapper(),
                new DataSourceTransactionManager(dataSource));
        return new Fixture(dataSource, jdbc, storage, service);
    }

    private long businessOrg(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT id FROM org_units
                WHERE status='ACTIVE' AND unit_type NOT IN ('ROOT','GROUP')
                ORDER BY id LIMIT 1
                """, Long.class);
    }

    private EvaluationArchiveDtos.SaveRequest request(long orgId) {
        return new EvaluationArchiveDtos.SaveRequest(
                orgId, 2026, "ANNUAL_COMPREHENSIVE", "GOOD",
                "年度综合评估材料", "DEPARTMENT", null);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record Fixture(
            DataSource dataSource,
            JdbcTemplate jdbc,
            EvaluationArchiveStorageService storage,
            EvaluationArchiveService service
    ) {
    }
}
