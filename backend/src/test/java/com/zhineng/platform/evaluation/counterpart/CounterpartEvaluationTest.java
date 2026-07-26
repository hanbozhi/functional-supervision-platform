package com.zhineng.platform.evaluation.counterpart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import com.zhineng.platform.evaluation.counterpart.controller.CounterpartController;
import com.zhineng.platform.evaluation.counterpart.controller.CounterpartExceptionHandler;
import com.zhineng.platform.evaluation.counterpart.dto.CounterpartDtos;
import com.zhineng.platform.evaluation.counterpart.repository.CounterpartRepository;
import com.zhineng.platform.evaluation.counterpart.service.CounterpartException;
import com.zhineng.platform.evaluation.counterpart.service.CounterpartService;
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

class CounterpartEvaluationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesRelationQuestionnaireAnonymousAndReviewFlow() throws Exception {
        Fixture f = fixture();
        List<Map<String, Object>> orgs = f.service.organizations();
        long subject = number(orgs.get(0).get("id"));
        long target = number(orgs.get(1).get("id"));

        seedConfirmedDuty(f.jdbc, subject, "重大项目资金协调和联合监督");
        seedConfirmedDuty(f.jdbc, target, "重大项目资金统筹和部门协作");
        assertTrue(number(f.service.generateSuggestions().get("created")) >= 1);
        Map<String, Object> suggestion = f.service.relations("SUGGESTED", null).get(0);
        Map<String, Object> confirmed = f.service.verifyRelation(
                number(suggestion.get("id")),
                new CounterpartDtos.VerifyRequest(
                        "CONFIRMED", "职责关键词一致，确认纳入评价",
                        integer(suggestion.get("row_version"))));
        assertEquals("CONFIRMED", confirmed.get("status"));

        Map<String, Object> questionnaire = f.service.createQuestionnaire(
                new CounterpartDtos.QuestionnaireRequest(
                        "CP-2026-01", "2026年度对口部门评价", 2026,
                        "2026-12-31T23:59:59+08:00", "规则测试", null,
                        List.of(new CounterpartDtos.DimensionInput(
                                "COOP", "协同成效", 1)),
                        List.of(
                                new CounterpartDtos.QuestionInput(
                                        null, "COOP", "Q1", "协作响应是否及时",
                                        "SCORE", true, null, 1),
                                new CounterpartDtos.QuestionInput(
                                        null, "COOP", "Q2", "请输入改进意见",
                                        "TEXT", false, null, 2))));
        long questionnaireId = number(questionnaire.get("id"));
        List<Map<String, Object>> recipients = f.service.addRecipients(
                questionnaireId,
                new CounterpartDtos.RecipientRequest(
                        List.of(number(confirmed.get("id")))));
        assertEquals(1, recipients.size());
        f.service.publishQuestionnaire(questionnaireId);
        assertEquals(1, f.service.simulatePush(questionnaireId).size());

        Map<String, Object> recipient = f.service.recipients(questionnaireId).get(0);
        String token = recipient.get("fill_token").toString();
        Map<String, Object> fill = f.service.tokenQuestionnaire(token);
        assertEquals(recipient.get("anonymous_code"), fill.get("anonymousCode"));
        List<?> questions = (List<?>) fill.get("questions");
        long scoreQuestionId = number(((Map<?, ?>) questions.get(0)).get("id"));
        f.service.submit(token, new CounterpartDtos.SubmitRequest(
                List.of(new CounterpartDtos.AnswerInput(
                        scoreQuestionId, 1, null)), 8));
        CounterpartException duplicate = assertThrows(
                CounterpartException.class,
                () -> f.service.submit(token, new CounterpartDtos.SubmitRequest(
                        List.of(new CounterpartDtos.AnswerInput(
                                scoreQuestionId, 5, null)), 10)));
        assertEquals("ALREADY_SUBMITTED", duplicate.code());

        Map<String, Object> statistics = f.service.statistics(questionnaireId);
        assertFalse(((List<?>) statistics.get("questions")).isEmpty());
        Map<String, Object> detection = f.service.detectAnomalies(questionnaireId);
        List<?> cases = (List<?>) detection.get("cases");
        assertFalse(cases.isEmpty());
        Map<?, ?> anomaly = (Map<?, ?>) cases.get(0);
        long caseId = number(anomaly.get("id"));
        Map<String, Object> assigned = f.service.assign(
                caseId, new CounterpartDtos.AssignRequest(
                        null, "分派张主任复核", integer(anomaly.get("row_version"))));
        Map<String, Object> reviewed = f.service.review(
                caseId, new CounterpartDtos.ReviewRequest(
                        "ACCEPT", "端点低分属实，采纳预警",
                        integer(assigned.get("row_version"))));
        assertEquals("ACCEPTED", reviewed.get("status"));
        assertTrue(((List<?>) reviewed.get("reviews")).size() >= 2);

        Map<String, Object> restored = f.service.restore(number(recipient.get("id")));
        assertEquals(target, number(restored.get("target_org_id")));
        assertTrue(f.jdbc.queryForObject("""
                SELECT count(*) FROM sys_operation_logs
                WHERE module_code IN ('M2-1','M2-2','M2-3','M2-4')
                """, Integer.class) >= 10);

        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE version='9'", Integer.class));
        assertEquals(1, f.jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
    }

    @Test
    void validatesOrganizationsAndControllerQueries() throws Exception {
        Fixture f = fixture();
        List<Map<String, Object>> orgs = f.service.organizations();
        long id = number(orgs.get(0).get("id"));
        CounterpartException sameOrg = assertThrows(
                CounterpartException.class,
                () -> f.service.createRelation(new CounterpartDtos.RelationRequest(
                        id, id, "自我协作", "MANUAL", 100.0, null)));
        assertEquals("SAME_ORGANIZATION", sameOrg.code());

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new CounterpartController(f.service))
                .setControllerAdvice(new CounterpartExceptionHandler()).build();
        mvc.perform(get("/api/counterpart-evaluation/organizations"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterpart-evaluation/relations"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterpart-evaluation/questionnaires"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterpart-evaluation/anomaly-runs"))
                .andExpect(status().isOk());
    }

    private void seedConfirmedDuty(JdbcTemplate jdbc, long orgId, String duty) {
        long userId = jdbc.queryForObject(
                "SELECT id FROM sys_users WHERE username='zhang.zhuren'", Long.class);
        jdbc.update("""
                INSERT INTO three_fixed_plans(org_unit_id,created_by,updated_by)
                VALUES(?,?,?)
                """, orgId, userId, userId);
        long planId = jdbc.queryForObject(
                "SELECT id FROM three_fixed_plans WHERE org_unit_id=?", Long.class, orgId);
        jdbc.update("""
                INSERT INTO three_fixed_plan_versions(
                  plan_id,version_no,version_label,source_type,workflow_status,
                  parse_status,plan_name,main_responsibilities,
                  created_by,updated_by,reviewed_by,reviewed_at
                ) VALUES(?,1,'V1','MANUAL','CONFIRMED','NOT_APPLICABLE',
                  '测试三定方案',?,?,?,?,strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                """, planId, duty, userId, userId, userId);
        long versionId = jdbc.queryForObject("""
                SELECT id FROM three_fixed_plan_versions WHERE plan_id=?
                """, Long.class, planId);
        jdbc.update("UPDATE three_fixed_plans SET current_version_id=? WHERE id=?",
                versionId, planId);
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("counterpart.sqlite");
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        CounterpartRepository repository = new CounterpartRepository(jdbc);
        CounterpartService service = new CounterpartService(
                repository,
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), new ObjectMapper());
        return new Fixture(dataSource, jdbc, service);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private record Fixture(
            DataSource dataSource, JdbcTemplate jdbc, CounterpartService service
    ) {
    }
}
