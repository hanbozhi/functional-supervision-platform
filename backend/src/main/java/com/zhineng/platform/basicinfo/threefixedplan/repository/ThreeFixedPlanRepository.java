package com.zhineng.platform.basicinfo.threefixedplan.repository;

import com.zhineng.platform.basicinfo.threefixedplan.dto.ThreeFixedDtos;
import com.zhineng.platform.basicinfo.threefixedplan.parser.SimpleDocumentParser;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ThreeFixedPlanRepository {
    private final JdbcTemplate jdbc;

    public ThreeFixedPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean activeOrgExists(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*)>0 FROM org_units WHERE id=? AND status='ACTIVE'",
                Boolean.class, id));
    }

    public Long findPlanIdByOrg(long orgId) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM three_fixed_plans WHERE org_unit_id=?",
                (rs, n) -> rs.getLong(1), orgId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createPlan(long orgId, long userId) {
        return insert("""
                INSERT INTO three_fixed_plans(org_unit_id,created_by,updated_by)
                VALUES(?,?,?)
                """, orgId, userId, userId);
    }

    public boolean hasOpenVersion(long planId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)>0 FROM three_fixed_plan_versions
                WHERE plan_id=? AND workflow_status<>'CONFIRMED'
                """, Boolean.class, planId));
    }

    public int nextVersionNo(long planId) {
        return jdbc.queryForObject("""
                SELECT coalesce(max(version_no),0)+1 FROM three_fixed_plan_versions WHERE plan_id=?
                """, Integer.class, planId);
    }

    public long insertVersion(
            long planId, int versionNo, String label, String sourceType,
            String parseStatus, ThreeFixedDtos.Fields fields, String parsedText, long userId
    ) {
        return insert("""
                INSERT INTO three_fixed_plan_versions(
                  plan_id,version_no,version_label,source_type,parse_status,
                  plan_name,document_no,effective_date,organization_name,
                  organization_nature,staffing_type,approved_staffing,
                  main_responsibilities,internal_departments,remarks,parsed_text,
                  created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, planId, versionNo, label, sourceType, parseStatus,
                fields.planName(), fields.documentNo(), fields.effectiveDate(),
                fields.organizationName(), fields.organizationNature(), fields.staffingType(),
                fields.approvedStaffing(), fields.mainResponsibilities(),
                fields.internalDepartments(), fields.remarks(), parsedText, userId, userId);
    }

    public List<Map<String, Object>> list(
            Long orgId, String keyword, String status, String year, int size, int offset
    ) {
        Query query = listQuery(orgId, keyword, status, year);
        List<Object> params = new ArrayList<>(query.params);
        params.add(size);
        params.add(offset);
        return jdbc.queryForList("""
                SELECT plan.id, plan.org_unit_id, org.unit_name,
                  version.id AS latest_version_id, version.version_label,
                  version.plan_name, version.source_type, version.workflow_status,
                  version.parse_status, version.effective_date, version.updated_at
                FROM three_fixed_plans plan
                JOIN org_units org ON org.id=plan.org_unit_id
                JOIN three_fixed_plan_versions version ON version.id=(
                  SELECT v.id FROM three_fixed_plan_versions v
                  WHERE v.plan_id=plan.id ORDER BY v.version_no DESC LIMIT 1
                )
                """ + query.where + " ORDER BY version.updated_at DESC LIMIT ? OFFSET ?",
                params.toArray());
    }

    public long count(Long orgId, String keyword, String status, String year) {
        Query query = listQuery(orgId, keyword, status, year);
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT count(*) FROM three_fixed_plans plan
                JOIN org_units org ON org.id=plan.org_unit_id
                JOIN three_fixed_plan_versions version ON version.id=(
                  SELECT v.id FROM three_fixed_plan_versions v
                  WHERE v.plan_id=plan.id ORDER BY v.version_no DESC LIMIT 1
                )
                """ + query.where, Long.class, query.params.toArray())).orElse(0L);
    }

    public Map<String, Object> plan(long id) {
        return one("""
                SELECT plan.*,org.unit_name,current.version_label AS current_version_label
                FROM three_fixed_plans plan
                JOIN org_units org ON org.id=plan.org_unit_id
                LEFT JOIN three_fixed_plan_versions current ON current.id=plan.current_version_id
                WHERE plan.id=?
                """, id);
    }

    public List<Map<String, Object>> versions(long planId) {
        return jdbc.queryForList("""
                SELECT id,version_no,version_label,source_type,workflow_status,parse_status,
                  plan_name,effective_date,review_opinion,updated_at,row_version
                FROM three_fixed_plan_versions WHERE plan_id=?
                ORDER BY version_no DESC
                """, planId);
    }

    public Map<String, Object> version(long id) {
        return one("""
                SELECT version.*,plan.org_unit_id,org.unit_name,
                  creator.display_name AS created_by_name,
                  updater.display_name AS updated_by_name,
                  reviewer.display_name AS reviewed_by_name
                FROM three_fixed_plan_versions version
                JOIN three_fixed_plans plan ON plan.id=version.plan_id
                JOIN org_units org ON org.id=plan.org_unit_id
                LEFT JOIN sys_users creator ON creator.id=version.created_by
                LEFT JOIN sys_users updater ON updater.id=version.updated_by
                LEFT JOIN sys_users reviewer ON reviewer.id=version.reviewed_by
                WHERE version.id=?
                """, id);
    }

    public int updateFields(long id, ThreeFixedDtos.Fields f, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE three_fixed_plan_versions SET
                  plan_name=?,document_no=?,effective_date=?,organization_name=?,
                  organization_nature=?,staffing_type=?,approved_staffing=?,
                  main_responsibilities=?,internal_departments=?,remarks=?,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=? AND workflow_status<>'CONFIRMED'
                """, f.planName(), f.documentNo(), f.effectiveDate(), f.organizationName(),
                f.organizationNature(), f.staffingType(), f.approvedStaffing(),
                f.mainResponsibilities(), f.internalDepartments(), f.remarks(),
                userId, id, rowVersion);
    }

    public int updateAfterParse(
            long id, ThreeFixedDtos.Fields f, String parseStatus, String text,
            int rowVersion, long userId
    ) {
        return jdbc.update("""
                UPDATE three_fixed_plan_versions SET
                  parse_status=?,parsed_text=?,plan_name=?,document_no=?,effective_date=?,
                  organization_name=?,organization_nature=?,staffing_type=?,
                  approved_staffing=?,main_responsibilities=?,internal_departments=?,remarks=?,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=? AND workflow_status<>'CONFIRMED'
                """, parseStatus, text, f.planName(), f.documentNo(), f.effectiveDate(),
                f.organizationName(), f.organizationNature(), f.staffingType(),
                f.approvedStaffing(), f.mainResponsibilities(), f.internalDepartments(),
                f.remarks(), userId, id, rowVersion);
    }

    public void replaceParseResults(long versionId, List<ParseWrite> results) {
        jdbc.update("DELETE FROM three_fixed_parse_results WHERE version_id=?", versionId);
        for (ParseWrite result : results) {
            jdbc.update("""
                    INSERT INTO three_fixed_parse_results(
                      version_id,field_code,source_label,extracted_value,source_snippet,
                      parse_method,confidence_code
                    ) VALUES(?,?,?,?,?,?,?)
                    """, versionId, result.fieldCode, result.sourceLabel, result.value,
                    result.snippet, result.method, result.confidence);
        }
    }

    public List<Map<String, Object>> parseResults(long versionId) {
        return jdbc.queryForList("""
                SELECT id,field_code,source_label,extracted_value,corrected_value,
                  source_snippet,parse_method,confidence_code
                FROM three_fixed_parse_results WHERE version_id=? ORDER BY id
                """, versionId);
    }

    public void updateCorrections(long versionId, Map<String, String> corrections) {
        corrections.forEach((field, value) -> jdbc.update("""
                UPDATE three_fixed_parse_results SET corrected_value=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                WHERE version_id=? AND field_code=?
                """, value, versionId, field));
    }

    public int returnVersion(long id, String opinion, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE three_fixed_plan_versions SET workflow_status='RETURNED',
                  review_opinion=?,reviewed_by=?,reviewed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=? AND workflow_status='PENDING_REVIEW'
                """, opinion, userId, userId, id, rowVersion);
    }

    public int submit(long id, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE three_fixed_plan_versions SET workflow_status='PENDING_REVIEW',
                  review_opinion=NULL,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1
                WHERE id=? AND row_version=? AND workflow_status='RETURNED'
                """, userId, id, rowVersion);
    }

    public int confirm(long id, int rowVersion, long userId, String opinion) {
        return jdbc.update("""
                UPDATE three_fixed_plan_versions SET workflow_status='CONFIRMED',
                  review_opinion=?,reviewed_by=?,reviewed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=? AND workflow_status='PENDING_REVIEW'
                """, opinion, userId, userId, id, rowVersion);
    }

    public void publish(long planId, long versionId, long userId) {
        jdbc.update("""
                UPDATE three_fixed_plans SET current_version_id=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE id=?
                """, versionId, userId, planId);
    }

    public long insertAttachment(
            long versionId, String original, String stored, String path,
            String contentType, String extension, long size, String sha256, long userId
    ) {
        return insert("""
                INSERT INTO sys_attachments(
                  business_type,business_id,original_name,stored_name,storage_path,
                  content_type,extension,file_size,sha256,uploaded_by,status
                ) VALUES('THREE_FIXED_PLAN_VERSION',?,?,?,?,?,?,?,?,?,'ACTIVE')
                """, versionId, original, stored, path, contentType, extension, size, sha256, userId);
    }

    public Map<String, Object> attachment(long id) {
        return one("""
                SELECT * FROM sys_attachments
                WHERE id=? AND business_type='THREE_FIXED_PLAN_VERSION' AND status='ACTIVE'
                """, id);
    }

    public List<Map<String, Object>> attachments(long versionId) {
        return jdbc.queryForList("""
                SELECT id,original_name,content_type,extension,file_size,created_at
                FROM sys_attachments
                WHERE business_type='THREE_FIXED_PLAN_VERSION' AND business_id=?
                  AND status='ACTIVE' ORDER BY id
                """, versionId);
    }

    public Map<String, Object> attachmentForVersion(long versionId) {
        return one("""
                SELECT * FROM sys_attachments
                WHERE business_type='THREE_FIXED_PLAN_VERSION' AND business_id=?
                  AND status='ACTIVE' ORDER BY id LIMIT 1
                """, versionId);
    }

    public List<SimpleDocumentParser.Mapping> activeMappings(String fileType) {
        return jdbc.query("""
                SELECT file_type,source_label,target_field
                FROM three_fixed_field_mappings
                WHERE status='ACTIVE' AND file_type IN ('ALL',?)
                ORDER BY sort_order,id
                """, (rs, n) -> new SimpleDocumentParser.Mapping(
                rs.getString(1), rs.getString(2), rs.getString(3)), fileType);
    }

    public List<Map<String, Object>> mappings() {
        return jdbc.queryForList("""
                SELECT mapping.*,creator.display_name AS created_by_name,
                  updater.display_name AS updated_by_name
                FROM three_fixed_field_mappings mapping
                LEFT JOIN sys_users creator ON creator.id=mapping.created_by
                LEFT JOIN sys_users updater ON updater.id=mapping.updated_by
                ORDER BY mapping.sort_order,mapping.id
                """);
    }

    public long createMapping(ThreeFixedDtos.MappingRequest r, long userId) {
        return insert("""
                INSERT INTO three_fixed_field_mappings(
                  file_type,source_label,target_field,sort_order,created_by,updated_by
                ) VALUES(?,?,?,?,?,?)
                """, r.fileType(), r.sourceLabel(), r.targetField(),
                r.sortOrder() == null ? 0 : r.sortOrder(), userId, userId);
    }

    public int updateMapping(long id, ThreeFixedDtos.MappingRequest r, long userId) {
        return jdbc.update("""
                UPDATE three_fixed_field_mappings SET file_type=?,source_label=?,
                  target_field=?,sort_order=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                WHERE id=?
                """, r.fileType(), r.sourceLabel(), r.targetField(),
                r.sortOrder() == null ? 0 : r.sortOrder(), userId, id);
    }

    public int updateMappingStatus(long id, String status, long userId) {
        return jdbc.update("""
                UPDATE three_fixed_field_mappings SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE id=?
                """, status, userId, id);
    }

    private Query listQuery(Long orgId, String keyword, String status, String year) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (orgId != null) { conditions.add("plan.org_unit_id=?"); params.add(orgId); }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(version.plan_name LIKE ? OR version.document_no LIKE ? OR org.unit_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("version.workflow_status=?"); params.add(status.trim().toUpperCase());
        }
        if (year != null && !year.isBlank()) {
            conditions.add("substr(version.effective_date,1,4)=?"); params.add(year.trim());
        }
        return new Query(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), params);
    }

    private Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("未获得新增记录主键");
        return key.longValue();
    }

    private record Query(String where, List<Object> params) {
    }

    public record ParseWrite(
            String fieldCode, String sourceLabel, String value, String snippet,
            String method, String confidence
    ) {
    }
}
