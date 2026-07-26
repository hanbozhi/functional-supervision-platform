package com.zhineng.platform.evaluation.counterpart.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CounterpartRepository {
    private final JdbcTemplate jdbc;

    public CounterpartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> activeOrgs() {
        return jdbc.queryForList("""
                SELECT id,unit_code,unit_name,unit_type
                FROM org_units
                WHERE status='ACTIVE' AND unit_type NOT IN ('ROOT','GROUP')
                ORDER BY sort_order,unit_name
                """);
    }

    public Map<String, Object> org(long id) {
        return one("SELECT * FROM org_units WHERE id=?", id);
    }

    public String confirmedDuty(long orgId) {
        List<String> values = jdbc.queryForList("""
                SELECT v.main_responsibilities
                FROM three_fixed_plans p
                JOIN three_fixed_plan_versions v ON v.id=p.current_version_id
                WHERE p.org_unit_id=? AND v.workflow_status='CONFIRMED'
                """, String.class, orgId);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<Map<String, Object>> relations(String status, Long subjectOrgId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.*,s.unit_name subject_org_name,c.unit_name counterpart_org_name,
                  u.display_name verified_by_name
                FROM counterpart_relations r
                JOIN org_units s ON s.id=r.subject_org_id
                JOIN org_units c ON c.id=r.counterpart_org_id
                LEFT JOIN sys_users u ON u.id=r.verified_by
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status=?");
            args.add(status);
        }
        if (subjectOrgId != null) {
            sql.append(" AND r.subject_org_id=?");
            args.add(subjectOrgId);
        }
        sql.append(" ORDER BY r.updated_at DESC,r.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> relation(long id) {
        return one("""
                SELECT r.*,s.unit_name subject_org_name,c.unit_name counterpart_org_name
                FROM counterpart_relations r
                JOIN org_units s ON s.id=r.subject_org_id
                JOIN org_units c ON c.id=r.counterpart_org_id WHERE r.id=?
                """, id);
    }

    public boolean relationExists(long subject, long target, String item) {
        return count("""
                SELECT count(*) FROM counterpart_relations
                WHERE subject_org_id=? AND counterpart_org_id=? AND collaboration_item=?
                """, subject, target, item) > 0;
    }

    public long insertRelation(long subject, long target, String item, String source,
                               double confidence, String status, long userId) {
        return insert("""
                INSERT INTO counterpart_relations(
                  subject_org_id,counterpart_org_id,collaboration_item,source,
                  confidence,status,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?)
                """, subject, target, item, source, confidence, status, userId, userId);
    }

    public int updateRelation(long id, long subject, long target, String item,
                              double confidence, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE counterpart_relations SET subject_org_id=?,counterpart_org_id=?,
                  collaboration_item=?,confidence=?,status='SUGGESTED',
                  verification_opinion=NULL,verified_by=NULL,verified_at=NULL,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                """, subject, target, item, confidence, userId, id, rowVersion);
    }

    public int verifyRelation(long id, String status, String opinion,
                              int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE counterpart_relations SET status=?,verification_opinion=?,
                  verified_by=?,verified_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                """, status, opinion, userId, userId, id, rowVersion);
    }

    public int relationStatus(long id, String status, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE counterpart_relations SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                """, status, userId, id, rowVersion);
    }

    public List<Map<String, Object>> questionnaires(String status) {
        String where = status == null || status.isBlank() ? "" : " WHERE q.status=?";
        Object[] args = where.isEmpty() ? new Object[0] : new Object[]{status};
        return jdbc.queryForList("""
                SELECT q.*,
                  (SELECT count(*) FROM counterpart_questionnaire_questions x
                    WHERE x.questionnaire_id=q.id) question_count,
                  (SELECT count(*) FROM counterpart_questionnaire_recipients x
                    WHERE x.questionnaire_id=q.id) recipient_count,
                  (SELECT count(*) FROM counterpart_questionnaire_recipients x
                    WHERE x.questionnaire_id=q.id AND x.status='SUBMITTED') submitted_count
                FROM counterpart_questionnaires q
                """ + where + " ORDER BY q.evaluation_year DESC,q.id DESC", args);
    }

    public Map<String, Object> questionnaire(long id) {
        return one("SELECT * FROM counterpart_questionnaires WHERE id=?", id);
    }

    public List<Map<String, Object>> dimensions(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT * FROM counterpart_questionnaire_dimensions
                WHERE questionnaire_id=? ORDER BY sort_order,id
                """, questionnaireId);
    }

    public List<Map<String, Object>> questions(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT q.*,d.dimension_code,d.dimension_name
                FROM counterpart_questionnaire_questions q
                LEFT JOIN counterpart_questionnaire_dimensions d ON d.id=q.dimension_id
                WHERE q.questionnaire_id=? ORDER BY q.sort_order,q.id
                """, questionnaireId);
    }

    public long insertQuestionnaire(String code, String title, int year, String deadline,
                                    String description, Long indicatorVersionId, long userId) {
        return insert("""
                INSERT INTO counterpart_questionnaires(
                  batch_code,title,evaluation_year,deadline_at,description,
                  indicator_version_id,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?)
                """, code, title, year, deadline, description, indicatorVersionId, userId, userId);
    }

    public long insertDimension(long questionnaireId, String code, String name, int sortOrder) {
        return insert("""
                INSERT INTO counterpart_questionnaire_dimensions(
                  questionnaire_id,dimension_code,dimension_name,sort_order
                ) VALUES(?,?,?,?)
                """, questionnaireId, code, name, sortOrder);
    }

    public void insertQuestion(long questionnaireId, Long dimensionId, String code,
                               String text, String type, boolean required,
                               Long indicatorItemId, int sortOrder) {
        jdbc.update("""
                INSERT INTO counterpart_questionnaire_questions(
                  questionnaire_id,dimension_id,question_code,question_text,
                  question_type,required,indicator_item_id,sort_order
                ) VALUES(?,?,?,?,?,?,?,?)
                """, questionnaireId, dimensionId, code, text, type,
                required ? 1 : 0, indicatorItemId, sortOrder);
    }

    public int publishQuestionnaire(long id, long userId) {
        return jdbc.update("""
                UPDATE counterpart_questionnaires SET status='PUBLISHED',
                  published_by=?,published_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND status='DRAFT'
                """, userId, userId, id);
    }

    public int closeQuestionnaire(long id, String status, long userId) {
        return jdbc.update("""
                UPDATE counterpart_questionnaires SET status=?,closed_by=?,
                  closed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND status='PUBLISHED'
                """, status, userId, userId, id);
    }

    public List<Map<String, Object>> recipients(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT r.*,m.anonymous_code,m.fill_token,
                  e.unit_name evaluator_org_name,t.unit_name target_org_name,
                  cr.collaboration_item
                FROM counterpart_questionnaire_recipients r
                JOIN counterpart_anonymous_mappings m ON m.recipient_id=r.id
                JOIN org_units e ON e.id=r.evaluator_org_id
                JOIN org_units t ON t.id=r.target_org_id
                JOIN counterpart_relations cr ON cr.id=r.relation_id
                WHERE r.questionnaire_id=? ORDER BY r.id
                """, questionnaireId);
    }

    public long insertRecipient(long questionnaireId, Map<String, Object> relation, long userId) {
        return insert("""
                INSERT INTO counterpart_questionnaire_recipients(
                  questionnaire_id,relation_id,evaluator_org_id,target_org_id,created_by
                ) VALUES(?,?,?,?,?)
                """, questionnaireId, number(relation.get("id")),
                number(relation.get("subject_org_id")),
                number(relation.get("counterpart_org_id")), userId);
    }

    public boolean recipientExists(long questionnaireId, long relationId) {
        return count("""
                SELECT count(*) FROM counterpart_questionnaire_recipients
                WHERE questionnaire_id=? AND relation_id=?
                """, questionnaireId, relationId) > 0;
    }

    public void insertMapping(long recipientId, String anonymousCode, String token) {
        jdbc.update("""
                INSERT INTO counterpart_anonymous_mappings(
                  recipient_id,anonymous_code,fill_token
                ) VALUES(?,?,?)
                """, recipientId, anonymousCode, token);
    }

    public Map<String, Object> recipientByToken(String token) {
        return one("""
                SELECT r.*,m.anonymous_code,m.fill_token,q.title,q.deadline_at,
                  q.status questionnaire_status
                FROM counterpart_anonymous_mappings m
                JOIN counterpart_questionnaire_recipients r ON r.id=m.recipient_id
                JOIN counterpart_questionnaires q ON q.id=r.questionnaire_id
                WHERE m.fill_token=?
                """, token);
    }

    public Map<String, Object> recipient(long id) {
        return one("""
                SELECT r.*,m.anonymous_code,m.fill_token,
                  e.unit_name evaluator_org_name,t.unit_name target_org_name
                FROM counterpart_questionnaire_recipients r
                JOIN counterpart_anonymous_mappings m ON m.recipient_id=r.id
                JOIN org_units e ON e.id=r.evaluator_org_id
                JOIN org_units t ON t.id=r.target_org_id WHERE r.id=?
                """, id);
    }

    public void logPush(long questionnaireId, long recipientId, String status,
                        String summary, String failure, long userId) {
        jdbc.update("""
                INSERT INTO counterpart_push_logs(
                  questionnaire_id,recipient_id,delivery_status,message_summary,
                  failure_reason,sent_by
                ) VALUES(?,?,?,?,?,?)
                """, questionnaireId, recipientId, status, summary, failure, userId);
        if ("DELIVERED".equals(status)) {
            jdbc.update("""
                    UPDATE counterpart_questionnaire_recipients
                    SET status=CASE WHEN status='PENDING' THEN 'SENT' ELSE status END,
                      sent_at=COALESCE(sent_at,strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                    WHERE id=?
                    """, recipientId);
        }
    }

    public List<Map<String, Object>> pushLogs(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT l.*,m.anonymous_code FROM counterpart_push_logs l
                JOIN counterpart_anonymous_mappings m ON m.recipient_id=l.recipient_id
                WHERE l.questionnaire_id=? ORDER BY l.sent_at DESC,l.id DESC
                """, questionnaireId);
    }

    public long insertResponse(long recipientId, String anonymousCode,
                               int elapsedSeconds, String submittedAt) {
        return insert("""
                INSERT INTO counterpart_questionnaire_responses(
                  recipient_id,anonymous_code,client_elapsed_seconds,submitted_at
                ) VALUES(?,?,?,?)
                """, recipientId, anonymousCode, elapsedSeconds, submittedAt);
    }

    public void insertAnswer(long responseId, long questionId, Integer score, String text) {
        jdbc.update("""
                INSERT INTO counterpart_questionnaire_answers(
                  response_id,question_id,score_value,text_value
                ) VALUES(?,?,?,?)
                """, responseId, questionId, score, text);
    }

    public void markSubmitted(long recipientId, String submittedAt) {
        jdbc.update("""
                UPDATE counterpart_questionnaire_recipients
                SET status='SUBMITTED',submitted_at=? WHERE id=?
                """, submittedAt, recipientId);
    }

    public void restore(long recipientId, long userId) {
        jdbc.update("""
                UPDATE counterpart_anonymous_mappings SET restore_count=restore_count+1,
                  last_restored_by=?,last_restored_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                WHERE recipient_id=?
                """, userId, recipientId);
    }

    public List<Map<String, Object>> resultStats(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT d.dimension_name,q.question_text,
                  round(avg(a.score_value),2) average_score,count(a.score_value) sample_count
                FROM counterpart_questionnaire_answers a
                JOIN counterpart_questionnaire_responses r ON r.id=a.response_id
                JOIN counterpart_questionnaire_recipients rc ON rc.id=r.recipient_id
                JOIN counterpart_questionnaire_questions q ON q.id=a.question_id
                LEFT JOIN counterpart_questionnaire_dimensions d ON d.id=q.dimension_id
                WHERE rc.questionnaire_id=? AND a.score_value IS NOT NULL
                GROUP BY d.id,q.id ORDER BY d.sort_order,q.sort_order
                """, questionnaireId);
    }

    public List<Map<String, Object>> organizationStats(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT o.id org_unit_id,o.unit_name,
                  round(avg(a.score_value),2) average_score,count(a.score_value) sample_count
                FROM counterpart_questionnaire_answers a
                JOIN counterpart_questionnaire_responses r ON r.id=a.response_id
                JOIN counterpart_questionnaire_recipients rc ON rc.id=r.recipient_id
                JOIN org_units o ON o.id=rc.target_org_id
                WHERE rc.questionnaire_id=? AND a.score_value IS NOT NULL
                GROUP BY o.id ORDER BY average_score DESC
                """, questionnaireId);
    }

    public List<Map<String, Object>> scoreAnswers(long questionnaireId) {
        return jdbc.queryForList("""
                SELECT a.*,r.recipient_id,r.anonymous_code,r.client_elapsed_seconds,
                  r.submitted_at,q.question_text,q.dimension_id
                FROM counterpart_questionnaire_answers a
                JOIN counterpart_questionnaire_responses r ON r.id=a.response_id
                JOIN counterpart_questionnaire_recipients rc ON rc.id=r.recipient_id
                JOIN counterpart_questionnaire_questions q ON q.id=a.question_id
                WHERE rc.questionnaire_id=? AND a.score_value IS NOT NULL
                ORDER BY r.submitted_at,a.id
                """, questionnaireId);
    }

    public long insertAnomalyRun(long questionnaireId, String code, String rulesJson,
                                 int samples, long userId) {
        return insert("""
                INSERT INTO counterpart_anomaly_runs(
                  questionnaire_id,run_code,rules_json,sample_count,created_by
                ) VALUES(?,?,?,?,?)
                """, questionnaireId, code, rulesJson, samples, userId);
    }

    public void insertAnomaly(long runId, long responseId, Long questionId,
                              String type, Double observed, Double reference,
                              String explanation) {
        jdbc.update("""
                INSERT OR IGNORE INTO counterpart_anomaly_cases(
                  run_id,response_id,question_id,anomaly_type,observed_value,
                  reference_value,rule_explanation
                ) VALUES(?,?,?,?,?,?,?)
                """, runId, responseId, questionId, type, observed, reference, explanation);
    }

    public void finishAnomalyRun(long runId) {
        jdbc.update("""
                UPDATE counterpart_anomaly_runs SET anomaly_count=(
                  SELECT count(*) FROM counterpart_anomaly_cases WHERE run_id=?
                ) WHERE id=?
                """, runId, runId);
    }

    public List<Map<String, Object>> anomalyRuns(Long questionnaireId) {
        if (questionnaireId == null) {
            return jdbc.queryForList("""
                    SELECT r.*,q.title FROM counterpart_anomaly_runs r
                    JOIN counterpart_questionnaires q ON q.id=r.questionnaire_id
                    ORDER BY r.id DESC
                    """);
        }
        return jdbc.queryForList("""
                SELECT r.*,q.title FROM counterpart_anomaly_runs r
                JOIN counterpart_questionnaires q ON q.id=r.questionnaire_id
                WHERE r.questionnaire_id=? ORDER BY r.id DESC
                """, questionnaireId);
    }

    public List<Map<String, Object>> anomalies(long runId, String status) {
        String extra = status == null || status.isBlank() ? "" : " AND c.status=?";
        Object[] args = extra.isEmpty() ? new Object[]{runId} : new Object[]{runId, status};
        return jdbc.queryForList("""
                SELECT c.*,r.anonymous_code,q.question_text,u.display_name assigned_to_name
                FROM counterpart_anomaly_cases c
                JOIN counterpart_questionnaire_responses r ON r.id=c.response_id
                LEFT JOIN counterpart_questionnaire_questions q ON q.id=c.question_id
                LEFT JOIN sys_users u ON u.id=c.assigned_to
                WHERE c.run_id=?
                """ + extra + " ORDER BY c.id DESC", args);
    }

    public Map<String, Object> anomaly(long id) {
        return one("""
                SELECT c.*,r.anonymous_code,q.question_text FROM counterpart_anomaly_cases c
                JOIN counterpart_questionnaire_responses r ON r.id=c.response_id
                LEFT JOIN counterpart_questionnaire_questions q ON q.id=c.question_id
                WHERE c.id=?
                """, id);
    }

    public int updateAnomaly(long id, String status, Long assignee,
                             int rowVersion, long reviewer, String action, String opinion) {
        int count = jdbc.update("""
                UPDATE counterpart_anomaly_cases SET status=?,assigned_to=?,
                  assigned_at=CASE WHEN ?='ASSIGNED' THEN strftime('%Y-%m-%dT%H:%M:%SZ','now')
                    ELSE assigned_at END,row_version=row_version+1
                WHERE id=? AND row_version=?
                """, status, assignee, status, id, rowVersion);
        if (count == 1) {
            jdbc.update("""
                    INSERT INTO counterpart_anomaly_reviews(
                      anomaly_case_id,review_action,review_opinion,reviewer_id
                    ) VALUES(?,?,?,?)
                    """, id, action, opinion, reviewer);
        }
        return count;
    }

    public List<Map<String, Object>> reviews(long caseId) {
        return jdbc.queryForList("""
                SELECT r.*,u.display_name reviewer_name FROM counterpart_anomaly_reviews r
                LEFT JOIN sys_users u ON u.id=r.reviewer_id
                WHERE r.anomaly_case_id=? ORDER BY r.reviewed_at DESC,r.id DESC
                """, caseId);
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
