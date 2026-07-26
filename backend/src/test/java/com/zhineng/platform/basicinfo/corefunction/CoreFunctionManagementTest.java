package com.zhineng.platform.basicinfo.corefunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.corefunction.controller.CoreFunctionController;
import com.zhineng.platform.basicinfo.corefunction.controller.CoreFunctionExceptionHandler;
import com.zhineng.platform.basicinfo.corefunction.dto.CoreFunctionDtos;
import com.zhineng.platform.basicinfo.corefunction.matcher.KeywordDutyMatcher;
import com.zhineng.platform.basicinfo.corefunction.repository.CoreFunctionRepository;
import com.zhineng.platform.basicinfo.corefunction.service.CoreFunctionException;
import com.zhineng.platform.basicinfo.corefunction.service.CoreFunctionService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoreFunctionManagementTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesManualDutyMatchingReviewAndRematchHistory() throws Exception {
        Fixture f = fixture();
        long orgId = f.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-FG'", Long.class);
        long functionId = number(f.service.createFunction(new CoreFunctionDtos.FunctionRequest(
                orgId, "ECONOMIC", "经济发展", "经济管理", "负责发展改革职责", 10, null)), "id");

        Map<String, Object> energyDuty = f.service.createDuty(new CoreFunctionDtos.DutyRequest(
                functionId, "负责能源项目管理", "能源,项目,能源,工", 10, null));
        f.service.createDuty(new CoreFunctionDtos.DutyRequest(
                functionId, "负责旅游市场管理", "旅游,市场", 20, null));
        f.service.createDuty(new CoreFunctionDtos.DutyRequest(
                functionId, "负责开展相关工作", "", 30, null));

        f.service.saveMappings(orgId, new CoreFunctionDtos.MappingRequest(
                List.of("克拉玛依市发展和改革委员会")));
        Map<String, Object> firstRun = f.service.runMatch(
                orgId, new CoreFunctionDtos.MatchRequest(50));
        long runId = number(firstRun, "id");
        List<Map<String, Object>> results = f.service.results(runId, null, null);
        assertTrue(results.stream().anyMatch(r -> "MATCHED".equals(r.get("result_type"))));
        assertTrue(results.stream().anyMatch(r -> "DUTY_MISSING".equals(r.get("result_type"))));
        assertTrue(results.stream().anyMatch(
                r -> "UNAPPROVED_NEW_DUTY".equals(r.get("result_type"))));

        Map<String, Object> matched = results.stream()
                .filter(r -> "MATCHED".equals(r.get("result_type"))).findFirst().orElseThrow();
        Map<String, Object> confirmed = f.service.review(
                number(matched, "id"), new CoreFunctionDtos.ReviewRequest(
                        "MATCHED", numberObject(matched.get("duty_item_id")),
                        numberObject(matched.get("rights_item_id")), 95.0,
                        "CONFIRMED", "人工确认", integer(matched, "version_no")));
        assertEquals("CONFIRMED", confirmed.get("review_status"));

        Map<String, Object> missing = results.stream()
                .filter(r -> "DUTY_MISSING".equals(r.get("result_type"))).findFirst().orElseThrow();
        long alternativeRight = f.jdbc.queryForObject(
                "SELECT id FROM rights_items WHERE item_name='固定资产投资项目审批'", Long.class);
        Map<String, Object> adjusted = f.service.review(
                number(missing, "id"), new CoreFunctionDtos.ReviewRequest(
                        "MATCHED", numberObject(missing.get("duty_item_id")), alternativeRight,
                        70.0, "ADJUSTED", "人工调整为相关事项",
                        integer(missing, "version_no")));
        assertEquals("ADJUSTED", adjusted.get("review_status"));

        CoreFunctionException invalid = assertThrows(
                CoreFunctionException.class,
                () -> f.service.createManualResult(runId, new CoreFunctionDtos.ReviewRequest(
                        "MATCHED", number(energyDuty, "id"), null, 80.0,
                        "ADJUSTED", "缺少权责侧", null)));
        assertEquals("INCOMPLETE_MATCH_RESULT", invalid.code());

        f.service.runMatch(orgId, new CoreFunctionDtos.MatchRequest(50));
        assertEquals(2, f.service.runs(orgId).size());
        assertTrue(f.jdbc.queryForObject(
                "SELECT count(*) FROM sys_operation_logs WHERE module_code='M1-5'",
                Integer.class) >= 8);
    }

    @Test
    void previewsConfirmedThreeFixedAndRightsReimportIsNotBlocked() throws Exception {
        Fixture f = fixture();
        long orgId = f.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-CZ'", Long.class);
        long userId = f.jdbc.queryForObject(
                "SELECT id FROM sys_users WHERE username='zhang.zhuren'", Long.class);
        f.jdbc.update("INSERT INTO three_fixed_plans(org_unit_id,created_by,updated_by) VALUES(?,?,?)",
                orgId, userId, userId);
        long planId = f.jdbc.queryForObject(
                "SELECT id FROM three_fixed_plans WHERE org_unit_id=?", Long.class, orgId);
        f.jdbc.update("""
                INSERT INTO three_fixed_plan_versions(
                  plan_id,version_no,version_label,source_type,workflow_status,parse_status,
                  plan_name,main_responsibilities,created_by,updated_by
                ) VALUES(?,1,'V1','MANUAL','CONFIRMED','NOT_APPLICABLE',
                  '财政局三定','一、负责预算管理；二、承担财政监督。',?,?)
                """, planId, userId, userId);
        long versionId = f.jdbc.queryForObject(
                "SELECT id FROM three_fixed_plan_versions WHERE plan_id=?", Long.class, planId);
        f.jdbc.update("UPDATE three_fixed_plans SET current_version_id=? WHERE id=?",
                versionId, planId);

        Map<String, Object> preview = f.service.dutyPreview(orgId);
        assertEquals(true, preview.get("available"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) preview.get("items");
        assertEquals(2, items.size());

        long functionId = number(f.service.createFunction(new CoreFunctionDtos.FunctionRequest(
                orgId, "FINANCE", "财政管理", "财政", null, 10, null)), "id");
        List<CoreFunctionDtos.DutyCandidate> candidates = items.stream().map(item ->
                new CoreFunctionDtos.DutyCandidate(
                        functionId, String.valueOf(item.get("dutyContent")),
                        String.valueOf(item.get("keywords")),
                        String.valueOf(item.get("sourceSnippet")),
                        ((Number) item.get("sortOrder")).intValue())).toList();
        assertEquals(2, f.service.importDuties(
                orgId, new CoreFunctionDtos.DutyImportRequest(versionId, candidates)).size());

        // m1-5结果没有指向rights_items的外键，权责清单重导入可以清空自身数据。
        f.jdbc.update("DELETE FROM rights_items");
        assertEquals(0, f.jdbc.queryForObject("SELECT count(*) FROM rights_items", Integer.class));
        assertEquals(6, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations", Integer.class));
        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(6, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations", Integer.class));
        assertEquals(1, f.jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
    }

    @Test
    void matcherRulesAndControllerFailuresAreClear() throws Exception {
        Fixture f = fixture();
        KeywordDutyMatcher matcher = new KeywordDutyMatcher();
        assertEquals(List.of("能源项目"), matcher.generateKeywords("负责能源项目管理、相关工作"));
        assertEquals(List.of("能源", "项目"), matcher.parseKeywords("能源,项目,能源,工"));
        assertEquals(50.0, matcher.score(
                List.of("能源", "项目"), "能源监督事项").score());

        long orgId = f.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-JY'", Long.class);
        long functionId = number(f.service.createFunction(new CoreFunctionDtos.FunctionRequest(
                orgId, "EDU", "教育管理", "教育", null, 10, null)), "id");
        f.service.createDuty(new CoreFunctionDtos.DutyRequest(
                functionId, "负责教育管理", "教育", 10, null));
        assertEquals("NO_ACTIVE_MAPPINGS", assertThrows(
                CoreFunctionException.class,
                () -> f.service.runMatch(orgId, new CoreFunctionDtos.MatchRequest(50))).code());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CoreFunctionController(f.service))
                .setControllerAdvice(new CoreFunctionExceptionHandler()).build();
        mvc.perform(get("/api/basic-info/core-functions").param("orgId", String.valueOf(orgId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/basic-info/core-functions/stats")
                        .param("orgId", String.valueOf(orgId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeFunctions").value(1));
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("core-function.sqlite");
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createRightsTables(jdbc);
        CoreFunctionService service = new CoreFunctionService(
                new CoreFunctionRepository(jdbc), new KeywordDutyMatcher(),
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), new ObjectMapper());
        return new Fixture(dataSource, jdbc, service);
    }

    private void createRightsTables(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE source_files(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,file_name TEXT NOT NULL,
                  imported_at TEXT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE rights_items(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,source_file_id INTEGER NOT NULL,
                  department_guess TEXT,item_name TEXT,subitem_name TEXT,
                  department_duty TEXT,responsibility_content TEXT,
                  FOREIGN KEY(source_file_id) REFERENCES source_files(id)
                )
                """);
        jdbc.update("INSERT INTO source_files(file_name,imported_at) VALUES('权责清单.xlsx','2026-07-26')");
        long sourceId = jdbc.queryForObject("SELECT id FROM source_files", Long.class);
        jdbc.update("""
                INSERT INTO rights_items(
                  source_file_id,department_guess,item_name,subitem_name,
                  department_duty,responsibility_content
                ) VALUES
                  (?,?,?,?,?,?),(?,?,?,?,?,?),(?,?,?,?,?,?)
                """,
                sourceId, "克拉玛依市发展和改革委员会", "能源项目核准", null,
                "负责能源项目管理", "能源项目审查",
                sourceId, "克拉玛依市发展和改革委员会", "固定资产投资项目审批", null,
                "负责投资项目管理", "项目审批",
                sourceId, "克拉玛依市发展和改革委员会", "环境监测事项", null,
                "负责环境监测", "污染防治");
    }

    private long number(Map<String, Object> value, String key) {
        return ((Number) value.get(key)).longValue();
    }

    private int integer(Map<String, Object> value, String key) {
        return ((Number) value.get(key)).intValue();
    }

    private Long numberObject(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record Fixture(DataSource dataSource, JdbcTemplate jdbc,
                           CoreFunctionService service) {
    }
}
