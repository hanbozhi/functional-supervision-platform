package com.zhineng.platform.evaluation.publicservice;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;import com.zhineng.platform.common.audit.OperationLogRepository;import com.zhineng.platform.common.database.*;import com.zhineng.platform.common.user.repository.UserRepository;import com.zhineng.platform.common.user.service.CurrentUserService;
import com.zhineng.platform.evaluation.publicservice.dto.PublicEvaluationDtos.*;import com.zhineng.platform.evaluation.publicservice.repository.PublicEvaluationRepository;import com.zhineng.platform.evaluation.publicservice.service.PublicEvaluationService;import com.zhineng.platform.evaluation.publicservice.storage.PublicEvaluationStorage;
import java.nio.charset.StandardCharsets;import java.nio.file.Path;import java.util.*;import javax.sql.DataSource;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import org.springframework.boot.DefaultApplicationArguments;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.mock.env.MockEnvironment;import org.springframework.mock.web.MockMultipartFile;
class PublicEvaluationTest {
 @TempDir Path temp;
 @Test void localPrivacyImportAndImmutableOriginalFlow()throws Exception{
  Fixture f=fixture();Map<String,Object>org=f.repo.orgs().get(0);long oid=num(org.get("id"));
  Map<String,Object>item=f.service.saveItem(null,new ServiceItemRequest("TEST-01","测试事项",oid,"说明","ACTIVE"));
  Map<String,Object>local=f.service.submit(new EvaluationRequest(oid,num(item.get("id")),5,4,5,4,"办理方便，服务很好",false,"测试群众","13812341234","330000000000000000"),null);
  assertEquals("POSITIVE",local.get("sentiment"));assertFalse(local.containsKey("evaluator_phone"));
  long eid=num(local.get("id"));Map<String,Object>req=f.service.requestAccess(eid,new AccessRequest("异常回访","姓名,手机号"));
  req=f.service.review(num(req.get("id")),new ReviewRequest("APPROVE","同意模拟查看"));Map<String,Object>revealed=f.service.reveal(num(req.get("id")));assertEquals("13812341234",revealed.get("evaluator_phone"));assertEquals(1,f.service.audits().size());
  String code=org.get("unit_code").toString();String csv="机构编码,机构名称,综合评分,评价内容,评价时间,服务事项,是否匿名\n"+code+","+org.get("unit_name")+",1,办理太慢非常不满,2026-07-26,窗口事项,是\nNOPE,错误机构,5,很好,2026-07-26,事项,是\n";
  Map<String,Object>batch=f.service.importFile("HOTLINE_12345",new MockMultipartFile("file","external.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)));
  assertEquals(1L,num(batch.get("success_rows")));assertEquals(1L,num(batch.get("failed_rows")));
  assertEquals(2,f.service.list(null,null,null,null,null).size());assertEquals("NEGATIVE",f.service.list(null,"HOTLINE_12345",null,null,null).get(0).get("sentiment"));
  f.service.process(eid,new ProcessRequest("RESOLVED","已回访"));assertEquals("RESOLVED",f.service.detail(eid).get("process_status"));
  new DatabaseMigrationRunner(f.ds).run(new DefaultApplicationArguments(new String[0]));assertEquals(1,f.jdbc.queryForObject("SELECT count(*) FROM schema_migrations WHERE version='12'",Integer.class));assertEquals(1,f.jdbc.queryForObject("PRAGMA foreign_keys",Integer.class));
 }
 private Fixture fixture()throws Exception{Path db=temp.resolve("public.sqlite");DataSource ds=new SQLiteDataSourceConfig().dataSource(new MockEnvironment().withProperty("app.database.path",db.toString()));new DatabaseMigrationRunner(ds).run(new DefaultApplicationArguments(new String[0]));JdbcTemplate jdbc=new JdbcTemplate(ds);PublicEvaluationRepository repo=new PublicEvaluationRepository(jdbc);PublicEvaluationService service=new PublicEvaluationService(repo,new CurrentUserService(new UserRepository(jdbc),"zhang.zhuren"),new OperationLogRepository(jdbc),new PublicEvaluationStorage(temp.resolve("storage").toString()),new ObjectMapper());return new Fixture(ds,jdbc,repo,service);}
 private long num(Object v){return((Number)v).longValue();}private record Fixture(DataSource ds,JdbcTemplate jdbc,PublicEvaluationRepository repo,PublicEvaluationService service){}
}
