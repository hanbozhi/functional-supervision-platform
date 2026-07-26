package com.zhineng.platform.basicinfo.orgunit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.orgunit.controller.OrgUnitController;
import com.zhineng.platform.basicinfo.orgunit.controller.OrgUnitExceptionHandler;
import com.zhineng.platform.basicinfo.orgunit.dto.OrgUnitDtos;
import com.zhineng.platform.basicinfo.orgunit.repository.OrgUnitRepository;
import com.zhineng.platform.basicinfo.orgunit.service.OrgUnitBusinessException;
import com.zhineng.platform.basicinfo.orgunit.service.OrgUnitService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrgUnitManagementTest {
    @TempDir
    Path tempDirectory;

    @Test
    void serviceCompletesCreateEditVerifyAndStatusFlow() throws Exception {
        Fixture fixture = fixture("service.sqlite");
        OrgUnitService service = fixture.service;

        assertEquals(5, service.stats(null, "SUBTREE").totalUnits());
        long governmentGroupId = fixture.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-GOV'", Long.class);

        var created = service.create(new OrgUnitDtos.SaveRequest(
                governmentGroupId, "KLMY-SY", "克拉玛依市演示事业中心", "演示事业中心",
                "PUBLIC_INSTITUTION", "CITY", "PUBLIC_INSTITUTION",
                12, 30, null));
        assertEquals("PENDING", created.verificationStatus());
        assertEquals("张主任", created.createdByName());
        assertEquals(6, service.stats(null, "SUBTREE").totalUnits());
        assertEquals(1, service.stats(null, "SUBTREE").publicInstitutions());

        var verified = service.verify(created.id(), new OrgUnitDtos.VerificationRequest(
                "VERIFIED", "数据准确", created.versionNo()));
        assertEquals("VERIFIED", verified.verificationStatus());
        assertEquals(1, verified.verificationHistory().size());

        var sorted = service.update(created.id(), new OrgUnitDtos.SaveRequest(
                governmentGroupId, created.unitCode(), created.unitName(), created.unitShortName(),
                created.unitType(), created.unitLevel(), created.organizationNature(),
                created.approvedStaffing(), 31, verified.versionNo()));
        assertEquals("VERIFIED", sorted.verificationStatus());

        var renamed = service.update(created.id(), new OrgUnitDtos.SaveRequest(
                governmentGroupId, created.unitCode(), "克拉玛依市演示事业服务中心",
                created.unitShortName(), created.unitType(), created.unitLevel(),
                created.organizationNature(), created.approvedStaffing(), 31, sorted.versionNo()));
        assertEquals("PENDING", renamed.verificationStatus());

        var inactive = service.updateStatus(created.id(),
                new OrgUnitDtos.StatusRequest("INACTIVE", renamed.versionNo()));
        assertEquals("INACTIVE", inactive.status());
        var active = service.updateStatus(created.id(),
                new OrgUnitDtos.StatusRequest("ACTIVE", inactive.versionNo()));
        assertEquals("ACTIVE", active.status());
        assertEquals(6, fixture.jdbc.queryForObject(
                "SELECT count(*) FROM sys_operation_logs WHERE module_code='M1-1'",
                Integer.class));

        OrgUnitBusinessException duplicate = assertThrows(
                OrgUnitBusinessException.class,
                () -> service.create(new OrgUnitDtos.SaveRequest(
                        governmentGroupId, "klmy-sy", "重复编码", null,
                        "OTHER", "CITY", "OTHER", null, 99, null)));
        assertEquals("DUPLICATE_UNIT_CODE", duplicate.code());

        long rootId = fixture.jdbc.queryForObject(
                "SELECT id FROM org_units WHERE unit_code='KLMY-ROOT'", Long.class);
        var root = service.detail(rootId);
        OrgUnitBusinessException cycle = assertThrows(
                OrgUnitBusinessException.class,
                () -> service.update(rootId, new OrgUnitDtos.SaveRequest(
                        governmentGroupId, root.unitCode(), root.unitName(), root.unitShortName(),
                        "GROUP", root.unitLevel(), root.organizationNature(),
                        root.approvedStaffing(), root.sortOrder(), root.versionNo())));
        assertEquals("PARENT_CYCLE", cycle.code());
    }

    @Test
    void controllerReturnsTreeAndValidationErrors() throws Exception {
        Fixture fixture = fixture("controller.sqlite");
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new OrgUnitController(fixture.service))
                .setControllerAdvice(new OrgUnitExceptionHandler())
                .build();

        mvc.perform(get("/api/basic-info/org-units/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitCode").value("KLMY-ROOT"))
                .andExpect(jsonPath("$[0].children").isArray());

        String invalid = fixture.objectMapper.writeValueAsString(
                new OrgUnitDtos.SaveRequest(null, "bad code", "", null,
                        "OTHER", "CITY", "OTHER", null, 0, null));
        mvc.perform(post("/api/basic-info/org-units")
                        .contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());

        assertTrue(fixture.repository.findTreeRows(true).size() >= 7);
    }

    private Fixture fixture(String fileName) throws Exception {
        Path database = tempDirectory.resolve(fileName);
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path", database.toString()));
        new DatabaseMigrationRunner(dataSource)
                .run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        OrgUnitRepository repository = new OrgUnitRepository(jdbc);
        ObjectMapper objectMapper = new ObjectMapper();
        CurrentUserService currentUser = new CurrentUserService(
                new UserRepository(jdbc), "zhang.zhuren");
        OrgUnitService service = new OrgUnitService(
                repository, new OperationLogRepository(jdbc), currentUser, objectMapper);
        return new Fixture(jdbc, repository, service, objectMapper);
    }

    private record Fixture(
            JdbcTemplate jdbc,
            OrgUnitRepository repository,
            OrgUnitService service,
            ObjectMapper objectMapper
    ) {
    }
}
