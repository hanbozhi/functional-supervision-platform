package com.zhineng.platform.basicinfo.orgunit.repository;

import com.zhineng.platform.basicinfo.orgunit.dto.OrgUnitDtos;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrgUnitRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrgUnitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UnitRow> findTreeRows(boolean includeInactive) {
        String where = includeInactive ? "" : " WHERE unit.status = 'ACTIVE'";
        return jdbcTemplate.query(baseSelect() + where
                + " ORDER BY unit.sort_order, unit.id", this::mapUnit);
    }

    public PageRows findPage(
            Long parentId,
            String scope,
            String keyword,
            String unitType,
            String unitLevel,
            String nature,
            String status,
            String verificationStatus,
            int page,
            int size
    ) {
        Query query = buildQuery(parentId, scope, keyword, unitType, unitLevel,
                nature, status, verificationStatus);
        long total = Optional.ofNullable(jdbcTemplate.queryForObject(
                query.prefix + "SELECT count(*) FROM org_units unit " + query.where,
                Long.class, query.params.toArray())).orElse(0L);
        List<Object> pageParams = new ArrayList<>(query.params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<UnitRow> rows = jdbcTemplate.query(
                query.prefix + baseSelect() + query.where
                        + " ORDER BY unit.sort_order, unit.id LIMIT ? OFFSET ?",
                this::mapUnit, pageParams.toArray());
        return new PageRows(rows, total);
    }

    public OrgUnitDtos.Stats stats(Long parentId, String scope) {
        Query query = buildQuery(parentId, scope, null, null, null, null, null, null);
        String sql = query.prefix + """
                SELECT
                  count(*) AS total_units,
                  sum(CASE WHEN unit.unit_type IN ('OFFICE','ADMIN_AGENCY') THEN 1 ELSE 0 END)
                    AS administrative_units,
                  sum(CASE WHEN unit.unit_type = 'PUBLIC_INSTITUTION' THEN 1 ELSE 0 END)
                    AS public_institutions
                FROM org_units unit
                """ + appendCondition(query.where,
                "unit.unit_type NOT IN ('ROOT','GROUP') AND unit.status <> 'DELETED'");
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new OrgUnitDtos.Stats(
                rs.getLong("total_units"),
                rs.getLong("administrative_units"),
                rs.getLong("public_institutions")
        ), query.params.toArray());
    }

    public Optional<UnitRow> findById(long id) {
        List<UnitRow> rows = jdbcTemplate.query(
                baseSelect() + " WHERE unit.id = ?", this::mapUnit, id);
        return rows.stream().findFirst();
    }

    public boolean exists(long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT count(*) > 0 FROM org_units WHERE id = ?", Boolean.class, id));
    }

    public boolean codeExists(String code, Long excludedId) {
        if (excludedId == null) {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT count(*) > 0 FROM org_units WHERE unit_code = ? COLLATE NOCASE",
                    Boolean.class, code));
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT count(*) > 0 FROM org_units WHERE unit_code = ? COLLATE NOCASE AND id <> ?",
                Boolean.class, code, excludedId));
    }

    public boolean isDescendant(long unitId, long candidateParentId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                WITH RECURSIVE descendants(id) AS (
                  SELECT id FROM org_units WHERE parent_id = ?
                  UNION ALL
                  SELECT child.id
                  FROM org_units child JOIN descendants parent ON child.parent_id = parent.id
                )
                SELECT count(*) > 0 FROM descendants WHERE id = ?
                """, Boolean.class, unitId, candidateParentId));
    }

    public long childCount(long id) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM org_units WHERE parent_id = ?",
                Long.class, id)).orElse(0L);
    }

    public long insert(UnitWrite unit, long operatorId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO org_units(
                      parent_id, unit_code, unit_name, unit_short_name, unit_type,
                      unit_level, organization_nature, approved_staffing, sort_order,
                      status, verification_status, created_by, updated_by
                    ) VALUES(?,?,?,?,?,?,?,?,?,'ACTIVE','PENDING',?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setWriteParameters(statement, unit);
            statement.setLong(10, operatorId);
            statement.setLong(11, operatorId);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("新增机构后未获得主键");
        }
        return key.longValue();
    }

    public int update(long id, UnitWrite unit, long operatorId, int versionNo, boolean resetVerification) {
        String verification = resetVerification
                ? ", verification_status='PENDING', verification_opinion=NULL, verified_by=NULL, verified_at=NULL"
                : "";
        List<Object> params = new ArrayList<>();
        params.add(unit.parentId);
        params.add(unit.unitCode);
        params.add(unit.unitName);
        params.add(unit.unitShortName);
        params.add(unit.unitType);
        params.add(unit.unitLevel);
        params.add(unit.organizationNature);
        params.add(unit.approvedStaffing);
        params.add(unit.sortOrder);
        params.add(operatorId);
        params.add(id);
        params.add(versionNo);
        return jdbcTemplate.update("""
                UPDATE org_units SET
                  parent_id=?, unit_code=?, unit_name=?, unit_short_name=?, unit_type=?,
                  unit_level=?, organization_nature=?, approved_staffing=?, sort_order=?,
                  updated_by=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1
                """ + verification + " WHERE id=? AND version_no=?", params.toArray());
    }

    public int updateStatus(long id, String status, long operatorId, int versionNo) {
        return jdbcTemplate.update("""
                UPDATE org_units SET status=?, updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1
                WHERE id=? AND version_no=?
                """, status, operatorId, id, versionNo);
    }

    public int verify(
            long id,
            String result,
            String opinion,
            long operatorId,
            int versionNo
    ) {
        return jdbcTemplate.update("""
                UPDATE org_units SET verification_status=?, verification_opinion=?,
                  verified_by=?, verified_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1
                WHERE id=? AND version_no=? AND status='ACTIVE'
                """, result, opinion, operatorId, operatorId, id, versionNo);
    }

    public void insertVerification(long id, String result, String opinion, long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO org_unit_verifications(
                  org_unit_id, verification_result, verification_opinion, verifier_id
                ) VALUES(?,?,?,?)
                """, id, result, opinion, operatorId);
    }

    public List<OrgUnitDtos.Verification> findVerifications(long id) {
        return jdbcTemplate.query("""
                SELECT verification.id, verification.verification_result,
                  verification.verification_opinion, verification.verifier_id,
                  user.display_name AS verifier_name, verification.verified_at
                FROM org_unit_verifications verification
                LEFT JOIN sys_users user ON user.id = verification.verifier_id
                WHERE verification.org_unit_id = ?
                ORDER BY verification.verified_at DESC, verification.id DESC
                """, (rs, rowNum) -> new OrgUnitDtos.Verification(
                rs.getLong("id"),
                rs.getString("verification_result"),
                rs.getString("verification_opinion"),
                nullableLong(rs.getObject("verifier_id")),
                rs.getString("verifier_name"),
                rs.getString("verified_at")
        ), id);
    }

    private Query buildQuery(
            Long parentId,
            String scope,
            String keyword,
            String unitType,
            String unitLevel,
            String nature,
            String status,
            String verificationStatus
    ) {
        String prefix = "";
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (parentId != null) {
            if ("DIRECT".equalsIgnoreCase(scope)) {
                conditions.add("unit.parent_id = ?");
                params.add(parentId);
            } else {
                prefix = """
                        WITH RECURSIVE scoped(id) AS (
                          SELECT id FROM org_units WHERE id = ?
                          UNION ALL
                          SELECT child.id FROM org_units child
                          JOIN scoped parent ON child.parent_id = parent.id
                        )
                        """;
                params.add(parentId);
                conditions.add("unit.id IN (SELECT id FROM scoped)");
            }
        }
        if (hasText(keyword)) {
            conditions.add("(unit.unit_name LIKE ? OR unit.unit_short_name LIKE ? OR unit.unit_code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        addEquals(conditions, params, "unit.unit_type", unitType);
        addEquals(conditions, params, "unit.unit_level", unitLevel);
        addEquals(conditions, params, "unit.organization_nature", nature);
        addEquals(conditions, params, "unit.status", status);
        addEquals(conditions, params, "unit.verification_status", verificationStatus);
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new Query(prefix, where, params);
    }

    private void addEquals(List<String> conditions, List<Object> params, String column, String value) {
        if (hasText(value)) {
            conditions.add(column + " = ?");
            params.add(value.trim().toUpperCase());
        }
    }

    private String appendCondition(String where, String condition) {
        return where.isEmpty() ? " WHERE " + condition : where + " AND " + condition;
    }

    private String baseSelect() {
        return """
                SELECT unit.*, parent.unit_name AS parent_name,
                  creator.display_name AS created_by_name,
                  updater.display_name AS updated_by_name,
                  verifier.display_name AS verified_by_name
                FROM org_units unit
                LEFT JOIN org_units parent ON parent.id = unit.parent_id
                LEFT JOIN sys_users creator ON creator.id = unit.created_by
                LEFT JOIN sys_users updater ON updater.id = unit.updated_by
                LEFT JOIN sys_users verifier ON verifier.id = unit.verified_by
                """;
    }

    private UnitRow mapUnit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UnitRow(
                rs.getLong("id"),
                nullableLong(rs.getObject("parent_id")),
                rs.getString("parent_name"),
                rs.getString("unit_code"),
                rs.getString("unit_name"),
                rs.getString("unit_short_name"),
                rs.getString("unit_type"),
                rs.getString("unit_level"),
                rs.getString("organization_nature"),
                nullableInteger(rs.getObject("approved_staffing")),
                rs.getInt("sort_order"),
                rs.getString("status"),
                rs.getString("verification_status"),
                rs.getString("verification_opinion"),
                nullableLong(rs.getObject("created_by")),
                rs.getString("created_by_name"),
                rs.getString("created_at"),
                nullableLong(rs.getObject("updated_by")),
                rs.getString("updated_by_name"),
                rs.getString("updated_at"),
                nullableLong(rs.getObject("verified_by")),
                rs.getString("verified_by_name"),
                rs.getString("verified_at"),
                rs.getInt("version_no")
        );
    }

    private static void setWriteParameters(PreparedStatement statement, UnitWrite unit)
            throws java.sql.SQLException {
        statement.setObject(1, unit.parentId);
        statement.setString(2, unit.unitCode);
        statement.setString(3, unit.unitName);
        statement.setString(4, unit.unitShortName);
        statement.setString(5, unit.unitType);
        statement.setString(6, unit.unitLevel);
        statement.setString(7, unit.organizationNature);
        statement.setObject(8, unit.approvedStaffing);
        statement.setInt(9, unit.sortOrder);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private record Query(String prefix, String where, List<Object> params) {
    }

    public record PageRows(List<UnitRow> rows, long total) {
    }

    public record UnitWrite(
            Long parentId,
            String unitCode,
            String unitName,
            String unitShortName,
            String unitType,
            String unitLevel,
            String organizationNature,
            Integer approvedStaffing,
            int sortOrder
    ) {
    }

    public record UnitRow(
            long id,
            Long parentId,
            String parentName,
            String unitCode,
            String unitName,
            String unitShortName,
            String unitType,
            String unitLevel,
            String organizationNature,
            Integer approvedStaffing,
            int sortOrder,
            String status,
            String verificationStatus,
            String verificationOpinion,
            Long createdBy,
            String createdByName,
            String createdAt,
            Long updatedBy,
            String updatedByName,
            String updatedAt,
            Long verifiedBy,
            String verifiedByName,
            String verifiedAt,
            int versionNo
    ) {
    }
}
