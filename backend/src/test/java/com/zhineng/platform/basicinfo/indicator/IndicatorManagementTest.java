package com.zhineng.platform.basicinfo.indicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.indicator.controller.IndicatorController;
import com.zhineng.platform.basicinfo.indicator.controller.IndicatorExceptionHandler;
import com.zhineng.platform.basicinfo.indicator.dto.IndicatorDtos;
import com.zhineng.platform.basicinfo.indicator.repository.IndicatorRepository;
import com.zhineng.platform.basicinfo.indicator.service.IndicatorException;
import com.zhineng.platform.basicinfo.indicator.service.IndicatorService;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IndicatorManagementTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completesThreeLevelPublishCopyRulesAndTemplateFlow() throws Exception {
        Fixture f = fixture();
        Map<String, Object> system = f.service.createSystem(new IndicatorDtos.SystemRequest(
                "SYS-2026", "年度指标体系", 2026, "ADMINISTRATIVE", "测试体系"));
        long systemId = number(system.get("id"));
        long versionId = number(system.get("currentVersionId"));

        long firstLeaf = createBranch(f.service, versionId, "A", "履职效能", 50);
        createBranch(f.service, versionId, "B", "机构编制", 50);

        f.service.createRule(new IndicatorDtos.RuleRequest(
                firstLeaf, "THRESHOLD_DEDUCTION", "低于阈值扣分",
                Map.of("threshold", 60, "deduction", 4), "确定性阈值", 1, null));
        f.service.createRule(new IndicatorDtos.RuleRequest(
                firstLeaf, "STEP_SCORE", "分档评分",
                Map.of("steps", List.of(
                        Map.of("min", 90, "scoreRate", 100),
                        Map.of("min", 80, "scoreRate", 80))),
                "阶梯得分", 2, null));
        f.service.createRule(new IndicatorDtos.RuleRequest(
                firstLeaf, "VETO", "红线否决",
                Map.of("condition", "发生超编进人", "result", "不合格"),
                "一票否决", 3, null));

        Map<String, Object> published = f.service.publish(versionId, 0);
        assertEquals("PUBLISHED", published.get("status"));
        assertEquals(2, ((List<?>) published.get("tree")).size());
        assertEquals(3, f.service.rules(versionId, firstLeaf).size());

        Map<String, Object> firstItem = f.repository.items(versionId).get(0);
        assertEquals("VERSION_READ_ONLY", assertThrows(
                IndicatorException.class,
                () -> f.service.updateItem(number(firstItem.get("id")),
                        item(versionId, null, 1, "A2", "修改", 50, 0,
                                integer(firstItem.get("row_version"))))).code());

        Map<String, Object> copied = f.service.copyVersion(
                versionId, new IndicatorDtos.CopyVersionRequest(2027, "2027年度草稿"));
        assertEquals("DRAFT", copied.get("status"));
        assertEquals(6, f.repository.items(number(copied.get("id"))).size());
        assertEquals(3, f.service.rules(number(copied.get("id")), null).size());

        Map<String, Object> template = f.service.createTemplate(
                new IndicatorDtos.TemplateRequest(
                        versionId, "TPL-ADMIN", "行政部门指标模板",
                        "ADMINISTRATIVE", "完整快照"));
        long templateId = number(template.get("id"));
        assertEquals(6L, number(template.get("indicator_count")));
        assertEquals(6, ((List<?>) ((Map<?, ?>) template.get("snapshot")).get("items")).size());

        Map<String, Object> templateCopy = f.service.copyTemplate(
                templateId, new IndicatorDtos.TemplateCopyRequest("TPL-ADMIN-2", "模板副本"));
        assertEquals("模板副本", templateCopy.get("template_name"));

        Map<String, Object> initialized = f.service.initializeFromTemplate(
                templateId, new IndicatorDtos.TemplateInitializeRequest(
                        "SYS-2028", "模板初始化体系", 2028,
                        "ADMINISTRATIVE", "从模板创建"));
        assertEquals(6, f.repository.items(number(initialized.get("id"))).size());
        assertEquals(3, f.service.rules(number(initialized.get("id")), null).size());
        assertTrue(f.jdbc.queryForObject(
                "SELECT count(*) FROM sys_operation_logs WHERE module_code IN ('M1-7','M1-8','M1-9')",
                Integer.class) >= 15);
        assertEquals(1, f.service.systems(null, 2026, "ACTIVE").size());
        assertEquals(systemId, number(f.service.system(systemId).get("id")));
    }

    @Test
    void rejectsInvalidWeightAndMigratesOnlyOnce() throws Exception {
        Fixture f = fixture();
        Map<String, Object> system = f.service.createSystem(new IndicatorDtos.SystemRequest(
                "BAD-WEIGHT", "权重校验体系", 2026, "PUBLIC_INSTITUTION", null));
        long versionId = number(system.get("currentVersionId"));
        createBranch(f.service, versionId, "ONLY", "单一分组", 90);
        IndicatorException error = assertThrows(
                IndicatorException.class, () -> f.service.publish(versionId, 0));
        assertEquals("INVALID_WEIGHT_SUM", error.code());

        new DatabaseMigrationRunner(f.dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1, f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE version='8'", Integer.class));
        assertEquals(1, f.jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
    }

    @Test
    void controllerExposesSubsystemEndpoints() throws Exception {
        Fixture f = fixture();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new IndicatorController(f.service))
                .setControllerAdvice(new IndicatorExceptionHandler()).build();
        mvc.perform(get("/api/basic-info/indicator-systems")).andExpect(status().isOk());
        mvc.perform(get("/api/basic-info/indicator-versions")).andExpect(status().isOk());
        mvc.perform(get("/api/basic-info/indicator-templates")).andExpect(status().isOk());
    }

    private long createBranch(
            IndicatorService service, long versionId, String prefix, String name, double rootWeight
    ) {
        Map<String, Object> root = service.createItem(
                item(versionId, null, 1, prefix, name, rootWeight, 0, null));
        Map<String, Object> second = service.createItem(
                item(versionId, number(root.get("id")), 2, prefix + ".1",
                        name + "分组", 100, 0, null));
        Map<String, Object> leaf = service.createItem(
                item(versionId, number(second.get("id")), 3, prefix + ".1.1",
                        name + "评分项", 100, 10, null));
        return number(leaf.get("id"));
    }

    private IndicatorDtos.ItemRequest item(
            long versionId, Long parentId, int level, String code, String name,
            double weight, double score, Integer rowVersion
    ) {
        return new IndicatorDtos.ItemRequest(
                versionId, parentId, level, code, name, score, weight,
                "COMMON", "材料核验", level, rowVersion);
    }

    private Fixture fixture() throws Exception {
        Path db = tempDirectory.resolve("indicator.sqlite");
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", db.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        IndicatorRepository repository = new IndicatorRepository(jdbc);
        IndicatorService service = new IndicatorService(
                repository,
                new CurrentUserService(new UserRepository(jdbc), "zhang.zhuren"),
                new OperationLogRepository(jdbc), new ObjectMapper(),
                new DataSourceTransactionManager(dataSource));
        return new Fixture(dataSource, jdbc, repository, service);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private record Fixture(
            DataSource dataSource, JdbcTemplate jdbc,
            IndicatorRepository repository, IndicatorService service
    ) {}
}
