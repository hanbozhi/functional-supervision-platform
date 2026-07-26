package com.zhineng.platform.basicinfo.corefunction.repository;

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
public class CoreFunctionRepository {
    private final JdbcTemplate jdbc;

    public CoreFunctionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageRows functions(Long orgId, String keyword, String status, int page, int size) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (orgId != null) {
            clauses.add("function.org_unit_id=?");
            params.add(orgId);
        }
        if (keyword != null && !keyword.isBlank()) {
            clauses.add("(function.function_name LIKE ? OR function.function_code LIKE ? "
                    + "OR function.industry_tag LIKE ? OR function.description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        if (status != null && !status.isBlank()) {
            clauses.add("function.status=?");
            params.add(status.trim().toUpperCase());
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM department_core_functions function" + where,
                Long.class, params.toArray())).orElse(0L);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size); pageParams.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT function.*,org.unit_code,org.unit_name,
                  creator.display_name created_by_name,updater.display_name updated_by_name,
                  (SELECT count(*) FROM department_duty_items duty
                    WHERE duty.core_function_id=function.id AND duty.status='ACTIVE') duty_count
                FROM department_core_functions function
                JOIN org_units org ON org.id=function.org_unit_id
                LEFT JOIN sys_users creator ON creator.id=function.created_by
                LEFT JOIN sys_users updater ON updater.id=function.updated_by
                """ + where + " ORDER BY org.sort_order,function.sort_order,function.id "
                + "LIMIT ? OFFSET ?", pageParams.toArray());
        return new PageRows(rows, total);
    }

    public Map<String, Object> function(long id) {
        return one("""
                SELECT function.*,org.unit_code,org.unit_name,
                  creator.display_name created_by_name,updater.display_name updated_by_name
                FROM department_core_functions function
                JOIN org_units org ON org.id=function.org_unit_id
                LEFT JOIN sys_users creator ON creator.id=function.created_by
                LEFT JOIN sys_users updater ON updater.id=function.updated_by
                WHERE function.id=?
                """, id);
    }

    public long insertFunction(
            long orgId, String code, String name, String tag, String description,
            int sortOrder, long userId
    ) {
        return insert("""
                INSERT INTO department_core_functions(
                  org_unit_id,function_code,function_name,industry_tag,description,
                  sort_order,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?)
                """, orgId, code, name, tag, description, sortOrder, userId, userId);
    }

    public int updateFunction(
            long id, String code, String name, String tag, String description,
            int sortOrder, int versionNo, long userId
    ) {
        return jdbc.update("""
                UPDATE department_core_functions SET function_code=?,function_name=?,
                  industry_tag=?,description=?,sort_order=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),version_no=version_no+1
                WHERE id=? AND version_no=?
                """, code, name, tag, description, sortOrder, userId, id, versionNo);
    }

    public int updateFunctionStatus(long id, String status, int versionNo, long userId) {
        return jdbc.update("""
                UPDATE department_core_functions SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),version_no=version_no+1
                WHERE id=? AND version_no=?
                """, status, userId, id, versionNo);
    }

    public boolean functionCodeExists(long orgId, String code, Long excludedId) {
        String extra = excludedId == null ? "" : " AND id<>?";
        Object[] params = excludedId == null
                ? new Object[]{orgId, code} : new Object[]{orgId, code, excludedId};
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)>0 FROM department_core_functions
                WHERE org_unit_id=? AND function_code=? COLLATE NOCASE
                """ + extra, Boolean.class, params));
    }

    public List<Map<String, Object>> duties(Long functionId, Long orgId, String status) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (functionId != null) { clauses.add("duty.core_function_id=?"); params.add(functionId); }
        if (orgId != null) { clauses.add("duty.org_unit_id=?"); params.add(orgId); }
        if (status != null && !status.isBlank()) {
            clauses.add("duty.status=?"); params.add(status.toUpperCase());
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return jdbc.queryForList("""
                SELECT duty.*,function.function_name,function.function_code,
                  function.status function_status,
                  version.version_label,updater.display_name updated_by_name
                FROM department_duty_items duty
                JOIN department_core_functions function ON function.id=duty.core_function_id
                LEFT JOIN three_fixed_plan_versions version ON version.id=duty.source_version_id
                LEFT JOIN sys_users updater ON updater.id=duty.updated_by
                """ + where + " ORDER BY function.sort_order,duty.sort_order,duty.id",
                params.toArray());
    }

    public Map<String, Object> duty(long id) {
        return one("""
                SELECT duty.*,function.function_name,function.status function_status
                FROM department_duty_items duty
                JOIN department_core_functions function ON function.id=duty.core_function_id
                WHERE duty.id=?
                """, id);
    }

    public long insertDuty(
            long functionId, long orgId, String content, String keywords, String sourceType,
            Long sourceVersionId, String snippet, int sortOrder, long userId
    ) {
        return insert("""
                INSERT INTO department_duty_items(
                  core_function_id,org_unit_id,duty_content,keywords,source_type,
                  source_version_id,source_snippet,sort_order,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?,?)
                """, functionId, orgId, content, keywords, sourceType,
                sourceVersionId, snippet, sortOrder, userId, userId);
    }

    public int updateDuty(
            long id, String content, String keywords, int sortOrder,
            int versionNo, long userId
    ) {
        return jdbc.update("""
                UPDATE department_duty_items SET duty_content=?,keywords=?,sort_order=?,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1 WHERE id=? AND version_no=?
                """, content, keywords, sortOrder, userId, id, versionNo);
    }

    public int updateDutyStatus(long id, String status, int versionNo, long userId) {
        return jdbc.update("""
                UPDATE department_duty_items SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1 WHERE id=? AND version_no=?
                """, status, userId, id, versionNo);
    }

    public void supersedeThreeFixedDuties(long orgId, long userId) {
        jdbc.update("""
                UPDATE department_duty_items SET status='SUPERSEDED',updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),version_no=version_no+1
                WHERE org_unit_id=? AND source_type='THREE_FIXED' AND status<>'SUPERSEDED'
                """, userId, orgId);
    }

    public Map<String, Object> currentThreeFixed(long orgId) {
        return optional("""
                SELECT version.id version_id,version.version_label,
                  version.main_responsibilities,version.workflow_status
                FROM three_fixed_plans plan
                JOIN three_fixed_plan_versions version ON version.id=plan.current_version_id
                WHERE plan.org_unit_id=? AND plan.status='ACTIVE'
                  AND version.workflow_status='CONFIRMED'
                """, orgId).orElse(null);
    }

    public List<Map<String, Object>> rightsDepartments() {
        return jdbc.queryForList("""
                SELECT department.department_name,mapping.id mapping_id,mapping.org_unit_id,
                  org.unit_name mapped_org_name,mapping.mapping_type,mapping.status
                FROM (
                  SELECT DISTINCT trim(department_guess) department_name FROM rights_items
                  WHERE department_guess IS NOT NULL AND trim(department_guess)<>''
                ) department
                LEFT JOIN org_rights_department_mappings mapping
                  ON mapping.rights_department_name=department.department_name COLLATE NOCASE
                LEFT JOIN org_units org ON org.id=mapping.org_unit_id
                ORDER BY department.department_name
                """);
    }

    public List<Map<String, Object>> activeOrganizations() {
        return jdbc.queryForList("""
                SELECT id,unit_code,unit_name,unit_short_name FROM org_units
                WHERE status='ACTIVE' AND unit_type NOT IN ('ROOT','GROUP')
                ORDER BY sort_order,id
                """);
    }

    public Map<String, Object> organization(long id) {
        return optional("""
                SELECT id,unit_code,unit_name,unit_short_name,unit_type,status
                FROM org_units WHERE id=?
                """, id).orElse(null);
    }

    public void clearMappings(long orgId) {
        jdbc.update("DELETE FROM org_rights_department_mappings WHERE org_unit_id=?", orgId);
    }

    public void insertMapping(long orgId, String department, String type, long userId) {
        jdbc.update("""
                INSERT INTO org_rights_department_mappings(
                  org_unit_id,rights_department_name,mapping_type,created_by,updated_by
                ) VALUES(?,?,?,?,?)
                """, orgId, department, type, userId, userId);
    }

    public Map<String, Object> mappingByDepartment(String department) {
        return optional("""
                SELECT mapping.*,org.unit_name FROM org_rights_department_mappings mapping
                JOIN org_units org ON org.id=mapping.org_unit_id
                WHERE mapping.rights_department_name=? COLLATE NOCASE
                """, department).orElse(null);
    }

    public long activeMappingCount(long orgId) {
        return count("""
                SELECT count(*) FROM org_rights_department_mappings
                WHERE org_unit_id=? AND status='ACTIVE'
                """, orgId);
    }

    public long unmappedDepartmentCount() {
        return count("""
                SELECT count(*) FROM (
                  SELECT DISTINCT trim(department_guess) name FROM rights_items
                  WHERE department_guess IS NOT NULL AND trim(department_guess)<>''
                ) department
                LEFT JOIN org_rights_department_mappings mapping
                  ON mapping.rights_department_name=department.name COLLATE NOCASE
                    AND mapping.status='ACTIVE'
                WHERE mapping.id IS NULL
                """);
    }

    public List<RightsRow> rightsForOrg(long orgId) {
        return jdbc.query("""
                SELECT item.id,item.department_guess,item.item_name,item.subitem_name,
                  item.department_duty,item.responsibility_content,source.file_name
                FROM rights_items item
                JOIN source_files source ON source.id=item.source_file_id
                JOIN org_rights_department_mappings mapping
                  ON mapping.rights_department_name=item.department_guess COLLATE NOCASE
                WHERE mapping.org_unit_id=? AND mapping.status='ACTIVE'
                ORDER BY item.id
                """, (rs, n) -> new RightsRow(
                rs.getLong("id"), rs.getString("department_guess"),
                rs.getString("item_name"), rs.getString("subitem_name"),
                rs.getString("department_duty"), rs.getString("responsibility_content"),
                rs.getString("file_name")), orgId);
    }

    public String rightsDatasetSignature() {
        return jdbc.queryForObject("""
                SELECT count(*) || ':' || coalesce(max(id),0) || ':' ||
                  coalesce((SELECT max(imported_at) FROM source_files),'')
                FROM rights_items
                """, String.class);
    }

    public long insertRun(
            long orgId, Long sourceVersionId, String signature, int threshold,
            int dutyCount, int rightsCount, int matchedDutyCount, int missingCount,
            int unapprovedCount, double coverage, double matchRate, long userId
    ) {
        return insert("""
                INSERT INTO duty_match_runs(
                  org_unit_id,source_version_id,rights_dataset_signature,match_threshold,
                  duty_count,rights_item_count,matched_duty_count,duty_missing_count,
                  unapproved_new_count,coverage_rate,match_rate,status,created_by
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,'COMPLETED',?)
                """, orgId, sourceVersionId, signature, threshold, dutyCount, rightsCount,
                matchedDutyCount, missingCount, unapprovedCount, coverage, matchRate, userId);
    }

    public void insertResult(ResultWrite value) {
        jdbc.update("""
                INSERT INTO duty_match_results(
                  run_id,duty_item_id,rights_item_id,result_type,match_origin,
                  duty_content_snapshot,rights_department_snapshot,
                  rights_item_name_snapshot,rights_content_snapshot,
                  auto_score,final_score,matched_keywords,review_status
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.runId, value.dutyId, value.rightsId, value.resultType,
                value.origin, value.dutySnapshot, value.rightsDepartment,
                value.rightsName, value.rightsContent, value.autoScore,
                value.finalScore, value.keywords, value.reviewStatus);
    }

    public long insertManualResult(ResultWrite value) {
        return insert("""
                INSERT INTO duty_match_results(
                  run_id,duty_item_id,rights_item_id,result_type,match_origin,
                  duty_content_snapshot,rights_department_snapshot,
                  rights_item_name_snapshot,rights_content_snapshot,
                  auto_score,final_score,matched_keywords,review_status
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.runId, value.dutyId, value.rightsId, value.resultType,
                value.origin, value.dutySnapshot, value.rightsDepartment,
                value.rightsName, value.rightsContent, value.autoScore,
                value.finalScore, value.keywords, value.reviewStatus);
    }

    public List<Map<String, Object>> runs(long orgId) {
        String signature = rightsDatasetSignature();
        return jdbc.queryForList("""
                SELECT run.*,user.display_name created_by_name,
                  CASE WHEN run.rights_dataset_signature=? THEN 0 ELSE 1 END data_stale
                FROM duty_match_runs run
                LEFT JOIN sys_users user ON user.id=run.created_by
                WHERE run.org_unit_id=? ORDER BY run.id DESC
                """, signature, orgId);
    }

    public Map<String, Object> run(long id) {
        return one("SELECT * FROM duty_match_runs WHERE id=?", id);
    }

    public List<Map<String, Object>> results(
            long runId, String resultType, String reviewStatus
    ) {
        List<String> clauses = new ArrayList<>(List.of("result.run_id=?"));
        List<Object> params = new ArrayList<>(List.of(runId));
        if (resultType != null && !resultType.isBlank()) {
            clauses.add("result.result_type=?"); params.add(resultType.toUpperCase());
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            clauses.add("result.review_status=?"); params.add(reviewStatus.toUpperCase());
        }
        return jdbc.queryForList("""
                SELECT result.*,function.function_name,reviewer.display_name reviewed_by_name
                FROM duty_match_results result
                LEFT JOIN department_duty_items duty ON duty.id=result.duty_item_id
                LEFT JOIN department_core_functions function ON function.id=duty.core_function_id
                LEFT JOIN sys_users reviewer ON reviewer.id=result.reviewed_by
                WHERE
                """ + String.join(" AND ", clauses) + " ORDER BY result.result_type,result.id",
                params.toArray());
    }

    public Map<String, Object> result(long id) {
        return one("SELECT * FROM duty_match_results WHERE id=?", id);
    }

    public int reviewResult(
            long id, String type, Long dutyId, Long rightsId, double finalScore,
            String status, String opinion, int versionNo, long userId,
            String dutySnapshot, RightsRow rights
    ) {
        return jdbc.update("""
                UPDATE duty_match_results SET result_type=?,duty_item_id=?,rights_item_id=?,
                  duty_content_snapshot=?,rights_department_snapshot=?,
                  rights_item_name_snapshot=?,rights_content_snapshot=?,final_score=?,
                  review_status=?,processing_opinion=?,reviewed_by=?,
                  reviewed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),version_no=version_no+1
                WHERE id=? AND version_no=?
                """, type, dutyId, rightsId, dutySnapshot,
                rights == null ? null : rights.department(),
                rights == null ? null : rights.itemName(),
                rights == null ? null : rights.combinedText(),
                finalScore, status, opinion, userId, id, versionNo);
    }

    public void recalculateRun(long runId) {
        Map<String, Object> run = run(runId);
        int dutyCount = ((Number) run.get("duty_count")).intValue();
        long matched = count("""
                SELECT count(DISTINCT duty_item_id) FROM duty_match_results
                WHERE run_id=? AND result_type='MATCHED' AND review_status<>'REJECTED'
                """, runId);
        long missing = count("""
                SELECT count(*) FROM duty_match_results
                WHERE run_id=? AND result_type='DUTY_MISSING' AND review_status<>'REJECTED'
                """, runId);
        long unapproved = count("""
                SELECT count(*) FROM duty_match_results
                WHERE run_id=? AND result_type='UNAPPROVED_NEW_DUTY'
                  AND review_status<>'REJECTED'
                """, runId);
        Double scoreSum = jdbc.queryForObject("""
                SELECT coalesce(sum(best_score),0) FROM (
                  SELECT duty_item_id,coalesce(max(CASE
                    WHEN result_type='MATCHED' AND review_status<>'REJECTED'
                    THEN final_score END),0) best_score
                  FROM duty_match_results
                  WHERE run_id=? AND duty_item_id IS NOT NULL
                  GROUP BY duty_item_id
                )
                """, Double.class, runId);
        double coverage = dutyCount == 0 ? 0 : Math.round(matched * 10000.0 / dutyCount) / 100.0;
        jdbc.update("""
                UPDATE duty_match_runs SET matched_duty_count=?,duty_missing_count=?,
                  unapproved_new_count=?,coverage_rate=?,match_rate=? WHERE id=?
                """, matched, missing, unapproved, coverage,
                scoreSum == null || dutyCount == 0
                        ? 0 : Math.round(scoreSum * 100.0 / dutyCount) / 100.0,
                runId);
    }

    public RightsRow rightById(long id) {
        List<RightsRow> rows = jdbc.query("""
                SELECT item.id,item.department_guess,item.item_name,item.subitem_name,
                  item.department_duty,item.responsibility_content,source.file_name
                FROM rights_items item JOIN source_files source ON source.id=item.source_file_id
                WHERE item.id=?
                """, (rs, n) -> new RightsRow(
                rs.getLong("id"), rs.getString("department_guess"),
                rs.getString("item_name"), rs.getString("subitem_name"),
                rs.getString("department_duty"), rs.getString("responsibility_content"),
                rs.getString("file_name")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> stats(long orgId) {
        Map<String, Object> latest = optional("""
                SELECT * FROM duty_match_runs WHERE org_unit_id=? ORDER BY id DESC LIMIT 1
                """, orgId).orElse(Map.of());
        return Map.of(
                "activeFunctions", count("""
                    SELECT count(*) FROM department_core_functions
                    WHERE org_unit_id=? AND status='ACTIVE'""", orgId),
                "activeDuties", count("""
                    SELECT count(*) FROM department_duty_items
                    WHERE org_unit_id=? AND status='ACTIVE'""", orgId),
                "unmappedDepartments", unmappedDepartmentCount(),
                "latestRun", latest,
                "rightsDataSignature", rightsDatasetSignature());
    }

    private long count(String sql, Object... params) {
        return Optional.ofNullable(jdbc.queryForObject(sql, Long.class, params)).orElse(0L);
    }

    private Map<String, Object> one(String sql, Object... params) {
        return optional(sql, params).orElseThrow();
    }

    private Optional<Map<String, Object>> optional(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream().findFirst();
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("写入数据后未获得主键");
        return key.longValue();
    }

    public record PageRows(List<Map<String, Object>> rows, long total) {
    }

    public record RightsRow(
            long id, String department, String itemName, String subitemName,
            String departmentDuty, String responsibilityContent, String sourceFile
    ) {
        public String combinedText() {
            return String.join(" ", safe(itemName), safe(subitemName),
                    safe(departmentDuty), safe(responsibilityContent));
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    public record ResultWrite(
            long runId, Long dutyId, Long rightsId, String resultType, String origin,
            String dutySnapshot, String rightsDepartment, String rightsName,
            String rightsContent, double autoScore, double finalScore,
            String keywords, String reviewStatus
    ) {
    }
}
