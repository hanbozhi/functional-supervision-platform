package com.zhineng.platform.evaluation.internal.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InternalEvaluationRepository {
    private final JdbcTemplate jdbc;

    public InternalEvaluationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> tasks() {
        return jdbc.queryForList("""
                SELECT t.*,v.version_name,s.system_name,
                  count(DISTINCT o.id) org_count,
                  count(DISTINCT CASE WHEN sh.status='CONFIRMED' THEN sh.id END) confirmed_count,
                  count(DISTINCT CASE WHEN sh.status IN ('DRAFT','SUBMITTED','RETURNED')
                    THEN sh.id END) active_score_count,
                  round(CASE WHEN count(DISTINCT o.id)=0 THEN 0 ELSE
                    count(DISTINCT CASE WHEN sh.status='CONFIRMED' THEN sh.id END)*100.0/
                    count(DISTINCT o.id) END,1) progress_percent
                FROM internal_evaluation_tasks t
                JOIN indicator_versions v ON v.id=t.indicator_version_id
                JOIN indicator_systems s ON s.id=v.system_id
                LEFT JOIN internal_evaluation_task_orgs o ON o.task_id=t.id
                LEFT JOIN internal_evaluation_score_sheets sh ON sh.task_org_id=o.id
                GROUP BY t.id ORDER BY t.evaluation_year DESC,t.id DESC
                """);
    }

    public Map<String, Object> task(long id) {
        return one("""
                SELECT t.*,v.version_name,v.status indicator_version_status,
                  s.system_name FROM internal_evaluation_tasks t
                JOIN indicator_versions v ON v.id=t.indicator_version_id
                JOIN indicator_systems s ON s.id=v.system_id WHERE t.id=?
                """, id);
    }

    public List<Map<String, Object>> taskOrgs(long taskId) {
        return jdbc.queryForList("""
                SELECT o.*,u.unit_code,u.unit_name,u.unit_type,
                  a.evaluator_id,a.reviewer_id,
                  eu.display_name evaluator_name,ru.display_name reviewer_name,
                  sh.id score_sheet_id,sh.status score_status,sh.total_score,
                  sh.row_version sheet_row_version
                FROM internal_evaluation_task_orgs o
                JOIN org_units u ON u.id=o.org_unit_id
                LEFT JOIN internal_evaluation_assignments a ON a.task_org_id=o.id
                LEFT JOIN sys_users eu ON eu.id=a.evaluator_id
                LEFT JOIN sys_users ru ON ru.id=a.reviewer_id
                LEFT JOIN internal_evaluation_score_sheets sh ON sh.task_org_id=o.id
                WHERE o.task_id=? ORDER BY u.sort_order,u.unit_name
                """, taskId);
    }

    public List<Map<String, Object>> publishedVersions() {
        return jdbc.queryForList("""
                SELECT v.id,v.version_name,v.evaluation_year,s.system_name,
                  (SELECT count(*) FROM indicator_items i
                    WHERE i.version_id=v.id AND i.indicator_level=3
                      AND i.status='ACTIVE') score_item_count
                FROM indicator_versions v JOIN indicator_systems s ON s.id=v.system_id
                WHERE v.status='PUBLISHED' ORDER BY v.evaluation_year DESC,v.id DESC
                """);
    }

    public List<Map<String, Object>> activeOrgs() {
        return jdbc.queryForList("""
                SELECT id,unit_code,unit_name,unit_type FROM org_units
                WHERE status='ACTIVE' AND unit_type NOT IN ('ROOT','GROUP')
                ORDER BY sort_order,unit_name
                """);
    }

    public List<Map<String, Object>> activeUsers() {
        return jdbc.queryForList("""
                SELECT id,username,display_name,org_unit_id FROM sys_users
                WHERE status='ACTIVE' ORDER BY display_name
                """);
    }

    public long insertTask(String code, String name, int year, String type,
                           String start, String end, String description,
                           long indicatorVersionId, Long sourceTaskId, long userId) {
        return insert("""
                INSERT INTO internal_evaluation_tasks(
                  task_code,task_name,evaluation_year,task_type,start_date,end_date,
                  description,indicator_version_id,source_task_id,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, code,name,year,type,start,end,description,indicatorVersionId,
                sourceTaskId,userId,userId);
    }

    public long insertTaskOrg(long taskId, long orgId, long userId) {
        return insert("""
                INSERT INTO internal_evaluation_task_orgs(task_id,org_unit_id,created_by)
                VALUES(?,?,?)
                """, taskId,orgId,userId);
    }

    public void insertAssignment(long taskOrgId, long evaluator, long reviewer, long userId) {
        jdbc.update("""
                INSERT INTO internal_evaluation_assignments(
                  task_org_id,evaluator_id,reviewer_id,assigned_by
                ) VALUES(?,?,?,?)
                """, taskOrgId,evaluator,reviewer,userId);
    }

    public List<Map<String, Object>> sourceIndicators(long versionId) {
        return jdbc.queryForList("""
                SELECT * FROM indicator_items
                WHERE version_id=? AND indicator_level=3 AND status='ACTIVE'
                ORDER BY sort_order,id
                """, versionId);
    }

    public List<Map<String, Object>> sourceRules(long indicatorId) {
        return jdbc.queryForList("""
                SELECT rule_type,rule_name,config_json,description
                FROM indicator_scoring_rules
                WHERE indicator_id=? AND status='ACTIVE' ORDER BY sort_order,id
                """, indicatorId);
    }

    public long insertSnapshot(long taskId, Map<String, Object> indicator, String rulesJson) {
        return insert("""
                INSERT INTO internal_evaluation_indicator_snapshots(
                  task_id,source_indicator_id,indicator_code,indicator_name,
                  standard_score,weight,evaluation_method,rules_json,sort_order
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId,indicator.get("id"),indicator.get("indicator_code"),
                indicator.get("indicator_name"),indicator.get("standard_score"),
                indicator.get("weight"),indicator.get("evaluation_method"),rulesJson,
                indicator.get("sort_order"));
    }

    public long insertSheet(long taskOrgId, long userId) {
        return insert("""
                INSERT INTO internal_evaluation_score_sheets(task_org_id,updated_by)
                VALUES(?,?)
                """, taskOrgId,userId);
    }

    public void insertEntry(long sheetId, long snapshotId, long userId) {
        jdbc.update("""
                INSERT INTO internal_evaluation_score_entries(
                  score_sheet_id,snapshot_id,updated_by
                ) VALUES(?,?,?)
                """,sheetId,snapshotId,userId);
    }

    public int publishTask(long id,long userId) {
        return jdbc.update("""
                UPDATE internal_evaluation_tasks SET status='PUBLISHED',
                  published_by=?,published_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND status='DRAFT'
                """,userId,userId,id);
    }

    public List<Map<String, Object>> snapshots(long taskId) {
        return jdbc.queryForList("""
                SELECT * FROM internal_evaluation_indicator_snapshots
                WHERE task_id=? ORDER BY sort_order,id
                """,taskId);
    }

    public Map<String, Object> sheet(long id) {
        return one("""
                SELECT sh.*,o.task_id,o.org_unit_id,u.unit_name,t.task_name,t.status task_status,
                  a.evaluator_id,a.reviewer_id
                FROM internal_evaluation_score_sheets sh
                JOIN internal_evaluation_task_orgs o ON o.id=sh.task_org_id
                JOIN org_units u ON u.id=o.org_unit_id
                JOIN internal_evaluation_tasks t ON t.id=o.task_id
                LEFT JOIN internal_evaluation_assignments a ON a.task_org_id=o.id
                WHERE sh.id=?
                """,id);
    }

    public List<Map<String, Object>> entries(long sheetId) {
        return jdbc.queryForList("""
                SELECT e.*,s.indicator_code,s.indicator_name,s.standard_score,s.weight,
                  s.evaluation_method,s.rules_json,
                  (SELECT count(*) FROM internal_evaluation_score_materials m
                    JOIN sys_attachments a ON a.id=m.attachment_id
                    WHERE m.score_entry_id=e.id AND a.status='ACTIVE') material_count
                FROM internal_evaluation_score_entries e
                JOIN internal_evaluation_indicator_snapshots s ON s.id=e.snapshot_id
                WHERE e.score_sheet_id=? ORDER BY s.sort_order,s.id
                """,sheetId);
    }

    public Map<String,Object> entry(long id) {
        return one("""
                SELECT e.*,s.standard_score,sh.status sheet_status,sh.id sheet_id,
                  o.task_id FROM internal_evaluation_score_entries e
                JOIN internal_evaluation_indicator_snapshots s ON s.id=e.snapshot_id
                JOIN internal_evaluation_score_sheets sh ON sh.id=e.score_sheet_id
                JOIN internal_evaluation_task_orgs o ON o.id=sh.task_org_id WHERE e.id=?
                """,id);
    }

    public int updateEntry(long id,double score,String basisType,String basis,
                           String remarks,boolean veto,int rowVersion,long userId) {
        return jdbc.update("""
                UPDATE internal_evaluation_score_entries SET score=?,basis_type=?,
                  score_basis=?,remarks=?,veto_triggered=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                  AND EXISTS(SELECT 1 FROM internal_evaluation_score_sheets sh
                    WHERE sh.id=score_sheet_id AND sh.status IN ('NOT_STARTED','DRAFT','RETURNED'))
                """,score,basisType,basis,remarks,veto?1:0,userId,id,rowVersion);
    }

    public int saveSheet(long id,double total,int rowVersion,long userId) {
        return jdbc.update("""
                UPDATE internal_evaluation_score_sheets SET status='DRAFT',
                  total_score=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=? AND status IN ('NOT_STARTED','DRAFT','RETURNED')
                """,total,userId,id,rowVersion);
    }

    public int sheetStatus(long id,String fromStatus,String toStatus,String opinion,
                           int rowVersion,long userId) {
        int count;
        if ("CONFIRMED".equals(toStatus)) {
            count=jdbc.update("""
                    UPDATE internal_evaluation_score_sheets SET status=?,updated_by=?,
                      updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                      row_version=row_version+1,confirmed_by=?,
                      confirmed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                    WHERE id=? AND row_version=? AND status=?
                    """,toStatus,userId,userId,id,rowVersion,fromStatus);
        } else if ("SUBMITTED".equals(toStatus)) {
            count=jdbc.update("""
                    UPDATE internal_evaluation_score_sheets SET status=?,updated_by=?,
                      updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                      row_version=row_version+1,submitted_by=?,
                      submitted_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                    WHERE id=? AND row_version=? AND status=?
                    """,toStatus,userId,userId,id,rowVersion,fromStatus);
        } else {
            count=jdbc.update("""
                    UPDATE internal_evaluation_score_sheets SET status=?,updated_by=?,
                      updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                      row_version=row_version+1
                    WHERE id=? AND row_version=? AND status=?
                    """,toStatus,userId,id,rowVersion,fromStatus);
        }
        if(count==1) jdbc.update("""
                INSERT INTO internal_evaluation_reviews(
                  score_sheet_id,review_action,review_opinion,reviewer_id
                ) VALUES(?,?,?,?)
                """,id,"SUBMITTED".equals(toStatus)?"SUBMIT":
                "RETURNED".equals(toStatus)?"RETURN":"CONFIRM",opinion,userId);
        return count;
    }

    public void updateTaskProgress(long taskId,long userId) {
        jdbc.update("""
                UPDATE internal_evaluation_tasks SET status=CASE
                  WHEN NOT EXISTS(
                    SELECT 1 FROM internal_evaluation_task_orgs o
                    JOIN internal_evaluation_score_sheets s ON s.task_org_id=o.id
                    WHERE o.task_id=internal_evaluation_tasks.id AND s.status<>'CONFIRMED'
                  ) THEN 'COMPLETED'
                  WHEN EXISTS(
                    SELECT 1 FROM internal_evaluation_task_orgs o
                    JOIN internal_evaluation_score_sheets s ON s.task_org_id=o.id
                    WHERE o.task_id=internal_evaluation_tasks.id
                      AND s.status IN ('SUBMITTED','RETURNED')
                  ) THEN 'REVIEWING'
                  WHEN EXISTS(
                    SELECT 1 FROM internal_evaluation_task_orgs o
                    JOIN internal_evaluation_score_sheets s ON s.task_org_id=o.id
                    WHERE o.task_id=internal_evaluation_tasks.id
                      AND s.status='DRAFT'
                  ) THEN 'SCORING' ELSE status END,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                WHERE id=? AND status NOT IN ('DRAFT','CANCELLED')
                """,userId,taskId);
    }

    public int taskStatus(long id,String status,String reason,int rowVersion,long userId) {
        Map<String,Object> before=task(id);
        int count=jdbc.update("""
                UPDATE internal_evaluation_tasks SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                """,status,userId,id,rowVersion);
        if(count==1) history("TASK",id,String.valueOf(before.get("status")),status,reason,userId);
        return count;
    }

    public void history(String type,long id,String from,String to,String reason,long userId) {
        jdbc.update("""
                INSERT INTO internal_evaluation_status_history(
                  entity_type,entity_id,from_status,to_status,reason,operator_id
                ) VALUES(?,?,?,?,?,?)
                """,type,id,from,to,reason,userId);
    }

    public List<Map<String,Object>> history(String type,long id) {
        return jdbc.queryForList("""
                SELECT h.*,u.display_name operator_name
                FROM internal_evaluation_status_history h
                LEFT JOIN sys_users u ON u.id=h.operator_id
                WHERE h.entity_type=? AND h.entity_id=?
                ORDER BY h.changed_at DESC,h.id DESC
                """,type,id);
    }

    public long insertAttachment(long entryId,String original,String stored,String path,
                                 String type,String ext,long size,String sha,long userId,String remarks) {
        long attachmentId=insert("""
                INSERT INTO sys_attachments(
                  business_type,business_id,original_name,stored_name,storage_path,
                  content_type,extension,file_size,sha256,uploaded_by
                ) VALUES('INTERNAL_SCORE_ENTRY',?,?,?,?,?,?,?,?,?)
                """,entryId,original,stored,path,type,ext,size,sha,userId);
        insert("""
                INSERT INTO internal_evaluation_score_materials(
                  score_entry_id,attachment_id,remarks,created_by
                ) VALUES(?,?,?,?)
                """,entryId,attachmentId,remarks,userId);
        return attachmentId;
    }

    public List<Map<String,Object>> materials(long entryId) {
        return jdbc.queryForList("""
                SELECT m.*,a.original_name,a.storage_path,a.content_type,a.extension,
                  a.file_size,a.status,a.created_at
                FROM internal_evaluation_score_materials m
                JOIN sys_attachments a ON a.id=m.attachment_id
                WHERE m.score_entry_id=? AND a.status='ACTIVE'
                ORDER BY a.created_at DESC,a.id DESC
                """,entryId);
    }

    public Map<String,Object> attachment(long id) {
        return one("""
                SELECT a.*,m.score_entry_id FROM sys_attachments a
                JOIN internal_evaluation_score_materials m ON m.attachment_id=a.id
                WHERE a.id=? AND a.business_type='INTERNAL_SCORE_ENTRY' AND a.status='ACTIVE'
                """,id);
    }

    public List<Map<String,Object>> reviews(long sheetId) {
        return jdbc.queryForList("""
                SELECT r.*,u.display_name reviewer_name FROM internal_evaluation_reviews r
                LEFT JOIN sys_users u ON u.id=r.reviewer_id
                WHERE r.score_sheet_id=? ORDER BY r.reviewed_at DESC,r.id DESC
                """,sheetId);
    }

    public Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        return rows.isEmpty()?null:rows.get(0);
    }

    private long insert(String sql,Object...args) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection->{
            PreparedStatement statement=connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },keys);
        return keys.getKey().longValue();
    }
}
