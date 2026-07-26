package com.zhineng.platform.evaluation.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.database.DatabaseMigrationRunner;
import com.zhineng.platform.common.database.SQLiteDataSourceConfig;
import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import com.zhineng.platform.evaluation.internal.controller.InternalEvaluationController;
import com.zhineng.platform.evaluation.internal.controller.InternalEvaluationExceptionHandler;
import com.zhineng.platform.evaluation.internal.dto.InternalEvaluationDtos;
import com.zhineng.platform.evaluation.internal.repository.InternalEvaluationRepository;
import com.zhineng.platform.evaluation.internal.service.InternalEvaluationService;
import com.zhineng.platform.evaluation.internal.storage.InternalEvaluationStorageService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalEvaluationTest {
    @TempDir Path tempDirectory;

    @Test
    void completesTaskSnapshotScoreMaterialReturnAndConfirmFlow() throws Exception {
        Fixture f=fixture();
        long versionId=seedPublishedIndicators(f.jdbc);
        long orgId=number(f.service.organizations().get(0).get("id"));
        long userId=number(f.service.users().get(0).get("id"));
        Map<String,Object> task=f.service.createTask(new InternalEvaluationDtos.TaskRequest(
                "IE-2026-01","2026年度内部评估",2026,"ANNUAL",
                "2026-01-01","2026-12-31","测试任务",versionId,
                List.of(orgId),userId,userId));
        long taskId=number(task.get("id"));
        Map<String,Object> published=f.service.publish(taskId);
        assertEquals("PUBLISHED",published.get("status"));
        assertEquals(1,((List<?>)published.get("snapshots")).size());

        f.jdbc.update("UPDATE indicator_items SET indicator_name='后续已变化' WHERE version_id=? AND indicator_level=3",versionId);
        assertEquals("履职成效",f.service.task(taskId).get("snapshots") instanceof List<?> snapshots
                ? ((Map<?,?>)snapshots.get(0)).get("indicator_name"):null);

        Map<?,?> org=(Map<?,?>)((List<?>)published.get("organizations")).get(0);
        long sheetId=number(org.get("score_sheet_id"));
        Map<String,Object> sheet=f.service.scoreSheet(sheetId);
        Map<?,?> entry=(Map<?,?>)((List<?>)sheet.get("entries")).get(0);
        long entryId=number(entry.get("id"));
        sheet=f.service.saveScores(sheetId,new InternalEvaluationDtos.SaveScoresRequest(
                List.of(new InternalEvaluationDtos.ScoreInput(
                        entryId,8.5,"DEDUCTION","材料迟报","首次评分",false,
                        integer(entry.get("row_version")))),integer(sheet.get("row_version"))));
        assertEquals(8.5,((Number)sheet.get("total_score")).doubleValue());

        MockMultipartFile file=new MockMultipartFile(
                "file","evidence.pdf","application/pdf","evidence".getBytes());
        Map<String,Object> attachment=f.service.upload(entryId,"评分依据",file);
        assertTrue(java.nio.file.Files.exists(f.service.download(
                number(attachment.get("id")),true).path()));

        sheet=f.service.submit(sheetId,new InternalEvaluationDtos.ReviewRequest(
                null,"提交复核",integer(sheet.get("row_version"))));
        sheet=f.service.review(sheetId,new InternalEvaluationDtos.ReviewRequest(
                "RETURN","补充扣分说明",integer(sheet.get("row_version"))));
        assertEquals("RETURNED",sheet.get("status"));

        entry=(Map<?,?>)((List<?>)sheet.get("entries")).get(0);
        sheet=f.service.saveScores(sheetId,new InternalEvaluationDtos.SaveScoresRequest(
                List.of(new InternalEvaluationDtos.ScoreInput(
                        entryId,9.0,"DEDUCTION","已补充材料","重新评分",false,
                        integer(entry.get("row_version")))),integer(sheet.get("row_version"))));
        sheet=f.service.submit(sheetId,new InternalEvaluationDtos.ReviewRequest(
                null,"重新提交",integer(sheet.get("row_version"))));
        sheet=f.service.review(sheetId,new InternalEvaluationDtos.ReviewRequest(
                "CONFIRM","复核通过",integer(sheet.get("row_version"))));
        assertEquals("CONFIRMED",sheet.get("status"));
        assertEquals("COMPLETED",f.service.task(taskId).get("status"));
        assertEquals(4,((List<?>)sheet.get("reviews")).size());
        assertTrue(f.jdbc.queryForObject("""
                SELECT count(*) FROM sys_operation_logs WHERE module_code IN ('M2-5','M2-6')
                """,Integer.class)>=8);

        Map<String,Object> copied=f.service.copyTask(taskId,new InternalEvaluationDtos.CopyRequest(
                "IE-2027-01","2027年度内部评估",2027));
        assertEquals("DRAFT",copied.get("status"));
        assertEquals(1,((List<?>)copied.get("organizations")).size());

        new DatabaseMigrationRunner(f.dataSource).run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1,f.jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE version='10'",Integer.class));
        assertEquals(1,f.jdbc.queryForObject("PRAGMA foreign_keys",Integer.class));
    }

    @Test
    void exposesMainQueryEndpoints() throws Exception {
        Fixture f=fixture();
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new InternalEvaluationController(f.service))
                .setControllerAdvice(new InternalEvaluationExceptionHandler()).build();
        mvc.perform(get("/api/internal-evaluations/tasks")).andExpect(status().isOk());
        mvc.perform(get("/api/internal-evaluations/options/indicator-versions")).andExpect(status().isOk());
        mvc.perform(get("/api/internal-evaluations/options/organizations")).andExpect(status().isOk());
        mvc.perform(get("/api/internal-evaluations/options/users")).andExpect(status().isOk());
    }

    private long seedPublishedIndicators(JdbcTemplate jdbc){
        long user=jdbc.queryForObject("SELECT id FROM sys_users LIMIT 1",Long.class);
        jdbc.update("INSERT INTO indicator_systems(system_code,system_name,applicable_org_type,created_by,updated_by) VALUES('IE-SYS','内部评估指标','ALL',?,?)",user,user);
        long system=jdbc.queryForObject("SELECT id FROM indicator_systems WHERE system_code='IE-SYS'",Long.class);
        jdbc.update("INSERT INTO indicator_versions(system_id,evaluation_year,version_no,version_name,status,created_by,updated_by) VALUES(?,2026,1,'2026已发布版','PUBLISHED',?,?)",system,user,user);
        long version=jdbc.queryForObject("SELECT id FROM indicator_versions WHERE system_id=?",Long.class,system);
        jdbc.update("INSERT INTO indicator_items(version_id,indicator_level,indicator_code,indicator_name,weight,created_by,updated_by) VALUES(?,1,'A','履职能力',100,?,?)",version,user,user);
        long l1=jdbc.queryForObject("SELECT id FROM indicator_items WHERE indicator_code='A'",Long.class);
        jdbc.update("INSERT INTO indicator_items(version_id,parent_id,parent_version_id,parent_level,indicator_level,indicator_code,indicator_name,weight,created_by,updated_by) VALUES(?,?,?,?,2,'A.1','履职结果',100,?,?)",version,l1,version,1,user,user);
        long l2=jdbc.queryForObject("SELECT id FROM indicator_items WHERE indicator_code='A.1'",Long.class);
        jdbc.update("INSERT INTO indicator_items(version_id,parent_id,parent_version_id,parent_level,indicator_level,indicator_code,indicator_name,standard_score,weight,evaluation_method,created_by,updated_by) VALUES(?,?,?,?,3,'A.1.1','履职成效',10,100,'材料核验',?,?)",version,l2,version,2,user,user);
        long l3=jdbc.queryForObject("SELECT id FROM indicator_items WHERE indicator_code='A.1.1'",Long.class);
        jdbc.update("INSERT INTO indicator_scoring_rules(indicator_id,rule_type,rule_name,config_json,created_by,updated_by) VALUES(?,'THRESHOLD_DEDUCTION','阈值扣分','{\"threshold\":80,\"deduction\":1}',?,?)",l3,user,user);
        return version;
    }

    private Fixture fixture()throws Exception{
        Path db=tempDirectory.resolve("internal.sqlite");
        DataSource ds=new SQLiteDataSourceConfig().dataSource(
                new MockEnvironment().withProperty("app.database.path",db.toString()));
        new DatabaseMigrationRunner(ds).run(new DefaultApplicationArguments(new String[0]));
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        InternalEvaluationService service=new InternalEvaluationService(
                new InternalEvaluationRepository(jdbc),
                new CurrentUserService(new UserRepository(jdbc),"zhang.zhuren"),
                new OperationLogRepository(jdbc),
                new InternalEvaluationStorageService(tempDirectory.resolve("storage").toString()),
                new ObjectMapper());
        return new Fixture(ds,jdbc,service);
    }
    private long number(Object value){return ((Number)value).longValue();}
    private int integer(Object value){return ((Number)value).intValue();}
    private record Fixture(DataSource dataSource,JdbcTemplate jdbc,InternalEvaluationService service){}
}
